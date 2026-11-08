package com.dishanhai.sh_better_ui.client.gui.scaled;

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
    /** 父面板复用自己的胶囊和真实布局骨架，不额外创建页面实例。 */
    public interface Target {
        void prepareReturnLayer();
    }

    private final Screen owner;
    private Screen parent;
    private boolean prepared;
    private boolean rendering;
    private long startedAt;
    private List<UiSkeleton.Block> fallbackBlocks = List.of();

    public UiScreenReturn(Screen owner) {
        this.owner = owner;
    }

    /** 同一尺寸只准备一次；沿用父实例中的草稿、所选页及焦点恢复信息。 */
    public void prepare(Screen previous, int width, int height) {
        if (previous == owner) return;
        if (prepared && parent == previous && (previous == null || previous.width == width && previous.height == height)) return;
        parent = previous;
        prepared = true;
        startedAt = PageTransition.nowMs();
        if (parent == null) return;
        if (parent.width != width || parent.height != height) parent.resize(Minecraft.getInstance(), width, height);
        if (parent instanceof Target target) {
            target.prepareReturnLayer();
            fallbackBlocks = List.of();
        } else {
            fallbackBlocks = parent.children().stream().filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast).filter(widget -> widget.visible)
                    .map(widget -> new UiSkeleton.Block(new Bounds(widget.getX(), widget.getY(),
                            widget.getWidth(), widget.getHeight()))).toList();
        }
    }

    /** parent 为 null 时保留游戏已经绘制的世界，不增加本页遮罩或黑底。 */
    public void render(GuiGraphics graphics, int width, int height, float partialTick, UiPreferences preferences) {
        if (!prepared || parent == null || rendering) return;
        prepare(parent, width, height);
        graphics.flush();
        graphics.pose().pushPose();
        try {
            renderParent(graphics, partialTick);
            if (!(parent instanceof Target)) {
                long now = PageTransition.nowMs();
                float opacity = preferences.reducedMotion() ? 0f : 1f - GuiMotion.clamp01((now - startedAt) / 240f);
                if (opacity > 0f) {
                    int veil = GuiMotion.lerpColor(DarkModeTheme.BACKGROUND & 0xFFFFFF, DarkModeTheme.BACKGROUND, opacity);
                    graphics.fill(0, 0, width, height, veil);
                    UiSkeleton.draw(graphics, new Bounds(0, 0, width, height), fallbackBlocks, now, preferences, opacity);
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
            parent.render(graphics, -1, -1, partialTick);
        } finally {
            rendering = false;
        }
    }
}
