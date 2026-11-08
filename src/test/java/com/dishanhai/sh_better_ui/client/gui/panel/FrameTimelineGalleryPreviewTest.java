package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证图库真实控件的跳帧、重建、重置及窄窗布局。 */
class FrameTimelineGalleryPreviewTest {
    private static final Bounds NARROW = new Bounds(12, 64, 200, 57);
    private static final Bounds WIDE = new Bounds(12, 64, 560, 111);

    @Test
    void reducedMotionShowsLastFrameAndResetReturnsToZero() {
        var preview = new FrameTimelineGalleryPreview();
        var widgets = preview.widgets(WIDE, new UiPreferences(false, true, false));
        var slider = assertInstanceOf(UiSlider.class, widgets.get(0));
        assertEquals(0, slider.value());
        assertEquals(transport("ready"), widgets.get(1).getMessage());
        assertInstanceOf(Button.class, widgets.get(1)).onPress();
        assertEquals(119, slider.value());
        assertEquals(transport("ended"), widgets.get(1).getMessage());
        var rebuilt = preview.widgets(NARROW, new UiPreferences(false, true, false));
        assertEquals(119, assertInstanceOf(UiSlider.class, rebuilt.get(0)).value());
        assertInstanceOf(Button.class, rebuilt.get(2)).onPress();
        assertEquals(0, assertInstanceOf(UiSlider.class, rebuilt.get(0)).value());
        assertEquals(transport("ready"), rebuilt.get(1).getMessage());
    }

    @Test
    void scrubbingStoresTheFrameAcrossWidgetRebuilds() {
        var preview = new FrameTimelineGalleryPreview();
        var widgets = preview.widgets(WIDE, UiPreferences.DEFAULT);
        var slider = assertInstanceOf(UiSlider.class, widgets.get(0));
        slider.onClick(slider.getX() + slider.getWidth() / 2d, slider.getY() + 8);
        assertTrue(slider.value() >= 59 && slider.value() <= 60);
        assertEquals(transport("paused"), widgets.get(1).getMessage());
        int selected = slider.value();
        preview.pause();
        assertEquals(selected, assertInstanceOf(UiSlider.class,
                preview.widgets(NARROW, UiPreferences.DEFAULT).get(0)).value());
    }

    @Test
    void controlsFitBothSupportedLayoutsWithoutOverlap() {
        var preview = new FrameTimelineGalleryPreview();
        for (var area : List.of(NARROW, WIDE)) {
            var widgets = preview.widgets(area, UiPreferences.DEFAULT);
            assertEquals(3, widgets.size());
            for (int i = 0; i < widgets.size(); i++) {
                var bounds = bounds(widgets.get(i));
                assertTrue(bounds.x() >= area.x() && bounds.right() <= area.right());
                assertTrue(bounds.y() >= area.y() && bounds.bottom() <= area.bottom());
                assertTrue(bounds.width() > 8 && bounds.height() >= 16);
                for (int j = i + 1; j < widgets.size(); j++) {
                    var other = bounds(widgets.get(j));
                    assertTrue(bounds.right() <= other.x() || other.right() <= bounds.x()
                            || bounds.bottom() <= other.y() || other.bottom() <= bounds.y());
                }
            }
        }
    }

    private static Bounds bounds(AbstractWidget widget) {
        return new Bounds(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight());
    }

    private static Component transport(String state) {
        return DeveloperPanelScreen.label("gallery.frame_timeline.transport",
                DeveloperPanelScreen.label("gallery.frame_timeline.play"),
                DeveloperPanelScreen.label("gallery.frame_timeline." + state));
    }
}
