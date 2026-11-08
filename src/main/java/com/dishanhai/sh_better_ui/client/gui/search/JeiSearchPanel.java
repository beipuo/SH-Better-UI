package com.dishanhai.sh_better_ui.client.gui.search;

import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.GuiRenderUtil;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiAttention;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.selection.IngredientCatalog;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Direction;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Recipe;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.RecipeLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** 山海：供原生 Screen 嵌入的 JEI 搜索与只读配方预览。所有方法在客户端线程使用。 */
public final class JeiSearchPanel {
    private static final int PAD = 6;
    private static final int GAP = 4;
    private static final int CONTROL_HEIGHT = 18;
    private static final int ROW_HEIGHT = 20;
    private static final int ICON = 16;
    private static final Component MOD_FILTER = text("mod_filter");
    private static final Component UNAVAILABLE = text("unavailable");
    private static final Component CATALOG_UNAVAILABLE = Component.translatable("sh_better_ui.picker.unavailable");
    private static final Component NO_RESULTS = text("no_results");
    private static final Component NO_RECIPES = text("no_recipes");
    private static final Component PREVIEW_UNAVAILABLE = text("preview_unavailable");

    private final Font font;
    private final UiPreferences preferences;
    private final UiAttention attention = new UiAttention();
    private final Bounds panel;
    private final Bounds list;
    private final Bounds detail;
    private final SearchField<Ingredient> search;
    private final SearchField<String> modSearch;
    private final List<ResultRow> rows;
    private final List<AbstractWidget> widgets;
    private final UiButton obtaining;
    private final UiButton uses;
    private final UiButton open;
    private final UiButton choose;

    private SearchIndex<Ingredient> index = new SearchIndex<>(List.of());
    private IngredientCatalog catalog;
    private Predicate<? super Ingredient> filter = ingredient -> true;
    private Predicate<? super Ingredient> chosen = ingredient -> false;
    private List<SearchIndex.Entry<Ingredient>> results = List.of();
    private long revision = Long.MIN_VALUE;
    private long recipeRevision = Long.MIN_VALUE;
    private Language language;
    private String modQuery = "";
    private boolean available;
    private int firstResult;
    private String selectedKey;
    private Ingredient selected;
    private Direction direction = Direction.OBTAINING;
    private Recipe recipe;
    private RecipeLayout layout;
    private Consumer<? super Ingredient> onChoose;

    public JeiSearchPanel(Font font, int x, int y, int width, int height) {
        this(font, x, y, width, height, UiPreferences.configured());
    }

    public JeiSearchPanel(Font font, int x, int y, int width, int height, UiPreferences preferences) {
        this(font, x, y, width, height, preferences, IngredientCatalog.jei());
    }

