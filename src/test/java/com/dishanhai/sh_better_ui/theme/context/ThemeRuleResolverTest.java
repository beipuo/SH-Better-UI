// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.context;

import com.dishanhai.sh_better_ui.theme.transition.TransitionSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies deterministic composition, dependency filtering and temporal hysteresis. */
class ThemeRuleResolverTest {
    private final AtomicLong time = new AtomicLong();
    private final ThemeRuleResolver resolver = new ThemeRuleResolver(time::get);
    private final ThemeCondition rain = ThemeCondition.of(Set.of(ContextKeys.WEATHER),
            s -> s.get(ContextKeys.WEATHER).equals("rain"));

    private ContextSnapshot weather(String weather) {
        return ContextSnapshot.of(Map.of(ContextKeys.WEATHER, weather));
    }

    @Test
    void higherPriorityAndThenIdAreAppliedLastAndThemesAreDeduplicatedAtLastOccurrence() {
        resolver.register(ThemeRule.builder("test:z").when(rain).theme("test:same").priority(1).build());
        resolver.register(ThemeRule.builder("test:a").when(rain).theme("test:other").priority(1).build());
        resolver.register(ThemeRule.builder("test:low").when(rain).theme("test:same").priority(0).build());
        var selection = resolver.resolve(weather("rain"));
        assertEquals(List.of("test:other", "test:same"), selection.themeIds());
        assertEquals(Set.of("test:z", "test:a", "test:low"), selection.activeRuleIds());
        assertThrows(UnsupportedOperationException.class, () -> selection.themeIds().clear());
        assertSame(selection, resolver.resolve(weather("rain")));
    }

    @Test
    void onlyRulesWithChangedDependenciesAreRetested() {
        var calls = new AtomicInteger();
        resolver.register(ThemeRule.builder("test:count").when(ThemeCondition.of(Set.of(ContextKeys.WEATHER), s -> {
            calls.incrementAndGet(); return true;
        })).theme("test:theme").build());
        resolver.resolve(weather("rain"));
        resolver.resolve(ContextSnapshot.of(Map.of(ContextKeys.WEATHER, "rain", ContextKeys.GUI_SCALE, 4)), Set.of(ContextKeys.GUI_SCALE));
        assertEquals(1, calls.get());
        resolver.resolve(weather("clear"), Set.of(ContextKeys.WEATHER));
        assertEquals(2, calls.get());
    }

    @Test
    void stabilityMaturesWithoutAnotherEnvironmentChange() {
        resolver.register(ThemeRule.builder("test:rain").when(rain).theme("test:rain_theme").stability(100).build());
        var snapshot = weather("rain");
        assertTrue(resolver.resolve(snapshot).themeIds().isEmpty());
        assertTrue(resolver.debugStates().get("test:rain").pending());
        time.set(99);
        assertTrue(resolver.resolve(snapshot, Set.of()).themeIds().isEmpty());
        time.set(100);
        var active = resolver.resolve(snapshot, Set.of());
        assertEquals(List.of("test:rain_theme"), active.themeIds());
        time.set(200);
        assertSame(active, resolver.resolve(snapshot, Set.of()));
    }

    @Test
    void stabilityResetsOnlyWhenConditionActuallyChanges() {
        resolver.register(ThemeRule.builder("test:rain").when(rain).theme("test:theme").stability(100).build());
        resolver.resolve(weather("rain"));
        time.set(60);
        resolver.resolve(weather("clear"));
        time.set(70);
        resolver.resolve(weather("rain"));
        time.set(169);
        assertTrue(resolver.resolve(weather("rain")).themeIds().isEmpty());
        time.set(170);
        assertEquals(List.of("test:theme"), resolver.resolve(weather("rain")).themeIds());
    }

    @Test
    void dependencyChangesDebounceButDoNotRestartStability() {
        var bright = ThemeCondition.of(Set.of(ContextKeys.LIGHT_LEVEL), s -> s.get(ContextKeys.LIGHT_LEVEL) >= 10);
        resolver.register(ThemeRule.builder("test:bright").when(bright).theme("test:theme").stability(100).debounce(50).build());
        resolver.resolve(ContextSnapshot.of(Map.of(ContextKeys.LIGHT_LEVEL, 10)));
        time.set(80);
        var stable = ContextSnapshot.of(Map.of(ContextKeys.LIGHT_LEVEL, 11));
        resolver.resolve(stable);
        time.set(129);
        assertTrue(resolver.resolve(stable).themeIds().isEmpty());
        time.set(130);
        assertEquals(List.of("test:theme"), resolver.resolve(stable).themeIds());
    }

    @Test
    void debounceAppliesToExitAndMinimumActiveTimePreventsBoundaryFlicker() {
        resolver.register(ThemeRule.builder("test:rain").when(rain).theme("test:theme").debounce(20).minActive(100).build());
        var rainy = weather("rain");
        resolver.resolve(rainy);
        time.set(20);
        assertEquals(List.of("test:theme"), resolver.resolve(rainy).themeIds());
        time.set(30);
        resolver.resolve(weather("clear"));
        time.set(100);
        assertEquals(List.of("test:theme"), resolver.resolve(weather("clear")).themeIds());
        time.set(120);
        assertTrue(resolver.resolve(weather("clear")).themeIds().isEmpty());
    }

