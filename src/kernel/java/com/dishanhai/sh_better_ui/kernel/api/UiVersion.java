// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** A numeric release version, ordered by major, minor and patch. */
public record UiVersion(int major, int minor, int patch) implements Comparable<UiVersion> {
    private static final Pattern FORMAT = Pattern.compile("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)");

    public UiVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("UI version components must be nonnegative");
        }
    }

    /** Parses exactly three decimal components, without pre-release or build metadata. */
    public static UiVersion parse(String value) {
        var match = FORMAT.matcher(Objects.requireNonNull(value, "value"));
        if (!match.matches()) {
            throw new IllegalArgumentException("Expected a numeric major.minor.patch UI version: " + value);
        }
        try {
            return new UiVersion(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                    Integer.parseInt(match.group(3)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("UI version component exceeds the integer range: " + value, exception);
        }
    }

    @Override
    public int compareTo(UiVersion other) {
        int majorOrder = Integer.compare(major, other.major);
        if (majorOrder != 0) {
            return majorOrder;
        }
        int minorOrder = Integer.compare(minor, other.minor);
        return minorOrder == 0 ? Integer.compare(patch, other.patch) : minorOrder;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
