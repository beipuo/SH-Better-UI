// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.client.api.UiScreen;
import com.dishanhai.sh_better_ui.client.api.UiScreenBuilder;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityKey;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityStatus;
import com.dishanhai.sh_better_ui.kernel.api.ConfigKey;
import com.dishanhai.sh_better_ui.kernel.api.ConfigLayer;
import com.dishanhai.sh_better_ui.kernel.api.ConfigScope;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.ScopeKind;
import com.dishanhai.sh_better_ui.kernel.api.ScreenService;
import com.dishanhai.sh_better_ui.kernel.api.ServiceDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIConfigCenter;
import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.VersionRange;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Set;
import java.util.function.Supplier;

/** Discovered from this mod's META-INF/sh_better_ui.modules on both loaders. */
public final class ExampleUiModule implements UIModule {
    public static final String MOD_ID = "shbui_kernel_example";
    public static final UiId ID = id("controls");
    public static final CapabilityKey CONTROLS = new CapabilityKey(ID);
    public static final ConfigKey<Integer> STEP = ConfigKey.of(id("step"), Integer.class, 1,
            Set.of(ConfigLayer.COMPONENT), step -> step >= 1 && step <= 5);
    private static final UiVersion VERSION = UiVersion.parse("1.0.0");

    private ModuleContext module;
    private ScreenService screens;
    private UIConfigCenter configs;
    private boolean dynamicTheme;
    private boolean systemReader;
    private CounterPage page;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder().id(ID).version(VERSION)
                .requiredCapability(UIApis.NATIVE_SCREEN, VersionRange.ANY)
                .optionalCapability(UIApis.DYNAMIC_THEME, VersionRange.ANY)
                .optionalCapability(UIApis.SYSTEM_SCREEN_READER, VersionRange.ANY)
                .providedCapability(CONTROLS, VERSION)
                .build();
    }

    @Override
    public void register(ModuleContext context) {
        CounterService counter = this::open;
        context.services().register(new ServiceDescriptor<>(CounterService.KEY, VERSION,
                ServiceDescriptor.Visibility.PUBLIC, 0, false), counter);
    }

    @Override
    public void start(ModuleContext context) {
        var ui = context.engine();
        screens = ui.require(UIApis.SCREENS);
        configs = ui.configs();
        configs.define(STEP);
        dynamicTheme = ui.capabilities().supports(UIApis.DYNAMIC_THEME) && ui.find(UIApis.THEME).isPresent();
        systemReader = ui.capabilities().status(UIApis.SYSTEM_SCREEN_READER) == CapabilityStatus.AVAILABLE;
        module = context;
        ExampleSmoke.started(this);
    }

    @Override
    public void stop(ModuleContext context) {
        try {
            if (page != null) page.screen.dispose();
        } finally {
            page = null;
            module = null;
            screens = null;
            configs = null;
        }
    }

    private void open(Screen parent, Supplier<? extends GuiEventListener> returnFocus) {
        if (module == null) throw new IllegalStateException("Example UI module is inactive");
        if (!module.engine().platform().isOnUIThread()) throw new IllegalStateException("Client thread required");
        if (page != null) return;
        var next = new CounterPage();
        var builder = new UiScreenBuilder(label("title")).parent(parent).returnFocus(returnFocus)
                .onInit(next::init).onRemoved(screen -> {
                    // The kernel closes this page's mount when its native Screen closes.
                    if (page == next) page = null;
                });
        if (dynamicTheme) builder.dynamicTheme();
        next.screen = screens.create(module, builder);
        page = next;
        next.screen.open();
    }

    static UiId id(String path) {
        return new UiId(MOD_ID, path);
    }

    static Component label(String name, Object... arguments) {
        return Component.translatable(MOD_ID + "." + name, arguments);
    }

    CounterPage page() { return page; }
    boolean stopped() { return module == null && page == null; }

    final class CounterPage {
        private Integer previous;
        UiScreen screen;
        ScopeHandle tree;
        ScopeHandle counter;
        UiButton increase;
        UiButton undo;
        int count;

        void init(UiScreen active) {
            if (tree == null) {
                tree = screens.mount(module, active, id("counter_screen"));
                counter = tree.openChild(ScopeKind.COMPONENT, id("counter_button"));
                configs.beginTransaction().set(new ConfigScope(counter, ConfigLayer.COMPONENT), STEP, 3).commit();
            }
            var bounds = active.viewport();
            int width = Math.min(180, Math.max(1, bounds.width() - 12));
            int x = bounds.x() + (bounds.width() - width) / 2;
            int y = bounds.y() + Math.max(0, (bounds.height() - 50) / 2);
            increase = active.button(x, y, width, 22, countLabel(), () -> {
                previous = count;
                count += step();
                refresh();
            });
            undo = active.button(x, y + 28, width, 22, label("undo"), () -> {
                if (previous != null) count = previous;
                previous = null;
                refresh();
            });
            refresh();
        }

        boolean systemReader() { return systemReader; }

        int step() { return configs.get(counter, STEP); }

        private void refresh() {
            increase.setMessage(countLabel());
            increase.setTooltip(Tooltip.create(label(systemReader ? "system_reader_available" : "minecraft_narration")));
            undo.active = previous != null;
        }

        private Component countLabel() {
            return label("increase", count, step());
        }
    }
}
