package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.search.JeiSearchPanel;
import com.dishanhai.sh_better_ui.client.gui.search.SearchField;
import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex;
import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex.Entry;
import com.dishanhai.sh_better_ui.client.gui.selection.IngredientCatalog;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Direction;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Recipe;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;

import java.util.Collection;
import java.util.List;
import java.util.function.IntFunction;

/** 山海：本地搜索、原生输入框和可选 JEI 预览的客户端入口。 */
public final class SearchApi {
    SearchApi() {}

    public <T> SearchIndex<T> index(List<Entry<T>> entries) {
        return new SearchIndex<>(entries);
    }

    public <T> SearchField<T> field(Font font, Bounds bounds, SearchIndex<T> index) {
        return new SearchField<>(font, bounds.x(), bounds.y(), bounds.width(), bounds.height(), index);
    }

    public <T> SearchField<T> field(Font font, Bounds bounds, SearchIndex<T> index,
                                    UiStyle style, UiPreferences preferences) {
        return new SearchField<>(font, bounds.x(), bounds.y(), bounds.width(), bounds.height(), index,
                style, preferences);
    }

    /** 采用客户端显示偏好，并搜索 JEI 提供的素材。 */
    public JeiSearchPanel jeiPanel(Font font, Bounds bounds) {
        return new JeiSearchPanel(font, bounds.x(), bounds.y(), bounds.width(), bounds.height());
    }

    public JeiSearchPanel jeiPanel(Font font, Bounds bounds, IngredientCatalog catalog) {
        return jeiPanel(font, bounds, UiPreferences.configured(), catalog);
    }

    public JeiSearchPanel jeiPanel(Font font, Bounds bounds, UiPreferences preferences, IngredientCatalog catalog) {
        return new JeiSearchPanel(font, bounds.x(), bounds.y(), bounds.width(), bounds.height(), preferences, catalog);
    }

    public boolean jeiAvailable() {
        return JeiIntegration.isAvailable();
    }

    public long jeiRevision() {
        return JeiIntegration.revision();
    }

    public List<Ingredient> jeiIngredients() {
        return JeiIntegration.ingredients();
    }

    public List<Recipe> recipes(Ingredient ingredient, Direction direction, int limit) {
        return JeiIntegration.recipes(ingredient, direction, limit);
    }

    public boolean openRecipes(Ingredient ingredient, Direction direction) {
        return JeiIntegration.open(ingredient, direction);
    }

    /** 注册宿主界面和候选列表的避让区域，传入 {@code null} 清除登记。 */
    public void setJeiExtraAreas(Screen screen, IntFunction<? extends Collection<Rect2i>> provider) {
        JeiIntegration.setExtraAreas(screen, provider);
    }
}