    public JeiSearchPanel(Font font, int x, int y, int width, int height, UiPreferences preferences,
                          IngredientCatalog catalog) {
        if (width < 144 || height < 132) throw new IllegalArgumentException("JEI panel requires at least 144 by 132");
        this.font = Objects.requireNonNull(font);
        this.preferences = Objects.requireNonNull(preferences);
        this.catalog = Objects.requireNonNull(catalog);
        panel = new Bounds(x, y, width, height);
        int innerX = x + PAD;
        int innerWidth = width - 2 * PAD;
        int controlsY = y + PAD;
        int filterWidth = Math.clamp(innerWidth / 3, 54, 112);
        int searchWidth = innerWidth - filterWidth - GAP;
        search = new SearchField<>(font, innerX, controlsY, searchWidth, CONTROL_HEIGHT, index);
        modSearch = new SearchField<>(font, innerX + searchWidth + GAP, controlsY,
                filterWidth, CONTROL_HEIGHT, new SearchIndex<>(List.of()));
        modSearch.setMessage(MOD_FILTER);
        modSearch.setHint(MOD_FILTER);

        int bodyY = controlsY + CONTROL_HEIGHT + GAP;
        int bodyHeight = panel.bottom() - PAD - bodyY;
        if (width >= 280) {
            int listWidth = Math.min(176, innerWidth * 2 / 5);
            list = new Bounds(innerX, bodyY, listWidth, bodyHeight);
            detail = new Bounds(list.right() + GAP, bodyY, innerWidth - listWidth - GAP, bodyHeight);
        } else {
            int rows = Math.clamp((bodyHeight - 70) / ROW_HEIGHT, 1, 4);
            list = new Bounds(innerX, bodyY, innerWidth, rows * ROW_HEIGHT);
            detail = new Bounds(innerX, list.bottom() + GAP, innerWidth, bodyHeight - list.height() - GAP);
        }

        int splitWidth = (detail.width() - GAP) / 2;
        int tabsY = detail.y() + ROW_HEIGHT + 2;
        int actionsY = detail.bottom() - CONTROL_HEIGHT;
        obtaining = button("obtaining", detail.x(), tabsY, splitWidth, () -> setDirection(Direction.OBTAINING));
        uses = button("uses", detail.x() + splitWidth + GAP, tabsY, detail.width() - splitWidth - GAP,
                () -> setDirection(Direction.USES));
        open = button("open", detail.x(), actionsY, detail.width(), this::openNative);
        choose = button("choose", detail.x() + splitWidth + GAP, actionsY, detail.width() - splitWidth - GAP,
                this::chooseSelected);

        var resultRows = new ArrayList<ResultRow>();
        for (int row = 0; row < list.height() / ROW_HEIGHT; row++) resultRows.add(new ResultRow(row));
        rows = List.copyOf(resultRows);
        var controls = new ArrayList<AbstractWidget>(rows.size() + 6);
        controls.add(search);
        controls.add(modSearch);
        controls.addAll(rows);
        controls.add(obtaining);
        controls.add(uses);
        controls.add(open);
        controls.add(choose);
        widgets = List.copyOf(controls);

        search.setResponder(ignored -> refreshResults(true));
        search.setOnSubmit(ignored -> {
            if (!results.isEmpty()) {
                firstResult = 0;
                select(results.getFirst().value());
                updateControls();
            }
        });
        modSearch.setResponder(query -> {
            modQuery = query.strip().toLowerCase(Locale.ROOT);
            search.setScope(this::inScope);
            refreshResults(true);
        });
        search.setScope(this::inScope);
        refreshData();
    }

    /** 在 Screen.init 中用 addRenderableWidget 注册一次，以保留原生焦点与旁白。 */
    public List<AbstractWidget> widgets() {
        return widgets;
    }

    /** 只有明确点击选择按钮时才调用。传 null 隐藏选择按钮。 */
    public void setOnChoose(Consumer<? super Ingredient> onChoose) {
        this.onChoose = onChoose;
        updateControls();
    }

