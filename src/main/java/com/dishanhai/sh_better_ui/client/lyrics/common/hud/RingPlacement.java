// Adapted by dishanhai from Link-Lyric (beipuo).
// Source: https://github.com/beipuo/Link-Lyric/tree/1a7cd274c3e7afbb33c08c66414ec0def944643b
// See META-INF/third-party/link-lyric for licenses and upstream notices.
package com.dishanhai.sh_better_ui.client.lyrics.common.hud;

import java.util.ArrayList;

/** 山海：各行沿圆周切线摆放，按扇区宽度截断长句。 */
public final class RingPlacement implements PlacementStrategy {
    private static final float RADIUS_RATIO = .29f;

    static int lineWidth(int viewportWidth, int viewportHeight, int slots, float scale) {
        float radius = radius(viewportWidth, viewportHeight, scale);
        // 两条相邻切线的交点限定可用长度。再以半径限制少行时的外接框。
        double halfSector = Math.PI / Math.max(3, slots);
        double halfWidth = radius * Math.min(.85, Math.tan(halfSector));
        return Math.max(1, (int) (2 * halfWidth - 4));
    }

    private static float radius(int width, int height, float scale) {
        return Math.max(1f, Math.min(width, height) * RADIUS_RATIO / scale);
    }

    @Override
    public LyricPlacement place(PlacementInput in) {
        var style = in.style();
        var entries = SlotPlan.entries(in, style);
        int slots = Math.max(in.lineCount(), style.lineCount());
        int width = in.inPreview() ? in.previewArea().width() : in.screenWidth();
        int height = in.inPreview() ? in.previewArea().height() : in.screenHeight();
        float radius = slots == 1 ? 0f : radius(width, height, style.lineScale());
        int anchorX = LyricGeometry.anchorX(in.screenWidth(), in.offsetX(), in.previewArea());
        int anchorY = LyricGeometry.anchorY(in.screenHeight(), in.offsetY(), in.previewArea(),
                style.anchorRatio(), LyricGeometry.LINE_HEIGHT);
        float centerY = in.inPreview() ? anchorY : anchorY - radius * (1f - 2f * style.anchorRatio());
        float stepDegrees = 360f / slots;
        var lines = new ArrayList<LyricPlacement.PlacedLine>(entries.size());
        float[] union = OrientedBox.empty();
        for (var entry : entries) {
            float tangent = entry.slot() * stepDegrees;
            double angle = Math.toRadians(tangent - 90f);
            float x = anchorX + radius * (float) Math.cos(angle);
            float y = centerY + radius * (float) Math.sin(angle);
            float orientation = tangent > 90f && tangent <= 270f ? tangent - 180f
                    : tangent > 270f ? tangent - 360f : tangent;
            float lineHeight = LyricGeometry.LINE_HEIGHT * entry.scale();
            lines.add(new LyricPlacement.PlacedLine(entry.slot(), x - entry.width() * .5f,
                    y - lineHeight * .5f, entry.width(), entry.text(), entry.color(), entry.scale(),
                    entry.primary(), orientation));
            OrientedBox.merge(union, OrientedBox.of(x, y, entry.width(), lineHeight, orientation));
        }
        return OrientedBox.placement(lines, union, lineWidth(width, height, slots, style.lineScale()),
                anchorX, anchorY, style.padXPx());
    }
}
