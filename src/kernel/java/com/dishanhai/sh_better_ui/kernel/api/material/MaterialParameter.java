// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.material;

import com.dishanhai.sh_better_ui.kernel.api.UiId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** A declarative parameter description. All exposed JSON values are immutable. */
public record MaterialParameter(String type, Object defaultValue, Double minimum, Double maximum, Double step,
                                String unit, boolean editable, boolean requiresResourceRebuild,
                                boolean runtimeUpdate, boolean animatable, List<String> dependsOn,
                                List<Constraint> constraints, List<String> choices) {
    private static final Pattern KEY = Pattern.compile("[a-zA-Z][a-zA-Z0-9_]{0,63}");
    private static final Pattern COLOR = Pattern.compile("#[a-fA-F0-9]{6}([a-fA-F0-9]{2})?");
    private static final Set<String> RESERVED = Set.of("__proto__", "constructor", "prototype");
    private static final Set<String> TYPES = Set.of("boolean", "integer", "float", "enum", "color", "vector2",
            "vector3", "vector4", "gradient", "texture", "duration", "curve", "object");

    public MaterialParameter {
        if (type == null || !TYPES.contains(type) || !finiteOrNull(minimum) || !finiteOrNull(maximum) || !finiteOrNull(step)
                || minimum != null && maximum != null && minimum > maximum || step != null && step <= 0) {
            throw new IllegalArgumentException("Invalid parameter definition: " + type);
        }
        unit = Objects.requireNonNull(unit, "unit");
        dependsOn = List.copyOf(dependsOn);
        constraints = List.copyOf(constraints);
        choices = List.copyOf(choices);
        if (type.equals("enum") != !choices.isEmpty() || Set.copyOf(choices).size() != choices.size()
                || choices.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Invalid parameter choices");
        }
        defaultValue = immutableJson(defaultValue, 0);
    }

    private static boolean finiteOrNull(Double value) {
        return value == null || Double.isFinite(value);
    }

    static boolean key(String value) {
        return value != null && KEY.matcher(value).matches() && !RESERVED.contains(value);
    }

    static String resource(String value) {
        var id = UiId.parse(value);
        if (id.namespace().equals(".") || id.namespace().equals("..")) throw new IllegalArgumentException("Invalid resource: " + value);
        for (String segment : id.path().split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid resource: " + value);
            }
        }
        return value;
    }

    Object validate(String name, Object value, List<String> resources) {
        boolean valid = switch (type) {
            case "boolean" -> value instanceof Boolean;
            case "integer", "duration" -> bounded(value, true);
            case "float" -> bounded(value, false);
            case "enum" -> value instanceof String choice && choices.contains(choice);
            case "color" -> color(value);
            case "vector2", "vector3", "vector4" -> value instanceof List<?> list
                    && list.size() == type.charAt(type.length() - 1) - '0'
                    && list.stream().allMatch(item -> bounded(item, false));
            case "gradient" -> value instanceof List<?> list && (!list.isEmpty() || defaultValue instanceof List<?> base && base.isEmpty()) && list.size() <= 256
                    && list.stream().allMatch(MaterialParameter::color)
                    || orderedPoints(value, "offset", "color", MaterialParameter::color);
            case "texture" -> value instanceof String text && resources.contains(resource(text));
            case "curve" -> orderedPoints(value, "time", "value", item -> bounded(item, false));
            case "object" -> value instanceof Map<?, ?>;
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("Invalid parameter: " + name);
        return immutableJson(value, 0);
    }

    private boolean bounded(Object value, boolean integer) {
        if (!(value instanceof Number number)) return false;
        double scalar = number.doubleValue();
        return Double.isFinite(scalar) && (!integer || scalar == Math.rint(scalar) && Math.abs(scalar) <= 9007199254740991d)
                && (minimum == null || scalar >= minimum) && (maximum == null || scalar <= maximum);
    }

    private static boolean color(Object value) {
        return value instanceof String text && COLOR.matcher(text).matches();
    }

    private static boolean orderedPoints(Object value, String coordinate, String component, Predicate<Object> validComponent) {
        if (!(value instanceof List<?> points) || points.size() < 2 || points.size() > 256) return false;
        double previous = -1;
        for (Object item : points) {
            if (!(item instanceof Map<?, ?> point) || point.size() != 2
                    || !(point.get(coordinate) instanceof Number number) || !validComponent.test(point.get(component))) return false;
            double next = number.doubleValue();
            if (!(next >= 0 && next <= 1 && next > previous)) return false;
            previous = next;
        }
        return true;
    }

    static Object immutableJson(Object value, int depth) {
        if (depth > 16) throw new IllegalArgumentException("Parameter nesting exceeds 16 levels");
        if (value == null || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
            double scalar = number.doubleValue();
            return scalar == 0 ? 0d : scalar;
        }
        if (value instanceof List<?> list) {
            var copy = new ArrayList<>(list.size());
            for (Object item : list) copy.add(immutableJson(item, depth + 1));
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Map<?, ?> map) {
            var copy = new LinkedHashMap<String, Object>();
            map.forEach((name, item) -> {
                if (!(name instanceof String text) || RESERVED.contains(text)) throw new IllegalArgumentException("Reserved or invalid object key");
                copy.put(text, immutableJson(item, depth + 1));
            });
            return Collections.unmodifiableMap(copy);
        }
        throw new IllegalArgumentException("Unsupported JSON parameter value");
    }

    public record Constraint(String op, String parameter, Object value) {
        public Constraint {
            if (op == null || !Set.of("eq", "neq", "lt", "lte", "gt", "gte").contains(op)
                    || (parameter == null) == (value == null) || parameter != null && !key(parameter)
                    || value != null && !(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                throw new IllegalArgumentException("Invalid parameter constraint");
            }
            value = immutableJson(value, 0);
        }

        boolean permits(Object left, Object right) {
            if (op.equals("eq") || op.equals("neq")) {
                if (!(left instanceof String || left instanceof Number || left instanceof Boolean)
                        || right == null || left.getClass() != right.getClass()) return false;
                return op.equals("eq") == left.equals(right);
            }
            if (!(left instanceof Number a) || !(right instanceof Number b)) return false;
            return switch (op) {
                case "lt" -> a.doubleValue() < b.doubleValue();
                case "lte" -> a.doubleValue() <= b.doubleValue();
                case "gt" -> a.doubleValue() > b.doubleValue();
                case "gte" -> a.doubleValue() >= b.doubleValue();
                default -> false;
            };
        }
    }
}
