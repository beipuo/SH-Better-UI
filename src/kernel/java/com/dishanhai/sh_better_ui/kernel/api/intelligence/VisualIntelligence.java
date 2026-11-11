// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.intelligence;

import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Pure, bounded visual analysis. Pixel evidence and proposed material composites have separate provenance. */
public final class VisualIntelligence {
    private static final int MAX_PIXELS = 4096;
    private VisualIntelligence() {}

    public enum Stage { BACKDROP_PRE_MATERIAL, POST_MATERIAL_PRE_TEXT, FINAL_COMPOSITE }
    public enum Source { GPU_DOWNSAMPLED_PIXELS, CPU_PIXELS, PREDICTED }
    public enum Validity { VALID, PENDING, STALE, UNAVAILABLE }
    public enum Strategy { FOREGROUND, OUTLINE, SHADOW, ISOLATION, BLUR, TINT, OPACITY, CONTRAST_MASK, HIGH_READABILITY }

    /** Framebuffer pixels with an upper-left origin; never GUI units or physical window coordinates. */
    public record PixelRegion(int x, int y, int width, int height) {
        public PixelRegion {
            if (width <= 0 || height <= 0 || (long) x + width > Integer.MAX_VALUE
                    || (long) y + height > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid pixel region");
        }
    }

    public record Statistics(int count, double minimum, double p10, double median, double p90, double maximum,
                             double mean, double variance, int meanArgb, double textureComplexity,
                             List<Integer> colorDistribution) {
        public Statistics { colorDistribution = List.copyOf(colorDistribution); }
        private static final Statistics EMPTY = new Statistics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of());
    }

    /** Bounded immutable region sample. Array access returns a copy; statistics describe the stored pixels. */
    public record BackgroundSample(PixelRegion region, int sampleWidth, int sampleHeight, int[] pixels,
                                   Stage stage, Source source, Validity validity, long frameId,
                                   long capturedNanos, long stateVersion, Statistics statistics) {
        public BackgroundSample {
            Objects.requireNonNull(region); Objects.requireNonNull(stage); Objects.requireNonNull(source);
            Objects.requireNonNull(validity); Objects.requireNonNull(statistics);
            pixels = Objects.requireNonNull(pixels).clone();
            if (pixels.length > MAX_PIXELS || sampleWidth < 0 || sampleHeight < 0
                    || (long) sampleWidth * sampleHeight != pixels.length
                    || validity == Validity.VALID && pixels.length == 0) {
                throw new IllegalArgumentException("Invalid bounded background sample");
            }
            // Caller-supplied statistics must never masquerade as measurements of these pixels.
            statistics = pixels.length == 0 ? Statistics.EMPTY : summarize(sampleWidth, sampleHeight, pixels);
        }
        @Override public int[] pixels() { return pixels.clone(); }

        public static BackgroundSample pixels(PixelRegion region, int width, int height, int[] argb, Stage stage,
                                               Source source, long frameId, long timeNanos, long version) {
            return new BackgroundSample(region, width, height, argb, stage, source, Validity.VALID,
                    frameId, timeNanos, version, Statistics.EMPTY);
        }

        public static BackgroundSample unavailable(PixelRegion region, Stage stage, long frameId, long time, long version) {
            return unavailable(region, stage, Validity.UNAVAILABLE, frameId, time, version);
        }

        public static BackgroundSample unavailable(PixelRegion region, Stage stage, Validity validity,
                                                    long frameId, long time, long version) {
            if (validity == Validity.VALID) throw new IllegalArgumentException("Unavailable sample cannot be valid");
            return new BackgroundSample(region, 0, 0, new int[0], stage, Source.GPU_DOWNSAMPLED_PIXELS,
                    validity, frameId, time, version, Statistics.EMPTY);
        }
    }

    public record TextStyle(int argb, float fontSize, float fontWeight, int outlineArgb, float outlineWidth,
                            int shadowArgb, float shadowRadius) {
        public TextStyle {
            finiteRange(fontSize, .1, 256, "font size"); finiteRange(fontWeight, 1, 1000, "font weight");
            finiteRange(outlineWidth, 0, 8, "outline"); finiteRange(shadowRadius, 0, 16, "shadow");
        }
        public static TextStyle of(int argb) { return new TextStyle(argb, 9, 400, 0, 0, 0, 0); }
        public TextStyle foreground(int color) {
            return new TextStyle(color, fontSize, fontWeight, outlineArgb, outlineWidth, shadowArgb, shadowRadius);
        }
    }

