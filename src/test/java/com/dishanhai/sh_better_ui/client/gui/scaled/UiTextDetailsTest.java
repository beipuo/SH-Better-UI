package com.dishanhai.sh_better_ui.client.gui.scaled;

import net.minecraft.ChatFormatting;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：详情截断保留文字样式、换行宽度和原地展开状态。 */
class UiTextDetailsTest {
    private static final StringSplitter SPLITTER = new StringSplitter((codePoint, style) -> style.isBold() ? 2f : 1f);
    private static final Font FONT = new Font(ignored -> null, false) {
        @Override
        public StringSplitter getSplitter() {
            return SPLITTER;
        }
    };

    @Test
    void capsuleDetailsCanReverseBackToTheSameScreen() {
        var screen = new UiTextDetailsScreen(null, Component.empty(), Component.empty(), UiPreferences.DEFAULT,
                () -> null);
        screen.revealFrom(new UiButton(20, 20, 80, 20, Component.empty(), ignored -> {}));

        assertTrue(screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertTrue(screen.mouseClicked(0, 0, GLFW.GLFW_MOUSE_BUTTON_LEFT));
        assertTrue(screen.mouseClicked(40, 30, GLFW.GLFW_MOUSE_BUTTON_LEFT));
        assertFalse(screen.mouseClicked(0, 0, GLFW.GLFW_MOUSE_BUTTON_LEFT));
    }

    @Test
    void truncatesStyledUnicodeAtWidthAndPreservesEarlierLines() {
        var text = Component.literal("前一行\n").append(Component.literal("甲😀乙丙丁戊己庚辛壬癸")
                .withStyle(ChatFormatting.BLUE));
        var details = new UiTextDetails(Component.empty(), text, 2);
        int markerWidth = (int) Math.ceil(SPLITTER.stringWidth(CommonComponents.ELLIPSIS));
        var layout = details.layout(FONT, markerWidth + 3);

        assertTrue(layout.expandable());
        assertTrue(layout.truncated());
        assertEquals(2 * FONT.lineHeight, layout.height());
        assertEquals("前一行", textOf(layout.lines().getFirst()));
        var last = layout.lines().getLast();
        assertEquals("甲😀乙" + CommonComponents.ELLIPSIS.getString(), textOf(last));
        assertEquals(markerWidth + 3, SPLITTER.stringWidth(last));
        last.accept((index, style, codePoint) -> {
            if (codePoint == '甲' || codePoint == 0x1F600 || codePoint == '乙') {
                assertNotNull(style.getColor());
                assertEquals(ChatFormatting.BLUE.getColor().intValue(), style.getColor().getValue());
            }
            return true;
        });
    }

    @Test
    void keepsMarkerWithinVeryNarrowWidth() {
        var details = new UiTextDetails(Component.empty(), Component.literal("abcdef"), 1);
        var layout = details.layout(FONT, 1);

        assertTrue(layout.truncated());
        assertTrue(SPLITTER.stringWidth(layout.lines().getFirst()) <= 1f);
    }

    @Test
    void togglesInlineStateAndKeepsShortTextFreeOfButtons() {
        var details = new UiTextDetails(Component.empty(), Component.literal("第一行\n第二行"), 1);
        var changes = new ArrayList<Boolean>();
        var collapsed = details.layout(FONT, 30);
        var button = details.inlineButton(collapsed, 0, 0, 16, 16, UiPreferences.DEFAULT, changes::add).orElseThrow();

        button.onPress();
        var expanded = details.layout(FONT, 30);
        assertTrue(details.expanded());
        assertTrue(button.isSelected());
        assertEquals(Component.translatable("sh_better_ui.details.collapse"), button.getMessage());
        assertTrue(expanded.expandable());
        assertFalse(expanded.truncated());
        assertEquals("第二行", textOf(expanded.lines().getLast()));
        assertTrue(details.pageButton(expanded, null, 0, 0, 16, 16, UiPreferences.DEFAULT).isPresent());

        button.onPress();
        assertFalse(details.expanded());
        assertEquals(java.util.List.of(true, false), changes);

        var shortDetails = new UiTextDetails(Component.empty(), Component.literal("简短"), 1);
        var shortLayout = shortDetails.layout(FONT, 30);
        assertFalse(shortLayout.expandable());
        assertFalse(shortLayout.truncated());
        assertTrue(shortDetails.inlineButton(shortLayout, 0, 0, 16, 16, UiPreferences.DEFAULT, changes::add).isEmpty());
        assertTrue(shortDetails.pageButton(shortLayout, null, 0, 0, 16, 16, UiPreferences.DEFAULT).isEmpty());
    }

    private static String textOf(FormattedCharSequence line) {
        var text = new StringBuilder();
        line.accept((index, style, codePoint) -> {
            text.appendCodePoint(codePoint);
            return true;
        });
        return text.toString();
    }
}
