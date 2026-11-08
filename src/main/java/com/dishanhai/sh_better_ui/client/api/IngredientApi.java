package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.filter.IngredientFilter;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.selection.IngredientCatalog;
import com.dishanhai.sh_better_ui.client.gui.selection.IngredientPicker;
import com.dishanhai.sh_better_ui.client.gui.selection.IngredientPicker.Request;
import com.dishanhai.sh_better_ui.client.gui.selection.IngredientSelection;
import com.dishanhai.sh_better_ui.client.gui.selection.IngredientValues;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** 山海：素材来源、筛选、草稿和选取窗口的客户端入口。 */
public final class IngredientApi {
    IngredientApi() {}

    /** 优先使用 JEI 可见素材，JEI 不可用时使用原生注册表。 */
    public IngredientCatalog automaticCatalog() {
        return IngredientCatalog.automatic();
    }

    public IngredientCatalog registryCatalog() {
        return IngredientCatalog.registry();
    }

    public IngredientCatalog jeiCatalog() {
        return IngredientCatalog.jei();
    }

    public IngredientCatalog catalog(Collection<? extends Ingredient> ingredients) {
        return IngredientCatalog.of(ingredients);
    }

    public IngredientFilter allFilter() {
        return IngredientFilter.all();
    }

    public IngredientFilter itemFilter() {
        return IngredientFilter.items();
    }

    public IngredientFilter fluidFilter() {
        return IngredientFilter.fluids();
    }

    public Ingredient item(ItemStack stack) {
        return IngredientValues.item(stack);
    }

    public IngredientSelection selection(int maxSelections, Collection<? extends Ingredient> initial) {
        return new IngredientSelection(maxSelections, initial);
    }

    public Request singleRequest(Component title, IngredientCatalog catalog) {
        return Request.single(title, catalog);
    }

    public Request multipleRequest(Component title, IngredientCatalog catalog, int maxSelections) {
        return Request.multiple(title, catalog, maxSelections);
    }

    public Request request(Component title, IngredientCatalog catalog, Predicate<? super Ingredient> baseFilter,
                           int maxSelections, List<Ingredient> initial, UiPreferences preferences) {
        return new Request(title, catalog, baseFilter, maxSelections, initial, preferences);
    }

    /** 用户确认后向宿主回传选取结果。 */
    public boolean openPicker(Screen parent, Request request, Supplier<? extends GuiEventListener> restoreFocus,
                              Consumer<? super List<Ingredient>> onConfirm) {
        return IngredientPicker.open(parent, request, restoreFocus, onConfirm);
    }
}
