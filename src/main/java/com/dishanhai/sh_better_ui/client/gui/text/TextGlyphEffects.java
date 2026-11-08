package com.dishanhai.sh_better_ui.client.gui.text;

/** 山海：移植自 gt_shanhai 的显式和随机逐字效果。 */
final class TextGlyphEffects {
    private TextGlyphEffects() {}

    static UiTextEffects.Glyph sample(String symbols, TextPalettes.Palette palette,
                                      TextPalettes.RandomFrame random, int rgb, int index,
                                      int length, long time, boolean reducedMotion) {
        boolean outline = has(symbols, '#');
        if (reducedMotion) return new UiTextEffects.Glyph(rgb, 0, 0, outline, false);

        float x = 0;
        float y = 0;
        if (has(symbols, '@')) {
            float[] circle = calcCircleOffset(index, time);
            x += circle[0];
            y += circle[1];
        }
        if (has(symbols, '~')) y += calcWaveY(index, time);
        if (has(symbols, '*')) x += calcWaveX(index, time);
        if (has(symbols, '%')) {
            float[] shake = calcShakeOffset(index, time);
            x += shake[0];
            y += shake[1];
        }
        if (has(symbols, '!')) y += calcBounceY(index, time);
        if (has(symbols, '^')) rgb = applyScanColor(rgb, index, length, time);
        if (has(symbols, '?')) {
            x += calcGlitchX(index, time);
            y += calcGlitchY(index, time);
            rgb = applyGlitchColor(rgb, index, time);
        }
        if (has(symbols, '+')) rgb = applyBreatheColor(rgb, index, time);
        if (has(symbols, '>')) rgb = applyChaseColor(rgb, index, length, time);

        boolean obfuscated = has(symbols, '`');
        if (palette != null) {
            obfuscated |= palette.obfuscated() || palette.mode() == TextPalettes.Mode.DISTORT
                    && Math.floorMod(time + index * 131L, 400) < 120;
            if (palette.mode() == TextPalettes.Mode.WOBBLE) {
                y += (float) (Math.sin(index * 0.7 + time * 0.008) * 3);
            }
        }
        if (random == null || random.strength() <= 0) return new UiTextEffects.Glyph(rgb, x, y, outline, obfuscated);
        float[] offset = randomOffset(random.motion(), index, random.motionTime());
        x += offset[0] * random.strength();
        y += offset[1] * random.strength();
        int color = randomColor(random.motion(), rgb, index, length, random.motionTime());
        return new UiTextEffects.Glyph(mixColor(rgb, color, random.strength()), x, y, outline, obfuscated);
    }

    private static boolean has(String symbols, char symbol) {
        return symbols.indexOf(symbol) >= 0;
    }

    private static float[] randomOffset(TextPalettes.RandomMotion motion, int index, long time) {
        return switch (motion) {
            case REVERSE, TIME_JUMP, WAVE_Y -> new float[]{0, calcWaveY(index, time)};
            case WAVE_X -> new float[]{calcWaveX(index, time), 0};
            case SHAKE -> calcShakeOffset(index, time);
            case CIRCLE -> calcCircleOffset(index, time);
            case BOUNCE -> new float[]{0, calcBounceY(index, time)};
            case GLITCH -> new float[]{calcGlitchX(index, time), calcGlitchY(index, time)};
            default -> new float[]{0, 0};
        };
    }

    private static int randomColor(TextPalettes.RandomMotion motion, int rgb, int index, int length, long time) {
        return switch (motion) {
            case SCAN -> applyScanColor(rgb, index, length, time);
            case BREATHE -> applyBreatheColor(rgb, index, time);
            case CHASE -> applyChaseColor(rgb, index, length, time);
            case GLITCH -> applyGlitchColor(rgb, index, time);
            case TWINKLE -> mixColor(rgb, 0xFFFFFF,
                    (float) Math.pow(Math.max(0, Math.sin(time * 0.012 + index * 2.4)), 8));
            default -> rgb;
        };
    }

