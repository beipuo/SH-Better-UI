// Author: dishanhai
package com.dishanhai.sh_better_ui.spatial;

import java.util.Objects;

/** Geometry in local GUI units. Transform matrices use column vectors. */
public final class SpatialGeometry {
    private SpatialGeometry() {}

    static double range(double value, double min, double max, String name) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("Invalid spatial " + name + ": " + value);
        }
        return value;
    }

    public record Point(double x, double y) {}

    public record Matrix(double a, double b, double c, double d, double x, double y) {
        public static final Matrix IDENTITY = new Matrix(1, 0, 0, 1, 0, 0);

        public Point point(double u, double v) {
            return new Point(a * u + c * v + x, b * u + d * v + y);
        }

        public Matrix compose(Matrix local) {
            var origin = point(local.x, local.y);
            return new Matrix(a * local.a + c * local.b, b * local.a + d * local.b,
                    a * local.c + c * local.d, b * local.c + d * local.d, origin.x, origin.y);
        }

        public Matrix inverse() {
            double det = a * d - b * c;
            if (det == 0 || !Double.isFinite(det)) throw new IllegalStateException("Singular spatial transform");
            return new Matrix(d / det, -b / det, -c / det, a / det,
                    (c * y - d * x) / det, (b * x - a * y) / det);
        }
    }

    public record Transform(double x, double y, double scaleX, double scaleY,
                            double radians, double originX, double originY) {
        public static final Transform IDENTITY = new Transform(0, 0, 1, 1, 0, 0, 0);

        public Transform {
            range(x, -32768, 32768, "x");
            range(y, -32768, 32768, "y");
            range(Math.abs(scaleX), 0.01, 16, "scaleX");
            range(Math.abs(scaleY), 0.01, 16, "scaleY");
            range(radians, -1e6, 1e6, "rotation");
            range(originX, -32768, 32768, "originX");
            range(originY, -32768, 32768, "originY");
        }

        public Matrix matrix() {
            double cos = Math.cos(radians), sin = Math.sin(radians);
            double a = cos * scaleX, b = sin * scaleX, c = -sin * scaleY, d = cos * scaleY;
            return new Matrix(a, b, c, d, x + originX - a * originX - c * originY,
                    y + originY - b * originX - d * originY);
        }
    }

    public enum Kind { BOX, ELLIPSE }

    public record Shape(Kind kind, double width, double height, double topLeft,
                        double topRight, double bottomRight, double bottomLeft) {
        public Shape {
            Objects.requireNonNull(kind);
            range(width, .001, 32768, "width");
            range(height, .001, 32768, "height");
            double limit = Math.min(width, height) / 2;
            topLeft = Math.min(limit, range(topLeft, 0, 32768, "topLeft"));
            topRight = Math.min(limit, range(topRight, 0, 32768, "topRight"));
            bottomRight = Math.min(limit, range(bottomRight, 0, 32768, "bottomRight"));
            bottomLeft = Math.min(limit, range(bottomLeft, 0, 32768, "bottomLeft"));
        }

        public static Shape rectangle(double width, double height) { return rounded(width, height, 0); }

        public static Shape rounded(double width, double height, double radius) {
            return new Shape(Kind.BOX, width, height, radius, radius, radius, radius);
        }

        public static Shape capsule(double width, double height) {
            return rounded(width, height, Math.min(width, height) / 2);
        }

        public static Shape ellipse(double width, double height) {
            return new Shape(Kind.ELLIPSE, width, height, 0, 0, 0, 0);
        }

        public static Shape circle(double diameter) { return ellipse(diameter, diameter); }

        /** Exact box/circle distance. For non-circular ellipses this estimates distance with the correct sign. */
        public double distance(double x, double y) {
            double px = x - width / 2, py = y - height / 2;
            if (kind == Kind.ELLIPSE) {
                return (Math.hypot(px / (width / 2), py / (height / 2)) - 1) * Math.min(width, height) / 2;
            }
            double radius = py < 0 ? (px < 0 ? topLeft : topRight) : (px < 0 ? bottomLeft : bottomRight);
            double qx = Math.abs(px) - width / 2 + radius, qy = Math.abs(py) - height / 2 + radius;
            return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - radius;
        }
    }
}
