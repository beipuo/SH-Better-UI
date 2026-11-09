package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.audio.MusicRhythm.Frame;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphics;

/** 山海：可组合的像素霓虹底板与律动画布。先绘制它们，再绘制宿主的文字、控件和焦点。 */
public final class MusicHud {
    private static final int CYAN = 0x68F8E4;
    private static final int VIOLET = 0xB591FF;

    private MusicHud() {}

    public static void drawPanel(GuiGraphics g, Bounds area, Frame frame, UiPreferences preferences) {
        if (area.width() < 8 || area.height() < 8) return;
        int x = area.x(), y = area.y(), r = area.right(), b = area.bottom();
        if (preferences.highContrast()) {
            g.fill(x, y, r, b, 0xFF050608);
            g.renderOutline(x, y, area.width(), area.height(), 0xFFFFFFFF);
            return;
        }

        g.fillGradient(x, y + 2, r, b - 2, 0xF518233B, 0xEC101024);
        g.fill(x + 2, y, r - 2, y + 2, 0xF518233B);
        g.fill(x + 2, b - 2, r - 2, b, 0xEC101024);
        boolean animated = !preferences.reducedMotion();
        float glow = animated ? .10f * frame.bass() + .12f * frame.energy() : 0;
        int depth = Math.max(2, area.height() / 4);
        g.fillGradient(x + 1, y + 2, r - 1, y + depth, ink(CYAN, .035f + glow * .32f), ink(CYAN, 0));
        g.fillGradient(x + 1, b - depth, r - 1, b - 1, ink(VIOLET, 0), ink(VIOLET, .06f + glow));

        g.fill(x + 2, y, r - 2, y + 1, 0xFF39506B);
        g.fill(x + 2, b - 1, r - 2, b, 0xFF383550);
        g.fill(x, y + 2, x + 1, b - 2, 0xFF39445F);
        g.fill(r - 1, y + 2, r, b - 2, 0xFF39445F);
        int edge = Math.min(18, area.width() / 4);
        g.fill(x + 2, y, x + 2 + edge, y + 1, ink(CYAN, .63f + glow));
        g.fill(x + 1, y + 1, x + 3, y + 3, ink(CYAN, .68f));
        g.fill(r - 2 - edge, b - 1, r - 2, b, ink(VIOLET, .60f + glow));
        g.fill(r - 3, b - 3, r - 1, b - 1, ink(VIOLET, .64f));
    }

    /** 绘制独立的有界波形、粒子和拍点。此区域只承载装饰，不负责点击或焦点。 */
    public static void drawScope(GuiGraphics g, Bounds area, Frame frame, UiPreferences preferences) {
        if (area.width() < 12 || area.height() < 7) return;
        int x = area.x(), y = area.y(), r = area.right(), b = area.bottom();
        int cy = y + area.height() / 2;
        if (preferences.highContrast()) {
            g.renderOutline(x, y, area.width(), area.height(), 0xFFB4BECE);
            g.fill(x + 2, cy, r - 2, cy + 1, 0xFFE5E9F0);
            return;
        }
        if (preferences.reducedMotion()) frame = Frame.STILL;

        g.enableScissor(x, y, r, b);
        try {
            g.fillGradient(x, y, r, b, 0xFC0D1728, 0xEE18132D);
            g.renderOutline(x, y, area.width(), area.height(), 0xFF334159);
            g.fillGradient(x + 1, cy, r - 1, b - 1, ink(VIOLET, .12f * frame.bass()), ink(VIOLET, 0));
            var pixels = CapsuleRenderer.colorVertices(g, 0, 0);
            for (int i = 1; i < 16; i++) {
                int gx = x + i * area.width() / 16;
                fill(pixels, gx, y + 2, gx + 1, b - 2, 0x164E7194);
            }
            fill(pixels, x + 1, cy, r - 1, cy + 1, 0x55445D7D);

            int cx = x + area.width() / 2;
            for (var pulse : frame.pulses()) {
                float t = pulse.progress();
                int radius = 4 + Math.round((1 - (1 - t) * (1 - t)) * Math.min(48, area.width() / 3));
                pixelRing(pixels, cx, cy, radius, ink(CYAN, pulse.opacity() * .56f));
                if (radius > 8) pixelRing(pixels, cx, cy, radius - 6, ink(VIOLET, pulse.opacity() * .30f));
            }

            int amplitude = Math.min(23, Math.max(1, (area.height() - 4) / 3));
            if (frame.bass() + frame.mid() > .002f) {
                wave(pixels, area, frame, cy, amplitude, 2.6f, VIOLET, frame.energy() * .30f + frame.bass() * .3f);
                wave(pixels, area, frame, cy, amplitude, 0, CYAN, frame.mid());
            } else {
                fill(pixels, x + 1, cy, r - 1, cy + 1, ink(CYAN, .30f));
            }
            sparkles(pixels, area, frame);
            g.flush();
        } finally {
            g.disableScissor();
        }
    }

