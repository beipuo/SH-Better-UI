package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;

import java.util.Objects;

/** 山海：从小胶囊揭示面板，正文保持原位，由骨架过渡到真实内容。 */
public final class UiCapsuleReveal {
    private final InterruptibleAnimation opening = new InterruptibleAnimation(Spring.FLUID_PANEL, 1f);
    private final InterruptibleAnimation originX = new InterruptibleAnimation(Spring.INDICATOR);
    private float originY;
    private boolean pending;
    private boolean closing;
    private boolean closed;
    private Runnable onClosed;

    public void startFrom(AbstractWidget source) {
        startAt(source.getX() + source.getWidth() * 0.5f, source.getY() + source.getHeight() * 0.5f);
    }

    public void startAt(float centerX, float centerY) {
        startAt(centerX, centerY, PageTransition.nowMs());
    }

    void startAt(float centerX, float centerY, long now) {
        if (closing || closed) return;
        opening.sample(now);
        originY = centerY;
        if (pending || !opening.running()) {
            originX.snapTo(centerX);
            pending = true;
        } else {
            originX.animateTo(centerX, now);
        }
    }

    public boolean active() {
        return pending || closing || opening.running();
    }

    public void finish() {
        if (closing) {
            completeClose();
            return;
        }
        if (closed) return;
        pending = false;
        opening.snapTo(1f);
        originX.finish();
    }

    public boolean closing() { return closing; }

    /** 沿当前轮廓收回到来源胶囊，完成后只调用一次返回动作。减少动效时立即完成。 */
    public void close(UiPreferences preferences, Runnable onClosed) {
        close(PageTransition.nowMs(), preferences, onClosed);
    }

    void close(long now, UiPreferences preferences, Runnable onClosed) {
        if (closing || closed) return;
        this.onClosed = Objects.requireNonNull(onClosed);
        pending = false;
        closing = true;
        opening.setOpen(false, now);
        if (preferences.reducedMotion()) completeClose();
    }

    private void completeClose() {
        opening.snapTo(0f);
        closing = false;
        closed = true;
        Runnable action = onClosed;
        onClosed = null;
        if (action != null) action.run();
    }

    float sampleProgress(long now, UiPreferences preferences) {
        if (closed) return 0f;
        if (pending) {
            pending = false;
            opening.snapTo(0f);
            opening.setOpen(true, now);
        }
        float progress = GuiMotion.clamp01(opening.sample(now, preferences));
        if (closing) return progress <= 0.005f ? 0f : progress;
        if (progress >= 0.995f) {
            finish();
            progress = 1f;
        }
        return progress;
    }

    /** 正在变形时绘制底板与裁切的正文，调用方应暂停指针输入。返回 false 时照常绘制。 */
    public boolean render(GuiGraphics graphics, Bounds target, UiPreferences preferences, Runnable content) {
        if (closed) return true;
        if (!active()) return false;
        long now = PageTransition.nowMs();
        float progress = sampleProgress(now, preferences);
        if (target.width() < FluidMorph.SEED_WIDTH || target.height() < FluidMorph.SEED_HEIGHT) {
            finish();
            return closed;
        }
        if (!closing && progress >= 0.995f) return false;

        float opacity = GuiRenderUtil.easeOut((progress - 0.54f) / 0.44f);
        if (!closing && opacity < 1f) {
            // 前进时先提示完整目标布局，不能被来源按钮附近的小轮廓裁掉。
            UiSkeleton.draw(graphics, target, UiSkeleton.cardBlocks(target), now, preferences, (1f - opacity) * 0.42f);
        }
        var seed = FluidMorph.seed(originX.sample(now), originY);
        var panel = new GuiRect(target.x(), target.y(), target.width(), target.height());
        var frame = FluidMorph.between(seed, panel, LiquidGlassTheme.POPOVER_RADIUS, progress);
        LiquidGlassTheme.popover(graphics, frame, preferences, progress);
        if (closing && progress == 0f) {
            // 先绘制与入场相同的胶囊，再把界面交回上一页。
            completeClose();
            return true;
        }
        var rect = frame.rect();
        float inset = frame.radius() * 0.4f * (1f - progress);
        int x = Math.max(target.x(), (int) Math.ceil(rect.getX() + inset));
        int y = Math.max(target.y(), (int) Math.ceil(rect.getY() + inset));
        int right = Math.min(target.right(), (int) Math.floor(rect.getX() + rect.getW() - inset));
        int bottom = Math.min(target.bottom(), (int) Math.floor(rect.getY() + rect.getH() - inset));
        if (right <= x || bottom <= y) return true;

        var clip = new Bounds(x, y, right - x, bottom - y);
        graphics.enableScissor(x, y, right, bottom);
        try {
            if (opacity > 0f) {
                content.run();
                int fill = LiquidGlassTheme.POPOVER_FILL | 0xFF000000;
                int veil = GuiMotion.lerpColor(fill, fill & 0xFFFFFF, opacity);
                graphics.fill(x, y, right, bottom, veil);
            }
            UiSkeleton.draw(graphics, clip, UiSkeleton.cardBlocks(target), now, preferences, 1f - opacity);
        } finally {
            graphics.disableScissor();
        }
        return true;
    }
}