    /** Local protection is applied by the existing material renderer, after its optical pass and before text. */
    public record MaterialSnapshot(GlassMaterial glass, int tintArgb, float opacity, float contrastMask,
                                   boolean highReadability) {
        public MaterialSnapshot {
            Objects.requireNonNull(glass);
            finiteRange(opacity, 0, 1, "local opacity"); finiteRange(contrastMask, 0, 1, "contrast mask");
        }
        public static MaterialSnapshot of(GlassMaterial material) { return new MaterialSnapshot(material, 0, 0, 0, false); }
    }

    public record MaterialConstraints(boolean foreground, boolean outline, boolean shadow, boolean isolation,
                                      boolean blur, boolean tint, boolean opacity, boolean contrastMask,
                                      boolean highReadability, float maxBlur, float maxOpacity) {
        public static final MaterialConstraints DEFAULT = new MaterialConstraints(true, true, true, true, true,
                true, true, true, true, 12, 1);
        public MaterialConstraints {
            finiteRange(maxBlur, 0, 32, "maximum blur"); finiteRange(maxOpacity, 0, 1, "maximum opacity");
        }
    }

    /** Contrast is mathematical; edge/texture/temporal scores are heuristics, never measured glyph recognition. */
    public record LegibilityReport(double computedRatio, double worstRatio, double heuristicScore,
                                    double edgeClarity, double textureInterference, double dynamicStability,
                                    boolean pixelObserved, boolean finalCompositeObserved, boolean valid,
                                    List<String> limitations) {
        public LegibilityReport { limitations = List.copyOf(limitations); }
    }

    public record MaterialResolution(TextStyle text, MaterialSnapshot material, List<Strategy> strategies,
                                     LegibilityReport before, LegibilityReport after, double minimumRatio) {
        public MaterialResolution {
            Objects.requireNonNull(text); Objects.requireNonNull(material); Objects.requireNonNull(before);
            Objects.requireNonNull(after); strategies = List.copyOf(strategies);
            finiteRange(minimumRatio, 1, 21, "minimum ratio");
        }
        public boolean satisfied() { return after.valid() && after.worstRatio() >= minimumRatio; }
    }

    public static LegibilityReport evaluate(TextStyle text, BackgroundSample background, MaterialSnapshot material) {
        Objects.requireNonNull(text); Objects.requireNonNull(background); Objects.requireNonNull(material);
        boolean pixels = background.source() != Source.PREDICTED && background.validity() == Validity.VALID;
        boolean finalPixels = pixels && background.stage() == Stage.FINAL_COMPOSITE;
        if (background.validity() != Validity.VALID || finalPixels || background.stage() == Stage.FINAL_COMPOSITE) {
            return new LegibilityReport(Double.NaN, Double.NaN, 0, 0, 0, 0, pixels, finalPixels, false,
                    List.of(finalPixels || background.stage() == Stage.FINAL_COMPOSITE
                            ? "sh_better_ui.correctness.limitation.final_composite_glyph"
                            : "sh_better_ui.correctness.limitation.background_unavailable"));
        }
        boolean predictedMaterial = background.stage() == Stage.BACKDROP_PRE_MATERIAL;
        var analyzed = predictedMaterial ? composite(background, material) : background;
        double worst = 21, sum = 0;
        for (int color : analyzed.pixels) {
            double ratio = ContrastPolicy.ratio(text.argb(), 0, opaque(color));
            worst = Math.min(worst, ratio); sum += ratio;
        }
        double texture = analyzed.statistics().textureComplexity();
        double sizePenalty = clamp((10 - text.fontSize()) / 12);
        double weightPenalty = clamp((400 - text.fontWeight()) / 600);
        double outlineProtection = text.outlineWidth() > 0 && (text.outlineArgb() >>> 24) > 160 ? .18 : 0;
        double shadowProtection = text.shadowRadius() > 0 && text.shadowRadius() <= 2 ? .06 : 0;
        double edge = clamp(1 - texture * .6 - sizePenalty * .4 - weightPenalty * .2
                - Math.max(0, text.shadowRadius() - 2) * .025 + outlineProtection + shadowProtection);
        double score = clamp(Math.min(1, worst / 7) * .65 + edge * .35);
        var limitations = new ArrayList<String>();
        limitations.add("sh_better_ui.correctness.limitation.glyph_heuristic");
        limitations.add("sh_better_ui.correctness.limitation.temporal_unmeasured");
        if (background.source() == Source.GPU_DOWNSAMPLED_PIXELS) {
            limitations.add("sh_better_ui.correctness.limitation.downsample_extrema");
        }
        if (predictedMaterial || background.source() == Source.PREDICTED) {
            limitations.add("sh_better_ui.correctness.limitation.material_prediction");
        }
        return new LegibilityReport(sum / analyzed.pixels.length, worst, score, edge, texture, Double.NaN,
                pixels && !predictedMaterial, false, true, limitations);
    }

