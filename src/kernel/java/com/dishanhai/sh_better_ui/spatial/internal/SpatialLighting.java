// Author: dishanhai
package com.dishanhai.sh_better_ui.spatial.internal;

import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Matrix;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Light;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.LightType;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Material;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

final class SpatialLighting {
    private SpatialLighting() {}

    record Sample(float red, float green, float blue, float directionX, float directionY, boolean directional) {
        static final Sample DEFAULT = new Sample(1, 1, 1, -.45f, -.89f, false);

        int tint(int color) {
            if (red == 1 && green == 1 && blue == 1) return color;
            return (color & 0xff000000) | channel(color >> 16 & 255, red) << 16
                    | channel(color >> 8 & 255, green) << 8 | channel(color & 255, blue);
        }

        private static int channel(int channel, float multiplier) {
            double value = channel / 255.0;
            double linear = value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
            linear = Math.min(1, linear * multiplier);
            double encoded = linear <= .0031308 ? linear * 12.92 : 1.055 * Math.pow(linear, 1 / 2.4) - .055;
            return (int) Math.round(encoded * 255);
        }
    }

    /** Broad phase in GUI coordinates. Bits preserve light registration order within each cell. */
    static final class Index {
        private static final int CELL_SIZE = 128;
        private static final double INDEXED_RADIUS = 192;
        private final Light[] lights;
        private final Map<Long, Long> cells = new HashMap<>();
        private long wide;

        Index(Collection<Light> registered) {
            lights = registered.toArray(Light[]::new);
            for (int i = 0; i < lights.length; i++) {
                Light light = lights[i];
                if (light.intensity() == 0 || light.color() >>> 24 == 0) continue;
                long bit = 1L << i;
                double radius = light.radius();
                if (global(light) || radius > INDEXED_RADIUS) {
                    wide |= bit;
                    continue;
                }
                int right = cell(light.x() + radius), bottom = cell(light.y() + radius);
                for (int y = cell(light.y() - radius); y <= bottom; y++) {
                    for (int x = cell(light.x() - radius); x <= right; x++) {
                        long key = key(x, y);
                        cells.put(key, cells.getOrDefault(key, 0L) | bit);
                    }
                }
            }
        }

        long candidates(double x, double y) { return wide | cells.getOrDefault(key(cell(x), cell(y)), 0L); }

        private static int cell(double value) { return (int) Math.floor(value / CELL_SIZE); }

        private static long key(int x, int y) { return (long) x << 32 | y & 0xffff_ffffL; }
    }

    static boolean global(Light light) {
        return light != null && (light.type() == LightType.AMBIENT || light.type() == LightType.DIRECTIONAL);
    }

    static boolean affects(Light light, double x, double y) {
        if (light == null || light.intensity() == 0 || light.color() >>> 24 == 0) return false;
        double dx = light.x() - x, dy = light.y() - y;
        return global(light) || dx * dx + dy * dy < light.radius() * light.radius();
    }

    static Sample sample(Index index, double x, double y, double elevation,
                         Matrix inverse, Material material, int limit) {
        if (limit == 0 || index == null) return Sample.DEFAULT;
        double r = .58, g = .58, b = .58, dx = 0, dy = 0;
        int count = 0;
        for (long candidates = index.candidates(x, y); candidates != 0; candidates &= candidates - 1) {
            var light = index.lights[Long.numberOfTrailingZeros(candidates)];
            double angleX = Math.cos(light.direction()), angleY = Math.sin(light.direction());
            double vx = light.x() - x, vy = light.y() - y;
            double distance = Math.hypot(vx, vy);
            double energy = light.intensity() * (light.color() >>> 24) / 255;
            switch (light.type()) {
                case AMBIENT -> { vx = 0; vy = 0; }
                case DIRECTIONAL -> { vx = -angleX; vy = -angleY; }
                case POINT, SPOT -> {
                    if (distance >= light.radius()) continue;
                    if (distance > 0) {
                        vx /= distance;
                        vy /= distance;
                        if (light.type() == LightType.SPOT
                                && -vx * angleX - vy * angleY < Math.cos(light.cone())) continue;
                    }
                    double falloff = 1 - distance / light.radius();
                    double lift = Math.abs(light.height() - elevation);
                    energy *= falloff * falloff * (1 / (1 + lift / light.radius()));
                }
            }
            if (energy <= 0) continue;
            r += energy * (light.color() >> 16 & 255) / 255;
            g += energy * (light.color() >> 8 & 255) / 255;
            b += energy * (light.color() & 255) / 255;
            dx += vx * energy;
            dy += vy * energy;
            if (++count == limit) break;
        }
        if (count == 0) return Sample.DEFAULT;
        double lx = inverse.a() * dx + inverse.c() * dy;
        double ly = inverse.b() * dx + inverse.d() * dy;
        double length = Math.hypot(lx, ly);
        double influence = .45 + .55 * Math.abs(material.depth());
        return new Sample((float) (1 + (Math.min(r, 2) - 1) * influence),
                (float) (1 + (Math.min(g, 2) - 1) * influence),
                (float) (1 + (Math.min(b, 2) - 1) * influence),
                length > 0 ? (float) (lx / length) : Sample.DEFAULT.directionX,
                length > 0 ? (float) (ly / length) : Sample.DEFAULT.directionY, length > 0);
    }
}
