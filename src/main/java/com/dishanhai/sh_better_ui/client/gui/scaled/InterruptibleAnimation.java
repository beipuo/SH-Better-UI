package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.ArrayDeque;
import java.util.Objects;

/**
 * 山海：一个控件属性的独立动画轨道。事件与绘制在客户端线程使用同一单调毫秒时钟，
 * 例如 {@link PageTransition#nowMs()}。所有者可以销毁轨道并使尚未领取的完成事件失效。
 */
public final class InterruptibleAnimation implements AutoCloseable {
    public enum Policy {
        /** 直接设置值，用于首帧布局、拖拽跟手等必须立即生效的操作。 */
        IMMEDIATE,
        /** 保留取样位置和速度，适用于悬停、滚动及布局目标变化。 */
        RETARGET,
        /** 转向给定的反向端点，初速朝向新端点，保留速度大小。 */
        REVERSE,
        /** 从取样值平滑混合到新值，默认使用 180ms 的 smoothstep。 */
        BLEND,
        /** 当前阶段完成后按提交顺序执行，最多积压八个阶段。 */
        QUEUE,
        /** 忙碌时只允许严格更高优先级的请求替换，空闲时按 RETARGET 执行。 */
        IGNORE,
        /** 等当前阶段完成，只保留最新后继阶段。 */
        FINISH_THEN_SWITCH
    }

    public enum BlendCurve { LINEAR, SMOOTHSTEP }

    private static final int QUEUE_LIMIT = 8;
    private static final long DEFAULT_BLEND_MS = 180L;

    private record Phase(float target, int priority, long revision) {}
    private record Blend(float from, float to, long startMs, long durationMs, BlendCurve curve) {}

    private final Spring spring;
    private ArrayDeque<Phase> queued;
    private Blend blend;
    private double blendProgress;
    private float blendVelocity;
    private int priority = Integer.MIN_VALUE;
    private long nextRevision;
    private long activeRevision;
    private boolean closed;

    public InterruptibleAnimation(Spring.Preset preset) {
        this(preset, 0f);
    }

    /** 使用有限初值；实例创建时静止，不自动播放。 */
    public InterruptibleAnimation(Spring.Preset preset, float initial) {
        // 大帧间隔最多追赶 250ms，保留原有 Spring 调用方的 50ms 策略。
        this.spring = new Spring(preset, Float.isFinite(initial) ? initial : 0f, 0.25);
    }

    /** 所有者直接改目标，保留位置及速度，取消策略队列和完成事件。 */
    public void animateTo(float target, long nowMs) {
        if (closed || !Float.isFinite(target)) return;
        advance(nowMs);
        retarget(target, nowMs, false);
        clearCommands();
    }

    /** 替换待生效目标；等待期间继续当前弹簧，混合轨迹则从当前值和速度接续为弹簧。 */
    public void animateToAfter(float target, long nowMs, long delayMs) {
        if (closed || !Float.isFinite(target)) return;
        if (delayMs <= 0L) {
            animateTo(target, nowMs);
            return;
        }
        advance(nowMs);
        if (blend != null) retarget(blend.to(), nowMs, false);
        spring.setTargetAfter(target, nowMs, delayMs);
        clearCommands();
    }

    /**
     * 提交同一属性的目标。低于当前或待执行阶段优先级时拒绝，空闲时重新仲裁。
     * 返回非零完成代号，拒绝、关闭及无效值返回 0。
     */
    public long transitionTo(float target, long nowMs, Policy policy, int priority) {
        return submit(target, nowMs, Objects.requireNonNull(policy), priority, DEFAULT_BLEND_MS, BlendCurve.SMOOTHSTEP);
    }

    /** 从实时值开始执行定时混合。每个属性可独立选用时长和曲线。 */
    public long blendTo(float target, long nowMs, int priority, long durationMs, BlendCurve curve) {
        if (durationMs <= 0L) throw new IllegalArgumentException();
        return submit(target, nowMs, Policy.BLEND, priority, durationMs, Objects.requireNonNull(curve));
    }

    private long submit(float target, long nowMs, Policy policy, int requestedPriority,
                        long durationMs, BlendCurve curve) {
        if (closed || !Float.isFinite(target)) return 0L;
        advance(nowMs);
        boolean busy = running();
        if (busy && (requestedPriority < priority
                || (policy == Policy.IGNORE && requestedPriority == priority))) return 0L;
        if (hasQueued()) {
            for (Phase phase : queued) {
                if (requestedPriority < phase.priority()
                        || (policy == Policy.IGNORE && requestedPriority == phase.priority())) return 0L;
            }
        }
        if (busy && policy == Policy.QUEUE && queued != null && queued.size() == QUEUE_LIMIT) return 0L;

        long revision = ++nextRevision;
        if (busy && (policy == Policy.QUEUE || policy == Policy.FINISH_THEN_SWITCH)) {
            if (queued == null) queued = new ArrayDeque<>(QUEUE_LIMIT);
            if (policy == Policy.FINISH_THEN_SWITCH) queued.clear();
            queued.addLast(new Phase(target, requestedPriority, revision));
            return revision;
        }
        if (queued != null) queued.clear();
        priority = requestedPriority;
        activeRevision = revision;
        switch (policy) {
            case IMMEDIATE -> {
                blend = null;
                spring.snap(target);
            }
            case BLEND -> {
                if (blend != null && blend.to() == target && blend.durationMs() == durationMs
                        && blend.curve() == curve) return revision;
                float from = value();
                spring.snap(from);
                blend = from == target ? null : new Blend(from, target, nowMs, durationMs, curve);
                blendProgress = 0d;
                blendVelocity = blend == null ? 0f : blendVelocity(blend, 0d);
            }
            default -> retarget(target, nowMs, policy == Policy.REVERSE);
        }
        return revision;
    }

