package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.glass.GuiRect;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** 山海：从小胶囊揭示面板，正文保持原位，由骨架过渡到真实内容。 */
public final class UiCapsuleReveal {
    static final float CONTENT_FADE_START = 0.54f;
    private static final float CONTENT_FADE_END = 0.98f;
    private static final int PANEL_PRIORITY = 30;

    private final InterruptibleAnimation opening = new InterruptibleAnimation(Spring.FLUID_PANEL, 1f);
    private final InterruptibleAnimation originX = new InterruptibleAnimation(Spring.INDICATOR);
    private float originY;
    private boolean pending;
    private long closeRevision;
    private boolean closed;
    private Runnable onClosed;
    private Supplier<? extends AbstractWidget> returnSource;
    private GuiRect closingSeed;
    private float closingProgress;
    private Bounds visibleContent;
    private Function<Bounds, List<UiSkeleton.Block>> skeletonLayout = UiSkeleton::cardBlocks;

    /** 按最终目标区提供占位块。块使用正文的 Screen 坐标，并随当前布局重新计算。 */
    public void setSkeletonLayout(Function<Bounds, List<UiSkeleton.Block>> layout) {
        skeletonLayout = Objects.requireNonNull(layout);
    }

    List<UiSkeleton.Block> skeletonBlocks(Bounds target) {
        return skeletonLayout.apply(target);
    }

    public void startFrom(AbstractWidget source) {
        startFrom(() -> source);
    }

    /** 入场仍使用小胶囊，返回时读取当前入口，兼容父页重建控件。 */
    public void startFrom(Supplier<? extends AbstractWidget> source) {
        if (closing() || closed) return;
        var widget = source.get();
        if (widget == null) return;
        returnSource = source;
        startAt(widget.getX() + widget.getWidth() * 0.5f, widget.getY() + widget.getHeight() * 0.5f);
    }

    public void startAt(float centerX, float centerY) {
        startAt(centerX, centerY, PageTransition.nowMs());
    }

    void startAt(float centerX, float centerY, long now) {
        if (closing() || closed) return;
        opening.sample(now);
        originY = centerY;
        if (pending || !opening.running()) {
            closingSeed = null;
            visibleContent = null;
            originX.snapTo(centerX);
            pending = true;
        } else {
            originX.animateTo(centerX, now);
        }
    }

    /** 只移动正在展开的胶囊起点，已经展开时保持当前轮廓。 */
    public void retargetAt(float centerX, float centerY) {
        long now = PageTransition.nowMs();
        if (closing() || closed) return;
        opening.sample(now);
        if (pending || opening.running()) startAt(centerX, centerY, now);
    }

    public boolean active() {
        return pending || closing() || opening.running();
    }

    public void finish() {
        if (closing()) {
            opening.finish();
            commitClose();
            return;
        }
        if (closed) return;
        pending = false;
        opening.snapTo(1f);
        originX.finish();
        closingSeed = null;
    }

    public boolean closing() { return closeRevision != 0L; }

    /** 打开途中只让已经显现的区域接收指针输入。 */
    public boolean allowsPointer(double x, double y) {
        return !closed && !closing() && (!active() || visibleContent != null && visibleContent.contains(x, y));
    }

    /** 键盘可以移动焦点；确认键只激活已经显现的控件。 */
    public boolean allowsActivation(int key, GuiEventListener focus) {
        return !activationKey(key) || !(focus instanceof AbstractWidget widget)
                || allowsPointer(widget.getX() + widget.getWidth() * 0.5, widget.getY() + widget.getHeight() * 0.5);
    }

    /** 父页在收起过程中已经可见，同一个入口可以反向打开子页。 */
    public boolean reopenFromSource(double x, double y, int button) {
        var source = closing() && button == GLFW.GLFW_MOUSE_BUTTON_LEFT && returnSource != null
                ? returnSource.get() : null;
        return source != null && source.isMouseOver(x, y) && reopen();
    }

