package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** 山海：在当前页收回时预绘上一页，不切换 Screen，也不向上一页传递输入。 */
public final class UiScreenReturn {
    /** 父面板准备已展开的内容与焦点，作为当前胶囊收回时的底层。 */
    public interface Target {
        void prepareReturnLayer();
    }

    private final Screen owner;
    private final UiFrameHandoff handoff = new UiFrameHandoff();
    private Screen parent;
    private boolean prepared;
    private boolean rendering;
    private long startedAt;
    private List<UiSkeleton.Block> fallbackBlocks = List.of();

    public UiScreenReturn(Screen owner) {
        this.owner = owner;
    }

    /** 保存上一帧实际可见的合成结果，供前进、返回和中途反向共同使用。 */
    public void beginHandoff(Screen visible, UiPreferences preferences) {
        var minecraft = Minecraft.getInstance();
        if (visible != null && minecraft != null && minecraft.screen == visible) {
            handoff.capture(new Bounds(0, 0, visible.width, visible.height), preferences);
        }
    }

    public boolean renderTransition(UiCapsuleReveal entrance, GuiGraphics graphics, Bounds target,
                                    UiPreferences preferences, Runnable content) {
        return entrance.renderWithHandoff(graphics, target, preferences, content, () -> !handoff.active());
    }

    public void renderHandoff(GuiGraphics graphics, UiPreferences preferences) {
        handoff.render(graphics, preferences);
    }

    public void clearHandoff() {
        handoff.clear();
    }

    /** 同一尺寸只准备一次；沿用父实例中的草稿、所选页及焦点恢复信息。 */
    public void prepare(Screen previous, int width, int height) {
        if (previous == owner) return;
        if (prepared && parent == previous && (previous == null || previous.width == width && previous.height == height)) return;
        parent = previous;
        prepared = true;
        startedAt = PageTransition.nowMs();
        if (previous == null) return;
        UiFrameScheduler.renderPreview(() -> {
            if (previous.width != width || previous.height != height) {
                previous.resize(Minecraft.getInstance(), width, height);
            }
            if (previous instanceof Target target) {
                target.prepareReturnLayer();
                fallbackBlocks = List.of();
            } else {
                fallbackBlocks = previous.children().stream().filter(AbstractWidget.class::isInstance)
                        .map(AbstractWidget.class::cast).filter(widget -> widget.visible)
                        .map(widget -> new UiSkeleton.Block(new Bounds(widget.getX(), widget.getY(),
                                widget.getWidth(), widget.getHeight()))).toList();
            }
        });
    }

    /** parent 为 null 时保留游戏已经绘制的世界，不增加本页遮罩或黑底。 */
    public void render(GuiGraphics graphics, int width, int height, float partialTick, UiPreferences preferences) {
        renderLayer(graphics, width, height, partialTick, preferences);
    }

    /** 只绘制实际父页，供调用方用自己的动画进度合成背景。 */
    public void renderBackdrop(GuiGraphics graphics, int width, int height, float partialTick) {
        renderLayer(graphics, width, height, partialTick, null);
    }

    private void renderLayer(GuiGraphics graphics, int width, int height, float partialTick,
                             UiPreferences fallbackPreferences) {
        if (!prepared || parent == null || rendering) return;
        prepare(parent, width, height);
        graphics.flush();
        graphics.pose().pushPose();
        try {
            renderParent(graphics, partialTick);
            if (fallbackPreferences != null && !(parent instanceof Target)) {
                long now = PageTransition.nowMs();
                var effect = ThemeEffects.local("screenReturn");
                float opacity = fallbackPreferences.reducedMotion() || !effect.enabled() ? 0f : effect.strength()
                        * (1f - GuiMotion.clamp01((now - startedAt) * effect.speed() / 240f));
                if (opacity > 0f) {
                    int color = effect.ink(DarkModeTheme.backgroundColor());
                    int veil = GuiMotion.lerpColor(color & 0xFFFFFF, color, opacity);
                    graphics.fill(0, 0, width, height, veil);
                    UiSkeleton.draw(graphics, new Bounds(0, 0, width, height), fallbackBlocks, now,
                            fallbackPreferences, opacity);
                }
            }
        } finally {
            graphics.flush();
            graphics.setColor(1f, 1f, 1f, 1f);
            graphics.pose().popPose();
            RenderSystem.disableScissor();
            GaussBlur.clear();
            GaussBlur.clearTopLayer();
        }
    }

    void renderParent(GuiGraphics graphics, float partialTick) {
        if (parent == null || rendering) return;
        rendering = true;
        try {
            Screen preview = parent;
            UiFrameScheduler.renderPreview(() -> preview.render(graphics, -1, -1, partialTick));
        } finally {
            rendering = false;
        }
    }
}
