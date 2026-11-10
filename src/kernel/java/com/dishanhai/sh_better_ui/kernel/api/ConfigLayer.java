// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

/** Configuration precedence from lowest to highest. */
public enum ConfigLayer {
    ENGINE_DEFAULTS, MODULE_DEFAULTS, RESOURCE_THEME, USER_GLOBAL, SCREEN, COMPONENT, RUNTIME_OVERRIDE
}
