// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

public record EventResult(boolean propagationStopped, boolean immediatePropagationStopped,
                          boolean defaultPrevented, boolean handled, boolean consumed, boolean blockVanilla) {}
