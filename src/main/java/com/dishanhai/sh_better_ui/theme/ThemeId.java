// Author: dishanhai
package com.dishanhai.sh_better_ui.theme;

import java.util.Objects;
import java.util.regex.Pattern;

/** Validates the resource-compatible IDs used for themes and rules. */
public final class ThemeId {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*");

    private ThemeId() {}

    /** Returns a valid identifier, or throws with the supplied identifier. */
    public static String require(String id) {
        Objects.requireNonNull(id, "id");
        if (id.length() > 256 || !ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Expected namespaced theme ID: " + id);
        }
        for (String segment : id.substring(id.indexOf(':') + 1).split("/")) {
            if (segment.equals(".") || segment.equals("..")) throw new IllegalArgumentException("Invalid theme ID: " + id);
        }
        return id;
    }
}
