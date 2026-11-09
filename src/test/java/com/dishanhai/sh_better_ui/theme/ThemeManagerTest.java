// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;
import com.dishanhai.sh_better_ui.theme.event.ThemeEvent;
import com.dishanhai.sh_better_ui.theme.event.Subscription;
import com.dishanhai.sh_better_ui.theme.style.StyleInvalidation;
import com.dishanhai.sh_better_ui.theme.style.StyleRule;
import com.dishanhai.sh_better_ui.theme.style.StyleSelector;
import com.dishanhai.sh_better_ui.theme.style.StyleState;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.dishanhai.sh_better_ui.theme.transition.Easing;
import com.dishanhai.sh_better_ui.theme.transition.TransitionSpec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ThemeManagerTest {
    private final AtomicLong now = new AtomicLong();
    private final List<RuntimeException> errors = new ArrayList<>();
    private final ThemeManager themes = new ThemeManager(now::get, errors::add);

    @Test
    void listenerReentryWaitsForAllListenersAndExceptionsAreIsolated() {
        register("example:a", 4);
        register("example:b", 8);
        var log = new ArrayList<String>();
        themes.observe(event -> {
            if (event.kind() == ThemeEvent.Kind.CHANGED && event.themeIds().contains("example:a")) {
                log.add("a/first");
                themes.setTheme("example:b");
                throw new IllegalStateException("consumer error");
            }
        });
        themes.observe(event -> {
            if (event.kind() == ThemeEvent.Kind.CHANGED) log.add(event.themeIds().get(0));
        });
        themes.setTheme("example:a");
        assertEquals(List.of("a/first", "example:a", "example:b"), log);
        assertEquals(1, errors.size());
        assertEquals(8f, themes.snapshot().resolve(ThemeTokens.RADIUS_MEDIUM));
    }

    @Test
    void publishesFrameValuesAndCoalescesBindingWork() {
        register("example:a", 4);
        register("example:b", 24);
        themes.setTheme("example:a");
        var scope = themes.global().child("screen");
        var calls = new AtomicInteger();
        var owner = scope.invalidationOwner(types -> calls.incrementAndGet());
        var property = scope.bind(ThemeBinding.of(ThemeTokens.RADIUS_MEDIUM), owner, StyleInvalidation.LAYOUT);
        themes.transitionTo("example:b", TransitionSpec.of(100, Easing.LINEAR));
        now.set(50);
        themes.frame();
        assertEquals(14f, property.value());
        assertEquals(1, calls.get());
        themes.frame();
        assertEquals(1, calls.get());
        themes.transitionTo("example:a", TransitionSpec.of(100, Easing.LINEAR));
        assertEquals(14f, property.value());
        themes.preferences(themes.preferences().withReducedMotion(true));
        assertEquals(4f, property.value());
        scope.close();
        assertTrue(property.isClosed());
    }

    @Test
    void rejectsBrokenReplacementAndProtectsTheLastValidRegistry() {
        register("example:a", 4);
        themes.setTheme("example:a");
        var invalid = Theme.builder("example:a").parent("example:a").build();
        assertThrows(ThemeResolutionException.class,
                () -> themes.register(invalid, ThemeRegistry.DuplicatePolicy.REPLACE_CODE));
        assertEquals(4f, themes.snapshot().resolve(ThemeTokens.RADIUS_MEDIUM));
        assertNotSame(invalid, themes.registry().find(invalid.id()).orElseThrow());
    }

    @Test
    void accessibilityStillUpdatesWhileAFixedPackThemeIsTemporarilyUnavailable() {
        var pack = Theme.builder("example:pack").token(ThemeTokens.RADIUS_MEDIUM, 18f).build();
        themes.replaceResourceThemes(List.of(pack), List.of());
        themes.setTheme(pack.id());
        themes.replaceResourceThemes(List.of(), List.of());
        assertEquals(List.of(BuiltinThemes.DARK), themes.snapshot().themeIds());
        assertDoesNotThrow(() -> themes.preferences(themes.preferences().withHighContrast(true)));
        assertTrue(themes.preferences().highContrast());
        themes.replaceResourceThemes(List.of(pack), List.of());
        assertEquals(List.of(pack.id()), themes.snapshot().themeIds());
        assertEquals(18f, themes.snapshot().resolve(ThemeTokens.RADIUS_MEDIUM));
    }

    @Test
    void observesOnlyWhileScopeIsMounted() {
        register("example:a", 4);
        var scope = themes.global().child("screen");
        var notifications = new AtomicInteger();
        themes.observe(scope, event -> notifications.incrementAndGet());
        themes.setTheme("example:a");
        assertTrue(notifications.get() > 0);
        int before = notifications.get();
        scope.close();
        themes.preferences(ThemePreferences.DEFAULT);
        assertEquals(before, notifications.get());
    }

    @Test
    void stateStylesWinOverLocalValuesAndAccessibilityWinsOverExplicitValues() {
        var hover = ColorValue.argb(0xff557799);
        var local = ColorValue.argb(0xff223344);
        var explicit = ColorValue.argb(0xffaaaaaa);
        themes.register(Theme.builder("example:style")
                .style(StyleRule.when(StyleSelector.of("example:button", StyleState.HOVERED))
                        .set(ThemeTokens.SURFACE, hover).build())
                .build());
        themes.setTheme("example:style");
        var scope = themes.global().child("panel");
        scope.configure(override -> override.override(ThemeTokens.SURFACE, local));
        assertEquals(local, scope.style("example:button", Set.of()).resolve(ThemeTokens.SURFACE));
        var hovered = scope.style("example:button", Set.of(StyleState.HOVERED));
        assertEquals(hover, hovered.resolve(ThemeTokens.SURFACE));
        assertEquals(explicit, hovered.resolve(ThemeTokens.SURFACE, explicit));
        themes.preferences(themes.preferences().withHighContrast(true));
        var property = ThemeInspector.property(scope, "example:button", Set.of(StyleState.HOVERED),
                ThemeTokens.SURFACE, explicit);
        assertEquals("accessibility", property.source());
        assertEquals(ColorValue.argb(0xff000000), property.value());
    }

    @Test
    void subscriptionsAddedOrRemovedDuringAnEventTakeEffectDeterministically() {
        register("example:a", 4);
        var phases = new ArrayList<ThemeEvent.Kind>();
        var removedCalls = new AtomicInteger();
        var nextCalls = new AtomicInteger();
        var subscription = new Subscription[1];
        themes.observe(event -> {
            phases.add(event.kind());
            if (event.kind() == ThemeEvent.Kind.REQUESTED) {
                subscription[0].close();
                themes.observe(next -> nextCalls.incrementAndGet());
            }
        });
        subscription[0] = themes.observe(event -> removedCalls.incrementAndGet());
        themes.setTheme("example:a");
        assertEquals(List.of(ThemeEvent.Kind.REQUESTED, ThemeEvent.Kind.WILL_CHANGE,
                ThemeEvent.Kind.TRANSITION_STARTED, ThemeEvent.Kind.TRANSITION_COMPLETED,
                ThemeEvent.Kind.CHANGED, ThemeEvent.Kind.TOKEN_CHANGED), phases);
        assertEquals(0, removedCalls.get());
        assertEquals(phases.size() - 1, nextCalls.get());
    }

    private void register(String id, float radius) {
        themes.register(Theme.builder(id).token(ThemeTokens.RADIUS_MEDIUM, radius).build());
    }
}
