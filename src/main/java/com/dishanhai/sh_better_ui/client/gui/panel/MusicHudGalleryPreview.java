package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.audio.AudioFrame;
import com.dishanhai.sh_better_ui.client.audio.MusicRhythm;
import com.dishanhai.sh_better_ui.client.audio.SystemAudioCapture;
import com.dishanhai.sh_better_ui.client.audio.SystemAudioCapture.Status;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicHud;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;

/** 山海：无声演示与手动开启的系统音频共用同一套律动映射。 */
final class MusicHudGalleryPreview implements PreviewScene {
    static final int HEIGHT = 132;
    private static final String TEXT = "gallery.music_hud.";
    private static final int INSET = 6;
    private static final int BUTTON_HEIGHT = 22;
    private final MusicRhythm rhythm = ShBetterUiApi.motion().musicRhythm();
    private SystemAudioCapture capture;
    private boolean demoPlaying = true;
    private long demoStart = Long.MIN_VALUE;
    private Font font;
    private UiButton demoButton, captureButton;
    private Status displayedStatus;
    private boolean displayedDemo;
    private Component statusText;

    @Override
    public void layout(PreviewContext host) {
        font = host.font();
        Bounds area = host.viewport();
        int width = Math.min(140, (area.width() - 3 * INSET) / 2);
        int x = area.x() + (area.width() - 2 * width - INSET) / 2;
        int y = area.bottom() - BUTTON_HEIGHT - INSET;
        var prefs = host.preferences();
        demoButton = GalleryPreviewLayout.addButton(host, new Bounds(x, y, width, BUTTON_HEIGHT),
                label(TEXT + "play"), GraphicIcon.RESUME, () -> {
                    stopCapture();
                    demoPlaying = !demoPlaying;
                    demoStart = Long.MIN_VALUE;
                    if (demoPlaying) rhythm.reset();
                    updateControls(prefs);
                });
        captureButton = GalleryPreviewLayout.addButton(host, new Bounds(x + width + INSET, y, width, BUTTON_HEIGHT),
                label(TEXT + "listen"), GraphicIcon.MUSIC, () -> toggleCapture(prefs));
        statusText = null;
        updateControls(prefs);
    }

    private void toggleCapture(UiPreferences preferences) {
        Status status = status();
        if (staticMode(preferences) || status == Status.STOPPING) return;
        if (listening(status)) {
            stopCapture();
            updateControls(preferences);
            return;
        }
        stopCapture();
        if (status() == Status.STOPPING) {
            updateControls(preferences);
            return;
        }
        demoPlaying = false;
        rhythm.reset();
        capture = ShBetterUiApi.audio().systemOutput();
        capture.start();
        updateControls(preferences);
    }

    @Override
    public void render(UiFrame frame) {
        var prefs = frame.preferences();
        boolean still = staticMode(prefs);
        long now = frame.nowMs();
        AudioFrame audio;
        if (still) {
            stopCapture();
            demoStart = Long.MIN_VALUE;
            audio = AudioFrame.silence(now);
        } else if (demoPlaying) {
            if (demoStart == Long.MIN_VALUE) demoStart = now;
            audio = demoFrame(now, now - demoStart);
        } else if (capture != null) {
            audio = capture.sample();
        } else {
            audio = AudioFrame.silence(now);
        }
        var motion = rhythm.sample(audio, now, still);
        updateControls(prefs);
        demoButton.setIconOffsetY(-Math.round(2.4f * motion.lift(.3f)));
        captureButton.setIconOffsetY(-Math.round(2.4f * motion.lift(2.8f)));

        var g = frame.graphics();
        var area = frame.viewport();
        MusicHud.drawPanel(g, area, motion, prefs);
        int x = area.x() + INSET, y = area.y() + INSET;
        GraphicIcon.MUSIC.draw(g, x, y + 1 - Math.round(2 * motion.lift(1.4f)), 1,
                prefs.highContrast() ? 0xFFFFFFFF : 0xFF83EADD, prefs);
        int textX = x + GraphicIcon.PIXELS + 5;
        g.drawString(font, label(TEXT + "title"), textX, y, 0xFFF0F8FF, false);
        g.drawString(font, statusText, textX, y + font.lineHeight + 2, 0xFFD0DAED, false);

        int scopeTop = y + 2 * font.lineHeight + 4;
        int scopeBottom = demoButton.getY() - INSET;
        if (scopeBottom - scopeTop >= 7) {
            MusicHud.drawScope(g, new Bounds(x, scopeTop, area.width() - 2 * INSET, scopeBottom - scopeTop),
                    motion, prefs);
        }
    }

