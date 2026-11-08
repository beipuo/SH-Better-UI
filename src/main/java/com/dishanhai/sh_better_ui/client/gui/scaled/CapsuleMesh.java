package com.dishanhai.sh_better_ui.client.gui.scaled;

/** 山海：以连续坐标生成圆角轮廓，内外过渡带通过顶点颜色抗锯齿。 */
final class CapsuleMesh {
    private CapsuleMesh() {}

    @FunctionalInterface
    interface VertexSink {
        void vertex(float x, float y, int color);
    }

    static void emit(float width, float height, float radius, float stroke, float pixelsPerUnit,
                     int edge, int fill, VertexSink sink) {
        float halfSide = Math.min(width, height) * 0.5f;
        if (halfSide <= 0f) return;
        float scale = Math.max(0.01f, pixelsPerUnit);
        float feather = Math.min(0.5f / scale, halfSide * 0.5f);
        boolean hasInterior = stroke > 0f && stroke < halfSide;
        if (stroke == 0f) edge = fill;
        if (hasInterior) feather = Math.min(feather, Math.min(stroke, halfSide - stroke) * 0.5f);
        // 依据屏幕半径增加弧段，让常用 GUI 比例的弦高误差小于一个物理像素。
        int arcSegments = Math.clamp((int) Math.ceil(Math.sqrt(radius * scale / 0.32)), 6, 48);
        Point start = new Point(width - radius, radius, 0f, -1f, 1, -1);
        Point previous = start;
        int clearEdge = edge & 0x00FFFFFF;
        int inner = (fill >>> 24) == 0 ? clearEdge : fill;
        for (int corner = 0; corner < 4; corner++) {
            int sideX = corner < 2 ? 1 : -1;
            int sideY = corner == 0 || corner == 3 ? -1 : 1;
            float cx = sideX > 0 ? width - radius : radius;
            float cy = sideY > 0 ? height - radius : radius;
            for (int step = corner == 0 ? 1 : 0; step <= arcSegments; step++) {
                double angle = (corner - 1 + step / (double) arcSegments) * (Math.PI / 2);
                Point next = new Point(cx, cy, (float) Math.cos(angle), (float) Math.sin(angle), sideX, sideY);
                slice(previous, next, radius, stroke, feather, width, height,
                        edge, clearEdge, inner, hasInterior, sink);
                previous = next;
            }
        }
        slice(previous, start, radius, stroke, feather, width, height,
                edge, clearEdge, inner, hasInterior, sink);
    }

    private static void slice(Point from, Point to, float radius, float stroke, float feather,
                              float width, float height, int edge, int clearEdge, int inner,
                              boolean hasInterior, VertexSink sink) {
        band(from, to, radius + feather, radius - feather, clearEdge, edge, sink);
        float coreRadius = radius - feather;
        if (hasInterior) {
            band(from, to, radius - feather, radius - stroke + feather, edge, edge, sink);
            coreRadius = radius - stroke - feather;
            band(from, to, radius - stroke + feather, coreRadius, edge, inner, sink);
        }
        int coreColor = hasInterior ? inner : edge;
        if ((coreColor >>> 24) != 0) {
            from.emit(coreRadius, coreColor, sink);
            sink.vertex(width * 0.5f, height * 0.5f, coreColor);
            to.emit(coreRadius, coreColor, sink);
            to.emit(coreRadius, coreColor, sink);
        }
    }

    private static void band(Point from, Point to, float outer, float inner,
                             int outerColor, int innerColor, VertexSink sink) {
        if (outer <= inner) return;
        from.emit(outer, outerColor, sink);
        from.emit(inner, innerColor, sink);
        to.emit(inner, innerColor, sink);
        to.emit(outer, outerColor, sink);
    }

    private record Point(float x, float y, float nx, float ny, int sideX, int sideY) {
        void emit(float distance, int color, VertexSink sink) {
            // 内缩越过弧心时，整段弧收成内矩形的一个直角。
            if (distance < 0f) sink.vertex(x + sideX * distance, y + sideY * distance, color);
            else sink.vertex(x + nx * distance, y + ny * distance, color);
        }
    }
}
