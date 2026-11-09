// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.token;

/** Read-only view passed to expressions. Reads made during evaluation form dependency edges. */
public interface TokenLookup {
    /** Returns the value of the supplied typed token. */
    <T> T resolve(ThemeToken<T> token);
}
