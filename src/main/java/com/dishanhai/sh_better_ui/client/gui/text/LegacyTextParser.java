package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 山海：把旧格式字符串拆成有独立原版样式的文本段。 */
final class LegacyTextParser {
    private static final Pattern TAG = Pattern.compile("\\{\\{([^{}]+)}}|\\{([^{}]+)}|<([^<>]+)>");
    private static final List<String> CLOSE = List.of("{{/}}", "{/}", "</>");

    private record Effect(String name, String symbols) {
        Style apply(Style style) {
            return UiTextEffects.mark(style, name, symbols);
        }
    }

    private record Prefix(Effect effect, int end) {}
    private record Scope(int kind, Effect previous) {}

    private LegacyTextParser() {}

    static boolean hasMarkup(String raw, boolean requireEffect) {
        for (int i = raw.indexOf('&'); i >= 0; i = raw.indexOf('&', i + 1)) {
            if (prefix(raw, i) != null) return true;
        }
        if (!requireEffect && raw.indexOf('§') >= 0) {
            for (int i = raw.indexOf('§'); i >= 0 && i + 1 < raw.length(); i = raw.indexOf('§', i + 1)) {
                if (ChatFormatting.getByCode(raw.charAt(i + 1)) != null) return true;
            }
        }
        if (raw.indexOf('{') < 0 && raw.indexOf('<') < 0) return false;
        var tags = TAG.matcher(raw);
        while (tags.find()) {
            int kind = tagKind(tags);
            if (UiTextEffects.find(tags.group(kind + 1).strip()) != null
                    && raw.indexOf(CLOSE.get(kind), tags.end()) >= 0) return true;
        }
        return false;
    }

    static Component parse(String raw, Style base, boolean requireEffect) {
        if (!hasMarkup(raw, requireEffect)) return null;
        var tags = TAG.matcher(raw);
        var scopes = new ArrayDeque<Scope>();
        var parts = new ArrayList<MutableComponent>();
        Style style = base;
        Effect active = null;
        int start = 0;
        for (int i = 0; i < raw.length();) {
            char c = raw.charAt(i);
            Prefix prefix = c == '&' ? prefix(raw, i) : null;
            ChatFormatting vanilla = c == '§' && i + 1 < raw.length()
                    ? ChatFormatting.getByCode(raw.charAt(i + 1)) : null;
            boolean reset = active != null && c == '&' && i + 1 < raw.length()
                    && Character.toLowerCase(raw.charAt(i + 1)) == 'r';
            int next = i + 1;
            if (prefix != null) {
                append(parts, raw, start, i, style, active);
                active = prefix.effect();
                next = prefix.end();
            } else if (reset || vanilla != null) {
                append(parts, raw, start, i, style, active);
                if (reset || vanilla == ChatFormatting.RESET) {
                    style = base;
                    active = null;
                    scopes.clear();
                } else {
                    style = style.applyLegacyFormat(vanilla);
                }
                next = i + 2;
            } else if ((c == '{' || c == '<') && tags.region(i, raw.length()).lookingAt()) {
                int kind = tagKind(tags);
                String name = tags.group(kind + 1).strip();
                if (name.equals("/") && !scopes.isEmpty() && scopes.peek().kind() == kind) {
                    append(parts, raw, start, i, style, active);
                    active = scopes.pop().previous();
                    next = tags.end();
                } else if (UiTextEffects.find(name) != null && raw.indexOf(CLOSE.get(kind), tags.end()) >= 0) {
                    append(parts, raw, start, i, style, active);
                    scopes.push(new Scope(kind, active));
                    active = new Effect(name, "");
                    next = tags.end();
                } else {
                    i = tags.end();
                    continue;
                }
            } else {
                i = next;
                continue;
            }
            start = i = next;
        }
        append(parts, raw, start, raw.length(), style, active);
        if (parts.isEmpty()) return Component.empty().setStyle(base);
        if (parts.size() == 1) return parts.getFirst();
        MutableComponent root = parts.getFirst().getStyle().equals(base)
                ? parts.removeFirst() : Component.empty().setStyle(base);
        parts.forEach(root::append);
        return root;
    }

    private static int tagKind(java.util.regex.Matcher tags) {
        return tags.group(1) != null ? 0 : tags.group(2) != null ? 1 : 2;
    }

    private static void append(List<MutableComponent> parts, String raw, int start, int end,
                               Style style, Effect effect) {
        if (start < end) {
            parts.add(Component.literal(raw.substring(start, end))
                    .setStyle(effect == null ? style : effect.apply(style)));
        }
    }

    private static Prefix prefix(String raw, int amp) {
        int start = amp + 1;
        int cursor = UiTextEffects.endOfSymbols(raw, start);
        if (cursor == raw.length()) return null;
        String symbols = raw.substring(start, cursor);
        if (raw.charAt(cursor) == '-') {
            return symbols.isEmpty() || cursor + 1 == raw.length()
                    ? null : new Prefix(new Effect("", symbols), cursor + 1);
        }
        if (raw.charAt(cursor) != '$') return null;
        int nameStart = UiTextEffects.endOfSymbols(raw, cursor + 1);
        symbols += raw.substring(cursor + 1, nameStart);
        int nameEnd = nameStart;
        while (nameEnd < raw.length() && isNameCharacter(raw.charAt(nameEnd))) nameEnd++;
        int separator = raw.lastIndexOf('-', nameEnd - 1);
        for (int dash = separator; dash >= nameStart; dash = raw.lastIndexOf('-', dash - 1)) {
            String name = raw.substring(nameStart, dash);
            if (UiTextEffects.find(name) != null) {
                return dash + 1 == raw.length() ? null : new Prefix(new Effect(name, symbols), dash + 1);
            }
        }
        if (separator >= nameStart) return null;
        String longest = "";
        for (String name : TextPalettes.names()) {
            if (name.length() > longest.length() && raw.regionMatches(true, nameStart, name, 0, name.length())) {
                longest = name;
            }
        }
        int bodyStart = nameStart + longest.length();
        return longest.isEmpty() || bodyStart == raw.length() ? null
                : new Prefix(new Effect(longest, symbols), bodyStart);
    }

    private static boolean isNameCharacter(char character) {
        return Character.isLetterOrDigit(character) || "_-./:".indexOf(character) >= 0;
    }
}
