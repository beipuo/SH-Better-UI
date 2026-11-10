// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.Platform.*;
import static com.dishanhai.sh_better_ui.kernel.internal.TestModules.*;
import static org.junit.jupiter.api.Assertions.*;

class ResolutionContractTest {
    private static final CapabilityKey LAYOUT = new CapabilityKey(UiId.parse("example:layout"));
    private static final CapabilityKey MISSING = new CapabilityKey(UiId.parse("example:missing"));
    private static final UiVersion ONE = UiVersion.parse("1.0.0");
    private static final UiVersion TWO = UiVersion.parse("2.0.0");
    private static final UiId A = UiId.parse("example:a");
    private static final UiId B = UiId.parse("example:b");
    private static final UiId C = UiId.parse("example:c");

    @Test
    void discoveryVerifiesLoaderIdentityGrantsAndPlatform() {
        var portable = candidate(module("a").build());
        var neoOnly = candidate(module("b").platforms(Set.of(NEOFORGE_1_21_1)).build());
        var result = ModuleDiscovery.discover(FORGE_1_20_1, List.of(neoOnly, portable));
        assertEquals(List.of(A), result.modules().stream().map(ModuleDiscovery.Module::id).toList());
        assertTrue(result.diagnostics().get(0).contains("example:b"));
        var spoofed = new ModuleDiscovery.Candidate("different", "other.jar", portable.module(), Set.of(), false);
        assertThrows(SecurityException.class, () -> discover(spoofed));
        assertThrows(SecurityException.class, () -> discover(candidate(
                module("a").requestPermission(UIPermission.REPLACE_SERVICE).build())));
        assertThrows(IllegalArgumentException.class, () -> discover(candidate(module("a").apiContractVersion(2).build())));
        assertThrows(IllegalArgumentException.class, () -> discover(portable, portable));
    }

    @Test
    void dependenciesAndCapabilitiesOrderProvidersBeforeTheirConsumers() {
        var provider = candidate(module("b").providedCapability(LAYOUT, ONE).build());
        var consumer = candidate(module("a").requiredCapability(LAYOUT, VersionRange.ANY)
                .optionalCapability(MISSING, VersionRange.ANY).build());
        var dependent = candidate(module("c").dependency(A, VersionRange.ANY, true).build());
        var plan = DependencyGraph.resolve(discover(dependent, consumer, provider));
        assertEquals(List.of(B, A, C), plan.order().stream().map(ModuleDiscovery.Module::id).toList());
        assertEquals(Set.of(B), plan.requiredDependencies().get(A));
        assertEquals(Set.of(A), plan.requiredDependencies().get(C));
        assertTrue(plan.diagnostics().get(0).contains("example:missing"));
        assertEquals(plan, DependencyGraph.resolve(discover(provider, dependent, consumer)));
    }

    @Test
    void reportsCompleteRequiredCycleAndDropsOptionalCycles() {
        var a = candidate(module("a").dependency(B, VersionRange.ANY, true).build());
        var b = candidate(module("b").dependency(C, VersionRange.ANY, true).build());
        var c = candidate(module("c").dependency(A, VersionRange.ANY, true).build());
        var error = assertThrows(IllegalArgumentException.class,
                () -> DependencyGraph.resolve(discover(c, b, a)));
        assertTrue(error.getMessage().contains("example:a -> example:b -> example:c -> example:a"));

        var optional = candidate(module("c").dependency(A, VersionRange.ANY, false).build());
        var plan = DependencyGraph.resolve(discover(optional, a, b));
        assertEquals(List.of(C, B, A), plan.order().stream().map(ModuleDiscovery.Module::id).toList());
        assertTrue(plan.diagnostics().get(0).contains("cycle"));
    }

    @Test
    void rejectsMissingVersionsConflictsAndLaterPhaseRequirements() {
        var b = candidate(module("b").build());
        var mismatch = candidate(module("a").dependency(B, VersionRange.atLeast(TWO), true).build());
        assertThrows(IllegalArgumentException.class, () -> DependencyGraph.resolve(discover(mismatch, b)));
        assertThrows(IllegalArgumentException.class, () -> DependencyGraph.resolve(discover(mismatch)));
        var conflict = candidate(module("a").conflict(B).build());
        assertThrows(IllegalArgumentException.class, () -> DependencyGraph.resolve(discover(b, conflict)));
        var early = candidate(module("a").initializationPhase(ModuleDescriptor.InitializationPhase.SERVICE)
                .dependency(B, VersionRange.ANY, true).build());
        assertThrows(IllegalArgumentException.class, () -> DependencyGraph.resolve(discover(b, early)));
    }

    @Test
    void capabilityResolutionFiltersVersionBeforePreferenceAndReportsFallback() {
        var preferred = new CapabilityDescriptor(LAYOUT, ONE, B, 4, false);
        var tied = new CapabilityDescriptor(LAYOUT, ONE, A, 4, false);
        var fallback = new CapabilityDescriptor(LAYOUT, TWO, C, 50, true);
        var index = new CapabilityIndex(List.of(fallback, preferred, tied));
        assertEquals(List.of(tied, preferred, fallback), index.available());
        assertTrue(index.supports(LAYOUT));
        assertEquals(CapabilityStatus.AVAILABLE, index.status(LAYOUT));
        assertEquals(CapabilityStatus.UNAVAILABLE, index.status(MISSING));
        var request = new CapabilityRequest(Map.of(LAYOUT, VersionRange.atLeast(TWO)), Map.of(MISSING, VersionRange.ANY));
        var resolution = index.resolve(request);
        assertEquals(Map.of(LAYOUT, fallback), resolution.selected());
        assertTrue(resolution.requiredMissing().isEmpty());
        assertEquals(Set.of(MISSING), resolution.optionalMissing());
        assertEquals(CapabilityStatus.DEGRADED, resolution.status(LAYOUT));
        assertEquals(Set.of(MISSING), index.resolve(new CapabilityRequest(Map.of(MISSING, VersionRange.ANY), Map.of()))
                .requiredMissing());
    }
}
