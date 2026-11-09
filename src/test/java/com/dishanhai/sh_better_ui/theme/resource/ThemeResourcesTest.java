// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.resource;

import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ThemeResourcesTest {
    private final ThemeManager manager = new ThemeManager(() -> 0L, ignored -> {});
    private final ThemeReloadManager reload = new ThemeReloadManager(manager);
    private final ThemeResourceLoader loader = new ThemeResourceLoader(new ThemeJsonParser());

    @Test
    void packPriorityAndMissingParentStillResolveValidTheme() {
        long generation = reload.begin();
        var batch = loader.prepare(List.of(input("example:dynamic", "low", "malformed lower layer"),
                input("example:dynamic", "high", json("example:dynamic", "example:missing", """
                        {"color.accent":"#FFAABB", "color.primary":{"ref":"color.accent"}}
                        """))), manager.tokenCatalog());
        assertTrue(reload.commit(generation, batch));
        manager.setTheme("example:dynamic");
        assertEquals(ColorValue.parse("#FFAABB"), manager.snapshot().resolve(ThemeTokens.PRIMARY));
        assertTrue(batch.diagnostics().get(0).contains("high"));
        assertTrue(manager.snapshot().diagnostics().get(0).contains("example:missing"));
    }

    @Test
    void badReloadRetainsAllPreviousThemesAndLaterValidReloadRecovers() {
        assertTrue(commit(input("example:dynamic", "valid", json("example:dynamic", null,
                "{\"shape.radius.medium\":12}"))));
        manager.setTheme("example:dynamic");
        var previous = manager.snapshot();
        long version = manager.resourceVersion();
        assertFalse(commit(input("example:dynamic", "invalid", json("example:dynamic", null,
                "{\"shape.radius.medium\":\"large\"}"))));
        assertFalse(commit(input("example:dynamic", "cycle", json("example:dynamic", "example:dynamic", "{}"))));
        assertFalse(commit(input("example:dynamic", "token-cycle", json("example:dynamic", null,
                "{\"color.accent\":{\"ref\":\"color.primary\"},\"color.primary\":{\"ref\":\"color.accent\"}}"))));
        assertSame(previous, manager.snapshot());
        assertEquals(version, manager.resourceVersion());
        assertTrue(commit(input("example:dynamic", "recovery", json("example:dynamic", null,
                "{\"shape.radius.medium\":23}"))));
        assertEquals(23f, manager.snapshot().resolve(ThemeTokens.RADIUS_MEDIUM));
    }

    @Test
    void lateWorkerCannotOverwriteTheNewerBatch() {
        long first = reload.begin();
        var old = loader.prepare(List.of(input("example:old", "old", json("example:old", null, "{}"))),
                manager.tokenCatalog());
        long second = reload.begin();
        var latest = loader.prepare(List.of(input("example:new", "new", json("example:new", null, "{}"))),
                manager.tokenCatalog());
        assertTrue(reload.commit(second, latest));
        assertFalse(reload.commit(first, old));
        assertTrue(manager.registry().find("example:new").isPresent());
        assertTrue(manager.registry().find("example:old").isEmpty());
    }

    @Test
    void rejectsDuplicatePropertiesUnknownFieldsAndCrossNamespaceDefinition() {
        assertFalse(commit(input("example:x", "duplicate", json("example:x", null,
                "{\"color.accent\":\"#112233\",\"color.accent\":\"#445566\"}"))));
        assertFalse(commit(input("example:x", "unknown", "{\"schemaVersion\":1,\"unknown\":true}")));
        assertFalse(commit(input("example:x", "foreign", json("other:x", null, "{}"))));
        assertFalse(commit(input("example:x", "extra", json("example:x", null, "{}") + "{}")));
    }

    private boolean commit(ThemeResourceLoader.Resource resource) {
        long generation = reload.begin();
        return reload.commit(generation, loader.prepare(List.of(resource), manager.tokenCatalog()));
    }

    private static ThemeResourceLoader.Resource input(String id, String pack, String content) {
        return new ThemeResourceLoader.Resource(id, pack,
                () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
    }

    private static String json(String id, String parent, String tokens) {
        return "{\"schemaVersion\":1,\"id\":\"" + id + "\","
                + (parent == null ? "" : "\"parent\":\"" + parent + "\",")
                + "\"tokens\":" + tokens + "}";
    }
}
