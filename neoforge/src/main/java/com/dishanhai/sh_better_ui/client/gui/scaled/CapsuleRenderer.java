package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.io.IOException;

/** 山海：单个四边形内逐像素计算圆弧，注册的 shader 由游戏负责重载与释放。 */
@EventBusSubscriber(modid = ShBetterUi.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
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
                     float radius, float stroke, float pixelsPerUnit, int edge, int fill, boolean writeDepth) {
        graphics.flush();
        var pose = graphics.pose().last().pose();
        if (shader == null) {
            var vertices = graphics.bufferSource().getBuffer(RenderType.gui());
            CapsuleMesh.emit(width, height, radius, stroke, pixelsPerUnit, edge, fill,
                    (px, py, color) -> vertices.addVertex(pose, x + px, y + py, 0f).setColor(color));
        } else {
            shader.safeGetUniform("Size").set(width, height);
            shader.safeGetUniform("CornerRadius").set(radius);
            shader.safeGetUniform("Stroke").set(stroke);
            color("EdgeColor", edge);
            color("FillColor", fill);
            float margin = 1f / Math.max(0.01f, pixelsPerUnit);
            float left = -margin, top = -margin, right = width + margin, bottom = height + margin;
            var vertices = graphics.bufferSource().getBuffer(writeDepth ? DEPTH_TYPE : TYPE);
            vertices.addVertex(pose, x + left, y + top, 0f).setUv(left, top);
            vertices.addVertex(pose, x + left, y + bottom, 0f).setUv(left, bottom);
            vertices.addVertex(pose, x + right, y + bottom, 0f).setUv(right, bottom);
            vertices.addVertex(pose, x + right, y + top, 0f).setUv(right, top);
        }
        // 每次提交后立即绘制，防止下一控件的 uniform 覆盖尚未提交的顶点。
        graphics.flush();
    }

    private static void color(String name, int argb) {
        shader.safeGetUniform(name).set((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f,
                (argb & 255) / 255f, (argb >>> 24) / 255f);
    }
}
