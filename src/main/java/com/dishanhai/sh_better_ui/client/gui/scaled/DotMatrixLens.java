package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile.PointerLens;

/** 山海：DotField 风格的径向变形。指针采集由宿主负责。 */
final class DotMatrixLens {
    private static final Spring.Preset FOLLOW = new Spring.Preset(2d * Math.PI / 18d, 1d, 0.05f, 0.5f);
    private final Spring x = new Spring(FOLLOW);
    private final Spring y = new Spring(FOLLOW);
    private long lastMs;
    private boolean ready;
    private float presence;
    private float zoom;

    void reset() {
        ready = false;
        presence = zoom = 0f;
    }

    Frame advance(PointerLens profile, UiPointerTracker.Frame pointer, long now, boolean reducedMotion) {
        if (profile.radius() == 0 || pointer == null) {
            reset();
            return Frame.NONE;
        }
        boolean inside = pointer.inside();
        if (reducedMotion) {
            reset();
            return inside ? frame(profile, pointer.x(), pointer.y(), 1f, 0f, true) : Frame.NONE;
        }
        if (!ready && !inside) return Frame.NONE;

        float dt = ready ? (float) UiMath.clamp((now - (double) lastMs) / 1000d, 0d, 0.05d) : 0f;
        lastMs = now;
        if (inside) {
            if (presence < 0.01f) {
                x.snap(pointer.x());
                y.snap(pointer.y());
            }
            x.setTarget(pointer.x(), now);
            y.setTarget(pointer.y(), now);
        } else {
            x.update(now);
            y.update(now);
        }
        ready = true;
        presence = approach(presence, inside ? 1f : 0f, dt, inside ? 0.18f : 0.4f);
        zoom = approach(zoom, inside && pointer.down() && profile.holdToZoom() ? 1f : 0f, dt, 0.14f);
        if (!inside && presence < 0.01f) {
            reset();
            return Frame.NONE;
        }
        float eased = presence * presence * (3f - 2f * presence);
        return frame(profile, x.value(), y.value(), eased, zoom, false);
    }

    private static float approach(float value, float target, float dt, float duration) {
        return GuiMotion.lerp(value, target, (float) -Math.expm1(-dt / duration));
    }

    private static Frame frame(PointerLens profile, float x, float y, float presence, float zoom, boolean reduced) {
        float bulge = profile.bulgeStrength();
        float strength = UiMath.clamp(bulge * (bulge >= 0f ? 1.2f : 0.7f) * (1f + zoom * 0.6f), -0.85f, 1.45f);
        return new Frame(x, y, profile.radius() * (1d + zoom * 0.12d), reduced ? 0f : strength * presence,
                reduced ? 0d : Math.toRadians(profile.twistDegrees()) * (1d + zoom * 0.6d) * presence,
                profile.glowRadius() * (1d + zoom * 0.1d),
                profile.glowIntensity() * (1f + zoom * 0.25f) * presence * (profile.glowArgb() >>> 24) / 255f);
    }

    record Frame(float x, float y, double radius, float strength, double twist, double glowRadius, float glow) {
        private static final Frame NONE = new Frame(0f, 0f, 0d, 0f, 0d, 0d, 0f);

        /** 返回空值表示该点可沿用原网格坐标和大小。 */
        Sample sample(float sourceX, float sourceY) {
            if (strength == 0f && twist == 0d && glow == 0f) return null;
            double dx = sourceX - x, dy = sourceY - y;
            double distanceSquared = dx * dx + dy * dy;
            double normalized = distanceSquared / (radius * radius);
            double glowSquared = glowRadius * glowRadius;
            if (normalized >= 1d && distanceSquared >= glowSquared) return null;
            double falloff = Math.max(0d, 1d - normalized);
            double weight = falloff * falloff * falloff;
            double grow = 1d + strength * weight;
            double spread = 1d + strength * (weight - 6d * normalized * falloff * falloff);
            double angle = twist * weight;
            double displayX = dx, displayY = dy;
            if (angle != 0d) {
                double cos = Math.cos(angle), sin = Math.sin(angle);
                displayX = cos * dx - sin * dy;
                displayY = sin * dx + cos * dy;
            }
            float scale = (float) Math.sqrt(Math.max(grow * spread, 0.02d));
            // 旋转保持到中心的距离，径向缩放后的距离决定光池。
            double pool = glow > 0f && glowSquared > 0d
                    ? Math.max(0d, 1d - distanceSquared * grow * grow / glowSquared) : 0d;
            return new Sample((float) (x + displayX * grow), (float) (y + displayY * grow),
                    scale, (float) (pool * pool * glow));
        }
    }

    record Sample(float x, float y, float scale, float glow) {}
}
