// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.UIModule;
import com.dishanhai.sh_better_ui.kernel.api.UIPermission;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Reads a fixed descriptor from each loader-owned mod file. Never scans the class path. */
final class NativeModuleSources {
    private static final int MAX_BYTES = 16_384;
    private static final int MAX_MODULES = 128;
    private static final Set<UIPermission> OWN_SCREEN_GRANTS = Set.of(UIPermission.CAPTURE_INPUT,
            UIPermission.BLOCK_VANILLA_INPUT, UIPermission.REGISTER_RENDER_PASS);
    private static final System.Logger LOGGER = System.getLogger(NativeModuleSources.class.getName());

    private NativeModuleSources() {}

    static List<ModuleDiscovery.Candidate> discover(List<ClientUiKernel.ModFile> modFiles) {
        var layer = NativeModuleSources.class.getModule().getLayer();
        var sorted = modFiles.stream().sorted(Comparator.comparing(ClientUiKernel.ModFile::moduleName)
                .thenComparing(file -> file.descriptor().toString())).toList();
        var candidates = new ArrayList<ModuleDiscovery.Candidate>();
        for (var file : sorted) {
            if (!Files.isRegularFile(file.descriptor())) continue;
            try {
                var source = layer == null ? null : layer.findModule(file.moduleName()).orElse(null);
                if (source == null) throw new IllegalArgumentException("Mod file has no matching loader module");
                var lines = read(file.descriptor());
                var seen = new HashSet<String>();
                for (String line : lines) {
                    var entry = line.strip();
                    if (entry.isEmpty() || entry.startsWith("#")) continue;
                    if (candidates.size() == MAX_MODULES) {
                        throw new IllegalArgumentException("Client UI module discovery limit reached");
                    }
                    if (seen.add(entry)) load(file, source, entry, candidates);
                }
            } catch (IOException | RuntimeException | LinkageError failure) {
                LOGGER.log(System.Logger.Level.ERROR, "Reading UI modules from " + file.descriptor(), failure);
            }
        }
        return List.copyOf(candidates);
    }

    private static List<String> read(Path descriptor) throws IOException {
        try (var input = Files.newInputStream(descriptor)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES) throw new IOException("UI module descriptor exceeds the size limit");
            return StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().lines().toList();
        }
    }

    private static void load(ClientUiKernel.ModFile file, Module source, String entry,
                             List<ModuleDiscovery.Candidate> candidates) {
        try {
            String[] tokens = entry.split("\\s+");
            if (tokens.length != 2 || !file.modIds().contains(tokens[0])) {
                throw new SecurityException("Expected a mod ID owned by the descriptor's mod file");
            }
            Class<?> type = Class.forName(source, tokens[1]);
            if (type == null || type.getModule() != source) {
                throw new SecurityException("UI module class is outside the declaring mod file");
            }
            var module = type.asSubclass(UIModule.class).getConstructor().newInstance();
            candidates.add(new ModuleDiscovery.Candidate(tokens[0], file.descriptor() + "#" + tokens[1],
                    module, OWN_SCREEN_GRANTS, false));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            LOGGER.log(System.Logger.Level.ERROR, "Rejected UI module entry " + entry + " in " + file.descriptor(), failure);
        }
    }
}