    private void updateControls(UiPreferences prefs) {
        Status status = status();
        boolean still = staticMode(prefs);
        boolean playing = demoPlaying && !still;
        if (statusText != null && status == displayedStatus && playing == displayedDemo && demoButton.active == !still) {
            return;
        }
        displayedStatus = status;
        displayedDemo = playing;
        statusText = still ? label(TEXT + (prefs.highContrast() ? "contrast" : "reduced"))
                : playing ? label(TEXT + "demo")
                : switch (status) {
                    case READY, CLOSED -> label(TEXT + "paused");
                    case STARTING -> label(TEXT + "starting");
                    case RUNNING -> label(TEXT + "running");
                    case FAILED -> label(TEXT + "failed");
                    case UNSUPPORTED -> label(TEXT + "unsupported");
                    default -> Component.translatable(status.translationKey());
                };
        demoButton.active = !still;
        captureButton.active = !still && status != Status.STOPPING;
        demoButton.setMessage(label(TEXT + (playing ? "pause" : "play")));
        demoButton.setLeadingIcon(playing ? GraphicIcon.PAUSE : GraphicIcon.RESUME);
        demoButton.setSelected(playing);
        demoButton.setPreview(still ? statusText : label(TEXT + "demo_hint"));
        boolean listening = listening(status);
        captureButton.setMessage(label(TEXT + (listening ? "stop" : status == Status.FAILED ? "retry" : "listen")));
        captureButton.setSelected(listening);
        captureButton.setPreview(still ? statusText : Component.translatable(status.translationKey())
                .append("\n").append(Component.translatable("sh_better_ui.audio.tooltip")));
    }

    private Status status() { return capture == null ? Status.READY : capture.status(); }

    private static boolean listening(Status status) { return status == Status.STARTING || status == Status.RUNNING; }

    private static boolean staticMode(UiPreferences prefs) { return prefs.reducedMotion() || prefs.highContrast(); }

    private void stopCapture() {
        if (capture != null && status() != Status.CLOSED && status() != Status.STOPPING) capture.requestStop();
    }

    @Override
    public void suspend() {
        stopCapture();
        demoStart = Long.MIN_VALUE;
        rhythm.reset();
    }

    /** 一个 15 秒循环。每个频段用不同的节奏，整体响度缓慢进入和离开高潮。 */
    private static AudioFrame demoFrame(long now, long elapsed) {
        long beat = elapsed / 500;
        double age = elapsed % 500;
        double section = .5 - .5 * Math.cos(2 * Math.PI * (elapsed % 15_000) / 15_000);
        double accent = beat % 4 == 0 ? 1 : .42;
        double kick = accent * Math.exp(-age / 82);
        double melody = .55 + .45 * Math.sin(elapsed * .0051 - .8);
        double ticks = Math.exp(-((elapsed + 71) % 250) / 64d);
        float bass = (float) (.021 + section * .075 + kick * (.055 + section * .22));
        float mid = (float) (.014 + melody * (.024 + .23 * section));
        float treble = (float) (.003 + ticks * (.024 + section * .14));
        float rms = (float) (.035 + section * .225 + kick * (.02 + section * .12));
        return new AudioFrame(now, rms, 1.4f * rms, bass, mid, treble, (float) Math.exp(-age / 120), beat);
    }
}
