// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

/** An invalid theme, token expression or inheritance chain with its resolution path. */
public final class ThemeResolutionException extends IllegalArgumentException {
    public ThemeResolutionException(String message) { super(message); }

    public ThemeResolutionException(String message, Throwable cause) { super(message, cause); }
}
