package com.dishanhai.sh_better_ui.client.gui.search;

import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：覆盖两版原生 EditBox 上的回调和搜索交互。 */
class SearchFieldTest {
    @Test
    void tabFillsSelectedCandidateOnceAndUndoRestoresQuery() {
        var field = fieldAt(20);
        var changes = new ArrayList<String>();
        field.setResponder(changes::add);
        field.setValue("Ir");
        assertTrue(field.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0));
        assertTrue(field.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals("Iridium", field.getValue());
        assertEquals(field.getValue().length(), field.getCursorPosition());
        assertFalse(field.dismissSuggestions());

        assertTrue(field.undoCompletion());
        assertEquals("Ir", field.getValue());
        assertFalse(field.undoCompletion());
        assertEquals(List.of("Ir", "Iridium", "Ir"), changes);
    }

    @Test
    void escapeAndOutsideClicksDismissWithoutChangingTextWhileEnterSubmitsQuery() {
        var field = fieldAt(20);
        var submissions = new ArrayList<String>();
        field.setOnSubmit(submissions::add);
        field.setValue("Ir");
        assertTrue(field.dismissSuggestions());
        assertFalse(field.dismissSuggestions());
        assertEquals("Ir", field.getValue());

        field.setValue("I");
        assertFalse(field.mouseClickedSuggestion(1, 1, GLFW.GLFW_MOUSE_BUTTON_LEFT, 120));
        assertFalse(field.dismissSuggestions());
        assertTrue(field.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0));
        assertEquals(List.of("I"), submissions);
        assertEquals("I", field.getValue());
    }

    @Test
    void dropdownFlipsAndFillsOnlyRowsInsideItsVisibleBounds() {
        var field = fieldAt(96);
        field.setValue("Ir");
        assertTrue(field.mouseClickedSuggestion(10, 93, GLFW.GLFW_MOUSE_BUTTON_RIGHT, 120));
        assertEquals("Ir", field.getValue());
        assertTrue(field.mouseClickedSuggestion(10, 93, GLFW.GLFW_MOUSE_BUTTON_LEFT, 120));
        assertEquals("Iridium", field.getValue());

        field.setValue("Ir");
        assertFalse(field.mouseClickedSuggestion(10, 94, GLFW.GLFW_MOUSE_BUTTON_LEFT, 120));
        assertEquals("Ir", field.getValue());
        assertFalse(field.undoCompletion());
    }

    @Test
    void scrollingMovesSelectionWhenOnlyOneRowFits() {
        var field = fieldAt(18);
        field.setValue("Ir");
        assertFalse(field.mouseScrolledSuggestions(1, 40, -1, 54));
        assertTrue(field.mouseScrolledSuggestions(10, 40, -1, 54));
        assertTrue(field.mouseClickedSuggestion(10, 40, GLFW.GLFW_MOUSE_BUTTON_LEFT, 54));
        assertEquals("Iridium", field.getValue());
    }

    @Test
    void scopeFilterAndMaxLengthApplyBeforeTheNextSelection() {
        var field = fieldAt(20);
        field.setValue("Ir");
        assertTrue(field.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0));
        field.setScope(value -> value == 1);
        field.setMaxLength(4);
        field.setFilter(text -> !text.equals("Iron"));
        assertFalse(field.dismissSuggestions());
        field.setFilter(text -> true);
        assertTrue(field.keyPressed(GLFW.GLFW_KEY_TAB, 0, 0));
        assertEquals("Iron", field.getValue());
    }

    private static SearchField<Integer> fieldAt(int y) {
        Font font = new Font(id -> null, false) {
            @Override
            public String plainSubstrByWidth(String text, int width) {
                return plainSubstrByWidth(text, width, false);
            }

            @Override
            public String plainSubstrByWidth(String text, int width, boolean backwards) {
                int length = Math.clamp(width, 0, text.length());
                return backwards ? text.substring(text.length() - length) : text.substring(0, length);
            }
        };
        var index = new SearchIndex<>(List.of(new SearchIndex.Entry<>(1, "Iron", List.of(), List.of()),
                new SearchIndex.Entry<>(2, "Iridium", List.of(), List.of())));
        var field = new SearchField<>(font, 10, y, 100, 18, index);
        field.setFocused(true);
        return field;
    }
}
