package com.dishanhai.sh_better_ui.client.gui.scaled;

/**
 * 山海：控件独享的可打断动画入口，复用 {@link Spring}，不持有屏幕或全局状态。
 * 开关、反向及换目标均保留命令时刻的位置和速度；只有 stop、finish、snapTo 会清零速度。
 *
 * <p>事件与绘制必须传入同一单调毫秒时钟，例如 {@link PageTransition#nowMs()}。
 * 值可表示进度、像素或其他标量；仅 setOpen/toggle 约定关闭为 0、打开为 1。
 * 每个控件持有一个实例，在客户端线程调用。</p>
 */
public final class InterruptibleAnimation {
    private final Spring spring;

    public InterruptibleAnimation(Spring.Preset preset) {
        this(preset, 0f);
    }

    /** 使用有限初值；实例创建时静止，不自动播放。 */
    public InterruptibleAnimation(Spring.Preset preset, float initial) {
        this.spring = new Spring(preset, Float.isFinite(initial) ? initial : 0f);
    }

    /** 推进旧轨迹至命令时间，再替换目标；非有限目标忽略。 */
    public void animateTo(float target, long nowMs) {
        spring.setTarget(target, nowMs);
    }

    /** 替换待生效目标；等待期间继续旧轨迹，delayMs<=0 时立即生效。 */
    public void animateToAfter(float target, long nowMs, long delayMs) {
        spring.setTargetAfter(target, nowMs, delayMs);
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
        spring.update(nowMs);
        spring.snap(spring.value());
    }

    /** 立即到达最终目标，包含尚未生效的延迟目标。 */
    public void finish() {
        spring.snap(spring.destination());
    }

    /** 立即设置静止值并取消所有运动；可在控件移除时重置，非有限值忽略。 */
    public void snapTo(float value) {
        spring.snap(value);
    }

    /** 每帧取样；同一时间重复调用不重复积分。 */
    public float sample(long nowMs) {
        spring.update(nowMs);
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
        return spring.destination();
    }

    public float velocity() {
        return spring.velocity();
    }

    /** 有运动或延迟目标时返回 true；仅查询，不推进时间。 */
    public boolean running() {
        return !spring.settled();
    }
}
