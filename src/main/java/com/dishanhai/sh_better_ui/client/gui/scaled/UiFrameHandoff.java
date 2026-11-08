package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

/** 山海：在切换命令到来时留住主帧缓冲的实际画面，从该帧淡出到新内容。 */
public final class UiFrameHandoff {
    private final InterruptibleAnimation opacity = new InterruptibleAnimation(Spring.PANEL);
    private int texture;
    private int textureWidth;
    private int textureHeight;
    private int framebuffer;
    private int framebufferWidth;
    private int framebufferHeight;
    private double guiScale;
    private float x;
    private float y;
    private boolean pending;

    /** 在当前页面仍是屏幕所显示的画面时调用。连续切换覆盖此前快照。 */
    public boolean capture(Bounds area, UiPreferences preferences) {
        if (area.width() == 0 || area.height() == 0 || preferences.reducedMotion()
                || !RenderSystem.isOnRenderThread() || VisualRevealRenderer.handoffShader() == null) {
            clear();
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        RenderTarget target = client == null ? null : client.getMainRenderTarget();
        var window = client == null ? null : client.getWindow();
        double scale = window == null ? 0 : window.getGuiScale();
        if (target == null || target.frameBufferId <= 0 || !(scale > 0)
                || target.viewWidth != window.getWidth() || target.viewHeight != window.getHeight()) {
            clear();
            return false;
        }

        int left = Math.clamp((int) Math.floor(area.x() * scale), 0, target.viewWidth);
        int top = Math.clamp((int) Math.floor(area.y() * scale), 0, target.viewHeight);
        int right = Math.clamp((int) Math.ceil(area.right() * scale), 0, target.viewWidth);
        int bottom = Math.clamp((int) Math.ceil(area.bottom() * scale), 0, target.viewHeight);
        if (right <= left || bottom <= top) {
            clear();
            return false;
        }

        int width = right - left;
        int height = bottom - top;
        if (!copy(target.frameBufferId, left, target.viewHeight - bottom, width, height)) {
            clear();
            return false;
        }
        textureWidth = width;
        textureHeight = height;
        framebuffer = target.frameBufferId;
        framebufferWidth = target.viewWidth;
        framebufferHeight = target.viewHeight;
        guiScale = scale;
        x = (float) (left / scale);
        y = (float) (top / scale);
        pending = true;
        return true;
    }

    public boolean active() {
        return texture != 0;
    }

    /** 第一帧保留完整快照；之后由原有混合轨道将快照淡出。 */
    public void render(GuiGraphics graphics, UiPreferences preferences) {
        if (!active()) return;
        var shader = VisualRevealRenderer.handoffShader();
        if (preferences.reducedMotion() || !RenderSystem.isOnRenderThread() || shader == null || !sameViewport()) {
            clear();
            return;
        }

        long now = PageTransition.nowMs();
        if (pending) {
            opacity.snapTo(1f);
            opacity.transitionTo(0f, now, InterruptibleAnimation.Policy.BLEND, 0);
            pending = false;
        }
        float alpha = opacity.sample(now);
        if (alpha <= 0f) {
            clear();
            return;
        }
        float width = (float) (textureWidth / guiScale);
        float height = (float) (textureHeight / guiScale);
        SnapshotRenderer.draw(graphics, shader, texture, x, y, width, height, alpha);
    }

    /** 页面移除时释放尚未播放完毕的快照。 */
    public void clear() {
        int previous = texture;
        texture = 0;
        pending = false;
        opacity.snapTo(0f);
        if (previous == 0) return;
        if (RenderSystem.isOnRenderThread()) TextureUtil.releaseTextureId(previous);
        else RenderSystem.recordRenderCall(() -> TextureUtil.releaseTextureId(previous));
    }

    private boolean sameViewport() {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return false;
        var window = client.getWindow();
        var target = client.getMainRenderTarget();
        return window != null && target != null && target.frameBufferId == framebuffer && target.viewWidth == framebufferWidth
                && target.viewHeight == framebufferHeight && window.getWidth() == framebufferWidth
                && window.getHeight() == framebufferHeight && window.getGuiScale() == guiScale;
    }

    private boolean copy(int target, int left, int bottom, int width, int height) {
        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, target);
        int readBuffer = GL11.glGetInteger(GL11.GL_READ_BUFFER);
        try {
            if (GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) return false;
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            boolean newTexture = texture == 0;
            if (newTexture) texture = TextureUtil.generateTextureId();
            RenderSystem.bindTexture(texture);
            if (newTexture) {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            }
            if (newTexture || width != textureWidth || height != textureHeight) {
                // RGB 丢弃主帧缓冲中可能由其他绘制阶段留下的 alpha。
                GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, left, bottom, width, height, 0);
                return GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) == width
                        && GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) == height;
            } else {
                GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, left, bottom, width, height);
            }
            return true;
        } finally {
            GL11.glReadBuffer(readBuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            RenderSystem.bindTexture(boundTexture);
        }
    }

    private static final class SnapshotRenderer extends RenderType {
        private static final RenderType TYPE = create("sh_better_ui_frame_handoff", DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(VisualRevealRenderer::handoffShader))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private SnapshotRenderer() {
            super("sh_better_ui_frame_handoff", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS,
                    256, false, false, () -> {}, () -> {});
        }

        static void draw(GuiGraphics graphics, ShaderInstance handoffShader, int texture,
                         float x, float y, float width, float height, float alpha) {
            boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
            boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            boolean blending = GL11.glIsEnabled(GL11.GL_BLEND);
            int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            int destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            int destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            int equationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
            int equationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
            int activeTexture = GlStateManager._getActiveTexture();
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            int texture0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            RenderSystem.activeTexture(activeTexture);
            var shader = RenderSystem.getShader();
            float[] color = RenderSystem.getShaderColor().clone();
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var colorMask = stack.malloc(4);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
                try {
                    graphics.flush();
                    RenderSystem.disableDepthTest();
                    RenderSystem.colorMask(true, true, true, true);
                    GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
                    handoffShader.setSampler("TextureSampler", texture);
                    handoffShader.safeGetUniform("EffectMode").set(3);
                    RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
                    // GPU 纹理的 V=0 是屏幕底边，负高度把采样方向翻回 GUI 坐标。
                    VisualRevealRenderer.quad(graphics, TYPE, x, y + height, width, -height,
                            graphics.pose().last().pose());
                } finally {
                    RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
                    handoffShader.setSampler("TextureSampler", null);
                    RenderSystem.setShader(() -> shader);
                    RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                    RenderSystem.bindTexture(texture0);
                    RenderSystem.activeTexture(activeTexture);
                    RenderSystem.colorMask(colorMask.get(0) != 0, colorMask.get(1) != 0,
                            colorMask.get(2) != 0, colorMask.get(3) != 0);
                    RenderSystem.depthMask(depthMask);
                    RenderSystem.depthFunc(depthFunc);
                    if (depthTest) RenderSystem.enableDepthTest();
                    else RenderSystem.disableDepthTest();
                    if (cull) RenderSystem.enableCull();
                    else RenderSystem.disableCull();
                    GL20.glBlendEquationSeparate(equationRgb, equationAlpha);
                    RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
                    if (blending) RenderSystem.enableBlend();
                    else RenderSystem.disableBlend();
                }
            }
        }
    }
}