    public static MaterialResolution resolve(TextStyle text, BackgroundSample background, MaterialSnapshot material,
                                               double minimumRatio) {
        return resolve(text, background, material, minimumRatio, MaterialConstraints.DEFAULT);
    }

    /** Deterministic, ordered, local repair with at most nine candidates and sixteen opacity refinements. */
    public static MaterialResolution resolve(TextStyle text, BackgroundSample background, MaterialSnapshot material,
                                               double minimumRatio, MaterialConstraints constraints) {
        finiteRange(minimumRatio, 1, 21, "minimum ratio"); Objects.requireNonNull(constraints);
        var before = evaluate(text, background, material);
        var after = before;
        var steps = new ArrayList<Strategy>();
        if (!before.valid() || before.worstRatio() >= minimumRatio) {
            return new MaterialResolution(text, material, steps, before, after, minimumRatio);
        }
        if (constraints.foreground()) {
            var black = evaluate(text.foreground(0xff000000), background, material);
            var white = evaluate(text.foreground(0xffffffff), background, material);
            int color = black.worstRatio() >= white.worstRatio() ? 0xff000000 : 0xffffffff;
            var best = color == 0xff000000 ? black : white;
            if (best.worstRatio() > after.worstRatio()) {
                text = text.foreground(color); after = best; steps.add(Strategy.FOREGROUND);
            }
        }
        if (after.worstRatio() >= minimumRatio) return new MaterialResolution(text, material, steps, before, after, minimumRatio);
        int backing = luminance(text.argb()) > .4 ? 0xff000000 : 0xffffffff;
        if (constraints.outline()) {
            text = new TextStyle(text.argb(), text.fontSize(), text.fontWeight(), backing,
                    Math.max(1, text.outlineWidth()), text.shadowArgb(), text.shadowRadius());
            steps.add(Strategy.OUTLINE);
        }
        if (constraints.shadow()) {
            text = new TextStyle(text.argb(), text.fontSize(), text.fontWeight(), text.outlineArgb(),
                    text.outlineWidth(), backing, 1);
            steps.add(Strategy.SHADOW);
        }
        // All following composites are predictions, including when their source pixels were measured.
        BackgroundSample base = background.stage() == Stage.BACKDROP_PRE_MATERIAL ? composite(background, material) : background;
        if (constraints.isolation()) {
            material = protection(material, backing, Math.min(.25f, constraints.maxOpacity()), 0, false);
            steps.add(Strategy.ISOLATION);
            after = evaluate(text, composite(base, material), material);
        }
        if (after.worstRatio() < minimumRatio && constraints.blur()) {
            var optics = material.glass().optics();
            float radius = Math.min(constraints.maxBlur(), Math.max(optics.blurRadius(), 4));
            if (radius > optics.blurRadius()) {
                var updated = new GlassOptics(optics.transmission(), optics.refraction(), optics.lensing(),
                        Math.max(.65f, optics.scattering()), optics.edgeHighlight(), optics.thickness(), radius);
                var glass = new GlassMaterial(material.glass().variant(), updated, material.glass().quality(), material.glass().foreground());
                material = new MaterialSnapshot(glass, material.tintArgb(), material.opacity(), material.contrastMask(), false);
                steps.add(Strategy.BLUR);
                // A blur request improves the heuristic only; it cannot prove contrast before actual rendering.
            }
        }
        if (after.worstRatio() < minimumRatio && constraints.tint()) {
            material = protection(material, backing, Math.min(.45f, constraints.maxOpacity()), 0, false);
            steps.add(Strategy.TINT);
            after = evaluate(text, composite(base, material), material);
        }
        if (after.worstRatio() < minimumRatio && constraints.opacity()) {
            float low = material.opacity(), high = Math.min(.90f, constraints.maxOpacity());
            for (int i = 0; i < 16 && high > low; i++) {
                float middle = (low + high) * .5f;
                var candidate = protection(material, backing, middle, 0, false);
                if (evaluate(text, composite(base, candidate), candidate).worstRatio() >= minimumRatio) high = middle;
                else low = middle;
            }
            material = protection(material, backing, high, 0, false);
            steps.add(Strategy.OPACITY);
            after = evaluate(text, composite(base, material), material);
        }
        if (after.worstRatio() < minimumRatio && constraints.contrastMask()) {
            material = protection(material, backing, material.opacity(), Math.min(.97f, constraints.maxOpacity()), false);
            steps.add(Strategy.CONTRAST_MASK);
            after = evaluate(text, composite(base, material), material);
        }
        if (after.worstRatio() < minimumRatio && constraints.highReadability()) {
            material = protection(material, backing, constraints.maxOpacity(), 0, true);
            steps.add(Strategy.HIGH_READABILITY);
            after = evaluate(text, composite(base, material), material);
        }
        return new MaterialResolution(text, material, steps, before, after, minimumRatio);
    }

