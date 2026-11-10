// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

/** A registrar issued to one module for the duration of its register callback. */
public interface ServiceRegistrar {
    default <T extends UIService> RegistrationHandle register(ServiceDescriptor<T> descriptor, T service) {
        return register(descriptor, service, RegistrationConflictPolicy.REJECT);
    }

    <T extends UIService> RegistrationHandle register(ServiceDescriptor<T> descriptor, T service,
                                                      RegistrationConflictPolicy policy);
}
