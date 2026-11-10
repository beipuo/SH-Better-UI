// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiPointerCapture;
import com.dishanhai.sh_better_ui.client.theme.MinecraftThemes;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.kernel.api.ConfigLayer;
import com.dishanhai.sh_better_ui.kernel.api.ConfigScope;
import com.dishanhai.sh_better_ui.kernel.api.EventResult;
import com.dishanhai.sh_better_ui.kernel.api.EngineState;
import com.dishanhai.sh_better_ui.kernel.api.ConflictPolicy;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.NativeInput;
import com.dishanhai.sh_better_ui.kernel.api.OwnershipLease;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.ResourceRequest;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.ServiceDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.UIPermission;
import com.dishanhai.sh_better_ui.kernel.api.UIPhase;
import com.dishanhai.sh_better_ui.kernel.api.UIDirtyFlag;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.ThemeScope;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/** Physical client setup and shutdown shared by the two loaders. */
public final class ClientUiKernel implements AutoCloseable {
    private static final ModuleDescriptor.ResourceClaim LEGACY_POINTER = new ModuleDescriptor.ResourceClaim(
            ModuleDescriptor.ResourceKind.POINTER_CAPTURE, UiId.parse("sh_better_ui:native/pointer"));
    private static volatile ClientUiKernel active;
    private final KernelHost host;
    private final ModuleContext theme;
    private final ClientAnimation animation;
    private final ClientLayout layout;
    private final ClientInput input;
    private final ClientScreens screens;
    private final ClientFocus focus;
    private final ClientCollision collision;
    private final ClientRender rendering;
    private final ClientResources resources;
    private final ConfigScope userGlobal;
    private ClientUiSettings publishedSettings;
    private long nextPage;

    private ClientUiKernel(KernelHost host, ModuleContext theme, ClientAnimation animation, ClientLayout layout,
                           ClientInput input, ClientScreens screens, ClientFocus focus, ClientCollision collision,
                           ClientRender rendering, ClientResources resources) {
        this.host = host;
        this.theme = theme;
        this.animation = animation;
        this.layout = layout;
        this.input = input;
        this.screens = screens;
        this.focus = focus;
        this.collision = collision;
        this.rendering = rendering;
        this.resources = resources;
        userGlobal = new ConfigScope(host.rootScope(), ConfigLayer.USER_GLOBAL);
    }

    public static void start(ModuleDescriptor.Platform platform) {
        start(platform, List.of());
    }

    public static void start(ModuleDescriptor.Platform platform, List<ModFile> modFiles) {
        if (active == null) {
            active = install(platform, EnginePublication.CLIENT, MinecraftThemes.manager(), MinecraftThemes::shutdown,
                    System::nanoTime, NativeModuleSources.discover(modFiles),
                    Minecraft.getInstance().gameDirectory.toPath().resolve("config/sh_better_ui/modules"));
        }
    }

    /** CPU-only compatibility pump. Native render work waits for the loader's GUI callback. */
    public static void frame() {
        if (!hasNativeAuthority()) return;
        var client = active;
        if (!client.host.canBeginNativeFrame()) return;
        client.publishSettings();
        client.host.beginNativeFrame(() -> {}, client::phase);
        client.host.finishNativeFrame();
    }

    public static OwnershipLease ownNativeGpu(ModuleDescriptor.ResourceKind kind, UiId id, Runnable release) {
        RenderSystem.assertOnRenderThread();
        if (kind != ModuleDescriptor.ResourceKind.RENDER_TARGET && kind != ModuleDescriptor.ResourceKind.TEXTURE_RESOURCE) {
            throw new IllegalArgumentException("Not a GPU resource kind: " + kind);
        }
        if (!id.namespace().equals("sh_better_ui")) throw new IllegalArgumentException("Native UI resource namespace");
        Objects.requireNonNull(release);
        var client = active;
        if (client == null || client.host.lifecycle().state() != EngineState.RUNNING) return null;
        return client.host.rootScope().claim(new ModuleDescriptor.ResourceClaim(kind, id), release::run);
    }

    public static boolean hasNativeAuthority() {
        var client = active;
        return client != null && client.host.lifecycle().state() == EngineState.RUNNING;
    }

    public static boolean beginNativeFrame(Minecraft minecraft, LongConsumer frameClock) {
        if (!hasNativeAuthority()) return false;
        var client = active;
        if (!client.host.canBeginNativeFrame()) return false;
        client.sync(minecraft);
        client.publishSettings();
        client.host.beginNativeFrame(() -> frameClock.accept(client.host.nowNanos()), phase -> {
            if (phase == UIPhase.STYLE_RESOLUTION) MinecraftThemes.frame();
            client.phase(phase);
        });
        return true;
    }

