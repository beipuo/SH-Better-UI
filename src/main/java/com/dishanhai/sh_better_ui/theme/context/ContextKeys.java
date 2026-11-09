// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.context;

import java.util.Map;

/** Shared environment fields; integrations may define additional namespaced keys. */
public final class ContextKeys {
    public static final ContextKey<Boolean> WORLD_PRESENT = server("world_present", Boolean.class, false);
    public static final ContextKey<Integer> BIOME_COLOR = server("biome_color", Integer.class, 0xff6699ff);
    public static final ContextKey<Integer> SKY_COLOR = client("sky_color", Integer.class, 0xff6699ff);
    public static final ContextKey<String> AUDIO_PLAYBACK = client("audio_playback", String.class, "stopped");
    public static final ContextKey<Double> AUDIO_BASS = client("audio_bass", Double.class, 0.0);
    public static final ContextKey<Double> AUDIO_MID = client("audio_mid", Double.class, 0.0);
    public static final ContextKey<Double> AUDIO_TREBLE = client("audio_treble", Double.class, 0.0);
    public static final ContextKey<Integer> ALBUM_COLOR = client("album_color", Integer.class, 0);
    /** Current dimension identifier, empty when no world is available. */
    public static final ContextKey<String> DIMENSION = server("dimension", String.class, "");
    /** Current biome identifier, empty when unavailable. */
    public static final ContextKey<String> BIOME = server("biome", String.class, "");
    /** Adapter-defined weather name; defaults to clear. */
    public static final ContextKey<String> WEATHER = server("weather", String.class, "clear");
    /** World day time in ticks. */
    public static final ContextKey<Long> DAY_TIME = server("day_time", Long.class, 0L);
    /** Ambient light level from the world adapter. */
    public static final ContextKey<Integer> LIGHT_LEVEL = server("light_level", Integer.class, 15);
    /** Current integer GUI scale. */
    public static final ContextKey<Integer> GUI_SCALE = client("gui_scale", Integer.class, 1);
    /** Window width in adapter-defined pixels. */
    public static final ContextKey<Integer> WINDOW_WIDTH = client("window_width", Integer.class, 0);
    /** Window height in adapter-defined pixels. */
    public static final ContextKey<Integer> WINDOW_HEIGHT = client("window_height", Integer.class, 0);
    /** Current language code. */
    public static final ContextKey<String> LANGUAGE = client("language", String.class, "en_us");
    /** Adapter-defined player status, empty when no player is available. */
    public static final ContextKey<String> PLAYER_STATUS = server("player_status", String.class, "");
    /** Resource reload generation. */
    public static final ContextKey<Long> RELOAD_VERSION = client("reload_version", Long.class, 0L);
    /** Whether the user requests reduced motion. */
    public static final ContextKey<Boolean> REDUCED_MOTION = client("reduced_motion", Boolean.class, false);
    /** Whether the user requests high contrast. */
    public static final ContextKey<Boolean> HIGH_CONTRAST = client("high_contrast", Boolean.class, false);
    /** Extensible preferences containing immutable scalar values or frozen containers. */
    @SuppressWarnings("rawtypes")
    public static final ContextKey<Map> USER_PREFERENCES = client("user_preferences", Map.class, Map.of());
    /** Optional external audio level, zero when unavailable. */
    public static final ContextKey<Double> AUDIO_LEVEL = new ContextKey<>("sh_better_ui:audio_level", Double.class, 0.0, ContextKey.Source.EXTERNAL);

    private ContextKeys() { }
    private static <T> ContextKey<T> client(String id, Class<T> type, T fallback) {
        return new ContextKey<>("sh_better_ui:" + id, type, fallback, ContextKey.Source.CLIENT);
    }
    private static <T> ContextKey<T> server(String id, Class<T> type, T fallback) {
        return new ContextKey<>("sh_better_ui:" + id, type, fallback, ContextKey.Source.SERVER);
    }
}
