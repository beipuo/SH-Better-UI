package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 山海：反重力配置的输入范围与不可变快照。 */
class AntigravityProfileTest {
    @Test void defaultsMatchTheExample() {
        var p = AntigravityProfile.DEFAULT;
        assertEquals(1500, p.count());
        assertEquals(7, p.ringRadius());
        assertEquals(9, p.magnetRadius());
        assertEquals(List.of(0xFF5227FF, 0xFFFF9FFC, 0xFFB497CF), p.colors());
        assertEquals(AntigravityProfile.Shape.CAPSULE, p.particleShape());
    }
    @Test void paletteIsAnImmutableCopyAndSingleColorOverridesIt() {
        var colors = new ArrayList<>(List.of(0xFF123456));
        var p = AntigravityProfile.builder().colors(colors).build();
        colors.clear();
        assertEquals(List.of(0xFF123456), p.colors());
        assertThrows(UnsupportedOperationException.class, () -> p.colors().clear());
        assertEquals(List.of(0xFFABCDEF), p.toBuilder().color(0xFFABCDEF).build().colors());
        assertEquals(List.of(0xFF123456), p.colors());
    }
    @Test void inputsHaveFiniteBoundedWorkAndStablePhysics() {
        var p = AntigravityProfile.builder().count(Integer.MAX_VALUE).waveCount(-1)
                .opacity(2).depthFactor(5).lerpSpeed(0).ambient(-1).build();
        assertEquals(20_000, p.count());
        assertEquals(0, p.waveCount());
        assertEquals(1, p.opacity());
        assertEquals(2, p.depthFactor());
        assertEquals(.001f, p.lerpSpeed());
        assertEquals(0, p.ambient());
        assertThrows(IllegalArgumentException.class, () -> AntigravityProfile.builder().ringRadius(Float.NaN).build());
        assertThrows(IllegalArgumentException.class, () -> AntigravityProfile.builder().rotationSpeed(Float.POSITIVE_INFINITY).build());
        assertThrows(IllegalArgumentException.class, () -> AntigravityProfile.builder().colors(List.of()).build());
    }
}