    public static void renderNative(Screen owner, GuiGraphics graphics, float partialTick) {
        if (!hasNativeAuthority()) return;
        RenderSystem.assertOnRenderThread();
        var minecraft = Minecraft.getInstance();
        if (owner != minecraft.screen || minecraft.getOverlay() != null || owner == null && minecraft.options.hideGui) return;
        var client = active;
        var previous = client.screens.activeScope(owner);
        client.sync(minecraft);
        if (!client.sameNativeSurface(minecraft, owner, previous)) return;
        var screen = client.screens.activeScope(owner);
        if (owner != null && screen == null) return;
        var viewport = client.screens.viewport();
        var surface = owner == null ? RenderService.Surface.HUD : RenderService.Surface.SCREEN;
        var frame = new RenderService.Frame(surface, viewport.width(), viewport.height(), viewport.guiScale(), partialTick);
        client.rendering.render(frame, new GuiCanvas(graphics), () -> client.host.renderNativeFrame(screen));
    }

    public static void finishNativeFrame() {
        if (hasNativeAuthority()) active.host.finishNativeFrame();
    }

    public static void tick(Minecraft minecraft) {
        if (hasNativeAuthority()) active.sync(minecraft);
    }

    public static void screenInitialized(Screen owner) {
        if (hasNativeAuthority()) {
            active.screens.initialized(owner);
            active.focus.reconcile();
        }
    }

    public static void screenClosing(Screen owner) {
        if (hasNativeAuthority()) active.screens.closing(owner);
    }

    public static void login(Object connection) {
        if (hasNativeAuthority()) active.screens.login(connection);
    }

    public static void logout(Object connection) {
        if (hasNativeAuthority()) active.screens.logout(connection);
    }

    public static void windowFocus(Minecraft minecraft) {
        if (hasNativeAuthority() && active.host.isOnUIThread()) active.sync(minecraft);
    }

    public static boolean nativeInput(Screen owner, NativeInput event) {
        if (!hasNativeAuthority()) return false;
        var minecraft = Minecraft.getInstance();
        if (owner == null || owner != minecraft.screen || minecraft.getOverlay() != null
                || !minecraft.isWindowActive()) return false;
        var client = active;
        if (!client.host.canRouteNativeInput()) return false;
        var previous = client.screens.activeScope(owner);
        client.sync(minecraft);
        return client.sameNativeSurface(minecraft, owner, previous) && minecraft.isWindowActive()
                && client.dispatch(client.screens.activeScope(owner), event).blockVanilla();
    }

    public static void pointerMoved(long windowHandle, double x, double y) {
        if (!hasNativeAuthority()) return;
        var client = active;
        var minecraft = Minecraft.getInstance();
        if (!client.input.hasTargets(client.screens.activeScope()) || minecraft.screen == null) return;
        var window = minecraft.getWindow();
        if (window.getWindow() != windowHandle) return;
        nativeInput(minecraft.screen, new NativeInput.Pointer(NativeInput.PointerAction.MOVE,
                x * window.getGuiScaledWidth() / Math.max(1, window.getScreenWidth()),
                y * window.getGuiScaledHeight() / Math.max(1, window.getScreenHeight()), -1, 0, 0));
    }

