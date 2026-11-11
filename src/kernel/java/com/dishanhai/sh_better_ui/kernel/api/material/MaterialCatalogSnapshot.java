// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.material;

import com.dishanhai.sh_better_ui.kernel.api.UiVersion;

import java.text.Normalizer;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** An atomically published directory, including live extensions. The hash identifies the bundled release. */
public final class MaterialCatalogSnapshot {
    private final UiVersion version;
    private final String contentHash;
    private final long generation;
    private final List<MaterialEntry> entries;
    private final Map<String, MaterialEntry> ids;
    private final Map<String, MaterialEntry> aliases;

    public MaterialCatalogSnapshot(UiVersion version, String contentHash, long generation, Collection<MaterialEntry> values) {
        this.version = java.util.Objects.requireNonNull(version);
        if (!contentHash.matches("sha256:[a-f0-9]{64}") || generation < 0) throw new IllegalArgumentException("Invalid catalog identity");
        this.contentHash = contentHash;
        this.generation = generation;
        var byId = new TreeMap<String, MaterialEntry>();
        for (var entry : values) {
            if (byId.putIfAbsent(entry.id(), entry) != null) throw new IllegalArgumentException("Duplicate material ID: " + entry.id());
        }
        var byAlias = new HashMap<String, MaterialEntry>();
        for (var entry : byId.values()) {
            var seen = new HashSet<String>();
            for (String alias : entry.aliases()) {
                String key = normalize(alias);
                var stable = byId.get(key);
                var previous = byAlias.putIfAbsent(key, entry);
                if (key.isBlank() || !seen.add(key) || stable != null && stable != entry || previous != null && previous != entry) {
                    throw new IllegalArgumentException("Conflicting alias: " + alias);
                }
            }
        }
        this.entries = List.copyOf(byId.values());
        this.ids = Map.copyOf(byId);
        this.aliases = Map.copyOf(byAlias);
        for (var entry : entries) follow(entry);
    }

    public UiVersion version() { return version; }
    public String contentHash() { return contentHash; }
    public long generation() { return generation; }
    public List<MaterialEntry> entries() { return entries; }

    public Optional<MaterialEntry> find(String id) {
        if (id == null) return Optional.empty();
        String key = normalize(id);
        var entry = ids.getOrDefault(key, aliases.get(key));
        return entry == null ? Optional.empty() : Optional.of(follow(entry));
    }

    private MaterialEntry follow(MaterialEntry entry) {
        if (entry.replacementId() == null) return entry;
        var visited = new HashSet<String>();
        while (entry.replacementId() != null) {
            if (!visited.add(entry.id())) throw new IllegalArgumentException("Cyclic material replacement: " + entry.id());
            String replacement = entry.replacementId();
            entry = ids.get(replacement);
            if (entry == null) throw new IllegalArgumentException("Missing material replacement: " + replacement);
        }
        return entry;
    }

    public static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
    }
}
