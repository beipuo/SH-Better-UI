package com.dishanhai.sh_better_ui.client.gui.layout;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiMath;
import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentLayout.View;
import com.dishanhai.sh_better_ui.client.gui.layout.PixelAlignment.Snap;
import com.dishanhai.sh_better_ui.client.gui.layout.VisualAlignment.Bounds;

import static com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.*;

/** 山海：逻辑定位与渲染姿态的未吸附结果。 */
record AlignmentPlacement(AlignmentFrame frame, Transform layout, Transform world, Rect ancestorClip) {
    static AlignmentPlacement measure(AlignmentFrame frame, AlignmentPlacement parent, boolean keepAnchor) {
        Rect rect = frame.layout().rect();
        Transform origin = Transform.translation(rect.x(), rect.y());
        Transform layout = parent == null ? origin : parent.world.compose(origin);
        Transform pose = keepAnchor ? frame.pose().around(frame.layout().anchor()) : frame.pose();
        return new AlignmentPlacement(frame, layout, layout.compose(pose), parent == null ? null : parent.clip());
    }

    AlignmentPlacement move(double dx, double dy) {
        if (dx == 0 && dy == 0) return this;
        return new AlignmentPlacement(frame, layout.move(dx, dy), world.move(dx, dy), ancestorClip);
    }

    Rect contentArea(boolean followPose) {
        return (followPose ? world : layout).map(frame.layout().local().inset(frame.layout().padding()));
    }

    Rect bounds(Bounds kind, boolean target) {
        return switch (kind) {
            case LAYOUT -> target ? contentArea(false) : layout.map(frame.layout().local().outset(frame.layout().margin()));
            case RENDER -> world.map(frame.render());
            case VISUAL -> target ? clipped(world.map(frame.visual().ink()), clip()) : world.map(localVisible());
            case HIT -> hitTransform().map(frame.hit());
        };
    }

    Rect occupied() {
        Rect visible = localVisible();
        return world.map(visible.empty() ? visible : visible.outset(frame.layout().margin()));
    }

    Double baseline() {
        Double baseline = frame.visual().baseline();
        return baseline == null ? null : world.map(new Anchor(frame.visual().content().center().x(), baseline)).y();
    }

    Anchor opticalCenter(boolean target) {
        return opticalCenter(world, target ? clip() : null);
    }

    View view(PixelAlignment grid, Snap snap, Rect clamp) {
        Anchor localAnchor = frame.layout().anchor();
        Transform placed = grid.apply(world, localAnchor, snap);
        double dx = placed.tx() - world.tx(), dy = placed.ty() - world.ty();
        if (clamp != null && !contains(clamp, occupied(), dx, dy)) {
            placed = world;
            dx = 0;
            dy = 0;
        }
        // 裁剪是布局容器的屏幕空间 scissor。父、子共用同一份未吸附裁剪。
        Rect clip = clip();
        Transform hit = frame.hitFollowsPose() ? placed : layout.move(dx, dy);
        return new View(layout.move(dx, dy).map(frame.layout().local()), placed.map(frame.render()),
                clipped(placed.map(frame.visual().ink()), clip), clipped(hit.map(frame.hit()), clip), clip,
                placed.map(localAnchor), opticalCenter(placed, clip), placed, hit, frame.hit());
    }

    private Transform hitTransform() { return frame.hitFollowsPose() ? world : layout; }

    private static boolean contains(Rect outer, Rect inner, double dx, double dy) {
        return inner.empty() || (inner.x() + dx >= outer.x() && inner.right() + dx <= outer.right()
                && inner.y() + dy >= outer.y() && inner.bottom() + dy <= outer.bottom());
    }

    private Anchor opticalCenter(Transform placed, Rect screenClip) {
        var visual = frame.visual();
        Anchor center = constrain(visual.centroid(), frame.clip());
        Anchor correction = visual.correction();
        Anchor visibleCenter = constrain(placed.map(center), screenClip);
        Anchor corrected = placed.map(new Anchor(center.x() + correction.x(), center.y() + correction.y()));
        Anchor mapped = placed.map(center);
        return new Anchor(visibleCenter.x() + corrected.x() - mapped.x(),
                visibleCenter.y() + corrected.y() - mapped.y());
    }

    private Rect localVisible() { return clipped(frame.visual().ink(), frame.clip()); }

    private Rect clip() {
        return frame.clip() == null ? ancestorClip : clipped(world.map(frame.clip()), ancestorClip);
    }

    private static Rect clipped(Rect rect, Rect clip) { return clip == null ? rect : rect.intersect(clip); }

    private static Anchor constrain(Anchor center, Rect clip) {
        return clip == null ? center : new Anchor(UiMath.clamp(center.x(), clip.x(), clip.right()),
                UiMath.clamp(center.y(), clip.y(), clip.bottom()));
    }
}
