package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.UiFrameScheduler;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** 山海：可拖曳关闭的非模态通知。由客户端 Screen 注册为原生可渲染控件。 */
public final class UiSwipeToast extends AbstractWidget {
    private static final int MARGIN = 8;
    private static final int PADDING = 8;
    private static final int GAP = 5;
    private static final int BUTTON_SIZE = 20;
    private static final int MIN_TEXT = 24;
    private static final int MAX_LINES = 6;
    private static final double FLICK_SPEED = 110;
    private static final double RESISTANCE = 24;
    private static final String KEY = "sh_better_ui.swipe_toast.";

    public enum Placement { BOTTOM_RIGHT, INLINE }
    public enum Fuse { TOP, BOTTOM, NONE }
    public enum CloseReason { TIMEOUT, SWIPE, ACTION, CLOSE, ESCAPE, PROGRAMMATIC }

    public record Action(Component label, Runnable run) {
        public Action {
            label = Objects.requireNonNull(label).copy();
            Objects.requireNonNull(run);
        }
    }

    public record Content(Component title, Component description, GraphicIcon icon, Action action) {
        public Content {
            title = Objects.requireNonNull(title).copy();
            description = Objects.requireNonNull(description).copy();
        }

        public Content(Component title, Component description) {
            this(title, description, null, null);
        }
    }

    /** 毫秒为零时持续显示；dismissible 仅控制滑动和 Escape。 */
    public record Options(long durationMs, boolean pauseOnHover, boolean closeButton, boolean dismissible) {
        public static final Options DEFAULT = new Options(4000, true, false, true);

        public Options {
            if (durationMs < 0) throw new IllegalArgumentException("durationMs");
        }
    }

    /** 宽度、圆角与滑动阈值使用 Minecraft GUI 单位，颜色使用 ARGB。 */
    public record Style(int width, float radius, int background, int color, int fuseColor, Fuse fuse,
                        int slideMs, float settleBounce, double swipeDistance) {
        public static final Style DEFAULT = new Style(356, 12f, 0xFF27272A, 0xFFF5F5F5, 0xFFF5A524,
                Fuse.BOTTOM, 400, 0.2f, 40);

        public Style {
            Objects.requireNonNull(fuse);
            if (width < 1 || !Float.isFinite(radius) || radius < 0 || slideMs < 0
                    || !Float.isFinite(settleBounce) || settleBounce < 0 || settleBounce > 1
                    || !Double.isFinite(swipeDistance) || swipeDistance <= 0) throw new IllegalArgumentException();
        }
    }

    private enum Target { CARD, ACTION, CLOSE }

    private final Screen owner;
    private final Placement placement;
    private final Content content;
    private final Options options;
    private final Style style;
    private final Consumer<CloseReason> onClose;
    private final SwipeToastTimeline timeline;
    private final SwipeGesture swipe = new SwipeGesture();
    private final Spring settle;
    private UiPreferences preferences;
    private Layout layout;
    private Bounds card;
    private Bounds slot;
    private Target pressed;
    private Target selected;
    private float grabOffset;
    private boolean announced;
    private int occupiedHeight;
    private long openRevision;

    public UiSwipeToast(Screen owner, Placement placement, Content content, Options options, Style style,
                        UiPreferences preferences, Consumer<CloseReason> onClose) {
        super(0, 0, 0, 0, Objects.requireNonNull(content).title());
        this.owner = Objects.requireNonNull(owner);
        this.placement = Objects.requireNonNull(placement);
        this.content = content;
        this.options = Objects.requireNonNull(options);
        this.style = Objects.requireNonNull(style);
        this.preferences = Objects.requireNonNull(preferences);
        this.onClose = Objects.requireNonNull(onClose);
        timeline = new SwipeToastTimeline(scaledDuration(options.durationMs()), style.slideMs(),
                placement == Placement.INLINE, PageTransition.nowMs());
        settle = new Spring(new Spring.Preset(0.34, 1 - style.settleBounce() * 0.75, 0.05f, 1f));
        selected = firstTarget();
        active = interactive();
    }

    /** 设置行内槽位。通知宽度不会超过可用宽度。 */
    public void setInlineBounds(int x, int y, int availableWidth) {
        if (placement != Placement.INLINE) throw new IllegalStateException();
        setX(x);
        setY(y);
        setWidth(Math.max(0, availableWidth));
    }

    /** 当前帧占用的行内高度。关闭回调在该高度归零后触发。 */
    public int occupiedHeight() { return occupiedHeight; }

    public boolean isOpen() { return timeline.isOpen(); }

