// Copyright (c) 2026 dishanhai. Licensed under the MIT License.
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.MusicApi;
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiFrame;
import com.dishanhai.sh_better_ui.client.audio.AudioFrame;
import com.dishanhai.sh_better_ui.client.audio.MusicRhythm;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.lyrics.common.hud.FloatingLyricRenderer;
import com.dishanhai.sh_better_ui.client.music.MusicLyricFeed;
import com.dishanhai.sh_better_ui.client.music.MusicService;
import com.dishanhai.sh_better_ui.client.music.MusicSnapshot;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.Optional;

/** A per-view music card. Hosts supply a keyboard-accessible toggle and the two bounds. */
public final class LocalMusicIsland {
    private static final int ACCENT = 0xFF72DDD0;
    private static final String TEXT = "sh_better_ui.music.";
    private final MusicApi music;
    private final InterruptibleAnimation expansion = new InterruptibleAnimation(Spring.FLUID_MORPH);
    private final MusicRhythm rhythm = new MusicRhythm();
    private final FloatingLyricRenderer lyrics;
    private MusicService.Frame current;
    private boolean expanded;

    public LocalMusicIsland(MusicApi music) {
        this.music = music;
        current = music.getFrame();
        lyrics = ShBetterUiApi.lyrics().renderer(new MusicLyricFeed(() -> current));
    }

    public boolean expanded() { return expanded; }

    public void setExpanded(boolean expanded, long nowMs, boolean reducedMotion) {
        if (this.expanded == expanded) return;
        this.expanded = expanded;
        float target = expanded ? 1 : 0;
        if (reducedMotion) expansion.snapTo(target);
        else expansion.blendTo(target, nowMs, 0, expanded ? 265 : 220,
                InterruptibleAnimation.BlendCurve.SMOOTHSTEP);
    }

    public Component narration() {
        var snapshot = current.snapshot();
        var session = snapshot.session();
        var action = Component.translatable(TEXT + (expanded ? "collapse" : "expand"));
        var status = status(snapshot);
        if (session == null || session.track() == null) return Component.translatable(TEXT + "narration_no_track", action, status);
        return Component.translatable(TEXT + "narration", action, title(session), status)
                .append("\n").append(credit(session));
    }

    public void render(UiFrame ui, Font font, Bounds compact, Bounds full, boolean hovered) {
        render(ui, font, compact, full, hovered, music.getAudioFeatures());
    }

