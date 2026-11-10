// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

import java.util.Objects;
import java.util.regex.Pattern;

/** A lower-case namespaced ID with the same character rules on both loaders. */
public record UiId(String namespace, String path) implements Comparable<UiId> {
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_./-]+");

    public UiId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (!NAMESPACE.matcher(namespace).matches() || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("Invalid UI ID: " + namespace + ":" + path);
        }
    }

    public static UiId parse(String value) {
        int separator = Objects.requireNonNull(value, "value").indexOf(':');
        if (separator < 0) {
            throw new IllegalArgumentException("Expected a namespaced UI ID: " + value);
        }
        return new UiId(value.substring(0, separator), value.substring(separator + 1));
    }

    @Override
    public int compareTo(UiId other) {
        int namespaces = namespace.compareTo(other.namespace);
        return namespaces == 0 ? path.compareTo(other.path) : namespaces;
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
