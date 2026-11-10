// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.panel.ControlPanelScreen;
import com.dishanhai.sh_better_ui.config.DynamicThemeSettings;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.dishanhai.sh_better_ui.client.gui.scaled.DynamicThemeSmoke.require;

/** Verifies a genuine control-panel Apply followed by a separate process reading the TOML. */
final class DynamicThemeSettingsSmoke {
    private static final String PREFIX = "sh_better_ui.panel.";
    private static final DynamicThemeSettings EXPECTED = new DynamicThemeSettings("sh_better_ui:aurora", 200, true);
    private static final String TAB = PREFIX + "section.dynamic_theme";
    private static long deadline;
    private static long editedAt;
    private static int stage;
    private static TitleScreen title;
    private static ControlPanelScreen panel;

    private DynamicThemeSettingsSmoke() {}

    static boolean frame(Minecraft client, Path output, String mode, long now) throws IOException {
        if (deadline == 0) deadline = now + 40_000;
        require(now < deadline, "Timed out in dynamic theme settings stage " + stage + " (" + mode + ")");
        if (stage == 0) {
            if (!(client.screen instanceof TitleScreen menu)) return false;
            DynamicThemeSmoke.checkKernel();
            if (mode.equals("read")) {
                assertPersisted();
                return true;
            }
            title = menu;
            var settings = ShBetterUiApi.settings();
            var current = settings.current();
            if (!current.dynamicTheme().equals(DynamicThemeSettings.DEFAULT)) {
                require(settings.save(current.withDynamicTheme(DynamicThemeSettings.DEFAULT)), "Could not reset the test config");
            }
            client.options.guiScale().set(1);
            client.resizeDisplay();
            openPanel(client);
            stage++;
        } else if (stage == 1) {
            var input = input();
            if (input == null) {
                panel.keyPressed(GLFW.GLFW_KEY_END, 0, 0);
                return false;
            }
            stageTheme("invalid");
            require(!button("apply").active, "Invalid theme ID changed the draft");
            stageTheme(EXPECTED.fixedTheme());
            var speed = panel.children().stream().filter(UiSlider.class::isInstance).map(UiSlider.class::cast)
                    .findFirst().orElseThrow();
            panel.setFocused(speed);
            require(speed.mouseClicked(speed.getX() + 4 + (speed.getWidth() - 8) * 0.25, speed.getY() + 4, 0),
                    "Transition speed slider did not accept pointer input");
            speed.mouseReleased(speed.getX(), speed.getY(), 0);
            require(speed.value() == EXPECTED.animationPercent(), "Unexpected transition speed");
            button("dynamic_theme.low_performance").onPress();
            editedAt = now;
            stage++;
        } else if (stage == 2) {
            if (now - editedAt < 1_100) return false;
            Files.createDirectories(output);
            try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                image.writeToFile(output.resolve("preferences.png"));
            }
            require(button("apply").active, "Valid settings did not create a draft");
            button("apply").onPress();
            require(ShBetterUiApi.settings().current().dynamicTheme().equals(EXPECTED), "Apply did not save the draft");
            stageTheme("sh_better_ui:light");
            require(button("apply").active, "The second edit did not create a draft");
            client.setScreen(title);
            stage++;
        } else {
            if (!ShBetterUiApi.themes().preferences().lowPerformance()) return false;
            assertPersisted();
            return true;
        }
        return false;
    }

    private static void openPanel(Minecraft client) {
        panel = ShBetterUiApi.settings().controlPanel(title, new KeyMapping("key.sh_better_ui.control_panel",
                GLFW.GLFW_KEY_F8, "key.categories.sh_better_ui"));
        client.setScreen(panel);
        var bar = panel.children().stream().filter(UiScrollableSectionBar.class::isInstance)
                .map(UiScrollableSectionBar.class::cast)
                .filter(candidate -> candidate.buttons().stream().anyMatch(button -> key(button.getMessage()).equals(TAB)))
                .findFirst().orElseThrow();
        panel.setFocused(bar);
    }

    private static void stageTheme(String id) {
        input().setValue(id);
        button("dynamic_theme.stage").onPress();
    }

    private static EditBox input() {
        return panel.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast)
                .filter(box -> key(box.getMessage()).equals(PREFIX + "dynamic_theme.id")).findFirst().orElse(null);
    }

    private static UiButton button(String suffix) {
        return panel.children().stream().filter(UiButton.class::isInstance).map(UiButton.class::cast)
                .filter(button -> key(button.getMessage()).equals(PREFIX + suffix)).findFirst().orElseThrow();
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
    }

    private static void assertPersisted() {
        require(ShBetterUiApi.settings().current().dynamicTheme().equals(EXPECTED), "Saved settings mismatch");
        require(UnifiedUI.get().configs().get(UIApis.CLIENT_SETTINGS).dynamicTheme().equals(EXPECTED),
                "Kernel User Global settings do not match the saved settings");
        var preferences = ShBetterUiApi.themes().preferences();
        require(EXPECTED.fixedTheme().equals(preferences.fixedThemeId()) && preferences.animationSpeed() == 2
                && preferences.lowPerformance(), "Runtime preferences do not match the saved settings");
        require(ShBetterUiApi.themes().snapshot().themeIds().contains(EXPECTED.fixedTheme()), "Fixed theme was not displayed");
    }
}
