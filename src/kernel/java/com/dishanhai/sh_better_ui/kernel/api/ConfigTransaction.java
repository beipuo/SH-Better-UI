// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

/** Owner-thread batch. Commit is single-use, including on validation failure. */
public interface ConfigTransaction {
    <T> ConfigTransaction set(ConfigScope scope, ConfigKey<T> key, T value);

    ConfigTransaction clear(ConfigScope scope, ConfigKey<?> key);

    <T> ConfigTransaction load(ConfigScope scope, ConfigKey<T> key, int storedVersion, T value);

    /** Returns the published configuration version. Throws on stale or invalid writes. */
    long commit();
}
