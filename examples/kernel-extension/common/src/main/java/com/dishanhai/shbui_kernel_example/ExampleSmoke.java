// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.kernel.api.CapabilityRequest;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ModuleLifecycle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.VersionRange;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Optional packaged-client check. Enable it only in the example's isolated run directory. */
final class ExampleSmoke {
    private static final String OUTPUT = System.getProperty("shbui.example.smoke");
    private static ExampleUiModule module;
    private static Screen title;
    private static Stage stage = Stage.START;
    private static long startedAt;
    private static long closedGeneration;
    private static boolean queued;
    private static boolean done;
    private static boolean passed;

    private enum Stage { START, ACT, CAPTURE_AND_CLOSE, REOPEN }

    private ExampleSmoke() {}

    static void started(ExampleUiModule instance) {
        if (OUTPUT != null) module = instance;
    }

    static void frame() {
        if (OUTPUT == null || done || queued) return;
        var client = Minecraft.getInstance();
        // The render listener may be ordered before kernel cleanup. Run edits on the next client task turn.
        queued = true;
        client.tell(() -> {
            queued = false;
            advance(client);
        });
    }

    private static void advance(Minecraft client) {
        if (startedAt == 0) startedAt = System.nanoTime();
        try {
            require(System.nanoTime() - startedAt < 120_000_000_000L, "Timed out at " + stage);
            if (client.getOverlay() != null) return;
            switch (stage) {
                case START -> {
                    if (!(client.screen instanceof TitleScreen)) return;
                    verifyAndOpen(client);
                    stage = Stage.ACT;
                }
                case ACT -> {
                    act(client);
                    stage = Stage.CAPTURE_AND_CLOSE;
                }
                case CAPTURE_AND_CLOSE -> {
                    screenshotAndReopen(client);
                    stage = Stage.REOPEN;
                }
                case REOPEN -> {
                    verifyReopenAndClose(client);
                    passed = true;
                    done = true;
                    client.stop();
                }
            }
        } catch (IOException | RuntimeException | LinkageError | AssertionError failure) {
            System.err.println("KERNEL_EXTENSION_SMOKE_FAILED " + failure);
            failure.printStackTrace();
            done = true;
            client.stop();
        }
    }

    private static void verifyAndOpen(Minecraft client) {
        require(module != null && UnifiedUI.isAvailable(), "The loader did not discover the extension");
        var ui = UnifiedUI.get();
        int java = ui.platform().info() == ModuleDescriptor.Platform.FORGE_1_20_1 ? 17 : 21;
        require(Runtime.version().feature() == java, "Unexpected client Java version");
        require(ExampleUiModule.class.getModule() != UnifiedUI.class.getModule(), "The extension and library share a module");
        String exampleJar = jarLocation(ExampleUiModule.class);
        String libraryJar = jarLocation(UnifiedUI.class);
        require(!exampleJar.equals(libraryJar), "The extension and library share a JAR");
        require(ui.lifecycle().snapshot().modules().get(ExampleUiModule.ID) == ModuleLifecycle.RUNNING,
                "The discovered module is not running");
        var capabilities = ui.capabilities().resolve(new CapabilityRequest(
                Map.of(ExampleUiModule.CONTROLS, VersionRange.ANY),
                Map.of(UIApis.SYSTEM_SCREEN_READER, VersionRange.ANY)));
        require(capabilities.requiredMissing().isEmpty() && capabilities.optionalMissing().contains(UIApis.SYSTEM_SCREEN_READER),
                "The custom capability or optional fallback is unavailable");
        require(ui.find(CounterService.KEY).isPresent() && ui.find(UIApis.THEME).isPresent(),
                "The custom or built-in service is unavailable");

        title = client.screen;
        press(title, ExampleClient.titleEntry(title).orElseThrow());
    }

    private static void act(Minecraft client) {
        var page = module.page();
        require(page != null && client.screen == page.screen && page.tree.isOpen() && page.counter.isOpen(),
                "The extension did not mount the composed screen");
        require(!page.systemReader(), "The example did not select the Minecraft narration fallback");
        require(UnifiedUI.get().configs().get(ExampleUiModule.STEP) == 1 && page.step() == 3,
                "The component configuration was not isolated");
        press(page.screen, page.increase);
        require(page.count == 3 && page.undo.active
                && page.increase.getMessage().equals(ExampleUiModule.label("increase", 3, 3)), "Increment failed");
        press(page.screen, page.undo);
        require(page.count == 0 && !page.undo.active, "Undo failed");
        press(page.screen, page.increase);
    }

    private static void screenshotAndReopen(Minecraft client) throws IOException {
        var page = module.page();
        var legacyContext = page.screen.context();
        require(client.screen == page.screen && page.count == 3, "The counter screen was not rendered");
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(Path.of(OUTPUT, "screen.png").toFile());
        }
        require(page.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0), "Escape did not close the screen");
        require(client.screen == title && module.page() == null && legacyContext.isClosed()
                && !page.tree.isOpen() && !page.counter.isOpen(), "Closing did not release the screen scope");
        require(client.screen.getFocused() == ExampleClient.titleEntry(title).orElseThrow(), "Parent focus was not restored");

        closedGeneration = page.tree.generation();
        press(title, ExampleClient.titleEntry(title).orElseThrow());
    }

    private static void verifyReopenAndClose(Minecraft client) throws IOException {
        var reopened = module.page();
        require(reopened != null && client.screen == reopened.screen && reopened.counter.isOpen()
                && reopened.count == 0 && reopened.tree.generation() != closedGeneration, "The screen reused closed state");
        reopened.screen.onClose();
        require(!reopened.counter.isOpen() && UnifiedUI.isAvailable(), "Closing a screen changed the engine lifetime");
        Files.writeString(Path.of(OUTPUT, "success.txt"),
                "Named extension and library JARs, custom service, optional fallback, scoped config, keyboard, undo, and screen cleanup passed.\n"
                        + "extension=" + jarLocation(ExampleUiModule.class) + "\nlibrary=" + jarLocation(UnifiedUI.class) + "\n");
    }

    static void shutdown() {
        if (OUTPUT == null || !passed || module == null || !module.stopped() || UnifiedUI.isAvailable()) return;
        try {
            Files.writeString(Path.of(OUTPUT, "stopped.txt"), "Extension stopped and facade withdrawn.\n");
        } catch (IOException failure) {
            throw new IllegalStateException("Could not record extension shutdown", failure);
        }
    }

    private static void press(Screen screen, Button button) {
        screen.setFocused(button);
        require(screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0), "Enter did not activate a focused button");
    }

    private static String jarLocation(Class<?> type) {
        require(type.getModule().isNamed(), "The class did not come from a named loader module");
        var source = type.getProtectionDomain().getCodeSource();
        require(source != null && source.getLocation() != null && source.getLocation().toString().contains(".jar"),
                "Missing packaged JAR code source for " + type.getName());
        return source.getLocation().toString();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
