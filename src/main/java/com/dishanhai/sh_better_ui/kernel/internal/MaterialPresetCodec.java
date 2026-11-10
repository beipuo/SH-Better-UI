// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialCatalogSnapshot;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialEntry;
import com.dishanhai.sh_better_ui.kernel.api.material.MaterialPreset;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class MaterialPresetCodec {
    private static final int MAX_BYTES = 64 * 1024;
    private static final Set<String> FIELDS = Set.of("schemaVersion", "catalogVersion", "materialId", "materialRevision", "parameters");
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private final String schemaVersion;
    private final List<Migration> migrations;

    MaterialPresetCodec(String schemaVersion, List<Migration> migrations) {
        UiVersion.parse(schemaVersion);
        this.schemaVersion = schemaVersion;
        this.migrations = List.copyOf(migrations);
    }

    String export(MaterialCatalogSnapshot catalog, String id, Map<String, Object> values) {
        var entry = active(catalog, id);
        var document = new LinkedHashMap<String, Object>();
        document.put("schemaVersion", schemaVersion);
        document.put("catalogVersion", catalog.version().toString());
        document.put("materialId", entry.id());
        document.put("materialRevision", entry.materialRevision());
        document.put("parameters", entry.validateParameters(values));
        return bounded(JSON.toJson(document));
    }

    MaterialPreset read(MaterialCatalogSnapshot catalog, String json) {
        Map<String, Object> document;
        try (var reader = new JsonReader(new StringReader(bounded(json)))) {
            document = object(readValue(reader, 0));
            if (reader.peek() != JsonToken.END_DOCUMENT || !document.keySet().equals(FIELDS)) {
                throw new IllegalArgumentException("Invalid preset document");
            }
        } catch (IOException | IllegalStateException failure) {
            throw new IllegalArgumentException("Invalid preset JSON", failure);
        }
        var entry = active(catalog, string(document.get("materialId")));
        var release = UiVersion.parse(string(document.get("catalogVersion")));
        String schema = string(document.get("schemaVersion"));
        String revision = string(document.get("materialRevision"));
        UiVersion.parse(schema);
        UiVersion.parse(revision);
        var values = object(document.get("parameters"));
        var visited = new HashSet<String>();
        while (!schema.equals(schemaVersion) || !revision.equals(entry.materialRevision())) {
            String key = schema + "/" + revision;
            if (!visited.add(key)) throw new IllegalArgumentException("Cyclic preset migration");
            var step = migration(entry.id(), schema, revision);
            var renamed = new LinkedHashMap<String, Object>();
            values.forEach((name, value) -> {
                String next = step.rename().getOrDefault(name, name);
                if (renamed.containsKey(next)) throw new IllegalArgumentException("Conflicting migrated parameter: " + next);
                renamed.put(next, value);
            });
            values = renamed;
            schema = step.toSchemaVersion();
            revision = step.toRevision();
        }
        if (release.compareTo(catalog.version()) > 0 || visited.isEmpty() && release.major() != catalog.version().major()) {
            throw new IllegalArgumentException("Incompatible catalog release: " + release);
        }
        return new MaterialPreset(schemaVersion, catalog.version().toString(), entry.id(), entry.materialRevision(),
                entry.validateParameters(values));
    }

    private Migration migration(String id, String schema, String revision) {
        return migrations.stream().filter(step -> step.materialId().equals(id)
                && step.fromSchemaVersion().equals(schema) && step.fromRevision().equals(revision)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Incompatible preset schema or material revision: " + schema + "/" + revision));
    }

    private static MaterialEntry active(MaterialCatalogSnapshot catalog, String id) {
        return catalog.find(id).filter(entry -> !entry.deprecated())
                .orElseThrow(() -> new IllegalArgumentException("Unknown or retired material: " + id));
    }

    private static String bounded(String text) {
        if (text == null || text.length() > MAX_BYTES || text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("Preset exceeds 64 KiB");
        }
        return text;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalArgumentException("Expected a JSON object");
    }

    private static String string(Object value) {
        if (value instanceof String text) return text;
        throw new IllegalArgumentException("Expected a JSON string");
    }

    private static Object readValue(JsonReader reader, int depth) throws IOException {
        if (depth > 18) throw new IllegalArgumentException("Preset nesting exceeds 18 levels");
        return switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                var map = new LinkedHashMap<String, Object>();
                reader.beginObject();
                while (reader.hasNext()) {
                    String key = reader.nextName();
                    map.put(key, readValue(reader, depth + 1));
                }
                reader.endObject();
                yield map;
            }
            case BEGIN_ARRAY -> {
                var values = new ArrayList<>();
                reader.beginArray();
                while (reader.hasNext()) values.add(readValue(reader, depth + 1));
                reader.endArray();
                yield values;
            }
            case BOOLEAN -> reader.nextBoolean();
            case NUMBER -> reader.nextDouble();
            case STRING -> reader.nextString();
            case NULL -> { reader.nextNull(); yield null; }
            default -> throw new IllegalArgumentException("Invalid JSON value");
        };
    }

    record Migration(String materialId, String fromRevision, String toRevision, String fromSchemaVersion,
                     String toSchemaVersion, Map<String, String> rename) {}
}
