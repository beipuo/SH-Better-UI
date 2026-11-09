// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.resource;

import com.dishanhai.sh_better_ui.theme.ThemeManager;
import com.dishanhai.sh_better_ui.theme.ThemeResolutionException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Arbitrates asynchronous preparation results. Only the most recent generation may publish. */
public final class ThemeReloadManager {
    private final Supplier<ThemeManager> manager;
    private final AtomicLong generation = new AtomicLong();

    public ThemeReloadManager(ThemeManager manager) {
        Objects.requireNonNull(manager);
        this.manager = () -> manager;
    }

    /** Lazily gets the manager on its owner thread during commit. */
    public ThemeReloadManager(Supplier<ThemeManager> manager) { this.manager = Objects.requireNonNull(manager); }

    /** May be called on the thread initiating a reload. */
    public long begin() { return generation.incrementAndGet(); }

    /** Called on the manager owner thread after the platform preparation barrier. */
    public boolean commit(long ticket, ThemeResourceLoader.Batch prepared) {
        if (ticket != generation.get()) return false;
        ThemeManager manager = this.manager.get();
        if (manager.isClosed()) return false;
        if (prepared.error() != null) {
            manager.resolutionFailed(new ThemeResolutionException(prepared.error()));
            return false;
        }
        try {
            manager.replaceResourceThemes(prepared.definitions(), prepared.diagnostics(),
                    () -> ticket == generation.get());
            return true;
        } catch (ThemeResolutionException failure) {
            // The manager has already recorded the validation failure.
            return false;
        } catch (IllegalArgumentException failure) {
            manager.resolutionFailed(failure);
            return false;
        }
    }
}
