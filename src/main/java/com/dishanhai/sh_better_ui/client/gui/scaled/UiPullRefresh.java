package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.ShBetterUi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.IntConsumer;

/** 山海：列表独享的下拉刷新控制器。事件、绘制和回执在客户端线程使用同一单调时钟。 */
public final class UiPullRefresh {
    private static final String TEXT_KEY = "sh_better_ui.pull_refresh.";
    private static final int PRIMARY_BUTTON = 0;
    private static final int SPINNER_SIZE = 12;
    private static final long REFRESH_ACTION = 0;
    private static final AtomicInteger REQUEST_IDS = new AtomicInteger();
    private static final Function<Frame, GraphicIcon> DEFAULT_FRAME_ICON = Frame::icon;

    /** 距离使用逻辑 GUI 单位，触发距离从按下位置开始计算。 */
    public record Config(float deadZone, float triggerDistance, float maxReveal, long timeoutMs) {
        public static final Config DEFAULT = new Config(6, 48, 32, ButtonFeedback.PENDING_TIMEOUT_MS);

        public Config {
            if (!Float.isFinite(deadZone) || deadZone < 0 || !Float.isFinite(triggerDistance)
                    || triggerDistance <= deadZone || !Float.isFinite(maxReveal) || maxReveal <= 0 || timeoutMs <= 0) {
                throw new IllegalArgumentException("Invalid pull refresh configuration");
            }
        }
    }

    public enum Phase { IDLE, PULLING, ARMED }

    public record Frame(Phase phase, float progress, float reveal, ButtonFeedback.Status status) {
        public Component message() {
            return switch (phase) {
                case PULLING -> Component.translatable(TEXT_KEY + "pulling");
                case ARMED -> Component.translatable(TEXT_KEY + "armed");
                case IDLE -> Component.translatable(status == ButtonFeedback.Status.READY
                        ? TEXT_KEY + "hint" : status.translationKey());
            };
        }

        public GraphicIcon icon() {
            return switch (phase) {
                case PULLING -> GraphicIcon.PULL_DOWN;
                case ARMED -> GraphicIcon.REFRESH;
                case IDLE -> switch (status) {
                    case READY -> GraphicIcon.PULL_DOWN;
                    case PENDING -> GraphicIcon.REFRESH;
                    case SUCCEEDED -> GraphicIcon.SUCCESS;
                    case PARTIAL, TIMED_OUT -> GraphicIcon.WARNING;
                    case FAILED -> GraphicIcon.ERROR;
                    case CANCELLED -> GraphicIcon.CLOSE;
                };
            };
        }
    }

    private enum Direction { UNDECIDED, DOWN, REJECTED }

    private final Config config;
    private final IntConsumer onRefresh;
    private final Function<Frame, GraphicIcon> frameIcon;
    private final PointerDrag pointer = new PointerDrag();
    private final InterruptibleAnimation reveal = new InterruptibleAnimation(Spring.HOVER);
    private ButtonFeedback feedback = new ButtonFeedback();
    private Direction direction = Direction.UNDECIDED;
    private boolean releaseAllowed = true;
    private float progress;

    public UiPullRefresh(IntConsumer onRefresh) {
        this(Config.DEFAULT, onRefresh);
    }

    public UiPullRefresh(Config config, IntConsumer onRefresh) {
        this(config, onRefresh, DEFAULT_FRAME_ICON);
    }

    /** 自定义各状态的图标，包括等待时的显示。返回 null 时只显示文字。 */
    public UiPullRefresh(Config config, IntConsumer onRefresh, Function<Frame, GraphicIcon> frameIcon) {
        this.config = Objects.requireNonNull(config);
        this.onRefresh = Objects.requireNonNull(onRefresh);
        this.frameIcon = Objects.requireNonNull(frameIcon);
    }

    /** 命中可见把手且子控件未处理事件后调用。{@code atTop} 使用列表当前的实际位置。 */
    public boolean begin(int button, double x, double y, boolean atTop, long nowMs) {
        if (button != PRIMARY_BUTTON || !atTop || status(nowMs) == ButtonFeedback.Status.PENDING
                || !pointer.begin(button, x, y)) return false;
        reveal.snapTo(0);
        return true;
    }

