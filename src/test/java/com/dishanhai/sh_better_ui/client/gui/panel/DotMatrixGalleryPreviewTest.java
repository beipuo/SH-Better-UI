package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：点阵预览的控件回调和窄窗布局。 */
class DotMatrixGalleryPreviewTest {
    private static final Bounds NARROW = new Bounds(20, 63, 200, 57);
    private static final Bounds WIDE = new Bounds(32, 64, 576, 111);

    @Test
    void materialsGalleryOpensWithTheDotMatrixSample() {
        var sample = ShBetterUiApi.developerPreviews().entries().stream()
                .filter(spec -> spec.category() == PreviewSpec.Category.MATERIALS).findFirst().orElseThrow();
        assertEquals("sh_better_ui:dot_matrix", sample.id());
        assertTrue(sample.code().contains("dots.setProfile("));
        assertTrue(sample.code().contains(".withFade("));
        assertTrue(sample.code().contains(".withPointerWave("));
        assertTrue(sample.code().contains(".withGradient("));
    }

    @Test
    void changingWidgetsUpdatesTheProfileAndRetainsValuesAfterResize() {
        var preview = new DotMatrixGalleryPreview();
        var widgets = widgets(preview, NARROW);
        var hue = assertInstanceOf(UiSlider.class, widgets.get(3));
        var effect = assertInstanceOf(Button.class, widgets.get(4));
        var chance = assertInstanceOf(UiSlider.class, widgets.get(5));
        var cap = assertInstanceOf(UiSlider.class, widgets.get(6));
        var initial = preview.profile();

        hue.onClick(hue.getX() + 4, hue.getY() + 4);
        assertEquals(0, hue.value());
        assertNotEquals(initial.palette(), preview.profile().palette());
        assertNotEquals(initial.gradient().stops(), preview.profile().gradient().stops());
        assertEquals(initial.grid(), preview.profile().grid());
        assertEquals(initial.motion(), preview.profile().motion());

        assertTrue(chance.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertTrue(chance.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0));
        assertEquals((Math.round(initial.motion().floatProbability() * 100) + 1) / 100f,
                preview.profile().motion().floatProbability());
        chance.mouseDragged(boundsOf(chance).right() - 4, chance.getY() + 4, GLFW.GLFW_MOUSE_BUTTON_LEFT, 20, 0);
        assertEquals(1f, preview.profile().motion().floatProbability());

        cap.onClick(cap.getX() + 4, cap.getY() + 4);
        assertEquals(0, preview.profile().motion().maxFloatingDots());

        effect.onPress();
        assertSame(DotMatrixProfile.Effect.WAVE, preview.profile().motion().effect());
        effect.onPress();
        assertSame(DotMatrixProfile.Effect.STEADY, preview.profile().motion().effect());

        var current = preview.profile();
        var resized = widgets(preview, WIDE);
        assertEquals(current, preview.profile());
        assertEquals(hue.value(), ((UiSlider) resized.get(3)).value());
        assertEquals(effect.getMessage(), resized.get(4).getMessage());
        assertEquals(chance.value(), ((UiSlider) resized.get(5)).value());
        assertEquals(cap.value(), ((UiSlider) resized.get(6)).value());
        assertNotSame(hue, resized.get(3));
    }

