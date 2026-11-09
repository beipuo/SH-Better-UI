// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.context;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies immutable, typed environment capture without a Minecraft runtime. */
class ContextSnapshotTest {
    @Test
    void typedReadsUseFallbackAndRejectWrongTypes() {
        var snapshot = new ContextSnapshot(7, Map.of(ContextKeys.LIGHT_LEVEL.id(), 12));
        assertEquals(7, snapshot.version());
        assertEquals(12, snapshot.get(ContextKeys.LIGHT_LEVEL));
        assertEquals(ContextKeys.LANGUAGE.fallback(), snapshot.get(ContextKeys.LANGUAGE));
        assertThrows(IllegalArgumentException.class,
                () -> new ContextSnapshot(0, Map.of(ContextKeys.LIGHT_LEVEL.id(), "wrong")).get(ContextKeys.LIGHT_LEVEL));
    }

    @Test
    @SuppressWarnings("unchecked")
    void nestedContainersAreCopiedAndFrozen() {
        var items = new ArrayList<>(List.of("first"));
        var preferences = new HashMap<String, Object>();
        preferences.put("items", items);
        var data = new HashMap<String, Object>();
        data.put("test:preferences", preferences);
        var snapshot = new ContextSnapshot(0, data);
        items.add("later");
        preferences.put("later", true);
        data.clear();
        var frozen = (Map<String, Object>) snapshot.data().get("test:preferences");
        assertEquals(List.of("first"), frozen.get("items"));
        assertFalse(frozen.containsKey("later"));
        assertThrows(UnsupportedOperationException.class, () -> frozen.put("x", 1));
        assertThrows(UnsupportedOperationException.class, () -> ((List<Object>) frozen.get("items")).add(2));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.data().clear());
    }

    @Test
    void mutableLeavesAndCyclesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ContextSnapshot(0, Map.of("test:value", new StringBuilder())));
        assertThrows(IllegalArgumentException.class,
                () -> new ContextKey<>("test:value", AtomicInteger.class, new AtomicInteger(), ContextKey.Source.EXTERNAL));
        var cycle = new ArrayList<Object>();
        cycle.add(cycle);
        assertThrows(IllegalArgumentException.class, () -> new ContextSnapshot(0, Map.of("test:cycle", cycle)));
    }

    @Test
    void keyDefinitionAndInputAreValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> new ContextKey<>("missing_namespace", String.class, "", ContextKey.Source.CLIENT));
        assertThrows(IllegalArgumentException.class, () -> new ContextSnapshot(-1, Map.of()));
        assertEquals(ContextKey.Source.CLIENT, ContextKeys.GUI_SCALE.source());
        assertEquals(ContextKey.Source.SERVER, ContextKeys.DIMENSION.source());
    }

    @Test
    void detectorTracksOnlyRelevantEffectiveValuesAndReusesUnchangedSnapshot() {
        var detector = new ContextChangeDetector(Set.of(ContextKeys.WEATHER));
        var first = detector.update(ContextSnapshot.of(Map.of(ContextKeys.WEATHER, "rain", ContextKeys.GUI_SCALE, 2)));
        assertEquals(Set.of(ContextKeys.WEATHER), first.changedKeys());
        var ignored = detector.update(ContextSnapshot.of(Map.of(ContextKeys.WEATHER, "rain", ContextKeys.GUI_SCALE, 3)));
        assertSame(first.snapshot(), ignored.snapshot());
        assertTrue(ignored.changedKeys().isEmpty());
        var changed = detector.capture(() -> ContextSnapshot.of(Map.of(ContextKeys.WEATHER, "clear")));
        assertEquals(first.snapshot().version() + 1, changed.snapshot().version());
        assertEquals(Set.of(ContextKeys.WEATHER), changed.changedKeys());
        assertThrows(UnsupportedOperationException.class, () -> changed.changedKeys().clear());
    }

    @Test
    void conditionCombinatorsDeclareUnionOfDependenciesAndShortCircuit() {
        var calls = new AtomicInteger();
        var rain = ThemeCondition.of(Set.of(ContextKeys.WEATHER), s -> s.get(ContextKeys.WEATHER).equals("rain"));
        var count = ThemeCondition.of(Set.of(ContextKeys.BIOME), s -> { calls.incrementAndGet(); return true; });
        assertFalse(rain.and(count).test(ContextSnapshot.empty()));
        assertEquals(0, calls.get());
        assertEquals(Set.of(ContextKeys.WEATHER, ContextKeys.BIOME), rain.or(count).dependencies());
        assertTrue(rain.not().test(ContextSnapshot.empty()));
        assertThrows(UnsupportedOperationException.class, () -> rain.dependencies().clear());
    }
}
