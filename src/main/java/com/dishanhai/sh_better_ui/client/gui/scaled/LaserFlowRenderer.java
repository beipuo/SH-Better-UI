package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** 山海：在一个 GUI 四边形内合成光束、受光面和可选纹理。 */
final class LaserFlowRenderer extends RenderType {
    private static ShaderInstance shader;
    private static final RenderType TYPE = create("sh_better_ui_laser_flow", DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE).createCompositeState(false));

    private LaserFlowRenderer() {
        super("sh_better_ui_laser_flow", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }

    static void registerShaders(ResourceProvider resources, BiConsumer<ShaderInstance, Consumer<ShaderInstance>> register) {
        var id = ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, "laser_flow");
        try {
            register.accept(new ShaderInstance(resources, id, DefaultVertexFormat.POSITION_TEX), loaded -> shader = loaded);
        } catch (IOException exception) {
            shader = null;
            ShBetterUi.LOGGER.warn("Could not load GUI shader {}", id, exception);
        }
    }

    static boolean available() { return shader != null; }

    static void draw(GuiGraphics graphics, Bounds bounds, LaserFlowProfile profile, LaserFlowSurface surface,
                     LaserFlowReveal reveal, UiPointerTracker.Frame pointer, float phase, float opening) {
        graphics.flush();
        float width = bounds.width();
        float height = bounds.height();
        float beamX = width * profile.beamPosition();
        float impactY = height * profile.surfaceLevel();
        float left = 0;
        float top = impactY;
        float right = width;
        float surfaceHeight = 0;
        float corner = 0;
        if (surface != null && surface.bounds().width() > 0 && surface.bounds().height() > 0) {
            var panel = surface.bounds();
            float panelLeft = (float) panel.x() - bounds.x();
            float panelRight = panelLeft + panel.width();
            if (beamX >= panelLeft && beamX <= panelRight) {
                left = panelLeft;
                right = panelRight;
                top = (float) panel.y() - bounds.y();
                surfaceHeight = panel.height();
                corner = surface.radius();
                float offset = beamX - Math.clamp(beamX, left + corner, right - corner);
                impactY = top + corner - (float) Math.sqrt(Math.max(0, corner * corner - offset * offset));
            }
        }

        boolean showImage = pointer.inside() && reveal != null && reveal.radius() > 0 && reveal.opacity() > 0;
        int color = profile.color();
        shader.safeGetUniform("Area").set(width, height);
        shader.safeGetUniform("Beam").set(beamX, impactY, corner);
        shader.safeGetUniform("Surface").set(left, top, right, surfaceHeight);
        shader.safeGetUniform("Tint").set((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f,
                (color & 255) / 255f, (color >>> 24) / 255f);
        shader.safeGetUniform("Shape").set(profile.beamWidth(), profile.flare(), profile.spread(), profile.spill());
        shader.safeGetUniform("Material").set(profile.fog(), profile.dust(), profile.streaks(), profile.dots());
        shader.safeGetUniform("Motion").set(phase, opening, profile.pulse(), (float) (profile.seed() & 0xFFFF));
        shader.safeGetUniform("Lens").set(pointer.x(), pointer.y(),
                pointer.inside() && profile.mouseInteraction() ? 1f : 0f);
        shader.safeGetUniform("Options").set(profile.intensity(), profile.lightMode() ? 1f : 0f,
                showImage ? reveal.radius() : 0f, showImage ? reveal.opacity() : 0f);

        if (!showImage) {
            quad(graphics, bounds);
            return;
        }
        int activeTexture = GlStateManager._getActiveTexture();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            shader.setSampler("RevealSampler", Minecraft.getInstance().getTextureManager().getTexture(reveal.texture()));
            quad(graphics, bounds);
        } finally {
            shader.setSampler("RevealSampler", null);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(boundTexture);
            RenderSystem.activeTexture(activeTexture);
        }
    }

    private static void quad(GuiGraphics graphics, Bounds bounds) {
        VisualRevealRenderer.quad(graphics, TYPE, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                graphics.pose().last().pose());
    }
}
