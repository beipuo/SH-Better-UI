// Adapted by dishanhai from Link-Lyric (beipuo).
// Source: https://github.com/beipuo/Link-Lyric/tree/1a7cd274c3e7afbb33c08c66414ec0def944643b
// See META-INF/third-party/link-lyric for licenses and upstream notices.
package com.dishanhai.sh_better_ui.client.lyrics.common.hud;

import com.dishanhai.sh_better_ui.client.lyrics.common.style.LyricStyleConfig;

import java.util.ArrayList;
import java.util.List;

/** 山海：为曲线布局提供非空歌词槽，副行宽度已按字号缩放。 */
final class SlotPlan {
    private SlotPlan() {}

    record Entry(int slot, String text, int width, int color, float scale, boolean primary) {}

    static List<Entry> entries(PlacementInput in, LyricStyleConfig style) {
        int slots = Math.max(1, in.lineCount());
        int primarySpan = in.primarySpan();
        var entries = new ArrayList<Entry>(slots);
        for (int slot = 0; slot < slots; slot++) {
            String text = in.textAt(slot);
            boolean primary = slot < primarySpan;
            if (primary || !text.isEmpty()) {
                int width = slot == 0 ? in.primaryWidth() : in.widthAt(slot);
                int color = primary ? style.sungColor() : in.colorAt(slot);
                entries.add(new Entry(slot, text, width, color, in.scaleAt(slot), primary));
            }
        }
        return entries;
    }
}
