// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

public interface RegistrationHandle extends AutoCloseable {
    enum Status { ACTIVE, SKIPPED, REVOKED }

    Status status();

    /** Withdraws an active registration during its owner's register callback. */
    @Override
    void close();
}
