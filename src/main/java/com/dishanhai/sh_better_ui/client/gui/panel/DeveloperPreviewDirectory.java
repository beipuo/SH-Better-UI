package com.dishanhai.sh_better_ui.client.gui.panel;

import com.dishanhai.sh_better_ui.client.api.preview.PreviewRegistry;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.blur.GaussBlur;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.search.SearchField;
import com.dishanhai.sh_better_ui.client.gui.search.SearchIndex;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.dishanhai.sh_better_ui.client.gui.panel.DeveloperPanelScreen.label;

/** 山海：按当前分类搜索已注册的开发者预览，只有进入样例时才创建场景。 */
final class DeveloperPreviewDirectory extends Screen {
    private static final int PADDING = 10;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_PITCH = ROW_HEIGHT + 4;
    private static final int CHROME_HEIGHT = 99;
    private static final int ARROW_WIDTH = 24;
    private static final Component BACK = Component.translatable("gui.back");
    private static final Component TOO_SMALL = Component.translatable("sh_better_ui.picker.too_small");

    static {
        GaussBlur.allowScreen(screen -> screen instanceof DeveloperPreviewDirectory);
    }

    private final DeveloperGalleryScreen parent;
    private final UiPreferences preferences;
    private final PreviewSpec.Category category;
    private final PreviewRegistry registry;
    private final List<UiButton> rows = new ArrayList<>();
    private List<PreviewSpec> entries = List.of();
    private SearchIndex<PreviewSpec> index = new SearchIndex<>(List.of());
    private List<SearchIndex.Entry<PreviewSpec>> matches = List.of();
    private Bounds panel;
    private SearchField<PreviewSpec> search;
    private UiButton clear;
    private UiButton previous;
    private UiButton next;
    private PreviewSpec selected;
    private String query = "";
    private int page;
    private boolean revealSelection;
    private boolean returning;

    DeveloperPreviewDirectory(DeveloperGalleryScreen parent, UiPreferences preferences,
                              PreviewSpec.Category category, PreviewRegistry registry) {
        super(label("gallery.directory", Component.translatable(category.titleKey())));
        this.parent = parent;
        this.preferences = preferences;
        this.category = category;
        this.registry = registry;
    }

    void open(PreviewSpec selected) {
        this.selected = selected;
        revealSelection = query.isBlank() && selected != null;
    }

