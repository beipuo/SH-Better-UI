// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.intelligence;

import com.dishanhai.sh_better_ui.kernel.api.*;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/** A built-in capability of the existing UnifiedUI kernel. Mutations require its owner thread and owned scopes. */
public interface UICorrectnessEngine extends UIService {
    ServiceKey<UICorrectnessEngine> KEY = new ServiceKey<>(UiId.parse("sh_better_ui:correctness"), UICorrectnessEngine.class, 1);
    record Settings(boolean enabled, boolean automaticCorrection, boolean backgroundSampling, int maxNodes,
                    int maxPlacements, long layoutBudgetNanos, long sampleIntervalNanos) {
        public static final Settings DEFAULT = new Settings(true, true, true, 256, 256, 4_000_000, 100_000_000);
        public Settings {
            if (maxNodes < 1 || maxNodes > 256 || maxPlacements < 1 || maxPlacements > 256
                    || layoutBudgetNanos < 1 || layoutBudgetNanos > 100_000_000
                    || sampleIntervalNanos < 16_000_000 || sampleIntervalNanos > 10_000_000_000L) {
                throw new IllegalArgumentException("Invalid correctness settings");
            }
        }
    }
    ConfigKey<Settings> SETTINGS = new ConfigKey<>(UiId.parse("sh_better_ui:correctness_settings"), Settings.class, Settings.DEFAULT,
            Set.of(ConfigLayer.USER_GLOBAL),
            ConfigKey.Mutability.RUNTIME, ConfigKey.Persistence.USER_GLOBAL, 1,
            "Correctness performance and automatic correction policy", value -> value != null, UnaryOperator.identity(), Map.of());
    enum InvalidationReason { DECLARATION, CONTENT, VIEWPORT, THEME, RESOURCE_RELOAD, BACKGROUND, INTERACTION, MOTION }
    Binding register(ModuleContext module, ScopeHandle scope, UIElement declaration);
    UISnapshot snapshot(ModuleContext module, ScopeHandle scope);
    ValidationReport report(ModuleContext module, ScopeHandle scope);
    ValidationReport validate(UISnapshot snapshot);
    default ValidationReport validateInteraction(UIInteractionSnapshot observed) { return observed.validate(); }
    List<SpatialIntelligence.CollisionPrediction> predict(UISnapshot snapshot, double horizonSeconds);
    CorrectionPlan resolve(UISnapshot snapshot, ValidationReport report);
    CorrectionPlan.Result apply(ModuleContext module, ScopeHandle scope, CorrectionPlan plan);
    CorrectionPlan.Result rollback(ModuleContext module, ScopeHandle scope, CorrectionPlan plan);
    String exportJson(ModuleContext module, ScopeHandle scope);
    Metrics metrics();

    record Metrics(long layoutNanos, long motionNanos, long contrastNanos, long correctionNanos,
                   long renderPreparationNanos, long cacheHits, long revalidations, long rejectedResults,
                   int registeredNodes, int lastIterations, long gpuSamplingNanos, long samplingCpuNanos,
                   long samplingCacheHits, long allocationsPerFrame) {
        public Metrics(long layoutNanos, long motionNanos, long contrastNanos, long correctionNanos,
                        long renderPreparationNanos, long cacheHits, long revalidations, long rejectedResults,
                        int registeredNodes, int lastIterations) {
            this(layoutNanos, motionNanos, contrastNanos, correctionNanos, renderPreparationNanos, cacheHits,
                    revalidations, rejectedResults, registeredNodes, lastIterations, -1, -1, 0, -1);
        }
    }

    interface Binding extends Subscription {
        UIElement declaration();
        long revision();
        /** Compare-and-set on the UI thread. An old revision cannot overwrite a newer declaration. */
        boolean update(long expectedRevision, UIElement declaration);
        void invalidate(InvalidationReason reason);
        Optional<UISnapshot.Node> committed();
        Optional<UISnapshot.Node> presented();
        /** Queries the last actual native presentation, never an unpainted predicted layout. */
        boolean hitTest(double guiX, double guiY);
    }
}
