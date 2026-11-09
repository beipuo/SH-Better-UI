package com.dishanhai.sh_better_ui.client.gui.layout;

import com.dishanhai.sh_better_ui.client.gui.layout.AlignmentFrame.VisualBounds;

import java.util.function.IntBinaryOperator;

import static com.dishanhai.sh_better_ui.client.gui.layout.AlignmentGeometry.*;

/** 山海：一次扫描得到的归一化可见边界和 alpha 加权中心。 */
public record TextureMetrics(Rect visible, Anchor centroid) {
    /**
     * 每个像素读取一次。alpha 取 0..255，阈值为 1..255，完全透明时保留空边界和纹理中心。
     * 输出以整张图的宽高归一化，像素面积使用其外边缘，重心使用像素中心。
     */
    public static TextureMetrics scan(int width, int height, int threshold, IntBinaryOperator alpha) {
        if (width <= 0 || height <= 0 || threshold < 1 || threshold > 255) {
            throw new IllegalArgumentException("Invalid texture metrics dimensions or alpha threshold");
        }
        int left = width, top = height, right = 0, bottom = 0;
        double sum = 0, xWeight = 0, yWeight = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int weight = alpha.applyAsInt(x, y);
                if (weight < threshold) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x + 1);
                bottom = Math.max(bottom, y + 1);
                sum += weight;
                xWeight += (x + 0.5) * weight;
                yWeight += (y + 0.5) * weight;
            }
        }
        if (sum == 0) return new TextureMetrics(new Rect(0.5, 0.5, 0, 0), new Anchor(0.5, 0.5));
        return new TextureMetrics(new Rect(left / (double) width, top / (double) height,
                (right - left) / (double) width, (bottom - top) / (double) height),
                new Anchor(xWeight / sum / width, yWeight / sum / height));
    }

    /** 把缓存度量投影到实际绘制区域，可用于不同尺寸及动画帧。 */
    public VisualBounds in(Rect region) {
        var scale = Transform.translation(region.x(), region.y()).compose(Transform.scale(region.width(), region.height()));
        return new VisualBounds(scale.map(visible), scale.map(centroid), new Anchor(0, 0), Insets.NONE, null);
    }
}
