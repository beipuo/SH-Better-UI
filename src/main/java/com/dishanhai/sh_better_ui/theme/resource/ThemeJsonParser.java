// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.resource;

import com.dishanhai.sh_better_ui.theme.Theme;
import com.dishanhai.sh_better_ui.theme.ThemeId;
import com.dishanhai.sh_better_ui.theme.style.StyleRule;
import com.dishanhai.sh_better_ui.theme.style.StyleSelector;
import com.dishanhai.sh_better_ui.theme.style.StyleState;
import com.dishanhai.sh_better_ui.theme.token.ColorValue;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import com.dishanhai.sh_better_ui.theme.token.TokenExpression;
import com.dishanhai.sh_better_ui.theme.token.TokenType;
import com.dishanhai.sh_better_ui.theme.transition.Easing;
import com.dishanhai.sh_better_ui.theme.transition.VisualProperties.Shadow;
import com.dishanhai.sh_better_ui.theme.transition.VisualProperties.Transform;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Bounded, data-only parser for schema version 1. Unknown fields and duplicate keys fail the load. */
public final class ThemeJsonParser {
    public static final int MAX_BYTES = 65_536;
    private static final int MAX_DEPTH = 16;
    private static final int MAX_NODES = 4096;
    private static final int MAX_TOKENS = 256;
    private static final int MAX_STYLES = 64;
    private static final int MAX_EXPRESSION_DEPTH = 8;
    private static final Set<String> FIELDS = Set.of("schemaVersion", "id", "parent", "nameKey",
            "metadata", "tokens", "styles");
    private final Map<TokenType<?>, Function<JsonElement, ?>> codecs;

    public ThemeJsonParser() { this(Map.of()); }

    private ThemeJsonParser(Map<TokenType<?>, Function<JsonElement, ?>> codecs) {
        this.codecs = Map.copyOf(codecs);
    }

    /** Returns a parser with an immutable copy of a trusted extension codec. The codec runs on a reload worker. */
    public <T> ThemeJsonParser withCodec(TokenType<T> type, Function<JsonElement, T> codec) {
        var next = new LinkedHashMap<>(codecs);
        next.put(Objects.requireNonNull(type), Objects.requireNonNull(codec));
        return new ThemeJsonParser(next);
    }

