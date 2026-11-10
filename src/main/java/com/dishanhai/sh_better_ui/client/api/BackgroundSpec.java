// Author: dishanhai
package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiMouseState;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.theme.ThemeId;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** A page decoration with independent state for every host and an optional editable record profile. */
public record BackgroundSpec(String id, String titleKey, String settingsGroup, Record defaultProfile, Factory factory) {
    public BackgroundSpec {
        Objects.requireNonNull(id);
        if (id.contains(":")) ThemeId.require(id);
        else if (!id.matches("[a-z][a-zA-Z0-9]{0,63}") || id.equals("none")) throw new IllegalArgumentException(id);
        if (Objects.requireNonNull(titleKey).isBlank()) throw new IllegalArgumentException("Missing background title key");
        if (defaultProfile != null) ThemeId.require(settingsGroup);
        Objects.requireNonNull(factory);
    }

    /** A new effect registers its own profile under the same ID. Use ThemeEffects.profile(id, defaults) to read it. */
    public BackgroundSpec(String id, String titleKey, Record profile, Factory factory) {
        this(ThemeId.require(id), titleKey, profile == null ? null : id, profile, factory);
    }

    @FunctionalInterface
    public interface Factory {
        /** The optional host logo may be used by effects which draw an image. */
        Renderer create(ResourceLocation logo);
    }

    @FunctionalInterface
    public interface Renderer extends AutoCloseable {
        /** Coordinates are in the host's GUI space. Mouse may be null while input is blocked. */
        void draw(GuiGraphics graphics, Font font, Bounds area, UiMouseState mouse, double x, double y,
                  long now, UiPreferences preferences);

        default void click(Bounds area, double x, double y, long now) {}

        @Override
        default void close() {}
    }
}
