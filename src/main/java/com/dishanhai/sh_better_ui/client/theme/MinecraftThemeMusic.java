// Author: dishanhai
package com.dishanhai.sh_better_ui.client.theme;

import com.dishanhai.sh_better_ui.client.music.ClientMusic;
import com.dishanhai.sh_better_ui.client.music.MusicArtworkTexture;
import com.dishanhai.sh_better_ui.client.music.MusicSnapshot;
import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;
import com.dishanhai.sh_better_ui.theme.palette.AudioReactiveTheme;
import com.dishanhai.sh_better_ui.theme.palette.ThemePalette;
import com.dishanhai.sh_better_ui.theme.palette.ThemePaletteGenerator;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/** Client-owned bridge for low-frequency artwork colors and independent local audio effects. */
final class MinecraftThemeMusic implements AutoCloseable {
    private final ThemePaletteGenerator generator;
    private final AudioReactiveTheme reactive = new AudioReactiveTheme(.85f, 40, .4f, .2f);
    private ScheduledThreadPoolExecutor decoder;
    private Future<?> pending;
    private String requested;
    private ThemePalette palette;
    private boolean enabled;
    private boolean playing;
    private long generation;

    MinecraftThemeMusic(ThemePaletteGenerator generator) { this.generator = generator; }

    void enabled(boolean value) {
        enabled = value;
        if (!value) resetArtwork();
    }

    ThemePalette palette() { return palette; }

    AudioReactiveTheme.Effects effects(ThemePreferences preferences, long now) {
        var frame = playing && preferences.musicReactive() ? ClientMusic.audio().orElse(null) : null;
        return reactive.sample(frame != null, frame == null ? 0 : frame.bass(), frame == null ? 0 : frame.mid(),
                preferences, now);
    }

    void capture(MusicSnapshot.Session session, ThemePreferences preferences, boolean light, Executor owner) {
        playing = session != null && session.status() == MusicSnapshot.PlaybackStatus.PLAYING;
        var artwork = enabled && playing && preferences.musicReactive() && !preferences.lowPerformance()
                ? session.artwork() : null;
        String id = artwork == null ? null : artwork.id() + ":" + light;
        if (Objects.equals(requested, id)) return;
        resetArtwork();
        requested = id;
        if (artwork == null) return;
        if (decoder == null) {
            decoder = new ScheduledThreadPoolExecutor(1, task -> {
                Thread thread = new Thread(task, "shb-theme-cover");
                thread.setDaemon(true);
                return thread;
            });
            decoder.setRemoveOnCancelPolicy(true);
        }
        long ticket = generation;
        pending = decoder.submit(() -> {
            var value = read(artwork, light);
            if (!Thread.currentThread().isInterrupted()) {
                owner.execute(() -> {
                    if (generation == ticket) palette = value;
                });
            }
        });
    }

    private ThemePalette read(MusicSnapshot.Artwork artwork, boolean light) {
        try (var image = MusicArtworkTexture.readArtwork(artwork)) {
            if (image == null) return null;
            int width = image.getWidth(), height = image.getHeight();
            int[] pixels = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    pixels[y * width + x] = (abgr & 0xff00ff00) | ((abgr >>> 16) & 255) | ((abgr & 255) << 16);
                }
            }
            ColorValue seed = generator.extract(width, height, pixels);
            return generator.fromSeed(seed, light);
        } catch (IOException | RuntimeException failure) {
            return null;
        }
    }

    private void resetArtwork() {
        generation++;
        if (pending != null) pending.cancel(true);
        pending = null;
        requested = null;
        palette = null;
    }

    @Override
    public void close() {
        resetArtwork();
        reactive.reset();
        if (decoder != null) decoder.shutdownNow();
    }
}