    @Override
    protected void init() {
        rows.clear();
        panel = null;
        if (width < 212 || height < CHROME_HEIGHT + ROW_PITCH + 8) {
            int buttonWidth = Math.min(100, width - 8);
            if (buttonWidth >= 24 && height >= ROW_HEIGHT + 8) {
                addButton((width - buttonWidth) / 2, height - ROW_HEIGHT - 4, buttonWidth,
                        BACK, GraphicIcon.BACK, this::onClose);
            }
            return;
        }
        int rowCount = Math.min(8, (height - CHROME_HEIGHT - 8) / ROW_PITCH);
        int panelWidth = Math.min(width - 12, 400);
        int panelHeight = CHROME_HEIGHT + rowCount * ROW_PITCH;
        panel = new Bounds((width - panelWidth) / 2, (height - panelHeight) / 2, panelWidth, panelHeight);
        int x = panel.x() + PADDING;
        int right = panel.right() - PADDING;
        int searchY = panel.y() + 37;
        search = addRenderableWidget(new SearchField<>(font, x, searchY, right - x - ARROW_WIDTH - 4, ROW_HEIGHT,
                new SearchIndex<>(List.of()), UiStyle.LIQUID_GLASS, preferences));
        search.setSampleBackground(false);
        search.setMessage(label("gallery.directory_search_hint"));
        search.setTooltip(null);
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            page = 0;
            updateMatches();
        });
        search.setOnSubmit(value -> {
            if (!value.isBlank() && !matches.isEmpty()) choose(matches.getFirst().value());
        });
        clear = addButton(right - ARROW_WIDTH, searchY, ARROW_WIDTH, label("gallery.directory_clear"), null, () -> {
            search.setValue("");
            setInitialFocus(search);
        });
        clear.setIcon(GraphicIcon.RESET);

        int rowY = searchY + ROW_PITCH;
        for (int i = 0; i < rowCount; i++) {
            int slot = i;
            rows.add(addButton(x, rowY + i * ROW_PITCH, right - x, Component.empty(), null, () -> {
                int item = page * rows.size() + slot;
                if (item < matches.size()) choose(matches.get(item).value());
            }));
        }
        int footer = panel.bottom() - PADDING - ROW_HEIGHT;
        previous = addButton(x, footer, ARROW_WIDTH, label("gallery.directory_previous"), null, () -> turn(-1));
        previous.setIcon(GraphicIcon.PREVIOUS);
        next = addButton(x + ARROW_WIDTH + 4, footer, ARROW_WIDTH, label("gallery.directory_next"), null, () -> turn(1));
        next.setIcon(GraphicIcon.NEXT);
        addButton(right - 60, footer, 60, BACK, GraphicIcon.BACK, this::onClose);
        refreshIndex();
        if (revealSelection) {
            for (int i = 0; i < matches.size(); i++) {
                if (matches.get(i).value() == selected) {
                    page = i / rows.size();
                    break;
                }
            }
            revealSelection = false;
            updateRows();
        }
        setInitialFocus(search);
    }

    private UiButton addButton(int x, int y, int width, Component text, GraphicIcon icon, Runnable action) {
        return addRenderableWidget(DeveloperPanelScreen.button(x, y, width, ROW_HEIGHT, text, icon, preferences, action));
    }

    @Override
    public void tick() {
        if (panel != null && entries != registry.entries()) refreshIndex();
    }

    private void refreshIndex() {
        entries = registry.entries();
        index = new SearchIndex<>(entries.stream().filter(spec -> spec.category() == category).map(spec -> {
            String name = Component.translatable(spec.titleKey()).getString();
            return new SearchIndex.Entry<>(spec, name.isBlank() ? spec.id() : name, List.of(spec.id()), List.of());
        }).toList());
        updateMatches();
    }

    private void updateMatches() {
        matches = index.search(query);
        page = Math.clamp(page, 0, pageCount() - 1);
        clear.active = !query.isEmpty();
        updateRows();
    }

    private int pageCount() {
        return Math.max(1, Math.ceilDiv(matches.size(), rows.size()));
    }

    private void updateRows() {
        int first = page * rows.size();
        for (int i = 0; i < rows.size(); i++) {
            UiButton row = rows.get(i);
            row.visible = first + i < matches.size();
            row.active = row.visible;
            if (row.visible) {
                PreviewSpec spec = matches.get(first + i).value();
                row.setMessage(Component.translatable(spec.titleKey()));
                row.setSelected(spec == selected);
                row.setPreview(row.getMessage().copy().append("\n").append(spec.id()));
            }
        }
        previous.active = page > 0;
        next.active = page + 1 < pageCount();
        if (getFocused() instanceof UiButton button && !button.active) setInitialFocus(search);
    }

    private boolean turn(int direction) {
        int target = Math.clamp(page + direction, 0, pageCount() - 1);
        if (target == page) return false;
        page = target;
        updateRows();
        minecraft.getNarrator().say(pageLabel());
        return true;
    }

    private Component pageLabel() {
        return label("gallery.directory_page", page + 1, pageCount(), matches.size());
    }

    private void choose(PreviewSpec spec) {
        if (registry.find(spec.id()).orElse(null) == spec) returnToGallery(spec.id());
        else refreshIndex();
    }

    // NeoForge 由 Screen.render 调用，Forge 1.20.1 使用不同签名。
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) DarkModeTheme.background(graphics, 0, 0, width, height);
        else graphics.fill(0, 0, width, height, 0x6020252E);
        if (panel == null) {
            GalleryPreviewLayout.drawCentered(graphics, font, TOO_SMALL, 4, 4, Math.max(1, width - 8));
        } else {
            LiquidGlassTheme.popover(graphics, panel.x(), panel.y(), panel.width(), panel.height(), preferences);
            int x = panel.x() + PADDING;
            int contentWidth = panel.width() - 2 * PADDING;
            graphics.drawString(font, font.substrByWidth(getTitle(), contentWidth).getString(),
                    x, panel.y() + PADDING, LiquidGlassTheme.textColor(), false);
            graphics.drawString(font, font.substrByWidth(label("gallery.directory_search"), contentWidth).getString(),
                    x, search.getY() - 12, LiquidGlassTheme.mutedTextColor(), false);
            if (matches.isEmpty()) {
                GalleryPreviewLayout.drawCentered(graphics, font,
                        label(query.isBlank() ? "gallery.empty" : "gallery.directory_no_matches"),
                        x, search.getY() + ROW_PITCH, contentWidth);
            }
            int statusX = next.getX() + next.getWidth() + 6;
            GalleryPreviewLayout.drawCentered(graphics, font, pageLabel(), statusX, next.getY(),
                    panel.right() - PADDING - 66 - statusX);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (panel != null && key == GLFW.GLFW_KEY_PAGE_UP) return turn(-1);
        if (panel != null && key == GLFW.GLFW_KEY_PAGE_DOWN) return turn(1);
        return super.keyPressed(key, scan, modifiers);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return mouseScrolled(mouseX, mouseY, 0, delta);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        return panel != null && panel.contains(mouseX, mouseY) && mouseY >= search.getY() + ROW_PITCH
                && deltaY != 0 && turn(deltaY > 0 ? -1 : 1);
    }

    @Override
    public void onClose() {
        returnToGallery(null);
    }

    private void returnToGallery(String id) {
        returning = true;
        try {
            parent.returnFromDirectory(id);
        } finally {
            returning = false;
        }
    }

    @Override
    public void removed() {
        if (!returning) parent.abandonDirectory();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
