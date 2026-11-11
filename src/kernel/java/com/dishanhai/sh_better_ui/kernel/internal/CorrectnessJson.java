// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.intelligence.UISnapshot;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** On-demand diagnostic export. No renderer, mutable registry, or platform JSON dependency is consulted. */
final class CorrectnessJson {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private CorrectnessJson() {}

    static String export(UISnapshot snapshot) {
        Objects.requireNonNull(snapshot);
        var output = new StringBuilder(4096);
        output.append("{\"schemaVersion\":1,\"snapshot\":");
        append(output, snapshot);
        output.append(",\"unresolvedHard\":");
        append(output, snapshot.report().unresolvedHard());
        output.append(",\"nonFiniteValues\":\"null\"}");
        return output.toString();
    }

    /** Record-based serialization also includes future additive snapshot fields without duplicating their schema. */
    static String write(Object value) {
        var output = new StringBuilder();
        append(output, value);
        return output.toString();
    }

    private static void append(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof UiId id) {
            quote(output, id.toString());
        } else if (value instanceof CharSequence text) {
            quote(output, text.toString());
        } else if (value instanceof Enum<?> choice) {
            quote(output, choice.name());
        } else if (value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof Number number) {
            output.append(number instanceof Double && !Double.isFinite(number.doubleValue())
                    || number instanceof Float && !Float.isFinite(number.floatValue()) ? "null" : number.toString());
        } else if (value instanceof Map<?, ?> map) {
            output.append('{');
            boolean comma = false;
            var entries = map.entrySet().stream().sorted(Comparator.comparing(entry -> entry.getKey().toString())).toList();
            for (var entry : entries) {
                if (comma) output.append(',');
                quote(output, entry.getKey().toString());
                output.append(':');
                append(output, entry.getValue());
                comma = true;
            }
            output.append('}');
        } else if (value instanceof Set<?> set) {
            append(output, set.stream().sorted(Comparator.comparing(Object::toString)).toList());
        } else if (value instanceof Iterable<?> iterable) {
            output.append('[');
            boolean comma = false;
            for (var item : iterable) {
                if (comma) output.append(',');
                append(output, item);
                comma = true;
            }
            output.append(']');
        } else if (value.getClass().isArray()) {
            output.append('[');
            for (int index = 0; index < Array.getLength(value); index++) {
                if (index != 0) output.append(',');
                append(output, Array.get(value, index));
            }
            output.append(']');
        } else if (value.getClass().isRecord()) {
            output.append('{');
            boolean comma = false;
            for (var component : value.getClass().getRecordComponents()) {
                if (comma) output.append(',');
                quote(output, component.getName());
                output.append(':');
                try {
                    append(output, component.getAccessor().invoke(value));
                } catch (IllegalAccessException | InvocationTargetException failure) {
                    throw new IllegalArgumentException("Cannot export diagnostic record " + value.getClass().getName(), failure);
                }
                comma = true;
            }
            output.append('}');
        } else {
            throw new IllegalArgumentException("Unsupported diagnostic value " + value.getClass().getName());
        }
    }

    private static void quote(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char c = value.charAt(index);
            switch (c) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    boolean solitarySurrogate = Character.isHighSurrogate(c)
                            ? index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(index + 1))
                            : Character.isLowSurrogate(c) && (index == 0 || !Character.isHighSurrogate(value.charAt(index - 1)));
                    if (c < 32 || solitarySurrogate) {
                        output.append("\\u").append(HEX[(c >>> 12) & 15]).append(HEX[(c >>> 8) & 15])
                                .append(HEX[(c >>> 4) & 15]).append(HEX[c & 15]);
                    } else {
                        output.append(c);
                    }
                }
            }
        }
        output.append('"');
    }
}
