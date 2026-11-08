package com.dishanhai.sh_better_ui.client.gui.text;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 山海：兼容 GT 山海格式码的色板与逐字效果注册入口。 */
public final class UiTextEffects {
    private static final String MARKER = "shbui:fcs:";
    private static final String LEGACY_MARKER = "fcs:";
    private static final String BUILT_IN_NAMESPACE = "sh_better_ui:";
    private static final String SYMBOLS = "#~*%@!^?+>`";
    private static final Map<String, Format> EXTERNAL = new ConcurrentHashMap<>();

    record Format(TextPalettes.Palette palette, String symbols, Effect effect) {}

    /** 字符索引与长度均按当前效果段的 Unicode 码点计数。 */
    public record GlyphContext(int rgb, int index, int length, int codePoint, long timeMillis,
                               boolean reducedMotion) {}

    /** RGB 不含 alpha，位移使用 GUI 逻辑单位。 */
    public record Glyph(int rgb, float x, float y, boolean outline, boolean obfuscated) {
        public Glyph {
            rgb &= 0xFFFFFF;
            if (!Float.isFinite(x) || !Float.isFinite(y)) {
                throw new IllegalArgumentException("Glyph offsets must be finite");
            }
        }
    }

    /** 实现在渲染线程执行，且可能按每个字形调用。 */
    @FunctionalInterface
    public interface Effect {
        Glyph sample(GlyphContext context);
    }

    private UiTextEffects() {}

    public static void registerPalette(ResourceLocation id, int... rgb) {
        registerPalette(id, false, rgb);
    }

    /** 注册循环色板。正文模式使用较慢的过渡速度。 */
    public static synchronized void registerPalette(ResourceLocation id, boolean body, int... rgb) {
        int[] stops = rgb.clone();
        if (stops.length == 0) throw new IllegalArgumentException("Palette must contain a color");
        for (int stop : stops) {
            if ((stop & 0xFF000000) != 0) throw new IllegalArgumentException("Palette colors must be RGB");
        }
        TextPalettes.Palette palette = new TextPalettes.Palette(stops, body, TextPalettes.Mode.CYCLE, false);
        register(id, new Format(palette, "", null));
        TextPalettes.addFlowingPalette(palette);
    }

    public static synchronized void registerEffect(ResourceLocation id, Effect effect) {
        register(id, new Format(null, "", Objects.requireNonNull(effect, "effect")));
    }

    private static void register(ResourceLocation id, Format format) {
        String key = Objects.requireNonNull(id, "id").toString();
        if (find(key) != null) throw new IllegalArgumentException("Text effect already registered: " + key);
        EXTERNAL.put(key, format);
    }

    public static Component apply(Component text, ResourceLocation id) {
        return apply(text, id, "");
    }

    /** 将注册项与可选的 GT 效果符号写入组件根样式的 insertion，保留其余样式和子组件。 */
    public static Component apply(Component text, ResourceLocation id, String symbols) {
        String key = Objects.requireNonNull(id, "id").toString();
        return find(key) == null ? text : mark(text, key, checkedSymbols(symbols));
    }

    /** 对原生组件单独应用 GT 效果符号。 */
    public static Component apply(Component text, String symbols) {
        String checked = checkedSymbols(symbols);
        return checked.isEmpty() ? text : mark(text, "", checked);
    }

    public static Component parse(String raw) {
        return parse(raw, Style.EMPTY);
    }

