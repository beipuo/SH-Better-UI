// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

/** Count budgets are hard limits. Phase time is cooperative between callbacks. */
record SchedulerBudget(int phaseCapacity, int backgroundCapacity, int backgroundWorkers,
                       int frameTasks, int phaseTasks, int modulePhaseTasks, long phaseNanos) {
    static final SchedulerBudget DEFAULT = new SchedulerBudget(512, 128, 2, 256, 64, 32, 1_000_000);

    SchedulerBudget {
        if (phaseCapacity < 1 || backgroundCapacity < 1 || backgroundWorkers < 1
                || frameTasks < 1 || phaseTasks < 1 || modulePhaseTasks < 1 || phaseNanos < 1) {
            throw new IllegalArgumentException("UI scheduler budgets must be positive");
        }
    }
}
