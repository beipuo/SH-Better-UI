package com.dishanhai.sh_better_ui.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Objects;

/** 山海：可持久化的主题效果覆写。空对象继承默认值。 */
public final class ThemeOverrides {
    public static final ThemeOverrides EMPTY = new ThemeOverrides("{}");
    public static final int MAX_LENGTH = 32767;

    private final String json;
    private final JsonObject values;

    public ThemeOverrides(String json) {
        Objects.requireNonNull(json);
        if (json.length() > MAX_LENGTH) throw new IllegalArgumentException("Theme settings are too large");
        try {
            values = JsonParser.parseString(json).getAsJsonObject();
            for (var entry : values.entrySet()) {
                if (!entry.getValue().isJsonObject()) throw new IllegalArgumentException(entry.getKey());
            }
            this.json = values.toString();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid theme settings", e);
        }
    }

    public static boolean valid(Object value) {
        if (!(value instanceof String json)) return false;
        try {
            new ThemeOverrides(json);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public JsonObject group(String name) {
        var group = values.getAsJsonObject(name);
        return group == null ? new JsonObject() : group.deepCopy();
    }

    public ThemeOverrides withGroup(String name, JsonObject group) {
        var next = values.deepCopy();
        if (group.size() == 0) next.remove(name);
        else next.add(name, group);
        return new ThemeOverrides(next.toString());
    }

    /** 局部参数按字段覆盖；未提供的分组和嵌套字段继续继承当前快照。 */
    public ThemeOverrides overlay(ThemeOverrides local) {
        Objects.requireNonNull(local);
        if (local.equals(EMPTY)) return this;
        var next = values.deepCopy();
        merge(next, local.values);
        return new ThemeOverrides(next.toString());
    }

    private static void merge(JsonObject target, JsonObject local) {
        for (var entry : local.entrySet()) {
            var existing = target.get(entry.getKey());
            if (entry.getValue().isJsonObject() && existing != null && existing.isJsonObject()) {
                merge(existing.getAsJsonObject(), entry.getValue().getAsJsonObject());
            } else {
                target.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
    }

    public boolean contains(String name) { return values.has(name); }
    public java.util.Set<String> groups() { return java.util.Set.copyOf(values.keySet()); }
    public String json() { return json; }

    @Override
    public boolean equals(Object other) {
        return other instanceof ThemeOverrides overrides && values.equals(overrides.values);
    }

    @Override
    public int hashCode() { return values.hashCode(); }
}
