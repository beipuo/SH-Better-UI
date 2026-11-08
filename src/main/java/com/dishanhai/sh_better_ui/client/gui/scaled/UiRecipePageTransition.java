package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
import com.dishanhai.sh_better_ui.client.glass.GlassMotion;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPageSlide.Direction;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/** 山海：复用 gt_shanhai 配方修改器的双页滑动、速度模糊及侧栏遮罩。每个视口持有一个实例。 */
public final class UiRecipePageTransition {
    private static final float COMPANION_RISE = 6f;

    public record Frame(float progress, float incomingX, float incomingY, float outgoingX, float outgoingY,
                        GlassMotion.Sample blur) {
        public boolean active() { return progress < 1f; }

        /** 固定侧栏在中点换内容，新内容从下方抬升。遮罩使用侧栏自己的底色。 */
        public Companion companion() {
            boolean incoming = progress >= 0.5f;
            float veil = 1f - Math.abs(2f * progress - 1f);
            return new Companion(incoming, incoming ? COMPANION_RISE * veil : 0f, veil);
        }
    }

    public record Companion(boolean incoming, float offsetY, float veilOpacity) {}

    private final PageTransition timeline = new PageTransition();
    private Direction direction = Direction.LEFT;
    private float distance;
    private float startX;
    private float startY;
    private float startProgress;
    private float startBlur;
    private float blurRadius = GlassMotion.MOTION_RADIUS_VIRTUAL;

    /** 双页相邻滑动。同一视口反向时可交换两页并延续当前位置，绘制回调应只负责呈现。 */
    public void start(Direction direction, Bounds viewport, long nowMs, UiPreferences preferences) {
        if (viewport.width() == 0 || viewport.height() == 0) {
            timeline.reset(0, nowMs);
            return;
        }
        float distance = direction.axisX() != 0 ? viewport.width() : viewport.height();
        begin(direction, distance, nowMs, preferences, true);
    }

    /** 仅新页入场。连续切页沿当前轨迹完成，适用于立即卸载旧控件的标签页。 */
    public void enter(Direction direction, float distance, long nowMs, UiPreferences preferences) {
        begin(direction, distance, nowMs, preferences, false);
    }

    private void begin(Direction direction, float distance, long nowMs, UiPreferences preferences, boolean paired) {
        Objects.requireNonNull(direction);
        if (!Float.isFinite(distance) || distance < 0f) throw new IllegalArgumentException("Invalid slide distance");
        Frame previous = sample(nowMs, preferences);
        if (!paired && previous.active() && distance > 0f) return;
        timeline.reset(0, nowMs);
        if (distance == 0f || preferences.reducedMotion()) return;

        boolean reversing = paired && previous.active() && this.distance == distance
                && this.direction.axisX() == -direction.axisX() && this.direction.axisY() == -direction.axisY();
        startX = reversing ? previous.outgoingX() : -direction.axisX() * distance;
        startY = reversing ? previous.outgoingY() : -direction.axisY() * distance;
        startProgress = reversing ? 1f - previous.progress() : 0f;
        startBlur = reversing ? previous.blur().getStrength() : 0f;
        this.direction = direction;
        this.distance = distance;
        blurRadius = paired ? GlassMotion.MOTION_RADIUS_VIRTUAL : GlassMotion.MOTION_RADIUS_VIRTUAL / 2f;
        if (startX != 0f || startY != 0f) timeline.advance(1, nowMs);
    }

    /** 事件和绘制使用同一个单调毫秒时钟，例如 {@link PageTransition#nowMs()}。 */
    public Frame sample(long nowMs, UiPreferences preferences) {
        if (preferences.reducedMotion()) timeline.reset(0, nowMs);
        float eased = timeline.progress(nowMs);
        float remaining = 1f - eased;
        float x = startX * remaining;
        float y = startY * remaining;
        float speed = GlassMotion.speed(timeline.linear(nowMs));
        float strength = speed + (1f - speed) * startBlur * remaining;
        float maxAxis = Math.max(Math.abs(startX), Math.abs(startY));
        float axisX = maxAxis == 0f ? 0f : Math.abs(startX) / maxAxis;
        float axisY = maxAxis == 0f ? 0f : Math.abs(startY) / maxAxis;
        return new Frame(GuiMotion.lerp(startProgress, 1f, eased), x, y,
                x + direction.axisX() * distance, y + direction.axisY() * distance,
                new GlassMotion.Sample(strength, blurRadius, axisX, axisY));
    }

    /** 在固定视口内仅绘制当前页。交互坐标减去同一帧的 incomingX/Y。 */
    public static void draw(GuiGraphics graphics, Bounds viewport, Frame frame, UiPreferences preferences,
                            Runnable incoming) {
        draw(graphics, viewport, frame, preferences, null, incoming);
    }

    /** 旧页只做视觉过渡。交互和焦点由调用方管理，过渡完成后只绘制新页。 */
    public static void draw(GuiGraphics graphics, Bounds viewport, Frame frame, UiPreferences preferences,
                            Runnable outgoing, Runnable incoming) {
        if (viewport.width() == 0 || viewport.height() == 0) return;
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        try {
            if (!frame.active() || preferences.reducedMotion()) {
                incoming.run();
                return;
            }
            if (outgoing != null) drawAt(graphics, frame.outgoingX(), frame.outgoingY(), outgoing);
            drawAt(graphics, frame.incomingX(), frame.incomingY(), incoming);
            if (preferences.useBackdropBlur()) {
                GlassBlur.pageSlideNow(graphics, new GuiRect(viewport.x(), viewport.y(), viewport.width(), viewport.height()),
                        frame.blur());
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private static void drawAt(GuiGraphics graphics, float x, float y, Runnable draw) {
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(x, y, 0f);
            draw.run();
        } finally {
            pose.popPose();
        }
    }
}
