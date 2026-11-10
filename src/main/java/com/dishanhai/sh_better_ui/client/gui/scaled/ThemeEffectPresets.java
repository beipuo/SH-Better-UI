package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.ElectricLogoProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.DotMatrixProfile.Gradient;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.ThemeOverrides;
import com.dishanhai.sh_better_ui.kernel.api.AppearanceService;
import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** 山海：将预设应用为普通覆写，保留预设未涉及的字段及原有配置格式。 */
public final class ThemeEffectPresets {
    private static final String PRESET_KEY = "sh_better_ui.theme_editor.preset.";
    private static final List<Preset> DOT_MATRIX = List.of(
            dotMatrix("solid", Gradient.NONE),
            dotMatrix("rainbow", Gradient.RAINBOW),
            dotMatrix("reverse", Gradient.REVERSE_RAINBOW),
            dotMatrix("aurora", Gradient.AURORA));
    private static final List<Preset> ELECTRIC_LOGO = Arrays.stream(ElectricLogoProfile.Preset.values())
            .map(preset -> electric(id(preset), preset.profile())).toList();
    private static final List<Preset> PATTERN_WAVES = Arrays.stream(PatternWavesProfile.Preset.values())
            .map(preset -> new Preset(id(preset), "sh_better_ui.panel.developer.gallery.pattern_waves." + id(preset),
                    ThemeEffects.fields(preset.profile()))).toList();

    private ThemeEffectPresets() {}

    static List<Preset> forGroup(String group) {
        return switch (group) {
            case "dotMatrix" -> DOT_MATRIX;
            case "electricLogo" -> ELECTRIC_LOGO;
            case "patternWaves" -> PATTERN_WAVES;
            default -> List.of();
        };
    }

    public static List<AppearanceService.Preset> describe(String group) {
        return forGroup(group).stream().map(preset -> new AppearanceService.Preset(preset.name(), preset.titleKey()))
                .toList();
    }

    public static JsonObject apply(ClientUiSettings settings, String group, String name, JsonObject inherited,
                                   JsonObject local) {
        var preset = forGroup(group).stream().filter(candidate -> candidate.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown effect preset: " + group + "/" + name));
        return preset.applyValidated(settings, group, inherited, local);
    }

    private static String id(Enum<?> preset) {
        return preset.name().toLowerCase(Locale.ROOT);
    }

    private static Preset dotMatrix(String name, Gradient gradient) {
        var fields = ThemeEffects.fields(gradient);
        fields.remove("flow");
        if (gradient.stops().isEmpty()) fields.remove("periodMs");
        var patch = new JsonObject();
        patch.add("gradient", fields);
        return new Preset(name, PRESET_KEY + name, patch);
    }

    private static Preset electric(String name, ElectricLogoProfile profile) {
        var patch = ThemeEffects.fields(profile);
        patch.remove("interaction");
        patch.getAsJsonObject("lines").remove("scale");
        return new Preset(name, PRESET_KEY + name, patch);
    }

    record Preset(String name, String titleKey, JsonObject patch) {
        JsonObject applyValidated(ClientUiSettings settings, String group, JsonObject inherited, JsonObject local) {
            var draft = apply(group, local);
            ThemeEffects.resolveEditor(settings, group, inherited, draft);
            return draft;
        }

        JsonObject apply(String group, JsonObject local) {
            return ThemeOverrides.EMPTY.withGroup(group, local)
                    .overlay(ThemeOverrides.EMPTY.withGroup(group, patch)).group(group);
        }

        boolean matches(JsonObject resolved) {
            return matches(patch, resolved);
        }

        private static boolean matches(JsonObject patch, JsonObject resolved) {
            for (var entry : patch.entrySet()) {
                var actual = resolved.get(entry.getKey());
                var expected = entry.getValue();
                if (expected.isJsonObject() && actual != null && actual.isJsonObject()) {
                    if (!matches(expected.getAsJsonObject(), actual.getAsJsonObject())) return false;
                } else if (!expected.equals(actual)) {
                    return false;
                }
            }
            return true;
        }
    }
}
