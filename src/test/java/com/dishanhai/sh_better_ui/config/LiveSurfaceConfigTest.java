// Author: dishanhai
package com.dishanhai.sh_better_ui.config;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Priority;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceSettings;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceSettings.Anchor;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceSettings.MotionMode;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceSettings.ThemeMode;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle.Quality;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LiveSurfaceConfigTest {
    @TempDir Path directory;

    @Test
    void writesVersionedDefaultsOnlyWhenTheFileIsAbsent() throws IOException {
        Path configDir = directory.resolve("config");
        var diagnostics = new ArrayList<String>();
        assertEquals(LiveSurfaceSettings.DEFAULT, LiveSurfaceConfig.load(configDir, diagnostics::add));
        Path file = configDir.resolve(LiveSurfaceConfig.FILE_NAME);
        String generated = Files.readString(file);
        assertTrue(generated.contains("version = " + LiveSurfaceSettings.VERSION));
        assertEquals(LiveSurfaceSettings.DEFAULT, LiveSurfaceConfig.load(configDir, diagnostics::add));
        assertTrue(diagnostics.isEmpty());
        assertEquals(generated, Files.readString(file));
    }

    @Test
    void parsesAllSectionsAndReloadsEditedValues() throws IOException {
        write("""
                version = 1
                [kernel]
                enabled = false
                diagnostics = true
                strictValidation = false
                [island]
                enabled = false
                anchor = "bottom-center"
                scale = 1.25
                maxActivities = 24
                compact.enabled = false
                expanded.enabled = false
                split.enabled = false
                [motion]
                enabled = true
                reducedMotion = true
                spring.response = 1.2
                spring.damping = 1.8
                [theme]
                mode = "custom"
                background = 0xff111b25
                accent = "#75feca"
                [shadow]
                enabled = false
                quality = "medium"
                [notifications]
                enabled = false
                deduplication = false
                quiet = true
                maxNotifications = 7
                defaultDuration = 6200
                [input]
                longPressThreshold = 700
                doubleClickThreshold = 230
                dragThreshold = 8
                [performance]
                adaptive = false
                frameBudget = 2.5
                [activities.priority]
                "example:jobs.done" = "high"
                "example:diagnostics" = "background"
                "example:critical" = "critical"
                "invalid" = "high"
                "example:unknown" = "extreme"
                """
        );

        var diagnostics = new ArrayList<String>();
        var config = LiveSurfaceConfig.load(directory, diagnostics::add);
        assertFalse(config.kernelEnabled());
        assertTrue(config.diagnostics());
        assertFalse(config.strictValidation());
        assertTrue(diagnostics.isEmpty());
        assertEquals(new LiveSurfaceSettings.Layout(false, Anchor.BOTTOM_CENTER, 1.25, 24, false, false, false), config.layout());
        assertEquals(new LiveSurfaceSettings.Motion(MotionMode.REDUCED, 1.2, 1.8), config.motion());
        assertEquals(new LiveSurfaceSettings.Theme(ThemeMode.CUSTOM, 0xff111b25, 0xff75feca, false, Quality.MEDIUM), config.theme());
        assertEquals(new LiveSurfaceSettings.Notifications(false, false, true, 7, 6200,
                Map.of(UiId.parse("example:jobs.done"), Priority.HIGH,
                        UiId.parse("example:diagnostics"), Priority.BACKGROUND)), config.notifications());
        assertEquals(new LiveSurfaceSettings.Input(700, 230, 8), config.input());
        assertEquals(new LiveSurfaceSettings.Performance(false, 2.5), config.performance());

        write("[motion]\nenabled = false\nreducedMotion = true\n");
        assertEquals(MotionMode.NONE, LiveSurfaceConfig.load(directory, diagnostics::add).motion().mode());
    }

    @Test
    void readsLegacyReducedMotionWithoutRewritingTheFile() throws IOException {
        for (String header : new String[]{"", "version = 0\n"}) {
            String legacy = header + "# legacy comment\n[island]\nreducedMotion = true\n";
            Path file = write(legacy);
            assertEquals(MotionMode.REDUCED, LiveSurfaceConfig.load(directory, ignored -> {}).motion().mode());
            assertEquals(legacy, Files.readString(file));
        }
        write("version = 0\n[island]\nreducedMotion = true\n[motion]\nreducedMotion = false\n");
        assertEquals(MotionMode.FULL, LiveSurfaceConfig.load(directory, ignored -> {}).motion().mode());
    }

    @Test
    void canonicalIslandSectionsTakePrecedenceOverLegacySections() throws IOException {
        write("""
                version = 1
                [island]
                maxNotifications = 9
                [island.motion]
                reducedMotion = true
                [island.theme]
                mode = "kernel"
                [motion]
                reducedMotion = false
                [theme]
                mode = "dark"
                """);
        var config = LiveSurfaceConfig.load(directory, ignored -> {});
        assertEquals(MotionMode.REDUCED, config.motion().mode());
        assertEquals(ThemeMode.KERNEL, config.theme().mode());
        assertEquals(9, config.notifications().maxNotifications());
    }

    @Test
    void invalidFieldsFallBackIndependentlyAndStrictModeIdentifiesThem() throws IOException {
        write("""
                version = 1
                [kernel]
                strictValidation = true
                enabled = "false"
                [island]
                scale = 2.0
                anchor = "left"
                maxActivities = 22
                [motion.spring]
                response = 0.001
                damping = 1.35
                [theme]
                background = "#10112233"
                accent = "#ffaacc"
                [notifications]
                maxNotifications = 0
                defaultDuration = 2500
                [input]
                longPressThreshold = 450.5
                [performance]
                frameBudget = -0.1
                [activities.priority]
                "example:unknown" = "CRITICAL"
                """
        );

        var diagnostics = new ArrayList<String>();
        var config = LiveSurfaceConfig.load(directory, diagnostics::add);
        var defaults = LiveSurfaceSettings.DEFAULT;
        assertEquals(defaults.kernelEnabled(), config.kernelEnabled());
        assertEquals(defaults.layout().scale(), config.layout().scale());
        assertEquals(defaults.layout().anchor(), config.layout().anchor());
        assertEquals(22, config.layout().maxActivities());
        assertEquals(defaults.motion().response(), config.motion().response());
        assertEquals(1.35, config.motion().damping());
        assertEquals(defaults.theme().background(), config.theme().background());
        assertEquals(0xffffaacc, config.theme().accent());
        assertEquals(defaults.notifications().maxNotifications(), config.notifications().maxNotifications());
        assertEquals(2500, config.notifications().defaultDurationMs());
        assertTrue(config.notifications().priorities().isEmpty());
        assertEquals(defaults.input(), config.input());
        assertEquals(defaults.performance(), config.performance());
        for (String key : new String[]{"kernel.enabled", "island.scale", "island.anchor", "motion.spring.response",
                "theme.background", "notifications.maxNotifications", "input.longPressThreshold",
                "performance.frameBudget", "activities.priority"}) {
            assertTrue(diagnostics.stream().anyMatch(message -> message.contains(key)), key);
        }
    }

    @Test
    void preservesMalformedAndFutureVersionFiles() throws IOException {
        for (String contents : new String[]{"[island\nenabled = ", "version = 999\n[island]\nenabled = false\n"}) {
            Path file = write(contents);
            var diagnostics = new ArrayList<String>();
            assertEquals(LiveSurfaceSettings.DEFAULT, LiveSurfaceConfig.load(directory, diagnostics::add));
            assertFalse(diagnostics.isEmpty());
            assertEquals(contents, Files.readString(file));
        }
    }

    private Path write(String contents) throws IOException {
        return Files.writeString(directory.resolve(LiveSurfaceConfig.FILE_NAME), contents);
    }
}
