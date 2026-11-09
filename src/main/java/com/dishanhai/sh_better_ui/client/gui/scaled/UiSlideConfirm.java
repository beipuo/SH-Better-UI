package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.text.UiFormattedText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Objects;

/** 山海：在滑钮到达终点并放开时确认一次的原生控件。 */
public final class UiSlideConfirm extends AbstractWidget {
    private static final String TEXT_KEY = "sh_better_ui.slide_confirm.";
    private static final Component ARMED = Component.translatable(TEXT_KEY + "armed");
    private static final Component COMPLETED = Component.translatable(TEXT_KEY + "completed");
    private static final Component USAGE = Component.translatable(TEXT_KEY + "usage");
    private static final int INSET = 2;
    private static final int LABEL_GAP = 4;
    private static final int MIN_HEIGHT = GraphicIcon.PIXELS + 2 * INSET;
    private static final int MIN_TRAVEL = 64;
    private static final int PRIMARY_BUTTON = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    private static final int SLIDE_KEY = GLFW.GLFW_KEY_RIGHT;

    /** 局部扩展模式的确认阈值和键盘滑动时长。确认仍以放开时的落点为准。 */
    public record LocalConfig(double threshold, int keyTravelMs) {
        public static final LocalConfig DEFAULT = new LocalConfig(1d, 600);

        public LocalConfig {
            if (!(threshold > 0 && threshold <= 1) || keyTravelMs <= 0) {
                throw new IllegalArgumentException("Invalid slide configuration");
            }
        }
    }

    private final Screen owner;
    private final Component label;
    private final Runnable onConfirm;
    private final Gesture gesture = new Gesture();
    private final Tooltip hint;
    private final Tooltip completedTooltip = Tooltip.create(COMPLETED);
    private UiPreferences preferences;

    private final UiPointerCapture.Handler capture = new UiPointerCapture.Handler() {
        @Override
        public void drag(double x, double y) {
            if (canContinue()) gesture.drag(track(), x, y);
            else UiPointerCapture.cancel(owner);
        }

        @Override
        public void release(double x, double y) {
            clearScreenDrag();
            long now = PageTransition.nowMs();
            if (!canContinue()) gesture.cancel(now);
            else if (gesture.release(track(), x, y, now)) confirm();
        }

        @Override
        public void cancel() {
            clearScreenDrag();
            gesture.cancel(PageTransition.nowMs());
        }
    };

    public UiSlideConfirm(Screen owner, Bounds area, UiPreferences preferences, Runnable onConfirm) {
        this(owner, area, Component.translatable(TEXT_KEY + "label"), preferences, onConfirm);
    }

    /**
     * 将此控件作为 owner 的原生可渲染控件注册。高度至少为 20，宽度至少为高度加 64，均为 GUI 单位。
     * 确认状态先更新，再调用 onConfirm。再次使用前由宿主调用 reset()。
     */
    public UiSlideConfirm(Screen owner, Bounds area, Component label, UiPreferences preferences, Runnable onConfirm) {
        super(area.x(), area.y(), area.width(), area.height(), Objects.requireNonNull(label));
        if (!track().hasTravel()) throw new IllegalArgumentException();
        this.owner = Objects.requireNonNull(owner);
        this.label = label;
        this.preferences = Objects.requireNonNull(preferences);
        this.onConfirm = Objects.requireNonNull(onConfirm);
        hint = Tooltip.create(Component.translatable(TEXT_KEY + "tooltip", label));
        setTooltip(hint);
    }

    public boolean isCompleted() {
        return gesture.completed();
    }

    public void setPreferences(UiPreferences preferences) {
        this.preferences = Objects.requireNonNull(preferences);
    }

