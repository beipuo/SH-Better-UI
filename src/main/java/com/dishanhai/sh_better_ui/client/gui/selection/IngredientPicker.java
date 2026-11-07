package com.dishanhai.sh_better_ui.client.gui.selection;

import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.search.JeiSearchPanel;
import com.dishanhai.sh_better_ui.client.gui.search.SearchField;
import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** 山海：使用共用搜索面板的物品与流体选取入口；草稿只在确认后交回调用画面。 */
public final class IngredientPicker {
    private IngredientPicker() {}

    public record Request(Component title, IngredientCatalog catalog, Predicate<? super Ingredient> baseFilter,
                          int maxSelections, List<Ingredient> initial, UiPreferences preferences) {
        public Request {
            title = Objects.requireNonNull(title).copy();
            Objects.requireNonNull(catalog);
            Objects.requireNonNull(baseFilter);
            Objects.requireNonNull(preferences);
            initial = new IngredientSelection(maxSelections, initial).values();
        }

        public Request(Component title, IngredientCatalog catalog, int maxSelections) {
            this(title, catalog, ingredient -> true, maxSelections, List.of(), UiPreferences.DEFAULT);
        }

        public static Request single(Component title, IngredientCatalog catalog) {
            return new Request(title, catalog, 1);
        }

        public static Request multiple(Component title, IngredientCatalog catalog, int maxSelections) {
            return new Request(title, catalog, maxSelections);
        }
    }

    /** 仅从当前画面打开；空结果可确认以清空筛选，取消不会调用 onConfirm。 */
    public static boolean open(Screen parent, Request request, Supplier<? extends GuiEventListener> restoreFocus,
                               Consumer<? super List<Ingredient>> onConfirm) {
        Minecraft client = Minecraft.getInstance();
        if (parent == null || client.screen != parent) return false;
        var picker = new PickerScreen(parent, Objects.requireNonNull(request),
                Objects.requireNonNull(restoreFocus), Objects.requireNonNull(onConfirm));
        client.setScreen(picker);
        return client.screen == picker;
    }

    private static Component text(String key, Object... arguments) {
        return Component.translatable("sh_better_ui.picker." + key, arguments);
    }

