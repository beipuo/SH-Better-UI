package com.dishanhai.sh_better_ui.client.gui.scaled;

import java.util.Arrays;

/** 山海：反重力粒子的确定性 CPU 状态；不读取客户端、不持有 GPU 资源。 */
final class AntigravityField {
    private static final float TURN = (float) (Math.PI * 2);
    private static final float WORLD_HEIGHT = 31.5f;
    float[] x = new float[0], y = new float[0], vx = new float[0], vy = new float[0];
    float[] depth = new float[0], phase = new float[0], colorSeed = new float[0], sizeSeed = new float[0];
    float cursorX, cursorY, time, hue, charge, reveal;
    int width, height;
    private float[] homeX, homeY, band, agility, delay, orbit;
    private float cursorVx, cursorVy, targetX, targetY, introClock;

    /** 调整区域或数量时保留现有粒子的归一化位置、速度与轨道。 */
    void resize(int width, int height, AntigravityProfile profile) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (this.width == width && this.height == height && x.length == profile.count()) return;
        float[] oldX = x, oldY = y, oldVx = vx, oldVy = vy, oldOrbit = orbit;
        float scaleX = this.width > 0 ? width / (float) this.width : 1;
        float scaleY = this.height > 0 ? height / (float) this.height : 1;
        boolean fresh = oldX.length == 0;
        if (fresh) {
            cursorX = targetX = width * .5f;
            cursorY = targetY = height * .5f;
            introClock = profile.intro() ? 0 : 3;
            reveal = profile.intro() ? 0 : 1;
        } else {
            cursorX *= scaleX;
            cursorY *= scaleY;
            targetX *= scaleX;
            targetY *= scaleY;
            cursorVx *= scaleX;
            cursorVy *= scaleY;
        }
        this.width = width;
        this.height = height;
        int total = profile.count();
        x = new float[total]; y = new float[total]; vx = new float[total]; vy = new float[total];
        depth = new float[total]; phase = new float[total]; colorSeed = new float[total]; sizeSeed = new float[total];
        homeX = new float[total]; homeY = new float[total]; band = new float[total];
        agility = new float[total]; delay = new float[total]; orbit = new float[total];
        var random = new SeededRandom();
        float spanX = width * 1.12f, spanY = height * 1.12f;
        int columns = Math.max(1, (int) Math.ceil(Math.sqrt(total * (double) spanX / spanY)));
        int rows = Math.max(1, (int) Math.ceil(total / (double) columns));
        int[] cells = new int[columns * rows];
        for (int i = 0; i < cells.length; i++) cells[i] = i;
        for (int i = cells.length - 1; i > 0; i--) {
            int swap = (int) (random.next() * (i + 1));
            int cell = cells[i]; cells[i] = cells[swap]; cells[swap] = cell;
        }
        for (int i = 0; i < total; i++) depth[i] = random.next() * 2 - 1;
        Arrays.sort(depth);
        for (int i = 0; i < total; i++) {
            int cell = cells[i];
            homeX[i] = -width * .06f + ((cell % columns + .5f + (random.next() - .5f) * .85f) / columns) * spanX;
            homeY[i] = -height * .06f + ((cell / columns + .5f + (random.next() - .5f) * .85f) / rows) * spanY;
            band[i] = random.next() * 2 - 1;
            phase[i] = random.next() * TURN;
            agility[i] = .75f + random.next() * .5f;
            delay[i] = random.next();
            if (i < oldX.length) {
                x[i] = oldX[i] * scaleX;
                y[i] = oldY[i] * scaleY;
                vx[i] = oldVx[i] * scaleX;
                vy[i] = oldVy[i] * scaleY;
                orbit[i] = oldOrbit[i];
            } else {
                x[i] = homeX[i];
                y[i] = homeY[i] + (fresh && profile.intro() ? height * (.45f + delay[i] * .4f) : 0);
            }
            colorSeed[i] = random.next();
            sizeSeed[i] = random.next();
        }
    }

    void advance(AntigravityProfile profile, UiPointerTracker.Frame pointer, float dt, boolean reducedMotion) {
        if (x.length == 0) return;
        dt = Float.isFinite(dt) ? Math.clamp(dt, 0, 1f / 30) : 0;
        if (reducedMotion) {
            // 减少动效完全停止时间、光标漂移、蓄力及弹簧，只保留静态粒子环。
            cursorX = targetX = width * .5f;
            cursorY = targetY = height * .5f;
            cursorVx = cursorVy = charge = 0;
            introClock = 3;
            reveal = 1;
            simulate(profile, 0, true);
            return;
        }
        boolean interactive = profile.interactive() && pointer != null;
        if (!interactive || !profile.clickBurst() || pointer.cancelled()) {
            charge = 0;
        } else {
            if (pointer.released()) burst(profile, .45f + charge);
            charge = pointer.down() ? Math.min(1, charge + dt / .9f) : Math.max(0, charge - dt * 4);
        }
        time += dt;
        hue = (hue + dt * profile.colorSpeed()) % 1;
        introClock = profile.intro() ? Math.min(3, introClock + dt) : 3;
        reveal = Math.min(1, introClock / .6f);
        boolean idle = !interactive || !pointer.inside() || pointer.idleMillis() > 2000;
        if (profile.autoAnimate() && idle) {
            targetX = width * .5f + (float) Math.sin(time * .5f) * width * .25f;
            targetY = height * .5f + (float) Math.cos(time) * height * .25f;
        } else if (interactive && pointer.inside()) {
            targetX = pointer.x();
            targetY = pointer.y();
        }
        cursorVx += ((targetX - cursorX) * 49 - cursorVx * 14) * dt;
        cursorVy += ((targetY - cursorY) * 49 - cursorVy * 14) * dt;
        cursorX += cursorVx * dt;
        cursorY += cursorVy * dt;
        simulate(profile, dt, false);
    }

    private void simulate(AntigravityProfile profile, float dt, boolean settle) {
        float unit = height / WORLD_HEIGHT;
        float ring = profile.ringRadius() * unit * (1 - .38f * charge);
        float magnet = Math.max(profile.magnetRadius() * unit, 1);
        float spread = 5 / (profile.fieldStrength() + .1f) * unit;
        float wave = profile.waveAmplitude() * unit * .5f;
        float omegaBase = 2.2f + profile.lerpSpeed() * 80;
        float spin = profile.rotationSpeed() + charge * 3;
        float edge = magnet * .18f;
        float centerX = width * .5f, centerY = height * .5f;
        for (int i = 0; i < x.length; i++) {
            float parallax = 1 - depth[i] * .12f * profile.depthFactor();
            float cx = centerX + (cursorX - centerX) * parallax;
            float cy = centerY + (cursorY - centerY) * parallax;
            float hx = homeX[i];
            float hy = homeY[i] + (float) Math.sin(time * .7f + phase[i]) * unit * .12f * profile.waveAmplitude();
            float dx = hx - cx, dy = hy - cy;
            float distance = (float) Math.hypot(dx, dy);
            float capture = 1 - Math.clamp((distance - (magnet - edge)) / (edge * 2), 0, 1);
            float ease = capture * capture * (3 - 2 * capture);
            float turn = orbit[i];
            if (!settle) {
                turn += spin * ease * dt;
                turn -= (float) Math.atan2(Math.sin(turn), Math.cos(turn)) * (1 - ease) * Math.min(1, dt * 2.5f);
                orbit[i] = turn % TURN;
            }
            float tx = hx, ty = hy;
            if (ease > 0 || Math.abs(turn) > 1e-4f) {
                float angle = (float) Math.atan2(dy, dx) + turn;
                float ripple = wave * (.6f * (float) Math.sin(angle * profile.waveCount() - time * profile.waveSpeed() * 2)
                        + .4f * (float) Math.sin(phase[i] + time * profile.waveSpeed() * 1.3f));
                float radius = distance + (ring + ripple + band[i] * spread - distance) * ease;
                tx = cx + (float) Math.cos(angle) * radius;
                ty = cy + (float) Math.sin(angle) * radius;
            }
            if (settle) {
                x[i] = tx; y[i] = ty; vx[i] = vy[i] = 0;
                continue;
            }
            float omega = introClock < delay[i] * .9f ? 0 : omegaBase * agility[i];
            float damping = 1.64f * omega, stiffness = omega * omega;
            int steps = Math.clamp((int) Math.ceil(omega * dt * 2), 1, 4);
            float h = dt / steps;
            for (int step = 0; step < steps; step++) {
                vx[i] += ((tx - x[i]) * stiffness - vx[i] * damping) * h;
                vy[i] += ((ty - y[i]) * stiffness - vy[i] * damping) * h;
                x[i] += vx[i] * h;
                y[i] += vy[i] * h;
            }
        }
    }

    /** 主动爆散只改变速度；宿主负责过滤减少动效和非交互输入。 */
    void burst(AntigravityProfile profile, float strength) {
        if (!Float.isFinite(strength)) return;
        strength = Math.clamp(strength, 0, 2);
        float unit = height / WORLD_HEIGHT;
        float reach = Math.max(1, Math.max(profile.magnetRadius(), profile.ringRadius()) * unit * 1.8f);
        for (int i = 0; i < x.length; i++) {
            float dx = x[i] - cursorX, dy = y[i] - cursorY;
            float distance = (float) Math.hypot(dx, dy) + .001f;
            float ratio = distance / reach;
            float kick = unit * 110 * strength * (float) Math.exp(-ratio * ratio) * agility[i];
            vx[i] += dx / distance * kick;
            vy[i] += dy / distance * kick;
        }
    }

    /** 清空状态，让下一次 resize 重新播放入场。 */
    void restart() {
        x = y = vx = vy = depth = phase = colorSeed = sizeSeed = new float[0];
        homeX = homeY = band = agility = delay = orbit = null;
        width = height = 0;
        cursorX = cursorY = cursorVx = cursorVy = targetX = targetY = 0;
        time = hue = charge = reveal = introClock = 0;
    }

    /** 与附件的 32 位随机数相同，固定种子使相同配置可复现。 */
    private static final class SeededRandom {
        private int seed = 1337;
        float next() {
            seed += 0x6d2b79f5;
            int t = (seed ^ seed >>> 15) * (1 | seed);
            t = (t + (t ^ t >>> 7) * (61 | t)) ^ t;
            return Math.min(Math.nextDown(1f), (float) (Integer.toUnsignedLong(t ^ t >>> 14) / 4294967296.0));
        }
    }
}
