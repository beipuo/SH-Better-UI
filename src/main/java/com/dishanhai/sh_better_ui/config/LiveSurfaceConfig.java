// Author: dishanhai
package com.dishanhai.sh_better_ui.config;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Priority;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceSettings;
import com.dishanhai.sh_better_ui.kernel.api.live.LiveSurfaceSettings.MotionMode;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlParser;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Loads the local client settings snapshot shared by Forge and NeoForge. */
public final class LiveSurfaceConfig {
    public static final String FILE_NAME = "sh_better_ui-live.toml";

    private static final LiveSurfaceSettings DEFAULT = LiveSurfaceSettings.DEFAULT;
    private static final Pattern COLOR = Pattern.compile("[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?");

    private LiveSurfaceConfig() {}

    /** Reads current values from disk, creating a versioned default file when needed. */
    public static LiveSurfaceSettings load(Path configDir) {
        return load(configDir, LoggerFactory.getLogger(LiveSurfaceConfig.class)::warn);
    }

    static LiveSurfaceSettings load(Path configDir, Consumer<String> diagnostic) {
        Path file = configDir.resolve(FILE_NAME);
        try {
            if (Files.notExists(file)) {
                Files.createDirectories(configDir);
                try {
                    Files.writeString(file, defaults(), StandardOpenOption.CREATE_NEW);
                    return DEFAULT;
                } catch (FileAlreadyExistsException ignored) {
                    // Another loader created the file while we were preparing the defaults.
                }
            }
            try (var reader = Files.newBufferedReader(file)) {
                return decode(new TomlParser().parse(reader), diagnostic);
            }
        } catch (IOException | ParsingException | NumberFormatException e) {
            diagnostic.accept("Unable to read " + FILE_NAME + " (" + e.getClass().getSimpleName() + ")");
            return DEFAULT;
        }
    }

    private static LiveSurfaceSettings decode(UnmodifiableConfig config, Consumer<String> diagnostic) {
        var read = new Options(config, diagnostic);
        read.strict = read.flag("kernel.strictValidation", DEFAULT.strictValidation());
        int version = read.integer("version", 0, 0, Integer.MAX_VALUE);
        if (version > LiveSurfaceSettings.VERSION) {
            read.invalid("version", "0 through " + LiveSurfaceSettings.VERSION);
            return DEFAULT;
        }
        var layout = DEFAULT.layout();
        var motion = DEFAULT.motion();
        var theme = DEFAULT.theme();
        var notifications = DEFAULT.notifications();
        var input = DEFAULT.input();
        var performance = DEFAULT.performance();
        String reducedMotionKey = version == 0 && read.raw("motion.reducedMotion") == null
                ? "island.reducedMotion" : "motion.reducedMotion";
        boolean reducedMotion = read.flag(reducedMotionKey, motion.mode() == MotionMode.REDUCED);
        boolean animate = read.flag("motion.enabled", motion.mode() != MotionMode.NONE);
        MotionMode motionMode = !animate ? MotionMode.NONE : reducedMotion ? MotionMode.REDUCED : MotionMode.FULL;

        return new LiveSurfaceSettings(read.flag("kernel.enabled", DEFAULT.kernelEnabled()),
                read.flag("kernel.diagnostics", DEFAULT.diagnostics()), read.strict,
                new LiveSurfaceSettings.Layout(read.flag("island.enabled", layout.enabled()),
                        read.enumeration("island.anchor", layout.anchor()), read.decimal("island.scale", layout.scale(), .75, 1.5),
                        read.integer("island.maxActivities", layout.maxActivities(), 1, 512),
                        read.flag("island.compact.enabled", layout.compact()), read.flag("island.expanded.enabled", layout.expanded()),
                        read.flag("island.split.enabled", layout.split())),
                new LiveSurfaceSettings.Motion(motionMode, read.decimal("motion.spring.response", motion.response(), .08, 2),
                        read.decimal("motion.spring.damping", motion.damping(), .3, 2)),
                new LiveSurfaceSettings.Theme(read.enumeration("theme.mode", theme.mode()),
                        read.color("theme.background", theme.background()), read.color("theme.accent", theme.accent()),
                        read.flag("shadow.enabled", theme.shadow()), read.enumeration("shadow.quality", theme.quality())),
                new LiveSurfaceSettings.Notifications(read.flag("notifications.enabled", notifications.enabled()),
                        read.flag("notifications.deduplication", notifications.deduplicate()),
                        read.flag("notifications.quiet", notifications.quiet()),
                        read.integer("notifications.maxNotifications", notifications.maxNotifications(), 1, 128),
                        read.integer("notifications.defaultDuration", notifications.defaultDurationMs(), 600, 30_000), read.priorities()),
                new LiveSurfaceSettings.Input(read.integer("input.longPressThreshold", input.longPressMs(), 150, 2_000),
                        read.integer("input.doubleClickThreshold", input.doubleClickMs(), 100, 700),
                        read.decimal("input.dragThreshold", input.dragThreshold(), 1, 32)),
                new LiveSurfaceSettings.Performance(read.flag("performance.adaptive", performance.adaptive()),
                        read.decimal("performance.frameBudget", performance.frameBudgetMs(), .1, 12)));
    }

