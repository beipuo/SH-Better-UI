// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.token;

/** A deterministic expression over the supplied token lookup. Java expressions must be side-effect free. */
@FunctionalInterface
public interface TokenExpression<T> {
    /** Computes an immutable value using only the supplied lookup. */
    T evaluate(TokenLookup lookup);

    /** Immutable literal expression, comparable by value across resource reloads. */
    record Literal<T>(T value) implements TokenExpression<T> {
        @Override
        public T evaluate(TokenLookup lookup) { return value; }
    }
}
