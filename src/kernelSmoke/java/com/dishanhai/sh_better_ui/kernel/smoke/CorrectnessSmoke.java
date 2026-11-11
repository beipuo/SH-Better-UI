// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.SpatialIntelligence;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.UICorrectnessEngine;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.UIElement;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.UISnapshot;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.ValidationReport;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.VisualIntelligence;
import com.dishanhai.sh_better_ui.kernel.internal.CorrectnessInspectorScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Native Minecraft fixture, invoked after LiveSurfaceSmoke and before the existing logout/shutdown checks. */
final class CorrectnessSmoke {
    private static final UiId NODE = UiId.parse("sh_better_ui:smoke/correctness/panel");
    private static final UiId IMPOSSIBLE = UiId.parse("sh_better_ui:smoke/correctness/impossible");
    private static final String LABEL = "sh_better_ui.correctness.inspector.title";
    private static final String PREFIX = "sh_better_ui.correctness.inspector.";
    private static final int LIGHT = 0xfff3efe2, DARK = 0xff263642;
    private static final String[] TABS = {"layout", "motion", "visual", "correctness"};
    private static CorrectnessSmoke instance;

    private final ModuleContext module;
    private final UICorrectnessEngine engine;
    private final Screen previous;
    private final int originalWidth, originalHeight, originalGuiScale;
    private final long startedNanos = System.nanoTime();
    private final Probe probe;
    private Stage stage = Stage.START;
    private long stageFrame, stageNanos, beforeResource, beforeResizeVersion, oldGeneration;
    private long nextDiagnostic;
    private int initialGuiWidth, clicks, inspectorTab;
    private CompletableFuture<Void> reload;
    private UISnapshot archived;
    private UICorrectnessEngine.Binding retired;

    private CorrectnessSmoke(ModuleContext module, Minecraft client) {
        this.module = module;
        engine = module.engine().correctness();
        require(engine == module.engine().require(UICorrectnessEngine.KEY), "Correctness service bypassed the kernel registry");
        require(module.engine().capabilities().status(new CapabilityKey(UICorrectnessEngine.KEY.id())) == CapabilityStatus.AVAILABLE,
                "Correctness capability was not published");
        previous = client.screen;
        originalWidth = client.getWindow().getScreenWidth();
        originalHeight = client.getWindow().getScreenHeight();
        originalGuiScale = client.options.guiScale().get();
        probe = new Probe();
    }

    static boolean started() { return instance != null; }

    static boolean advance(ModuleContext module, Minecraft client, KernelClientSmoke.Platform platform, Path output) throws Exception {
        if (instance == null) instance = new CorrectnessSmoke(module, client);
        return instance.advance(client, platform, output);
    }

