package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

/** 山海：客户端的非模态小弹窗，由库在 HUD 或 Screen 绘制结束后显示。 */
public final class UiToast {
    private static final long DEFAULT_HOLD_MS = 3_500L;
    private static final long ENTER_MS = 320L;
    private static final long EXIT_MS = 220L;
    private static final long MAX_HOLD_MS = Long.MAX_VALUE - ENTER_MS - EXIT_MS;
    private static final int MAX_PENDING = 16;
    private static final int MARGIN = 8;
    private static final int PADDING = 8;
    private static final int ICON_GAP = 6;
    private static final int TEXT_INSET = PADDING + GraphicIcon.PIXELS + ICON_GAP;
    private static final int MAX_WIDTH = 208;
    private static final int MAX_LINES = 6;
    private static final ArrayDeque<Notice> pending = new ArrayDeque<>();
    private static Active active;

    private UiToast() {}

    public enum Kind {
        INFO(GraphicIcon.INFO, DarkModeTheme.ACCENT),
        SUCCESS(GraphicIcon.SUCCESS, DarkModeTheme.SUCCESS),
        WARNING(GraphicIcon.WARNING, 0xFFFBBF24),
        ERROR(GraphicIcon.ERROR, 0xFFF87171);

        private final GraphicIcon icon;
        private final int color;

        Kind(GraphicIcon icon, int color) {
            this.icon = icon;
            this.color = color;
        }
    }

    public enum Effect {
        SLIDE_FROM_RIGHT, DROP_FROM_TOP, FADE, POP, NONE
    }

    /** 标题使用本库翻译，详情可传任务名或其他本地化短句。 */
    public static void taskCompleted(Component details) {
        show(Kind.SUCCESS, Component.translatable("sh_better_ui.toast.task_completed"), details);
    }

    public static void show(Kind kind, Component title, Component details) {
        show(kind, title, details, Effect.SLIDE_FROM_RIGHT);
    }

    public static void show(Kind kind, Component title, Component details, Effect effect) {
        show(kind, title, details, effect, DEFAULT_HOLD_MS, UiPreferences.DEFAULT);
    }

    /**
     * 在客户端提交。基础停留时间会乘以原版通知时长倍率，可用 Component.empty() 省略详情。
     * 每次显示一条。等待队列满时舍弃最早的待显示通知。
     */
    public static void show(Kind kind, Component title, Component details, Effect effect,
                            long holdMs, UiPreferences preferences) {
        if (holdMs <= 0 || holdMs > MAX_HOLD_MS) {
            throw new IllegalArgumentException("holdMs");
        }
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(preferences, "preferences");
        Notice notice = new Notice(kind, title.copy(), details.copy(), effect, holdMs, preferences);
        Minecraft.getInstance().execute(() -> {
            if (pending.size() == MAX_PENDING) pending.removeFirst();
            pending.addLast(notice);
        });
    }

    /** 清空正在显示及排队的通知。退出世界时由库调用。 */
    public static void clear() {
        Minecraft.getInstance().execute(() -> {
            pending.clear();
            active = null;
        });
    }