    private static String defaults() {
        var layout = DEFAULT.layout();
        var motion = DEFAULT.motion();
        var theme = DEFAULT.theme();
        var notifications = DEFAULT.notifications();
        var input = DEFAULT.input();
        var performance = DEFAULT.performance();
        return String.format(Locale.ROOT, """
                version = %d

                [kernel]
                enabled = %s
                diagnostics = %s
                strictValidation = %s

                [island]
                enabled = %s
                anchor = "%s"
                scale = %s
                maxActivities = %d
                compact.enabled = %s
                expanded.enabled = %s
                split.enabled = %s

                [island.motion]
                enabled = %s
                reducedMotion = %s
                spring.response = %s
                spring.damping = %s

                [island.theme]
                mode = "%s"
                background = "#%08x"
                accent = "#%08x"

                [island.shadow]
                enabled = %s
                quality = "%s"

                [island.notifications]
                enabled = %s
                deduplication = %s
                quiet = %s
                maxNotifications = %d
                defaultDuration = %d

                [island.input]
                longPressThreshold = %d
                doubleClickThreshold = %d
                dragThreshold = %s

                [island.performance]
                adaptive = %s
                frameBudget = %s

                [activities.priority]
                """, LiveSurfaceSettings.VERSION, DEFAULT.kernelEnabled(), DEFAULT.diagnostics(), DEFAULT.strictValidation(),
                layout.enabled(), layout.anchor(), layout.scale(), layout.maxActivities(), layout.compact(), layout.expanded(), layout.split(),
                motion.mode() != MotionMode.NONE, motion.mode() == MotionMode.REDUCED, motion.response(), motion.damping(),
                theme.mode(), theme.background(), theme.accent(), theme.shadow(), theme.quality(), notifications.enabled(),
                notifications.deduplicate(), notifications.quiet(), notifications.maxNotifications(), notifications.defaultDurationMs(),
                input.longPressMs(), input.doubleClickMs(), input.dragThreshold(), performance.adaptive(), performance.frameBudgetMs());
    }

    private static final class Options {
        private final UnmodifiableConfig config;
        private final Consumer<String> diagnostic;
        private boolean strict = DEFAULT.strictValidation();

        private Options(UnmodifiableConfig config, Consumer<String> diagnostic) {
            this.config = config;
            this.diagnostic = diagnostic;
        }

        private boolean flag(String key, boolean fallback) {
            Object value = raw(key);
            if (value == null) return fallback;
            if (value instanceof Boolean flag) return flag;
            invalid(key, "a boolean");
            return fallback;
        }

