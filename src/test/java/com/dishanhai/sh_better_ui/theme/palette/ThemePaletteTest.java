// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.palette;

import com.dishanhai.sh_better_ui.theme.accessibility.ContrastPolicy;
import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ThemePaletteTest {
    @Test
    void extractsDominantHueAndKeepsTextReadableInBothModes() {
        int[] pixels = new int[100];
        Arrays.fill(pixels, 0xffbd3225);
        Arrays.fill(pixels, 0, 10, 0xff1880cc);
        var generator = new ThemePaletteGenerator();
        var seed = generator.extract(10, 10, pixels);
        assertTrue(((seed.argb() >>> 16) & 255) > (seed.argb() & 255));
        for (boolean light : new boolean[]{false, true}) {
            var palette = generator.fromSeed(seed, light);
            assertTrue(ContrastPolicy.ratio(palette.text().argb(), palette.surface().argb(),
                    palette.background().argb()) >= 4.5);
            assertTrue(ContrastPolicy.ratio(palette.onPrimary().argb(), palette.primary().argb(),
                    palette.background().argb()) >= 4.5);
        }
        assertThrows(IllegalArgumentException.class, () -> generator.extract(4096, 4096, pixels));
    }

    @Test
    void asyncSourceRunsInWorkerAndTransparentPixelsUseFallback() {
        var generator = new ThemePaletteGenerator();
        var worker = new ArrayDeque<Runnable>();
        var task = generator.generate(() -> generator.extract(1, 1, new int[]{0x00ff0000}), false, worker::add);
        assertFalse(task.isDone());
        worker.remove().run();
        assertEquals(generator.fromSeed(ColorValue.argb(0xff6699ff), false), task.join());
    }

    @Test
    void audioSmoothsAndThrottlesLocalEffectsAndResetsWhenPlaybackStops() {
        var reactive = new AudioReactiveTheme(0.5f, 40, 0.8f, 0.4f);
        var first = reactive.sample(true, 3, 1, ThemePreferences.DEFAULT, 100);
        assertEquals(0.4f, first.glow(), 1e-5f);
        assertSame(first, reactive.sample(true, 0, 0, ThemePreferences.DEFAULT, 120));
        assertTrue(reactive.sample(true, 1, 1, ThemePreferences.DEFAULT, 140).glow() > first.glow());
        assertEquals(AudioReactiveTheme.Effects.NONE, reactive.sample(false, 1, 1, ThemePreferences.DEFAULT, 150));
        assertEquals(AudioReactiveTheme.Effects.NONE,
                reactive.sample(true, 1, 1, ThemePreferences.DEFAULT.withReducedMotion(true), 300));
    }
}
