package com.dishanhai.sh_better_ui.client.gui.search;

import com.dishanhai.sh_better_ui.client.gui.scaled.UiMath;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIcon;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** 山海：原生输入框、前缀预测与可由宿主画面置顶的搜索联想清单。 */
public final class SearchField<T> extends EditBox {
    private static final int SUGGESTION_LIMIT = 8;
    private static final int GAP = 2;
    private static final int BORDERED_TEXT_PADDING = 4;
    private static final int ICON_RIGHT_PADDING = 4;
    private static final int ICON_TEXT_GAP = 8;
    private static final int MIN_TEXT_WIDTH = 32;
    private static final float DROPDOWN_Z = 300f;
    private static final int TOOLTIP_PADDING = 4;
    private static final Component USAGE = Component.translatable("sh_better_ui.search.usage");

    private final Font font;
    private final UiStyle style;
    private final UiPreferences preferences;
    private SearchIndex<T> index;
    private Predicate<? super T> scope = value -> true;
    private Predicate<String> filter = text -> true;
    private Consumer<String> onChange;
    private Consumer<String> onSubmit;
    private Component hint;
    private String observedQuery = "";
    private String undoQuery;
    private List<SearchIndex.Suggestion<T>> suggestions;
    private int maxLength;
    private int selected;
    private int firstVisible;
    private boolean dismissed;
    private boolean bordered = true;
    private boolean sampleBackground = true;

    public SearchField(Font font, int x, int y, int width, int height, SearchIndex<T> index) {
        this(font, x, y, width, height, index, UiStyle.DEFAULT, UiPreferences.configured());
    }

    public SearchField(Font font, int x, int y, int width, int height, SearchIndex<T> index,
                       UiStyle style, UiPreferences preferences) {
        super(font, x, y, width, height, Component.translatable("sh_better_ui.search.label"));
        this.font = font;
        this.index = Objects.requireNonNull(index);
        this.style = Objects.requireNonNull(style);
        this.preferences = Objects.requireNonNull(preferences);
        setMaxLength(256);
        setHint(getMessage());
        setTooltip(Tooltip.create(USAGE));
        super.setResponder(ignored -> queryChanged());
    }

    /** 位于已有玻璃工具列内时可跳过输入框取样；浮出的候选清单仍独立取样。 */
    public void setSampleBackground(boolean sampleBackground) {
        this.sampleBackground = sampleBackground;
    }

    @Override
    public void setBordered(boolean bordered) {
        super.setBordered(bordered);
        this.bordered = bordered;
    }

    @Override
    public int getInnerWidth() {
        return nativeTextWidth() - searchIconInset();
    }

    private int nativeTextWidth() {
        // 原生 EditBox 的带边框文字留白也适用于玻璃主题。
        return bordered ? getWidth() - 2 * BORDERED_TEXT_PADDING : getWidth();
    }

    private int searchIconInset() {
        int nativeRightPadding = bordered ? BORDERED_TEXT_PADDING : 0;
        int inset = GraphicIcon.PIXELS + ICON_TEXT_GAP + ICON_RIGHT_PADDING - nativeRightPadding;
        return getHeight() >= GraphicIcon.PIXELS + 2 && nativeTextWidth() - inset >= MIN_TEXT_WIDTH ? inset : 0;
    }

    @Override
    public void setHint(Component hint) {
        this.hint = hint;
        super.setHint(hint);
    }