    @Test
    void disablingOverridesMinimumActiveAndEnablingRetestsTheRule() {
        resolver.register(ThemeRule.builder("test:rain").when(rain).theme("test:theme").minActive(10_000).build());
        var snapshot = weather("rain");
        resolver.resolve(snapshot);
        assertTrue(resolver.setEnabled("test:rain", false));
        assertTrue(resolver.resolve(snapshot, Set.of()).themeIds().isEmpty());
        resolver.setEnabled("test:rain", true);
        assertEquals(List.of("test:theme"), resolver.resolve(snapshot, Set.of()).themeIds());
        assertFalse(resolver.setEnabled("test:missing", false));
    }

    @Test
    void registrationReplacementAndRemovalReevaluateWithoutContextChanges() {
        var snapshot = weather("rain");
        resolver.resolve(snapshot);
        resolver.register(ThemeRule.builder("test:rain").when(rain).theme("test:first").build());
        assertEquals(List.of("test:first"), resolver.resolve(snapshot, Set.of()).themeIds());
        resolver.register(ThemeRule.builder("test:rain").when(rain).theme("test:second").build());
        assertEquals(List.of("test:second"), resolver.resolve(snapshot, Set.of()).themeIds());
        assertTrue(resolver.remove("test:rain"));
        assertTrue(resolver.resolve(snapshot, Set.of()).themeIds().isEmpty());
    }

    @Test
    void failingRuleIsIsolatedAndDiagnosed() {
        resolver.register(ThemeRule.builder("test:broken").when(ThemeCondition.of(Set.of(), s -> {
            throw new IllegalStateException("bad condition");
        })).theme("test:broken_theme").build());
        resolver.register(ThemeRule.builder("test:good").when(rain).theme("test:good_theme").build());
        assertEquals(List.of("test:good_theme"), resolver.resolve(weather("rain")).themeIds());
        assertEquals("test:broken", resolver.diagnostics().get(0).ruleId());
        assertTrue(resolver.debugStates().get("test:broken").failed());
    }

    @Test
    void wrongTypedDependencyOnlyFailsItsOwningRuleAndRecoversAfterAChange() {
        resolver.register(ThemeRule.builder("test:light").when(ThemeCondition.of(Set.of(ContextKeys.LIGHT_LEVEL),
                s -> s.get(ContextKeys.LIGHT_LEVEL) > 5)).theme("test:light_theme").build());
        resolver.register(ThemeRule.builder("test:rain").when(rain).theme("test:rain_theme").build());
        var malformed = new ContextSnapshot(0, Map.of(ContextKeys.LIGHT_LEVEL.id(), "bad", ContextKeys.WEATHER.id(), "rain"));
        assertEquals(List.of("test:rain_theme"), resolver.resolve(malformed).themeIds());
        assertEquals(1, resolver.diagnostics().size());
        var recovered = ContextSnapshot.of(Map.of(ContextKeys.LIGHT_LEVEL, 10, ContextKeys.WEATHER, "rain"));
        assertEquals(List.of("test:light_theme", "test:rain_theme"), resolver.resolve(recovered).themeIds());
        assertTrue(resolver.diagnostics().isEmpty());
    }

    @Test
    void returningToActiveConditionCancelsPendingExitWithoutRestartingSelection() {
        resolver.register(ThemeRule.builder("test:rain").when(rain).theme("test:theme").minActive(100).build());
        var active = resolver.resolve(weather("rain"));
        time.set(20);
        assertSame(active, resolver.resolve(weather("clear")));
        assertTrue(resolver.debugStates().get("test:rain").pending());
        time.set(30);
        assertSame(active, resolver.resolve(weather("rain")));
        assertFalse(resolver.debugStates().get("test:rain").pending());
    }

    @Test
    void disabledRulesDoNotEvaluateUntilEnabledAndClockRegressionDoesNotUndoTime() {
        var count = new AtomicInteger();
        resolver.register(ThemeRule.builder("test:count").when(ThemeCondition.of(Set.of(), s -> {
            count.incrementAndGet(); return true;
        })).theme("test:theme").enabled(false).stability(20).build());
        time.set(100);
        resolver.resolve(ContextSnapshot.empty());
        assertEquals(0, count.get());
        resolver.setEnabled("test:count", true);
        resolver.resolve(ContextSnapshot.empty());
        time.set(90);
        assertTrue(resolver.resolve(ContextSnapshot.empty()).themeIds().isEmpty());
        time.set(120);
        assertEquals(List.of("test:theme"), resolver.resolve(ContextSnapshot.empty()).themeIds());
        assertEquals(1, count.get());
    }

    @Test
    void transitionComesFromTheHighestPriorityActiveRule() {
        var transition = TransitionSpec.of(250, x -> x);
        resolver.register(ThemeRule.builder("test:rain").when(rain).theme("test:theme").transition(transition).build());
        assertSame(transition, resolver.resolve(weather("rain")).transition());
    }

    @Test
    void ruleAndBulkRegistrationInputsAreValidatedWithoutPartialMutation() {
        assertThrows(IllegalArgumentException.class, () -> ThemeRule.builder("bad id"));
        assertThrows(IllegalStateException.class, () -> ThemeRule.builder("test:rule").build());
        assertThrows(IllegalArgumentException.class, () -> ThemeRule.builder("test:rule").debounce(-1));
        var rule = ThemeRule.builder("test:rule").when(rain).theme("test:theme").build();
        resolver.register(rule);
        assertThrows(IllegalArgumentException.class, () -> resolver.setRules(List.of(rule, rule)));
        assertEquals(Set.of(ContextKeys.WEATHER), resolver.dependencies());
    }
}
