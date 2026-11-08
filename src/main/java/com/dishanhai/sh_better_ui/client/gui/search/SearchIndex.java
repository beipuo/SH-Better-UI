package com.dishanhai.sh_better_ui.client.gui.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 山海：供搜索框与自绘界面使用的本地搜索快照。数据或显示语言变化时由调用方替换。 */
public final class SearchIndex<T> {
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final int NO_LITERAL_MATCH = 3;
    private static final int RANK_COUNT = 7;

    public record Entry<T>(T value, String name, List<String> ids, List<String> aliases) {
        public Entry {
            Objects.requireNonNull(value, "value");
            if (normalize(name).isEmpty()) throw new IllegalArgumentException("name must contain searchable text");
            ids = List.copyOf(ids);
            aliases = List.copyOf(aliases);
        }
    }

    public record Suggestion<T>(Entry<T> entry, String completion) {
        /** 返回可直接追加的字面前缀后缀。别名、分词和包含命中可能没有后缀。 */
        public String suffixFor(String input) {
            if (input.isEmpty() || completion.length() <= input.length()
                    || !completion.regionMatches(true, 0, input, 0, input.length())) return "";
            return completion.substring(input.length());
        }
    }

    private record Prepared<T>(Entry<T> entry, String name, List<String> ids, List<String> aliases,
                               List<String> pinyinKeys) {
        static <T> Prepared<T> of(Entry<T> entry) {
            String name = normalize(entry.name());
            List<String> ids = entry.ids().stream().map(SearchIndex::normalize).toList();
            List<String> aliases = entry.aliases().stream().map(SearchIndex::normalize).toList();
            List<String> pinyin = new ArrayList<>(PinyinSearch.keys(name));
            aliases.forEach(alias -> pinyin.addAll(PinyinSearch.keys(alias)));
            return new Prepared<>(entry, name, ids, aliases, List.copyOf(pinyin));
        }

        boolean matches(Query query, boolean requiresPinyin) {
            boolean usedPinyin = false;
            for (Token token : query.tokens()) {
                String literal = token.literal();
                if (name.contains(literal) || ids.stream().anyMatch(id -> id.contains(literal))
                        || aliases.stream().anyMatch(alias -> alias.contains(literal))) continue;
                if (token.pinyin().isEmpty() || pinyinKeys.stream().noneMatch(key -> key.contains(token.pinyin()))) {
                    return false;
                }
                usedPinyin = true;
            }
            return usedPinyin || !requiresPinyin;
        }
    }

    private record Token(String literal, String pinyin) {}

    private record Query(String text, List<Token> tokens) {
        static Query of(String raw) {
            String text = normalize(raw);
            if (text.isEmpty()) return new Query(text, List.of());

            List<Token> tokens = WHITESPACE.splitAsStream(text)
                    .map(token -> new Token(token, PinyinSearch.fold(token))).toList();
            return new Query(text, tokens);
        }
    }

    private final List<Prepared<T>> entries;

    public SearchIndex(List<Entry<T>> entries) {
        this.entries = entries.stream().map(Prepared::of).toList();
    }

    public List<Entry<T>> search(String query) {
        return search(query, value -> true);
    }

    /** 每个词可匹配名称、ID、别名或汉字的全拼和首字母。结果保留索引顺序。 */
    public List<Entry<T>> search(String query, Predicate<? super T> scope) {
        Query prepared = Query.of(query);
        return entries.stream().filter(entry -> scope.test(entry.entry().value()) && entry.matches(prepared, false))
                .map(Prepared::entry).toList();
    }

    Set<T> searchValuesByPinyin(String query) {
        Query prepared = Query.of(query);
        if (prepared.tokens().isEmpty()) return Set.of();
        return entries.stream().filter(entry -> entry.matches(prepared, true))
                .map(entry -> entry.entry().value()).collect(Collectors.toUnmodifiableSet());
    }

    public List<Suggestion<T>> suggest(String query, int limit) {
        return suggest(query, value -> true, limit);
    }

    /** 按名称精确／前缀／包含、ID 精确／前缀／包含、其余匹配依序排列。 */
    public List<Suggestion<T>> suggest(String query, Predicate<? super T> scope, int limit) {
        return suggest(query, scope, limit, completion -> true);
    }

    List<Suggestion<T>> suggest(String query, Predicate<? super T> scope, int limit,
                                Predicate<String> acceptsCompletion) {
        Query prepared = Query.of(query);
        if (limit <= 0 || prepared.tokens().isEmpty()) return List.of();

        List<List<Suggestion<T>>> ranked = new ArrayList<>(RANK_COUNT);
        for (int i = 0; i < RANK_COUNT; i++) ranked.add(new ArrayList<>());
        for (Prepared<T> candidate : entries) {
            Entry<T> entry = candidate.entry();
            if (!scope.test(entry.value()) || !candidate.matches(prepared, false)) continue;

            String completion = entry.name();
            boolean fillable = acceptsCompletion.test(completion);
            int rank = fillable ? matchRank(candidate.name(), prepared.text()) : NO_LITERAL_MATCH;
            if (rank == NO_LITERAL_MATCH) {
                int idRank = NO_LITERAL_MATCH;
                for (int i = 0; i < candidate.ids().size(); i++) {
                    int next = matchRank(candidate.ids().get(i), prepared.text());
                    if (next < idRank && acceptsCompletion.test(entry.ids().get(i))) {
                        idRank = next;
                        completion = entry.ids().get(i);
                        fillable = true;
                    }
                }
                rank += idRank;
            }
            List<Suggestion<T>> bucket = ranked.get(rank);
            if (bucket.size() < limit && fillable) {
                bucket.add(new Suggestion<>(entry, completion));
            }
            if (rank == 0 && bucket.size() == limit) break;
        }

        List<Suggestion<T>> result = new ArrayList<>();
        for (List<Suggestion<T>> bucket : ranked) {
            int count = Math.min(bucket.size(), limit - result.size());
            result.addAll(bucket.subList(0, count));
            if (result.size() == limit) break;
        }
        return List.copyOf(result);
    }

    private static int matchRank(String text, String query) {
        if (text.equals(query)) return 0;
        if (text.startsWith(query)) return 1;
        return text.contains(query) ? 2 : NO_LITERAL_MATCH;
    }

    private static String normalize(String text) {
        return WHITESPACE.matcher(text).replaceAll(" ").strip().toLowerCase(Locale.ROOT);
    }
}
