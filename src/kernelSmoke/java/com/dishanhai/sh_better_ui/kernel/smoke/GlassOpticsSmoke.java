// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassMaterial;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassOptics;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassQuality;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.util.Arrays;
import java.util.List;

final class GlassOpticsSmoke {
    static final GlassRegion DRAG_ZONE = GlassRegion.capsule(90, 30, 58, 24);
    private static final int STRIPE_A = 0xff214464;
    private static final int STRIPE_B = 0xff652b42;
    private static final int FIRST = 76;
    private static final int SECOND = 172;
    private static final int TOP = 76;
    private static final int WIDTH = 76;
    private static final int HEIGHT = 28;
    private static final int CENTER_Y = TOP + HEIGHT / 2;

    private GlassOpticsSmoke() {}

    static void drawReload(RenderService.Context context) {
        var material = UnifiedUI.get().glass().material(GlassVariant.REGULAR);
        context.glass(List.of(new GlassDraw(GlassRegion.capsule(76, 76, 76, 28), material)));
    }

    static void drawWorld(RenderService.Context context) {
        var material = UnifiedUI.get().glass().material(GlassVariant.CLEAR);
        int x = (context.frame().width() - 108) / 2;
        context.glass(List.of(new GlassDraw(GlassRegion.capsule(x, 30, 108, 30), material, -.7f, -.45f, .7f)));
        context.text(Component.translatable("menu.game"), x + 22, 41, material.foreground().argb());
    }

    static void draw(RenderService.Context context) {
        for (int x = FIRST - 8; x < SECOND + WIDTH + 8; x += 4) {
            context.fill(x, TOP - 8, x + 4, TOP + HEIGHT + 8, ((x - FIRST) / 4 & 1) == 0 ? STRIPE_A : STRIPE_B);
        }
        context.fill(FIRST - 8, 119, SECOND + WIDTH + 8, 164, 0xfff8f2de);
        var glass = UnifiedUI.get().glass();
        var regular = glass.material(GlassVariant.REGULAR);
        var clear = glass.material(GlassVariant.CLEAR);
        var neutral = new GlassMaterial(GlassVariant.CLEAR, new GlassOptics(1, 0, 0, 0, 0, 4, 0),
                GlassQuality.PERFORMANCE, GlassMaterial.Foreground.LIGHT);
        var lens = new GlassMaterial(GlassVariant.CLEAR, new GlassOptics(1, 8, 0, 0, 0, 4, 0),
                GlassQuality.PERFORMANCE, GlassMaterial.Foreground.LIGHT);
        var dark = new GlassMaterial(GlassVariant.CLEAR, clear.optics(), GlassQuality.ULTRA, GlassMaterial.Foreground.DARK);
        var fallback = new GlassMaterial(GlassVariant.REGULAR, regular.optics(), GlassQuality.FALLBACK, regular.foreground());

        context.clip(FIRST - 8, TOP - 8, 316, 164, () -> {
            RenderSystem.disableDepthTest();
            try {
                int[] before = glState();
                context.glass(List.of(new GlassDraw(GlassRegion.capsule(FIRST, TOP, WIDTH, HEIGHT), neutral),
                        new GlassDraw(GlassRegion.capsule(SECOND, TOP, WIDTH, HEIGHT), lens),
                        new GlassDraw(GlassRegion.ellipse(268, 76, 40, 28), dark)));
                int[] after = glState();
                require(Arrays.equals(before, after), "Glass changed native draw state "
                        + Arrays.toString(before) + " -> " + Arrays.toString(after));
            } finally {
                RenderSystem.enableDepthTest();
            }

            var settings = ClientUiSettings.current();
            ClientUiSettings.update(settings.withDisplay(true, settings.reducedMotion(), settings.widgetBackdropBlur()));
            try {
                context.glass(List.of(new GlassDraw(GlassRegion.continuous(FIRST, 128, WIDTH, 28, 13), regular, -.7f, -.5f, 1),
                        new GlassDraw(GlassRegion.rounded(SECOND, 128, WIDTH, 28, 7), dark)));
            } finally {
                ClientUiSettings.update(settings);
            }
        });
        context.glass(List.of(new GlassDraw(DRAG_ZONE, regular)));
        context.glass(List.of(new GlassDraw(GlassRegion.capsule(265, 128, 55, 28), fallback)));
        context.text(Component.translatable("gui.done"), FIRST + 20, 138, regular.foreground().argb());
        context.text(Component.translatable("gui.back"), SECOND + 20, 138, dark.foreground().argb());
    }

    static void verify(NativeImage image, Minecraft client) {
        var stats = UnifiedUI.get().glass().diagnostics();
        require(stats.shaderReady() && stats.passesWithCapture() == 1 && stats.opticalDraws() == 6
                        && stats.fallbackDraws() == 1 && stats.captureBytes() > 0,
                "Glass did not render two batches against one capture " + stats);
        int changed = 0;
        for (int offset = 2; offset < 10; offset++) {
            if (pixel(image, client, FIRST + offset, CENTER_Y) != pixel(image, client, SECOND + offset, CENTER_Y)) changed++;
        }
        require(changed >= 3, "Refraction did not move the stripe edges");
        require(pixel(image, client, FIRST + WIDTH / 2, CENTER_Y) == pixel(image, client, SECOND + WIDTH / 2, CENTER_Y),
                "The center did not preserve the transmitted backdrop");
        require(luminance(pixel(image, client, FIRST + 12, 142)) < .105,
                "Light text is missing high-contrast protection");
        require(luminance(pixel(image, client, SECOND + 12, 142)) > .29,
                "Dark text is missing high-contrast protection");
        require(pixel(image, client, 292, 142) == GlassMaterial.Foreground.LIGHT.fallbackArgb(),
                "The solid fallback did not draw");
    }

    private static int pixel(NativeImage image, Minecraft client, int x, int y) {
        int abgr = KernelClientSmoke.pixel(image, client, x, y);
        return abgr & 0xff00ff00 | (abgr & 255) << 16 | (abgr >> 16 & 255);
    }

    private static double luminance(int color) {
        return .2126 * linear(color >> 16 & 255) + .7152 * linear(color >> 8 & 255) + .0722 * linear(color & 255);
    }

    private static double linear(int component) {
        double value = component / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }

    private static int[] glState() {
        try (var stack = MemoryStack.stackPush()) {
            var viewport = stack.mallocInt(4);
            var scissor = stack.mallocInt(4);
            var color = stack.malloc(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissor);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, color);
            return new int[] {
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM), GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                    GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING), GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),
                    GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D), GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA), GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    GL11.glIsEnabled(GL11.GL_BLEND) ? 1 : 0, GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) ? 1 : 0,
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST) ? 1 : 0, GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) ? 1 : 0,
                    GL11.glIsEnabled(GL11.GL_CULL_FACE) ? 1 : 0, GL11.glIsEnabled(GL11.GL_STENCIL_TEST) ? 1 : 0,
                    viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3),
                    scissor.get(0), scissor.get(1), scissor.get(2), scissor.get(3),
                    color.get(0), color.get(1), color.get(2), color.get(3)
            };
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