    public static OwnershipLease ownNativeModal(Screen owner, Runnable release) {
        Objects.requireNonNull(release);
        if (!hasNativeAuthority() || Minecraft.getInstance().screen != owner) return null;
        var client = active;
        client.host.assertOwner();
        var scope = client.screens.activeScope(owner);
        if (scope == null || !scope.isOpen()) return null;
        return scope.claim(new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.MODAL_LAYER,
                UiId.parse("sh_better_ui:native/modal")), release::run);
    }

    public static OwnershipLease claimNativePointer(Screen owner, ModuleContext module, ScopeHandle node,
                                                    UiId claimId, Runnable revoke) {
        if (!hasNativeAuthority()) return null;
        var client = active;
        var minecraft = Minecraft.getInstance();
        if (minecraft.screen != owner) return null;
        var previous = client.screens.activeScope(owner);
        client.sync(minecraft);
        if (!client.sameNativeSurface(minecraft, owner, previous) || !minecraft.isWindowActive()) return null;
        var screen = client.screens.activeScope(owner);
        if (screen == null) return null;
        if (module == null) {
            return client.host.pointerAvailable(screen, LEGACY_POINTER) ? screen.claim(LEGACY_POINTER, revoke::run) : null;
        }
        var target = client.host.ownedNode(module, node);
        if (client.screens.ownerOf(target) != owner) return null;
        var access = client.host.moduleAccess(module);
        var claim = new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.POINTER_CAPTURE, claimId);
        access.requireClaim(claim);
        access.require(UIPermission.CAPTURE_INPUT, target);
        access.require(UIPermission.BLOCK_VANILLA_INPUT, target);
        return client.host.pointerAvailable(target, claim) ? client.host.acquireNativePointer(access,
                new ResourceRequest(claim, target, 0, 0, ConflictPolicy.REJECT), revoke::run) : null;
    }

    public static void nativeCaptureFailed(ModuleContext module, String action, Throwable failure) {
        var client = active;
        if (client != null) client.host.report("UI pointer " + action + " for " + module.descriptor().id(), failure);
    }

    public static PreparableReloadListener reloadListener(Runnable apply) {
        return (barrier, manager, preparationProfiler, reloadProfiler, prepareExecutor, reloadExecutor) -> {
            var client = active;
            var themes = MinecraftThemes.prepareReload(manager, prepareExecutor);
            var data = client == null
                    ? java.util.concurrent.CompletableFuture.<ClientResources.Prepared>completedFuture(null)
                    : client.resources.prepare(manager, prepareExecutor);
            record Batch(MinecraftThemes.PreparedReload themes, ClientResources.Prepared data) {}
            return themes.thenCombine(data, Batch::new).thenCompose(barrier::wait).thenAcceptAsync(ready -> {
                if (active != client) return;
                Runnable install = () -> {
                    if (!MinecraftThemes.commitReload(ready.themes())) {
                        throw new IllegalStateException("UI theme reload was superseded or stopped");
                    }
                    apply.run();
                };
                if (client == null) install.run();
                else client.resources.commit(ready.data(), install);
            }, Minecraft.getInstance());
        };
    }

    public static void stop() {
        var client = active;
        if (client != null) {
            try {
                client.close();
            } finally {
                active = null;
            }
        } else {
            MinecraftThemes.shutdown();
        }
    }

    /** The opt-in UI context closes this handle on removal. Shutdown also closes any remaining pages. */
    public static AutoCloseable ownPageTheme(ThemeScope scope) {
        if (active == null) return scope;
        return active.attachPageTheme(scope);
    }

    ScopeHandle attachPageTheme(ThemeScope scope) {
        var page = theme.scope().openChild(ScopeKind.SCREEN, new UiId("sh_better_ui", "theme_page/" + ++nextPage));
        page.track(scope);
        return page;
    }

    void runFrame() {
        publishSettings();
        host.runFrame(this::phase);
    }

    EventResult dispatch(ScopeHandle screen, NativeInput event) {
        return input.dispatch(screen, event);
    }

    ClientScreens screens() { return screens; }

    ClientRender rendering() { return rendering; }

    ClientResources resources() { return resources; }

    void reconcileFocus() { focus.reconcile(); }

    private void sync(Minecraft minecraft) {
        boolean wasFocused = screens.viewport().focused();
        screens.sync(minecraft);
        if (wasFocused && !screens.viewport().focused()) {
            UiPointerCapture.windowLost(minecraft.screen);
            focus.windowLost();
        }
        focus.reconcile();
    }

    private boolean sameNativeSurface(Minecraft minecraft, Screen owner, ScopeHandle previous) {
        return minecraft.screen == owner && minecraft.getOverlay() == null
                && (previous == null || screens.activeScope(owner) == previous);
    }

    private void phase(UIPhase phase) {
        switch (phase) {
            case MEASURE -> layout.process(UIDirtyFlag.MEASURE);
            case LAYOUT -> layout.process(UIDirtyFlag.LAYOUT);
            case ANIMATION -> animation.update();
            case GEOMETRY_UPDATE -> collision.update();
            default -> {}
        }
    }

    private void publishSettings() {
        var current = ClientUiSettings.current();
        if (current.equals(publishedSettings)) return;
        theme.engine().configs().beginTransaction().set(userGlobal, UIApis.CLIENT_SETTINGS, current).commit();
        publishedSettings = current;
    }

    @Override
    public void close() {
        screens.clear();
        host.close();
    }

    static ClientUiKernel install(ModuleDescriptor.Platform platform, EnginePublication publication,
                                  ThemeManager manager, AutoCloseable release) {
        return install(platform, publication, manager, release, System::nanoTime, List.of());
    }

    static ClientUiKernel install(ModuleDescriptor.Platform platform, EnginePublication publication,
                                  ThemeManager manager, AutoCloseable release, LongSupplier clock,
                                  List<ModuleDiscovery.Candidate> extensions) {
        return install(platform, publication, manager, release, clock, extensions, null);
    }

    static ClientUiKernel install(ModuleDescriptor.Platform platform, EnginePublication publication,
                                  ThemeManager manager, AutoCloseable release, LongSupplier clock,
                                  List<ModuleDiscovery.Candidate> extensions, Path storage) {
        var kernel = new KernelHost(platform, publication, clock, null, SchedulerBudget.DEFAULT);
        if (storage != null) kernel.storage(storage);
        var module = new BuiltinTheme(kernel, manager, release);
        var animation = new ClientAnimation(kernel);
        var layout = new ClientLayout(kernel);
        var screens = new ClientScreens(kernel, platform);
        var input = new ClientInput(kernel, screens);
        var focus = new ClientFocus(kernel, screens, input);
        var collision = new ClientCollision(kernel, input);
        var rendering = new ClientRender(kernel, screens);
        var resources = new ClientResources(kernel);
        var candidates = new ArrayList<ModuleDiscovery.Candidate>();
        candidates.add(new ModuleDiscovery.Candidate(UIApis.THEME.id().namespace(),
                BuiltinTheme.class.getName(), module, Set.of(UIPermission.MODIFY_GLOBAL_CONFIG), true));
        candidates.add(BuiltinService.candidate(UIApis.ANIMATION, animation, UIApis.SCOPED_ANIMATION));
        candidates.add(BuiltinService.candidate(UIApis.LAYOUT, layout, UIApis.INCREMENTAL_LAYOUT));
        candidates.add(BuiltinService.candidate(UIApis.INPUT, input, UIApis.INPUT_ROUTING));
        candidates.add(BuiltinService.candidate(UIApis.FOCUS, focus, UIApis.SCREEN_FOCUS));
        candidates.add(BuiltinService.candidate(UIApis.COLLISION, collision, UIApis.PRESENTED_HIT_TEST));
        candidates.add(BuiltinService.candidate(UIApis.RENDER, rendering, UIApis.NATIVE_RENDER));
        candidates.add(BuiltinService.candidate(UIApis.RESOURCES, resources, UIApis.RELOAD_GENERATIONS));
        candidates.add(BuiltinService.candidate(UIApis.ACCESSIBILITY, new ClientAccessibility(kernel),
                UIApis.MINECRAFT_NARRATION));
        candidates.add(BuiltinService.candidate(UIApis.PLATFORM, screens));
        candidates.add(BuiltinService.candidate(UIApis.SCREENS, screens, UIApis.NATIVE_SCREEN));
        candidates.addAll(extensions);
        kernel.startNative(candidates);
        var client = new ClientUiKernel(kernel, module.context, animation, layout, input, screens, focus, collision,
                rendering, resources);
        try {
            client.publishSettings();
            return client;
        } catch (RuntimeException | LinkageError failure) {
            kernel.close();
            throw failure;
        }
    }

    /** Descriptor path and identities supplied by the physical loader. */
    public record ModFile(Path descriptor, String moduleName, Set<String> modIds) {
        public ModFile {
            Objects.requireNonNull(descriptor);
            Objects.requireNonNull(moduleName);
            modIds = Set.copyOf(modIds);
        }
    }

    private record GuiCanvas(GuiGraphics graphics) implements ClientRender.Backend {
        @Override public void push() { graphics.pose().pushPose(); }
        @Override public void pop() { graphics.pose().popPose(); }
        @Override public void translate(double x, double y, double z) { graphics.pose().translate(x, y, z); }
        @Override public void scale(float factor) { graphics.pose().scale(factor, factor, 1); }
        @Override public void clip(int x, int y, int right, int bottom) { graphics.enableScissor(x, y, right, bottom); }
        @Override public void unclip() { graphics.disableScissor(); }
        @Override public void fill(int x, int y, int right, int bottom, int color) { graphics.fill(x, y, right, bottom, color); }
    }

    private static final class BuiltinTheme implements UIModule {
        private static final UiVersion VERSION = new UiVersion(1, 0, 0);
        private static final ModuleDescriptor DESCRIPTOR = ModuleDescriptor.builder().id(UIApis.THEME.id()).version(VERSION)
                .initializationPhase(ModuleDescriptor.InitializationPhase.CORE)
                .providedCapability(UIApis.DYNAMIC_THEME, VERSION)
                .requestPermission(UIPermission.MODIFY_GLOBAL_CONFIG)
                .build();
        private final ThemeManager manager;
        private final KernelHost host;
        private final AutoCloseable release;
        private ModuleContext context;

        private BuiltinTheme(KernelHost host, ThemeManager manager, AutoCloseable release) {
            this.host = host;
            this.manager = manager;
            this.release = release;
        }

        @Override
        public ModuleDescriptor descriptor() {
            return DESCRIPTOR;
        }

        @Override
        public void register(ModuleContext context) {
            context.scope().track(release);
            context.services().register(new ServiceDescriptor<>(UIApis.THEME, VERSION,
                    ServiceDescriptor.Visibility.PUBLIC, 0, false), new ClientTheme(host, manager, context.scope()));
        }

        @Override
        public void start(ModuleContext context) {
            context.engine().configs().define(UIApis.CLIENT_SETTINGS);
            this.context = context;
        }

        @Override
        public void stop(ModuleContext context) {}
    }

}
