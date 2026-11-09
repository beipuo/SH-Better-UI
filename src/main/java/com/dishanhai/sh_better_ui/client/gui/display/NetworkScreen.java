package com.dishanhai.sh_better_ui.client.gui.display;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.NetworkTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.NetworkToggle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** 山海：可直接打开的网络式界面模板。页面内容、编辑草稿和数据提交由接入方提供。 */
public final class NetworkScreen extends Screen {
    private static final int CLOSE_WIDTH = 20;
    private static final int GAP = 4;
    private static final Component BACK = Component.translatable("gui.back");
    private static final Component TOO_SMALL = Component.translatable("sh_better_ui.picker.too_small");

    public sealed interface Row permits Field, Edit, Status, Toggle {}

    public record Field(Component label, Supplier<Component> value) implements Row {
        public Field {
            Objects.requireNonNull(label);
            Objects.requireNonNull(value);
        }
    }

    /** 每次重建时先从接入方草稿填值，再注册 responder。 */
    public record Edit(Component label, Consumer<EditBox> configure) implements Row {
        public Edit {
            Objects.requireNonNull(label);
            Objects.requireNonNull(configure);
        }
    }

    public record Status(Supplier<GraphicDisplay.Entry> value) implements Row {
        public Status {
            Objects.requireNonNull(value);
        }
    }

    /** value 读取已确认状态，enabled 可在等待服务器确认时返回 false。 */
    public record Toggle(Component label, BooleanSupplier value, BooleanSupplier enabled,
                         Consumer<Boolean> onChange) implements Row {
        public Toggle {
            Objects.requireNonNull(label);
            Objects.requireNonNull(value);
            Objects.requireNonNull(enabled);
            Objects.requireNonNull(onChange);
        }
    }

    public record Tab(Component label, GraphicIcon icon, Supplier<Component> heading, List<Row> rows) {
        public Tab {
            Objects.requireNonNull(label);
            Objects.requireNonNull(icon);
            Objects.requireNonNull(heading);
            rows = List.copyOf(rows);
        }
    }

    public record AddAction(Component label, Runnable onPress) {
        public AddAction {
            Objects.requireNonNull(label);
            Objects.requireNonNull(onPress);
        }
    }

    private final Screen parent;
    private final UiPreferences preferences;
    private final List<Tab> tabs;
    private final AddAction addAction;
    private final int[] pageByTab;
    private final List<UiButton> tabButtons = new ArrayList<>();
    private final List<ToggleBinding> toggles = new ArrayList<>();
    private int selectedTab;
    private NetworkLayout.Result layout;
    private UiButton previous;
    private UiButton next;
    private View pending;

    /** 在客户端线程创建并用 Minecraft.setScreen 打开。parent 和 addAction 均可为 null。 */
    public NetworkScreen(Screen parent, Component title, UiPreferences preferences, List<Tab> tabs,
                         AddAction addAction) {
        super(title);
        this.parent = parent;
        this.preferences = Objects.requireNonNull(preferences);
        this.tabs = List.copyOf(tabs);
        if (this.tabs.isEmpty() || this.tabs.size() > 7) {
            throw new IllegalArgumentException("Expected 1 to 7 network tabs");
        }
        this.addAction = addAction;
        pageByTab = new int[this.tabs.size()];
    }

    @Override
    protected void init() {
        tabButtons.clear();
        toggles.clear();
        previous = next = null;
        Tab tab = tabs.get(selectedTab);
        var kinds = tab.rows().stream().map(NetworkScreen::kind).toList();
        layout = NetworkLayout.arrange(width, height, tabs.size(), addAction != null,
                kinds, font.lineHeight, pageByTab[selectedTab]).orElse(null);
        if (layout == null) {
            if (width >= 24 && height >= 22) {
                int buttonWidth = Math.min(100, width - 8);
                addRenderableWidget(NetworkTheme.tab(new Bounds((width - buttonWidth) / 2, height - 22,
                        buttonWidth, 18), BACK, null, preferences, button -> onClose()));
            }
            return;
        }
        pageByTab[selectedTab] = layout.pageIndex();
        for (int i = 0; i < tabs.size(); i++) {
            int index = i;
            Tab item = tabs.get(i);
            var button = addRenderableWidget(NetworkTheme.tab(layout.tabs().get(i), item.label(), item.icon(),
                    preferences, ignored -> {
                        if (index != selectedTab) pending = new View(index, pageByTab[index]);
                    }));
            button.setSelected(index == selectedTab);
            tabButtons.add(button);
        }
        if (addAction != null) {
            addRenderableWidget(NetworkTheme.tab(layout.action().orElseThrow(), addAction.label(), GraphicIcon.ADD,
                    preferences, button -> addAction.onPress().run()));
        }
        Bounds heading = layout.title();
        addRenderableWidget(NetworkTheme.tab(new Bounds(heading.right() - CLOSE_WIDTH, heading.y(),
                CLOSE_WIDTH, heading.height()), BACK, GraphicIcon.CLOSE, preferences, button -> onClose()));
        for (int i = 0; i < layout.rows().size(); i++) {
            Row row = tab.rows().get(layout.firstRow() + i);
            Bounds area = layout.rows().get(i);
            switch (row) {
                case Edit edit -> {
                    int labelWidth = editLabelWidth(area);
                    var input = NetworkTheme.editBox(font, new Bounds(area.x() + labelWidth, area.y(),
                            area.width() - labelWidth, area.height()), edit.label(), preferences);
                    edit.configure().accept(input);
                    addRenderableWidget(input);
                }
                case Toggle toggle -> {
                    var widget = new NetworkToggle(area, toggle.label(), toggle.value().getAsBoolean(), preferences,
                            ignored -> {
                                if (toggle.enabled().getAsBoolean()) {
                                    toggle.onChange().accept(!toggle.value().getAsBoolean());
                                }
                            });
                    widget.active = toggle.enabled().getAsBoolean();
                    toggles.add(new ToggleBinding(toggle, addRenderableWidget(widget)));
                }
                default -> { }
            }
        }
        if (layout.pageCount() > 1) {
            previous = addRenderableWidget(GraphicDisplay.pageButton(layout.previous(), false, preferences,
                    button -> requestPage(-1)));
            next = addRenderableWidget(GraphicDisplay.pageButton(layout.next(), true, preferences,
                    button -> requestPage(1)));
            previous.active = layout.pageIndex() > 0;
            next.active = layout.pageIndex() + 1 < layout.pageCount();
        }
    }