    public void setOpen(boolean open) {
        if (open == isOpen()) return;
        long now = PageTransition.nowMs();
        if (open) {
            if (visible) settleBack(0f, now);
            else settle.snap(0f);
            visible = true;
            active = interactive();
            announced = false;
            timeline.open(now);
            openRevision++;
        } else {
            close(CloseReason.PROGRAMMATIC, now);
        }
    }

    /** 按原版通知时长倍率重新开始计时。传零可保留至手动关闭。 */
    public void setDurationMs(long durationMs) {
        timeline.setDuration(scaledDuration(durationMs), PageTransition.nowMs());
    }

    public void setPreferences(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
        if (preferences.reducedMotion() && pressed == null && isOpen()) settle.snap(0f);
    }

    private static long scaledDuration(long durationMs) {
        if (durationMs < 0) throw new IllegalArgumentException("durationMs");
        return durationMs == 0 ? 0 : Math.max(1,
                Math.round(durationMs * Minecraft.getInstance().options.notificationDisplayTime().get()));
    }

    /** 平台事件入口：浮层只接收落在可见区域内的按下，并让模态框先处理。 */
    public static boolean handleOverlayPress(Screen screen, double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        for (var child : screen.children().reversed()) {
            if (child instanceof UiSwipeToast toast && toast.placement == Placement.BOTTOM_RIGHT
                    && toast.mouseClicked(x, y, button)) {
                screen.setFocused(toast);
                return true;
            }
        }
        return false;
    }

    /** 平台事件入口：在 Screen 把 Escape 当作退出键前，先处理有焦点的通知。 */
    public static boolean handleEscape(Screen screen) {
        return screen.getFocused() instanceof UiSwipeToast toast && toast.options.dismissible()
                && toast.canInteract() && toast.close(CloseReason.ESCAPE, PageTransition.nowMs());
    }

    @Override
    public boolean isMouseOver(double x, double y) {
        return active && visible && hit(x, y);
    }

