package com.dishanhai.sh_better_ui.client.gui.scaled;

/**
 * 山海：可中途改目标的单弹簧。先推进到命令时间，再保留 value 与 velocity 改目标，不会跳也不会重播。
 *
 * <p>显式积分，限制单次追赶的时间并拆成不超过 4ms 的子步。
 * 时间用毫秒，调用方应始终使用同一时钟。</p>
 */
public final class Spring {

    /**
     * @param response       近似到位时间（秒），越小越快
     * @param damping        阻尼比，1.0 为临界阻尼（不回弹），0.92 有极轻微回弹
     * @param settleEps      离目标小于它才可能判定静止（与 value 同单位）
     * @param settleVelocity 速度小于它才可能判定静止
     */
    public record Preset(double response, double damping, float settleEps, float settleVelocity) {}

    /** 卡片、页签、按钮 hover。 */
    public static final Preset HOVER = new Preset(0.10, 1.0, 0.0015f, 0.02f);
    /** 浮层开关、开店、其他界面开场。 */
    public static final Preset PANEL = new Preset(0.20, 0.92, 0.0015f, 0.02f);
    /** 胶囊与面板共用的过阻尼进度，从静止出发时不越过目标。 */
    public static final Preset FLUID_MORPH = new Preset(0.16, 1.12, 0.00035f, 0.02f);
    public static final Preset FLUID_PANEL = new Preset(0.42, FLUID_MORPH.damping(), 0.002f,
            FLUID_MORPH.settleVelocity());
    /** 页签高亮块的 x、宽（像素量级）。 */
    public static final Preset INDICATOR = new Preset(0.18, 0.92, 0.05f, 1.0f);
    /** 网格、详情列滚动（像素量级）。 */
    public static final Preset SCROLL = new Preset(0.16, 1.0, 0.05f, 1.0f);
    /** 卡片错峰入场，无回弹，透明度不会超过 1。 */
    public static final Preset STAGGER = new Preset(0.20, 1.0, 0.0015f, 0.02f);

    private static final double MAX_STEP_S = 0.05;
    private static final double SUBSTEP_S = 0.004;

    private final double stiffness;
    private final double drag;
    private final double maxStepSeconds;
    private final float settleEps;
    private final float settleVelocity;

    private float value;
    private float velocity;
    private float target;
    private long lastMs;
    private boolean settled = true;

    private boolean hasPending;
    private float pendingTarget;
    private long pendingAtMs;

    public Spring(Preset preset) {
        this(preset, 0f);
    }

    public Spring(Preset preset, float initial) {
        this(preset, initial, MAX_STEP_S);
    }

    Spring(Preset preset, float initial, double maxStepSeconds) {
        double omega = 2.0 * Math.PI / preset.response();
        this.stiffness = omega * omega;
        this.drag = 2.0 * preset.damping() * omega;
        this.maxStepSeconds = maxStepSeconds;
        this.settleEps = preset.settleEps();
        this.settleVelocity = preset.settleVelocity();
        this.value = initial;
        this.target = initial;
    }

    /** 开场弹簧：从 0 起弹向 1，起点取当前 {@code System.currentTimeMillis()}。字段初始化里直接用。 */
    public static Spring opening(Preset preset) {
        return opening(preset, System.currentTimeMillis());
    }

    /** 开场弹簧，起点与后续取样使用调用方的同一个时钟。 */
    public static Spring opening(Preset preset, long nowMs) {
        Spring s = new Spring(preset);
        s.setTarget(1f, nowMs);
        return s;
    }

    /** 立刻改目标，value 与 velocity 保持。非有限值忽略。 */
    public void setTarget(float newTarget, long nowMs) {
        if (!Float.isFinite(newTarget)) return;
        update(nowMs);
        retarget(newTarget, nowMs);
    }

    /** 手势放开时，用测得的初速度接续当前位置。速度单位为 value/秒。 */
    void setTarget(float newTarget, float initialVelocity, long nowMs) {
        if (!Float.isFinite(newTarget) || !Float.isFinite(initialVelocity)) return;
        update(nowMs);
        this.velocity = initialVelocity;
        retarget(newTarget, nowMs);
    }

    private void retarget(float newTarget, long nowMs) {
        if (this.settled) this.lastMs = nowMs;
        this.hasPending = false;
        this.target = newTarget;
        this.settled = reachedTarget();
        if (this.settled) {
            this.value = this.target;
            this.velocity = 0f;
        }
    }

    /** {@code delayMs} 之后才开始朝新目标走，之前保持当前轨迹（错峰用）。{@code delayMs<=0} 等同 {@link #setTarget}。 */
    public void setTargetAfter(float newTarget, long nowMs, long delayMs) {
        if (delayMs <= 0L) {
            setTarget(newTarget, nowMs);
            return;
        }
        if (!Float.isFinite(newTarget)) return;
        update(nowMs);
        if (this.settled) this.lastMs = nowMs;
        this.hasPending = true;
        this.pendingTarget = newTarget;
        this.pendingAtMs = nowMs > Long.MAX_VALUE - delayMs ? Long.MAX_VALUE : nowMs + delayMs;
    }

    /** value=目标=给定值，速度清零，立即静止，挂起的延迟目标作废。 */
    public void snap(float v) {
        if (!Float.isFinite(v)) return;
        this.value = v;
        this.target = v;
        this.velocity = 0f;
        this.hasPending = false;
        this.settled = true;
    }

    /** 推进到 {@code nowMs}。静止后重复调用无副作用。 */
    public void update(long nowMs) {
        if ((this.settled && !this.hasPending) || nowMs <= this.lastMs) return;
        if (this.hasPending && nowMs >= this.pendingAtMs) {
            advanceTo(this.pendingAtMs);
            this.hasPending = false;
            this.target = this.pendingTarget;
            this.settled = reachedTarget();
            if (this.settled) {
                this.value = this.target;
                this.velocity = 0f;
            }
        }
        advanceTo(nowMs);
    }

    private void advanceTo(long nowMs) {
        if (nowMs <= this.lastMs) return;
        double dt = Math.min((nowMs - (double) this.lastMs) / 1000.0, maxStepSeconds);
        this.lastMs = nowMs;
        if (this.settled) return;

        int steps = Math.max(1, (int) Math.ceil(dt / SUBSTEP_S));
        double h = dt / steps;
        for (int i = 0; i < steps; i++) {
            double accel = -this.stiffness * (this.value - this.target) - this.drag * this.velocity;
            this.velocity += (float) (accel * h);
            this.value += (float) (this.velocity * h);
        }

        if (!Float.isFinite(this.value) || !Float.isFinite(this.velocity) || reachedTarget()) {
            this.value = this.target;
            this.velocity = 0f;
            this.settled = true;
        }
    }

    public float value() {
        return this.value;
    }

    public float target() {
        return this.target;
    }

    /** 最终目的地：存在延迟目标时返回延迟目标，否则返回当前物理目标。 */
    public float destination() {
        return this.hasPending ? this.pendingTarget : this.target;
    }

    /** 当前速度，单位为 value/秒；改目标不会清零。 */
    public float velocity() {
        return this.velocity;
    }

    public boolean settled() {
        return this.settled && !this.hasPending;
    }

    private boolean reachedTarget() {
        return Math.abs(this.value - this.target) < this.settleEps
                && Math.abs(this.velocity) < this.settleVelocity;
    }
}