    /** 已捕获的主键在把手外仍会跟随指针。 */
    public boolean drag(int button, double x, double y, long nowMs) {
        return drag(button, x, y, true, nowMs);
    }

    /** 有放开区域的接入方同时更新落点预览。最终放开仍独立验证。 */
    public boolean drag(int button, double x, double y, boolean releaseAllowed, long nowMs) {
        if (button != PRIMARY_BUTTON || !pointer.active()) return false;
        if (pointer.move(button, x, y) != null) {
            this.releaseAllowed = releaseAllowed;
            updatePull();
        }
        return true;
    }

    /** 返回是否消耗本次捕获。只有最终位置有效且达到门槛才请求刷新。 */
    public boolean release(int button, double x, double y, boolean insideViewportAndAtTop, long nowMs) {
        if (button != PRIMARY_BUTTON || !pointer.active()) return false;
        boolean valid = pointer.release(button, x, y) != null;
        if (valid) updatePull();
        boolean armed = valid && insideViewportAndAtTop && isArmed();
        cancelGesture(nowMs);
        if (armed) requestRefresh(nowMs);
        return true;
    }

    public boolean isDragging() {
        return pointer.active();
    }

    /** 取消指针捕获。已经发出的刷新仍然等待接入方回执。 */
    public void cancelGesture(long nowMs) {
        pointer.cancel();
        direction = Direction.UNDECIDED;
        releaseAllowed = true;
        progress = 0;
        reveal.animateTo(0, nowMs);
    }

    /** 按钮和手势共用此入口。先登记请求，再调用可能立即回执的接入方。 */
    public boolean requestRefresh(long nowMs) {
        if (pointer.active() || !feedback.tryBegin(REFRESH_ACTION, nowMs)) return false;
        int requestId = REQUEST_IDS.incrementAndGet();
        feedback.pending(REFRESH_ACTION, requestId, nowMs, config.timeoutMs());
        try {
            onRefresh.accept(requestId);
        } catch (RuntimeException failure) {
            complete(requestId, ButtonFeedback.Tone.FAILED, nowMs);
            ShBetterUi.LOGGER.warn("Pull refresh callback failed", failure);
        }
        return true;
    }

    /** 只接受仍在等待的请求。返回 true 后，接入方可在同一客户端任务中应用对应结果。 */
    public boolean complete(int requestId, ButtonFeedback.Tone tone, long nowMs) {
        return feedback.resolve(requestId, Objects.requireNonNull(tone), nowMs);
    }

    /** 关闭界面或切换列表来源时调用。让旧回执失效并立即清除手势。 */
    public void reset(long nowMs) {
        cancelGesture(nowMs);
        reveal.snapTo(0);
        feedback = new ButtonFeedback();
    }

    public Frame sample(long nowMs, UiPreferences preferences) {
        Phase phase = isArmed() && releaseAllowed ? Phase.ARMED
                : pointer.active() && direction != Direction.REJECTED ? Phase.PULLING : Phase.IDLE;
        float offset = reveal.sample(nowMs, preferences);
        return new Frame(phase, progress, preferences.reducedMotion() ? 0 : offset, status(nowMs));
    }

    /** 注册到原生 Screen 的焦点顺序，即可通过键盘或点击调用同一个刷新入口。 */
    public UiButton createButton(Rect2i bounds, UiPreferences preferences) {
        UiButton button = new UiButton(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(),
                Component.translatable(TEXT_KEY + "label"), ignored -> requestRefresh(PageTransition.nowMs()),
                preferences, false);
        button.setLeadingIcon(GraphicIcon.REFRESH);
        button.setPreview(Component.translatable(TEXT_KEY + "tooltip"));
        return button;
    }

    /** 在调用方保留的可见区域内画把手、释放提示和请求状态。 */
    public void render(GuiGraphics graphics, Font font, Rect2i bounds, long nowMs, UiPreferences preferences) {
        render(graphics, font, bounds, bounds, nowMs, preferences);
    }

