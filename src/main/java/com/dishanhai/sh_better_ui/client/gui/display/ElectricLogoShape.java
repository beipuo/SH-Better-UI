package com.dishanhai.sh_better_ui.client.gui.display;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Arrays;

/** 山海：提取贴图轮廓，生成供电光 shader 采样的有符号距离场。 */
record ElectricLogoShape(int width, int height, int logoWidth, int logoHeight, int[] argb, float[] edges) {
    static final int DISTANCE_LIMIT = 512;
    private static final int MAX_RASTER_SIZE = 256;
    private static final int MIN_PADDING = 32;
    private static final float DIAGONAL = (float) Math.sqrt(2);

    static ElectricLogoShape bolt() {
        return DefaultBolt.SHAPE;
    }

    private static ElectricLogoShape drawBolt() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = image.createGraphics();
        try {
            canvas.setColor(Color.WHITE);
            canvas.fillPolygon(new int[]{30, 46, 36, 55, 19, 27, 11},
                    new int[]{3, 3, 23, 23, 61, 37, 37}, 7);
        } finally {
            canvas.dispose();
        }
        return trace(image);
    }

    static ElectricLogoShape trace(BufferedImage image) {
        double fit = (double) MAX_RASTER_SIZE / Math.max(image.getWidth(), image.getHeight());
        int sourceWidth = Math.max(1, (int) Math.round(image.getWidth() * fit));
        int sourceHeight = Math.max(1, (int) Math.round(image.getHeight() * fit));
        BufferedImage raster = new BufferedImage(sourceWidth, sourceHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D canvas = raster.createGraphics();
        try {
            canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            canvas.drawImage(image, 0, 0, sourceWidth, sourceHeight, null);
        } finally {
            canvas.dispose();
        }
        int[] pixels = raster.getRGB(0, 0, sourceWidth, sourceHeight, null, 0, sourceWidth);
        boolean[] coverage = mask(pixels, sourceWidth, sourceHeight);

        int left = sourceWidth;
        int top = sourceHeight;
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < sourceHeight; y++) {
            for (int x = 0; x < sourceWidth; x++) {
                if (coverage[y * sourceWidth + x]) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (right < left) return null;

        int logoWidth = right - left + 1;
        int logoHeight = bottom - top + 1;
        int pad = Math.max(MIN_PADDING, (Math.max(logoWidth, logoHeight) + 4) / 5);
        int width = logoWidth + pad * 2;
        int height = logoHeight + pad * 2;
        // 多留一圈守卫像素，让两次距离扫描覆盖输出栅格的四条边。
        int stride = width + 2;
        boolean[] inside = new boolean[stride * (height + 2)];
        for (int y = 0; y < logoHeight; y++) {
            System.arraycopy(coverage, (y + top) * sourceWidth + left,
                    inside, (y + pad + 1) * stride + pad + 1, logoWidth);
        }
        return new ElectricLogoShape(width, height, logoWidth, logoHeight, distanceField(inside, width, height),
                edgePoints(inside, stride, logoWidth, logoHeight, pad));
    }

    private static boolean[] mask(int[] pixels, int width, int height) {
        int transparent = 0;
        int minAlpha = 255;
        int maxAlpha = 0;
        for (int pixel : pixels) {
            int alpha = pixel >>> 24;
            if (alpha < 250) transparent++;
            minAlpha = Math.min(minAlpha, alpha);
            maxAlpha = Math.max(maxAlpha, alpha);
        }
        boolean[] mask = new boolean[pixels.length];
        // 少量全透明像素也可能是轮廓孔洞，避免把它们当作色键前景。
        if (minAlpha < 128 || transparent * 100 > pixels.length) {
            if (maxAlpha == 0) return mask;
            int threshold = (maxAlpha + 1) / 2;
            for (int i = 0; i < pixels.length; i++) mask[i] = pixels[i] >>> 24 >= threshold;
            return mask;
        }

        int red = 0;
        int green = 0;
        int blue = 0;
        int count = 0;
        for (int y = 0; y < height; y++) {
            int step = y == 0 || y == height - 1 ? 1 : Math.max(1, width - 1);
            for (int x = 0; x < width; x += step) {
                int pixel = pixels[y * width + x];
                red += pixel >> 16 & 255;
                green += pixel >> 8 & 255;
                blue += pixel & 255;
                count++;
            }
        }
        red /= count;
        green /= count;
        blue /= count;
        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            mask[i] = Math.abs((pixel >> 16 & 255) - red) >= 40
                    || Math.abs((pixel >> 8 & 255) - green) >= 40
                    || Math.abs((pixel & 255) - blue) >= 40;
        }
        return mask;
    }

    private static int[] distanceField(boolean[] inside, int width, int height) {
        int stride = width + 2;
        float[] distances = new float[inside.length];
        Arrays.fill(distances, DISTANCE_LIMIT);
        for (int y = 1; y <= height; y++) {
            for (int x = 1; x <= width; x++) relax(inside, distances, y * stride + x, -1, -stride);
        }
        for (int y = height; y >= 1; y--) {
            for (int x = width; x >= 1; x--) relax(inside, distances, y * stride + x, 1, stride);
        }

        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = (y + 1) * stride + x + 1;
                float signed = inside[index] ? -distances[index] : distances[index];
                int packed = Math.round((signed / DISTANCE_LIMIT + 1f) * 32767.5f);
                pixels[y * width + x] = 0xFF000000 | packed << 8;
            }
        }
        return pixels;
    }

    private static void relax(boolean[] inside, float[] distances, int index, int horizontal, int vertical) {
        float best = distances[index];
        best = Math.min(best, step(inside, distances, index, horizontal, 1f));
        best = Math.min(best, step(inside, distances, index, vertical, 1f));
        best = Math.min(best, step(inside, distances, index, vertical - horizontal, DIAGONAL));
        best = Math.min(best, step(inside, distances, index, vertical + horizontal, DIAGONAL));
        distances[index] = best;
    }

    private static float step(boolean[] inside, float[] distances, int index, int offset, float cost) {
        int neighbor = index + offset;
        return cost + (inside[index] == inside[neighbor] ? distances[neighbor] : -0.5f);
    }

    private static float[] edgePoints(boolean[] inside, int stride, int logoWidth, int logoHeight, int pad) {
        float[] points = new float[2 * (2 * logoWidth * logoHeight + logoWidth + logoHeight)];
        int count = 0;
        for (int y = pad - 1; y < pad + logoHeight; y++) {
            for (int x = pad - 1; x < pad + logoWidth; x++) {
                int index = (y + 1) * stride + x + 1;
                if (inside[index] != inside[index + 1]) {
                    points[count++] = x + 1f;
                    points[count++] = y + 0.5f;
                }
                if (inside[index] != inside[index + stride]) {
                    points[count++] = x + 0.5f;
                    points[count++] = y + 1f;
                }
            }
        }
        return Arrays.copyOf(points, count);
    }

    private static final class DefaultBolt {
        private static final ElectricLogoShape SHAPE = drawBolt();
    }
}
