package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.LightRaysProfile.Origin;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证图库注册、控件更新和最小布局。 */
class LightRaysGalleryPreviewTest {
    private static final Bounds NARROW = new Bounds(20, 63, 200, 57);
    private static final Bounds WIDE = new Bounds(32, 63, 576, 111);

    @Test
    void registersTheLightRaysSampleInTheControlsGallery() {
        assertTrue(DeveloperGalleryScreen.Section.CONTROLS.samples.contains(DeveloperSamples.LIGHT_RAYS));
        assertTrue(DeveloperSamples.LIGHT_RAYS.code.contains("rays.setProfile("));
    }

    @Test
    void sliderAndBackdropApplyLiveAndSurviveRebuildingTheWidgets() {
        var preview = new LightRaysGalleryPreview();
        var widgets = widgets(preview, NARROW);
        var speed = visibleSlider(widgets);
        speed.onClick(speed.getX() + 4, speed.getY() + 4);
        assertEquals(0f, preview.profile().raysSpeed());

        var board = (Button) widgets.get(1);
        board.onPress();
        assertTrue(preview.profile().lightMode());
        assertEquals(0f, preview.profile().raysSpeed());

        var next = (Button) widgets.get(4);
        for (int i = 0; i < 4; i++) next.onPress();
        var origin = visibleSlider(widgets);
        assertEquals(Origin.TOP_CENTER.ordinal(), origin.value());
        origin.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        origin.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, 0);
        assertEquals(Origin.TOP_RIGHT, preview.profile().raysOrigin());

        var resized = widgets(preview, WIDE);
        assertEquals(origin.value(), visibleSlider(resized).value());
        assertEquals(board.getMessage(), resized.get(1).getMessage());
        ((Button) resized.get(1)).onPress();
        assertFalse(preview.profile().lightMode());
        assertEquals(Origin.TOP_RIGHT, preview.profile().raysOrigin());
        assertEquals(0f, preview.profile().raysSpeed());
    }

    @Test
    void allParametersFitBesideTheCanvasAndCanBeSelectedInBothDirections() {
        var preview = new LightRaysGalleryPreview();
        for (Bounds area : List.of(NARROW, WIDE)) {
            var widgets = widgets(preview, area);
            var first = visibleSlider(widgets);
            var previous = (Button) widgets.get(3);
            var next = (Button) widgets.get(4);
            long parameterCount = widgets.stream().filter(UiSlider.class::isInstance).count();
            for (int i = 0; i < parameterCount; i++) {
                var visible = widgets.stream().filter(widget -> widget.visible).map(LightRaysGalleryPreviewTest::bounds).toList();
                assertEquals(6, visible.size());
                for (int j = 0; j < visible.size(); j++) {
                    Bounds widget = visible.get(j);
                    assertTrue(widget.x() >= area.x() && widget.right() <= area.right());
                    assertTrue(widget.y() >= area.y() && widget.bottom() <= area.bottom());
                    for (Bounds other : visible.subList(j + 1, visible.size())) {
                        assertTrue(widget.right() <= other.x() || other.right() <= widget.x()
                                || widget.bottom() <= other.y() || other.bottom() <= widget.y());
                    }
                }
                next.onPress();
            }
            assertSame(first, visibleSlider(widgets));
            previous.onPress();
            assertNotSame(first, visibleSlider(widgets));
            next.onPress();
            assertSame(first, visibleSlider(widgets));
        }
    }

    private static List<AbstractWidget> widgets(LightRaysGalleryPreview preview, Bounds area) {
        return preview.widgets(area, UiPreferences.DEFAULT, () -> true);
    }

    private static UiSlider visibleSlider(List<AbstractWidget> widgets) {
        var visible = widgets.stream().filter(widget -> widget.visible && widget instanceof UiSlider).toList();
        assertEquals(1, visible.size());
        return (UiSlider) visible.getFirst();
    }

    private static Bounds bounds(AbstractWidget widget) {
        return new Bounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
    }
}
