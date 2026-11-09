package com.dishanhai.sh_better_ui.client.gui.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 山海：解析普通词、模组和 tag 筛选词，并保留补全前的原始输入。 */
record SearchQuery(String text, List<Token> tokens, String prefix, boolean plainText) {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern TOKEN = Pattern.compile("[@#]?\"(?:\\\\.|[^\"\\\\])*\"?|\\S+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern RESERVED_TEXT = Pattern.compile("(^|\\s)[@#]|\"", Pattern.UNICODE_CHARACTER_CLASS);

    enum Kind { TEXT, MOD, TAG }

    record Token(Kind kind, String text, String pinyin) {}

    static SearchQuery of(String raw) {
        var matcher = TOKEN.matcher(raw);
        List<Token> tokens = new ArrayList<>();
        int lastStart = 0;
        while (matcher.find()) {
            lastStart = matcher.start();
            String text = matcher.group();
            Kind kind = switch (text.charAt(0)) {
                case '@' -> Kind.MOD;
                case '#' -> Kind.TAG;
                default -> Kind.TEXT;
            };
            if (kind != Kind.TEXT) text = text.substring(1);
            if (text.startsWith("\"")) {
                int end = text.length();
                if (end > 1 && text.endsWith("\"")) {
                    int slashes = 0;
                    for (int i = end - 2; i >= 0 && text.charAt(i) == '\\'; i--) slashes++;
                    if (slashes % 2 == 0) end--;
                }
                text = unescape(text.substring(1, end));
            }
            text = normalize(text);
            tokens.add(new Token(kind, text, PinyinSearch.fold(text)));
        }
        return new SearchQuery(normalize(raw), List.copyOf(tokens), raw.substring(0, lastStart), false);
    }

    /** 宿主已有自己的查询语法时，只按空白分词，保留 @、# 和引号的字面含义。 */
    static SearchQuery plainText(String raw) {
        String text = normalize(raw);
        List<Token> tokens = text.isEmpty() ? List.of() : java.util.Arrays.stream(text.split(" "))
                .map(token -> new Token(Kind.TEXT, token, PinyinSearch.fold(token))).toList();
        return new SearchQuery(text, tokens, "", true);
    }

    boolean hasFilters() {
        return tokens.stream().anyMatch(token -> token.kind() != Kind.TEXT);
    }

    String replaceLast(String completion) {
        return prefix + completion;
    }

    String completeText(String completion) {
        if (plainText) return completion;
        if (RESERVED_TEXT.matcher(completion).find()) {
            completion = "\"" + completion.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return hasFilters() ? replaceLast(completion) : completion;
    }

    private static String unescape(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()
                    && (text.charAt(i + 1) == '\\' || text.charAt(i + 1) == '"')) {
                c = text.charAt(++i);
            }
            result.append(c);
        }
        return result.toString();
    }

    static String normalize(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
