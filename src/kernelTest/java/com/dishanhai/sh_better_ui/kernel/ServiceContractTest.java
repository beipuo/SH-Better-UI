// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel;

import com.dishanhai.sh_better_ui.kernel.api.APIRegistry;
import com.dishanhai.sh_better_ui.kernel.api.RegistrySnapshot;
import com.dishanhai.sh_better_ui.kernel.api.ServiceDescriptor;
import com.dishanhai.sh_better_ui.kernel.api.ServiceKey;
import com.dishanhai.sh_better_ui.kernel.api.UIService;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ServiceContractTest {
    private interface ExampleService extends UIService {}
    private interface OtherService extends UIService {}

    private static final UiId ID = UiId.parse("example:layout");
    private static final ServiceKey<ExampleService> KEY = new ServiceKey<>(ID, ExampleService.class, 1);

    @Test
    void serviceKeyIdentityIncludesNamespaceTypeAndContractVersion() {
        assertEquals(KEY, new ServiceKey<>(UiId.parse("example:layout"), ExampleService.class, 1));
        assertNotEquals(KEY, new ServiceKey<>(UiId.parse("other:layout"), ExampleService.class, 1));
        assertNotEquals(KEY, new ServiceKey<>(ID, OtherService.class, 1));
        assertNotEquals(KEY, new ServiceKey<>(ID, ExampleService.class, 2));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void serviceKeyRejectsErasedTypeBypassesAndNonPositiveVersions() {
        assertThrows(IllegalArgumentException.class, () -> new ServiceKey<>(ID, (Class) String.class, 1));
        assertThrows(IllegalArgumentException.class, () -> new ServiceKey<>(ID, ExampleService.class, 0));
        assertThrows(IllegalArgumentException.class, () -> new ServiceKey<>(ID, ExampleService.class, -1));
        assertThrows(NullPointerException.class, () -> new ServiceKey<>(null, ExampleService.class, 1));
        assertThrows(NullPointerException.class, () -> new ServiceKey<>(ID, null, 1));
    }

    @Test
    void descriptorDistinguishesProviderReleaseFromApiContractVersion() {
        UiVersion release = UiVersion.parse("2.3.4");
        var descriptor = new ServiceDescriptor<>(KEY, release, ServiceDescriptor.Visibility.MODULE_PRIVATE,
                8, true);
        assertEquals(1, descriptor.key().contractVersion());
        assertEquals(release, descriptor.providerVersion());
        assertEquals(ServiceDescriptor.Visibility.MODULE_PRIVATE, descriptor.visibility());
        assertEquals(8, descriptor.priority());
        assertTrue(descriptor.replaceable());
        assertThrows(NullPointerException.class,
                () -> new ServiceDescriptor<>(KEY, release, null, 8, true));
    }

    @Test
    void requireUsesTypedFindAndReportsTheMissingContract() {
        ExampleService service = new ExampleService() {};
        APIRegistry registry = new APIRegistry() {
            @Override
            public <T extends UIService> Optional<T> find(ServiceKey<T> key) {
                return KEY.equals(key) ? Optional.of(key.type().cast(service)) : Optional.empty();
            }

            @Override
            public RegistrySnapshot snapshot() {
                return new RegistrySnapshot(1, List.of());
            }
        };

        assertSame(service, registry.require(KEY));
        var missing = new ServiceKey<>(ID, ExampleService.class, 2);
        assertTrue(registry.find(missing).isEmpty());
        var error = assertThrows(NoSuchElementException.class, () -> registry.require(missing));
        assertTrue(error.getMessage().contains("example:layout"));
        assertTrue(error.getMessage().contains("contract 2"));
        assertThrows(NullPointerException.class, () -> registry.require(null));
    }
}