    /** The host may supply its own authorized output capture for this view. */
    public void render(UiFrame ui, Font font, Bounds compact, Bounds full, boolean hovered, Optional<AudioFrame> audio) {
        if (compact.width() < 94 || compact.height() < 29) return;
        current = music.getFrame();
        var snapshot = current.snapshot();
        var session = snapshot.session();
        var preferences = ui.preferences();
        boolean still = preferences.reducedMotion() || preferences.highContrast();
        var outline = MusicIsland.outline(compact, full, expansion.sample(ui.nowMs(), still));
        var graphics = ui.graphics();
        MusicIsland.drawShell(graphics, outline, preferences, hovered);

        int x = compact.x() + 7;
        int artY = compact.y() + (compact.height() - 18) / 2;
        var cover = music.cover(current);
        if (cover.isPresent()) {
            var texture = cover.get();
            float fit = 18f / Math.max(texture.width(), texture.height());
            int width = Math.max(1, Math.round(texture.width() * fit));
            int height = Math.max(1, Math.round(texture.height() * fit));
            graphics.blit(texture.id(), x + (18 - width) / 2, artY + (18 - height) / 2, width, height,
                    0f, 0f, texture.width(), texture.height(), texture.width(), texture.height());
        } else GraphicIcon.MUSIC.draw(graphics, x + 1, artY + 1, 1, 0xFFFFFFFF, preferences);

        int textRight = compact.right() - 8;
        if (audio.isPresent() && session != null && session.status() == MusicSnapshot.PlaybackStatus.PLAYING) {
            int waveX = textRight - MusicIsland.WAVEFORM_WIDTH;
            var movement = rhythm.sample(audio.get(), ui.nowMs(), still);
            MusicIsland.drawWaveform(graphics, new Bounds(waveX, artY, MusicIsland.WAVEFORM_WIDTH, 18),
                    movement, ACCENT, preferences);
            textRight = waveX - 4;
        } else rhythm.reset();

        var state = status(snapshot);
        var track = session == null ? null : session.track();
        int secondary = preferences.highContrast() ? 0xFFFFFFFF : 0xFFCBD5E1;
        int step = font.lineHeight + 1;
        boolean showCredit = track != null && compact.height() >= step * 3 + 8;
        int lines = session == null ? 1 : showCredit ? 3 : 2;
        int y = compact.y() + (compact.height() - lines * step + 1) / 2;
        int textX = x + 22;
        if (session != null) {
            drawText(ui, font, title(session), textX, y, textRight, 0xFFFFFFFF);
            y += step;
            if (showCredit) {
                drawText(ui, font, credit(session), textX, y, textRight, secondary);
                y += step;
            }
        }
        drawText(ui, font, state, textX, y, textRight, secondary);

        int height = Math.round(outline.rect().getH());
        int lineY = compact.bottom() + 2;
        int bottom = compact.y() + height - 6;
        if (bottom < lineY + step + 4 || track == null) return;
        int left = Math.round(outline.rect().getX()) + 10;
        int right = Math.round(outline.rect().getX() + outline.rect().getW()) - 10;
        long position = session.positionAt(System.nanoTime());
        if (position >= 0 && session.durationMs().isPresent()) {
            long duration = session.durationMs().getAsLong();
            graphics.fill(left, lineY, right, lineY + 2, 0xFF415064);
            int fill = (int) Math.round((right - left) * Math.min(1, position / (double) duration));
            if (fill > 0) graphics.fill(left, lineY, left + fill, lineY + 2, still ? 0xFFFFFFFF : ACCENT);
        }
        var time = position < 0 ? Component.translatable(TEXT + "time_unknown")
                : Component.translatable(TEXT + "time", clock(position),
                        session.durationMs().isPresent() ? clock(session.durationMs().getAsLong())
                                : Component.translatable(TEXT + "duration_unknown"));
        drawText(ui, font, time, left, lineY + 4, right, secondary);
        int lyricY = lineY + step + 7;
        if (bottom - lyricY < step) return;
        var lyricState = current.lyrics().state();
        if (lyricState == MusicService.LyricsState.AVAILABLE && position >= 0) {
            int lyricHeight = bottom - lyricY;
            int lineCount = lyricHeight >= 28 ? 2 : 1;
            if (lyrics.style().lineCount() != lineCount) lyrics.setStyle(lyrics.style().withLineCount(lineCount));
            if (ShBetterUiApi.lyrics().draw(lyrics, ui, font, new Bounds(left, lyricY, right - left, lyricHeight))) {
                return;
            }
        }
        String hint = lyricState == MusicService.LyricsState.LOADING ? "lyrics_loading"
                : lyricState != MusicService.LyricsState.AVAILABLE ? "lyrics_missing"
                : position < 0 ? "lyrics_no_time" : "lyrics_waiting";
        drawText(ui, font, Component.translatable(TEXT + hint), left, lyricY, right, secondary);
    }

    public void suspend() { rhythm.reset(); }

    private static void drawText(UiFrame ui, Font font, Component text, int x, int y, int right, int color) {
        String value = text.getString();
        int width = Math.max(0, right - x);
        if (font.width(value) <= width) ui.graphics().drawString(font, text, x, y, color, false);
        else if (width >= font.width("…")) {
            ui.graphics().drawString(font, font.plainSubstrByWidth(value, width - font.width("…")) + "…",
                    x, y, color, false);
        }
    }

    private static String clock(long millis) {
        long seconds = millis / 1000;
        return seconds / 60 + (seconds % 60 < 10 ? ":0" : ":") + seconds % 60;
    }

    private static Component credit(MusicSnapshot.Session session) {
        var track = session.track();
        var artist = track.artist().map(Component::literal).orElseGet(() -> Component.translatable(TEXT + "unknown_artist"));
        return track.album().<Component>map(album -> Component.translatable(TEXT + "credit", artist, album)).orElse(artist);
    }

    private static Component title(MusicSnapshot.Session session) {
        return session.track() == null ? Component.translatable(TEXT + "unknown_title")
                : session.track().title().map(Component::literal).orElseGet(() ->
                        Component.translatable(TEXT + "unknown_title"));
    }

    private static Component status(MusicSnapshot snapshot) {
        String key = snapshot.session() == null
                ? snapshot.state().name() : snapshot.session().status().name();
        return Component.translatable(TEXT + "status." + key.toLowerCase(Locale.ROOT));
    }
}
