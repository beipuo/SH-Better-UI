package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;

/** 山海：单个四边形内逐像素计算圆弧，注册的 shader 由游戏负责重载与释放。 */
@Mod.EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CapsuleRenderer extends RenderType {
    private static ShaderInstance shader;
    private static final RenderType TYPE = type("sh_better_ui_capsule", COLOR_WRITE);
    private static final RenderType DEPTH_TYPE = type("sh_better_ui_rounded_gui", COLOR_DEPTH_WRITE);

    private static RenderType type(String name, WriteMaskStateShard writeMask) {
        return create(name, DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(writeMask)
                    .createCompositeState(false));
    }

    private CapsuleRenderer() {
        super("sh_better_ui_capsule", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, "capsule"), DefaultVertexFormat.POSITION_TEX),
                loaded -> shader = loaded);
    }

    static void draw(GuiGraphics graphics, float x, float y, float width, float height,
                     float radius, float stroke, float pixelsPerUnit, int edge, int fill, boolean writeDepth,
                     float turn, float breath, float spread) {
        graphics.flush();
        if (shader == null) {
            CapsuleMesh.emit(width, height, radius, stroke, pixelsPerUnit, edge, fill,
                    colorVertices(graphics, x, y));
        } else {
            shader.safeGetUniform("Size").set(width, height);
            shader.safeGetUniform("CornerRadius").set(radius);
            shader.safeGetUniform("Stroke").set(stroke);
            color("EdgeColor", edge);
            color("FillColor", fill);
            var lighting = shader.getUniform("CardLight");
            if (lighting != null) {
                if (spread > 0f) {
                    lighting.set(turn, breath, spread);
                    quad(graphics, TYPE, x, y, -spread, height - 2f, width + spread, height + spread);
                    graphics.flush();
                }
                lighting.set(turn, 0f, 0f);
            }
            float margin = 1f / Math.max(0.01f, pixelsPerUnit);
            quad(graphics, writeDepth ? DEPTH_TYPE : TYPE, x, y, -margin, -margin, width + margin, height + margin);
        }
        // 每次提交后立即绘制，防止下一控件的 uniform 覆盖尚未提交的顶点。
        graphics.flush();
    }

    private static void quad(GuiGraphics graphics, RenderType type, float x, float y,
                             float left, float top, float right, float bottom) {
        var pose = graphics.pose().last().pose();
        var vertices = graphics.bufferSource().getBuffer(type);
        vertices.vertex(pose, x + left, y + top, 0f).uv(left, top).endVertex();
        vertices.vertex(pose, x + left, y + bottom, 0f).uv(left, bottom).endVertex();
        vertices.vertex(pose, x + right, y + bottom, 0f).uv(right, bottom).endVertex();
        vertices.vertex(pose, x + right, y + top, 0f).uv(right, top).endVertex();
    }

    /** 写入当前 GUI 层的颜色四边形，提交由调用方负责。 */
    static CapsuleMesh.VertexSink colorVertices(GuiGraphics graphics, float x, float y) {
        var pose = graphics.pose().last().pose();
        var vertices = graphics.bufferSource().getBuffer(RenderType.gui());
        return (px, py, color) -> vertices.vertex(pose, x + px, y + py, 0f).color(color).endVertex();
    }

    private static void color(String name, int argb) {
        shader.safeGetUniform(name).set((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f,
                (argb & 255) / 255f, (argb >>> 24) / 255f);
    }
}