        private int integer(String key, int fallback, int min, int max) {
            Object value = raw(key);
            if (value == null) return fallback;
            if (value instanceof Integer || value instanceof Long) {
                long number = ((Number) value).longValue();
                if (number >= min && number <= max) return (int) number;
            }
            invalid(key, "an integer from " + min + " through " + max);
            return fallback;
        }

        private double decimal(String key, double fallback, double min, double max) {
            Object value = raw(key);
            if (value == null) return fallback;
            if (value instanceof Number number) {
                double decimal = number.doubleValue();
                if (decimal >= min && decimal <= max) return decimal;
            }
            invalid(key, "a number from " + min + " through " + max);
            return fallback;
        }

        private <E extends Enum<E>> E enumeration(String key, E fallback) {
            Object value = raw(key);
            if (value == null) return fallback;
            E parsed = parseEnum(value, fallback.getDeclaringClass());
            if (parsed != null) return parsed;
            invalid(key, "a supported " + fallback.getDeclaringClass().getSimpleName());
            return fallback;
        }

        private int color(String key, int fallback) {
            Object value = raw(key);
            if (value == null) return fallback;
            Integer color = parseColor(value);
            if (color != null && (color >>> 24) >= 0xc0) return color;
            invalid(key, "a #RRGGBB or #AARRGGBB color with alpha at least C0");
            return fallback;
        }

        private Object raw(String key) {
            if (!key.startsWith("kernel.") && !key.startsWith("island.") && !key.equals("version")) {
                Object canonical = config.getRaw("island." + key);
                if (canonical != null) return canonical;
                if (key.equals("notifications.maxNotifications")) {
                    Object alternative = config.getRaw("island.maxNotifications");
                    if (alternative != null) return alternative;
                }
            }
            return config.getRaw(key);
        }

        private Map<UiId, Priority> priorities() {
            String key = "activities.priority";
            Object value = config.getRaw(key);
            if (value == null) return Map.of();
            if (!(value instanceof UnmodifiableConfig table)) {
                invalid(key, "a table of activity priorities");
                return Map.of();
            }

            var priorities = new LinkedHashMap<UiId, Priority>();
            boolean hasInvalid = false;
            var entries = table.entrySet().stream().sorted(Comparator.comparing(UnmodifiableConfig.Entry::getKey)).toList();
            for (var entry : entries) {
                Priority priority = parseEnum(entry.getValue(), Priority.class);
                if (priority == null || priority == Priority.CRITICAL) {
                    hasInvalid = true;
                    continue;
                }
                try {
                    UiId id = UiId.parse(entry.getKey());
                    if (priorities.size() == 128) {
                        invalid(key, "at most 128 priority overrides");
                        break;
                    }
                    priorities.put(id, priority);
                } catch (IllegalArgumentException e) {
                    hasInvalid = true;
                }
            }
            if (hasInvalid) invalid(key, "namespaced activity IDs and BACKGROUND, LOW, NORMAL or HIGH priorities");
            return priorities;
        }

        private void invalid(String key, String expected) {
            if (strict) diagnostic.accept("Invalid " + FILE_NAME + " option " + key + " (expected " + expected + ")");
        }
    }

    private static <E extends Enum<E>> E parseEnum(Object value, Class<E> type) {
        if (!(value instanceof String text)) return null;
        try {
            return Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Integer parseColor(Object value) {
        if (value instanceof String text) {
            String hex = text.startsWith("#") ? text.substring(1) : text;
            if (!COLOR.matcher(hex).matches()) return null;
            int color = (int) Long.parseLong(hex, 16);
            return hex.length() == 6 ? color | 0xff000000 : color;
        }
        if (value instanceof Integer || value instanceof Long) {
            long color = ((Number) value).longValue();
            if (color < Integer.MIN_VALUE || color > 0xffffffffL) return null;
            return color >= 0 && color <= 0xffffff ? (int) color | 0xff000000 : (int) color;
        }
        return null;
    }
}