    /** 解析第一个有效的内联前缀，保留前面的普通文字与无法识别的格式码。 */
    public static Component parse(String raw, Style style) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(style, "style");
        Inline inline = findInline(raw);
        if (inline == null) return Component.literal(raw).setStyle(style);
        Component body = mark(Component.literal(raw.substring(inline.start())).setStyle(style),
                inline.name(), inline.symbols());
        if (inline.prefixEnd() == 0) return body;
        return Component.literal(raw.substring(0, inline.prefixEnd())).setStyle(style).append(body);
    }

    /**
     * 在量宽或换行之前处理组件各样式段中的旧格式码。
     * 如发生转换，结果是当前语言下的快照，切换语言后请从原组件重新处理。
     */
    public static Component normalize(Component component) {
        boolean hasCode = component.visit((style, text) -> findInline(text) == null
                ? Optional.empty() : Optional.of(Boolean.TRUE), Style.EMPTY).isPresent();
        if (!hasCode) return component;
        MutableComponent normalized = Component.empty();
        component.visit((style, text) -> {
            normalized.append(parse(text, style));
            return Optional.empty();
        }, Style.EMPTY);
        return normalized;
    }

    private static Inline findInline(String raw) {
        for (int amp = raw.indexOf('&'); amp >= 0; amp = raw.indexOf('&', amp + 1)) {
            Inline inline = parseInline(raw, amp);
            if (inline != null) return inline;
        }
        return null;
    }

    private record Inline(String name, String symbols, int prefixEnd, int start) {}

    private static Inline parseInline(String raw, int amp) {
        int start = amp + 1;
        int length = raw.length();
        int cursor = endOfSymbols(raw, start);
        String symbols = raw.substring(start, cursor);
        if (cursor >= length) return null;
        if (raw.charAt(cursor) == '-') {
            return symbols.isEmpty() || cursor + 1 == length ? null : new Inline("", symbols, amp, cursor + 1);
        }
        if (raw.charAt(cursor) != '$') return null;
        int nameStart = endOfSymbols(raw, cursor + 1);
        symbols += raw.substring(cursor + 1, nameStart);
        int nameEnd = nameStart;
        while (nameEnd < length && isNameCharacter(raw.charAt(nameEnd))) nameEnd++;
        int separator = raw.lastIndexOf('-', nameEnd - 1);
        if (separator >= nameStart) {
            for (int dash = separator; dash >= nameStart; dash = raw.lastIndexOf('-', dash - 1)) {
                String name = raw.substring(nameStart, dash);
                if (dash + 1 < length && find(name) != null) return new Inline(name, symbols, amp, dash + 1);
            }
            return null;
        }
        // 旧紧凑格式依照已知色板名的最长前缀识别。
        String longest = "";
        for (String name : TextPalettes.names()) {
            if (name.length() > longest.length()
                    && raw.regionMatches(true, nameStart, name, 0, name.length())) longest = name;
        }
        int bodyStart = nameStart + longest.length();
        return longest.isEmpty() || bodyStart == length ? null : new Inline(longest, symbols, amp, bodyStart);
    }

    private static int endOfSymbols(String text, int start) {
        int cursor = start;
        while (cursor < text.length() && SYMBOLS.indexOf(text.charAt(cursor)) >= 0) cursor++;
        return cursor;
    }

    private static boolean isNameCharacter(char character) {
        return Character.isLetterOrDigit(character) || "_-./:".indexOf(character) >= 0;
    }

    private static String checkedSymbols(String symbols) {
        Objects.requireNonNull(symbols, "symbols");
        if (endOfSymbols(symbols, 0) != symbols.length()) {
            throw new IllegalArgumentException("Unknown text effect symbol: " + symbols);
        }
        return symbols;
    }

    private static Component mark(Component text, String name, String symbols) {
        return text.copy().withStyle(style -> style.withInsertion(MARKER + name + "|" + symbols));
    }

    private static Format find(String rawName) {
        String name = rawName.toLowerCase(Locale.ROOT);
        Format external = EXTERNAL.get(name);
        if (external != null) return external;
        if (name.startsWith(BUILT_IN_NAMESPACE)) name = name.substring(BUILT_IN_NAMESPACE.length());
        TextPalettes.Palette palette = TextPalettes.get(name);
        return palette == null ? null : new Format(palette, "", null);
    }

    static Format format(Style style) {
        String insertion = style.getInsertion();
        if (insertion == null) return null;
        String marker;
        if (insertion.startsWith(MARKER)) marker = MARKER;
        else if (insertion.startsWith(LEGACY_MARKER)) marker = LEGACY_MARKER;
        else return null;

        int separator = insertion.indexOf('|', marker.length());
        String name = separator < 0 ? insertion.substring(marker.length())
                : insertion.substring(marker.length(), separator);
        Format registered = name.isEmpty() ? null : find(name);
        if (!name.isEmpty() && registered == null) return null;

        StringBuilder symbols = new StringBuilder();
        if (separator >= 0) {
            for (int i = 0; i < SYMBOLS.length(); i++) {
                char symbol = SYMBOLS.charAt(i);
                if (insertion.indexOf(symbol, separator + 1) >= 0) symbols.append(symbol);
            }
        }
        if (registered == null && symbols.isEmpty()) return null;
        return new Format(registered == null ? null : registered.palette(), symbols.toString(),
                registered == null ? null : registered.effect());
    }

    static Glyph sample(Format format, int rgb, int index, int length, int codePoint,
                        long timeMillis, boolean reducedMotion) {
        long time = reducedMotion ? 0 : timeMillis;
        TextPalettes.Palette palette = format.palette();
        TextPalettes.RandomFrame random = null;
        if (palette != null) {
            if (palette.mode() == TextPalettes.Mode.RANDOM) {
                random = TextPalettes.randomFrame(time);
                rgb = TextPalettes.sampleRandom(random, index, length);
            } else {
                rgb = TextPalettes.color(palette, index, length, time);
            }
        }

        Glyph glyph = TextGlyphEffects.sample(format.symbols(), palette, random, rgb, index, length, time,
                reducedMotion);
        if (format.effect() == null) return glyph;
        Glyph custom = Objects.requireNonNull(format.effect().sample(
                new GlyphContext(glyph.rgb(), index, length, codePoint, time, reducedMotion)), "effect result");
        return new Glyph(custom.rgb(), reducedMotion ? 0 : glyph.x() + custom.x(),
                reducedMotion ? 0 : glyph.y() + custom.y(), glyph.outline() || custom.outline(),
                !reducedMotion && (glyph.obfuscated() || custom.obfuscated()));
    }
}