    /** 供平台渲染事件调用，每帧只应在 HUD 或 Screen 其中一处绘制。 */
    public static void renderOverlay(GuiGraphics graphics) {
        if (active == null && pending.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int width = Math.min(MAX_WIDTH, graphics.guiWidth() - 2 * MARGIN);
        int lineLimit = Math.min(MAX_LINES, (graphics.guiHeight() - 2 * MARGIN - 2 * PADDING) / font.lineHeight);
        if (width < TEXT_INSET + PADDING + 16 || lineLimit < 2) return;

        long now = PageTransition.nowMs();
        if (active == null || now - active.startedAt >= active.totalMs()) {
            Notice next = pending.pollFirst();
            if (next == null) {
                active = null;
                return;
            }
            active = new Active(next, now, minecraft.options.notificationDisplayTime().get());
            minecraft.getNarrator().say(CommonComponents.joinForNarration(next.title, next.details));
        }

        Layout layout = active.layout(font, width, lineLimit);
        Notice notice = active.notice;
        Frame frame = sample(notice.effect, notice.preferences.reducedMotion(), now - active.startedAt,
                active.holdMs, graphics.guiWidth(), layout.height);
        int alpha = Math.round(frame.opacity * 255f);
        // Minecraft 字体会把极低的 alpha 当成省略 alpha，跳过这些帧以免淡入时闪白。
        if (alpha < 4) return;

        graphics.pose().pushPose();
        try {
            graphics.pose().translate(MARGIN + frame.offsetX, MARGIN + frame.offsetY, 500f);
            graphics.pose().translate(width / 2f, layout.height / 2f, 0f);
            graphics.pose().scale(frame.scale, frame.scale, 1f);
            graphics.pose().translate(-width / 2f, -layout.height / 2f, 0f);
            boolean contrast = notice.preferences.highContrast();
            DarkModeTheme.rounded(graphics, 0, 0, width, layout.height, 6, 1,
                    tint(contrast ? DarkModeTheme.TEXT : notice.kind.color, alpha),
                    tint(contrast ? DarkModeTheme.BACKGROUND : DarkModeTheme.SURFACE, alpha));
            notice.kind.icon.draw(graphics, PADDING, (layout.height - GraphicIcon.PIXELS) / 2, 1,
                    tint(contrast ? DarkModeTheme.TEXT : notice.kind.color, alpha));
            int y = (layout.height - (layout.title.size() + layout.details.size()) * font.lineHeight) / 2;
            for (FormattedCharSequence line : layout.title) {
                graphics.drawString(font, line, TEXT_INSET, y, tint(DarkModeTheme.TEXT, alpha), false);
                y += font.lineHeight;
            }
            for (FormattedCharSequence line : layout.details) {
                graphics.drawString(font, line, TEXT_INSET, y, tint(DarkModeTheme.TEXT_SECONDARY, alpha), false);
                y += font.lineHeight;
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    static Frame sample(Effect effect, boolean reducedMotion, long ageMs, long holdMs, int screenWidth, int height) {
        float enter = GuiMotion.clamp01(ageMs / (float) ENTER_MS);
        float exit = GuiMotion.clamp01((ageMs - ENTER_MS - holdMs) / (float) EXIT_MS);
        float remaining = 1f - enter;
        float hidden = remaining * remaining * remaining + exit * exit;
        return switch (reducedMotion ? Effect.NONE : effect) {
            case SLIDE_FROM_RIGHT -> new Frame((screenWidth - MARGIN) * hidden, 0f, 1f, 1f);
            case DROP_FROM_TOP -> new Frame(0f, -(height + MARGIN) * hidden, 1f, 1f);
            case FADE -> new Frame(0f, 0f, 1f, 1f - hidden);
            case POP -> new Frame(0f, 0f, GuiMotion.lerp(0.84f, 1f, 1f - hidden), 1f - hidden);
            case NONE -> new Frame(0f, 0f, 1f, 1f);
        };
    }

    private static int tint(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    record Frame(float offsetX, float offsetY, float scale, float opacity) {}

    private record Notice(Kind kind, Component title, Component details, Effect effect,
                          long holdMs, UiPreferences preferences) {}

    private record Layout(int width, int lineLimit, int height,
                          List<FormattedCharSequence> title, List<FormattedCharSequence> details) {}

    private static final class Active {
        private final Notice notice;
        private final long startedAt;
        private final long holdMs;
        private Layout layout;

        private Active(Notice notice, long startedAt, double durationMultiplier) {
            this.notice = notice;
            this.startedAt = startedAt;
            this.holdMs = Math.clamp(Math.round(notice.holdMs * durationMultiplier), 1L, MAX_HOLD_MS);
        }

        private long totalMs() {
            return notice.preferences.reducedMotion() || notice.effect == Effect.NONE
                    ? holdMs : ENTER_MS + holdMs + EXIT_MS;
        }

        private Layout layout(Font font, int width, int lineLimit) {
            if (layout == null || width != layout.width || lineLimit != layout.lineLimit) {
                int textWidth = width - TEXT_INSET - PADDING;
                var details = notice.details.getString().isEmpty()
                        ? List.<FormattedCharSequence>of() : font.split(notice.details, textWidth);
                var title = font.split(notice.title, textWidth);
                int titleCount = Math.min(title.size(), details.isEmpty() ? lineLimit : lineLimit - 1);
                int detailsCount = Math.min(details.size(), lineLimit - titleCount);
                int height = 2 * PADDING + Math.max(GraphicIcon.PIXELS, (titleCount + detailsCount) * font.lineHeight);
                layout = new Layout(width, lineLimit, height,
                        title.subList(0, titleCount), details.subList(0, detailsCount));
            }
            return layout;
        }
    }
}