    @Test
    void motionSettingsUpdateTheFadePaletteDirectionAndPeriodIndependently() {
        var preview = new DotMatrixGalleryPreview();
        var widgets = widgets(preview, NARROW);
        var settings = assertInstanceOf(Button.class, widgets.get(1));
        var fade = assertInstanceOf(UiSlider.class, widgets.get(7));
        var palette = assertInstanceOf(Button.class, widgets.get(8));
        var flow = assertInstanceOf(Button.class, widgets.get(9));
        var period = assertInstanceOf(UiSlider.class, widgets.get(10));
        var initial = preview.profile();
        assertEquals(DotMatrixProfile.Gradient.RAINBOW, initial.gradient());
        assertTrue(initial.fade().probability() > 0f && initial.fade().probability() < 1f);
        assertFalse(fade.visible);

        settings.onPress();
        assertTrue(fade.visible);
        assertFalse(widgets.get(3).visible);
        fade.onClick(fade.getX() + 4, fade.getY() + 4);
        assertEquals(0f, preview.profile().fade().probability());
        palette.onPress();
        assertEquals(initial.gradient().stops().getLast().argb(), preview.profile().gradient().stops().get(1).argb());
        flow.onPress();
        assertSame(DotMatrixProfile.Flow.DOWN, preview.profile().gradient().flow());
        period.onClick(period.getX() + 4, period.getY() + 4);
        assertEquals(1000, preview.profile().gradient().periodMs());
        assertEquals(initial.motion(), preview.profile().motion());

        palette.onPress();
        palette.onPress();
        assertEquals(DotMatrixProfile.Gradient.NONE, preview.profile().gradient());
        assertFalse(flow.active);
        assertFalse(period.active);
        palette.onPress();
        assertTrue(flow.active);
        assertTrue(period.active);
        assertEquals(initial.gradient().stops(), preview.profile().gradient().stops());
        assertSame(DotMatrixProfile.Flow.DOWN, preview.profile().gradient().flow());
        assertEquals(1000, preview.profile().gradient().periodMs());

        var current = preview.profile();
        var resized = widgets(preview, WIDE);
        assertEquals(current, preview.profile());
        assertTrue(resized.get(7).visible);
        assertFalse(resized.get(3).visible);
        assertEquals(settings.getMessage(), resized.get(1).getMessage());
    }

    @Test
    void pointerWaveTogglesIndependentlyAndKeepsItsValueAfterResize() {
        var preview = new DotMatrixGalleryPreview();
        var toggle = assertInstanceOf(UiButton.class, widgets(preview, NARROW).getFirst());
        var initial = preview.profile();
        assertTrue(toggle.isSelected());
        assertEquals(DotMatrixProfile.PointerWave.DEFAULT, initial.interaction().pointerWave());

        toggle.onPress();
        assertFalse(toggle.isSelected());
        assertEquals(initial.withPointerWave(DotMatrixProfile.PointerWave.NONE), preview.profile());

        var resized = assertInstanceOf(UiButton.class, widgets(preview, WIDE).getFirst());
        assertFalse(resized.isSelected());
        assertEquals(toggle.getMessage(), resized.getMessage());
        resized.onPress();
        assertEquals(initial, preview.profile());
    }

    @Test
    void controlsAndCanvasFitWithoutOverlapAtBothWindowSizes() {
        var preview = new DotMatrixGalleryPreview();
        for (Bounds area : List.of(NARROW, WIDE)) {
            List<AbstractWidget> widgets = widgets(preview, area);
            var waveToggle = boundsOf(widgets.getFirst());
            var settings = assertInstanceOf(Button.class, widgets.get(1));
            assertTrue(waveToggle.x() >= area.x() && waveToggle.right() < settings.getX());
            assertTrue(waveToggle.bottom() < area.y());
            assertTrue(boundsOf(settings).bottom() < area.y());
            for (int page = 0; page < 2; page++) {
                var visible = widgets.stream().skip(2).filter(widget -> widget.visible).toList();
                assertEquals(5, visible.size());
                for (int i = 0; i < visible.size(); i++) {
                    Bounds widget = boundsOf(visible.get(i));
                    assertTrue(widget.width() > 0 && widget.height() >= 16);
                    assertTrue(widget.x() >= area.x() && widget.right() <= area.right());
                    assertTrue(widget.y() >= area.y() && widget.bottom() <= area.bottom());
                    for (int j = i + 1; j < visible.size(); j++) {
                        Bounds other = boundsOf(visible.get(j));
                        assertTrue(widget.right() <= other.x() || other.right() <= widget.x()
                                || widget.bottom() <= other.y() || other.bottom() <= widget.y());
                    }
                }
                settings.onPress();
            }
        }
    }

    private static List<AbstractWidget> widgets(DotMatrixGalleryPreview preview, Bounds area) {
        var settings = new Bounds(area.right() - DotMatrixGalleryPreview.SETTINGS_WIDTH, area.y() - 54,
                DotMatrixGalleryPreview.SETTINGS_WIDTH, 22);
        return preview.widgets(area, settings, UiPreferences.DEFAULT, () -> true);
    }

    private static Bounds boundsOf(AbstractWidget widget) {
        return new Bounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
    }
}
