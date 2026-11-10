// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** An immutable index of offers from one set of modules. */
final class CapabilityIndex implements CapabilityResolver {
    private static final Comparator<CapabilityDescriptor> ORDER = Comparator.comparing(CapabilityDescriptor::key)
            .thenComparing(CapabilityDescriptor::fallback)
            .thenComparing(Comparator.comparingInt(CapabilityDescriptor::priority).reversed())
            .thenComparing(CapabilityDescriptor::version, Comparator.reverseOrder())
            .thenComparing(CapabilityDescriptor::provider);

    private final List<CapabilityDescriptor> available;
    private final Map<CapabilityKey, List<CapabilityDescriptor>> byKey;

    CapabilityIndex(Collection<CapabilityDescriptor> offers) {
        available = offers.stream().sorted(ORDER).toList();
        var index = new HashMap<CapabilityKey, List<CapabilityDescriptor>>();
        for (var offer : available) {
            index.computeIfAbsent(offer.key(), key -> new ArrayList<>()).add(offer);
        }
        index.replaceAll((key, providers) -> List.copyOf(providers));
        byKey = Map.copyOf(index);
    }

    static CapabilityIndex fromModules(Collection<ModuleDiscovery.Module> modules) {
        var offers = new ArrayList<CapabilityDescriptor>();
        for (var module : modules) {
            module.descriptor().providedCapabilities().forEach((key, offer) -> offers.add(
                    new CapabilityDescriptor(key, offer.version(), module.id(), offer.priority(), offer.fallback())));
        }
        return new CapabilityIndex(offers);
    }

    @Override
    public CapabilityStatus status(CapabilityKey key) {
        var offers = byKey.get(Objects.requireNonNull(key, "key"));
        if (offers == null) {
            return CapabilityStatus.UNAVAILABLE;
        }
        return offers.get(0).fallback() ? CapabilityStatus.DEGRADED : CapabilityStatus.AVAILABLE;
    }

    @Override
    public List<CapabilityDescriptor> available() {
        return available;
    }

    @Override
    public CapabilityResolution resolve(CapabilityRequest request) {
        var selected = new HashMap<CapabilityKey, CapabilityDescriptor>();
        var requiredMissing = new HashSet<CapabilityKey>();
        var optionalMissing = new HashSet<CapabilityKey>();
        collect(request.required(), selected, requiredMissing);
        collect(request.optional(), selected, optionalMissing);
        return new CapabilityResolution(selected, requiredMissing, optionalMissing);
    }

    CapabilityDescriptor select(CapabilityKey key, VersionRange versions, Predicate<UiId> eligible) {
        for (var offer : byKey.getOrDefault(key, List.of())) {
            if (versions.contains(offer.version()) && eligible.test(offer.provider())) {
                return offer;
            }
        }
        return null;
    }

    private void collect(Map<CapabilityKey, VersionRange> requirements,
                         Map<CapabilityKey, CapabilityDescriptor> selected, Set<CapabilityKey> missing) {
        requirements.forEach((key, range) -> {
            var offer = select(key, range, provider -> true);
            if (offer == null) {
                missing.add(key);
            } else {
                selected.put(key, offer);
            }
        });
    }
}
