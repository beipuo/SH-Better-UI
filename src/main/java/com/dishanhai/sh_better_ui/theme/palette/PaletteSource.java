// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.palette;

import com.dishanhai.sh_better_ui.theme.token.ColorValue;

/** Supplies a seed on a worker thread. Capture game or UI state before creating this source. */
@FunctionalInterface
public interface PaletteSource {
    ColorValue seed() throws Exception;
}
