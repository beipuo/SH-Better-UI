// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.function.Consumer;

public interface UIConfigCenter {
    /** Defines a schema in the calling module's namespace. */
    void define(ConfigKey<?> key);

    <T> T get(ConfigKey<T> key);

    <T> T get(ScopeHandle scope, ConfigKey<T> key);

    /** Sets a runtime override in the view's own scope. */
    <T> void set(ConfigKey<T> key, T value);

    ConfigSnapshot snapshot();

    ConfigSnapshot snapshot(ScopeHandle scope);

    ConfigTransaction beginTransaction();

    <T> Subscription observe(ScopeHandle scope, ConfigKey<T> key, Consumer<ValueChange<T>> listener);
}
