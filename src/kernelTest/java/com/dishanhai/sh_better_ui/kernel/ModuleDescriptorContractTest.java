// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel;

import com.dishanhai.sh_better_ui.kernel.api.CapabilityKey;
import com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.UIEngine;
import com.dishanhai.sh_better_ui.kernel.api.UIPermission;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.VersionRange;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.dishanhai.sh_better_ui.kernel.api.ModuleDescriptor.*;
import static org.junit.jupiter.api.Assertions.*;

class ModuleDescriptorContractTest {
    private static final UiId MODULE = UiId.parse("example:ui");
    private static final UiId DEPENDENCY = UiId.parse("other:widgets");
    private static final UiVersion RELEASE = UiVersion.parse("1.0.0");
    private static final CapabilityKey THEME = new CapabilityKey(UiId.parse("example:theme"));
    private static final CapabilityKey FOCUS = new CapabilityKey(UiId.parse("example:focus"));
    private static final CapabilityKey LAYOUT = new CapabilityKey(UiId.parse("example:layout"));

    @Test
    void minimalBuilderDescribesAnExtensionForBothPlatforms() {
        ModuleDescriptor descriptor = builder().id("example:ui").version("1.0.0").build();
        assertEquals(MODULE, descriptor.id());
        assertEquals(RELEASE, descriptor.version());
        assertEquals(UIEngine.CONTRACT_VERSION, descriptor.apiContractVersion());
        assertEquals(InitializationPhase.EXTENSION, descriptor.initializationPhase());
        assertEquals(ExecutionAffinity.UI_THREAD, descriptor.affinity());
        assertTrue(descriptor.requestedPermissions().isEmpty());
        assertTrue(descriptor.configurationSchema().isEmpty());
        assertEquals(Set.of(Platform.FORGE_1_20_1, Platform.NEOFORGE_1_21_1), descriptor.platforms());
    }

    @Test
    void builderCapturesTypedMetadataAndBuildsAnIndependentSnapshot() {
        var schema = new SchemaReference(UiId.parse("example:settings"), 2);
        var resource = new ResourceClaim(ResourceKind.RENDER_TARGET, UiId.parse("example:overlay"));
        var conflict = UiId.parse("other:overlay");
        var builder = builder().id(MODULE).version(RELEASE).apiContractVersion(2)
                .requiredCapability(THEME, VersionRange.atLeast(RELEASE))
                .optionalCapability(FOCUS, VersionRange.ANY)
                .providedCapability(LAYOUT, RELEASE)
                .dependency(DEPENDENCY, VersionRange.ANY, false)
                .conflict(conflict)
                .initializationPhase(InitializationPhase.SERVICE)
                .affinity(ExecutionAffinity.RENDER_THREAD)
                .requestPermission(UIPermission.REGISTER_RENDER_PASS)
                .configurationSchema(schema)
                .claimResource(resource)
                .platforms(Set.of(Platform.NEOFORGE_1_21_1));
        ModuleDescriptor descriptor = builder.build();
        builder.requestPermission(UIPermission.ACCESS_INTERNAL_DIAGNOSTICS);
        assertEquals(2, descriptor.apiContractVersion());
        assertEquals(Map.of(THEME, VersionRange.atLeast(RELEASE)), descriptor.requiredCapabilities());
        assertEquals(Map.of(FOCUS, VersionRange.ANY), descriptor.optionalCapabilities());
        assertEquals(Map.of(LAYOUT, new CapabilityOffer(RELEASE, 0, false)), descriptor.providedCapabilities());
        assertEquals(Map.of(DEPENDENCY, new Dependency(VersionRange.ANY, false)), descriptor.dependencies());
        assertEquals(Set.of(conflict), descriptor.conflicts());
        assertEquals(InitializationPhase.SERVICE, descriptor.initializationPhase());
        assertEquals(ExecutionAffinity.RENDER_THREAD, descriptor.affinity());
        assertEquals(Set.of(UIPermission.REGISTER_RENDER_PASS), descriptor.requestedPermissions());
        assertEquals(Optional.of(schema), descriptor.configurationSchema());
        assertEquals(Set.of(resource), descriptor.resourceClaims());
        assertEquals(Set.of(Platform.NEOFORGE_1_21_1), descriptor.platforms());
    }

    @Test
    void canonicalConstructorCopiesCallerCollections() {
        var requirements = new HashMap<>(Map.of(THEME, VersionRange.ANY));
        var platforms = new HashSet<>(Set.of(Platform.FORGE_1_20_1));
        var descriptor = new ModuleDescriptor(MODULE, RELEASE, 1, requirements, Map.of(), Map.of(),
                Map.of(), Set.of(), InitializationPhase.EXTENSION, ExecutionAffinity.UI_THREAD, Set.of(),
                Optional.empty(), Set.of(), platforms);

        requirements.clear();
        platforms.clear();
        assertEquals(Map.of(THEME, VersionRange.ANY), descriptor.requiredCapabilities());
        assertEquals(Set.of(Platform.FORGE_1_20_1), descriptor.platforms());
        assertThrows(UnsupportedOperationException.class, descriptor.requiredCapabilities()::clear);
        assertThrows(UnsupportedOperationException.class, descriptor.platforms()::clear);
    }

    @Test
    void invalidModuleMetadataIsRejectedBeforeDiscovery() {
        assertThrows(NullPointerException.class, () -> builder().id(MODULE).build());
        assertThrows(IllegalArgumentException.class, () -> module().apiContractVersion(0).build());
        assertThrows(IllegalArgumentException.class, () -> module().platforms(Set.of()).build());
        assertThrows(IllegalArgumentException.class, () -> module().dependency(MODULE, VersionRange.ANY, true).build());
        assertThrows(IllegalArgumentException.class, () -> module().conflict(MODULE).build());
        assertThrows(IllegalArgumentException.class, () -> module().requiredCapability(THEME, VersionRange.ANY)
                .optionalCapability(THEME, VersionRange.ANY).build());
        assertThrows(IllegalArgumentException.class, () -> module().dependency(DEPENDENCY, VersionRange.ANY, true)
                .dependency(DEPENDENCY, VersionRange.ANY, false));
        assertThrows(IllegalArgumentException.class, () -> new SchemaReference(MODULE, 0));
    }

    private static Builder module() {
        return builder().id(MODULE).version(RELEASE);
    }
}
