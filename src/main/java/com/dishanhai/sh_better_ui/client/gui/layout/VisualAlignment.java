package com.dishanhai.sh_better_ui.client.gui.layout;

import java.util.Objects;

import static com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.*;

/** 山海：用一个或两个轴的锚点关系描述对齐。建立一次，后续由所属布局重算。 */
public final class VisualAlignment {
    private VisualAlignment() {}

    /** START 和 END 依据布局文字方向解析，LEFT 和 RIGHT 固定为物理方向。 */
    public enum Align { LEFT, CENTER, RIGHT, START, END, TOP, BOTTOM, BASELINE }

    /** 对齐时选用的边界。VISUAL 的中心可额外应用光学修正。 */
    public enum Bounds { LAYOUT, RENDER, VISUAL, HIT }

    /** CURRENT 跟随实时几何，TARGET 使用终点几何求出定位补偿。 */
    public enum Reference { CURRENT, TARGET }

    /** 决定轴向对齐位置。未指定的轴保留原位置，可由另一条约束决定。 */
    public static Builder align(AlignmentLayout.Node component) { return new Builder(component); }

    record AxisRule(Align source, Align target) {}

    sealed interface Target permits NodeTarget, FixedTarget, ParentTarget {}
    record NodeTarget(AlignmentLayout.Node node) implements Target {}
    record FixedTarget(Rect rect) implements Target {}
    enum ParentTarget implements Target { PARENT, SCREEN }

    record Rule(Target target, AxisRule horizontal, AxisRule vertical, Bounds bounds,
                boolean optical, Anchor opticalOffset, Anchor offset, Reference reference, int priority) {}

    public static final class Builder {
        private final AlignmentLayout.Node component;
        private Target target = ParentTarget.PARENT;
        private AxisRule horizontal;
        private AxisRule vertical;
        private Bounds bounds = Bounds.LAYOUT;
        private boolean optical;
        private Anchor opticalOffset = new Anchor(0, 0);
        private Anchor offset = new Anchor(0, 0);
        private Reference reference = Reference.CURRENT;
        private int priority;

        private Builder(AlignmentLayout.Node component) { this.component = Objects.requireNonNull(component); }

        /** 相对同一布局中的已登记节点，包括可实时更新的定位节点。 */
        public Builder relativeTo(AlignmentLayout.Node node) {
            target = new NodeTarget(Objects.requireNonNull(node));
            return this;
        }

        /** 相对屏幕 GUI 逻辑空间中的矩形。 */
        public Builder relativeTo(Rect rect) {
            target = new FixedTarget(Objects.requireNonNull(rect));
            return this;
        }

        /** 相对屏幕 GUI 逻辑空间中的指定点。点也可作为基线目标。 */
        public Builder relativeTo(Anchor anchor) {
            return relativeTo(new Rect(anchor.x(), anchor.y(), 0, 0));
        }

        /** 相对父节点。根节点使用布局的屏幕区域。 */
        public Builder relativeToParent() { target = ParentTarget.PARENT; return this; }
        public Builder relativeToScreen() { target = ParentTarget.SCREEN; return this; }

        public Builder horizontal(Align align) { return horizontal(align, align); }

        /** 可以把自身左边与兄弟的右边对齐，配合 offset 留出间隔。 */
        public Builder horizontal(Align source, Align targetAlign) {
            horizontal = new AxisRule(horizontalAlign(source), horizontalAlign(targetAlign));
            return this;
        }

        public Builder vertical(Align align) { return vertical(align, align); }

        public Builder vertical(Align source, Align targetAlign) {
            vertical = new AxisRule(verticalAlign(source), verticalAlign(targetAlign));
            return this;
        }

        public Builder bounds(Bounds value) { bounds = Objects.requireNonNull(value); return this; }

        /** 对 VISUAL 中心使用估算重心及度量自带的人工修正。 */
        public Builder opticalCorrection(boolean value) { optical = value; return this; }

        /** 当前关系额外的源中心修正，以屏幕 GUI 单位计。也可单独使用。 */
        public Builder opticalOffset(double x, double y) { opticalOffset = new Anchor(x, y); return this; }

        /** 在锚点匹配之后额外移动的屏幕 GUI 距离。 */
        public Builder offset(double x, double y) { offset = new Anchor(x, y); return this; }

        public Builder reference(Reference value) { reference = Objects.requireNonNull(value); return this; }

        /** 同轴取最高优先级。同优先级时先登记的约束获胜。 */
        public Builder priority(int value) { priority = value; return this; }

        /** 登记不可变约束。关闭返回的句柄可移除该关系。 */
        public AlignmentLayout.Constraint apply() {
            if (horizontal == null && vertical == null) throw new IllegalStateException("Alignment has no axis");
            return component.add(new Rule(target, horizontal, vertical, bounds, optical, opticalOffset,
                    offset, reference, priority));
        }

        private static Align horizontalAlign(Align value) {
            return switch (value) {
                case LEFT, CENTER, RIGHT, START, END -> value;
                default -> throw new IllegalArgumentException("Invalid horizontal alignment: " + value);
            };
        }

        private static Align verticalAlign(Align value) {
            return switch (value) {
                case TOP, CENTER, BOTTOM, BASELINE -> value;
                default -> throw new IllegalArgumentException("Invalid vertical alignment: " + value);
            };
        }
    }
}
