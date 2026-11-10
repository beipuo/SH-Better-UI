// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiSlider;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassService;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialCatalog;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static com.dishanhai.sh_better_ui.kernel.smoke.KernelClientSmoke.require;

/** Drives the normal material route and native keyboard controls in both loader clients. */
final class MaterialCatalogSmoke {
    private static final String ID = "sh_better_ui:liquid_glass";
    private static final String CONSUMER_TITLE = "shbui_catalog_consumer.title";
    private static final String KEYS = "sh_better_ui.panel.developer.gallery.material.";
    private static final UiPreferences REDUCED_MOTION = new UiPreferences(false, true, true);
    private static final int[] KEYFRAMES_MS = {800, 1600, 2400};

    private enum Stage { OPEN, DRAWN, KEYFRAMES, RELOADED, REOPENED, CONSUMER }

    private final long started = System.nanoTime();
    private Stage stage = Stage.OPEN;
    private Screen parent;
    private Screen preview;
    private int previousScale;
    private int previousWidth;
    private int previousHeight;
    private int defaultSliderValue;
    private int editedSliderValue;
    private int keyframe;
    private long opticalBefore;
    private long resourceBefore;
    private long reloadStarted;
    private long openStarted;
    private boolean browserPresetImported;
    private CompletableFuture<Void> reload;
    private final JsonObject performance = new JsonObject();