    /** 完整标签 ID 精确匹配，尚未选中完整 ID 的输入按标签 ID 子串筛选。 */
    static Predicate<Ingredient> tagFilter(String query, Set<ResourceLocation> knownTags) {
        String normalized = query.strip().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("#")) normalized = normalized.substring(1);
        if (normalized.isEmpty()) return ingredient -> true;
        ResourceLocation exact = ResourceLocation.tryParse(normalized);
        if (exact != null && exact.toString().equals(normalized) && knownTags.contains(exact)) {
            return ingredient -> ingredient.tags().contains(exact);
        }
        String fragment = normalized;
        return ingredient -> ingredient.tags().stream().anyMatch(tag -> tag.toString().contains(fragment));
    }

    private static final class PickerScreen extends Screen {
        private static final int MARGIN = 6;
        private static final int GAP = 4;
        private static final int CONTROL_HEIGHT = 18;
        private static final int MIN_WIDTH = 220;
        private final Screen parent;
        private final Request request;
        private final Supplier<? extends GuiEventListener> restoreFocus;
        private final Consumer<? super List<Ingredient>> onConfirm;
        private final IngredientSelection selection;
        private JeiSearchPanel panel;
        private JeiSearchPanel.Snapshot view;
        private JeiSearchPanel.Snapshot browsingView;
        private SearchField<ResourceLocation> tagSearch;
        private UiButton all, items, fluids, selectedOnlyButton, confirm, clear, undo, cancel;
        private SearchIndex<ResourceLocation> tagIndex = new SearchIndex<>(List.of());
        private Set<ResourceLocation> knownTags = Set.of();
        private Predicate<Ingredient> tagFilter = ingredient -> true;
        private long catalogRevision = Long.MIN_VALUE;
        private boolean available;
        private boolean selectedOnly;
        private Kind kind;
        private String tagQuery = "";
        private Component notice;
        private int countY;
        private boolean compact;

        private PickerScreen(Screen parent, Request request, Supplier<? extends GuiEventListener> restoreFocus,
                             Consumer<? super List<Ingredient>> onConfirm) {
            super(request.title());
            this.parent = parent;
            this.request = request;
            this.restoreFocus = restoreFocus;
            this.onConfirm = onConfirm;
            selection = new IngredientSelection(request.maxSelections(), request.initial());
        }

        @Override
        protected void init() {
            saveView();
            panel = null;
            tagSearch = null;
            refreshCatalog(true);
            int actionsY = Math.max(0, height - MARGIN - CONTROL_HEIGHT);
            countY = actionsY - font.lineHeight - GAP;
            int filterY = MARGIN + font.lineHeight + 5;
            int tagY = filterY + CONTROL_HEIGHT + GAP;
            int panelY = tagY + CONTROL_HEIGHT + GAP;
            int panelHeight = countY - MARGIN - panelY;
            compact = width < MIN_WIDTH || panelHeight < 132;
            if (compact) {
                int buttonWidth = Math.max(1, Math.min(120, width - 2 * MARGIN));
                cancel = addRenderableWidget(button("cancel", (width - buttonWidth) / 2, actionsY, buttonWidth,
                        () -> finish(false), false));
                setInitialFocus();
                return;
            }

            int innerWidth = width - 2 * MARGIN;
            int typeWidth = Math.min(56, (innerWidth - 3 * GAP) / 4);
            all = addRenderableWidget(button("kind.all", MARGIN, filterY, typeWidth, () -> setKind(null), false));
            items = addRenderableWidget(button("kind.item", MARGIN + typeWidth + GAP, filterY, typeWidth,
                    () -> setKind(Kind.ITEM), false));
            fluids = addRenderableWidget(button("kind.fluid", MARGIN + 2 * (typeWidth + GAP), filterY, typeWidth,
                    () -> setKind(Kind.FLUID), false));
            int selectedX = MARGIN + 3 * (typeWidth + GAP);
            selectedOnlyButton = addRenderableWidget(button("selected_only", selectedX, filterY,
                    width - MARGIN - selectedX, this::toggleSelectedOnly, false));

            tagSearch = addRenderableWidget(new SearchField<>(font, MARGIN, tagY, innerWidth,
                    CONTROL_HEIGHT, tagIndex));
            tagSearch.setMessage(text("tag"));
            tagSearch.setHint(text("tag"));
            tagSearch.setTooltip(Tooltip.create(text("tag.preview")));
            tagSearch.setValue(tagQuery);
            tagSearch.setResponder(query -> {
                tagQuery = query;
                applyFilter();
            });
            tagSearch.setOnSubmit(ignored -> applyFilter());

            panel = new JeiSearchPanel(font, MARGIN, panelY, innerWidth, panelHeight,
                    request.preferences(), request.catalog());
            tagFilter = tagFilter(tagQuery, knownTags);
            panel.setFilter(this::matchesView);
            panel.setChosen(selection::contains);
            panel.setOnChoose(this::choose);
            if (view != null) panel.restore(view);
            for (var widget : panel.widgets()) addRenderableWidget(widget);

            int buttonWidth = (innerWidth - 3 * GAP) / 4;
            int step = buttonWidth + GAP;
            clear = addRenderableWidget(button("clear", MARGIN, actionsY, buttonWidth, this::clearSelection, false));
            undo = addRenderableWidget(button("undo", MARGIN + step, actionsY, buttonWidth,
                    this::undoSelection, false));
            undo.setPreview(text("undo.preview"));
            cancel = addRenderableWidget(button("cancel", MARGIN + 2 * step, actionsY, buttonWidth,
                    () -> finish(false), false));
            confirm = addRenderableWidget(button("confirm", MARGIN + 3 * step, actionsY,
                    innerWidth - 3 * step, () -> finish(true), true));
            JeiIntegration.setExtraAreas(this, this::extraAreas);
            updateControls();
            setInitialFocus();
        }

        // NeoForge 在 init 后也会调用此方法。
        protected void setInitialFocus() {
            if (cancel != null && getFocused() == null) {
                setInitialFocus(panel != null && available ? panel.widgets().getFirst() : cancel);
            }
        }

        @Override
        protected void repositionElements() {
            GuiEventListener focused = getFocused();
            super.repositionElements();
            if (!(focused instanceof AbstractWidget previous)
                    || !(previous instanceof UiButton || previous instanceof SearchField<?>)) return;
            for (GuiEventListener child : children()) {
                if (child instanceof AbstractWidget widget && widget.active && widget.visible
                        && widget.getClass() == previous.getClass() && widget.getMessage().equals(previous.getMessage())) {
                    if (widget != getFocused()) setInitialFocus(widget);
                    break;
                }
            }
        }

        private UiButton button(String key, int x, int y, int width, Runnable action, boolean primary) {
            return new UiButton(x, y, width, CONTROL_HEIGHT, text(key), ignored -> action.run(),
                    request.preferences(), primary);
        }

        private void saveView() {
            if (panel != null) view = panel.snapshot();
            if (tagSearch != null) tagQuery = tagSearch.getValue();
        }

        private List<Rect2i> extraAreas(int screenHeight) {
            var areas = new ArrayList<>(panel.extraAreas(screenHeight));
            areas.add(new Rect2i(0, 0, width, height));
            tagSearch.suggestionArea(screenHeight).ifPresent(areas::add);
            return List.copyOf(areas);
        }

        private boolean refreshCatalog(boolean force) {
            boolean ready = request.catalog().isAvailable();
            long revision = request.catalog().revision();
            if (!force && available == ready && catalogRevision == revision) return false;
            available = ready;
            catalogRevision = revision;
            List<Ingredient> source = ready ? List.copyOf(request.catalog().ingredients()) : List.of();
            if (ready) {
                int before = selection.size();
                selection.reconcile(source, request.baseFilter());
                if (selection.size() < before) notice = text("removed", before - selection.size());
            }
            var tags = new LinkedHashSet<ResourceLocation>();
            for (Ingredient ingredient : source) {
                if (ingredient.visible() && request.baseFilter().test(ingredient)) tags.addAll(ingredient.tags());
            }
            knownTags = Set.copyOf(tags);
            var entries = new ArrayList<SearchIndex.Entry<ResourceLocation>>(tags.size());
            for (ResourceLocation tag : tags) {
                entries.add(new SearchIndex.Entry<>(tag, tag.toString(), List.of(tag.toString()), List.of()));
            }
            tagIndex = new SearchIndex<>(entries);
            if (tagSearch != null) tagSearch.setIndex(tagIndex);
            return true;
        }

        private boolean matchesView(Ingredient ingredient) {
            if (!request.baseFilter().test(ingredient)) return false;
            if (selectedOnly) return selection.contains(ingredient);
            return (kind == null || ingredient.kind() == kind) && tagFilter.test(ingredient);
        }

        private void applyFilter() {
            tagFilter = tagFilter(tagQuery, knownTags);
            if (panel != null) panel.setFilter(this::matchesView);
            updateControls();
        }

        private void setKind(Kind next) {
            kind = next;
            applyFilter();
        }

        private void toggleSelectedOnly() {
            if (!selectedOnly) {
                browsingView = panel.snapshot();
                selectedOnly = true;
                applyFilter();
                panel.restore(new JeiSearchPanel.Snapshot("", "", browsingView.selectedKey(), 0,
                        browsingView.direction()));
            } else {
                selectedOnly = false;
                applyFilter();
                if (browsingView != null) panel.restore(browsingView);
                browsingView = null;
            }
        }

        private void choose(Ingredient ingredient) {
            if (!selection.toggle(ingredient)) notice = text("limit", request.maxSelections());
            else notice = null;
            panel.refreshSelection();
            if (selectedOnly) panel.setFilter(this::matchesView);
            updateControls();
        }

        private void clearSelection() {
            selection.clear();
            notice = null;
            panel.refreshSelection();
            if (selectedOnly) panel.setFilter(this::matchesView);
            updateControls();
        }

        private void undoSelection() {
            if (!selection.undo()) return;
            notice = null;
            refreshCatalog(true);
            syncCatalogView();
        }

        private void syncCatalogView() {
            var snapshot = panel.snapshot();
            panel.tick();
            applyFilter();
            if (snapshot.firstResult() > 0) panel.restore(snapshot);
        }

        private Component summary() {
            if (notice != null) return notice;
            if (selection.isAtLimit()) return text("limit", request.maxSelections());
            return text("count", selection.size(), request.maxSelections());
        }

        private void updateControls() {
            if (panel == null) return;
            all.setSelected(kind == null);
            items.setSelected(kind == Kind.ITEM);
            fluids.setSelected(kind == Kind.FLUID);
            all.active = items.active = fluids.active = tagSearch.active = available && !selectedOnly;
            selectedOnlyButton.setMessage(text("selected_only", selection.size()));
            selectedOnlyButton.setSelected(selectedOnly);
            selectedOnlyButton.setPreview(text("selected_only.preview").copy().append("\n").append(summary()));
            clear.active = selection.size() > 0;
            undo.active = selection.canUndo();
            confirm.active = available;
            confirm.setPreview(text("confirm.preview", selection.size()));
        }

        private void finish(boolean confirmed) {
            if (selection.isFinished() || minecraft.screen != this) return;
            if (confirmed) {
                if (panel == null) return;
                int before = selection.size();
                refreshCatalog(true);
                syncCatalogView();
                if (!available || selection.size() < before) return;
            }
            saveView();
            minecraft.setScreen(parent);
            if (minecraft.screen != parent) return;
            GuiEventListener target = restoreFocus.get();
            if (target != null && parent.children().contains(target)) parent.setFocused(target);
            selection.finish(confirmed).ifPresent(onConfirm);
        }

        @Override
        public void tick() {
            boolean changed = refreshCatalog(false);
            if (panel != null) {
                if (changed) syncCatalogView();
                else panel.tick();
                if (!available && getFocused() instanceof AbstractWidget widget && !widget.active) {
                    setInitialFocus(cancel);
                }
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            DarkModeTheme.background(graphics, 0, 0, width, height);
            graphics.drawString(font, font.plainSubstrByWidth(title.getString(), Math.max(1, width - 2 * MARGIN)),
                    MARGIN, MARGIN, DarkModeTheme.TEXT, false);
            if (compact) {
                int y = MARGIN + font.lineHeight + GAP;
                for (var line : font.split(text("too_small"), Math.max(1, width - 2 * MARGIN))) {
                    if (y + font.lineHeight > height - CONTROL_HEIGHT - 2 * MARGIN) break;
                    graphics.drawString(font, line, MARGIN, y, DarkModeTheme.TEXT_SECONDARY, false);
                    y += font.lineHeight + 2;
                }
            } else {
                panel.renderBackground(graphics);
                graphics.drawString(font, font.plainSubstrByWidth(summary().getString(), width - 2 * MARGIN),
                        MARGIN, countY, DarkModeTheme.TEXT_SECONDARY, false);
            }
            super.render(graphics, mouseX, mouseY, partialTick);
            if (panel != null) {
                boolean overTagSuggestions = tagSearch.suggestionArea(height)
                        .filter(area -> area.contains(mouseX, mouseY)).isPresent();
                panel.renderOverlays(graphics, overTagSuggestions ? -1 : mouseX,
                        overTagSuggestions ? -1 : mouseY, height);
                tagSearch.renderSuggestions(graphics, mouseX, mouseY, height);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (panel != null && (tagSearch.mouseClickedSuggestion(mouseX, mouseY, button, height)
                    || panel.mouseClicked(mouseX, mouseY, button, height))) return true;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // Forge 和 NeoForge 分别使用垂直滚轮与双轴滚轮签名。
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            return panel != null && (tagSearch.mouseScrolledSuggestions(mouseX, mouseY, delta, height)
                    || panel.mouseScrolled(mouseX, mouseY, delta, height));
        }

        public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
            return mouseScrolled(mouseX, mouseY, deltaY);
        }

        @Override
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key == GLFW.GLFW_KEY_ESCAPE && panel != null
                    && (tagSearch.dismissSuggestions() | panel.dismissSuggestions())) return true;
            return super.keyPressed(key, scanCode, modifiers);
        }

        @Override
        public void onClose() { finish(false); }

        @Override
        public Component getNarrationMessage() {
            return compact ? title.copy().append("\n").append(text("too_small")) : super.getNarrationMessage();
        }

        @Override
        public void removed() {
            saveView();
            JeiIntegration.setExtraAreas(this, null);
        }
    }
}
