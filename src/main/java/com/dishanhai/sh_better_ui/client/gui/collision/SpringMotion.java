package com.dishanhai.sh_better_ui.client.gui.collision;

import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;

/** 山海：GUI 单位的单通道阻尼运动，沿用共享 Spring 的时间步限制。 */
public final class SpringMotion {
    private final Spring spring;

    public SpringMotion(float initial) {
        if (!Float.isFinite(initial)) throw new IllegalArgumentException("Non-finite spring position");
        spring = new Spring(Spring.SCROLL, initial);
    }

    public void retarget(float target, long nowMs) {
        if (target != spring.target()) spring.setTarget(target, nowMs);
    }

    public void update(long nowMs) { spring.update(nowMs); }
    public void snap(float value) { spring.snap(value); }
    public float value() { return spring.value(); }
    public float velocity() { return spring.velocity(); }
    public boolean settled() { return spring.settled(); }
}
