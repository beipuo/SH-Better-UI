package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.blur.GaussBlur.RevealDirection;
import com.dishanhai.sh_better_ui.client.blur.GlassStyle;
import com.dishanhai.sh_better_ui.client.glass.GlassBlur;
import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/** 山海：四方向换页，可在同一个视口内绘制平移、立体底板及可选的模糊和玻璃。 */
public final class UiPageSlide {
    public static final long DURATION_MS = 320L;
    private static final float PITCH = 16f;
    private static final float YAW = 18f;
    private static final float DEPTH_SCALE = 0.08f;
    private static final float CORNER = 6f;

    /** 当前页进入视口时的移动方向，例如 LEFT 表示由右向左。 */
    public enum Direction {
        LEFT(-1, 0, RevealDirection.RIGHT_TO_LEFT),
        RIGHT(1, 0, RevealDirection.LEFT_TO_RIGHT),
        UP(0, -1, RevealDirection.BOTTOM_TO_TOP),
        DOWN(0, 1, RevealDirection.TOP_TO_BOTTOM);

        private final int x;
        private final int y;
        private final RevealDirection reveal;

        Direction(int x, int y, RevealDirection reveal) {
            this.x = x;
            this.y = y;
            this.reveal = reveal;
        }

        int axisX() { return x; }

        int axisY() { return y; }
    }

    public enum Motion { FLAT, DEPTH }

    /** 实时模糊采样当前背景；渐进式模糊在前景绘制后从进入边缘向另一侧递减。 */
    public record Effects(boolean progressiveBlur, boolean realtimeBlur, GlassStyle glassStyle) {
        public static final Effects NONE = new Effects(false, false, GlassStyle.NONE);

        public Effects {
            Objects.requireNonNull(glassStyle);
        }
    }

    /** 交互控件只使用整数位移，底板与非交互预览可另外应用 pitch、yaw 和 scale。 */
    public record Frame(int offsetX, int offsetY, float progress, Direction direction, Motion motion,
                        float pitch, float yaw, float scale, float effectAmount) {
        public boolean active() {
            return progress < 1f;
        }

        /** 以指定点为中心变换非交互内容。调用方负责保存与恢复 pose。 */
        public void applyDepth(PoseStack pose, float centerX, float centerY) {
            if (!hasDepth()) return;
            pose.translate(centerX, centerY, 0f);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.scale(scale, scale, scale);
            pose.translate(-centerX, -centerY, 0f);
        }

        private boolean hasDepth() {
            return pitch != 0f || yaw != 0f || scale != 1f;
        }
    }

    private static final Frame STILL = new Frame(0, 0, 1f, Direction.RIGHT, Motion.FLAT, 0f, 0f, 1f, 0f);
    private Frame origin = STILL;
    private Direction direction = Direction.RIGHT;
    private Motion motion = Motion.FLAT;
    private long startedAt;
    private int distance;
    private boolean steering;

    /** 事件和绘制使用同一个单调毫秒时钟。重定向从此刻的可见位置和立体形状继续。 */
    public void start(Direction direction, Motion motion, int distance, long nowMs, UiPreferences preferences) {
        Objects.requireNonNull(direction);
        Objects.requireNonNull(motion);
        if (distance < 0) throw new IllegalArgumentException();
        Frame previous = sample(nowMs, preferences);
        if (distance == 0 || preferences.reducedMotion()) {
            origin = STILL;
            return;
        }
        this.direction = direction;
        this.motion = motion;
        this.distance = distance;
        startedAt = nowMs;
        steering = previous.active() && (previous.direction() != direction || previous.motion() != motion);
        boolean depth = motion == Motion.DEPTH;
        origin = previous.active() ? previous : new Frame(-direction.x * distance, -direction.y * distance,
                0f, direction, motion, depth ? direction.y * PITCH : 0f, depth ? -direction.x * YAW : 0f,
                depth ? 1f - DEPTH_SCALE : 1f, 1f);
    }