    private static void wave(CapsuleMesh.VertexSink pixels, Bounds a, Frame frame, int cy, int amplitude,
                             float phase, int rgb, float weight) {
        int count = Math.min(64, (a.width() - 2) / 2);
        int previousY = cy;
        for (int i = 0; i < count; i++) {
            float t = (i + .5f) / count;
            float taper = (float) Math.sin(Math.PI * t);
            int y = cy + Math.round(taper * amplitude * (frame.wave(phase + 12 * t)
                    + .65f * frame.bass() * (float) Math.sin(8 * t - frame.flowPhase() + phase)));
            int x = a.x() + 1 + i * (a.width() - 2) / count;
            int right = a.x() + 1 + (i + 1) * (a.width() - 2) / count;
            if (weight > 0) fill(pixels, x, y - 1, right, y + 2, ink(rgb, .18f * weight));
            int color = ink(rgb, .30f + .62f * weight);
            fill(pixels, x, Math.min(previousY, y), x + 1, Math.max(previousY, y) + 1, color);
            fill(pixels, x + 1, y, right, y + 1, color);
            previousY = y;
        }
    }

    private static void pixelRing(CapsuleMesh.VertexSink pixels, int cx, int cy, int radius, int color) {
        if (color >>> 24 == 0) return;
        // Step a wide diamond in pixel units, leaving the waveform visible at the center.
        for (int i = 0; i <= radius; i += 2) {
            int dy = (radius - i + 1) / 2;
            int left = cx - i, right = cx + i;
            fill(pixels, left, cy - dy, left + 2, cy - dy + 1, color);
            if (i > 0) fill(pixels, right, cy - dy, right + 2, cy - dy + 1, color);
            if (dy > 0) {
                fill(pixels, left, cy + dy, left + 2, cy + dy + 1, color);
                if (i > 0) fill(pixels, right, cy + dy, right + 2, cy + dy + 1, color);
            }
        }
    }

    private static void sparkles(CapsuleMesh.VertexSink pixels, Bounds a, Frame frame) {
        if (frame.treble() < .015f) return;
        int count = Math.min(24, a.width() / 8);
        for (int i = 0; i < count; i++) {
            float amount = frame.sparkle(i * 2.39996f);
            if (amount < .035f) continue;
            int x = a.x() + 3 + i * (a.width() - 6) / count;
            float lift = frame.wave(i * 1.7f);
            int y = a.y() + 2 + (int) (((i * 37 % 97) / 96f) * (a.height() - 5)) + Math.round(lift * 2);
            int rgb = (i & 1) == 0 ? CYAN : VIOLET;
            fill(pixels, x - 1, y - 1, x + 2, y + 2, ink(rgb, amount * .15f));
            fill(pixels, x, y, x + 1, y + 1, ink(rgb, amount * .75f));
        }
    }

    private static void fill(CapsuleMesh.VertexSink pixels, int x, int y, int r, int b, int color) {
        pixels.vertex(x, y, color);
        pixels.vertex(x, b, color);
        pixels.vertex(r, b, color);
        pixels.vertex(r, y, color);
    }

    private static int ink(int rgb, float opacity) {
        return rgb | Math.round(GuiMotion.clamp01(opacity) * 255) << 24;
    }
}
