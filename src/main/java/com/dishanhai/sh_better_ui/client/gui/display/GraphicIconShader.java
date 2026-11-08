package com.dishanhai.sh_better_ui.client.gui.display;

import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/** 山海：以 POSITION_TEX 绘制单个图标，shader 使用 Sampler0、GUI 矩阵与 ColorModulator。 */
public record GraphicIconShader(ResourceLocation id, BiConsumer<ShaderInstance, Frame> uniforms) {
    public static final GraphicIconShader GRADIENT_SWEEP = builtIn("icon_gradient_sweep", 2400L);
    public static final GraphicIconShader COLOR_CYCLE = builtIn("icon_color_cycle", 3600L);
    public static final GraphicIconShader ENERGY_WAVE = builtIn("icon_energy_wave", 2000L);
    public static final GraphicIconShader PULSE = builtIn("icon_pulse", 1800L);

    static final List<GraphicIconShader> BUILT_INS = List.of(GRADIENT_SWEEP, COLOR_CYCLE, ENERGY_WAVE, PULSE);

    public GraphicIconShader {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(uniforms, "uniforms");
    }

    public GraphicIconShader(ResourceLocation id) {
        this(id, (shader, frame) -> {});
    }

    /** 每次提交前供回调设置自定义 uniform。尺寸使用 GUI 单位，时间为单调时钟。 */
    public record Frame(long timeMillis, boolean reducedMotion, int size) {}

    private static GraphicIconShader builtIn(String path, long periodMillis) {
        return new GraphicIconShader(ResourceLocation.tryParse("sh_better_ui:" + path),
                (shader, frame) -> shader.safeGetUniform("IconPhase").set(phase(frame, periodMillis)));
    }

    static float phase(Frame frame, long periodMillis) {
        return frame.reducedMotion() ? 0.5f : Math.floorMod(frame.timeMillis(), periodMillis) / (float) periodMillis;
    }

    boolean draw(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size, UiPreferences preferences) {
        String name = id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
        Minecraft minecraft = Minecraft.getInstance();
        ShaderInstance shader = minecraft.gameRenderer.getShader(name);
        if (shader == null || shader.getVertexFormat() != DefaultVertexFormat.POSITION_TEX) return false;

        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int activeTexture = GlStateManager._getActiveTexture();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            uniforms.accept(shader, new Frame(PageTransition.nowMs(), preferences.reducedMotion(), size));
            shader.safeGetUniform("ModelViewMat").set(RenderSystem.getModelViewMatrix());
            shader.safeGetUniform("ProjMat").set(RenderSystem.getProjectionMatrix());
            shader.safeGetUniform("ColorModulator").set(RenderSystem.getShaderColor());
            shader.setSampler("Sampler0", minecraft.getTextureManager().getTexture(texture));
            try {
                shader.apply();
                // 核心 shader 可以设置混合状态，图标统一使用直通 alpha 的 source-over 合成。
                RenderSystem.enableBlend();
                RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
                RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                        GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
                IconShaderRenderer.drawQuad(graphics.pose().last().pose(), x, y, size);
            } finally {
                shader.clear();
                shader.setSampler("Sampler0", null);
            }
        } finally {
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.bindTexture(boundTexture);
            RenderSystem.activeTexture(activeTexture);
            GlStateManager._glUseProgram(previousProgram);
        }
        return true;
    }
}
