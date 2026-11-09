// Adapted by dishanhai from Link-Lyric (beipuo).
// Source: https://github.com/beipuo/Link-Lyric/tree/1a7cd274c3e7afbb33c08c66414ec0def944643b
// See META-INF/third-party/link-lyric for licenses and upstream notices.
package com.dishanhai.sh_better_ui.client.lyrics.common.hud;

import java.util.ArrayList;

/** 山海：沿侧向圆弧铺开歌词，按旋转后的高度为相邻行留出空间。 */
public final class ArcPlacement implements PlacementStrategy {
    @Override
    public LyricPlacement place(PlacementInput in) {
        var style = in.style();
        var entries = SlotPlan.entries(in, style);
        int maxWidth = LyricGeometry.availableWidth(style.maxWidthRatio(), in.previewArea(), in.screenWidth());
        int anchorX = LyricGeometry.anchorX(in.screenWidth(), in.offsetX(), in.previewArea());
        float halfAngle = Math.min(8f, (float) Math.toDegrees(Math.atan2(12, maxWidth)));
        float bow = Math.min(12f, maxWidth * .08f);
        float maxArc = (float) Math.toRadians(halfAngle);
        float radius = bow / (1f - (float) Math.cos(maxArc));
        float[] angles = new float[entries.size()];
        float[] heights = new float[entries.size()];
        float blockHeight = 0f;
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            float fraction = entries.size() <= 1 ? .5f : i / (float) (entries.size() - 1);
            float degrees = halfAngle * (2 * fraction - 1);
            double radians = Math.toRadians(degrees);
            angles[i] = degrees;
            heights[i] = (float) (Math.abs(Math.sin(radians)) * entry.width()
                    + Math.cos(radians) * LyricGeometry.LINE_HEIGHT * entry.scale());
            blockHeight += heights[i];
        }
        blockHeight += style.lineGapPx() * (entries.size() - 1);
        int anchorY = LyricGeometry.anchorY(in.screenHeight(), in.offsetY(), in.previewArea(),
                style.anchorRatio(), Math.round(blockHeight));
        float top = anchorY - blockHeight * .5f;
        float[] union = OrientedBox.empty();
        var lines = new ArrayList<LyricPlacement.PlacedLine>(entries.size());
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.get(i);
            float offset = entries.size() == 1 ? 0f
                    : bow * .5f - radius * (1f - (float) Math.cos(Math.toRadians(angles[i])));
            float x = anchorX + offset;
            float y = top + heights[i] * .5f;
            float height = LyricGeometry.LINE_HEIGHT * entry.scale();
            lines.add(new LyricPlacement.PlacedLine(entry.slot(), x - entry.width() * .5f,
                    y - height * .5f, entry.width(), entry.text(), entry.color(), entry.scale(),
                    entry.primary(), angles[i]));
            OrientedBox.merge(union, OrientedBox.of(x, y, entry.width(), height, angles[i]));
            top += heights[i] + style.lineGapPx();
        }
        return OrientedBox.placement(lines, union, maxWidth, anchorX, anchorY, style.padXPx());
    }
}