    private boolean advance(Minecraft client, KernelClientSmoke.Platform platform, Path output) throws Exception {
        long now = System.nanoTime();
        if (now - startedNanos >= 40_000_000_000L) throw new AssertionError("Correctness smoke timed out at " + stage + diagnostic());
        if (probe.primary != null && now - stageNanos > 3_000_000_000L && now > nextDiagnostic) {
            nextDiagnostic = now + 2_000_000_000L;
            var node = probe.primary.committed().orElse(null);
            System.out.println("CORRECTNESS_SMOKE_WAIT " + stage + " node=" + (node == null ? null : node.resolved())
                    + " renderable=" + (node != null && node.renderable())
                    + " raw=" + (node == null ? null : node.background().validity())
                    + " material=" + (node == null ? null : node.materialObservation().validity()));
        }
        long frame = module.engine().scheduler().snapshot().frame();
        if (!client.isWindowActive()) client.setWindowActive(true);
        switch (stage) {
            case START -> {
                Files.createDirectories(output);
                client.options.guiScale().set(1);
                client.resizeDisplay();
                client.setScreen(probe);
                enter(Stage.BASELINE, frame);
            }
            case BASELINE -> {
                var node = ready(true);
                if (node == null) return false;
                var snapshot = engine.snapshot(module, probe.tree);
                verifyPublished(client, snapshot, node);
                verifyPixels(client, node);
                screenshot(client, output.resolve("correctness-layout.png"));
                Files.writeString(output.resolve("correctness-baseline.json"), engine.exportJson(module, probe.tree));
                KernelClientSmoke.move(client, node.interactive().x() + node.interactive().width() / 2,
                        node.interactive().y() + node.interactive().height() / 2);
                platform.flushPointer(client);
                KernelClientSmoke.button(client, GLFW.GLFW_PRESS);
                KernelClientSmoke.button(client, GLFW.GLFW_RELEASE);
                require(clicks == 1, "Presented correctness bounds did not route exactly one native press");
                oldGeneration = probe.tree.generation();
                beforeResizeVersion = snapshot.version();
                initialGuiWidth = client.getWindow().getGuiScaledWidth();
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), originalWidth == 736 ? 768 : 736, 500);
                client.resizeDisplay();
                enter(Stage.RESIZED, frame);
            }
            case RESIZED -> {
                if (client.getWindow().getGuiScaledWidth() == initialGuiWidth) return false;
                var node = ready(false);
                if (node == null || node.version() <= beforeResizeVersion) return false;
                require(probe.tree.generation() == oldGeneration, "Native resize remounted correctness bindings");
                var snapshot = engine.snapshot(module, probe.tree);
                require(snapshot.viewport().width() == client.getWindow().getGuiScaledWidth(), "Viewport and render snapshot used different GUI widths");
                assertSafe(snapshot, node);
                screenshot(client, output.resolve("correctness-resized.png"));
                beforeResizeVersion = snapshot.version();
                client.options.guiScale().set(2);
                client.resizeDisplay();
                enter(Stage.SCALED, frame);
            }
            case SCALED -> {
                if (frame < stageFrame + 2 || client.getWindow().getGuiScale() != 2) return false;
                var node = probe.primary.committed().orElse(null);
                if (node == null || node.version() <= beforeResizeVersion) return false;
                var snapshot = engine.snapshot(module, probe.tree);
                if (node.renderable()) assertSafe(snapshot, node);
                else require(!node.interactiveEnabled() && !probe.primary.hitTest(node.resolved().x(), node.resolved().y()),
                        "Small viewport kept an unsafe invisible input region");
                require(probe.tree.generation() == oldGeneration, "GUI Scale discarded the node lifetime");
                screenshot(client, output.resolve("correctness-scale2.png"));
                client.options.guiScale().set(1);
                client.resizeDisplay();
                enter(Stage.RESTORED, frame);
            }
            case RESTORED -> {
                var node = ready(false);
                if (node == null || client.getWindow().getGuiScale() != 1) return false;
                beforeResource = module.engine().require(UIApis.RESOURCES).version();
                reload = client.reloadResourcePacks();
                enter(Stage.RELOADED, frame);
            }
            case RELOADED -> {
                if (!reload.isDone()) return false;
                reload.join();
                if (module.engine().require(UIApis.RESOURCES).version() <= beforeResource) return false;
                var node = ready(true);
                if (node == null) return false;
                require(node.measurement().resourceVersion() > beforeResource, "Text measurement survived resource reload with an old generation");
                require(node.background().capturedNanos() >= stageNanos, "Old background sample survived resource reload");
                require(node.materialObservation().capturedNanos() >= stageNanos, "Old effective material pixels survived resource reload");
                archived = engine.snapshot(module, probe.tree);
                verifyPublished(client, archived, node);
                Files.writeString(output.resolve("correctness-reloaded.json"), engine.exportJson(module, probe.tree));
                screenshot(client, output.resolve("correctness-reloaded.png"));
                retired = probe.primary;
                module.engine().require(UIApis.RENDER).openInspector(module, probe.tree);
                require(client.screen instanceof CorrectnessInspectorScreen, "Render service did not open the native inspector");
                require(!retired.isActive() && retired.presented().isEmpty(), "Inspector transition retained a closed screen's input presentation");
                inspectorTab = 0;
                enter(Stage.INSPECTOR_SELECT, frame);
            }
            case INSPECTOR_SELECT -> {
                if (frame < stageFrame + 2) return false;
                require(client.screen instanceof CorrectnessInspectorScreen, "Inspector did not survive its first native frame");
                if (!client.screen.getNarrationMessage().getString().contains(NODE.toString())) {
                    KernelClientSmoke.move(client, client.getWindow().getGuiScaledWidth() * .75,
                            client.getWindow().getGuiScaledHeight() - 40);
                    platform.flushPointer(client);
                    KernelClientSmoke.button(client, GLFW.GLFW_PRESS);
                    KernelClientSmoke.button(client, GLFW.GLFW_RELEASE);
                }
                enter(Stage.INSPECTOR, frame);
            }
            case INSPECTOR -> {
                if (frame < stageFrame + 3) return false;
                require(client.screen instanceof CorrectnessInspectorScreen, "Inspector was replaced before preview capture");
                require(client.screen.getNarrationMessage().getString().contains(NODE.toString()), "Inspector did not select the rendered node");
                String tab = TABS[inspectorTab];
                String expected = Component.translatable(PREFIX + "active_tab", Component.translatable(PREFIX + "tab." + tab)).getString();
                require(client.screen.getNarrationMessage().getString().contains(expected), "Native inspector did not select " + tab);
                screenshot(client, output.resolve("correctness-inspector-" + tab + ".png"));
                if (++inspectorTab < TABS.length) {
                    int inset = Math.min(8, Math.max(1, client.getWindow().getGuiScaledWidth() / 12));
                    int innerWidth = client.getWindow().getGuiScaledWidth() - inset * 2;
                    int cell = (innerWidth - 15) / 4;
                    KernelClientSmoke.move(client, inset + inspectorTab * (cell + 5) + cell / 2d, 52);
                    platform.flushPointer(client);
                    KernelClientSmoke.button(client, GLFW.GLFW_PRESS);
                    KernelClientSmoke.button(client, GLFW.GLFW_RELEASE);
                    enter(Stage.INSPECTOR, frame);
                } else {
                    client.screen.onClose();
                    require(client.screen == probe, "Inspector did not return to its original screen");
                    require(probe.getFocused() != null, "Inspector return lost native keyboard focus");
                    enter(Stage.RETURNED, frame);
                }
            }
            case RETURNED -> {
                if (ready(false) == null) return false;
                require(!retired.isActive() && probe.primary.isActive() && probe.primary != retired,
                        "Reopened screen reused a retired correctness binding");
                require(engine.metrics().registeredNodes() >= 2, "Reopened declarations did not register");
                String result = module.engine().platform().info() + " Java " + Runtime.version().feature()
                        + "\nregistry/capability, immutable commit/native receipt, native pointer, observed widget safe area, "
                        + "unsatisfiable hard constraint gate, framebuffer material pixels, real background provenance, "
                        + "native text measurement/localization, resize/GUI Scale, resource reload, frozen inspector four tabs, "
                        + "structured JSON, return focus, old scope/input retirement\n"
                        + "frames=" + frame + " archivedVersion=" + archived.version() + " metrics=" + engine.metrics() + "\n";
                client.setScreen(previous);
                client.options.guiScale().set(originalGuiScale);
                GLFW.glfwSetWindowSize(client.getWindow().getWindow(), originalWidth, originalHeight);
                client.resizeDisplay();
                require(!probe.primary.isActive() && probe.primary.presented().isEmpty(), "Correctness screen removal kept active input");
                Files.writeString(output.resolve("correctness-success.txt"), result);
                System.out.println("CORRECTNESS_SMOKE_OK " + result);
                enter(Stage.DONE, frame);
                return true;
            }
            case DONE -> { return true; }
        }
        return false;
    }

    private UISnapshot.Node ready(boolean requirePixels) {
        if (probe.primary == null) return null;
        var node = probe.primary.committed().orElse(null);
        var painted = probe.primary.presented().orElse(null);
        if (node == null || painted == null || !node.renderable() || painted.version() != node.version()) return null;
        if (requirePixels && (node.background().validity() != VisualIntelligence.Validity.VALID
                || node.materialObservation().validity() != VisualIntelligence.Validity.VALID)) return null;
        return node;
    }

    private void verifyPublished(Minecraft client, UISnapshot snapshot, UISnapshot.Node node) {
        require(node.measurement().measured() && node.measurement().width() > 0 && node.measurement().height() > 0,
                "Correctness did not use native text measurement");
        require(!Component.translatable(LABEL).getString().equals(LABEL), "UI label was rendered as an untranslated key");
        require(node.background().source() == VisualIntelligence.Source.GPU_DOWNSAMPLED_PIXELS
                        && node.materialObservation().stage() == VisualIntelligence.Stage.POST_MATERIAL_PRE_TEXT,
                "Native pixel observations lost stage/source provenance");
        require(node.legibility().valid() && node.legibility().pixelObserved() && !node.legibility().finalCompositeObserved(),
                "Material pixels were not distinguished from glyph/final composite evidence");
        require(snapshot.safeAreas().stream().anyMatch(area -> area.id().toString().startsWith("minecraft:widget/")
                        && area.evidence() == ValidationReport.Evidence.OBSERVED), "Native widget safe area was not observed");
        assertSafe(snapshot, node);
        require(!node.preferred().equals(node.resolved()), "The obstructed preferred position was not resolved");
        var impossible = probe.impossible.committed().orElseThrow();
        require(!impossible.renderable() && !impossible.interactiveEnabled() && probe.impossible.presented().isEmpty(),
                "Unsatisfiable hard dimensions reached native rendering or input");
        require(snapshot.report().unresolvedHard().stream().anyMatch(issue -> issue.nodes().contains(IMPOSSIBLE)),
                "Unsatisfiable dimensions did not retain an explainable hard conflict");
        require(probe.primary.hitTest(node.interactive().x() + node.interactive().width() / 2,
                node.interactive().y() + node.interactive().height() / 2), "Actual presentation was absent from hit testing");
        require(node.resolved().space() == SpatialIntelligence.Space.GUI && snapshot.viewport().width() == client.getWindow().getGuiScaledWidth(),
                "GUI coordinates and framebuffer coordinates were mixed");
    }

    private static void assertSafe(UISnapshot snapshot, UISnapshot.Node node) {
        require(snapshot.viewport().contains(node.resolved()), "Resolved node escaped the GUI viewport");
        for (var area : snapshot.safeAreas()) require(!area.bounds().intersects(node.resolved()),
                "Resolved node intersected reserved area " + area.id() + " " + area.bounds() + " vs " + node.resolved());
    }

    private static void verifyPixels(Minecraft client, UISnapshot.Node node) throws Exception {
        int changed = 0;
        var bounds = node.resolved();
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            for (int row = 1; row <= 3; row++) for (int column = 1; column <= 5; column++) {
                int x = (int) (bounds.x() + bounds.width() * column / 6);
                int y = (int) (bounds.y() + bounds.height() * row / 4);
                int pixel = argb(KernelClientSmoke.pixel(image, client, x, y));
                if (pixel != backgroundAt(x, y)) changed++;
            }
        }
        require(changed >= 3, "Committed material/foreground did not appear in the real Minecraft framebuffer");
    }

    private void enter(Stage next, long frame) {
        stage = next; stageFrame = frame; stageNanos = System.nanoTime();
        nextDiagnostic = stageNanos + 3_000_000_000L;
        System.out.println("CORRECTNESS_SMOKE_STAGE " + next + " frame=" + frame);
    }
    private String diagnostic() {
        return probe.primary == null ? "" : " committed=" + probe.primary.committed() + " report=" + engine.report(module, probe.tree);
    }
    private static void screenshot(Minecraft client, Path path) throws Exception {
        try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) { image.writeToFile(path.toFile()); }
    }
    private static int backgroundAt(int x, int y) { return ((x / 16 + y / 16) & 1) == 0 ? LIGHT : DARK; }
    private static int argb(int abgr) { return abgr & 0xff00ff00 | (abgr & 255) << 16 | (abgr >> 16 & 255); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private final class Probe extends Screen {
        private ScopeHandle tree;
        private UICorrectnessEngine.Binding primary, impossible;
        Probe() { super(Component.translatable(LABEL)); }
        @Override protected void init() {
            if (tree == null || !tree.isOpen()) {
                tree = module.engine().require(UIApis.SCREENS).mount(module, this, UiId.parse("sh_better_ui:smoke/correctness/tree"));
                primary = engine.register(module, tree, UIElement.builder(NODE.toString())
                        .anchor(SpatialIntelligence.Anchor.TOP_CENTER).preferredSize(220, 56).minSize(180, 44).maxSize(300, 80)
                        .priority(80).margin(8).text(LABEL)
                        .interaction(new UIElement.InteractionPolicy(true, true, false, true, LABEL)).build());
                impossible = engine.register(module, tree, UIElement.builder(IMPOSSIBLE.toString())
                        .preferredSize(10000, 10000).minSize(10000, 10000).maxSize(10000, 10000).priority(1)
                        .interactive(true).build());
                module.engine().require(UIApis.INPUT).target(module, tree, 80, event -> event instanceof NativeInput.Pointer pointer
                        && primary.hitTest(pointer.x(), pointer.y()));
                module.engine().events().subscribe(InputService.EVENT, tree, UIEventPhase.TARGET, 0, event -> {
                    if (event.payload() instanceof NativeInput.Pointer pointer) {
                        if (pointer.action() == NativeInput.PointerAction.PRESS) clicks++;
                        event.consume();
                    }
                });
            }
            var obstacle = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> {})
                    .bounds((width - 160) / 2, 8, 160, 24).build());
            setInitialFocus(obstacle);
        }
        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}
        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            for (int y = 0; y < height; y += 16) for (int x = 0; x < width; x += 16) {
                graphics.fill(x, y, Math.min(width, x + 16), Math.min(height, y + 16), backgroundAt(x, y));
            }
            super.render(graphics, mouseX, mouseY, partialTick);
        }
        @Override public boolean isPauseScreen() { return false; }
    }

    private enum Stage { START, BASELINE, RESIZED, SCALED, RESTORED, RELOADED, INSPECTOR_SELECT, INSPECTOR, RETURNED, DONE }
}