    public boolean reopenFromKey(int key) {
        var source = closing() && activationKey(key) && returnSource != null ? returnSource.get() : null;
        return source != null && source.visible && source.active && reopen();
    }

    private static boolean activationKey(int key) {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE;
    }

    /** 从正在收起的轮廓反向展开，并撤销原来的关闭回调。 */
    public boolean reopen() {
        return reopen(PageTransition.nowMs());
    }

    boolean reopen(long now) {
        if (!closing()) return false;
        opening.transitionTo(1f, now, InterruptibleAnimation.Policy.REVERSE, PANEL_PRIORITY);
        closeRevision = 0L;
        onClosed = null;
        return true;
    }

    /** 沿当前轮廓收回到入口的实际边界，完成后只调用一次返回动作。减少动效时立即完成。 */
    public void close(UiPreferences preferences, Runnable onClosed) {
        close(PageTransition.nowMs(), preferences, onClosed);
    }

    void close(long now, UiPreferences preferences, Runnable onClosed) {
        if (closing() || closed) return;
        this.onClosed = Objects.requireNonNull(onClosed);
        float progress = sampleProgress(now, preferences);
        if (closingSeed == null) {
            closingProgress = progress;
            closingSeed = FluidMorph.seed(originX.sample(now), originY);
        }
        closeRevision = opening.transitionTo(0f, now, InterruptibleAnimation.Policy.REVERSE, PANEL_PRIORITY);
        if (preferences.reducedMotion()) finish();
    }

    boolean commitClose() {
        if (!opening.takeCompletion(closeRevision)) return false;
        closeRevision = 0L;
        closed = true;
        Runnable action = onClosed;
        onClosed = null;
        action.run();
        return true;
    }

    float sampleProgress(long now, UiPreferences preferences) {
        if (closed) return 0f;
        if (pending) {
            pending = false;
            opening.snapTo(0f);
            opening.setOpen(true, now);
        }
        float progress = GuiMotion.clamp01(opening.sample(now, preferences));
        if (closing()) {
            // 反向速度可能把弹簧带过可见端点，此时可在这一帧完成交接。
            if (progress == 0f) opening.finish();
        } else if (progress == 1f) {
            finish();
        }
        return progress;
    }

    FluidMorph frame(Bounds target, float progress, long now) {
        var panel = new GuiRect(target.x(), target.y(), target.width(), target.height());
        var seed = closingSeed;
        if (seed != null && progress <= closingProgress) {
            var from = FluidMorph.between(seed, panel, LiquidGlassTheme.POPOVER_RADIUS, closingProgress);
            var source = returnSource == null ? null : returnSource.get();
            var destination = source == null ? seed : new GuiRect(source.getX(), source.getY(),
                    source.getWidth(), source.getHeight());
            float remaining = closingProgress > 0f ? progress / closingProgress : 0f;
            return FluidMorph.between(destination, from.rect(), from.radius(), remaining);
        }
        if (seed == null) seed = FluidMorph.seed(originX.sample(now), originY);
        return FluidMorph.between(seed, panel, LiquidGlassTheme.POPOVER_RADIUS, progress);
    }

    /** 正在变形时绘制底板与裁切的正文，调用方应暂停指针输入。返回 false 时照常绘制。 */
    public boolean render(GuiGraphics graphics, Bounds target, UiPreferences preferences, Runnable content) {
        return render(graphics, target, preferences, content, false, null, null);
    }

    /** 过渡帧用同一次采样绘制背景；返回 false 时由调用方绘制完整画面。 */
    public boolean render(GuiGraphics graphics, Bounds target, UiPreferences preferences,
                          Consumer<Float> backdrop, Runnable content) {
        return render(graphics, target, preferences, content, false, backdrop, null);
    }

