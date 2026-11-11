// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Snapshot;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceService;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;

import java.time.Duration;

import static com.dishanhai.shbui_kernel_example.ExampleSmoke.press;
import static com.dishanhai.shbui_kernel_example.ExampleSmoke.require;

/** Drives the example page's actual buttons and reads publications through the public kernel API. */
final class LiveExamplesSmoke {
    private static final long STAGE_TIMEOUT = Duration.ofSeconds(15).toNanos();
    private static final long PAUSE_CHECK = Duration.ofMillis(250).toNanos();
    private final LiveSurfaceService surfaces = UnifiedUI.get().require(LiveSurfaceService.KEY);
    private Stage stage = Stage.MUSIC_PLAYING;
    private long stageStarted = System.nanoTime();
    private long pausedAt;
    private double pausedProgress, backgroundProgress;
    private long backgroundInstance;

    LiveExamplesSmoke(Screen screen) {
        requireEmpty();
        press(screen, button(screen, "music_start"));
    }

    boolean advance(Screen screen) {
        var view = surfaces.view();
        var primary = view.plan().primary();
        long now = System.nanoTime();
        require(now - stageStarted < STAGE_TIMEOUT, "Live example stalled at " + stage + " primary="
                + (primary == null ? null : primary.id()) + " active=" + surfaces.diagnostics().activities());
        switch (stage) {
            case MUSIC_PLAYING -> {
                if (!named(primary, "music") || primary.content().progress() < .08) return false;
                require(primary.content().seekable() && primary.content().icon() != null
                                && !primary.content().rhythm().isEmpty() && !primary.content().controls().isEmpty(),
                        "Music did not publish a seekable rhythm activity with artwork and playback control");
                press(screen, button(screen, "music_pause"));
                next(Stage.MUSIC_PAUSED, now);
            }
            case MUSIC_PAUSED -> {
                if (!named(primary, "music") || !primary.content().detail().equals(LiveActivityExamples.text("music_paused"))) {
                    return false;
                }
                require(primary.content().rhythm().stream().allMatch(band -> band == 0), "Paused music retained a moving rhythm");
                if (pausedAt == 0) {
                    pausedAt = now;
                    pausedProgress = primary.content().progress();
                }
                require(primary.content().progress() == pausedProgress, "Paused music advanced its playhead");
                if (now - pausedAt < PAUSE_CHECK) return false;
                press(screen, button(screen, "music_resume"));
                next(Stage.MUSIC_RESUMED, now);
            }
            case MUSIC_RESUMED -> {
                if (!named(primary, "music") || primary.content().progress() <= pausedProgress) return false;
                stop(screen);
                press(screen, button(screen, "craft_start"));
                next(Stage.CRAFT_PROGRESS, now);
            }
            case CRAFT_PROGRESS -> {
                if (!named(primary, "craft") || primary.content().progress() == 0) return false;
                require(primary.content().detail().equals(LiveActivityExamples.text("craft_local")), "Crafting used a different backend");
                press(screen, button(screen, "craft_cancel"));
                requireEmpty();
                press(screen, button(screen, "craft_start"));
                next(Stage.CRAFT_COMPLETE, now);
            }
            case CRAFT_COMPLETE -> {
                if (surfaces.diagnostics().activities() != 0 || surfaces.diagnostics().notifications() != 1
                        || primary == null || !primary.id().equals(ExampleUiModule.id("live/notice/craft_done"))) return false;
                require(primary.content().title().equals(LiveActivityExamples.text("craft_done")), "Crafting did not announce completion");
                stop(screen);
                press(screen, button(screen, "machine_start"));
                next(Stage.MACHINE_PROGRESS, now);
            }
            case MACHINE_PROGRESS -> {
                if (!named(primary, "machine") || primary.content().progress() < .125) return false;
                require(!primary.content().detail().translated() && primary.content().detail().value().contains("FE"),
                        "Machine did not report its energy buffer with processing progress");
                press(screen, button(screen, "machine_cancel"));
                requireEmpty();
                press(screen, button(screen, "priority_start"));
                next(Stage.BACKGROUND, now);
            }
            case BACKGROUND -> {
                if (!named(primary, "background")) return false;
                backgroundInstance = primary.instance();
                backgroundProgress = primary.content().progress();
                next(Stage.PREEMPTED, now);
            }
            case PREEMPTED -> {
                if (!named(primary, "foreground")) return false;
                var secondary = view.plan().secondary();
                require(surfaces.diagnostics().activities() == 2 && (named(secondary, "background")
                                || view.plan().waiting().contains(ExampleUiModule.id("live/background"))),
                        "High-priority activity did not keep the background task available");
                next(Stage.RESTORED, now);
            }
            case RESTORED -> {
                if (!named(primary, "background") || !view.primary().visible() || view.transitioning()) return false;
                require(primary.instance() == backgroundInstance && primary.content().progress() > backgroundProgress,
                        "Background task did not resume the same progressing activity");
                press(screen, button(screen, "connection_enable"));
                button(screen, "connection_disable");
                return true;
            }
        }
        return false;
    }

    void verifyReopened(Screen screen) {
        requireEmpty();
        // The module-scoped watcher should remain enabled while the page's activities and reservation are released.
        press(screen, button(screen, "connection_disable"));
        button(screen, "connection_enable");
    }

    private void stop(Screen screen) {
        press(screen, button(screen, "stop"));
        requireEmpty();
    }

    private void requireEmpty() {
        var diagnostics = surfaces.diagnostics();
        require(diagnostics.activities() == 0 && diagnostics.notifications() == 0,
                "Example activities or notifications remained after cancellation");
    }

    private void next(Stage value, long now) {
        stage = value;
        stageStarted = now;
        System.out.println("KERNEL_EXTENSION_LIVE " + value);
    }

    private static Button button(Screen screen, String key) {
        var label = ExampleUiModule.label("live." + key);
        return screen.children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(button -> label.equals(button.getMessage())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing live example button " + key));
    }

    private static boolean named(Snapshot snapshot, String name) {
        return snapshot != null && snapshot.id().equals(ExampleUiModule.id("live/" + name));
    }

    private enum Stage { MUSIC_PLAYING, MUSIC_PAUSED, MUSIC_RESUMED, CRAFT_PROGRESS, CRAFT_COMPLETE,
        MACHINE_PROGRESS, BACKGROUND, PREEMPTED, RESTORED }
}
