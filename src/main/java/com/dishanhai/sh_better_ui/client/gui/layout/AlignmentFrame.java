package com.dishanhai.sh_better_ui.client.gui.layout;

import java.util.Objects;

import static com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.*;

/**
 * 山海：一次测量的不可变输入。layout.rect 在父节点空间，其他矩形、中心和基线都在控件局部空间。
 * 只在尺寸、内容度量或姿态变化时替换。局部裁剪同时约束自身和后代。
 *
 * @param layout 逻辑矩形、内外边距及局部锚点
 * @param render 渲染主体边界
 * @param visual 可见内容、重心和效果范围
 * @param hit 独立的交互区域
 * @param pose 局部动画姿态
 * @param clip 局部裁剪，null 表示继承父级裁剪
 * @param hitFollowsPose 是否把自己的动画姿态用于点击区域。父级姿态始终会继承
 */
public record AlignmentFrame(LayoutBounds layout, Rect render, VisualBounds visual,
                             Rect hit, Transform pose, Rect clip, boolean hitFollowsPose) {
    public AlignmentFrame {
        Objects.requireNonNull(layout);
        Objects.requireNonNull(render);
        Objects.requireNonNull(visual);
        Objects.requireNonNull(hit);
        Objects.requireNonNull(pose);
    }

    public AlignmentFrame(LayoutBounds layout, Rect render, VisualBounds visual, Rect hit, Transform pose, Rect clip) {
        this(layout, render, visual, hit, pose, clip, true);
    }

    /** 指定父级空间内的逻辑矩形。初始渲染、可见和点击矩形都填满本地尺寸。 */
    public static AlignmentFrame box(Rect rect) {
        var local = new Rect(0, 0, rect.width(), rect.height());
        return new AlignmentFrame(new LayoutBounds(rect), local, new VisualBounds(local),
                local, Transform.IDENTITY, null);
    }

    public AlignmentFrame withLayout(LayoutBounds value) {
        return new AlignmentFrame(value, render, visual, hit, pose, clip, hitFollowsPose);
    }
    public AlignmentFrame withRender(Rect value) {
        return new AlignmentFrame(layout, value, visual, hit, pose, clip, hitFollowsPose);
    }
    public AlignmentFrame withVisual(VisualBounds value) {
        return new AlignmentFrame(layout, render, value, hit, pose, clip, hitFollowsPose);
    }
    public AlignmentFrame withHit(Rect value) {
        return new AlignmentFrame(layout, render, visual, value, pose, clip, hitFollowsPose);
    }
    public AlignmentFrame withPose(Transform value) {
        return new AlignmentFrame(layout, render, visual, hit, value, clip, hitFollowsPose);
    }
    public AlignmentFrame withClip(Rect value) {
        return new AlignmentFrame(layout, render, visual, hit, pose, value, hitFollowsPose);
    }

    /** 传 false 可保持悬停缩放前的交互范围，仍使用同一次布局位移与吸附。 */
    public AlignmentFrame withHitFollowsPose(boolean value) {
        return new AlignmentFrame(layout, render, visual, hit, pose, clip, value);
    }

    /** padding 给子布局留出内侧空间，margin 为外侧布局和碰撞保留空间。 */
    public record LayoutBounds(Rect rect, Insets padding, Insets margin, Anchor anchor) {
        public LayoutBounds {
            Objects.requireNonNull(rect);
            Objects.requireNonNull(padding);
            Objects.requireNonNull(margin);
            Objects.requireNonNull(anchor);
        }

        public LayoutBounds(Rect rect) {
            this(rect, Insets.NONE, Insets.NONE, new Anchor(rect.width() / 2, rect.height() / 2));
        }

        Rect local() { return new Rect(0, 0, rect.width(), rect.height()); }
    }

    /**
     * 可见像素的局部外接矩形、估算重心、人工光学修正和效果扩张范围。
     * baseline 是相对控件原点的 Y 坐标，普通图标传 null。
     */
    public record VisualBounds(Rect content, Anchor centroid, Anchor correction, Insets effects, Double baseline) {
        public VisualBounds {
            Objects.requireNonNull(content);
            Objects.requireNonNull(centroid);
            Objects.requireNonNull(correction);
            Objects.requireNonNull(effects);
            if (baseline != null) finite(baseline);
        }

        public VisualBounds(Rect content) {
            this(content, content.center(), new Anchor(0, 0), Insets.NONE, null);
        }

        public VisualBounds withCorrection(double x, double y) {
            return new VisualBounds(content, centroid, new Anchor(x, y), effects, baseline);
        }

        public VisualBounds withEffects(Insets value) {
            return new VisualBounds(content, centroid, correction, value, baseline);
        }

        public VisualBounds withBaseline(double y) {
            return new VisualBounds(content, centroid, correction, effects, y);
        }

        Rect ink() { return content.empty() ? content : content.outset(effects); }
    }
}