    /** Parses a theme under the ID assigned by its resource path. */
    public Theme parse(String resourceId, String source, String json, Map<String, ThemeToken<?>> catalog) {
        ThemeId.require(resourceId);
        if (json.length() > MAX_BYTES) throw new IllegalArgumentException("Theme JSON is too large");
        JsonObject root;
        try (var reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            root = object(read(reader, 0, new int[]{MAX_NODES}), "theme");
            if (reader.peek() != JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing JSON");
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid JSON: " + failure.getMessage(), failure);
        }
        fields(root, FIELDS);
        if (integer(root.get("schemaVersion"), "schemaVersion") != 1) {
            throw new IllegalArgumentException("Unsupported schemaVersion");
        }
        if (root.has("id") && !resourceId.equals(string(root.get("id"), "id"))) {
            throw new IllegalArgumentException("Theme id must match resource path: " + resourceId);
        }
        var theme = Theme.builder(resourceId).source(source);
        if (root.has("parent")) theme.parent(string(root.get("parent"), "parent"));
        if (root.has("nameKey")) theme.nameKey(string(root.get("nameKey"), "nameKey"));
        if (root.has("metadata")) {
            var metadata = object(root.get("metadata"), "metadata");
            limit(metadata.size(), 32, "metadata fields");
            metadata.entrySet().forEach(entry -> theme.metadata(entry.getKey(), string(entry.getValue(), entry.getKey())));
        }
        if (root.has("tokens")) {
            var tokens = object(root.get("tokens"), "tokens");
            limit(tokens.size(), MAX_TOKENS, "tokens");
            var assigned = new java.util.HashSet<ThemeToken<?>>();
            tokens.entrySet().forEach(entry -> {
                var token = key(catalog, entry.getKey());
                if (!assigned.add(token)) throw new IllegalArgumentException("Duplicate token " + token);
                addExpression(theme, token, entry.getValue(), catalog);
            });
        }
        if (root.has("styles")) {
            var styles = array(root.get("styles"), "styles");
            limit(styles.size(), MAX_STYLES, "styles");
            for (var entry : styles) theme.style(style(object(entry, "style"), catalog));
        }
        return theme.build();
    }

    private StyleRule style(JsonObject json, Map<String, ThemeToken<?>> catalog) {
        fields(json, Set.of("role", "states", "values"));
        var states = EnumSet.noneOf(StyleState.class);
        if (json.has("states")) {
            var array = array(json.get("states"), "states");
            limit(array.size(), StyleState.values().length, "style states");
            for (var state : array) {
                if (!states.add(StyleState.valueOf(string(state, "style state").toUpperCase(Locale.ROOT)))) {
                    throw new IllegalArgumentException("Duplicate style state " + state);
                }
            }
        }
        var builder = StyleRule.when(new StyleSelector(string(json.get("role"), "role"), states));
        var values = object(json.get("values"), "style values");
        limit(values.size(), MAX_TOKENS, "style values");
        var assigned = new java.util.HashSet<ThemeToken<?>>();
        values.entrySet().forEach(entry -> {
            var token = key(catalog, entry.getKey());
            if (!assigned.add(token)) throw new IllegalArgumentException("Duplicate style token " + token);
            addValue(builder, token, entry.getValue());
        });
        return builder.build();
    }

    private <T> void addValue(StyleRule.Builder builder, ThemeToken<T> token, JsonElement json) {
        builder.set(token, literal(token.type(), json));
    }

    private <T> void addExpression(Theme.Builder builder, ThemeToken<T> token, JsonElement json,
                                   Map<String, ThemeToken<?>> catalog) {
        builder.derive(token, expression(token.type(), json, catalog, 0));
    }

    private <T> TokenExpression<T> expression(TokenType<T> type, JsonElement json, Map<String, ThemeToken<?>> catalog,
                                             int depth) {
        if (depth > MAX_EXPRESSION_DEPTH) throw new IllegalArgumentException("Expression nesting limit exceeded");
        if (json.isJsonObject()) {
            var object = json.getAsJsonObject();
            if (object.has("ref")) {
                fields(object, Set.of("ref"));
                ThemeToken<?> reference = key(catalog, string(object.get("ref"), "ref"));
                if (reference.type() != type) throw new IllegalArgumentException("Incompatible reference: " + reference);
                return new Reference<>(type, reference);
            }
            if (object.has("mix")) {
                fields(object, Set.of("mix"));
                if (type != TokenType.COLOR) throw new IllegalArgumentException("mix requires a color token");
                var args = array(object.get("mix"), "mix");
                if (args.size() != 3) throw new IllegalArgumentException("mix expects two colors and a fraction");
                var from = expression(TokenType.COLOR, args.get(0), catalog, depth + 1);
                var to = expression(TokenType.COLOR, args.get(1), catalog, depth + 1);
                double fraction = number(args.get(2), "mix fraction").doubleValue();
                if (!(fraction >= 0 && fraction <= 1)) throw new IllegalArgumentException("mix fraction outside [0, 1]");
                return new Mix<>(type, from, to, fraction);
            }
        }
        return new TokenExpression.Literal<>(literal(type, json));
    }

    private record Reference<T>(TokenType<T> type, ThemeToken<?> reference) implements TokenExpression<T> {
        @Override
        public T evaluate(com.dishanhai.sh_better_ui.theme.token.TokenLookup lookup) {
            return type.cast(lookup.resolve(reference));
        }
    }

    private record Mix<T>(TokenType<T> type, TokenExpression<ColorValue> from, TokenExpression<ColorValue> to,
                          double fraction) implements TokenExpression<T> {
        @Override
        public T evaluate(com.dishanhai.sh_better_ui.theme.token.TokenLookup lookup) {
            return type.cast(ColorValue.interpolateOklab(from.evaluate(lookup), to.evaluate(lookup), fraction));
        }
    }

    private <T> T literal(TokenType<T> type, JsonElement json) {
        var codec = codecs.get(type);
        if (codec != null) return type.cast(codec.apply(json));
        Class<?> valueClass = type.valueClass();
        Object value;
        if (valueClass == ColorValue.class) value = ColorValue.parse(string(json, "color"));
        else if (valueClass == Float.class) value = number(json, "float").floatValue();
        else if (valueClass == Double.class) value = number(json, "double").doubleValue();
        else if (valueClass == Integer.class) value = integer(json, "integer");
        else if (valueClass == String.class) value = string(json, "string");
        else if (valueClass == Boolean.class && json.isJsonPrimitive() && json.getAsJsonPrimitive().isBoolean()) {
            value = json.getAsBoolean();
        } else if (valueClass == Duration.class) value = Duration.ofMillis(number(json, "duration milliseconds").longValueExact());
        else if (type == ThemeTokens.EASING) value = Easing.valueOf(string(json, "easing").toUpperCase(Locale.ROOT));
        else if (valueClass == Shadow.class) value = shadow(object(json, "shadow"));
        else if (valueClass == Transform.class) value = transform(object(json, "transform"));
        else throw new IllegalArgumentException("No matching JSON literal or codec for " + valueClass.getSimpleName());
        return type.cast(value);
    }

    private static Shadow shadow(JsonObject json) {
        fields(json, Set.of("x", "y", "radius", "color"));
        return new Shadow(number(json.get("x"), "x").floatValue(), number(json.get("y"), "y").floatValue(),
                number(json.get("radius"), "radius").floatValue(), ColorValue.parse(string(json.get("color"), "color")));
    }

    private static Transform transform(JsonObject json) {
        fields(json, Set.of("x", "y", "scaleX", "scaleY", "rotation"));
        return new Transform(number(json.get("x"), "x").floatValue(), number(json.get("y"), "y").floatValue(),
                number(json.get("scaleX"), "scaleX").floatValue(), number(json.get("scaleY"), "scaleY").floatValue(),
                number(json.get("rotation"), "rotation").floatValue());
    }

    private static ThemeToken<?> key(Map<String, ThemeToken<?>> catalog, String name) {
        ThemeToken<?> token = catalog.get(name.contains(":") ? name : "sh_better_ui:" + name);
        if (token == null) throw new IllegalArgumentException("Unknown token: " + name);
        return token;
    }

    private static JsonElement read(JsonReader reader, int depth, int[] budget) throws IOException {
        if (depth > MAX_DEPTH || --budget[0] < 0) throw new IllegalArgumentException("Theme JSON complexity limit");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    if (key.length() > 256) throw new IllegalArgumentException("JSON property name too long");
                    if (object.has(key)) throw new IllegalArgumentException("Duplicate JSON field: " + key);
                    object.add(key, read(reader, depth + 1, budget));
                }
                reader.endObject();
                yield object;
            }
            case BEGIN_ARRAY -> {
                var array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) array.add(read(reader, depth + 1, budget));
                reader.endArray();
                yield array;
            }
            case STRING -> new JsonPrimitive(reader.nextString());
            case NUMBER -> {
                String number = reader.nextString();
                if (number.length() > 64) throw new IllegalArgumentException("Number too long");
                yield new JsonPrimitive(new BigDecimal(number));
            }
            case BOOLEAN -> new JsonPrimitive(reader.nextBoolean());
            default -> throw new IllegalArgumentException("Unexpected JSON value at " + reader.getPath());
        };
    }

    private static void fields(JsonObject json, Set<String> allowed) {
        for (String key : json.keySet()) {
            if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown JSON field: " + key);
        }
    }

    private static JsonObject object(JsonElement value, String field) {
        if (value == null || !value.isJsonObject()) throw new IllegalArgumentException("Expected object for " + field);
        return value.getAsJsonObject();
    }

    private static JsonArray array(JsonElement value, String field) {
        if (value == null || !value.isJsonArray()) throw new IllegalArgumentException("Expected array for " + field);
        return value.getAsJsonArray();
    }

    private static String string(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected string for " + field);
        }
        String text = value.getAsString();
        if (text.isEmpty() || text.length() > 256) throw new IllegalArgumentException("Invalid string for " + field);
        return text;
    }

    private static BigDecimal number(JsonElement value, String field) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected number for " + field);
        }
        return value.getAsBigDecimal();
    }

    private static int integer(JsonElement value, String field) { return number(value, field).intValueExact(); }

    private static void limit(int count, int max, String field) {
        if (count > max) throw new IllegalArgumentException("Too many " + field + " (" + max + " maximum)");
    }
}