    private void retarget(float target, long nowMs, boolean reverse) {
        if (blend == null && (!reverse || target == spring.target())) {
            spring.setTarget(target, nowMs);
            return;
        }
        float speed = velocity();
        if (reverse) speed = target == value() ? 0f : Math.copySign(Math.abs(speed), target - value());
        blend = null;
        spring.setTarget(target, speed, nowMs);
    }

    /**
     * 在取样后领取一次当前目标的完成事件。新目标、取消和销毁都会使旧代号失效。
     * 队列只报告最终目标完成，中间阶段由轨道自行推进。
     */
    public boolean takeCompletion(long revision) {
        if (revision == 0L || revision != activeRevision || running()) return false;
        activeRevision = 0L;
        return true;
    }

    public void setOpen(boolean open, long nowMs) {
        animateTo(open ? 1f : 0f, nowMs);
    }

    /** 仅用于 0..1 开关：最终目标小于 0.5 时打开，否则关闭，包含尚未生效的目标。 */
    public void toggle(long nowMs) {
        setOpen(target() < 0.5f, nowMs);
    }

    /** 停在命令时刻的位置，清零速度并取消延迟目标。 */
    public void stop(long nowMs) {
        if (closed) return;
        advance(nowMs);
        blend = null;
        spring.snap(spring.value());
        clearCommands();
    }

    /** 立即到达最终目标，包含尚未生效的延迟目标。 */
    public void finish() {
        if (closed) return;
        float destination = target();
        if (hasQueued()) {
            activeRevision = queued.getLast().revision();
            queued.clear();
        }
        blend = null;
        spring.snap(destination);
    }

    /** 立即设置静止值并取消所有运动；可在控件移除时重置，非有限值忽略。 */
    public void snapTo(float value) {
        if (closed || !Float.isFinite(value)) return;
        blend = null;
        spring.snap(value);
        clearCommands();
    }

    /** 每帧取样；同一时间重复调用不重复积分。 */
    public float sample(long nowMs) {
        if (running()) advance(nowMs);
        return spring.value();
    }

    /** 减少动效时直接到达最终目标，跳过运动与等待。 */
    public float sample(long nowMs, boolean reducedMotion) {
        if (reducedMotion) finish();
        return sample(nowMs);
    }

    public float sample(long nowMs, UiPreferences preferences) {
        return sample(nowMs, preferences.reducedMotion());
    }

    /** 上次取样的位置，不推进时间。 */
    public float value() {
        return spring.value();
    }

    /** 最终目的地，包含尚未生效的延迟目标。 */
    public float target() {
        if (hasQueued()) return queued.getLast().target();
        return blend == null ? spring.destination() : blend.to();
    }

    public float velocity() {
        return blend == null ? spring.velocity() : blendVelocity;
    }

    /** 有运动或延迟目标时返回 true；仅查询，不推进时间。 */
    public boolean running() {
        return blend != null || !spring.settled() || hasQueued();
    }

    private void advance(long nowMs) {
        if (blend == null) {
            spring.update(nowMs);
        } else {
            double progress = UiMath.clamp(((double) nowMs - blend.startMs()) / blend.durationMs(), blendProgress, 1d);
            blendProgress = progress;
            double eased = blend.curve() == BlendCurve.LINEAR ? progress : progress * progress * (3d - 2d * progress);
            if (progress >= 1d) {
                spring.snap(blend.to());
                blend = null;
            } else {
                spring.snap((float) (blend.from() + ((double) blend.to() - blend.from()) * eased));
                blendVelocity = blendVelocity(blend, progress);
            }
        }
        while (blend == null && spring.settled() && hasQueued()) {
            Phase phase = queued.removeFirst();
            priority = phase.priority();
            activeRevision = phase.revision();
            spring.setTarget(phase.target(), nowMs);
        }
    }

    private static float blendVelocity(Blend blend, double progress) {
        double slope = blend.curve() == BlendCurve.LINEAR ? 1d : 6d * progress * (1d - progress);
        double speed = ((double) blend.to() - blend.from()) * slope * 1000d / blend.durationMs();
        return (float) UiMath.clamp(speed, -Float.MAX_VALUE, Float.MAX_VALUE);
    }

    private boolean hasQueued() {
        return queued != null && !queued.isEmpty();
    }

    private void clearCommands() {
        if (queued != null) queued.clear();
        priority = Integer.MIN_VALUE;
        activeRevision = 0L;
    }

    /** 释放此轨道。后续命令无效，取样保持最后一次绘制值。 */
    @Override
    public void close() {
        snapTo(value());
        closed = true;
    }
}
