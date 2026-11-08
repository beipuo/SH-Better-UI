package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.CodeRainEffect.Cell;
import com.dishanhai.sh_better_ui.client.gui.scaled.CodeRainEffect.Frame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证共享代码雨采样与扩展，覆盖无需图形上下文的边界。 */
class CodeRainTest {
    private static final Bounds AREA = new Bounds(0, 0, 100, 100);
    private static final CodeRainProfile ASCII = CodeRainProfile.builder()
            .mode(CodeRainProfile.Mode.ASCII).density(1).characters(".中😀").build();

    @Test
    void publicFactoryAcceptsUnicodeAlphabetAndRejectsInvalidGlyphs() {
        assertEquals(CodeRainProfile.Mode.RAIN, ShBetterUiApi.graphics().codeRain().profile().mode());
        assertSame(ASCII, ShBetterUiApi.graphics().codeRain(ASCII, CodeRainEffects.glow(.2f)).profile());
        assertEquals(3, ASCII.characters().codePointCount(0, ASCII.characters().length()));
        assertThrows(IllegalArgumentException.class, () -> CodeRainProfile.builder().characters("\uD800").build());
        assertThrows(IllegalArgumentException.class, () -> CodeRainProfile.builder().characters("a\nb").build());
        assertThrows(IllegalArgumentException.class, () -> CodeRainProfile.builder().speed(Float.NaN).build());
    }

    @Test
    void gridCapsWorkAndFitsNarrowRegions() {
        for (int width : new int[] {1, 15, 500, 8192, Integer.MAX_VALUE}) {
            for (int height : new int[] {1, 15, 500, 8192, Integer.MAX_VALUE}) {
                var grid = CodeRain.Grid.fit(width, height, 6);
                assertTrue(grid.columns() * grid.rows() <= 3600);
                assertTrue(grid.cellSize() <= Math.min(width, height));
            }
        }
        assertEquals(8, CodeRain.Grid.fit(100, 50, 12).columns());
    }

    @Test
    void streamHeadAdvancesWithADecayingTailAndStableSeed() {
        var rain = CodeRainProfile.builder().density(1).trailLength(6).seed(12).build();
        double head = CodeRain.head(rain, 3, 50, 0);
        double advanced = CodeRain.head(rain, 3, 50, 1);
        assertTrue((advanced - head + 56) % 56 > 0);
        assertEquals(head, CodeRain.head(rain, 3, 50, 0));
        assertEquals(1f, CodeRain.brightness(rain, 3, 8, 8));
        assertTrue(CodeRain.brightness(rain, 3, 6, 8) > CodeRain.brightness(rain, 3, 4, 8));
        assertEquals(0f, CodeRain.brightness(rain, 3, 2, 8));
        assertEquals(0f, CodeRain.brightness(rain, 3, 9, 8));
        var empty = CodeRainProfile.builder().density(0).build();
        assertEquals(0f, CodeRain.brightness(empty, 0, 0, 0));
    }

    @Test
    void faintGlyphsAvoidVanillasOpaqueAlphaFallback() {
        assertEquals(0, CodeRain.glyphColor(0x03FFFFFF, 1));
        assertEquals(0, CodeRain.glyphColor(0xFF123456, .01f));
        assertEquals(0x04123456, CodeRain.glyphColor(0x08123456, .5f));
    }

    @Test
    void shapeControlsGlyphRankAndPaletteUsesItsResult() {
        var horizon = CodeRainEffects.shape(CodeRainEffects.Shape.HORIZON, .5f, 0);
        int[] colors = {0xFF000000, 0x80FFFFFF};
        var gradient = CodeRainEffects.gradient(colors);
        colors[1] = 0;
        Cell bright = cell(50, 8);
        Cell dark = cell(50, 95);
        Frame frame = frame(0, false, null);
        horizon.apply(frame, bright);
        horizon.apply(frame, dark);
        gradient.apply(frame, bright);
        assertEquals(2, bright.glyphIndex());
        assertEquals(0f, dark.brightness());
        assertEquals(0x40FFFFFF, bright.argb());
        assertEquals(0f, CodeRainEffects.shapeLight(CodeRainEffects.Shape.ECLIPSE, .5f, .5f, .5f, 1));
        assertEquals(1f, CodeRainEffects.shapeLight(CodeRainEffects.Shape.PINCH, .5f, .5f, .5f, 1));
        assertEquals(1f, CodeRainEffects.shapeLight(CodeRainEffects.Shape.RISE, .5f, .5f, 1, 1));
    }

    @Test
    void reducedMotionCompletesIntroAndDisablesFlicker() {
        var intro = CodeRainEffects.intro(2);
        var flicker = CodeRainEffects.flicker(1);
        Cell moving = cell(25, 75);
        intro.apply(frame(0, false, null), moving);
        assertEquals(0, moving.brightness());
        Cell reduced = cell(25, 75);
        intro.apply(frame(0, true, null), reduced);
        flicker.apply(frame(.2, true, null), reduced);
        assertEquals(1, reduced.brightness());
        assertEquals(-1, reduced.glyphIndex());
    }

    @Test
    void dotMatrixSupportsPureDotsAndStableMixedCells() {
        var dots = CodeRainEffects.dotMatrix(.3f, 1);
        Cell all = cell(50, 50);
        dots.apply(frame(0, false, null), all);
        assertEquals(.3f, all.dotSize());
        assertEquals(1f, all.brightness());
        assertEquals(0x80FFFFFF, all.argb());

        var mixed = CodeRainEffects.dotMatrix(.25f, .4f);
        int selected = 0;
        for (int row = 0; row < 64; row++) {
            Cell before = cell(50, 50);
            Cell after = cell(50, 50);
            before.row = after.row = row;
            mixed.apply(frame(0, false, null), before);
            mixed.apply(frame(8, false, null), after);
            assertEquals(before.dotSize(), after.dotSize());
            if (before.dotSize() > 0) selected++;
        }
        assertTrue(selected > 0 && selected < 64);

        Cell glyph = cell(50, 50);
        CodeRainEffects.dotMatrix(1, 0).apply(frame(0, false, null), glyph);
        assertEquals(0, glyph.dotSize());
    }

    @Test
    void hostPulseWorksWithoutMouseAndClearsWhenSuspended() {
        var effect = CodeRainEffects.interaction(false, CodeRainEffects.Click.BURST, .12f);
        Frame frame = frame(0, false, null);
        effect.beginFrame(frame);
        effect.pulse(50, 50);
        effect.beginFrame(frame);
        Cell close = cell(50, 50);
        Cell far = cell(95, 95);
        effect.apply(frame, close);
        effect.apply(frame, far);
        assertTrue(close.glyphIndex() >= 0);
        assertEquals(-1, far.glyphIndex());

        effect.suspend();
        effect.beginFrame(frame);
        close.setGlyphIndex(-1);
        effect.apply(frame, close);
        assertEquals(-1, close.glyphIndex());
    }

    private static Frame frame(double seconds, boolean reducedMotion, UiPointerTracker.Frame pointer) {
        return new Frame(AREA, ASCII, 3, seconds, reducedMotion, pointer);
    }

    private static Cell cell(float x, float y) {
        Cell cell = new Cell();
        cell.x = x;
        cell.y = y;
        cell.setArgb(0x80FFFFFF);
        cell.setBrightness(1);
        cell.setGlyphIndex(-1);
        return cell;
    }
}
