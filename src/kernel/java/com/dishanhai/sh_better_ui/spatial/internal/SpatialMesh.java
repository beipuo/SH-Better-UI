// Author: dishanhai
package com.dishanhai.sh_better_ui.spatial.internal;

import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Matrix;
import com.dishanhai.sh_better_ui.spatial.SpatialGeometry.Shape;

import java.util.ArrayList;
import java.util.List;

/** Clips convex meshes in scene coordinates while preserving local coordinates for SDF evaluation. */
public final class SpatialMesh {
    private SpatialMesh() {}

    public record Vertex(double x, double y, double u, double v) {
        Vertex towards(Vertex other, double t) {
            return new Vertex(x + (other.x - x) * t, y + (other.y - y) * t,
                    u + (other.u - u) * t, v + (other.v - v) * t);
        }
    }

    record Clip(Matrix inverse, double width, double height) {
        boolean contains(double x, double y) {
            var p = inverse.point(x, y);
            return p.x() >= 0 && p.y() >= 0 && p.x() <= width && p.y() <= height;
        }

        double distance(Vertex v, int edge) {
            return switch (edge) {
                case 0 -> inverse.a() * v.x + inverse.c() * v.y + inverse.x();
                case 1 -> inverse.b() * v.x + inverse.d() * v.y + inverse.y();
                case 2 -> width - (inverse.a() * v.x + inverse.c() * v.y + inverse.x());
                default -> height - (inverse.b() * v.x + inverse.d() * v.y + inverse.y());
            };
        }
    }

    static Vertex vertex(Matrix matrix, double x, double y) {
        var point = matrix.point(x, y);
        return new Vertex(point.x(), point.y(), x, y);
    }

    static List<Vertex> quad(Matrix matrix, double x, double y, double right, double bottom) {
        return List.of(vertex(matrix, x, y), vertex(matrix, right, y),
                vertex(matrix, right, bottom), vertex(matrix, x, bottom));
    }

    static List<Vertex> clip(List<Vertex> polygon, Clip clip) {
        for (int edge = 0; edge < 4 && !polygon.isEmpty(); edge++) {
            boolean allInside = true;
            for (var vertex : polygon) {
                if (clip.distance(vertex, edge) < 0) {
                    allInside = false;
                    break;
                }
            }
            if (allInside) continue;
            var output = new ArrayList<Vertex>(polygon.size() + 1);
            var previous = polygon.get(polygon.size() - 1);
            double last = clip.distance(previous, edge);
            for (var current : polygon) {
                double next = clip.distance(current, edge);
                if ((last >= 0) != (next >= 0)) output.add(previous.towards(current, last / (last - next)));
                if (next >= 0) output.add(current);
                previous = current;
                last = next;
            }
            polygon = output;
        }
        return polygon;
    }

    static List<Vertex> outline(Matrix matrix, Shape shape) {
        var vertices = new ArrayList<Vertex>(36);
        switch (shape.kind()) {
            case ELLIPSE -> arc(vertices, matrix, shape.width() / 2, shape.height() / 2,
                    shape.width() / 2, shape.height() / 2, 0, Math.PI * 2, 32);
            case BOX -> {
                double tl = shape.topLeft(), tr = shape.topRight(), br = shape.bottomRight(), bl = shape.bottomLeft();
                double width = shape.width(), height = shape.height();
                arc(vertices, matrix, tl, tl, tl, tl, Math.PI, Math.PI / 2, 8);
                arc(vertices, matrix, width - tr, tr, tr, tr, Math.PI * 1.5, Math.PI / 2, 8);
                arc(vertices, matrix, width - br, height - br, br, br, 0, Math.PI / 2, 8);
                arc(vertices, matrix, bl, height - bl, bl, bl, Math.PI / 2, Math.PI / 2, 8);
            }
        }
        return vertices;
    }

    private static void arc(List<Vertex> vertices, Matrix matrix, double x, double y, double rx, double ry,
                            double start, double span, int steps) {
        int count = rx == 0 ? 0 : steps;
        if (span == Math.PI * 2) count--;
        for (int i = 0; i <= count; i++) {
            double angle = start + span * i / steps;
            vertices.add(vertex(matrix, x + rx * Math.cos(angle), y + ry * Math.sin(angle)));
        }
    }
}