    /** 用暗色主题的实色底板揭示预览区，适合位于玻璃面板内部的内容。 */
    public boolean renderInset(GuiGraphics graphics, Bounds target, UiPreferences preferences, Runnable content) {
        return render(graphics, target, preferences, content, true, null, null);
    }

    boolean renderWithHandoff(GuiGraphics graphics, Bounds target, UiPreferences preferences,
                              Runnable content, BooleanSupplier readyToClose) {
        return render(graphics, target, preferences, content, false, null, readyToClose);
    }

    private boolean render(GuiGraphics graphics, Bounds target, UiPreferences preferences, Runnable content,
                           boolean insetPanel, Consumer<Float> backdrop, BooleanSupplier readyToClose) {
        if (closed) return true;
        if (!active()) return false;
        visibleContent = null;
        long now = PageTransition.nowMs();
        float progress = sampleProgress(now, preferences);
        if (target.width() < FluidMorph.SEED_WIDTH || target.height() < FluidMorph.SEED_HEIGHT) {
            finish();
            return closed;
        }
        if (progress == 1f && !closing()) return false;
        if (backdrop != null) backdrop.accept(progress);
        if ((readyToClose == null || readyToClose.getAsBoolean()) && commitClose()) return true;
        if (progress == 0f) return true;

        float coverage = LiquidGlassTheme.revealCoverage(progress);
        float opacity = GuiRenderUtil.easeOut((progress - CONTENT_FADE_START) / (CONTENT_FADE_END - CONTENT_FADE_START));
        var blocks = opacity < 1f ? skeletonBlocks(target) : List.<UiSkeleton.Block>of();
        if (!closing() || backdrop != null) {
            // 正文出现前淡出全尺寸预览，剩余骨架随正文一起裁切。
            UiSkeleton.draw(graphics, target, blocks, now, preferences, previewOpacity(progress));
        }
        var frame = frame(target, progress, now);
        var rect = frame.rect();
        int fill = insetPanel ? DarkModeTheme.BACKGROUND : LiquidGlassTheme.POPOVER_FILL | 0xFF000000;
        if (insetPanel) {
            int edge = preferences.highContrast() ? DarkModeTheme.TEXT : DarkModeTheme.BORDER;
            RoundedCorners.draw(graphics, rect.getX(), rect.getY(), rect.getW(), rect.getH(), frame.radius(), 1f,
                    GuiMotion.lerpColor(edge & 0xFFFFFF, edge, coverage),
                    GuiMotion.lerpColor(fill & 0xFFFFFF, fill, coverage));
        } else {
            LiquidGlassTheme.popover(graphics, frame, preferences, progress);
        }
        float inset = frame.radius() * 0.4f * (1f - progress);
        int x = Math.max(target.x(), Math.round(rect.getX() + inset));
        int y = Math.max(target.y(), Math.round(rect.getY() + inset));
        int right = Math.min(target.right(), Math.round(rect.getX() + rect.getW() - inset));
        int bottom = Math.min(target.bottom(), Math.round(rect.getY() + rect.getH() - inset));
        if (right <= x || bottom <= y) return true;

        var clip = new Bounds(x, y, right - x, bottom - y);
        if (opacity >= 0.9f) visibleContent = clip;
        graphics.enableScissor(x, y, right, bottom);
        try {
            if (opacity > 0f) content.run();
            if (opacity > 0f && opacity < 1f) {
                int veil = GuiMotion.lerpColor(fill, fill & 0xFFFFFF, opacity);
                graphics.fill(x, y, right, bottom, veil);
            }
            if (opacity < 1f) {
                UiSkeleton.draw(graphics, clip, blocks, now, preferences, coverage * (1f - opacity));
            }
        } finally {
            graphics.disableScissor();
        }
        return true;
    }

    static float previewOpacity(float progress) {
        float remaining = 1f - GuiMotion.clamp01(progress / CONTENT_FADE_START);
        return LiquidGlassTheme.revealCoverage(progress) * remaining * 0.42f;
    }
}
