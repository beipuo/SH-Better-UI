// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.palette;

import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;

import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** Bounded, deterministic image clustering and tonal palette generation. */
public final class ThemePaletteGenerator {
    public static final int MAX_PIXELS = 4_194_304;
    private static final int MAX_SAMPLES = 4096;
    private static final int MAX_CACHED = 64;
    private static final ColorValue FALLBACK = ColorValue.argb(0xff6699ff);
    private final LinkedHashMap<Key, ThemePalette> cache = new LinkedHashMap<>(16, .75f, true);

    /** Generates from a fixed color or an environment/third-party color sampled on its owner thread. */
    public synchronized ThemePalette fromSeed(ColorValue seed, boolean light) {
        int opaque = Objects.requireNonNull(seed).argb() | 0xff000000;
        if (chroma(opaque) < 18) opaque = FALLBACK.argb();
        var key = new Key(opaque, light);
        var cached = cache.get(key);
        if (cached != null) return cached;
        var palette = tonal(ColorValue.argb(opaque), light);
        cache.put(key, palette);
        if (cache.size() > MAX_CACHED) cache.remove(cache.keySet().iterator().next());
        return palette;
    }

    /** Runs source extraction and generation in the supplied worker. No game objects should be captured. */
    public CompletableFuture<ThemePalette> generate(PaletteSource source, boolean light, Executor worker) {
        Objects.requireNonNull(source);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return fromSeed(source.seed(), light);
            } catch (Exception failure) {
                throw new CompletionException(failure);
            }
        }, worker);
    }

    /** Extracts a dominant chromatic cluster from bounded, unpremultiplied ARGB pixels. */
    public ColorValue extract(int width, int height, int[] argb) {
        long pixelCount = (long) width * height;
        if (width <= 0 || height <= 0 || pixelCount > MAX_PIXELS || pixelCount != argb.length) {
            throw new IllegalArgumentException("Invalid palette image dimensions: " + width + " x " + height);
        }
        int count = Math.min(argb.length, MAX_SAMPLES);
        int columns = Math.min(Math.min(width, count), Math.max(1, (int) Math.sqrt((double) count * width / height)));
        int rows = Math.min(height, Math.max(1, count / columns));
        var clusters = new LinkedHashMap<Integer, Cluster>();
        for (int row = 0; row < rows; row++) {
            int y = (int) ((row + 0.5) * height / rows);
            for (int column = 0; column < columns; column++) {
                int x = (int) ((column + 0.5) * width / columns);
                int color = argb[y * width + x];
                int alpha = color >>> 24;
                if (alpha < 64 || chroma(color) < 18) continue;
                int red = (color >>> 16) & 255, green = (color >>> 8) & 255, blue = color & 255;
                int bucket = (red >>> 4) << 8 | (green >>> 4) << 4 | blue >>> 4;
                clusters.computeIfAbsent(bucket, ignored -> new Cluster()).add(red, green, blue, alpha);
            }
        }
        Cluster dominant = null;
        for (var candidate : clusters.values()) {
            if (dominant == null || candidate.weight > dominant.weight) dominant = candidate;
        }
        return dominant == null ? FALLBACK : dominant.color();
    }

    private static ThemePalette tonal(ColorValue seed, boolean light) {
        ColorValue base = ColorValue.argb(light ? 0xfffcfdff : 0xff10131a);
        ColorValue background = ColorValue.interpolateOklab(base, seed, light ? .07 : .09);
        ColorValue surface = ColorValue.interpolateOklab(
                ColorValue.argb(light ? 0xffffffff : 0xff202531), seed, light ? .09 : .14);
        ColorValue primary = ColorValue.interpolateOklab(seed,
                ColorValue.argb(light ? 0xff000000 : 0xffffffff), light ? .22 : .32);
        ColorValue secondary = ColorValue.interpolateOklab(rotate(seed, 24), base, .22);
        ColorValue accent = ColorValue.interpolateOklab(rotate(seed, -16),
                ColorValue.argb(light ? 0xff000000 : 0xffffffff), light ? .16 : .35);
        ColorValue text = readable(light ? 0xff162032 : 0xfff5f7fb, surface, background);
        ColorValue onPrimary = readable(light ? 0xffffffff : 0xff131722, primary, background);
        return new ThemePalette(light, primary, onPrimary, secondary, accent, background, surface, text);
    }

    private static ColorValue readable(int ink, ColorValue fill, ColorValue background) {
        return ColorValue.argb(ContrastPolicy.readable(ink, fill.argb(), background.argb(), ContrastPolicy.TEXT_RATIO));
    }

    private static ColorValue rotate(ColorValue seed, double degrees) {
        double r = ((seed.argb() >>> 16) & 255) / 255.0;
        double g = ((seed.argb() >>> 8) & 255) / 255.0;
        double b = (seed.argb() & 255) / 255.0;
        double min = Math.min(r, Math.min(g, b)), max = Math.max(r, Math.max(g, b));
        double spread = max - min;
        if (spread == 0) return seed;
        double sector = max == r ? (g - b) / spread : max == g ? 2 + (b - r) / spread : 4 + (r - g) / spread;
        double hue = (sector + degrees / 60 + 6) % 6;
        double x = spread * (1 - Math.abs(hue % 2 - 1));
        double[] channels = switch ((int) hue) {
            case 0 -> new double[]{spread, x, 0};
            case 1 -> new double[]{x, spread, 0};
            case 2 -> new double[]{0, spread, x};
            case 3 -> new double[]{0, x, spread};
            case 4 -> new double[]{x, 0, spread};
            default -> new double[]{spread, 0, x};
        };
        return ColorValue.rgb(channel(channels[0] + min), channel(channels[1] + min), channel(channels[2] + min));
    }

    private static int channel(double unit) { return (int) Math.round(unit * 255); }

    private static int chroma(int argb) {
        int r = (argb >>> 16) & 255, g = (argb >>> 8) & 255, b = argb & 255;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }

    private record Key(int seed, boolean light) {}

    private static final class Cluster {
        private int weight, red, green, blue;

        void add(int r, int g, int b, int alpha) {
            weight += alpha;
            red += r * alpha;
            green += g * alpha;
            blue += b * alpha;
        }

        ColorValue color() {
            return ColorValue.rgb(red / weight, green / weight, blue / weight);
        }
    }
}
