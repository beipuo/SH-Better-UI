package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;

/** 山海：用同一个帧快照驱动画面、滑杆与状态，缩放窗口时保留播放位置。 */
final class FrameTimelineGalleryPreview {
    static final int PREVIEW_EXTRA = 68;
    private static final String TEXT = "gallery.frame_timeline.";
    private static final UiTimeline.Sequence CAPTION = new UiTimeline.Sequence(30, 90);
    private final UiTimeline timeline = new UiTimeline(30, 120);
    private UiButton transport;
    private UiSlider scrubber;
    private UiTimeline.State shownState;
    private Bounds canvas;

    List<AbstractWidget> widgets(Bounds area, UiPreferences preferences) {
        int gap = 3;
        int height = area.height() < 90 ? 16 : 20;
        int x = area.x() + gap;
        int width = area.width() - 2 * gap;
        int buttonsY = area.bottom() - gap - height;
        int sliderY = buttonsY - gap - height;
        int resetWidth = 64;
        canvas = new Bounds(x, area.y() + gap, width, Math.max(1, sliderY - area.y() - 2 * gap));
        shownState = null;
        transport = DeveloperPanelScreen.button(x, buttonsY, width - resetWidth - gap, height,
                label(TEXT + "play"), null, preferences, () -> {
                    long now = PageTransition.nowMs();
                    if (timeline.sample(now).state() == UiTimeline.State.PLAYING) timeline.pause(now);
                    else if (preferences.reducedMotion()) timeline.seek(timeline.durationFrames() - 1);
                    else timeline.play(now);
                    sync(timeline.sample(now));
                });
        transport.setPreview(label(TEXT + (preferences.reducedMotion() ? "reduced_motion" : "hint")));
        var reset = DeveloperPanelScreen.button(x + width - resetWidth, buttonsY, resetWidth, height,
                Component.translatable("controls.reset"), GraphicIcon.RESET, preferences, () -> {
                    timeline.reset();
                    sync(timeline.sample(PageTransition.nowMs()));
                });
        scrubber = new UiSlider(new Bounds(x, sliderY, width, height), 0, timeline.durationFrames() - 1, 1,
                timeline.sample(PageTransition.nowMs()).index(), preferences,
                frame -> label(TEXT + "frame", frame, timeline.durationFrames() - 1, timeline.fps()), frame -> {
                    timeline.seek(frame);
                    sync(timeline.sample(PageTransition.nowMs()));
                });
        scrubber.setTooltip(Tooltip.create(label(TEXT + "hint")));
        sync(timeline.sample(PageTransition.nowMs()));
        return List.of(scrubber, transport, reset);
    }

    void pause() { timeline.pause(PageTransition.nowMs()); }

    private void sync(UiTimeline.Frame frame) {
        scrubber.setValue(frame.index());
        if (shownState != frame.state()) {
            shownState = frame.state();
            var action = label(TEXT + (frame.state() == UiTimeline.State.PLAYING ? "pause" : "play"));
            var state = label(TEXT + frame.state().name().toLowerCase(Locale.ROOT));
            transport.setMessage(label(TEXT + "transport", action, state));
        }
    }

    void render(GuiGraphics graphics, UiPreferences preferences) {
        if (canvas == null) return;
        var frame = timeline.sample(PageTransition.nowMs());
        sync(frame);
        var font = Minecraft.getInstance().font;
        graphics.enableScissor(canvas.x(), canvas.y(), canvas.right(), canvas.bottom());
        try {
            graphics.fill(canvas.x(), canvas.y(), canvas.right(), canvas.bottom(),
                    preferences.highContrast() ? 0xFF000000 : 0xFF1A2333);
            boolean roomy = canvas.height() >= 42;
            int motionY = canvas.y() + (roomy ? 14 : 1);
            int motionHeight = Math.max(4, canvas.bottom() - motionY - (roomy ? 12 : 1));
            int cardSize = Math.min(24, motionHeight);
            float progress = frame.interpolate(0, 90, 0f, 1f);
            int cardX = canvas.x() + 5 + Math.round(progress * Math.max(0, canvas.width() - cardSize - 10));
            int alpha = preferences.highContrast() ? 255 : Math.max(8, Math.round(frame.interpolate(0, 20, 0f, 255f)));
            RoundedCorners.draw(graphics, cardX, motionY, cardSize, cardSize, Math.min(5, cardSize / 2), 1,
                    DarkModeTheme.TEXT, alpha << 24 | LiquidGlassTheme.ACCENT & 0xFFFFFF);
            if (roomy) {
                graphics.drawCenteredString(font, label(TEXT + "idea"), canvas.x() + canvas.width() / 2,
                        canvas.y() + 2, LiquidGlassTheme.TEXT);
                Component caption = CAPTION.contains(frame)
                        ? label(TEXT + "sequence", CAPTION.localFrame(frame)) : label(TEXT + "sequence_wait");
                graphics.drawCenteredString(font, caption, canvas.x() + canvas.width() / 2,
                        canvas.bottom() - font.lineHeight - 1, LiquidGlassTheme.TEXT);
            }
        } finally {
            graphics.disableScissor();
        }
    }
}
