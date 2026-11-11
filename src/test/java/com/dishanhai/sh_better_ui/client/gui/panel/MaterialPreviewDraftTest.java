// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MaterialPreviewDraftTest {
    private static final Map<String, Object> DEFAULTS = Map.of("refraction", 3.3);

    @Test
    void invalidReplacementKeepsBothDraftsAndUndoHistory() {
        var draft = draft();
        draft.replace(false, Map.of("refraction", 4.0));
        assertThrows(IllegalArgumentException.class, () -> draft.replace(false, Map.of("refraction", 33.0)));
        assertEquals(Map.of("refraction", 4.0), draft.values(false));
        assertEquals(DEFAULTS, draft.values(true));
        draft.undo();
        assertEquals(DEFAULTS, draft.values(false));
    }

    @Test
    void copyingAndSwappingPreserveIndependentDraftsAndDetachedSnapshots() {
        var draft = draft();
        draft.replace(false, Map.of("refraction", 4.0));
        draft.copyToOther(false);
        var saved = draft.snapshot();
        draft.reset(false);
        draft.swap();
        assertEquals(Map.of("refraction", 4.0), draft.values(false));
        assertEquals(DEFAULTS, draft.values(true));
        assertEquals(Map.of("refraction", 4.0), saved.b());
        assertThrows(UnsupportedOperationException.class, () -> saved.a().clear());
    }

    private static MaterialPreviewDraft draft() {
        return new MaterialPreviewDraft(DEFAULTS, values -> {
            if (!(values.get("refraction") instanceof Number value) || value.doubleValue() > 32) {
                throw new IllegalArgumentException("refraction");
            }
            return Map.copyOf(values);
        });
    }
}