    boolean advance(Minecraft client, KernelClientSmoke.Platform game, Path output) throws Exception {
        var engine = UnifiedUI.get();
        var optics = engine.glass().diagnostics();
        require(System.nanoTime() - started < 25_000_000_000L,
                "Material catalog smoke timed out at " + stage + " " + optics);
        switch (stage) {
            case OPEN -> {
                parent = client.screen;
                previousScale = client.options.guiScale().get();
                previousWidth = client.getWindow().getScreenWidth();
                previousHeight = client.getWindow().getScreenHeight();
                client.options.guiScale().set(1);
                // The gallery reserves 127 GUI pixels above/below its canvas at the default font size.
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), 800, 511);
                client.resizeDisplay();
                var catalog = engine.require(UIApis.MATERIAL_CATALOG);
                require(catalog.find(ID).orElseThrow() == catalog.find("liquid_glass").orElseThrow(),
                        "Material alias did not resolve the stable catalog entry");
                measureLookup(catalog);
                opticalBefore = optics.opticalDraws();
                openStarted = System.nanoTime();
                open(client, ID);
                stage = Stage.DRAWN;
            }
            case DRAWN -> {
                if (optics.opticalDraws() <= opticalBefore) return false;
                performance.addProperty("openToFirstOpticalWallMillis", (System.nanoTime() - openStarted) / 1_000_000d);
                require(!widget("play").active, "Reduced motion started continuous playback");
                var frame = (UiSlider) widget("frame");
                require(frame.value() == 0, "Reduced motion did not start at frame zero");
                press(client, widget("step"));
                require(frame.value() == 1, "Native single-step did not advance one frame");
                press(client, widget("time_reset"));
                require(frame.value() == 0, "Native time reset did not rewind the scene");
                captureKeyframe(client, output, 0);
                performance.add("baselineOptics", optics(optics));
                seekKeyframe(client, game, optics.opticalDraws());
                stage = Stage.KEYFRAMES;
            }
            case KEYFRAMES -> {
                if (optics.opticalDraws() <= opticalBefore) return false;
                captureKeyframe(client, output, KEYFRAMES_MS[keyframe]);
                keyframe++;
                if (keyframe < KEYFRAMES_MS.length) {
                    seekKeyframe(client, game, optics.opticalDraws());
                    return false;
                }
                press(client, widget("time_reset"));
                press(client, widget("section.playback"));
                var slider = (UiSlider) parameter();
                defaultSliderValue = slider.value();
                key(client, GLFW.GLFW_KEY_TAB);
                preview.setFocused(slider);
                key(client, GLFW.GLFW_KEY_RIGHT);
                if (slider.value() == defaultSliderValue) {
                    key(client, GLFW.GLFW_KEY_ENTER);
                    key(client, GLFW.GLFW_KEY_RIGHT);
                }
                editedSliderValue = slider.value();
                require(editedSliderValue > defaultSliderValue, "Native keyboard did not edit the parameter draft");
                press(client, widget("compare_off"));
                client.options.guiScale().set(2);
                client.resizeDisplay();
                require(client.screen == preview && ((UiSlider) parameter()).value() == editedSliderValue,
                        "GUI scale lost the active material draft");
                resourceBefore = engine.require(UIApis.RESOURCES).version();
                opticalBefore = engine.glass().diagnostics().opticalDraws();
                reloadStarted = System.nanoTime();
                reload = client.reloadResourcePacks();
                stage = Stage.RELOADED;
            }
            case RELOADED -> {
                if (!reload.isDone()) return false;
                reload.join();
                if (!performance.has("reloadWallMillis")) {
                    performance.addProperty("reloadWallMillis", (System.nanoTime() - reloadStarted) / 1_000_000d);
                }
                require(engine.require(UIApis.RESOURCES).version() > resourceBefore, "Resource reload did not publish a generation");
                if (optics.opticalDraws() < opticalBefore + 2) return false;
                require(client.screen == preview && ((UiSlider) parameter()).value() == editedSliderValue,
                        "Resource reload lost the active material draft");
                screenshot(client, output.resolve("material-catalog-liquid-glass-reloaded.png"));
                performance.add("reloadedOptics", optics(optics));
                var runtime = Runtime.getRuntime();
                performance.addProperty("processJavaHeapUsedBytes", runtime.totalMemory() - runtime.freeMemory());
                key(client, GLFW.GLFW_KEY_ESCAPE);
                require(client.screen == parent, "Fullscreen material preview did not return to its parent");
                opticalBefore = optics.opticalDraws();
                open(client, "liquid_glass");
                stage = Stage.REOPENED;
            }
            case REOPENED -> {
                if (optics.opticalDraws() <= opticalBefore) return false;
                press(client, widget("section.playback"));
                require(((UiSlider) parameter()).value() == defaultSliderValue,
                        "A fresh preview reused the discarded draft");
                key(client, GLFW.GLFW_KEY_ESCAPE);
                require(client.screen == parent, "Reopened material preview did not close");
                client.options.guiScale().set(previousScale);
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), previousWidth, previousHeight);
                client.resizeDisplay();
                performance.addProperty("smokePhaseWallMillis", (System.nanoTime() - started) / 1_000_000d);
                Files.writeString(output.resolve("material-catalog-performance.json"), performance.toString());
                if (Boolean.getBoolean("shb.kernel.smoke.materialConsumer")) {
                    var catalog = engine.require(UIApis.MATERIAL_CATALOG);
                    browserPresetImported = importBrowserPreset(catalog, output.resolve("consumer-preset.json"));
                    opticalBefore = engine.glass().diagnostics().opticalDraws();
                    openConsumer(client);
                    stage = Stage.CONSUMER;
                    return false;
                }
                return true;
            }
            case CONSUMER -> {
                if (optics.opticalDraws() <= opticalBefore) return false;
                require(client.screen == preview, "Consumer preview left the active screen before rendering");
                screenshot(client, output.resolve("material-catalog-consumer.png"));
                key(client, GLFW.GLFW_KEY_ESCAPE);
                require(client.screen == parent, "Consumer preview did not return to the title screen");
                Files.writeString(output.resolve("material-catalog-consumer.txt"),
                        "Consumer title button opened its UiScreen, rendered optical glass and returned to title\n"
                        + "Optical draws: " + opticalBefore + " -> " + optics.opticalDraws() + "\n"
                        + "Browser preset imported into native catalog: " + browserPresetImported + "\n");
                return true;
            }
        }
        return false;
    }

    private void open(Minecraft client, String id) {
        preview = ShBetterUiApi.settings().developerMaterialFullscreen(parent, id, REDUCED_MOTION, () -> null);
        require(preview.getTitle().getContents() instanceof TranslatableContents title
                        && title.getKey().equals("sh_better_ui.panel.developer.gallery.sample.liquid_glass"),
                "Material route did not reach the Liquid Glass PreviewSpec");
        client.setScreen(preview);
    }

    private void openConsumer(Minecraft client) {
        require(parent instanceof TitleScreen && client.screen == parent, "Consumer smoke requires the actual title screen");
        var button = translatedWidget(parent, CONSUMER_TITLE);
        require(button.active, "Material consumer title button was disabled");
        parent.setFocused(button);
        key(client, GLFW.GLFW_KEY_ENTER);
        preview = client.screen;
        require(preview instanceof UiScreen && preview.getTitle().getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals(CONSUMER_TITLE),
                "Material consumer button did not open its own UiScreen");
    }

    private static boolean importBrowserPreset(MaterialCatalog catalog, Path file) throws Exception {
        if (!Files.isRegularFile(file)) return false;
        var imported = catalog.importPreset(Files.readString(file));
        require(ID.equals(imported.materialId()) && imported.parameters().get("refraction") instanceof Number refraction
                        && refraction.doubleValue() == 8d,
                "Browser preset did not import into the expected material and refraction value");
        return true;
    }

    private void seekKeyframe(Minecraft client, KernelClientSmoke.Platform game, long opticalDraws) throws Exception {
        var slider = (UiSlider) widget("frame");
        int timeMs = KEYFRAMES_MS[keyframe];
        int frame = timeMs * 60 / 1000;
        int x = slider.getX() + 4 + Math.round((slider.getWidth() - 8) * (timeMs / 2400f));
        KernelClientSmoke.move(client, x, slider.getY() + slider.getHeight() / 2);
        game.flushPointer(client);
        KernelClientSmoke.button(client, GLFW.GLFW_PRESS);
        KernelClientSmoke.button(client, GLFW.GLFW_RELEASE);
        require(slider.value() == frame, "Fixture seek selected frame " + slider.value() + " instead of " + frame);
        KernelClientSmoke.move(client, 1, 1);
        game.flushPointer(client);
        opticalBefore = opticalDraws;
    }

    private AbstractWidget parameter() {
        return preview.children().stream().filter(UiSlider.class::isInstance).map(UiSlider.class::cast)
                .filter(widget -> widget.visible).findFirst().orElseThrow();
    }

    private AbstractWidget widget(String key) {
        return translatedWidget(KEYS + key);
    }

    private AbstractWidget translatedWidget(String key) {
        return translatedWidget(preview, key);
    }

    private static AbstractWidget translatedWidget(Screen screen, String key) {
        return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                .filter(widget -> widget.visible && widget.getMessage().getContents() instanceof TranslatableContents contents
                        && contents.getKey().equals(key)).findFirst().orElseThrow(() -> new AssertionError("Missing control " + key));
    }

    private void press(Minecraft client, AbstractWidget widget) {
        require(widget.active, "Expected active material control " + widget.getMessage().getString());
        preview.setFocused(widget);
        key(client, GLFW.GLFW_KEY_ENTER);
    }

    private static void key(Minecraft client, int key) {
        KernelClientSmoke.key(client, key, GLFW.GLFW_PRESS);
        KernelClientSmoke.key(client, key, GLFW.GLFW_RELEASE);
    }

    private static void screenshot(Minecraft client, Path path) throws Exception {
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            image.writeToFile(path.toFile());
        }
    }

    private void measureLookup(MaterialCatalog catalog) {
        int batchSize = 10_000;
        var expected = catalog.find(ID).orElseThrow();
        var samples = new long[5];
        int hits = 0;
        for (int i = -2; i < samples.length; i++) {
            long start = System.nanoTime();
            for (int j = 0; j < batchSize; j++) {
                if (catalog.find((j & 1) == 0 ? ID : "liquid_glass").orElse(null) == expected) hits++;
            }
            if (i >= 0) samples[i] = System.nanoTime() - start;
        }
        require(hits == batchSize * (samples.length + 2), "Material lookup benchmark returned an unexpected entry");
        Arrays.sort(samples);
        long median = samples[samples.length / 2];
        performance.addProperty("queryBatchSize", batchSize);
        performance.addProperty("queryWarmupBatches", 2);
        performance.addProperty("queryMeasuredBatches", samples.length);
        performance.addProperty("queryMedianBatchMillis", median / 1_000_000d);
        performance.addProperty("queryMedianNanosPerLookup", median / (double) batchSize);
    }

    private static JsonObject optics(GlassService.Diagnostics diagnostics) {
        var result = new JsonObject();
        result.addProperty("passesWithCapture", diagnostics.passesWithCapture());
        result.addProperty("opticalDraws", diagnostics.opticalDraws());
        result.addProperty("fallbackDraws", diagnostics.fallbackDraws());
        result.addProperty("captureBytes", diagnostics.captureBytes());
        result.addProperty("shaderReady", diagnostics.shaderReady());
        return result;
    }

    private void captureKeyframe(Minecraft client, Path output, int timeMs) throws Exception {
        var first = widget("section.playback");
        var last = widget("target");
        var back = translatedWidget("gui.back");
        int canvasX = first.getX();
        int canvasY = back.getY() + back.getHeight() + 9;
        int canvasWidth = last.getX() + last.getWidth() - canvasX;
        int canvasHeight = first.getY() - canvasY - client.font.lineHeight - 6;
        float scale = Math.min(canvasWidth / 640f, canvasHeight / 384f);
        int width = Math.round(640 * scale);
        int height = Math.round(384 * scale);
        int x = canvasX + (canvasWidth - width) / 2;
        int y = canvasY + (canvasHeight - height) / 2;
        double guiScale = client.getWindow().getGuiScale();
        require(guiScale == 1 && width == 640 && height == 384,
                "Unexpected native material fixture geometry " + width + "x" + height + " @" + guiScale);
        String filename = "material-catalog-liquid-glass" + (timeMs == 0 ? "" : "-" + timeMs);
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget());
             var crop = new NativeImage(width, height, false)) {
            if (timeMs == 0) image.writeToFile(output.resolve(filename + ".png").toFile());
            for (int row = 0; row < height; row++) {
                for (int column = 0; column < width; column++) {
                    crop.setPixelRGBA(column, row, image.getPixelRGBA(x + column, y + row));
                }
            }
            crop.writeToFile(output.resolve(filename + "-crop.png").toFile());
        }

        var catalog = UnifiedUI.get().require(UIApis.MATERIAL_CATALOG);
        var entry = catalog.find(ID).orElseThrow();
        var evidence = new JsonObject();
        evidence.addProperty("sceneId", "liquid-glass");
        evidence.addProperty("timeMs", timeMs);
        evidence.addProperty("guiScale", guiScale);
        var bounds = new JsonObject();
        bounds.addProperty("x", x);
        bounds.addProperty("y", y);
        bounds.addProperty("width", width);
        bounds.addProperty("height", height);
        evidence.add("crop", bounds);
        evidence.add("preset", JsonParser.parseString(catalog.exportPreset(ID, entry.defaultValues())));
        Files.writeString(output.resolve(filename + "-scene.json"), evidence.toString());
    }
}