    /** 取消当前操作并立即回到起点。可以在同一控件上开始下一次确认。 */
    public void reset() {
        if (gesture.pointer.active()) UiPointerCapture.cancel(owner);
        gesture.reset();
        setMessage(label);
        if (getTooltip() == completedTooltip) setTooltip(hint);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != PRIMARY_BUTTON
                || !gesture.begin(track(), mouseX, mouseY, PageTransition.nowMs(), preferences)) return false;
        if (UiPointerCapture.start(owner, button, capture)) return true;
        gesture.cancel(PageTransition.nowMs());
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key != SLIDE_KEY || !canContinue() || !windowActive()) return false;
        return gesture.beginKey(PageTransition.nowMs());
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        if (key != SLIDE_KEY || !gesture.keyHeld) return false;
        long now = PageTransition.nowMs();
        if (!canContinue() || !windowActive()) gesture.cancel(now);
        else if (gesture.releaseKey(now)) confirm();
        return true;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (!focused && gesture.keyHeld) gesture.cancel(PageTransition.nowMs());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, createNarrationMessage());
        if (!isCompleted()) {
            output.add(NarratedElementType.USAGE, gesture.armed(PageTransition.nowMs()) ? ARMED : USAGE);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long now = PageTransition.nowMs();
        if (gesture.pointer.active() && !canContinue()) UiPointerCapture.cancel(owner);
        if (gesture.keyHeld && (!canContinue() || !windowActive())) gesture.cancel(now);

        Track track = track();
        if (!track.hasTravel()) return;
        float progress = gesture.sample(now, preferences);
        boolean armed = gesture.armed(now);
        boolean completed = isCompleted();
        float successProgress = completed ? gesture.sampleCompletion(now, preferences) : 0f;
        drawTrack(graphics, track, preferences, getMessage(), progress, successProgress, armed, completed,
                gesture.held(), isFocused(), active, true, mouseX, mouseY);
    }

    private static void drawTrack(GuiGraphics graphics, Track track, UiPreferences preferences, Component label,
                                  float progress, float successProgress, boolean armed, boolean completed,
                                  boolean held, boolean focused, boolean active, boolean drawSurface,
                                  int mouseX, int mouseY) {
        float thumbX = track.thumbX(progress);
        int diameter = track.diameter();
        boolean highContrast = preferences.highContrast();
        int surface = highContrast ? DarkModeTheme.backgroundColor() : DarkModeTheme.surfaceColor();
        int accent = highContrast ? DarkModeTheme.textColor() : LiquidGlassTheme.accentColor();
        int edge = focused || armed ? accent
                : highContrast ? DarkModeTheme.secondaryTextColor() : DarkModeTheme.hoverBorderColor();
        int fill = completed && !highContrast ? GuiMotion.lerpColor(accent, DarkModeTheme.SUCCESS, successProgress) : accent;
        int thumb = active ? fill : DarkModeTheme.mutedTextColor();
        if (active && !highContrast && !completed && !focused && !held
                && !track.containsThumb(mouseX, mouseY, progress)) {
            thumb = DarkModeTheme.secondaryTextColor();
        }

        if (drawSurface) {
            RoundedCorners.draw(graphics, track.x, track.y, track.width, track.height, track.height * 0.5f,
                    focused ? 2f : 1f, edge, surface);
        }
        if (progress > 0f) {
            int progressFill = GuiMotion.lerpColor(surface, fill, 0.28f);
            RoundedCorners.draw(graphics, track.x + INSET, track.y + INSET, thumbX - track.x - INSET + diameter,
                    diameter, diameter * 0.5f, 0f, 0, progressFill);
        }
        drawLabel(graphics, track, preferences, label, armed, completed, active);
        RoundedCorners.draw(graphics, thumbX, track.y + INSET, diameter, diameter, diameter * 0.5f,
                held ? 1f : 0f, DarkModeTheme.backgroundColor(), thumb);
        GraphicIcon icon = completed ? GraphicIcon.SUCCESS : GraphicIcon.NEXT;
        int iconX = Math.round(thumbX + (diameter - GraphicIcon.PIXELS) * 0.5f);
        int iconY = track.y + (track.height - GraphicIcon.PIXELS) / 2;
        float iconScale = completed ? GuiMotion.lerp(0.66f, 1f, successProgress) : 1f;
        GuiRenderUtil.withButtonMotion(graphics, iconX, iconY, GraphicIcon.PIXELS, GraphicIcon.PIXELS, iconScale, 0f,
                () -> icon.draw(graphics, iconX, iconY, 1, DarkModeTheme.backgroundColor(), preferences));
    }

    private static void drawLabel(GuiGraphics graphics, Track track, UiPreferences preferences,
                                  Component label, boolean armed, boolean completed, boolean active) {
        boolean atEnd = armed || completed;
        int left = track.x + INSET + LABEL_GAP + (atEnd ? 0 : track.diameter());
        int right = track.x + track.width - INSET - LABEL_GAP - (atEnd ? track.diameter() : 0);
        var font = Minecraft.getInstance().font;
        if (right <= left || font.lineHeight > track.height - 2 * INSET) return;
        var text = armed ? ARMED : label;
        var line = Language.getInstance().getVisualOrder(font.substrByWidth(text, right - left));
        UiFormattedText.draw(graphics, font, line, left + (right - left - font.width(line)) / 2,
                track.y + (track.height - font.lineHeight) / 2,
                active || completed ? DarkModeTheme.textColor() : DarkModeTheme.mutedTextColor(), false, preferences);
    }

    private Track track() {
        return new Track(getX(), getY(), getWidth(), getHeight());
    }

    private boolean canContinue() {
        return active && visible && isFocused() && owner.children().contains(this) && track().hasTravel();
    }

    private boolean windowActive() {
        Minecraft client = Minecraft.getInstance();
        return client.screen == owner && client.isWindowActive();
    }

    private void clearScreenDrag() {
        // 捕获在 Screen 之前处理放开，原生拖拽标志也要在回调前清除。
        owner.setDragging(false);
    }

    private void confirm() {
        setMessage(COMPLETED);
        if (getTooltip() == hint) setTooltip(completedTooltip);
        onConfirm.run();
    }

    static boolean fitsLocal(Bounds bounds) {
        return new Track(bounds.x(), bounds.y(), bounds.width(), bounds.height()).hasTravel();
    }

    static UiLocalModal.Content localContent(UiLocalModal.Session session, LocalConfig config, Component label) {
        return new LocalContent(session, config, label);
    }

    private static final class LocalContent implements UiLocalModal.Content {
        private final UiLocalModal.Session session;
        private final Track track;
        private final Gesture gesture;
        private final Component label;
        private boolean narrated;
        private boolean completionNarrated;

        private LocalContent(UiLocalModal.Session session, LocalConfig config, Component label) {
            this.session = session;
            var target = session.target();
            track = new Track(target.x(), target.y(), target.width(), target.height());
            gesture = new Gesture(config);
            this.label = label;
        }

        @Override
        public boolean animatesContents() { return true; }

        @Override
        public void render(GuiGraphics graphics, UiLocalModal.Frame frame, int mouseX, int mouseY, float partialTick) {
            long now = frame.nowMs();
            var prefs = frame.preferences();
            var state = frame.state();
            boolean completed = gesture.completed();
            if (!narrated && frame.focused() && state == UiExpansionController.State.ACTIVE) {
                narrated = true;
                Minecraft.getInstance().getNarrator().say(Component.translatable(TEXT_KEY + "tooltip", label));
            }
            if (completed && !completionNarrated && frame.focused()) {
                completionNarrated = true;
                Minecraft.getInstance().getNarrator().say(COMPLETED);
            }
            float success = prefs.reducedMotion() || state == UiExpansionController.State.COLLAPSING
                    ? 1f : frame.phaseProgress();
            float progress = switch (state) {
                case CANCELLING -> gesture.localReturnFrom * (1f - frame.phaseProgress());
                case CONFIRMING -> GuiMotion.lerp(gesture.localReleaseProgress, 1f, success);
                case COLLAPSING -> completed ? 1f : 0f;
                default -> gesture.sample(now, prefs);
            };
            drawTrack(graphics, track, prefs, completed ? COMPLETED : label, progress, success, gesture.armed(now),
                    completed, gesture.held(), frame.focused(), true, false, mouseX, mouseY);
        }

        @Override
        public boolean mousePressed(double x, double y, int button, long now) {
            return button == PRIMARY_BUTTON && gesture.begin(track, x, y, now, session.preferences());
        }

        @Override
        public void mouseDragged(double x, double y, int button, long now) {
            gesture.drag(track, x, y);
        }

        @Override
        public void mouseReleased(double x, double y, int button, long now) {
            settle(gesture.release(track, x, y, now));
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers, long now) {
            return key == SLIDE_KEY && gesture.beginKey(now);
        }

        @Override
        public void keyReleased(int key, int scanCode, int modifiers, long now) {
            settle(gesture.releaseKey(now));
        }

        @Override
        public void cancel(long now) {
            gesture.cancel(now);
        }

        private void settle(boolean confirmed) {
            if (confirmed) {
                session.confirm();
            } else {
                session.cancel();
            }
        }
    }

    record Track(int x, int y, int width, int height) {
        boolean hasTravel() {
            return height >= MIN_HEIGHT && travel() >= MIN_TRAVEL;
        }

        int travel() {
            return width - height;
        }

        int diameter() {
            return height - 2 * INSET;
        }

        float thumbX(float progress) {
            return x + INSET + travel() * progress;
        }

        boolean containsThumb(double mouseX, double mouseY, float progress) {
            double radius = diameter() * 0.5;
            double dx = mouseX - thumbX(progress) - radius;
            double dy = mouseY - y - height * 0.5;
            return dx * dx + dy * dy <= radius * radius;
        }
    }

    static final class Gesture {
        private static final long KEY_TRAVEL_MS = 600L;
        private static final double BEFORE_END = Math.nextDown(1d);
        private final PointerDrag pointer = new PointerDrag();
        private final InterruptibleAnimation returning = new InterruptibleAnimation(Spring.FLUID_MORPH);
        private final InterruptibleAnimation completion = new InterruptibleAnimation(Spring.STAGGER);
        private final LocalConfig local;
        private double grabOffset;
        private double pointerProgress;
        private boolean keyHeld;
        private long keyStart;
        private boolean completed;
        private float localReturnFrom;
        private float localReleaseProgress;

        Gesture() { this(null); }

        Gesture(LocalConfig local) { this.local = local; }

        boolean begin(Track track, double x, double y, long now, UiPreferences preferences) {
            if (completed || held() || !track.hasTravel()) return false;
            float progress = sample(now, preferences);
            if (!track.containsThumb(x, y, progress) || !pointer.begin(PRIMARY_BUTTON, x, y)) return false;
            grabOffset = x - track.thumbX(progress);
            pointerProgress = Math.min(progress, BEFORE_END);
            return true;
        }

        void drag(Track track, double x, double y) {
            if (pointer.move(PRIMARY_BUTTON, x, y) != null) pointerProgress = position(track, x);
        }

        boolean release(Track track, double x, double y, long now) {
            if (!pointer.active()) return false;
            boolean valid = pointer.release(PRIMARY_BUTTON, x, y) != null;
            if (valid) pointerProgress = position(track, x);
            return settle(pointerProgress, valid && (local == null || pointer.offsetX() > 0d), now);
        }

        boolean beginKey(long now) {
            if (completed || pointer.active()) return false;
            if (!keyHeld) keyStart = now;
            keyHeld = true;
            return true;
        }

        boolean releaseKey(long now) {
            return keyHeld && settle(keyProgress(now), true, now);
        }

        boolean held() {
            return pointer.active() || keyHeld;
        }

        boolean completed() {
            return completed;
        }

        boolean armed(long now) {
            return held() && heldProgress(now) >= threshold();
        }

        float sample(long now, UiPreferences preferences) {
            if (completed) return 1f;
            return held() ? (float) heldProgress(now) : local == null ? returning.sample(now, preferences) : 0f;
        }

        float sampleCompletion(long now, UiPreferences preferences) {
            return GuiMotion.clamp01(completion.sample(now, preferences));
        }

        void cancel(long now) {
            if (held()) settle(heldProgress(now), false, now);
        }

        void reset() {
            pointer.cancel();
            keyHeld = false;
            completed = false;
            localReturnFrom = 0f;
            returning.snapTo(0);
            completion.snapTo(0);
        }

        private double heldProgress(long now) {
            return keyHeld ? keyProgress(now) : pointerProgress;
        }

        private double keyProgress(long now) {
            return UiMath.clamp((now - keyStart) / (local == null ? KEY_TRAVEL_MS : (double) local.keyTravelMs()), 0d, 1d);
        }

        private double position(Track track, double x) {
            double startX = track.thumbX(0f) + grabOffset;
            int travel = track.travel();
            // 终点由指针坐标判定。回弹中的新手势须有向右位移。
            if (x >= startX + travel && pointer.offsetX() > 0d) return 1d;
            return UiMath.clamp((x - startX) / travel, 0d, BEFORE_END);
        }

        private boolean settle(double progress, boolean allowConfirm, long now) {
            pointer.cancel();
            keyHeld = false;
            if (allowConfirm && progress >= threshold()) {
                completed = true;
                if (local == null) completion.animateTo(1f, now);
                else localReleaseProgress = (float) progress;
                return true;
            }
            if (local == null) {
                returning.snapTo((float) progress);
                returning.animateTo(0f, now);
            } else {
                localReturnFrom = (float) progress;
            }
            return false;
        }

        private double threshold() {
            return local == null ? 1d : local.threshold();
        }
    }
}
