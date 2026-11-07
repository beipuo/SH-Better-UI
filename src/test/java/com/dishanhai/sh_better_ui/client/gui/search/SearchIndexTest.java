package com.dishanhai.sh_better_ui.client.gui.search;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 山海：覆盖搜索范围、排序与可填充文本。 */
class SearchIndexTest {
    @Test
    void matchesTokensAcrossFieldsAndKeepsScopeAndSourceOrder() {
        var copper = entry(1, "铜锭", "minecraft:copper_ingot", "tongding");
        var iron = entry(2, "铁锭", "minecraft:iron_ingot", "tieding");
        var index = new SearchIndex<>(List.of(copper, iron));

        assertEquals(List.of(copper), index.search("  铜\u3000MINECRAFT:cop\tTONG "));
        assertEquals(List.of(), index.search("铜 iron"));
        assertEquals(List.of(copper, iron), index.search("\u3000\t"));
        assertEquals(List.of(iron), index.search("ingot", value -> value == 2));
        assertEquals(List.of(iron), index.search("", value -> value == 2));
        assertEquals(List.of(), index.suggest("copper", value -> value == 2, 8));
    }

    @Test
    void ranksNameBeforeIdsAndAliasesWithStableTiesAndLimits() {
        var index = new SearchIndex<>(List.of(entry(7, "Other", "mod:other", "iron"),
                entry(6, "Metal", "mod:iron"), entry(5, "Refined", "iron_plate"),
                entry(4, "Element", "iron"), entry(3, "Raw Iron", "mod:raw"),
                entry(2, "Iron Plate", "mod:plate"), entry(1, "Iron Alloy", "mod:alloy"),
                entry(0, "Iron", "mod:ingot")));

        assertEquals(List.of(0, 2, 1, 3, 4, 5, 6, 7), values(index.suggest("iron", 12)));
        assertEquals(List.of(0, 2, 1), values(index.suggest("iron", 3)));
        assertEquals(List.of(3, 4), values(index.suggest("iron", value -> value >= 3, 2)));
        assertEquals(List.of(), index.suggest(" ", 8));
        assertEquals(List.of(), index.suggest("iron", 0));
        assertEquals(List.of("iron", "iron_plate", "mod:iron", "Other"),
                index.suggest("iron", value -> value >= 4, 8).stream().map(SearchIndex.Suggestion::completion).toList());
    }

    @Test
    void predictsOnlyLiteralPrefixesAndSkipsUnfillableResults() {
        var iron = entry(1, "Iron Ingot", "mod:iron_ingot", "tieding");
        var shortName = entry(2, "Iron", "mod:iron");
        var index = new SearchIndex<>(List.of(iron, shortName));

        var name = index.suggest("IRON I", 1).getFirst();
        assertEquals("ngot", name.suffixFor("IRON I"));
        assertEquals("", name.suffixFor(" iron i"));
        assertEquals("", name.suffixFor(""));
        assertEquals("", index.suggest("tieding", 1).getFirst().suffixFor("tieding"));
        assertEquals("ingot", index.suggest("mod:iron_", 1).getFirst().suffixFor("mod:iron_"));
        assertEquals(List.of(2), values(index.suggest("iro", value -> true, 1, text -> text.length() <= 4)));
        var withShortId = new SearchIndex<>(List.of(entry(3, "Iron Ingot", "iron")));
        assertEquals("iron", withShortId.suggest("iro", value -> true, 1, text -> text.length() <= 4)
                .getFirst().completion());
    }

    @Test
    void snapshotsTheEntriesAndSearchFields() {
        var ids = new ArrayList<>(List.of("mod:iron"));
        var entries = new ArrayList<>(List.of(new SearchIndex.Entry<>(1, "Iron", ids, List.of())));
        var index = new SearchIndex<>(entries);
        ids.clear();
        entries.clear();

        assertEquals(1, index.search("mod:iron").size());
        assertThrows(UnsupportedOperationException.class, () -> index.search("").clear());
    }

    private static SearchIndex.Entry<Integer> entry(int value, String name, String id, String... aliases) {
        return new SearchIndex.Entry<>(value, name, List.of(id), List.of(aliases));
    }

    private static List<Integer> values(List<SearchIndex.Suggestion<Integer>> suggestions) {
        return suggestions.stream().map(suggestion -> suggestion.entry().value()).toList();
    }
}