    private static MaterialSnapshot protection(MaterialSnapshot old, int tint, float opacity, float mask, boolean high) {
        return new MaterialSnapshot(old.glass(), tint, opacity, mask, high);
    }

    private static BackgroundSample composite(BackgroundSample sample, MaterialSnapshot material) {
        int[] values = sample.pixels();
        // These are local color isolation predictions. The shader's optics are not emulated or claimed measured.
        float amount = Math.max(material.opacity(), material.contrastMask());
        int color = material.tintArgb();
        int alpha = Math.round(amount * (color >>> 24));
        int layer = (color & 0xffffff) | alpha << 24;
        for (int i = 0; i < values.length; i++) values[i] = ContrastPolicy.composite(layer, opaque(values[i]));
        return BackgroundSample.pixels(sample.region(), sample.sampleWidth(), sample.sampleHeight(), values,
                Stage.POST_MATERIAL_PRE_TEXT, Source.PREDICTED, sample.frameId(), sample.capturedNanos(), sample.stateVersion());
    }

    /** Durations use nanoseconds. Entry is immediate; conservative exits require continuous clear evidence. */
    public record StabilityPolicy(long minimumHoldNanos, long adjustmentIntervalNanos, long smoothingNanos,
                                   double filterAlpha, double exitMargin) {
        public static final StabilityPolicy DEFAULT = new StabilityPolicy(500_000_000L, 100_000_000L,
                150_000_000L, .25, .8);
        public StabilityPolicy {
            if (minimumHoldNanos < 0 || adjustmentIntervalNanos < 0 || smoothingNanos < 0) {
                throw new IllegalArgumentException("Negative temporal stability duration");
            }
            finiteRange(filterAlpha, .001, 1, "filter alpha"); finiteRange(exitMargin, 0, 10, "exit margin");
        }
    }

    /** One instance per UI node, owned by its UI thread. It never changes the caller's theme or material definitions. */
    public static final class Stabilizer {
        private final StabilityPolicy policy;
        private final MaterialConstraints localSafety;
        private MaterialResolution current, candidate;
        private long changedAt, candidateAt, lastTime = Long.MIN_VALUE;
        private double filteredRatio = Double.NaN;
        public Stabilizer() { this(StabilityPolicy.DEFAULT); }
        public Stabilizer(StabilityPolicy policy) { this(policy, MaterialConstraints.DEFAULT); }
        public Stabilizer(StabilityPolicy policy, MaterialConstraints allowed) {
            this.policy = Objects.requireNonNull(policy); Objects.requireNonNull(allowed);
            localSafety = new MaterialConstraints(false, allowed.outline(), allowed.shadow(), allowed.isolation(),
                    allowed.blur(), allowed.tint(), allowed.opacity(), allowed.contrastMask(), allowed.highReadability(),
                    allowed.maxBlur(), allowed.maxOpacity());
        }

        /** Rechecks the held foreground against new observed pixels before enforcing an exit hold. */
        public MaterialResolution update(MaterialResolution requested, BackgroundSample background, long nowNanos) {
            Objects.requireNonNull(background);
            if (current != null) {
                var held = evaluate(current.text(), background, current.material());
                if (held.valid() && held.worstRatio() < requested.minimumRatio() && requested.satisfied()) {
                    // A rapidly alternating background must not alternate black/white text. First isolate the held
                    // polarity locally. Its result is explicitly a prediction until the native renderer observes it.
                    var protectedHeld = resolve(current.text(), background, current.material(), requested.minimumRatio(), localSafety);
                    current = protectedHeld.satisfied() ? protectedHeld : requested;
                    changedAt = Math.max(nowNanos, lastTime); lastTime = changedAt;
                    candidate = null; return current;
                }
            }
            return update(requested, nowNanos);
        }

