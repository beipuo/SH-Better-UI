// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.smoke;

import com.dishanhai.sh_better_ui.client.gui.scaled.InterruptibleAnimation;
import com.dishanhai.sh_better_ui.client.gui.scaled.Spring;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.kernel.api.*;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class KernelClientSmoke implements UIModule {
    private static final UiId ID = id("kernel_smoke");
    private static final UiId POINTER = id("smoke/pointer");
    private static final UiId FOCUS = id("smoke/focus");
    private static final UiId ANIMATED = id("smoke/animated");
    private static final int BACKGROUND = 0xff151515;
    private static final int MARKER = 0xff22e622;
    private static KernelClientSmoke instance;
    private static long started;
    private static boolean done;

    private ModuleContext module;
    private Stage stage = Stage.START;
    private Screen title;
    private ProbeScreen probe;
    private CompletableFuture<Void> reload;
    private long resourceVersion;
    private long initialMisses;
    private long sessionFrames;
    private ScopeHandle worldScope;
    private AnimationService.Track animation;
    private TaskHandle screenDraw;
    private TaskHandle hudDraw;
    private double animated;
    private boolean stopped;
    private int captures;
    private int drags;
    private int releases;
    private int cancels;
    private int moves;
    private int viewportChanges;
    private Screen viewportReplacement;
    private boolean failDrag;
    private final List<NativeInput.KeyAction> keys = new ArrayList<>();
    private final StringBuilder chars = new StringBuilder();
    private NativeInput.Pointer wheel;

    interface Platform {
        void join(Minecraft client, Screen parent, String name);
        void leave(Minecraft client, Screen title);
        boolean optionalPresent();
        default void flushPointer(Minecraft client) {}
    }

    @Override public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id(ID).version("1.0.0")
                .claimResource(new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.POINTER_CAPTURE, POINTER))
                .claimResource(new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.KEYBOARD_FOCUS, FOCUS))
                .claimResource(new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.ANIMATION_PROPERTY, ANIMATED))
                .requestPermission(UIPermission.CAPTURE_INPUT).requestPermission(UIPermission.BLOCK_VANILLA_INPUT)
                .requestPermission(UIPermission.REGISTER_RENDER_PASS).build();
    }

    @Override public void register(ModuleContext context) {}

    @Override public void start(ModuleContext context) {
        module = context;
        instance = this;
    }

    @Override public void stop(ModuleContext context) { stopped = true; }

    static void frame(Platform platform) {
        var output = System.getProperty("shb.kernel.smoke");
        if (output == null || done) return;
        var client = Minecraft.getInstance();
        if (started == 0) started = System.nanoTime();
        try {
            require(System.nanoTime() - started < 120_000_000_000L, "Timed out at "
                    + (instance == null ? "discovery" : instance.stage));
            if (client.getOverlay() != null) return;
            if (instance == null) {
                require(!(client.screen instanceof TitleScreen), "Loader did not discover the kernel fixture");
                return;
            }
            instance.advance(client, platform, Path.of(output));
        } catch (Exception | AssertionError failure) {
            System.err.println("KERNEL_SMOKE_FAILED " + failure);
            failure.printStackTrace();
            done = true;
            client.stop();
        }
    }

    static void shutdown() {
        var output = System.getProperty("shb.kernel.smoke");
        if (output == null || instance == null || !done || !instance.stopped || UnifiedUI.isAvailable()) return;
        try {
            Files.writeString(Path.of(output, "stopped.txt"), "module stopped and facade withdrawn\n");
        } catch (Exception failure) {
            throw new IllegalStateException("Could not record kernel shutdown", failure);
        }
    }

    private void advance(Minecraft client, Platform game, Path output) throws Exception {
        var engine = UnifiedUI.get();
        switch (stage) {
            case START -> {
                if (!(client.screen instanceof TitleScreen)) return;
                var info = engine.platform().info();
                int java = info == ModuleDescriptor.Platform.FORGE_1_20_1 ? 17 : 21;
                require(Runtime.version().feature() == java, "Wrong client JDK");
                require(game.optionalPresent() == Boolean.getBoolean("shb.kernel.smoke.optional"),
                        "Optional mod load state differs from the requested variant");
                require(engine.capabilities().supports(UIApis.NATIVE_RENDER), "Render capability unavailable");
                engine.require(UIApis.THEME).snapshot();
                engine.require(UIApis.ACCESSIBILITY).preferences(null);
                title = client.screen;
                client.setWindowActive(true);
                key(client, GLFW.GLFW_KEY_TAB, GLFW.GLFW_PRESS);
                key(client, GLFW.GLFW_KEY_TAB, GLFW.GLFW_RELEASE);
                require(title.getFocused() != null, "Vanilla title did not handle Tab");
                probe = new ProbeScreen();
                client.setScreen(probe);
                var themes = engine.require(UIApis.THEME);
                var scoped = themes.scope(module, probe.tree);
                var spacing = themes.snapshot().resolve(ThemeTokens.SPACING_SM);
                scoped.configure(tokens -> tokens.override(ThemeTokens.SPACING_SM, spacing + 2));
                require(scoped.resolve(ThemeTokens.SPACING_SM) == spacing + 2
                        && themes.snapshot().resolve(ThemeTokens.SPACING_SM).equals(spacing),
                        "Screen theme override escaped its scope");
                nativeInputs(client, game, info);
                animation = engine.require(UIApis.ANIMATION).bind(module, probe.tree, ANIMATED,
                        Spring.PANEL, 0, () -> false, value -> animated = value);
                animation.play(1, InterruptibleAnimation.Policy.RETARGET, 0);
                screenDraw = engine.require(UIApis.RENDER).submit(module, probe.tree, RenderService.Surface.SCREEN,
                        RenderService.Layer.OVERLAY, 0, context -> {
                            require(context.frame().surface() == RenderService.Surface.SCREEN, "Wrong draw surface");
                            context.clip(12, 60, 32, 80, () -> context.fill(8, 56, 36, 84, MARKER));
                            context.fill(36, 66, 40, 74, MARKER);
                        });
                stage = Stage.DRAW;
            }
            case DRAW -> {
                require(screenDraw.status() == TaskHandle.Status.SUCCEEDED, "Native screen pass did not run");
                try (var image = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
                    require(pixel(image, client, 19, 70) == MARKER, "Framebuffer did not contain the render pass");
                    require(pixel(image, client, 10, 70) == BACKGROUND, "Render clip leaked outside its bounds");
                    require(pixel(image, client, 37, 70) == MARKER, "Render clip was not restored");
                    image.writeToFile(output.resolve("screen.png").toFile());
                }
                var resource = engine.require(UIApis.RESOURCES);
                resourceVersion = resource.version();
                var assets = resource.alignmentAssets(module, probe.tree, client.getResourceManager());
                require(assets.texture(texture(), 1).isEmpty(), "Test texture unexpectedly present before reload");
                require(assets.texture(texture(), 1).isEmpty() && assets.stats().hits() == 1,
                        "Test cache did not reuse a missing result");
                probe.assets = assets;
                initialMisses = assets.stats().misses();
                installPack(client, output);
                reload = client.reloadResourcePacks();
                stage = Stage.RELOAD;
            }
            case RELOAD -> {
                if (!reload.isDone()) return;
                reload.join();
                require(engine.require(UIApis.RESOURCES).version() > resourceVersion, "Resource generation did not advance");
                require(probe.assets.texture(texture(), 1).isPresent() && probe.assets.stats().misses() > initialMisses,
                        "Native resource reload kept stale texture cache");
                require(animated > 0 && animation.value() > 0, "Frame-clock animation did not advance");
                var pending = module.engine().scheduler().submit(UITask.ui(probe.tree, UIPhase.STATE_MUTATION,
                        UIPriority.NORMAL, () -> { throw new AssertionError("Closed Screen task ran"); })
                        .delayed(60_000_000_000L));
                client.setScreen(title);
                require(!probe.tree.isOpen() && !animation.isActive() && pending.status() == TaskHandle.Status.CANCELLED,
                        "Native Screen removal failed to cancel scoped work");
                for (int i = 0; i < 6; i++) {
                    client.setScreen(probe);
                    var reopened = probe.tree;
                    require(reopened.isOpen(), "Screen did not remount");
                    client.setScreen(title);
                    require(!reopened.isOpen() && !probe.pointerTarget.isActive(), "Screen resources survived remount");
                }
                client.setScreen(probe);
                var input = engine.require(UIApis.INPUT);
                input.target(module, probe.tree, 25,
                        value -> value instanceof NativeInput.Key key && key.key() == GLFW.GLFW_KEY_L);
                module.engine().events().subscribe(InputService.EVENT, probe.tree, UIEventPhase.TARGET, 0, event -> {
                    if (event.payload() instanceof NativeInput.Key key && key.action() == NativeInput.KeyAction.PRESS) {
                        event.consume();
                        game.join(client, title, "kernel-smoke-" + System.nanoTime());
                    }
                });
                stage = Stage.JOIN;
                // Process the native key between frames. The input callback may synchronously render a loading Screen.
                client.tell(() -> key(client, GLFW.GLFW_KEY_L, GLFW.GLFW_PRESS));
            }
            case JOIN -> {
                if (client.level == null || client.player == null || client.screen != null) return;
                require(engine.platform().hasClientSession(), "Native login did not open the session");
                worldScope = engine.require(UIApis.SCREENS).session(module, id("smoke/session"));
                sessionFrames = engine.scheduler().snapshot().frame();
                hudDraw = engine.require(UIApis.RENDER).submit(module, worldScope, RenderService.Surface.HUD,
                        RenderService.Layer.OVERLAY, 0, context -> {
                            require(context.frame().surface() == RenderService.Surface.HUD, "Wrong HUD surface");
                            context.fill(12, 12, 18, 18, MARKER);
                        });
                stage = Stage.HUD;
            }
            case HUD -> {
                if (!hudDraw.status().isTerminal() && engine.scheduler().snapshot().frame() < sessionFrames + 100) return;
                require(hudDraw.status() == TaskHandle.Status.SUCCEEDED, "Native HUD pass did not run");
                stage = Stage.LEAVE;
                game.leave(client, title);
            }
            case LEAVE -> {
                if (client.level != null || client.screen != title) return;
                require(!engine.platform().hasClientSession(), "Native logout did not release the session");
                require(!worldScope.isOpen(), "Native logout left an extension session open");
                require(UnifiedUI.isAvailable(), "Screen/session closure stopped the engine");
                var result = engine.platform().info() + " Java " + Runtime.version().feature()
                        + " optional=" + game.optionalPresent()
                        + "\nloader discovery, vanilla Tab, pointer press/drag/release/move, wheel, native focus, "
                        + "key press/repeat/release, UTF-16 input, GUI scale, window loss, framebuffer/clip, "
                        + "scoped theme, animation, resource pack/cache, remount cancellation, "
                        + "reentrant input, capture failure, world login/HUD/logout\n";
                Files.writeString(output.resolve("success.txt"), result);
                System.out.println("KERNEL_SMOKE_OK " + result);
                done = true;
                client.stop();
            }
        }
    }

    private void nativeInputs(Minecraft client, Platform game, ModuleDescriptor.Platform platform) throws Exception {
        var original = probe.first;
        move(client, 22, 18);
        button(client, GLFW.GLFW_PRESS);
        button(client, GLFW.GLFW_RELEASE);
        require(probe.nativeClicks == 1, "Native widget lost its click");
        var service = UnifiedUI.get().require(UIApis.FOCUS);
        var focus = service.request(module, probe.keyboardNode, FOCUS, probe.second, 0).orElseThrow();
        require(probe.getFocused() == probe.second, "Kernel focus was not native focus");
        for (int action : new int[] {GLFW.GLFW_PRESS, GLFW.GLFW_REPEAT, GLFW.GLFW_RELEASE}) {
            key(client, GLFW.GLFW_KEY_K, action);
        }
        require(keys.equals(List.of(NativeInput.KeyAction.PRESS, NativeInput.KeyAction.REPEAT,
                NativeInput.KeyAction.RELEASE)), "Native repeat was not distinguished: " + keys);
        require(probe.vanillaKeys == 0, "Consumed keyboard input reached the native screen");
        invoke(client.keyboardHandler, "charTyped", new Class<?>[] {long.class, int.class, int.class}, window(client), (int) 'q', 0);
        invoke(client.keyboardHandler, "charTyped", new Class<?>[] {long.class, int.class, int.class}, window(client), 0x1f600, 0);
        require(chars.toString().equals("q" + Character.toString(0x1f600)), "Native character units were lost");
        focus.close();
        require(probe.getFocused() == original, "Native focus was not restored");

        move(client, 105, 42);
        button(client, GLFW.GLFW_PRESS);
        move(client, 145, 65);
        game.flushPointer(client);
        button(client, GLFW.GLFW_RELEASE);
        require(captures == 1 && drags > 0 && releases == 1 && moves > 0,
                "Native pointer capture failed " + List.of(captures, drags, releases, moves));
        move(client, 105, 42);
        invoke(client.mouseHandler, "onScroll", new Class<?>[] {long.class, double.class, double.class}, window(client), 1.0, -1.0);
        require(wheel != null && wheel.deltaY() < 0
                && (platform == ModuleDescriptor.Platform.FORGE_1_20_1 ? wheel.deltaX() == 0 : wheel.deltaX() != 0),
                "Native scroll axes were incorrect");

        button(client, GLFW.GLFW_PRESS);
        require(captures == 2, "Second native capture failed");
        client.setWindowActive(false);
        require(cancels == 1 && !UnifiedUI.get().platform().viewport().focused(), "Window loss did not revoke capture");
        client.setWindowActive(true);
        button(client, GLFW.GLFW_RELEASE);
        require(releases == 1, "Canceled capture delivered its release");

        long generation = probe.tree.generation();
        var before = UnifiedUI.get().platform().viewport();
        client.options.guiScale().set(before.guiScale() == 1 ? 2 : 1);
        client.resizeDisplay();
        require(probe.tree.generation() == generation && original != probe.first && viewportChanges > 0,
                "GUI scale did not resize without remounting the Screen");
        require(UnifiedUI.get().platform().viewport().guiScale() != before.guiScale(), "GUI scale did not reach the bridge");

        var window = client.getWindow();
        var replacement = new ProbeScreen();
        viewportReplacement = replacement;
        int receivedMoves = moves;
        window.setGuiScale(window.getGuiScale() == 1 ? 2 : 1);
        move(client, 105, 42);
        require(client.screen == replacement && moves == receivedMoves,
                "An input from the previous Screen reached a remounted tree");
        client.resizeDisplay();
        client.setScreen(probe);

        move(client, 105, 42);
        failDrag = true;
        button(client, GLFW.GLFW_PRESS);
        move(client, 145, 65);
        game.flushPointer(client);
        button(client, GLFW.GLFW_RELEASE);
        failDrag = false;
        require(captures == 3 && cancels == 2 && releases == 1, "A failed capture did not drain its release");
    }

    private final class ProbeScreen extends Screen {
        private ScopeHandle tree;
        private ScopeHandle keyboardNode;
        private Subscription pointerTarget;
        private Button first;
        private Button second;
        private int nativeClicks;
        private int vanillaKeys;
        private com.dishanhai.sh_better_ui.client.gui.layout.AlignmentAssets assets;

        ProbeScreen() { super(Component.translatable("gui.done")); }

        @Override protected void init() {
            var engine = UnifiedUI.get();
            if (tree == null || !tree.isOpen()) {
                tree = engine.require(UIApis.SCREENS).mount(module, this, id("smoke/tree"));
                var input = engine.require(UIApis.INPUT);
                pointerTarget = input.target(module, tree, 5, event -> event instanceof NativeInput.Pointer pointer
                        && (pointer.action() == NativeInput.PointerAction.MOVE || pointer.x() >= 90));
                module.engine().events().subscribe(InputService.POINTER_MOVED, tree, UIEventPhase.TARGET, 0,
                        event -> moves++);
                module.engine().events().subscribe(InputService.EVENT, tree, UIEventPhase.TARGET, 0, event -> {
                    if (!(event.payload() instanceof NativeInput.Pointer pointer)) return;
                    if (pointer.action() == NativeInput.PointerAction.SCROLL) {
                        wheel = pointer;
                        event.consume();
                    } else if (pointer.action() == NativeInput.PointerAction.PRESS) {
                        require(input.capture(module, tree, POINTER, pointer.button(), new UiPointerCapture.Handler() {
                            @Override public void drag(double x, double y) {
                                drags++;
                                if (failDrag) throw new IllegalStateException("smoke capture callback failed");
                            }
                            @Override public void release(double x, double y) { releases++; }
                            @Override public void cancel() { cancels++; }
                        }), "Kernel native pointer slot was unavailable");
                        captures++;
                        event.consume();
                    }
                });
                module.engine().events().subscribe(ScreenService.VIEWPORT_CHANGED, tree, UIEventPhase.TARGET, 0,
                        event -> {
                            viewportChanges++;
                            if (viewportReplacement != null) {
                                var next = viewportReplacement;
                                viewportReplacement = null;
                                Minecraft.getInstance().setScreen(next);
                            }
                        });
            }
            if (keyboardNode != null) keyboardNode.close();
            keyboardNode = tree.openChild(ScopeKind.COMPONENT, id("smoke/keyboard"));
            first = addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> nativeClicks++)
                    .bounds(12, 12, 35, 20).build());
            second = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> {})
                    .bounds(52, 12, 35, 20).build());
            engine.require(UIApis.FOCUS).target(module, keyboardNode, second, 10);
            module.engine().events().subscribe(InputService.EVENT, keyboardNode, UIEventPhase.TARGET, 0, event -> {
                if (event.payload() instanceof NativeInput.Key key && key.key() == GLFW.GLFW_KEY_K) {
                    keys.add(key.action());
                    event.consume();
                } else if (event.payload() instanceof NativeInput.Character character) {
                    chars.append(character.codeUnit());
                    event.consume();
                }
            });
        }

        @Override public boolean keyPressed(int key, int scan, int mods) {
            if (key == GLFW.GLFW_KEY_K) vanillaKeys++;
            return super.keyPressed(key, scan, mods);
        }

        public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

        @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, width, height, BACKGROUND);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private static long window(Minecraft client) { return client.getWindow().getWindow(); }

    private static void key(Minecraft client, int key, int action) {
        client.keyboardHandler.keyPress(window(client), key, GLFW.glfwGetKeyScancode(key), action, 0);
    }

    private static void move(Minecraft client, double x, double y) throws Exception {
        var window = client.getWindow();
        invoke(client.mouseHandler, "onMove", new Class<?>[] {long.class, double.class, double.class}, window(client),
                x * window.getScreenWidth() / window.getGuiScaledWidth(),
                y * window.getScreenHeight() / window.getGuiScaledHeight());
    }

    private static void button(Minecraft client, int action) throws Exception {
        invoke(client.mouseHandler, "onPress", new Class<?>[] {long.class, int.class, int.class, int.class},
                window(client), GLFW.GLFW_MOUSE_BUTTON_LEFT, action, 0);
    }

    private static void invoke(Object target, String name, Class<?>[] arguments, Object... values) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, arguments);
        method.setAccessible(true);
        method.invoke(target, values);
    }

    private static int pixel(NativeImage image, Minecraft client, int x, int y) {
        double scale = client.getWindow().getGuiScale();
        return image.getPixelRGBA((int) (x * scale), (int) (y * scale)) | 0xff000000;
    }

    private static ResourceLocation texture() {
        return ResourceLocation.tryParse("sh_better_ui:textures/kernel_smoke.png");
    }

    private static void installPack(Minecraft client, Path output) throws Exception {
        var pack = output.resolve("resourcepacks/kernel-smoke");
        var textures = pack.resolve("assets/sh_better_ui/textures");
        Files.createDirectories(textures);
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
        Files.writeString(pack.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":" + format + ",\"description\":\"Kernel smoke fixture\"}}");
        try (var image = new NativeImage(2, 2, false)) {
            image.setPixelRGBA(0, 0, -1);
            image.writeToFile(textures.resolve("kernel_smoke.png").toFile());
        }
        var repository = client.getResourcePackRepository();
        repository.reload();
        require(repository.addPack("file/kernel-smoke"), "Native resource pack could not be selected");
    }

    static LevelSettings settings() {
        return new LevelSettings("Kernel smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
    }

    static WorldOptions options() { return new WorldOptions(17, false, false); }

    static WorldDimensions flat(RegistryAccess registries) {
        return registries.registryOrThrow(Registries.WORLD_PRESET)
                .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
    }

    private static UiId id(String path) { return new UiId("sh_better_ui", path); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private enum Stage { START, DRAW, RELOAD, JOIN, HUD, LEAVE }
}
