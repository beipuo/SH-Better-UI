// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.event;

/** An owner-thread callback for theme lifecycle events. */
@FunctionalInterface
public interface ThemeObserver {
    void onThemeEvent(ThemeEvent event);
}