        public MaterialResolution update(MaterialResolution requested, long nowNanos) {
            Objects.requireNonNull(requested);
            long now = Math.max(nowNanos, lastTime); lastTime = now;
            double evidence = requested.before().worstRatio();
            if (Double.isFinite(evidence)) filteredRatio = Double.isFinite(filteredRatio)
                    ? filteredRatio + (evidence - filteredRatio) * policy.filterAlpha() : evidence;
            if (current == null) { current = requested; changedAt = now; return current; }
            if (!requested.after().valid()) return current;
            boolean unsafe = requested.before().valid() && evidence < requested.minimumRatio();
            if (unsafe && !sameEffective(current, requested)) {
                current = requested; changedAt = now; candidate = null; return current;
            }
            if (sameEffective(current, requested)) { candidate = null; return current; }
            if (filteredRatio < requested.minimumRatio() + policy.exitMargin()
                    || evidence < requested.minimumRatio() + policy.exitMargin()) {
                candidate = null; return current;
            }
            if (candidate == null || !sameEffective(candidate, requested)) { candidate = requested; candidateAt = now; }
            if (now - changedAt < policy.adjustmentIntervalNanos() || now - candidateAt < policy.minimumHoldNanos()) return current;
            // Text polarity changes atomically: interpolating black/white would cross an unreadable gray.
            // Material opacity only moves after the hold and remains at least as protective as the requested value.
            double fraction = policy.smoothingNanos() == 0 ? 1
                    : Math.min(1, (now - candidateAt - policy.minimumHoldNanos()) / (double) policy.smoothingNanos());
            var from = current.material(); var to = requested.material();
            float opacity = (float) (from.opacity() + (to.opacity() - from.opacity()) * fraction);
            float mask = (float) (from.contrastMask() + (to.contrastMask() - from.contrastMask()) * fraction);
            var blended = new MaterialSnapshot(to.glass(), to.tintArgb(), Math.max(to.opacity(), opacity),
                    Math.max(to.contrastMask(), mask), to.highReadability());
            current = new MaterialResolution(requested.text(), blended, requested.strategies(), requested.before(),
                    requested.after(), requested.minimumRatio());
            changedAt = now;
            if (fraction >= 1) candidate = null;
            return current;
        }
        public double filteredRatio() { return filteredRatio; }
        public void reset() { current = null; candidate = null; filteredRatio = Double.NaN; lastTime = Long.MIN_VALUE; }
        private static boolean sameEffective(MaterialResolution a, MaterialResolution b) {
            return a.text().equals(b.text()) && a.material().equals(b.material());
        }
    }

    private static Statistics summarize(int width, int height, int[] pixels) {
        double[] levels = new double[pixels.length];
        double sum = 0, squares = 0, edges = 0; long r = 0, g = 0, b = 0; int neighbors = 0;
        int[] histogram = new int[64];
        for (int i = 0; i < pixels.length; i++) {
            int color = opaque(pixels[i]); double level = luminance(color); levels[i] = level;
            sum += level; squares += level * level; r += color >>> 16 & 255; g += color >>> 8 & 255; b += color & 255;
            histogram[((color >>> 22) & 3) << 4 | ((color >>> 14) & 3) << 2 | ((color >>> 6) & 3)]++;
            if (i % width > 0) { edges += Math.abs(level - levels[i - 1]); neighbors++; }
            if (i >= width) { edges += Math.abs(level - levels[i - width]); neighbors++; }
        }
        Arrays.sort(levels); int n = levels.length; double mean = sum / n;
        int color = 0xff000000 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
        return new Statistics(n, levels[0], quantile(levels, .1), quantile(levels, .5), quantile(levels, .9),
                levels[n - 1], mean, Math.max(0, squares / n - mean * mean), color,
                neighbors == 0 ? 0 : clamp(edges / neighbors), Arrays.stream(histogram).boxed().toList());
    }
    private static double quantile(double[] values, double quantile) {
        double index = quantile * (values.length - 1); int lower = (int) index; int upper = Math.min(values.length - 1, lower + 1);
        return values[lower] + (values[upper] - values[lower]) * (index - lower);
    }
    private static int opaque(int color) { return color | 0xff000000; }
    /** Relative linear-light luminance of the RGB channels; composite transparency over a known backdrop first. */
    public static double luminance(int color) {
        return .2126 * ColorValue.linear(color >>> 16 & 255) + .7152 * ColorValue.linear(color >>> 8 & 255)
                + .0722 * ColorValue.linear(color & 255);
    }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private static void finiteRange(double value, double low, double high, String name) {
        if (!Double.isFinite(value) || value < low || value > high) throw new IllegalArgumentException("Invalid " + name);
    }
}
