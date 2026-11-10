// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.intelligence;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry;

import java.util.List;
import java.util.Objects;

import static com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence.*;

/** Declarative, loader-independent intent; text is a localization key, never a native Component. */
public record UIElement(UiId id, UiId parent, State state, Constraints constraints, VisualPolicy visualPolicy,
                        MotionPolicy motionPolicy, UICorrectnessContract contract,
                        InteractionPolicy interactionPolicy, Text text, Rect preferred,
                        double padding, List<Breakpoint> breakpoints) implements UINode {
    public record Breakpoint(double minimumViewportWidth, Constraints constraints) {
        public Breakpoint {
            Objects.requireNonNull(constraints);
            if (!Double.isFinite(minimumViewportWidth) || minimumViewportWidth < 0) throw new IllegalArgumentException("Invalid breakpoint");
        }
    }
    public record State(boolean visible, boolean enabled, long contentVersion) {
        public State { if (contentVersion < 0) throw new IllegalArgumentException("Negative content version"); }
        public static final State DEFAULT = new State(true, true, 0);
    }
    public record Text(String translationKey, List<String> arguments, double measuredWidth, double measuredHeight) {
        public static final Text EMPTY = new Text("", List.of(), 0, 0);
        public Text {
            Objects.requireNonNull(translationKey);
            arguments = List.copyOf(arguments);
            int characters = 0;
            for (var argument : arguments) {
                characters += Objects.requireNonNull(argument).length();
                if (characters > 32768) throw new IllegalArgumentException("UI text arguments exceed the content budget");
            }
            if (translationKey.length() > 256 || arguments.size() > 32 || !Double.isFinite(measuredWidth)
                    || !Double.isFinite(measuredHeight) || measuredWidth < 0 || measuredHeight < 0) {
                throw new IllegalArgumentException("Invalid text measurement");
            }
        }
    }
    public record VisualPolicy(GlassMaterial glass, VisualIntelligence.TextStyle text, double minimumContrast,
                               boolean adaptive, float radius, float shadowExtent, int z, String foregroundToken) {
        public VisualPolicy {
            Objects.requireNonNull(glass);
            Objects.requireNonNull(text);
            Objects.requireNonNull(foregroundToken);
            if (foregroundToken.length() > 256) throw new IllegalArgumentException("Invalid theme token ID");
            if (!Double.isFinite(minimumContrast) || minimumContrast < 1 || minimumContrast > 21
                    || !Float.isFinite(radius) || radius < 0 || radius > 128
                    || !Float.isFinite(shadowExtent) || shadowExtent < 0 || shadowExtent > 128
                    || z < -1000 || z > 1000) throw new IllegalArgumentException("Invalid visual policy");
        }
        public static VisualPolicy adaptiveGlass() {
            return new VisualPolicy(GlassMaterial.preset(GlassVariant.REGULAR), VisualIntelligence.TextStyle.of(0xffffffff),
                    4.5, true, 12, 8, 0, "sh_better_ui:text.primary");
        }
        public VisualPolicy(GlassMaterial glass, VisualIntelligence.TextStyle text, double minimumContrast,
                            boolean adaptive, float radius, float shadowExtent, int z) {
            this(glass, text, minimumContrast, adaptive, radius, shadowExtent, z, "");
        }
    }
    /** A prediction is conditional until a trajectory is actually used by a consumer's motion adapter. */
    public record MotionPolicy(boolean interruptible, double horizonSeconds, Trajectory trajectory) {
        public static final MotionPolicy STILL = new MotionPolicy(false, .25, null);
        public static MotionPolicy animated() { return new MotionPolicy(true, .25, null); }
        public MotionPolicy {
            if (!Double.isFinite(horizonSeconds) || horizonSeconds < 0 || horizonSeconds > 10) {
                throw new IllegalArgumentException("Invalid prediction horizon");
            }
        }
    }
    public record InteractionPolicy(boolean interactive, boolean critical, boolean allowIndependentHitGeometry,
                                    boolean blocksInput, String accessibleNameKey) {
        public static final InteractionPolicy NONE = new InteractionPolicy(false, false, false, false, "");
        public InteractionPolicy { Objects.requireNonNull(accessibleNameKey); }
    }
    public UIElement {
        Objects.requireNonNull(id); Objects.requireNonNull(state); Objects.requireNonNull(constraints);
        Objects.requireNonNull(visualPolicy); Objects.requireNonNull(motionPolicy); Objects.requireNonNull(contract);
        Objects.requireNonNull(interactionPolicy); Objects.requireNonNull(text); Objects.requireNonNull(preferred);
        if (!Double.isFinite(padding) || padding < 0 || padding > 1024 || breakpoints.size() > 16) throw new IllegalArgumentException("Invalid padding or breakpoints");
        breakpoints = breakpoints.stream().sorted(java.util.Comparator.comparingDouble(Breakpoint::minimumViewportWidth)).toList();
        if (id.equals(parent)) throw new IllegalArgumentException("A UI node cannot parent itself");
        if (preferred.space() != Space.GUI && preferred.space() != Space.LOCAL) {
            throw new IllegalArgumentException("UI intent requires GUI or parent-local coordinates");
        }
        if (preferred.space() == Space.LOCAL && parent == null) throw new IllegalArgumentException("Local coordinates require a parent");
    }
    public Constraints constraintsFor(double viewportWidth) {
        var result = constraints;
        for (var rule : breakpoints) if (viewportWidth >= rule.minimumViewportWidth()) result = rule.constraints();
        return result;
    }
    public UIElement(UiId id, UiId parent, State state, Constraints constraints, VisualPolicy visualPolicy,
                     MotionPolicy motionPolicy, UICorrectnessContract contract, InteractionPolicy interactionPolicy,
                     Text text, Rect preferred) {
        this(id, parent, state, constraints, visualPolicy, motionPolicy, contract, interactionPolicy, text, preferred, 8, List.of());
    }
    public static Builder builder(String id) { return new Builder(UiId.parse(id)); }
    public Builder toBuilder() { return new Builder(this); }
    public static final class Builder {
        private final UiId id;
        private UiId parent;
        private State state = State.DEFAULT;
        private Anchor anchor = Anchor.TOP_LEFT;
        private double minW = 1, minH = 1, w = 120, h = 36, maxW = 32768, maxH = 32768, margin = 4;
        private int priority = 50;
        private boolean safe = true, avoid = true;
        private VisualPolicy visual = VisualPolicy.adaptiveGlass();
        private MotionPolicy motion = MotionPolicy.STILL;
        private UICorrectnessContract contract = UICorrectnessContract.ACCESSIBLE;
        private InteractionPolicy interaction = InteractionPolicy.NONE;
        private Text text = Text.EMPTY;
        private Rect preferred = new Rect(0, 0, 120, 36, Space.GUI);
        private double padding = 8;
        private final java.util.ArrayList<Breakpoint> breakpoints = new java.util.ArrayList<>();
        private Builder(UiId id) { this.id = id; }
        private Builder(UIElement source) {
            this(source.id); parent = source.parent; state = source.state; visual = source.visualPolicy;
            motion = source.motionPolicy; contract = source.contract; interaction = source.interactionPolicy;
            text = source.text; preferred = source.preferred;
            padding = source.padding; breakpoints.addAll(source.breakpoints);
            var c = source.constraints;
            anchor = c.anchor(); minW = c.minW(); minH = c.minH(); w = c.preferredW(); h = c.preferredH();
            maxW = c.maxW(); maxH = c.maxH(); margin = c.margin(); priority = c.priority();
            safe = c.respectSafeArea(); avoid = c.avoidOverlap();
        }
        public Builder parent(UiId value) { parent = value; return this; }
        public Builder state(State value) { state = value; return this; }
        public Builder anchor(Anchor value) { anchor = value; return this; }
        public Builder preferredSize(double width, double height) { w = width; h = height; return this; }
        public Builder minSize(double width, double height) { minW = width; minH = height; return this; }
        public Builder maxSize(double width, double height) { maxW = width; maxH = height; return this; }
        public Builder margin(double value) { margin = value; return this; }
        public Builder padding(double value) { padding = value; return this; }
        public Builder breakpoint(double minimumViewportWidth, Constraints constraints) {
            breakpoints.add(new Breakpoint(minimumViewportWidth, constraints)); return this;
        }
        public Builder priority(int value) { priority = value; return this; }
        public Builder respectSafeArea(boolean value) { safe = value; return this; }
        public Builder avoidOverlap(boolean value) { avoid = value; return this; }
        public Builder visualPolicy(VisualPolicy value) { visual = value; return this; }
        public Builder motion(MotionPolicy value) { motion = value; return this; }
        public Builder correctness(UICorrectnessContract value) { contract = value; return this; }
        public Builder interaction(InteractionPolicy value) { interaction = value; return this; }
        public Builder interactive(boolean value) {
            interaction = new InteractionPolicy(value, interaction.critical(), interaction.allowIndependentHitGeometry(),
                    interaction.blocksInput(), interaction.accessibleNameKey()); return this;
        }
        public Builder text(String key) { text = new Text(key, List.of(), 0, 0); return this; }
        public Builder text(Text value) { text = value; return this; }
        public Builder preferredBounds(Rect value) { preferred = value; return this; }
        public UIElement build() {
            var c = new Constraints(anchor, minW, minH, w, h, maxW, maxH, margin, priority, safe, avoid);
            return new UIElement(id, parent, state, c, visual, motion, contract, interaction, text, preferred, padding, breakpoints);
        }
    }
}
