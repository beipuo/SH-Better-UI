package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** 山海：在共享 GUI 批次里绘制光束四边形。 */
final class LightRaysRenderer extends RenderType {
    private static ShaderInstance shader;
    private static final RenderType TYPE = create("sh_better_ui_light_rays", DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE).createCompositeState(false));

    private LightRaysRenderer() {
        super("sh_better_ui_light_rays", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }

    static void registerShaders(ResourceProvider resources, BiConsumer<ShaderInstance, Consumer<ShaderInstance>> register) {
        var id = ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, "light_rays");
        try {
            register.accept(new ShaderInstance(resources, id, DefaultVertexFormat.POSITION_TEX), loaded -> shader = loaded);
        } catch (IOException exception) {
            shader = null;
            ShBetterUi.LOGGER.warn("Could not load GUI shader {}", id, exception);
        }
    }

    static boolean available() { return shader != null; }

    static void draw(GuiGraphics graphics, Bounds bounds, LightRaysProfile profile, LightRaysMotion motion) {
        graphics.flush();
        float width = bounds.width();
        float height = bounds.height();
        var origin = profile.raysOrigin();
        float sourceX = origin.x * width;
        float sourceY = origin.y * height;
        float pointerX = motion.pointerX * width;
        float pointerY = motion.pointerY * height;
        double radians = Math.toRadians(profile.tilt());
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float aimX = origin.directionX * cos - origin.directionY * sin;
        float aimY = origin.directionX * sin + origin.directionY * cos;
        if (profile.followMouse() && motion.aim > 0 && profile.mouseInfluence() > 0) {
            float dx = pointerX - sourceX;
            float dy = pointerY - sourceY;
            float steering = profile.mouseInfluence() * motion.aim / (float) Math.hypot(dx, dy);
            float blend = profile.mouseInfluence() * motion.aim;
            float mixedX = (1 - blend) * aimX + steering * dx;
            float mixedY = (1 - blend) * aimY + steering * dy;
            float length = (float) Math.hypot(mixedX, mixedY);
            if (length > 1e-4f) {
                aimX = mixedX / length;
                aimY = mixedY / length;
            }
        }

        int color = profile.raysColor();
        shader.safeGetUniform("Area").set(width, height);
        shader.safeGetUniform("Beam").set(sourceX, sourceY, aimX, aimY);
        shader.safeGetUniform("Cone").set(profile.rayLength(), profile.fadeDistance(), profile.lightSpread(), profile.rayDensity());
        shader.safeGetUniform("Material").set(profile.raySharpness(), profile.glow(), profile.haze(), profile.atmosphere());
        shader.safeGetUniform("Tint").set((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f,
                (color & 255) / 255f, (color >>> 24) / 255f * profile.opacity());
        shader.safeGetUniform("Tone").set(profile.intensity(), profile.saturation(), profile.noiseAmount(), profile.lightMode() ? 1f : 0f);
        shader.safeGetUniform("Motion").set(motion.phase, motion.opening, profile.distortion(), profile.pulsating() ? 1f : 0f);
        shader.safeGetUniform("Shadow").set(pointerX, pointerY, profile.cursorShadow() ? motion.shadow : 0f);
        var ages = motion.pulseAges;
        shader.safeGetUniform("PulseAges").set(ages[0], ages[1], ages[2], ages[3]);
        VisualRevealRenderer.quad(graphics, TYPE, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                graphics.pose().last().pose());
    }
}
