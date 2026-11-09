package com.dishanhai.sh_better_ui.client.gui.layout;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

import static com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.*;

/** 山海：绘制已发布的当前及目标状态，可在开发者工具中按节点开启。 */
public final class AlignmentDebugOverlay {
    private static final String KEY = "sh_better_ui.layout.debug.";
    private static final int LAYOUT = 0xFF69B6FF;
    private static final int RENDER = 0xFFFFB34D;
    private static final int VISUAL = 0xFF76F0A0;
    private static final int HIT = 0xFFFF71B8;
    private static final int ANCHOR = 0xFFFFE475;
    private static final int OPTICAL = 0xFFB79BFF;
    private static final int TARGET = 0xFFC2C2CF;

    private AlignmentDebugOverlay() {}

    /** 绘制边界及关系。应在绘制完控件、回到屏幕坐标后调用。 */
    public static void draw(GuiGraphics graphics, AlignmentLayout.Node node, PixelAlignment grid) {
        var current = node.current(grid);
        var target = node.target(grid);
        if (!target.renderBounds().equals(current.renderBounds())) outline(graphics, target.renderBounds(), TARGET, 0);
        outline(graphics, current.layoutBounds(), LAYOUT, 0);
        outline(graphics, current.renderBounds(), RENDER, 1);
        outline(graphics, current.visualBounds(), VISUAL, 2);
        outline(graphics, current.hitBounds(), HIT, 3);
        for (var relation : node.debug().relations()) {
            elbow(graphics, relation.sourcePoint(), relation.targetPoint(), relation.selected() ? TARGET : HIT);
        }
        cross(graphics, current.anchor(), ANCHOR, 4);
        cross(graphics, current.opticalCenter(), OPTICAL, 2);
    }

    /** 绘制图例、最近失效原因及诊断文本。坐标与可用空间由调试面板提供。 */
    public static void describe(GuiGraphics graphics, Font font, AlignmentLayout.Node node, int x, int y) {
        int line = font.lineHeight + 1;
        var labels = new String[]{"layout", "render", "visual", "hit", "anchor", "optical", "target"};
        int[] colors = {LAYOUT, RENDER, VISUAL, HIT, ANCHOR, OPTICAL, TARGET};
        for (int i = 0; i < labels.length; i++) {
            graphics.drawString(font, Component.translatable(KEY + labels[i]), x, y, colors[i]);
            y += line;
        }
        for (var reason : AlignmentLayout.Reason.values()) {
            if (!node.debug().reasons().contains(reason)) continue;
            graphics.drawString(font, Component.translatable(KEY + "reason." + key(reason)), x, y, LAYOUT);
            y += line;
        }
        for (var issue : node.debug().diagnostics()) {
            var state = Component.translatable(KEY + "state." + key(issue.state()));
            graphics.drawString(font, Component.translatable(KEY + "issue." + key(issue.issue()), state, issue.object()),
                    x, y, HIT);
            y += line;
        }
    }

    private static String key(Enum<?> value) { return value.name().toLowerCase(Locale.ROOT); }

    private static void outline(GuiGraphics graphics, Rect rect, int color, int inset) {
        int x = pixel(rect.x()) + inset, y = pixel(rect.y()) + inset;
        int w = pixel(rect.right()) - x - inset, h = pixel(rect.bottom()) - y - inset;
        if (w > 0 && h > 0) graphics.renderOutline(x, y, w, h, color);
    }

    private static void cross(GuiGraphics g, Anchor point, int color, int radius) {
        int x = pixel(point.x()), y = pixel(point.y());
        g.fill(x - radius, y, x + radius + 1, y + 1, color);
        g.fill(x, y - radius, x + 1, y + radius + 1, color);
    }

    private static void elbow(GuiGraphics g, Anchor source, Anchor target, int color) {
        int x1 = pixel(source.x()), x2 = pixel(target.x()), y1 = pixel(source.y()), y2 = pixel(target.y());
        g.fill(Math.min(x1, x2), y1, Math.max(x1, x2) + 1, y1 + 1, color);
        g.fill(x2, Math.min(y1, y2), x2 + 1, Math.max(y1, y2) + 1, color);
    }

    private static int pixel(double value) { return (int) Math.round(value); }
}
