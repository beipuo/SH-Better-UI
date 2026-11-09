// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.token;

import java.util.Objects;

/** A typed alias of another token in the same effective scope. */
public record TokenReference<T>(ThemeToken<T> token) implements TokenExpression<T> {
    public TokenReference { Objects.requireNonNull(token); }

    @Override
    public T evaluate(TokenLookup lookup) { return lookup.resolve(token); }
}
