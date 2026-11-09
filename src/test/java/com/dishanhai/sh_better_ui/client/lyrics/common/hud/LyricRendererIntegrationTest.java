package com.dishanhai.sh_better_ui.client.lyrics.common.hud;

import com.dishanhai.sh_better_ui.client.lyrics.common.style.LyricStyleConfig;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.client.lyrics.core.sync.TrackIdentity;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证时间轴换曲、空数据和多行内容顺序。 */
class LyricRendererIntegrationTest {
    @Test
    void rendersTranslationAndUpcomingLinesOnceAndClearsWhenTrackChanges() {
        var lyrics = ShBetterUiApi.lyrics();
        var source = lyrics.localFeed("test:lyrics");
        var renderer = new FloatingLyricRenderer(source);
        renderer.setStyle(LyricStyleConfig.DEFAULT.withLineCount(4));
        var painter = new Painter();
        var track = TrackIdentity.of("Test track", "", 6000);
        var timeline = lyrics.parseLrc("[00:00]first\n[00:02]second\n[00:04]third",
                "[00:00]translation", 6000);

        source.publish(track, timeline, 1000, false);
        assertTrue(renderer.render(painter, true));
        assertEquals(List.of("translation", "second", "third"), painter.secondary);

        painter.secondary.clear();
        source.publish(track, timeline, 4500, false);
        assertTrue(renderer.render(painter, true));
        assertTrue(painter.secondary.isEmpty());

        source.publish(TrackIdentity.of("New track", "", 5000), LyricTimeline.empty(), 0, false);
        assertFalse(renderer.render(painter, true));
        assertTrue(renderer.bounds().isEmpty());
    }

    @Test
    void curvedLayoutsKeepThreeLinesInThePreview() {
        var preview = new FloatingLyricRenderer.Bounds(30, 40, 300, 100);
        for (var layout : List.of(LayoutMode.ARC, LayoutMode.RING)) {
            int lineWidth = layout == LayoutMode.RING
                    ? RingPlacement.lineWidth(preview.width(), preview.height(), 3, 1f) : 130;
            var style = LyricStyleConfig.DEFAULT.withLineCount(3).withLayout(layout);
            var input = new PlacementInput(400, 300, preview, 0, 0, style,
                    List.of("first", "translation", "next"),
                    new int[]{lineWidth, lineWidth - 8, lineWidth - 8}, new int[]{-1, -1, -1},
                    new float[]{1, .8f, .8f}, lineWidth, 0, false, 0, 1);
            var placement = layout.strategy().place(input);
            var bounds = placement.footprint();

            assertEquals(3, placement.lines().size());
            assertTrue(preview.contains(bounds.x(), bounds.y()), layout.name());
            assertTrue(preview.contains(bounds.x() + bounds.width() - 1,
                    bounds.y() + bounds.height() - 1), layout.name());
            assertTrue(placement.lines().stream().anyMatch(LyricPlacement.PlacedLine::hasOrientation));
        }
    }

    private static final class Painter implements FloatingLyricRenderer.Painter {
        private final List<String> secondary = new ArrayList<>();
        @Override public int screenWidth() { return 400; }
        @Override public int screenHeight() { return 300; }
        @Override public int textWidth(String text) { return text.length() * 6; }
        @Override public void drawText(String text, int x, int y, int color) {}
        @Override public void fillRect(int x, int y, int width, int height, int color) {}
        @Override public void drawTextScaled(String text, int x, int y, float scale, int color, boolean shadow) {
            secondary.add(text);
        }
    }
}
