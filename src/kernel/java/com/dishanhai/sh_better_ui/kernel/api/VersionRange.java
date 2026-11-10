// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;
import java.util.Optional;

/** Half-open release interval with an optional upper bound. */
public record VersionRange(UiVersion minimumInclusive, Optional<UiVersion> maximumExclusive) {
    public static final VersionRange ANY = atLeast(new UiVersion(0, 0, 0));

    public VersionRange {
        Objects.requireNonNull(minimumInclusive, "minimumInclusive");
        Objects.requireNonNull(maximumExclusive, "maximumExclusive");
        if (maximumExclusive.isPresent() && minimumInclusive.compareTo(maximumExclusive.get()) >= 0) {
            throw new IllegalArgumentException("The upper UI version bound must exceed the lower bound");
        }
    }

    public static VersionRange atLeast(UiVersion minimum) {
        return new VersionRange(minimum, Optional.empty());
    }

    public static VersionRange between(UiVersion minimum, UiVersion maximum) {
        return new VersionRange(minimum, Optional.of(maximum));
    }

    public boolean contains(UiVersion version) {
        return version.compareTo(minimumInclusive) >= 0
                && (maximumExclusive.isEmpty() || version.compareTo(maximumExclusive.get()) < 0);
    }
}