    @Override
    public ScreenRectangle getRectangle() {
        if (card == null) return ScreenRectangle.empty();
        var rectangle = new ScreenRectangle(card.x(), card.y(), card.width(), card.height());
        if (slot == null) return rectangle;
        var clipped = rectangle.intersection(new ScreenRectangle(slot.x(), slot.y(), slot.width(), slot.height()));
        return clipped == null ? ScreenRectangle.empty() : clipped;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT || !canInteract() || !hit(x, y) || pressed != null) return false;
        Target target = targetAt(x, y);
        if (target == Target.CARD && !options.dismissible()) return false;
        if (!UiPointerCapture.start(owner, button, capture)) return false;
        long now = PageTransition.nowMs();
        pressed = target;
        if (target != Target.CARD) selected = target;
        else {
            settle.update(now);
            grabOffset = settle.value();
            swipe.begin(button, x, y, now);
        }
        return true;
    }

    private final UiPointerCapture.Handler capture = new UiPointerCapture.Handler() {
        @Override
        public void drag(double x, double y) {
            if (!canInteract()) UiPointerCapture.cancel(owner);
            else if (pressed == Target.CARD) swipe.move(GLFW.GLFW_MOUSE_BUTTON_LEFT, x, y, PageTransition.nowMs());
        }

        @Override
        public void release(double x, double y) {
            Target target = pressed;
            pressed = null;
            clearScreenDrag();
            long now = PageTransition.nowMs();
            if (target == Target.CARD) {
                var sample = swipe.release(GLFW.GLFW_MOUSE_BUTTON_LEFT, x, y, now);
                if (sample != null) settle.snap(dragOffset(sample));
                if (canInteract() && sample != null && sample.dismissesDown(style.swipeDistance(), FLICK_SPEED)) {
                    close(CloseReason.SWIPE, now);
                } else {
                    settleBack(sample == null ? 0f : (float) sample.velocityY(), now);
                }
            } else if (target != null && canInteract() && hit(x, y) && targetAt(x, y) == target) {
                activate(target, now);
            }
        }

        @Override
        public void cancel() {
            long now = PageTransition.nowMs();
            if (swipe.active()) settle.snap(dragOffset(swipe.sample(now)));
            swipe.cancel();
            pressed = null;
            clearScreenDrag();
            settleBack(0f, now);
        }
    };

    private void clearScreenDrag() {
        if (owner.getFocused() == this && placement == Placement.INLINE) owner.setDragging(false);
    }

    private void settleBack(float velocity, long now) {
        if (preferences.reducedMotion()) settle.snap(0f);
        else settle.setTarget(0f, Math.clamp(velocity, -1200f, 1200f), now);
    }

    private float dragOffset(SwipeGesture.Sample sample) {
        double offset = sample.offsetY();
        return grabOffset + (float) (offset >= 0 ? offset : offset * RESISTANCE / (RESISTANCE - offset));
    }

    private boolean canInteract() {
        Minecraft client = Minecraft.getInstance();
        if (!active || !visible || !isOpen() || client.screen != owner || !client.isWindowActive()
                || client.getOverlay() != null) return false;
        UiOverlayHost overlay = UiOverlayHost.current(owner);
        return overlay == null || !overlay.isModalOpen();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!isFocused() || !canInteract() || pressed != null) return false;
        if (key == GLFW.GLFW_KEY_ESCAPE && options.dismissible()) return close(CloseReason.ESCAPE, PageTransition.nowMs());
        if (hasBothButtons() && (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_RIGHT)) {
            selected = key == GLFW.GLFW_KEY_LEFT ? Target.ACTION : Target.CLOSE;
            Minecraft.getInstance().getNarrator().say(selected == Target.ACTION ? content.action().label() : closeLabel());
            return true;
        }
        if (selected != Target.CARD && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER
                || key == GLFW.GLFW_KEY_SPACE)) {
            activate(selected, PageTransition.nowMs());
            return true;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, narration());
        if (selected != Target.CARD) {
            output.add(NarratedElementType.USAGE, Component.translatable(KEY + (hasBothButtons() ? "buttons_hint" : "activate_hint"),
                    selected == Target.ACTION ? content.action().label() : closeLabel()));
        } else if (options.dismissible()) {
            output.add(NarratedElementType.USAGE, Component.translatable(KEY + "drag_hint"));
        }
    }

    private Component narration() {
        return CommonComponents.joinForNarration(content.title(), content.description());
    }

    private static Component closeLabel() { return Component.translatable(KEY + "close"); }

    private Target firstTarget() {
        return content.action() != null ? Target.ACTION : options.closeButton() ? Target.CLOSE : Target.CARD;
    }

    private boolean hasBothButtons() { return content.action() != null && options.closeButton(); }

    private boolean interactive() { return options.dismissible() || firstTarget() != Target.CARD; }

    private void activate(Target target, long now) {
        if (target == Target.ACTION) {
            if (close(CloseReason.ACTION, now)) content.action().run().run();
        } else if (target == Target.CLOSE) {
            close(CloseReason.CLOSE, now);
        }
    }

    private boolean close(CloseReason reason, long now) {
        if (!timeline.close(reason, now)) return false;
        deactivate();
        return true;
    }

    private void deactivate() {
        active = false;
        if (pressed != null) UiPointerCapture.cancel(owner);
        if (owner.getFocused() == this) owner.setFocused(null);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var client = Minecraft.getInstance();
        if (!UiFrameScheduler.canCommitFor(owner)) {
            if (card != null && layout != null) {
                drawFrame(graphics, client.font, layout, Integer.MIN_VALUE, Integer.MIN_VALUE, false);
            }
            return;
        }

        long now = PageTransition.nowMs();
        int available = placement == Placement.INLINE ? getWidth() : graphics.guiWidth() - 2 * MARGIN;
        int width = Math.min(style.width(), available);
        int maxHeight = placement == Placement.INLINE ? graphics.guiHeight() - Math.max(0, getY())
                : graphics.guiHeight() - 2 * MARGIN;
        var nextLayout = layout(client.font, width, maxHeight);
        if (nextLayout == null) {
            card = slot = null;
            deactivate();
        } else {
            timeline.layoutReady(now);
            active = isOpen() && interactive();
        }
        if (pressed != null && !canInteract()) UiPointerCapture.cancel(owner);
        boolean over = options.pauseOnHover() && hit(mouseX, mouseY);
        var reason = timeline.advance(now, nextLayout == null || !client.isWindowActive() || client.getOverlay() != null
                || over || isFocused() || pressed != null, preferences.reducedMotion());
        if (active && !isOpen()) deactivate();
        if (reason != null) {
            occupiedHeight = 0;
            visible = false;
            card = slot = null;
            long revision = openRevision;
            UiFrameScheduler.afterFrame(owner, () -> {
                if (openRevision == revision) onClose.accept(reason);
            });
            return;
        }
        if (nextLayout == null) { occupiedHeight = 0; return; }
        if (!announced && isOpen() && timeline.reveal() > 0f) {
            announced = true;
            client.getNarrator().say(narration());
        }
        settle.update(now);
        var sample = swipe.sample(now);
        float displacement = swipe.active() ? dragOffset(sample) : settle.value();
        float reveal = timeline.reveal();
        boolean inline = placement == Placement.INLINE;
        occupiedHeight = inline ? Math.round(nextLayout.height() * timeline.slot()) : 0;
        int x = inline ? getX() : graphics.guiWidth() - MARGIN - width;
        int y = inline ? getY() : graphics.guiHeight() - MARGIN - nextLayout.height();
        boolean clipSlot = inline && (occupiedHeight < nextLayout.height()
                || (displacement == 0f && reveal < timeline.slot()));
        slot = clipSlot ? new Bounds(x, y, width, occupiedHeight) : null;
        int slide = preferences.reducedMotion() ? 0 : inline ? occupiedHeight - nextLayout.height()
                + Math.round(Math.max(0f, timeline.slot() - reveal) * nextLayout.height())
                : Math.round((1f - reveal) * (nextLayout.height() + MARGIN));
        card = new Bounds(x, y + slide + Math.round(displacement), width, nextLayout.height());
        drawFrame(graphics, client.font, nextLayout, mouseX, mouseY,
                swipe.active() && sample.dismissesDown(style.swipeDistance(), FLICK_SPEED));
    }

    private void drawFrame(GuiGraphics graphics, Font font, Layout layout, int mouseX, int mouseY, boolean armed) {
        int alpha = Math.round(255f * timeline.reveal());
        boolean inline = placement == Placement.INLINE;
        if (alpha < 4 || (inline && occupiedHeight == 0)) return;
        if (slot != null) graphics.enableScissor(slot.x(), slot.y(), slot.right(), slot.bottom());
        graphics.pose().pushPose();
        try {
            if (!inline) graphics.pose().translate(0f, 0f, 400f);
            draw(graphics, font, layout, alpha, mouseX, mouseY, armed);
        } finally {
            graphics.pose().popPose();
            if (slot != null) graphics.disableScissor();
        }
    }

    private boolean hit(double x, double y) {
        return Math.round(timeline.reveal() * 255) >= 4 && card != null && card.contains(x, y)
                && (slot == null || slot.contains(x, y));
    }

    private Target targetAt(double x, double y) {
        if (options.closeButton() && button(Target.CLOSE).contains(x, y)) return Target.CLOSE;
        if (content.action() != null && button(Target.ACTION).contains(x, y)) return Target.ACTION;
        return Target.CARD;
    }

    private Bounds button(Target target) {
        int width = target == Target.CLOSE ? BUTTON_SIZE : layout.actionWidth();
        int right = card.right() - PADDING;
        if (target == Target.ACTION && options.closeButton()) right -= BUTTON_SIZE + GAP;
        return new Bounds(right - width, card.y() + (card.height() - BUTTON_SIZE) / 2, width, BUTTON_SIZE);
    }

    private Layout layout(Font font, int width, int maxHeight) {
        int close = options.closeButton() ? BUTTON_SIZE + GAP : 0;
        int action = content.action() != null ? Math.clamp(font.width(content.action().label()) + 12, 20, 96) : 0;
        int available = width - 2 * PADDING - close;
        if (action > 0) action = Math.min(action, available - MIN_TEXT - GAP);
        if (available < MIN_TEXT || (content.action() != null && action < 16)) return null;
        int body = available - (action > 0 ? action + GAP : 0);
        boolean icon = content.icon() != null && body >= MIN_TEXT + GraphicIcon.PIXELS + GAP;
        int textWidth = body - (icon ? GraphicIcon.PIXELS + GAP : 0);
        int limit = Math.min(MAX_LINES, (maxHeight - 2 * PADDING) / font.lineHeight);
        int minHeight = firstTarget() == Target.CARD ? (icon ? GraphicIcon.PIXELS : 0) : BUTTON_SIZE;
        if (limit < 1 || maxHeight < minHeight + 2 * PADDING) return null;
        Language language = Language.getInstance();
        if (layout != null && layout.font() == font && layout.language() == language
                && layout.width() == width && layout.lineLimit() == limit) return layout;
        var title = font.split(content.title(), textWidth);
        var description = content.description().getString().isEmpty() ? List.<FormattedCharSequence>of()
                : font.split(content.description(), textWidth);
        int titleCount = Math.min(title.size(), description.isEmpty() ? limit : Math.max(1, limit - 1));
        int descriptionCount = Math.min(description.size(), limit - titleCount);
        int height = 2 * PADDING + Math.max(minHeight, (titleCount + descriptionCount) * font.lineHeight);
        layout = new Layout(font, language, width, limit, height, icon, action, title.subList(0, titleCount),
                description.subList(0, descriptionCount));
        return layout;
    }

    private void draw(GuiGraphics g, Font font, Layout layout, int alpha, int mouseX, int mouseY, boolean armed) {
        boolean highContrast = preferences.highContrast();
        int ink = highContrast ? DarkModeTheme.textColor() : style == Style.DEFAULT ? UiTheme.current().text(style.color()) : style.color();
        int surface = highContrast ? DarkModeTheme.backgroundColor() : style == Style.DEFAULT ? UiTheme.current().surface(style.background()) : style.background();
        int fuse = highContrast ? ink : style.fuseColor();
        int edge = armed ? fuse : isFocused() || highContrast ? ink : DarkModeTheme.borderColor();
        if (pressed == Target.CARD && !highContrast) surface = GuiMotion.lerpColor(surface, ink, 0.10f);
        DarkModeTheme.rounded(g, card.x(), card.y(), card.width(), card.height(), style.radius(),
                armed || isFocused() ? 2 : 1, tint(edge, alpha), tint(surface, alpha));
        int x = card.x() + PADDING;
        if (layout.icon()) {
            content.icon().draw(g, x, card.y() + (card.height() - GraphicIcon.PIXELS) / 2, 1, tint(ink, alpha), preferences);
            x += GraphicIcon.PIXELS + GAP;
        }
        int y = card.y() + (card.height() - (layout.title().size() + layout.description().size()) * font.lineHeight) / 2;
        for (var line : layout.title()) {
            drawText(g, font, line, x, y, tint(ink, alpha));
            y += font.lineHeight;
        }
        int description = highContrast ? ink : GuiMotion.lerpColor(surface, ink, 0.72f);
        for (var line : layout.description()) {
            drawText(g, font, line, x, y, tint(description, alpha));
            y += font.lineHeight;
        }
        if (content.action() != null) drawButton(g, font, Target.ACTION, ink, surface, alpha, mouseX, mouseY);
        if (options.closeButton()) drawButton(g, font, Target.CLOSE, ink, surface, alpha, mouseX, mouseY);
        if (style.fuse() != Fuse.NONE && timeline.fuse() > 0f) {
            int left = card.x() + PADDING;
            int right = left + Math.round((card.width() - 2 * PADDING) * timeline.fuse());
            int fuseY = style.fuse() == Fuse.TOP ? card.y() + 3 : card.bottom() - 5;
            if (right > left) g.fill(left, fuseY, right, fuseY + 2, tint(fuse, alpha));
        }
    }

    private void drawButton(GuiGraphics g, Font font, Target target, int ink, int surface, int alpha, int mouseX, int mouseY) {
        Bounds bounds = button(target);
        boolean action = target == Target.ACTION;
        boolean hovered = hit(mouseX, mouseY) && bounds.contains(mouseX, mouseY);
        boolean focused = isFocused() && selected == target;
        int fill = action ? ink : surface;
        int text = action ? surface : ink;
        if (!preferences.highContrast() && (hovered || pressed == target)) {
            fill = GuiMotion.lerpColor(fill, text, pressed == target ? 0.25f : 0.13f);
        }
        if (focused) {
            DarkModeTheme.rounded(g, bounds.x() - 2, bounds.y() - 2, bounds.width() + 4, bounds.height() + 4,
                    6, 1, tint(ink, alpha), 0);
        }
        DarkModeTheme.rounded(g, bounds.x(), bounds.y(), bounds.width(), bounds.height(), 4, 0, 0, tint(fill, alpha));
        if (action) {
            var line = Language.getInstance().getVisualOrder(font.substrByWidth(content.action().label(), bounds.width() - 8));
            drawText(g, font, line, bounds.x() + (bounds.width() - font.width(line)) / 2,
                    bounds.y() + (bounds.height() - font.lineHeight) / 2, tint(text, alpha));
        } else {
            GraphicIcon.CLOSE.draw(g, bounds.x() + (bounds.width() - GraphicIcon.PIXELS) / 2,
                    bounds.y() + (bounds.height() - GraphicIcon.PIXELS) / 2, 1, tint(text, alpha), preferences);
        }
    }

    private static int tint(int argb, int alpha) {
        return (argb & 0xFFFFFF) | (((argb >>> 24) * alpha / 255) << 24);
    }

    private static void drawText(GuiGraphics g, Font font, FormattedCharSequence line, int x, int y, int color) {
        // 原版字体会把 0–3 的 alpha 当成未指定透明度。
        if ((color >>> 24) >= 4) g.drawString(font, line, x, y, color, false);
    }

    private record Layout(Font font, Language language, int width, int lineLimit, int height, boolean icon,
                          int actionWidth, List<FormattedCharSequence> title, List<FormattedCharSequence> description) {}
}
