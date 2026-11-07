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
    private static final RenderType TYPE = create("sh_better_ui_capsule", DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS, 256, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

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

    static void draw(GuiGraphics graphics, int x, int y, int width, int height,
                     float stroke, float pixelsPerUnit, int edge, int fill) {
        graphics.flush();
        var pose = graphics.pose().last().pose();
        if (shader == null) {
            var vertices = graphics.bufferSource().getBuffer(RenderType.gui());
            CapsuleMesh.emit(width, height, stroke, pixelsPerUnit, edge, fill,
                    (px, py, color) -> vertices.vertex(pose, x + px, y + py, 0f).color(color).endVertex());
        } else {
            shader.safeGetUniform("Size").set((float) width, (float) height);
            shader.safeGetUniform("Stroke").set(stroke);
            color("EdgeColor", edge);
            color("FillColor", fill);
            float margin = 1f / Math.max(0.01f, pixelsPerUnit);
            float left = -margin, top = -margin, right = width + margin, bottom = height + margin;
            var vertices = graphics.bufferSource().getBuffer(TYPE);
            vertices.vertex(pose, x + left, y + top, 0f).uv(left, top).endVertex();
            vertices.vertex(pose, x + left, y + bottom, 0f).uv(left, bottom).endVertex();
            vertices.vertex(pose, x + right, y + bottom, 0f).uv(right, bottom).endVertex();
            vertices.vertex(pose, x + right, y + top, 0f).uv(right, top).endVertex();
        }
        // 每次提交后立即绘制，防止下一控件的 uniform 覆盖尚未提交的顶点。
        graphics.flush();
    }

    private static void color(String name, int argb) {
        shader.safeGetUniform(name).set((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f,
                (argb & 255) / 255f, (argb >>> 24) / 255f);
    }
}