    /** {@code bounds} 使用当前 pose 的局部坐标，{@code clipBounds} 使用绝对 GUI 坐标。 */
    public void render(GuiGraphics graphics, Font font, Rect2i bounds, Rect2i clipBounds, long nowMs,
                       UiPreferences preferences) {
        int x = bounds.getX();
        int y = bounds.getY();
        int width = bounds.getWidth();
        int height = bounds.getHeight();
        if (width < 3 || height < 3 || clipBounds.getWidth() <= 0 || clipBounds.getHeight() <= 0) return;
        Frame frame = sample(nowMs, preferences);
        boolean highContrast = preferences.highContrast();
        int textColor = highContrast ? DarkModeTheme.TEXT : DarkModeTheme.TEXT_SECONDARY;
        int border = highContrast ? DarkModeTheme.TEXT : DarkModeTheme.BORDER_HOVER;
        int highlight = highContrast ? DarkModeTheme.TEXT : DarkModeTheme.ACCENT;
        boolean armed = frame.phase() == Phase.ARMED;
        graphics.enableScissor(clipBounds.getX(), clipBounds.getY(), clipBounds.getX() + clipBounds.getWidth(),
                clipBounds.getY() + clipBounds.getHeight());
        try {
            DarkModeTheme.rounded(graphics, x, y, width, height, 4, 1,
                    armed ? highlight : border, highContrast ? DarkModeTheme.BACKGROUND : DarkModeTheme.SURFACE);

            int textX = x + 4;
            int lineHeight = font.lineHeight;
            if (frame.status() == ButtonFeedback.Status.PENDING && frameIcon == DEFAULT_FRAME_ICON) {
                int size = Math.min(SPINNER_SIZE, Math.min(width, height) - 2);
                boolean showLabel = width >= size + 12 + 24 && height >= lineHeight + 2;
                int iconX = showLabel ? textX : x + (width - size) / 2;
                UiLoading.spinner(graphics, new Bounds(iconX, y + (height - size) / 2, size, size),
                        nowMs, preferences);
                textX = showLabel ? textX + size + 4 : x + width;
            } else if (width >= GraphicIcon.PIXELS + 12 + 24 && height >= GraphicIcon.PIXELS + 2) {
                GraphicIcon icon = frameIcon.apply(frame);
                if (icon != null) {
                    int iconY = y + contentTop(height, GraphicIcon.PIXELS, frame.reveal());
                    icon.draw(graphics, textX, iconY, 1, armed ? highlight : textColor, preferences);
                    textX += GraphicIcon.PIXELS + 4;
                }
            }
            int textWidth = x + width - textX - 4;
            if (textWidth > 0 && height >= lineHeight + 2) {
                int textY = y + contentTop(height, lineHeight, frame.reveal());
                var line = Language.getInstance().getVisualOrder(font.substrByWidth(frame.message(), textWidth));
                graphics.drawString(font, line, textX, textY, textColor, false);
            }

            int barWidth = width - 2;
            int barY = y + height - 2;
            if (frame.progress() > 0) {
                graphics.fill(x + 1, barY, x + 1 + Math.round(barWidth * frame.progress()), barY + 1, highlight);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private ButtonFeedback.Status status(long nowMs) {
        return feedback.status(REFRESH_ACTION, nowMs);
    }

    private boolean isArmed() {
        return direction == Direction.DOWN && pointer.offsetY() >= config.triggerDistance();
    }

    private void updatePull() {
        double x = Math.abs(pointer.offsetX());
        double y = pointer.offsetY();
        if (!Double.isFinite(x) || !Double.isFinite(y)) direction = Direction.REJECTED;
        if (direction == Direction.UNDECIDED && Math.max(x, Math.abs(y)) > config.deadZone()) {
            direction = y > x ? Direction.DOWN : Direction.REJECTED;
        }
        progress = direction == Direction.DOWN ? (float) Math.clamp(
                (y - config.deadZone()) / (config.triggerDistance() - config.deadZone()), 0, 1) : 0;
        reveal.snapTo(progress * config.maxReveal());
    }

    private static int contentTop(int height, int contentHeight, float reveal) {
        int centered = (height - contentHeight) / 2;
        return centered + Math.round(Math.min(height - contentHeight - 1 - centered, reveal));
    }

}