    private static NetworkLayout.RowKind kind(Row row) {
        return switch (row) {
            case Field ignored -> NetworkLayout.RowKind.FIELD;
            case Edit ignored -> NetworkLayout.RowKind.FIELD;
            case Status ignored -> NetworkLayout.RowKind.STATUS;
            case Toggle ignored -> NetworkLayout.RowKind.TOGGLE;
        };
    }

    private int editLabelWidth(Bounds area) {
        return area.width() / 3;
    }

    private void requestPage(int direction) {
        pending = new View(selectedTab, layout.pageIndex() + direction);
    }

    private void applyPendingView() {
        if (pending == null) return;
        View view = pending;
        pending = null;
        boolean tabChanged = view.tab() != selectedTab;
        boolean forward = view.page() > pageByTab[selectedTab];
        selectedTab = view.tab();
        pageByTab[selectedTab] = view.page();
        // 原生输入分发结束后再重建，避免焦点留在被移除的按钮。
        rebuildWidgets();
        if (layout == null) return;
        UiButton focus = tabButtons.get(selectedTab);
        if (!tabChanged && previous != null) {
            UiButton direction = forward ? next : previous;
            focus = direction.active ? direction : (forward ? previous : next);
        }
        setInitialFocus(focus);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        applyPendingView();
        if (minecraft.level == null) DarkModeTheme.background(graphics, 0, 0, width, height);
        if (layout == null) {
            int y = GAP;
            for (var line : font.split(TOO_SMALL, Math.max(1, width - 2 * GAP))) {
                if (y + font.lineHeight > height - 24) break;
                graphics.drawString(font, line, GAP, y, DarkModeTheme.textColor(), false);
                y += font.lineHeight + 2;
            }
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        for (var binding : toggles) {
            binding.widget().setValue(binding.row().value().getAsBoolean());
            binding.widget().active = binding.row().enabled().getAsBoolean();
        }
        var tip = drawContent(graphics, mouseX, mouseY);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (tip != null) graphics.renderTooltip(font, font.split(tip, Math.max(1, width - 24)), mouseX, mouseY);
    }

    // NeoForge 的 Screen.render 会调用此签名。保留世界背景，面板底色由模板绘制。
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    private Component drawContent(GuiGraphics graphics, int mouseX, int mouseY) {
        Tab tab = tabs.get(selectedTab);
        NetworkTheme.drawPanel(graphics, layout.panel(), preferences);
        Bounds titleArea = layout.title();
        var headingArea = new Bounds(titleArea.x(), titleArea.y(), titleArea.width() - CLOSE_WIDTH - GAP,
                titleArea.height());
        Component heading = tab.heading().get();
        NetworkTheme.drawField(graphics, font, headingArea, heading, preferences);
        Component tip = headingArea.contains(mouseX, mouseY) && font.width(heading) > headingArea.width() - 10
                ? heading : null;
        for (int i = 0; i < layout.rows().size(); i++) {
            Row row = tab.rows().get(layout.firstRow() + i);
            Bounds area = layout.rows().get(i);
            boolean hovered = area.contains(mouseX, mouseY);
            switch (row) {
                case Field field -> {
                    Component label = field.label().copy()
                            .withStyle(style -> style.withColor(NetworkTheme.labelColor(preferences)));
                    Component text = Component.translatable("sh_better_ui.network.field", label, field.value().get());
                    NetworkTheme.drawField(graphics, font, area, text, preferences);
                    if (hovered && font.width(text) > area.width() - 10) tip = text;
                }
                case Edit edit -> {
                    var labelArea = new Bounds(area.x(), area.y(), editLabelWidth(area) - GAP, area.height());
                    NetworkTheme.drawField(graphics, font, labelArea, edit.label(), preferences);
                    if (labelArea.contains(mouseX, mouseY) && font.width(edit.label()) > labelArea.width() - 10) {
                        tip = edit.label();
                    }
                }
                case Status status -> {
                    var entry = status.value().get();
                    var statusLayout = GraphicDisplayLayout.arrange(area, 1, area.height(), false);
                    GraphicDisplay.drawRows(graphics, font, statusLayout, List.of(entry), 1, DarkModeTheme.textColor(), 0);
                    if (hovered) tip = entry.label();
                }
                case Toggle ignored -> { }
            }
        }
        if (layout.pageCount() > 1) {
            var footer = new GraphicDisplayLayout.Result(layout.panel(), List.of(), layout.previous(), layout.next());
            GraphicDisplay.drawPageNumber(graphics, font, footer, layout.pageIndex() + 1, layout.pageCount(),
                    DarkModeTheme.textColor());
        }
        return tip;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private record View(int tab, int page) {}

    private record ToggleBinding(Toggle row, NetworkToggle widget) {}
}
