package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderStateShard.TransparencyStateShard;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

/** 山海：隔离波面提交批次时更改的状态。RenderType 的清理只重置默认值。 */
record WaveRenderState(ShaderInstance shader, int program, boolean depthTest, boolean depthWrite,
                       int depthFunc, boolean cull, boolean blend, int sourceRgb, int destinationRgb,
                       int sourceAlpha, int destinationAlpha, int equationRgb, int equationAlpha, int colorMask) {
    // ShaderInstance 会缓存 blend 设置，恢复宿主状态后下次绘制仍要显式设定。
    static final TransparencyStateShard ALPHA_BLEND = new TransparencyStateShard("sh_better_ui_wave_alpha", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }, RenderSystem::disableBlend);

    static WaveRenderState capture() {
        try (var stack = MemoryStack.stackPush()) {
            var mask = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
            int channels = (mask.get(0) != 0 ? 1 : 0) | (mask.get(1) != 0 ? 2 : 0)
                    | (mask.get(2) != 0 ? 4 : 0) | (mask.get(3) != 0 ? 8 : 0);
            return new WaveRenderState(RenderSystem.getShader(), GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST), GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC), GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_BLEND), GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA), GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
                    GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA), channels);
        }
    }

    void restore() {
        RenderSystem.setShader(() -> shader);
        GlStateManager._glUseProgram(program);
        RenderSystem.colorMask((colorMask & 1) != 0, (colorMask & 2) != 0,
                (colorMask & 4) != 0, (colorMask & 8) != 0);
        RenderSystem.depthMask(depthWrite);
        RenderSystem.depthFunc(depthFunc);
        if (depthTest) RenderSystem.enableDepthTest();
        else RenderSystem.disableDepthTest();
        if (cull) RenderSystem.enableCull();
        else RenderSystem.disableCull();
        GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
        RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
        if (blend) RenderSystem.enableBlend();
        else RenderSystem.disableBlend();
    }
}