    private static float[] calcCircleOffset(int charIndex, long time) {
        double angle = time * 0.003 + charIndex * 0.5;
        return new float[]{
            (float)(Math.cos(angle) * 3.0),
            (float)(Math.sin(angle) * 3.0)
        };
    }

    private static float calcWaveY(int charIndex, long time) {
        return (float)(Math.sin(charIndex * 0.5 + time * 0.002) * 3.0);
    }

    private static float calcWaveX(int charIndex, long time) {
        return (float)(Math.cos(charIndex * 0.7 + time * 0.0015) * 2.0);
    }

    private static float[] calcShakeOffset(int charIndex, long time) {
        double seed = charIndex * 4729;
        return new float[]{
            (float)(Math.sin(time * 0.02 + seed * 0.001) * (seed % 100) * 0.01),
            (float)(Math.cos(time * 0.02 * 1.7 + seed * 0.002) * (seed % 150) * 0.008)
        };
    }

    private static float calcBounceY(int charIndex, long time) {
        return (float)Math.abs(Math.sin(time * 0.005 + charIndex * 1.2)) * 4.0f;
    }

    private static float calcGlitchX(int charIndex, long time) {
        long phase = (time / 55L) + charIndex * 17L;
        if ((phase % 7L) > 1L) return 0.0f;
        return ((phase % 3L) - 1L) * 1.5f;
    }

    private static float calcGlitchY(int charIndex, long time) {
        long phase = (time / 65L) + charIndex * 23L;
        if ((phase % 9L) > 1L) return 0.0f;
        return ((phase % 3L) - 1L) * 1.0f;
    }

    private static int applyScanColor(int color, int charIndex, int totalLen, long time) {
        if (totalLen <= 0) return color;
        int period = Math.max(totalLen + 8, 12);
        int pos = (int)((time / 45L) % period) - 4;
        int dist = Math.abs(charIndex - pos);
        if (dist > 3) return color;
        float strength = (4.0f - dist) / 4.0f;
        return mixColor(color, 0xFFFFFF, strength * 0.75f);
    }

    private static int applyBreatheColor(int color, int charIndex, long time) {
        double wave = Math.sin(time * 0.003 + charIndex * 0.18);
        float strength = (float)((wave + 1.0D) * 0.22D);
        return mixColor(color, 0xFFFFFF, strength);
    }

    private static int applyChaseColor(int color, int charIndex, int totalLen, long time) {
        if (totalLen <= 0) return color;
        int period = Math.max(totalLen, 1);
        int pos = (int)((time / 70L) % period);
        int dist = Math.abs(charIndex - pos);
        dist = Math.min(dist, period - dist);
        if (dist > 2) return color;
        float strength = (3.0f - dist) / 3.0f;
        return mixColor(color, 0xFFFFFF, strength * 0.9f);
    }

    private static int applyGlitchColor(int color, int charIndex, long time) {
        long phase = (time / 60L) + charIndex * 31L;
        if ((phase % 11L) == 0L) return mixColor(color, 0x55FFFF, 0.65f);
        if ((phase % 13L) == 0L) return mixColor(color, 0xFF5555, 0.55f);
        return color;
    }

    private static int mixColor(int base, int target, float amount) {
        if (amount <= 0.0f) return base;
        if (amount > 1.0f) amount = 1.0f;
        int br = (base >> 16) & 0xFF;
        int bg = (base >> 8) & 0xFF;
        int bb = base & 0xFF;
        int tr = (target >> 16) & 0xFF;
        int tg = (target >> 8) & 0xFF;
        int tb = target & 0xFF;
        int r = (int)(br + (tr - br) * amount);
        int g = (int)(bg + (tg - bg) * amount);
        int b = (int)(bb + (tb - bb) * amount);
        return (r << 16) | (g << 8) | b;
    }
}
