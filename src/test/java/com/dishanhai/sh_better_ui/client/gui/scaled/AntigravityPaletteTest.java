package com.dishanhai.sh_better_ui.client.gui.scaled;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：配色端点、循环接缝、透明度与浅色背景可读性。 */
class AntigravityPaletteTest {
    @Test
    void preservesOriginalPaletteEndpoints() {
        var lut = AntigravityPalette.build(List.of(0xFF5227FF, 0xFFFF9FFC, 0xFFB497CF), false, false);
        assertEquals(256, lut.length);
        assertEquals(0xFF5227FF, AntigravityPalette.sample(lut, 0, false));
        assertEquals(0xFFB497CF, AntigravityPalette.sample(lut, 1, false));
    }

    @Test
    void clampsNonCyclicSamplesToEndpoints() {
        var lut = AntigravityPalette.build(List.of(0x12345678, 0xFEDCBA98), false, false);
        assertEquals(lut[0], AntigravityPalette.sample(lut, -2, false));
        assertEquals(lut[255], AntigravityPalette.sample(lut, 5, false));
    }

    @Test
    void wrapsCyclicSamplesInBothDirections() {
        var lut = AntigravityPalette.build(List.of(0xFF5227FF, 0xFFFF9FFC, 0xFFB497CF), false, true);
        assertEquals(lut[0], AntigravityPalette.sample(lut, 1, true));
        assertEquals(AntigravityPalette.sample(lut, .75f, true), AntigravityPalette.sample(lut, -.25f, true));
        assertEquals(AntigravityPalette.sample(lut, .25f, true), AntigravityPalette.sample(lut, 2.25f, true));
    }

    @Test
    void cyclicSeamApproachesFirstColorContinuously() {
        var lut = AntigravityPalette.build(List.of(0xFF5227FF, 0x80FF9FFC, 0x20B497CF), false, true);
        int before = AntigravityPalette.sample(lut, .99999f, true);
        for (int shift = 0; shift <= 24; shift += 8) {
            assertTrue(Math.abs((before >>> shift & 255) - (lut[0] >>> shift & 255)) <= 1);
        }
    }

    @Test
    void singleColorRemainsUniformIncludingTransparency() {
        for (boolean cyclic : new boolean[] {false, true}) {
            var lut = AntigravityPalette.build(List.of(0x774285CC), false, cyclic);
            assertTrue(Arrays.stream(lut).allMatch(color -> color == 0x774285CC));
        }
    }

    @Test
    void interpolatesAlphaAlongsideColor() {
        var lut = AntigravityPalette.build(List.of(0x005227FF, 0xFF5227FF), false, false);
        int middle = AntigravityPalette.sample(lut, .5f, false);
        assertEquals(128, middle >>> 24, 1);
        assertEquals(0x5227FF, middle & 0xFFFFFF);
    }

    @Test
    void retainsChromaBetweenSaturatedStops() {
        var lut = AntigravityPalette.build(List.of(0xFFFF0000, 0xFF00FF00), false, false);
        int middle = AntigravityPalette.sample(lut, .5f, false);
        assertTrue((middle >> 16 & 255) > 128);
        assertTrue((middle >> 8 & 255) > 128);
        assertTrue((middle & 255) < 40);
    }

    @Test
    void lightModeDeepensPaleColorsWithoutChangingAlpha() {
        int source = 0x99FFF0FC;
        int adjusted = AntigravityPalette.build(List.of(source), true, false)[0];
        assertEquals(source >>> 24, adjusted >>> 24);
        assertTrue((adjusted >> 16 & 255) < (source >> 16 & 255));
        assertTrue((adjusted >> 8 & 255) < (source >> 8 & 255));
        assertTrue((adjusted & 255) < (source & 255));
    }

    @Test
    void rejectsMissingStopsAndNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> AntigravityPalette.build(List.of(), false, false));
        var lut = AntigravityPalette.build(List.of(0xFFFFFFFF), false, false);
        assertThrows(IllegalArgumentException.class, () -> AntigravityPalette.sample(lut, Float.NaN, false));
        assertThrows(IllegalArgumentException.class, () -> AntigravityPalette.sample(lut, Float.POSITIVE_INFINITY, true));
        assertThrows(IllegalArgumentException.class, () -> AntigravityPalette.sample(new int[0], 0, false));
    }
}
