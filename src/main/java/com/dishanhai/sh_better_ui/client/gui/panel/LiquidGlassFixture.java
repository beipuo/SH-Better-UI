// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import com.google.gson.Gson;

import java.io.Reader;
import java.util.List;

/** Samples the shared native and WebGL Liquid Glass scene at an explicit simulation time. */
record LiquidGlassFixture(String texture, int width, int height, int durationMs, int fps,
                          GlassRegion surface, List<Point> pointer, List<Input> inputEvents, MovingObject movingObject) {
    LiquidGlassFixture {
        if (!"liquid-glass.png".equals(texture) || width <= 0 || height <= 0 || durationMs <= 0 || fps <= 0
                || (long) durationMs * fps % 1000 != 0 || (long) durationMs * fps / 1000 >= Integer.MAX_VALUE
                || surface.x() < 0 || surface.y() < 0 || surface.x() + surface.width() > width
                || surface.y() + surface.height() > height) {
            throw new IllegalArgumentException("Invalid Liquid Glass fixture");
        }
        pointer = List.copyOf(pointer);
        inputEvents = List.copyOf(inputEvents);
        validatePath(pointer, durationMs);
        validatePath(movingObject.points(), durationMs);
        for (var point : pointer) {
            if (point.x() < 0 || point.x() > 1 || point.y() < 0 || point.y() > 1) {
                throw new IllegalArgumentException("Fixture pointer is outside the scene");
            }
        }
    }

    static LiquidGlassFixture read(Reader reader) {
        return new Gson().fromJson(reader, LiquidGlassFixture.class);
    }

    int frameCount() { return (int) ((long) durationMs * fps / 1000) + 1; }

    Sample sample(double timeMs) {
        var object = samplePath(movingObject.points(), timeMs);
        var light = samplePath(pointer, timeMs);
        boolean inside = false;
        for (var event : inputEvents) {
            if (event.timeMs() > timeMs) break;
            if (event.type().equals("pointerenter")) inside = true;
            if (event.type().equals("pointerleave")) inside = false;
        }
        return new Sample((int) Math.round(object.x()), (int) Math.round(object.y()),
                lightCoordinate(light.x() * width, surface.x(), surface.width()),
                lightCoordinate(light.y() * height, surface.y(), surface.height()), inside ? .75f : 0f);
    }

    private static float lightCoordinate(double coordinate, float origin, float size) {
        return (float) Math.max(-1, Math.min(1, (coordinate - origin) * 2 / size - 1));
    }

    private static void validatePath(List<Point> path, int durationMs) {
        if (path.isEmpty() || path.get(0).timeMs() != 0 || path.get(path.size() - 1).timeMs() != durationMs) {
            throw new IllegalArgumentException("Fixture path must span the timeline");
        }
        double previous = -1;
        for (var point : path) {
            if (point.timeMs() <= previous || !Double.isFinite(point.x()) || !Double.isFinite(point.y())) {
                throw new IllegalArgumentException("Invalid fixture path point");
            }
            previous = point.timeMs();
        }
    }

    private static Point samplePath(List<Point> path, double timeMs) {
        var from = path.get(0);
        if (timeMs <= from.timeMs()) return from;
        for (int i = 1; i < path.size(); i++) {
            var to = path.get(i);
            if (timeMs <= to.timeMs()) {
                double t = (timeMs - from.timeMs()) / (to.timeMs() - from.timeMs());
                return new Point(timeMs, from.x() + (to.x() - from.x()) * t, from.y() + (to.y() - from.y()) * t);
            }
            from = to;
        }
        return from;
    }

    record Point(double timeMs, double x, double y) {}

    record Input(int timeMs, String type) {}

    record MovingObject(int width, int height, String color, List<Point> points) {
        MovingObject {
            if (width <= 0 || height <= 0 || !color.matches("#[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("Invalid fixture moving object");
            }
            points = List.copyOf(points);
        }

        int argb() { return 0xff000000 | Integer.parseInt(color.substring(1), 16); }
    }

    record Sample(int objectX, int objectY, float lightX, float lightY, float lightStrength) {}
}
