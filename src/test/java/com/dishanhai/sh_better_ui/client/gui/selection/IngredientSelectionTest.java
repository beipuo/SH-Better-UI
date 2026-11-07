package com.dishanhai.sh_better_ui.client.gui.selection;

import com.dishanhai.sh_better_ui.client.jei.JeiIntegration;
import com.dishanhai.sh_better_ui.client.jei.JeiIntegration.Ingredient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：选取草稿的变体、上限、副本、来源核对和一次性提交契约。 */
class IngredientSelectionTest {
    @Test
    void singleSelectionReplacesTheOldVariantAndCanBeCleared() {
        var first = ingredient("first", true);
        var second = ingredient("second", true);
        var selection = new IngredientSelection(1, List.of(first));
        assertTrue(selection.toggle(second));
        assertEquals(List.of(second), selection.values());
        assertTrue(selection.toggle(second));
        assertTrue(selection.values().isEmpty());
    }

    @Test
    void multipleSelectionKeepsVariantsOfOneIdAndRejectsOnlyNewEntriesAtTheLimit() {
        var first = ingredient("first", true);
        var second = ingredient("second", true);
        var third = ingredient("third", true);
        var selection = new IngredientSelection(2, List.of());
        assertTrue(selection.toggle(first));
        assertTrue(selection.toggle(second));
        assertTrue(selection.isAtLimit());
        assertFalse(selection.toggle(third));
        assertEquals(List.of(first, second), selection.values());
        assertTrue(selection.toggle(first));
        assertTrue(selection.toggle(third));
        assertEquals(List.of(second, third), selection.values());
    }

    @Test
    void constructorAndSnapshotsProtectCallerCollectionsAndDeduplicateByVariantKey() {
        var first = ingredient("first", true);
        var second = ingredient("second", true);
        var initial = new ArrayList<Ingredient>(List.of(first, first));
        var selection = new IngredientSelection(2, initial);
        initial.clear();
        var saved = selection.values();
        assertEquals(List.of(first), saved);
        assertThrows(UnsupportedOperationException.class, () -> saved.add(second));
        selection.toggle(second);
        assertEquals(List.of(first), saved);
        assertEquals(List.of(first, second), selection.values());
    }

    @Test
    void undoRestoresEditsInOrderAndSkipsRejectedAndEmptyEdits() {
        var first = ingredient("first", true);
        var second = ingredient("second", true);
        var third = ingredient("third", true);
        var selection = new IngredientSelection(2, List.of(first));
        assertFalse(selection.canUndo());
        assertTrue(selection.toggle(second));
        assertFalse(selection.toggle(third));
        selection.clear();
        selection.clear();
        assertTrue(selection.canUndo());
        assertTrue(selection.undo());
        assertEquals(List.of(first, second), selection.values());
        assertTrue(selection.undo());
        assertEquals(List.of(first), selection.values());
        assertFalse(selection.undo());
    }

    @Test
    void undoRestoresThePriorSingleVariantAndEndsOnCancel() {
        var first = ingredient("first", true);
        var second = ingredient("second", true);
        var selection = new IngredientSelection(1, List.of(first));
        assertTrue(selection.toggle(second));
        assertTrue(selection.toggle(second));
        assertTrue(selection.undo());
        assertEquals(List.of(second), selection.values());
        assertTrue(selection.undo());
        assertEquals(List.of(first), selection.values());
        selection.clear();
        assertTrue(selection.finish(false).isEmpty());
        assertFalse(selection.canUndo());
        assertFalse(selection.undo());
    }

    @Test
    void restoredSnapshotUsesCurrentVisibleAndAllowedVariants() {
        var old = ingredient("old", true);
        var current = ingredient("old", true);
        var removed = ingredient("removed", true);
        var denied = ingredient("denied", true);
        var selection = new IngredientSelection(3, List.of(old, removed, denied));
        selection.clear();
        assertTrue(selection.undo());
        assertTrue(selection.reconcile(List.of(current, denied), ingredient -> ingredient != denied));
        assertEquals(List.of(current), selection.values());
        assertFalse(selection.canUndo());
    }

    @Test
    void reconcileReplacesCurrentInstancesAndRemovesMissingHiddenOrDisallowedEntries() {
        var first = ingredient("first", true);
        var replacement = ingredient("first", true);
        var missing = ingredient("missing", true);
        var hidden = ingredient("hidden", true);
        var denied = ingredient("denied", true);
        var selection = new IngredientSelection(4, List.of(first, missing, hidden, denied));
        assertTrue(selection.reconcile(List.of(replacement, ingredient("hidden", false), denied),
                ingredient -> !ingredient.key().equals("denied")));
        assertEquals(List.of(replacement), selection.values());
        assertSame(replacement, selection.values().getFirst());
        assertFalse(selection.reconcile(List.of(replacement), ingredient -> true));
    }

    @Test
    void runtimeReplacementDoesNotRestoreAnotherVariantWithTheSameRegistryId() {
        var old = ingredient("old-runtime", true);
        var replacement = ingredient("new-runtime", true);
        var selection = new IngredientSelection(1, List.of(old));
        assertTrue(selection.reconcile(List.of(replacement), ingredient -> true));
        assertTrue(selection.values().isEmpty());
    }

    @Test
    void confirmationProducesAnImmutableResultOnceAndAllowsAnEmptyResult() {
        var first = ingredient("first", true);
        var selection = new IngredientSelection(1, List.of(first));
        var result = selection.finish(true).orElseThrow();
        assertEquals(List.of(first), result);
        assertThrows(UnsupportedOperationException.class, result::clear);
        assertTrue(selection.finish(true).isEmpty());
        assertTrue(selection.finish(false).isEmpty());
        assertFalse(selection.toggle(first));
        assertTrue(selection.isFinished());
        assertEquals(List.of(), new IngredientSelection(1, List.of()).finish(true).orElseThrow());
    }

    @Test
    void cancellationNeverChangesTheInitialListOrProducesAConfirmedResult() {
        var first = ingredient("first", true);
        var second = ingredient("second", true);
        var initial = new ArrayList<Ingredient>(List.of(first));
        var selection = new IngredientSelection(1, initial);
        selection.toggle(second);
        assertTrue(selection.finish(false).isEmpty());
        assertTrue(selection.finish(true).isEmpty());
        assertEquals(List.of(first), initial);
    }

    @Test
    void invalidLimitsAndTooManyDistinctInitialValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new IngredientSelection(0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new IngredientSelection(1,
                List.of(ingredient("first", true), ingredient("second", true))));
    }

    static Ingredient ingredient(String key, boolean visible) {
        return new Ingredient() {
            @Override public String key() { return key; }
            @Override public String name() { return key; }
            @Override public ResourceLocation id() { return ResourceLocation.tryParse("minecraft:iron_ingot"); }
            @Override public String modName() { return "Minecraft"; }
            @Override public List<String> aliases() { return List.of(); }
            @Override public JeiIntegration.Kind kind() { return JeiIntegration.Kind.ITEM; }
            @Override public boolean visible() { return visible; }
            @Override public int width() { return 16; }
            @Override public int height() { return 16; }
            @Override public void render(GuiGraphics graphics, int x, int y) {}
            @Override public List<Component> tooltip() { return List.of(); }
        };
    }
}
