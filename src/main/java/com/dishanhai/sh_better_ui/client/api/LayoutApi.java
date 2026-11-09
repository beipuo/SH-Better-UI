package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentAssets;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.Rect;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.View;
import com.dishanhai.sh_better_ui.client.gui.layout.PixelAlignment;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;

import java.util.function.Consumer;

/** 山海：共用视觉布局的 Minecraft 客户端适配。 */
public final class LayoutApi {
    LayoutApi() {}

    public AlignmentLayout create(Bounds viewport) { return new AlignmentLayout(new Rect(viewport)); }

    public AlignmentAssets assets(ResourceManager resources) { return new AlignmentAssets(resources); }

    /** GUI 投影使用 getGuiScale，GUI 宽高向上取整后不能用来反推投影倍率。 */
    public PixelAlignment pixels(Window window) { return new PixelAlignment(window.getGuiScale()); }

    /** 以现有原生控件的位置和尺寸为初始测量。 */
    public AlignmentFrame widget(AbstractWidget widget) {
        return AlignmentFrame.box(new Rect(widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
    }

    /**
     * 应用到原生控件的整数位置，原生键盘、旁白及鼠标路由可继续使用。
     * 要求 GUI_ORIGIN 吸附、纯平移、原尺寸点击矩形且无需额外裁剪。
     */
    public void placeWidget(AbstractWidget widget, View view) {
        var t = view.transform();
        if (t.xx() != 1 || t.yy() != 1 || t.xy() != 0 || t.yx() != 0 || view.clip() != null
                || !view.hitTransform().equals(t)
                || !view.localHit().equals(new Rect(0, 0, widget.getWidth(), widget.getHeight()))) {
            throw new IllegalArgumentException("Native widget placement requires an unclipped integer translation");
        }
        int x = integerCoordinate(t.tx()), y = integerCoordinate(t.ty());
        if (widget.getX() != x) widget.setX(x);
        if (widget.getY() != y) widget.setY(y);
    }

    /**
     * 在屏幕 GUI 原点的绘制阶段调用，回调收到局部坐标与保存好的裁剪。
     * 鼠标仍使用屏幕 GUI 坐标，交由同一 View 的 hitTest 判断。
     */
    public void draw(GuiGraphics graphics, View view, Consumer<GuiGraphics> content) {
        Rect clip = view.clip();
        if (clip != null && clip.empty()) return;
        if (clip != null) {
            graphics.enableScissor((int) Math.floor(clip.x()), (int) Math.floor(clip.y()),
                    (int) Math.ceil(clip.right()), (int) Math.ceil(clip.bottom()));
        }
        var pose = graphics.pose();
        pose.pushPose();
        try {
            var t = view.transform();
            pose.last().pose().mul(new Matrix4f()
                    .m00((float) t.xx()).m10((float) t.xy()).m30((float) t.tx())
                    .m01((float) t.yx()).m11((float) t.yy()).m31((float) t.ty()));
            content.accept(graphics);
        } finally {
            pose.popPose();
            if (clip != null) graphics.disableScissor();
        }
    }

    private static int integerCoordinate(double value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE || Math.rint(value) != value) {
            throw new IllegalArgumentException("Expected an integer GUI coordinate: " + value);
        }
        return (int) value;
    }
}