    /** 默认仍使用 JEI。可注入注册表或调用方维护的素材来源，保留当前查询与预览键。 */
    public void setCatalog(IngredientCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog);
        language = null;
        refreshData();
    }

    /** 结果与搜索联想使用同一条件。可重复调用以刷新依赖外部状态的条件。 */
    public void setFilter(Predicate<? super Ingredient> filter) {
        this.filter = Objects.requireNonNull(filter);
        search.setScope(this::inScope);
        refreshResults(true);
    }

    /** 草稿标记独立于当前预览项，由宿主持有单选或多选状态。 */
    public void setChosen(Predicate<? super Ingredient> chosen) {
        this.chosen = Objects.requireNonNull(chosen);
        refreshSelection();
    }

    public void refreshSelection() {
        updateControls();
    }

    public record Snapshot(String query, String modQuery, String selectedKey, int firstResult, Direction direction) {
        public Snapshot {
            Objects.requireNonNull(query);
            Objects.requireNonNull(modQuery);
            Objects.requireNonNull(direction);
        }
    }

    public Snapshot snapshot() {
        return new Snapshot(search.getValue(), modSearch.getValue(), selectedKey, firstResult, direction);
    }

    /** 宿主在缩放或重新 init 时，可以把快照应用到重建后的面板。 */
    public void restore(Snapshot state) {
        search.setValue(state.query());
        modSearch.setValue(state.modQuery());
        direction = state.direction();
        selectedKey = state.selectedKey();
        firstResult = Math.max(0, state.firstResult());
        selected = null;
        refreshResults(false);
    }

    /** 提供给 JEI 的界面排除区，包含面板及当前的两个下拉区域。 */
    public List<Rect2i> extraAreas(int screenHeight) {
        var areas = new ArrayList<Rect2i>(3);
        areas.add(new Rect2i(panel.x(), panel.y(), panel.width(), panel.height()));
        search.suggestionArea(screenHeight).ifPresent(areas::add);
        modSearch.suggestionArea(screenHeight).ifPresent(areas::add);
        return List.copyOf(areas);
    }

    /** 宿主可据此暂停登记底层控件的 Screen 延迟提示。 */
    public boolean hasOpenSuggestions(int screenHeight) {
        return search.suggestionArea(screenHeight).isPresent() || modSearch.suggestionArea(screenHeight).isPresent();
    }

    public void tick() {
        refreshData();
        if (layout != null) layout.tick();
    }

    /** 先于 Screen 的控件绘制。 */
    public void renderBackground(GuiGraphics graphics) {
        refreshData();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), DarkModeTheme.BACKGROUND);
        graphics.renderOutline(panel.x(), panel.y(), panel.width(), panel.height(), DarkModeTheme.BORDER_HOVER);
        if (!available) {
            Component status = catalog == IngredientCatalog.jei() ? UNAVAILABLE : CATALOG_UNAVAILABLE;
            drawText(graphics, status.getString(), list.x() + GAP, list.y() + GAP, panel.width() - 4 * PAD,
                    DarkModeTheme.TEXT_SECONDARY);
        } else if (results.isEmpty()) {
            drawText(graphics, NO_RESULTS.getString(), list.x() + GAP, list.y() + GAP, list.width() - 2 * GAP,
                    DarkModeTheme.TEXT_SECONDARY);
        }
        if (selected != null) {
            drawIcon(graphics, selected, detail.x() + 2, detail.y() + 2);
            drawText(graphics, selected.name(), detail.x() + 22, detail.y() + 2, detail.width() - 22,
                    DarkModeTheme.TEXT);
            drawText(graphics, selected.id().toString(), detail.x() + 22, detail.y() + 2 + font.lineHeight,
                    detail.width() - 22, DarkModeTheme.TEXT_SECONDARY);
        }
    }

    /** 在普通控件之后绘制配方、原生提示与下拉层。 */
    public void renderOverlays(GuiGraphics graphics, int mouseX, int mouseY, int screenHeight) {
        boolean suggestionsOpen = hasOpenSuggestions(screenHeight);
        if (selected != null) {
            Button current = direction == Direction.OBTAINING ? obtaining : uses;
            long now = PageTransition.nowMs();
            attention.select(List.of(selected.key(), direction), current.getX(), current.getY(),
                    current.getWidth(), current.getHeight(), now);
            var frame = attention.sample(now, preferences);
            DarkModeTheme.selectionIndicator(graphics, frame, preferences);
            int contentY = current.getY() + CONTROL_HEIGHT + 1;
            DarkModeTheme.attentionTarget(graphics, detail.x(), contentY, detail.width(),
                    Math.max(0, open.getY() - GAP - contentY), frame, preferences);
            drawRecipe(graphics, mouseX, mouseY, suggestionsOpen, frame);
        } else {
            attention.clear();
        }
        if (!suggestionsOpen) drawIngredientTooltip(graphics, mouseX, mouseY);
        search.renderSuggestions(graphics, mouseX, mouseY, screenHeight);
        modSearch.renderSuggestions(graphics, mouseX, mouseY, screenHeight);
    }

    /** 在 Screen 的默认点击分派之前调用，给下拉区域输入优先权。 */
    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenHeight) {
        refreshData();
        return search.mouseClickedSuggestion(mouseX, mouseY, button, screenHeight)
                || modSearch.mouseClickedSuggestion(mouseX, mouseY, button, screenHeight);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta, int screenHeight) {
        refreshData();
        if (search.mouseScrolledSuggestions(mouseX, mouseY, delta, screenHeight)
                || modSearch.mouseScrolledSuggestions(mouseX, mouseY, delta, screenHeight)) return true;
        if (!list.contains(mouseX, mouseY) || delta == 0 || !available) return false;
        scroll(delta > 0 ? -1 : 1);
        return true;
    }

    /** 在 Screen 的默认 Esc 处理前调用。 */
    public boolean dismissSuggestions() {
        return search.dismissSuggestions() | modSearch.dismissSuggestions();
    }

    private UiButton button(String key, int x, int y, int width, Runnable action) {
        Component label = text(key);
        var button = new UiButton(x, y, width, CONTROL_HEIGHT, label, ignored -> action.run(), preferences, false);
        button.setPreview(text(key + ".preview"));
        return button;
    }

    private void refreshData() {
        boolean ready = catalog.isAvailable();
        long version = catalog.revision();
        long currentRecipeRevision = JeiIntegration.revision();
        Language currentLanguage = Language.getInstance();
        if (ready == available && version == revision && currentLanguage == language) {
            if (recipeRevision != currentRecipeRevision) {
                recipeRevision = currentRecipeRevision;
                refreshRecipe();
                updateControls();
            }
            return;
        }
        recipeRevision = currentRecipeRevision;
        available = ready;
        revision = version;
        language = currentLanguage;
        var entries = new ArrayList<SearchIndex.Entry<Ingredient>>();
        var mods = new LinkedHashMap<String, SearchIndex.Entry<String>>();
        if (available) {
            for (Ingredient ingredient : catalog.ingredients()) {
                if (!ingredient.visible() || ingredient.name().isBlank()) continue;
                var id = ingredient.id();
                entries.add(new SearchIndex.Entry<>(ingredient, ingredient.name(), List.of(id.toString()), ingredient.aliases()));
                mods.computeIfAbsent(id.getNamespace(), namespace -> {
                    String name = ingredient.modName();
                    return new SearchIndex.Entry<>(namespace, name.isBlank() ? namespace : name, List.of(namespace), List.of());
                });
            }
        }
        index = new SearchIndex<>(entries);
        search.setIndex(index);
        modSearch.setIndex(new SearchIndex<>(List.copyOf(mods.values())));
        selected = null;
        refreshResults(false);
    }

    private boolean inModScope(Ingredient ingredient) {
        return modQuery.isEmpty() || ingredient.id().getNamespace().contains(modQuery)
                || ingredient.modName().toLowerCase(Locale.ROOT).contains(modQuery);
    }

    private boolean inScope(Ingredient ingredient) {
        return ingredient.visible() && filter.test(ingredient) && inModScope(ingredient);
    }

    private void refreshResults(boolean resetScroll) {
        results = index.search(search.getValue(), this::inScope);
        if (resetScroll) firstResult = 0;
        else if (available) firstResult = Math.min(firstResult, maxScroll());
        Ingredient match = null;
        if (selectedKey != null) {
            for (var result : results) {
                if (selectedKey.equals(result.value().key())) {
                    match = result.value();
                    break;
                }
            }
        }
        if (selected != match) {
            selected = match;
            refreshRecipe();
        } else if (match == null) {
            recipe = null;
            layout = null;
        }
        updateControls();
    }

    private void select(Ingredient ingredient) {
        selectedKey = ingredient.key();
        if (selected == ingredient) return;
        selected = ingredient;
        refreshRecipe();
        updateControls();
    }

    private void setDirection(Direction direction) {
        if (this.direction == direction) return;
        this.direction = direction;
        refreshRecipe();
        updateControls();
    }

    private void refreshRecipe() {
        List<Recipe> recipes = selected == null ? List.of() : JeiIntegration.recipes(selected, direction, 1);
        recipe = recipes.isEmpty() ? null : recipes.getFirst();
        layout = recipe == null ? null : recipe.layout().orElse(null);
    }

    private void openNative() {
        refreshData();
        if (selected != null && recipe != null) JeiIntegration.open(selected, direction);
    }

    private void chooseSelected() {
        refreshData();
        if (selected != null && onChoose != null) onChoose.accept(selected);
    }

    private int maxScroll() {
        return Math.max(0, results.size() - rows.size());
    }

    private boolean scroll(int amount) {
        int next = Math.clamp(firstResult + amount, 0, maxScroll());
        if (firstResult == next) return false;
        firstResult = next;
        updateControls();
        return true;
    }

    private void updateControls() {
        search.active = modSearch.active = available;
        for (ResultRow row : rows) {
            row.setIngredient(row.row < results.size() - firstResult ? results.get(firstResult + row.row).value() : null);
        }
        boolean hasSelection = selected != null;
        obtaining.visible = uses.visible = open.visible = hasSelection;
        obtaining.active = uses.active = hasSelection;
        obtaining.setSelected(hasSelection && direction == Direction.OBTAINING);
        uses.setSelected(hasSelection && direction == Direction.USES);
        open.active = recipe != null;
        choose.visible = choose.active = hasSelection && onChoose != null;
        boolean staged = hasSelection && chosen.test(selected);
        choose.setSelected(staged);
        choose.setMessage(text(staged ? "unchoose" : "choose"));
        choose.setPreview(text(staged ? "unchoose.preview" : "choose.preview"));
        open.setWidth(choose.visible ? (detail.width() - GAP) / 2 : detail.width());
    }

    private void drawRecipe(GuiGraphics graphics, int mouseX, int mouseY, boolean suggestionsOpen,
                            UiAttention.Frame frame) {
        int titleY = obtaining.getY() + CONTROL_HEIGHT + 2;
        int contentY = titleY + font.lineHeight + GAP;
        int contentBottom = open.getY() - GAP;
        int width = detail.width() - 2 * GAP;
        if (titleY + font.lineHeight > contentBottom) return;
        boolean layoutFits = layout != null && layout.width() > 0 && layout.height() > 0
                && layout.width() <= width && layout.height() <= contentBottom - contentY;
        boolean secondLineFits = contentY + font.lineHeight <= contentBottom;
        Component title = recipe == null ? NO_RECIPES
                : layoutFits || secondLineFits ? recipe.title() : PREVIEW_UNAVAILABLE;
        int lastTextY = secondLineFits && !layoutFits ? contentY : titleY;
        int textOffset = Math.min(Math.round(frame.contentOffsetY()),
                Math.max(0, contentBottom - lastTextY - font.lineHeight));
        int movingTitleY = titleY + textOffset;
        GuiRenderUtil.withAttentionContent(graphics, frame.contentAlpha(), 0f, () ->
                drawText(graphics, title.getString(), detail.x() + GAP, movingTitleY, width,
                        DarkModeTheme.TEXT_SECONDARY));
        if (recipe == null) return;

        if (!layoutFits) {
            if (secondLineFits) {
                GuiRenderUtil.withAttentionContent(graphics, frame.contentAlpha(), 0f, () ->
                        drawText(graphics, PREVIEW_UNAVAILABLE.getString(), detail.x() + GAP,
                                contentY + textOffset, width, DarkModeTheme.TEXT_SECONDARY));
            }
            return;
        }
        int previewX = detail.x() + (detail.width() - layout.width()) / 2;
        int previewY = contentY + (contentBottom - contentY - layout.height()) / 2
                + Math.round(frame.contentOffsetY());
        graphics.enableScissor(detail.x() + GAP, contentY, detail.right() - GAP, contentBottom);
        try {
            GuiRenderUtil.withAttentionContent(graphics, frame.contentAlpha(), 0f, () ->
                    layout.render(graphics, previewX, previewY, suggestionsOpen ? -1 : mouseX,
                            suggestionsOpen ? -1 : mouseY));
        } finally {
            graphics.disableScissor();
        }
        if (!suggestionsOpen && frame.contentAlpha() > 0f
                && mouseX >= previewX && mouseX < previewX + layout.width()
                && mouseY >= Math.max(previewY, contentY) && mouseY < Math.min(previewY + layout.height(), contentBottom)) {
            layout.renderOverlays(graphics, mouseX, mouseY);
        }
    }

    private void drawIngredientTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (selected != null && mouseX >= detail.x() && mouseX < detail.right()
                && mouseY >= detail.y() && mouseY < detail.y() + ROW_HEIGHT) {
            selected.renderTooltip(graphics, mouseX, mouseY);
            return;
        }
        for (ResultRow row : rows) {
            if (row.ingredient != null && row.isMouseOver(mouseX, mouseY)) {
                row.ingredient.renderTooltip(graphics, mouseX, mouseY);
                return;
            }
        }
    }

    private void drawIcon(GuiGraphics graphics, Ingredient ingredient, int x, int y) {
        graphics.enableScissor(x, y, x + ICON, y + ICON);
        try {
            ingredient.render(graphics, x + Math.max(0, ICON - ingredient.width()) / 2,
                    y + Math.max(0, ICON - ingredient.height()) / 2);
        } finally {
            graphics.disableScissor();
        }
    }

    private void drawText(GuiGraphics graphics, String text, int x, int y, int width, int color) {
        graphics.drawString(font, font.plainSubstrByWidth(text, width), x, y, color, false);
    }

    private static Component text(String key) {
        return Component.translatable("sh_better_ui.jei." + key);
    }

    private record Bounds(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }

        boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    private final class ResultRow extends Button {
        private final int row;
        private Ingredient ingredient;

        private ResultRow(int row) {
            super(list.x(), list.y() + row * ROW_HEIGHT, list.width(), ROW_HEIGHT, Component.empty(),
                    button -> {}, DEFAULT_NARRATION);
            this.row = row;
        }

        private void setIngredient(Ingredient ingredient) {
            this.ingredient = ingredient;
            visible = active = ingredient != null;
            setMessage(ingredient == null ? Component.empty() : Component.literal(ingredient.name()));
        }

        @Override
        public void onPress() {
            if (ingredient != null) select(ingredient);
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            super.updateWidgetNarration(output);
            if (ingredient != null && ingredient == selected) {
                output.add(NarratedElementType.HINT, Component.translatable("sh_better_ui.jei.row_selected", ingredient.name()));
            }
            if (ingredient != null && chosen.test(ingredient)) {
                output.add(NarratedElementType.HINT,
                        Component.translatable("sh_better_ui.picker.row_chosen", ingredient.name()));
            }
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (active && isFocused() && modifiers == 0) {
                if (key == GLFW.GLFW_KEY_UP && row == 0 && scroll(-1)) return true;
                if (key == GLFW.GLFW_KEY_DOWN && row == rows.size() - 1 && scroll(1)) return true;
                if (key == GLFW.GLFW_KEY_PAGE_UP && scroll(-rows.size())) return true;
                if (key == GLFW.GLFW_KEY_PAGE_DOWN && scroll(rows.size())) return true;
            }
            return super.keyPressed(key, scanCode, modifiers);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            if (ingredient == null) return;
            int x = getX();
            int y = getY();
            boolean previewed = ingredient == selected;
            boolean staged = chosen.test(ingredient);
            int border = isFocused() ? DarkModeTheme.TEXT : previewed ? DarkModeTheme.ACCENT : DarkModeTheme.BORDER;
            graphics.fill(x, y, x + getWidth(), y + ROW_HEIGHT,
                    isHoveredOrFocused() ? DarkModeTheme.BORDER : DarkModeTheme.SURFACE);
            graphics.renderOutline(x, y, getWidth(), ROW_HEIGHT, border);
            if (previewed) graphics.fill(x + 1, y + 1, x + 3, y + ROW_HEIGHT - 1, DarkModeTheme.ACCENT);
            if (staged) {
                int markX = x + getWidth() - 12;
                graphics.renderOutline(markX, y + 6, 8, 8, DarkModeTheme.TEXT);
                graphics.fill(markX + 2, y + 8, markX + 6, y + 12, DarkModeTheme.TEXT);
            }
            drawIcon(graphics, ingredient, x + 3, y + 2);
            drawText(graphics, getMessage().getString(), x + 23, y + (ROW_HEIGHT - font.lineHeight) / 2,
                    getWidth() - (staged ? 39 : 27), DarkModeTheme.TEXT);
        }
    }
}
