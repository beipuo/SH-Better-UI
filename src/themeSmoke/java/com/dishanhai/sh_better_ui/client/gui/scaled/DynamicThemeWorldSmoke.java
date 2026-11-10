// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.theme.MinecraftThemes;
import com.dishanhai.sh_better_ui.theme.BuiltinThemes;
import com.dishanhai.sh_better_ui.theme.accessibility.ThemePreferences;
import com.dishanhai.sh_better_ui.theme.context.ThemeCondition;
import com.dishanhai.sh_better_ui.theme.context.ThemeRule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.Set;

import static com.dishanhai.sh_better_ui.theme.context.ContextKeys.*;

/** Exercises the real integrated-server to client environment path in a disposable flat world. */
final class DynamicThemeWorldSmoke {
    private static final String RULE = "sh_better_ui:smoke/night";
    private final Hooks platform;
    private final Screen title;
    private Phase phase = Phase.FIRST_WORLD;
    private long deadline;

    DynamicThemeWorldSmoke(Screen title, Hooks platform, long now) {
        this.platform = platform;
        this.title = title;
        var themes = MinecraftThemes.manager();
        themes.preferences(ThemePreferences.DEFAULT);
        themes.registerRule(ThemeRule.builder(RULE).theme("sh_better_ui:aurora")
                .when(ThemeCondition.of(Set.of(DIMENSION, DAY_TIME), context ->
                        context.get(DIMENSION).equals("minecraft:overworld") && context.get(DAY_TIME) >= 13_000))
                .priority(50).build());
        deadline = now + 90_000;
    }

    void start(Minecraft client) {
        platform.create(client, title, "theme-world-" + Long.toUnsignedString(System.nanoTime()));
    }

    boolean frame(Minecraft client, long now) {
        var context = MinecraftThemes.environment();
        var theme = MinecraftThemes.manager().snapshot();
        String dimension = context.get(DIMENSION);
        if (now > deadline) {
            throw new AssertionError("World phase timed out: " + phase + ", dimension=" + dimension
                    + ", biome=" + context.get(BIOME) + ", time=" + context.get(DAY_TIME)
                    + ", weather=" + context.get(WEATHER) + ", themes=" + theme.themeIds());
        }
        switch (phase) {
            case FIRST_WORLD -> {
                if (!"minecraft:overworld".equals(dimension) || client.player == null || context.get(BIOME).isEmpty()) break;
                commands(client, "time set midnight", "weather thunder",
                        "fillbiome ~-8 -64 ~-8 ~8 -40 ~8 minecraft:forest");
                advance(Phase.NIGHT_AND_BIOME, now);
            }
            case NIGHT_AND_BIOME -> {
                if (context.get(DAY_TIME) < 13_000 || !"minecraft:forest".equals(context.get(BIOME))
                        || !"thunder".equals(context.get(WEATHER))
                        || !theme.themeIds().contains("sh_better_ui:aurora")) break;
                commands(client, "time set day");
                advance(Phase.DAY, now);
            }
            case DAY -> {
                if (context.get(DAY_TIME) >= 13_000 || theme.themeIds().contains("sh_better_ui:aurora")) break;
                commands(client, "execute in minecraft:the_nether run tp @s 0 90 0");
                advance(Phase.NETHER, now);
            }
            case NETHER -> {
                if (!"minecraft:the_nether".equals(dimension) || !theme.themeIds().contains(BuiltinThemes.NETHER)) break;
                commands(client, "execute in minecraft:the_end run tp @s 0 80 0");
                advance(Phase.END, now);
            }
            case END -> {
                if (!"minecraft:the_end".equals(dimension) || !theme.themeIds().contains(BuiltinThemes.END)) break;
                platform.leave(client, title);
                advance(Phase.BETWEEN_WORLDS, now);
            }
            case BETWEEN_WORLDS -> {
                if (context.get(WORLD_PRESENT) || client.level != null || !theme.themeIds().equals(java.util.List.of(BuiltinThemes.DARK))) break;
                platform.create(client, title, "theme-world-" + Long.toUnsignedString(System.nanoTime()));
                advance(Phase.SECOND_WORLD, now);
            }
            case SECOND_WORLD -> {
                if (!context.get(WORLD_PRESENT) || !"minecraft:overworld".equals(dimension) || client.player == null) break;
                platform.leave(client, title);
                advance(Phase.LEFT, now);
            }
            case LEFT -> {
                if (context.get(WORLD_PRESENT) || client.level != null) break;
                MinecraftThemes.manager().removeRule(RULE);
                System.out.println("DYNAMIC_THEME_WORLD_OK world-switch biome weather night/day nether end disconnect");
                return true;
            }
        }
        return false;
    }

    private void advance(Phase next, long now) {
        System.out.println("DYNAMIC_THEME_WORLD " + phase + " -> " + next);
        phase = next;
        deadline = now + 90_000;
    }

    private static void commands(Minecraft client, String... commands) {
        var server = client.getSingleplayerServer();
        if (server == null) throw new IllegalStateException("No test integrated server");
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().get(0);
            var source = player.createCommandSourceStack().withPermission(4);
            for (String command : commands) server.getCommands().performPrefixedCommand(source, command);
        });
    }

    static LevelSettings settings() {
        return new LevelSettings("Dynamic theme smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
    }

    static WorldOptions options() { return new WorldOptions(17, false, false); }

    static WorldDimensions flat(RegistryAccess registries) {
        return registries.registryOrThrow(Registries.WORLD_PRESET)
                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
    }

    interface Hooks {
        void create(Minecraft client, Screen parent, String name);
        void leave(Minecraft client, Screen title);
    }

    private enum Phase { FIRST_WORLD, NIGHT_AND_BIOME, DAY, NETHER, END, BETWEEN_WORLDS, SECOND_WORLD, LEFT }
}
