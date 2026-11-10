// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.material;

import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Source-backed, renderer-independent information about a reusable material or effect. */
public record MaterialEntry(
        String schemaVersion, String id, String materialRevision, String name, Map<String, String> localizedNames,
        List<String> aliases, String category, List<String> tags, String description,
        List<String> visualCharacteristics, List<String> useCases, String sourceRepository, String sourceRevision,
        List<String> sourceFiles, List<String> publicSymbols, String publicEntrypoint, List<String> requiredCapabilities,
        List<String> requiredResources, Map<String, MaterialParameter> parameters, Map<String, Object> defaultValues,
        String nativePreviewId, String webRendererId, String rendererVersion, String previewScene,
        List<String> renderPassDependencies, Map<String, String> loaderCompatibility, String webCompatibility,
        String validationStatus, List<String> validationEvidence, boolean deprecated, String replacementId) {
    public static final String SCHEMA_VERSION = "1.0.0";
    private static final Set<String> LOADER_STATUS = Set.of("source-verified", "client-verified", "unsupported", "unverified");
    private static final Set<String> WEB_STATUS = Set.of("native-only", "web-unsupported", "web-approximation", "web-verified");
    private static final Set<String> VALIDATION_STATUS = Set.of("source-verified", "automated", "client-verified", "unverified");

    public MaterialEntry {
        if (!SCHEMA_VERSION.equals(schemaVersion)) throw new IllegalArgumentException("Unsupported material schema: " + schemaVersion);
        UiId.parse(id);
        UiVersion.parse(materialRevision);
        text(name);
        text(category);
        text(description);
        text(sourceRevision);
        if (!text(sourceRepository).startsWith("https://")) throw new IllegalArgumentException("Expected HTTPS source repository");
        localizedNames = Map.copyOf(localizedNames);
        localizedNames.values().forEach(MaterialEntry::text);
        aliases = texts(aliases);
        tags = texts(tags);
        visualCharacteristics = texts(visualCharacteristics);
        useCases = texts(useCases);
        sourceFiles = texts(sourceFiles);
        publicSymbols = texts(publicSymbols);
        if (sourceFiles.isEmpty() || !publicSymbols.contains(publicEntrypoint)) throw new IllegalArgumentException("Missing source or public entrypoint");
        requiredCapabilities = texts(requiredCapabilities);
        requiredCapabilities.forEach(UiId::parse);
        requiredResources = texts(requiredResources);
        requiredResources.forEach(MaterialParameter::resource);
        if (nativePreviewId != null) UiId.parse(nativePreviewId);
        if (rendererVersion != null) UiVersion.parse(rendererVersion);
        renderPassDependencies = texts(renderPassDependencies);
        loaderCompatibility = Map.copyOf(loaderCompatibility);
        if (!loaderCompatibility.keySet().equals(Set.of("forge", "neoforge"))
                || !LOADER_STATUS.containsAll(loaderCompatibility.values()) || !WEB_STATUS.contains(webCompatibility)
                || !VALIDATION_STATUS.contains(validationStatus)) throw new IllegalArgumentException("Invalid compatibility or validation status");
        boolean web = webCompatibility.equals("web-approximation") || webCompatibility.equals("web-verified");
        boolean invalidWeb = web
                ? webRendererId == null || webRendererId.isBlank() || rendererVersion == null || previewScene == null || previewScene.isBlank()
                : webRendererId != null || rendererVersion != null || previewScene != null;
        if (invalidWeb) {
            throw new IllegalArgumentException("Web renderer, version, scene and compatibility must agree");
        }
        validationEvidence = texts(validationEvidence);
        if (replacementId != null) {
            UiId.parse(replacementId);
            if (!deprecated) throw new IllegalArgumentException("Only retired materials may redirect");
        }
        parameters = Collections.unmodifiableMap(new LinkedHashMap<>(parameters));
        for (var item : parameters.entrySet()) {
            String parameter = item.getKey();
            if (!MaterialParameter.key(parameter)) throw new IllegalArgumentException("Invalid parameter key: " + parameter);
            var definition = item.getValue();
            for (String dependency : definition.dependsOn()) checkDependency(parameters, parameter, dependency);
            for (var constraint : definition.constraints()) {
                if (constraint.parameter() != null) checkDependency(parameters, parameter, constraint.parameter());
            }
        }
        var derived = validate(parameters, requiredResources, Map.of());
        if (defaultValues != null && !derived.equals(MaterialParameter.immutableJson(defaultValues, 0))) {
            throw new IllegalArgumentException("Divergent parameter defaults: " + id);
        }
        defaultValues = derived;
    }

    private static void checkDependency(Map<String, MaterialParameter> parameters, String key, String dependency) {
        if (key.equals(dependency) || !parameters.containsKey(dependency)) {
            throw new IllegalArgumentException("Invalid parameter dependency: " + dependency);
        }
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing material text");
        return value;
    }

    private static List<String> texts(List<String> values) {
        var copy = List.copyOf(values);
        copy.forEach(MaterialEntry::text);
        return copy;
    }

    /** Validates a whole edit transaction and fills any absent fields with their declared defaults. */
    public Map<String, Object> validateParameters(Map<String, Object> values) {
        return validate(parameters, requiredResources, values);
    }

    private static Map<String, Object> validate(Map<String, MaterialParameter> parameters, List<String> resources,
                                                Map<String, Object> values) {
        for (String name : values.keySet()) {
            if (!parameters.containsKey(name)) throw new IllegalArgumentException("Unknown parameter: " + name);
        }
        var result = new LinkedHashMap<String, Object>();
        parameters.forEach((name, parameter) -> {
            Object value = parameter.validate(name, values.getOrDefault(name, parameter.defaultValue()), resources);
            if (!parameter.editable() && !value.equals(parameter.defaultValue())) {
                throw new IllegalArgumentException("Read-only parameter: " + name);
            }
            result.put(name, value);
        });
        parameters.forEach((name, parameter) -> parameter.constraints().forEach(constraint -> {
            Object right = constraint.parameter() == null ? constraint.value() : result.get(constraint.parameter());
            if (!constraint.permits(result.get(name), right)) throw new IllegalArgumentException("Constraint failed: " + name + " " + constraint.op());
        }));
        return Collections.unmodifiableMap(result);
    }
}