    private Component fittedHint() {
        int width = getInnerWidth();
        if (hint == null || font.width(hint) <= width) return hint;

        var clipped = Component.empty();
        font.substrByWidth(hint, width).visit((style, text) -> {
            clipped.append(Component.literal(text).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return clipped;
    }

    public void setIndex(SearchIndex<T> index) {
        this.index = Objects.requireNonNull(index);
        invalidateSuggestions();
    }

    /** 数据范围变更时重新传入条件，已输入的查询保留。 */
    public void setScope(Predicate<? super T> scope) {
        this.scope = Objects.requireNonNull(scope);
        invalidateSuggestions();
    }

    /** 每次实际查询变化通知一次，包含填充和撤销。 */
    @Override
    public void setResponder(Consumer<String> responder) {
        onChange = responder;
    }

    /** 设置 Enter 提交回调；传入 null 恢复画面原有 Enter 行为。 */
    public void setOnSubmit(Consumer<String> onSubmit) {
        this.onSubmit = onSubmit;
    }

    @Override
    public void setFilter(Predicate<String> filter) {
        super.setFilter(Objects.requireNonNull(filter));
        this.filter = filter;
        invalidateSuggestions();
    }

    @Override
    public void setMaxLength(int length) {
        if (length < 0) throw new IllegalArgumentException("length must be nonnegative");
        maxLength = length;
        super.setMaxLength(length);
        invalidateSuggestions();
    }

    @Override
    public void setFocused(boolean focused) {
        boolean wasFocused = isFocused();
        super.setFocused(focused);
        if (wasFocused != isFocused()) invalidateSuggestions();
    }

    private void queryChanged() {
        String query = getValue();
        if (query.equals(observedQuery)) return;
        observedQuery = query;
        undoQuery = null;
        invalidateSuggestions();
        if (onChange != null) onChange.accept(query);
    }

    private void invalidateSuggestions() {
        suggestions = null;
        selected = firstVisible = 0;
        dismissed = false;
        setSuggestion(null);
    }

    private boolean suggestionsOpen() {
        if (!active || !isVisible() || !canConsumeInput() || dismissed) return false;
        if (suggestions == null) {
            suggestions = index.suggest(getValue(), scope, SUGGESTION_LIMIT, this::canFill);
        }
        return !suggestions.isEmpty();
    }

    /** 宿主 Screen 应先处理 Esc，再调用自己的默认关闭逻辑。 */
    public boolean dismissSuggestions() {
        if (!suggestionsOpen()) return false;
        dismissed = true;
        setSuggestion(null);
        return true;
    }

    private boolean canFill(String text) {
        return text.length() <= maxLength && filter.test(text);
    }

    private boolean acceptSelected() {
        String previous = getValue();
        String completion = suggestions.get(selected).completion();
        if (!canFill(completion)) return false;
        if (!completion.equals(previous)) {
            setValue(completion);
            if (!completion.equals(getValue())) return true;
            undoQuery = previous;
        }
        dismissed = true;
        setSuggestion(null);
        return true;
    }

    /** 恢复最近一次填充前的查询。用户继续编辑后，撤销入口随之失效。 */
    public boolean undoCompletion() {
        String previous = undoQuery;
        if (previous == null || !canFill(previous)) return false;
        setValue(previous);
        return previous.equals(getValue());
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (!active || !isVisible() || !canConsumeInput()) return false;
        if (key == GLFW.GLFW_KEY_Z && undoQuery != null && Screen.hasControlDown()
                && !Screen.hasShiftDown() && !Screen.hasAltDown() && undoCompletion()) return true;
        if ((modifiers & (GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER)) == 0) {
            switch (key) {
                case GLFW.GLFW_KEY_ESCAPE -> { return dismissSuggestions(); }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                    if (onSubmit != null) {
                        dismissSuggestions();
                        onSubmit.accept(getValue());
                        return true;
                    }
                }
                case GLFW.GLFW_KEY_TAB -> { if (suggestionsOpen() && acceptSelected()) return true; }
                case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN -> {
                    if (suggestionsOpen()) {
                        moveSelection(key == GLFW.GLFW_KEY_UP ? -1 : 1);
                        return true;
                    }
                }
                default -> { }
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void moveSelection(int delta) {
        selected = UiMath.clamp(selected + delta, 0, suggestions.size() - 1);
    }

    /** 返回当前联想清单在宿主画面上的占用范围。 */
    public Optional<Rect2i> suggestionArea(int screenHeight) {
        Dropdown dropdown = dropdown(screenHeight);
        return dropdown == null ? Optional.empty()
                : Optional.of(new Rect2i(getX(), dropdown.top(), getWidth(), dropdown.height()));
    }

    /** 与候选行的点击和滚轮判定使用相同的边界。 */
    public boolean isOverSuggestions(double mouseX, double mouseY, int screenHeight) {
        return rowAt(dropdown(screenHeight), mouseX, mouseY) >= 0;
    }

    private boolean defaultTextColor = true;
    private boolean defaultDisabledColor = true;

    @Override
    public void setTextColor(int color) {
        defaultTextColor = false;
        super.setTextColor(color);
    }

    @Override
    public void setTextColorUneditable(int color) {
        defaultDisabledColor = false;
        super.setTextColorUneditable(color);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) return;
        String query = getValue();
        String suffix = "";
        if (suggestionsOpen() && getCursorPosition() == query.length() && getHighlighted().isEmpty()) {
            int space = Math.max(0, getInnerWidth() - font.width(query) - 1);
            suffix = font.plainSubstrByWidth(suggestions.get(selected).suffixFor(query), space);
        }
        setSuggestion(suffix.isEmpty() ? null : suffix);

        boolean showIcon = searchIconInset() != 0;
        boolean fitHint = showIcon && hint != null && query.isEmpty() && !isFocused();
        if (fitHint) super.setHint(fittedHint());
        try {
            renderInput(graphics, mouseX, mouseY, partialTick);
        } finally {
            if (fitHint) super.setHint(hint);
        }
        if (showIcon) {
            boolean glass = style == UiStyle.LIQUID_GLASS;
            int color = preferences.highContrast() || isFocused()
                    ? (glass ? LiquidGlassTheme.textColor() : DarkModeTheme.textColor())
                    : (glass ? LiquidGlassTheme.mutedTextColor() : DarkModeTheme.mutedTextColor());
            GraphicIcon.SEARCH.draw(graphics, getX() + getWidth() - ICON_RIGHT_PADDING - GraphicIcon.PIXELS,
                    getY() + (getHeight() - GraphicIcon.PIXELS) / 2, 1, color, preferences);
        }
    }

    private void renderInput(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (style != UiStyle.LIQUID_GLASS || !bordered) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            return;
        }

        if (defaultTextColor) super.setTextColor(UiTheme.current().text(0xFFE0E0E0));
        if (defaultDisabledColor) super.setTextColorUneditable(
                UiTheme.current().secondaryText(0xFF707070, 0.38f));
        LiquidGlassTheme.input(graphics, getX(), getY(), getWidth(), getHeight(), preferences,
                sampleBackground, isFocused());
        graphics.pose().pushPose();
        try {
            // 隐藏原生底板，同时保持原生带边框时的文字起点、游标与可用宽度。
            graphics.pose().translate(BORDERED_TEXT_PADDING, (getHeight() - 8) / 2, 0f);
            super.setBordered(false);
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
        } finally {
            super.setBordered(bordered);
            graphics.pose().popPose();
        }
    }

    /**
     * 在普通控件之后、画面裁切之外绘制；使用和输入框相同的坐标。
     * 宿主在候选打开时应暂停登记底层控件的 Screen 延迟提示。有后处理时在后处理结束后绘制。
     */
    public void renderSuggestions(GuiGraphics graphics, int mouseX, int mouseY, int screenHeight) {
        Dropdown dropdown = dropdown(screenHeight);
        if (dropdown == null) return;
        Component clippedLabel;
        graphics.flush();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0f, 0f, DROPDOWN_Z);
            clippedLabel = drawDropdown(graphics, dropdown, rowAt(dropdown, mouseX, mouseY));
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
        if (clippedLabel != null) renderSuggestionTooltip(graphics, clippedLabel, dropdown, mouseX, mouseY);
    }

    private void renderSuggestionTooltip(GuiGraphics graphics, Component label, Dropdown dropdown,
                                         int mouseX, int mouseY) {
        int left = Math.max(0, getX());
        int right = Math.min(graphics.guiWidth(), getX() + getWidth());
        int textWidth = right - left - 2 * TOOLTIP_PADDING;
        if (textWidth <= 0) return;

        // 提示框留在搜索列内，避免跨到 JEI 侧栏；优先放在输入框和候选列表之外。
        int top = Math.min(getY(), dropdown.top());
        int bottom = Math.max(getY() + getHeight(), dropdown.top() + dropdown.height());
        Rect2i anchor = new Rect2i(left, top, right - left, bottom - top);
        graphics.renderTooltip(font, font.split(label, textWidth), suggestionTooltipPositioner(anchor), mouseX, mouseY);
    }

    static ClientTooltipPositioner suggestionTooltipPositioner(Rect2i anchor) {
        return (screenWidth, screenHeight, mouseX, mouseY, width, height) -> {
            int above = anchor.getY() - TOOLTIP_PADDING - GAP;
            int below = anchor.getY() + anchor.getHeight() + TOOLTIP_PADDING + GAP;
            int minY = TOOLTIP_PADDING;
            int maxY = Math.max(minY, screenHeight - TOOLTIP_PADDING - height);
            int y = below <= maxY || maxY - below >= above - height - minY ? below : above - height;
            return new Vector2i(anchor.getX() + TOOLTIP_PADDING, UiMath.clamp(y, minY, maxY));
        };
    }

    private Component drawDropdown(GuiGraphics graphics, Dropdown dropdown, int hovered) {
        int x = getX();
        int bottom = dropdown.top() + dropdown.height();
        boolean liquidGlass = style == UiStyle.LIQUID_GLASS;
        if (liquidGlass) {
            LiquidGlassTheme.popover(graphics, x, dropdown.top(), getWidth(), dropdown.height(), preferences);
        } else {
            graphics.fill(x, dropdown.top(), x + getWidth(), bottom, DarkModeTheme.surfaceColor());
        }
        Component clippedLabel = null;
        for (int row = 0; row < dropdown.rows(); row++) {
            int index = firstVisible + row;
            int y = dropdown.top() + row * dropdown.rowHeight();
            if (liquidGlass) {
                LiquidGlassTheme.suggestionRow(graphics, x + 1, y + 1, getWidth() - 2, dropdown.rowHeight() - 2,
                        index == selected, index == hovered, preferences);
            } else if (index == selected || index == hovered) {
                graphics.fill(x + 1, y + 1, x + getWidth() - 1, y + dropdown.rowHeight(),
                        index == selected ? DarkModeTheme.backgroundColor() : DarkModeTheme.borderColor());
            }
            if (!liquidGlass && index == selected) {
                graphics.fill(x + 1, y + 1, x + 3, y + dropdown.rowHeight(), DarkModeTheme.accentColor());
            }
            Component label = label(suggestions.get(index));
            String text = label.getString();
            String visibleText = font.plainSubstrByWidth(text, getWidth() - 10);
            if (index == hovered && !visibleText.equals(text)) clippedLabel = label;
            graphics.drawString(font, visibleText, x + 5, y + (dropdown.rowHeight() - font.lineHeight) / 2,
                    liquidGlass ? LiquidGlassTheme.textColor() : DarkModeTheme.textColor(), false);
        }
        if (!liquidGlass) {
            graphics.renderOutline(x, dropdown.top(), getWidth(), dropdown.height(), DarkModeTheme.hoverBorderColor());
        }
        return clippedLabel;
    }

    /** 在宿主 Screen 的普通鼠标分派前调用，避免清单点击落到后方控件。 */
    public boolean mouseClickedSuggestion(double mouseX, double mouseY, int button, int screenHeight) {
        int row = rowAt(dropdown(screenHeight), mouseX, mouseY);
        if (row < 0) {
            if (!isMouseOver(mouseX, mouseY)) dismissSuggestions();
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            selected = row;
            acceptSelected();
        }
        return true;
    }

    /** 在宿主 Screen 的普通滚轮分派前调用。 */
    public boolean mouseScrolledSuggestions(double mouseX, double mouseY, double delta, int screenHeight) {
        if (rowAt(dropdown(screenHeight), mouseX, mouseY) < 0) return false;
        if (delta != 0) moveSelection(delta > 0 ? -1 : 1);
        return true;
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        super.updateWidgetNarration(output);
        if (suggestionsOpen()) {
            output.add(NarratedElementType.POSITION, Component.translatable("narrator.position.object_list",
                    selected + 1, suggestions.size()));
            output.add(NarratedElementType.HINT, label(suggestions.get(selected)));
            output.add(NarratedElementType.USAGE, USAGE);
        }
    }

    private static Component label(SearchIndex.Suggestion<?> suggestion) {
        String name = suggestion.label();
        String completion = suggestion.completion();
        if (!name.equals(suggestion.entry().name())) return Component.literal(name);
        return completion.equals(name) ? Component.literal(name)
                : Component.translatable("sh_better_ui.search.suggestion.id", completion, name);
    }

    private Dropdown dropdown(int screenHeight) {
        if (getWidth() <= 10 || getY() >= screenHeight || getY() + getHeight() <= 0 || !suggestionsOpen()) return null;
        int rowHeight = font.lineHeight + 7;
        int below = getY() + getHeight() + GAP;
        int above = getY() - GAP;
        int belowRows = Math.max(0, screenHeight - below) / rowHeight;
        int aboveRows = Math.max(0, above) / rowHeight;
        boolean placeBelow = belowRows >= suggestions.size() || belowRows >= aboveRows;
        int rows = Math.min(suggestions.size(), placeBelow ? belowRows : aboveRows);
        if (rows == 0) return null;

        firstVisible = UiMath.clamp(firstVisible, Math.max(0, selected - rows + 1), selected);
        firstVisible = Math.min(firstVisible, suggestions.size() - rows);
        return new Dropdown(placeBelow ? below : above - rows * rowHeight, rows, rowHeight);
    }

    private int rowAt(Dropdown dropdown, double mouseX, double mouseY) {
        if (dropdown == null || mouseX < getX() || mouseX >= getX() + getWidth()
                || mouseY < dropdown.top() || mouseY >= dropdown.top() + dropdown.height()) return -1;
        return firstVisible + (int) (mouseY - dropdown.top()) / dropdown.rowHeight();
    }

    private record Dropdown(int top, int rows, int rowHeight) {
        int height() { return rows * rowHeight; }
    }
}
