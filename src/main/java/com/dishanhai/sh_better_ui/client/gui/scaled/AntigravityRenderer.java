package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** 山海：一次批量绘制所有粒子；SDF 软边沿用 GUI pose、外层颜色与裁切，无实例私有 GL 资源。 */
final class AntigravityRenderer extends RenderType {
    private static ShaderInstance shader;
    private static final RenderType TYPE = create("sh_better_ui_antigravity", DefaultVertexFormat.POSITION_TEX_COLOR,
            VertexFormat.Mode.QUADS, 256 * 1024, false, false, CompositeState.builder()
                    .setShaderState(new ShaderStateShard(() -> shader))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));

    private AntigravityRenderer() {
        super("sh_better_ui_antigravity", DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS,
                256 * 1024, false, false, () -> {}, () -> {});
    }

    static void registerShaders(ResourceProvider resources, BiConsumer<ShaderInstance, Consumer<ShaderInstance>> register) {
        var id = ResourceLocation.fromNamespaceAndPath(ShBetterUi.MOD_ID, "antigravity");
        try {
            register.accept(new ShaderInstance(resources, id, DefaultVertexFormat.POSITION_TEX_COLOR), loaded -> shader = loaded);
        } catch (IOException exception) {
            shader = null;
            ShBetterUi.LOGGER.warn("Could not load GUI shader {}", id, exception);
        }
    }

    static boolean draw(GuiGraphics graphics, Bounds area, AntigravityProfile p, AntigravityField f, int[] palette) {
        if (shader == null) return false;
        if (p.particleSize() == 0 || p.opacity() == 0) return true;
        Matrix4f pose = graphics.pose().last().pose();
        if (Math.abs(pose.determinant()) < 1e-8f) return false;
        graphics.flush();
        shader.safeGetUniform("InversePose").set(new Matrix4f(pose).invert());
        shader.safeGetUniform("ClipRect").set((float) area.x(), (float) area.y(), (float) area.right(), (float) area.bottom());
        shader.safeGetUniform("ParticleShape").set(p.particleShape().ordinal());
        var vertices = graphics.bufferSource().getBuffer(TYPE);
        float unit = area.height() / 31.5f;
        float ringRadius = p.ringRadius() * unit * (1 - .38f * f.charge);
        float band = Math.max(unit * 6, 1);
        boolean capsule = p.particleShape() == AntigravityProfile.Shape.CAPSULE;
        boolean conic = p.colorMode() == AntigravityProfile.ColorMode.CONIC;
        for (int i = 0; i < f.x.length; i++) {
            float dx = f.x[i] - f.cursorX, dy = f.y[i] - f.cursorY;
            float distance = (float) Math.hypot(dx, dy);
            float dirX = distance > .001f ? dx / distance : 1;
            float dirY = distance > .001f ? dy / distance : 0;
            float speed = (float) Math.hypot(f.vx[i], f.vy[i]);
            float streak = Math.min(2.5f, speed / Math.max(unit * 30, .001f));
            if (speed > .001f) {
                float sign = Math.signum(f.vx[i] * dirX + f.vy[i] * dirY + .0001f);
                float blend = Math.min(1, streak);
                blend = blend * blend * (3 - 2 * blend);
                dirX += (f.vx[i] / speed * sign - dirX) * blend;
                dirY += (f.vy[i] / speed * sign - dirY) * blend;
                float norm = (float) Math.hypot(dirX, dirY);
                if (norm > .001f) { dirX /= norm; dirY /= norm; }
                else { dirX = 1; dirY = 0; }
            }
            float ring = (float) Math.exp(-Math.pow((distance - ringRadius) / band, 2));
            float grow = p.ambient() + (1 - p.ambient()) * ring;
            float depth = 1 + f.depth[i] * .32f * p.depthFactor();
            float pulse = 1 + (float) Math.sin(f.time * p.pulseSpeed() + f.phase[i]) * .22f * p.particleVariance();
            float size = p.particleSize() * unit * .6f * depth * pulse * (.85f + .3f * f.sizeSeed[i])
                    * (1 + f.charge * .25f * ring);
            float halfX = capsule ? size * Math.max(.17f, .5f * grow) * (1 + streak * 1.2f)
                    : size * (p.particleShape() == AntigravityProfile.Shape.SPHERE ? .22f : .26f) * (.45f + .55f * grow);
            float halfY = capsule ? size * .17f * (.55f + .45f * ring) : halfX;
            float u = switch (p.colorMode()) {
                case CONIC -> (float) (Math.atan2(dy, dx) / (Math.PI * 2)) + f.hue + (f.colorSeed[i] - .5f) * .06f;
                case RADIAL -> Math.clamp(distance / Math.max(ringRadius * 2.4f, .001f), 0, 1) + (f.colorSeed[i] - .5f) * .06f;
                case RANDOM -> (float) Math.min(p.colors().size() - 1, (int) (f.colorSeed[i] * p.colors().size()))
                        / Math.max(p.colors().size() - 1, 1);
            };
            int argb = AntigravityPalette.sample(palette, u, conic);
            float alpha = grow * (1 - Math.max(-f.depth[i], 0) * .35f * p.depthFactor()) * f.reveal * p.opacity();
            int color = Math.round((argb >>> 24) * alpha) << 24 | (argb & 0xFFFFFF);
            if ((color >>> 24) < 1) continue;
            float x = area.x() + f.x[i], y = area.y() + f.y[i];
            // SDF 留出软边空间。
            halfX *= 1.25f;
            halfY *= 1.25f;
            vertex(vertices, pose, x, y, dirX, dirY, halfX, halfY, -1, -1, color);
            vertex(vertices, pose, x, y, dirX, dirY, halfX, halfY, -1, 1, color);
            vertex(vertices, pose, x, y, dirX, dirY, halfX, halfY, 1, 1, color);
            vertex(vertices, pose, x, y, dirX, dirY, halfX, halfY, 1, -1, color);
        }
        graphics.bufferSource().endBatch(TYPE);
        return true;
    }

    private static void vertex(VertexConsumer vertices, Matrix4f pose, float x, float y, float dx, float dy,
                               float halfX, float halfY, float u, float v, int color) {
        VisualRevealRenderer.particleVertex(vertices, pose, x + dx * halfX * u - dy * halfY * v,
                y + dy * halfX * u + dx * halfY * v, u * 1.25f, v * 1.25f, color);
    }
}
