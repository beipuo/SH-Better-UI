// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.live;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService.Priority;
import com.dishanhai.sh_better_ui.spatial.SpatialStyle;

import java.util.Map;
import java.util.Objects;

/** Validated client preferences. Sizes and thresholds are in Minecraft GUI units unless otherwise specified. */
public record LiveSurfaceSettings(boolean kernelEnabled, boolean diagnostics, boolean strictValidation,
                                  Layout layout, Motion motion, Theme theme, Notifications notifications,
                                  Input input, Performance performance) {
    public static final int VERSION = 1;
    public static final LiveSurfaceSettings DEFAULT = new LiveSurfaceSettings(true, false, true,
            new Layout(true, Anchor.TOP_CENTER, 1, 128, true, true, true),
            new Motion(MotionMode.FULL, .20, 1.10),
            new Theme(ThemeMode.DARK, 0xff080808, 0xff78d9d0, true, SpatialStyle.Quality.AUTO),
            new Notifications(true, true, false, 48, 4_000, Map.of()),
            new Input(450, 300, 4), new Performance(true, 1.0));

    public LiveSurfaceSettings {
        Objects.requireNonNull(layout);
        Objects.requireNonNull(motion);
        Objects.requireNonNull(theme);
        Objects.requireNonNull(notifications);
        Objects.requireNonNull(input);
        Objects.requireNonNull(performance);
    }

    /** Preferred edge of the viewport. The placement service can move away from the anchor to avoid overlap. */
    public enum Anchor { TOP_CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_CENTER }
    /** Full geometry spring, reduced opacity-only animation, or immediate presentation. */
    public enum MotionMode { FULL, REDUCED, NONE }
    /** Default palette, current kernel theme or custom colors. High contrast is applied afterward. */
    public enum ThemeMode { DARK, KERNEL, CUSTOM }

    /** Native layout and simultaneous activity limits. */
    public record Layout(boolean enabled, Anchor anchor, double scale, int maxActivities,
                         boolean compact, boolean expanded, boolean split) {
        public Layout {
            Objects.requireNonNull(anchor);
            range(scale, .75, 1.5, "scale");
            range(maxActivities, 1, 512, "maxActivities");
        }
    }

    /** Geometry spring response in seconds and damping ratio. */
    public record Motion(MotionMode mode, double response, double damping) {
        public Motion {
            Objects.requireNonNull(mode);
            range(response, .08, 2, "spring response");
            range(damping, .3, 2, "spring damping");
        }
    }

    /** Straight ARGB palette overrides and maximum requested shadow quality. */
    public record Theme(ThemeMode mode, int background, int accent, boolean shadow, SpatialStyle.Quality quality) {
        public Theme {
            Objects.requireNonNull(mode);
            Objects.requireNonNull(quality);
            if ((background >>> 24) < 0xc0 || (accent >>> 24) < 0xc0) {
                throw new IllegalArgumentException("Live surface colors must be readable on changing HUD backgrounds");
            }
        }
    }

    /** Queue policy and local priority preferences. Overrides cannot grant system priority. */
    public record Notifications(boolean enabled, boolean deduplicate, boolean quiet, int maxNotifications,
                                int defaultDurationMs, Map<UiId, Priority> priorities) {
        public Notifications {
            range(maxNotifications, 1, 128, "maxNotifications");
            range(defaultDurationMs, 600, 30_000, "defaultDuration");
            priorities = Map.copyOf(priorities);
            if (priorities.size() > 128 || priorities.containsValue(Priority.CRITICAL)) {
                throw new IllegalArgumentException("Invalid live surface priority override");
            }
        }
    }

    /** Timing in milliseconds and drag threshold in GUI units. */
    public record Input(int longPressMs, int doubleClickMs, double dragThreshold) {
        public Input {
            range(longPressMs, 150, 2_000, "longPressThreshold");
            range(doubleClickMs, 100, 700, "doubleClickThreshold");
            range(dragThreshold, 1, 32, "dragThreshold");
        }
    }

    /** Local CPU budget for decorative rendering work. */
    public record Performance(boolean adaptive, double frameBudgetMs) {
        public Performance { range(frameBudgetMs, .1, 12, "frameBudget"); }
    }

    private static void range(double value, double min, double max, String field) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException("Invalid live surface " + field);
        }
    }
}
