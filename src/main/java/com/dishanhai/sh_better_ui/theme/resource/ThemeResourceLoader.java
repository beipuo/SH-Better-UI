// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.resource;

import com.dishanhai.sh_better_ui.theme.Theme;
import com.dishanhai.sh_better_ui.theme.ThemeId;
import com.dishanhai.sh_better_ui.theme.token.ThemeToken;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Worker-safe preparation of a whole ordered resource-pack layer. */
public final class ThemeResourceLoader {
    public static final String DIRECTORY = "ui/themes";
    private static final int MAX_RESOURCES = 512;
    private static final int MAX_STACK_ENTRIES = 4096;
    private static final int MAX_CACHE_ENTRIES = 64;
    private static final int MAX_TOTAL_BYTES = 4 * 1024 * 1024;
    private final ThemeJsonParser parser;
    private final LinkedHashMap<CacheKey, Theme> cache = new LinkedHashMap<>(16, 0.75f, true);
    private Map<String, ThemeToken<?>> cachedCatalog;

    public ThemeResourceLoader(ThemeJsonParser parser) { this.parser = Objects.requireNonNull(parser); }

    /** Theme entries are ordered from lowest to highest priority within each ID. */
    public Batch prepare(Collection<Resource> resources, Map<String, ThemeToken<?>> catalog) {
        if (resources.size() > MAX_STACK_ENTRIES) return Batch.failed("Too many theme resource stack entries");
        var stacks = new TreeMap<String, List<Resource>>();
        var diagnostics = new ArrayList<String>();
        for (var resource : resources) stacks.computeIfAbsent(resource.id, ignored -> new ArrayList<>()).add(resource);
        if (stacks.size() > MAX_RESOURCES) return Batch.failed("Too many resource themes");
        var themes = new ArrayList<Theme>();
        int bytes = 0;
        for (var entry : stacks.entrySet()) {
            var stack = entry.getValue();
            var winner = stack.get(stack.size() - 1);
            if (stack.size() > 1) diagnostics.add(winner.id + " selected from " + winner.pack + " over "
                    + stack.subList(0, stack.size() - 1).stream().map(Resource::pack).toList());
            try (InputStream stream = winner.open.open()) {
                byte[] raw = stream.readNBytes(ThemeJsonParser.MAX_BYTES + 1);
                bytes += raw.length;
                if (raw.length > ThemeJsonParser.MAX_BYTES || bytes > MAX_TOTAL_BYTES) {
                    throw new IllegalArgumentException("Theme resources exceed byte budget");
                }
                String text = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString();
                themes.add(parse(winner, text, catalog));
            } catch (IOException | RuntimeException failure) {
                return Batch.failed(winner.id + " [" + winner.pack + "]: " + failure.getMessage());
            }
        }
        return new Batch(themes, diagnostics, null);
    }

    private Theme parse(Resource resource, String text, Map<String, ThemeToken<?>> catalog) {
        var key = new CacheKey(resource.id, resource.pack, text);
        synchronized (cache) {
            if (cachedCatalog != catalog) {
                cache.clear();
                cachedCatalog = catalog;
            }
            Theme existing = cache.get(key);
            if (existing != null) return existing;
        }
        var parsed = parser.parse(resource.id, resource.pack, text, catalog);
        synchronized (cache) {
            if (cachedCatalog == catalog) {
                cache.put(key, parsed);
                if (cache.size() > MAX_CACHE_ENTRIES) cache.remove(cache.keySet().iterator().next());
            }
        }
        return parsed;
    }

    /** Computes an ID from a resource-pack file path under assets/&lt;namespace&gt;. */
    public static String themeId(String namespace, String path) {
        String prefix = DIRECTORY + "/";
        if (!path.startsWith(prefix) || !path.endsWith(".json")) throw new IllegalArgumentException("Invalid theme path: " + path);
        return ThemeId.require(namespace + ":" + path.substring(prefix.length(), path.length() - 5));
    }

    @FunctionalInterface
    public interface OpenResource {
        InputStream open() throws IOException;
    }

    /** A deferred resource read with provenance. The loader closes each opened stream. */
    public record Resource(String id, String pack, OpenResource open) {
        public Resource {
            ThemeId.require(id);
            Objects.requireNonNull(pack);
            Objects.requireNonNull(open);
        }
    }

    /** Immutable preparation result. An error invalidates the complete batch. */
    public record Batch(List<Theme> definitions, List<String> diagnostics, String error) {
        public Batch {
            definitions = List.copyOf(definitions);
            diagnostics = List.copyOf(diagnostics);
        }

        public static Batch failed(String error) { return new Batch(List.of(), List.of(), Objects.requireNonNull(error)); }
    }

    private record CacheKey(String id, String pack, String text) {}
}
