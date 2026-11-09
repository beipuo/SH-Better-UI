// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.context;

/** Captures environment data without exposing game classes to the theme engine. */
@FunctionalInterface
public interface EnvironmentProvider {
    /** @return a non-null immutable snapshot; the change detector owns its effective version */
    ContextSnapshot capture();
}
