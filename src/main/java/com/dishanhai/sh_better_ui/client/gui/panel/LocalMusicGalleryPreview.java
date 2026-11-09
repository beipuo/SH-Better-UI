// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewContext;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewScene;
import com.dishanhai.sh_better_ui.client.audio.SystemAudioCapture;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.LocalMusicIsland;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.music.MusicService;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/** Live media uses the same API available to other mods. */
final class LocalMusicGalleryPreview implements PreviewScene {
    static final int HEIGHT = 132;
    private static final String TEXT = "sh_better_ui.music.";
    private final LocalMusicIsland island = ShBetterUiApi.music().island();
    private PreviewContext host;
    private Bounds compact, full;
    private UiButton toggle, reload, wave;
    private SystemAudioCapture capture;
    private boolean open = true;
    private Component togglePreview;
    private SystemAudioCapture.Status waveStatus;

    @Override public void layout(PreviewContext context) {
        host = context;
        compact = null;
        togglePreview = null;
        waveStatus = null;
        var area = host.viewport();
        if (area.width() < 100 || area.height() < 69) return;
        int width = Math.min(area.width() - 4, 218);
        int panelWidth = Math.min(area.width() - 4, 276);
        int y = area.y() + 3;
        compact = new Bounds(area.x() + (area.width() - width) / 2, y, width, 40);
        full = new Bounds(area.x() + (area.width() - panelWidth) / 2, y, panelWidth, Math.min(106, area.height() - 26));
        boolean expandable = full.height() >= 79;
        island.setExpanded(expandable && open, PageTransition.nowMs(), true);
        int buttonY = full.bottom() + 4;
        int buttonWidth = (panelWidth - 8) / 3;
        toggle = host.add(new UiButton(full.x(), buttonY, buttonWidth, 18,
                label(island.expanded() ? "collapse" : "expand"), ignored -> {
                    open = !open;
                    island.setExpanded(open, PageTransition.nowMs(),
                            host.preferences().reducedMotion() || host.preferences().highContrast());
                }, host.preferences(), false));
        toggle.active = expandable;
        reload = host.add(new UiButton(full.x() + buttonWidth + 4, buttonY, buttonWidth, 18,
                label("lyrics_reload"), ignored -> ShBetterUiApi.music().refreshLyrics(), host.preferences(), false));
        reload.setPreview(label("lyrics_hint"));
        wave = host.add(new UiButton(full.x() + 2 * (buttonWidth + 4), buttonY, buttonWidth, 18,
                label("wave_enable"), ignored -> toggleWave(), host.preferences(), false));
        if (buttonWidth < 72) {
            toggle.setIcon(GraphicIcon.LIST);
            reload.setIcon(GraphicIcon.REFRESH);
            wave.setIcon(GraphicIcon.MUSIC);
        }
        updateWave();
    }

    @Override public void render(UiFrame frame) {
        if (compact == null) {
            var area = frame.viewport();
            GalleryPreviewLayout.drawCentered(frame.graphics(), host.font(),
                    Component.translatable("sh_better_ui.panel.developer.gallery.music_island.space"),
                    area.x(), area.y(), area.width());
            return;
        }
        var active = capture;
        island.render(frame, host.font(), compact, full, toggle.isHoveredOrFocused(),
                active != null && active.status() == SystemAudioCapture.Status.RUNNING
                        ? Optional.of(active.sample()) : Optional.empty());
        var narration = island.narration();
        if (!narration.equals(togglePreview)) {
            togglePreview = narration;
            toggle.setMessage(label(island.expanded() ? "collapse" : "expand"));
            toggle.setPreview(narration);
        }
        var lyricState = ShBetterUiApi.music().getCurrentLyrics().state();
        reload.active = lyricState == MusicService.LyricsState.AVAILABLE || lyricState == MusicService.LyricsState.UNAVAILABLE;
        updateWave();
    }

    private void toggleWave() {
        if (waveStatus == SystemAudioCapture.Status.STARTING || waveStatus == SystemAudioCapture.Status.RUNNING) {
            stopWave();
        } else {
            stopWave();
            capture = new SystemAudioCapture();
            capture.start();
        }
        updateWave();
    }

    private void updateWave() {
        var status = Util.getPlatform() != Util.OS.WINDOWS ? SystemAudioCapture.Status.UNSUPPORTED
                : capture == null ? SystemAudioCapture.Status.READY : capture.status();
        if (status == waveStatus) return;
        waveStatus = status;
        wave.active = status != SystemAudioCapture.Status.UNSUPPORTED;
        boolean running = status == SystemAudioCapture.Status.RUNNING || status == SystemAudioCapture.Status.STARTING;
        wave.setMessage(label(running ? "wave_disable" : "wave_enable"));
        wave.setPreview(Component.translatable(status.translationKey()).append("\n").append(label("wave_hint")));
    }

    private void stopWave() {
        if (capture != null) {
            capture.requestStop();
            capture = null;
        }
    }

    @Override public void suspend() {
        stopWave();
        island.suspend();
    }

    private static Component label(String key) { return Component.translatable(TEXT + key); }
}
