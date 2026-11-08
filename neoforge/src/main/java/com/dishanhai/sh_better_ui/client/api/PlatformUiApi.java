package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.glass.GlassTooltip;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiOverlayHost;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPullRefresh;
import com.dishanhai.sh_better_ui.client.gui.selection.IngredientValues;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient;
import com.dishanhai.sh_better_ui.compat.ldlib2.LdLib2UiBridge;
import com.dishanhai.sh_better_ui.compat.ldlib2.LdLib2UiBridge.PullRefreshHandle;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** 山海：NeoForge 1.21.1 的原生类型与可选 LDLib2 接入。 */
public final class PlatformUiApi {
    PlatformUiApi() {}

    public Ingredient fluid(FluidStack stack) {
        return IngredientValues.fluid(stack);
    }

    public void renderTooltip(RenderTooltipEvent.Pre event) {
        GlassTooltip.render(event);
    }

    public void renderTooltip(RenderTooltipEvent.Pre event, boolean smoke) {
        GlassTooltip.render(event, smoke);
    }

    public LdLib ldlib() {
        return LdLib.INSTANCE;
    }

    /** 使用以下接口的客户端需安装 NeoForge LDLib2。 */
    public static final class LdLib {
        private static final LdLib INSTANCE = new LdLib();

        private LdLib() {}

        public UiOverlayHost overlay(Screen owner, UiPreferences preferences) {
            return LdLib2UiBridge.overlay(owner, preferences);
        }

        public Supplier<Rect2i> anchor(Screen owner, UIElement element) {
            return LdLib2UiBridge.anchor(owner, element);
        }

        /** 将把手加入客户端 UI 树，并将其 focusTarget 注册到宿主 Screen。 */
        public PullRefreshHandle createPullRefreshHandle(Screen owner, UIElement viewport, UiPullRefresh refresh,
                                                         BooleanSupplier atTop, UiPreferences preferences) {
            return LdLib2UiBridge.createPullRefreshHandle(owner, viewport, refresh, atTop, preferences);
        }
    }
}
