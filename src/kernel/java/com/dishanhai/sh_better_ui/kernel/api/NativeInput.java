// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;

/** Immutable input sampled from the active native Screen, in scaled GUI coordinates. */
public sealed interface NativeInput {
    enum PointerAction { PRESS, RELEASE, DRAG, SCROLL, MOVE }
    enum KeyAction { PRESS, REPEAT, RELEASE }

    /** A button of -1 denotes pointer motion or scroll without a particular button. */
    record Pointer(PointerAction action, double x, double y, int button, double deltaX, double deltaY)
            implements NativeInput {
        public Pointer {
            Objects.requireNonNull(action, "action");
        }
    }

    record Key(KeyAction action, int key, int scanCode, int modifiers) implements NativeInput {
        public Key {
            Objects.requireNonNull(action, "action");
        }
    }

    /** The native Screen callback delivers UTF-16 units, including the two units of a supplementary character. */
    record Character(char codeUnit, int modifiers) implements NativeInput {}
}