    /** 已完成或减少动效时返回零位移、零倾角的最终帧。 */
    public Frame sample(long nowMs, UiPreferences preferences) {
        if (!origin.active() || preferences.reducedMotion()) {
            origin = STILL;
            return STILL;
        }
        var effect = ThemeEffects.local("pageSlide");
        float t = effect.enabled() ? Math.max(0L, nowMs - startedAt) * effect.speed() / DURATION_MS : 1f;
        if (t >= 1f) {
            origin = STILL;
            return STILL;
        }
        float progress = t * t * (3f - 2f * t);
        float remaining = 1f - progress;
        // 改方向时从当前帧连续转向，转向分量在起点和终点均为零。
        float turn = steering ? progress * remaining : 0f;
        float depthTurn = motion == Motion.DEPTH ? turn : 0f;
        float distanceTurn = distance * turn;
        return new Frame(Math.round(origin.offsetX() * remaining - direction.x * distanceTurn),
                Math.round(origin.offsetY() * remaining - direction.y * distanceTurn), progress, direction, motion,
                origin.pitch() * remaining + direction.y * PITCH * depthTurn,
                origin.yaw() * remaining - direction.x * YAW * depthTurn,
                1f - ((1f - origin.scale()) * remaining + DEPTH_SCALE * depthTurn),
                GuiMotion.clamp01(origin.effectAmount() * remaining + turn));
    }

    /** 在渲染线程裁切并绘制当前页一次。事件命中使用绘制时同一帧的整数位移。 */
    public static void draw(GuiGraphics graphics, Bounds viewport, Frame frame, UiPreferences preferences,
                            Effects effects, Runnable drawPage) {
        if (viewport.width() == 0 || viewport.height() == 0) return;
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());
        try {
            if (!frame.active() || preferences.reducedMotion()) {
                drawPage.run();
                return;
            }
            var region = new GuiRect(viewport.x() + frame.offsetX(), viewport.y() + frame.offsetY(),
                    viewport.width(), viewport.height());
            float amount = frame.effectAmount();
            boolean glass = effects.glassStyle() != GlassStyle.NONE;
            boolean canBlur = preferences.useBackdropBlur();
            boolean sampled = canBlur && (effects.realtimeBlur() || glass)
                    && GlassBlur.tryBackdrop(graphics, region, amount, effects.glassStyle(),
                    effects.realtimeBlur() ? 8f : 0f, CORNER);

            var pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(frame.offsetX(), frame.offsetY(), 0f);
                if (frame.hasDepth() || (glass && !sampled)) drawPlate(graphics, viewport, frame);
                drawPage.run();
            } finally {
                pose.popPose();
            }
            if (canBlur && effects.progressiveBlur()) {
                var profile = RevealProfile.DEFAULT;
                GlassBlur.revealRegion(graphics, region, profile.maxRadius() * amount,
                        profile.fullBlurUntil(), profile.sharpFrom(), frame.direction().reveal);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private static void drawPlate(GuiGraphics graphics, Bounds viewport, Frame frame) {
        var pose = graphics.pose();
        var base = pose.last().pose();
        float zFromX = base.m02(), zFromY = base.m12(), z = base.m32();
        pose.pushPose();
        try {
            frame.applyDepth(pose, viewport.x() + viewport.width() * 0.5f, viewport.y() + viewport.height() * 0.5f);
            // 保留旋转的 XY 投影，并让底板停在原来的绘制层，避免突出的一侧遮住原生控件。
            pose.last().pose().m02(zFromX).m12(zFromY).m32(z);
            float amount = frame.effectAmount();
            int edge = GuiMotion.lerpColor(0x009ACDFF, 0x669ACDFF, amount);
            int fill = GuiMotion.lerpColor(0x001D232C, 0x521D232C, amount);
            RoundedCorners.draw(graphics, viewport.x(), viewport.y(), viewport.width(), viewport.height(),
                    CORNER, 1f, edge, fill, false);
        } finally {
            pose.popPose();
        }
    }
}
