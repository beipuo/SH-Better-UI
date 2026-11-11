// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.panel;

import java.util.Map;
import java.util.function.UnaryOperator;

/** Per-preview values. The catalog validates a replacement before the visible draft changes. */
final class MaterialPreviewDraft {
    record Snapshot(Map<String, Object> a, Map<String, Object> b) {
        Snapshot {
            a = Map.copyOf(a);
            b = Map.copyOf(b);
        }
    }

    private final Map<String, Object> defaults;
    private final UnaryOperator<Map<String, Object>> validate;
    private Snapshot current;
    private Snapshot previous;

    MaterialPreviewDraft(Map<String, Object> defaults, UnaryOperator<Map<String, Object>> validate) {
        this.validate = validate;
        this.defaults = Map.copyOf(validate.apply(defaults));
        current = new Snapshot(this.defaults, this.defaults);
    }

    Map<String, Object> values(boolean second) {
        return second ? current.b() : current.a();
    }

    void replace(boolean second, Map<String, Object> next) {
        var valid = validate.apply(next);
        change(second ? new Snapshot(current.a(), valid) : new Snapshot(valid, current.b()));
    }

    void reset(boolean second) { replace(second, defaults); }

    void copyToOther(boolean sourceIsSecond) { replace(!sourceIsSecond, values(sourceIsSecond)); }

    void swap() { change(new Snapshot(current.b(), current.a())); }

    boolean canUndo() { return previous != null; }

    void undo() {
        if (previous == null) return;
        current = previous;
        previous = null;
    }

    Snapshot snapshot() { return current; }

    void restore(Snapshot saved) {
        current = new Snapshot(validate.apply(saved.a()), validate.apply(saved.b()));
        previous = null;
    }

    private void change(Snapshot next) {
        if (current.equals(next)) return;
        previous = current;
        current = next;
    }
}
