// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.live;

import com.dishanhai.sh_better_ui.kernel.api.*;

import java.util.List;

/** Observes the adaptive live surface and reserves HUD space through the Unified UI Kernel. */
public interface LiveSurfaceService extends UIService {
    /** Lookup through the module's Unified UI engine. */
    ServiceKey<LiveSurfaceService> KEY = new ServiceKey<>(UiId.parse("sh_better_ui:live_surfaces"), LiveSurfaceService.class, 1);
    CapabilityKey CAPABILITY = new CapabilityKey(KEY.id());

    /** Latest immutable arbitration plan and animated geometry. Safe to read off-thread. */
    View view();

    /** Latest counters and timings. Timings are CPU nanoseconds of the last measured operation. */
    Diagnostics diagnostics();

    /**
     * Reserves GUI-coordinate space in the current viewport until the scope or handle closes.
     * UI thread only. IDs must belong to the issuing module. Duplicate IDs and exhausted capacity throw.
     */
    Reservation reserve(ModuleContext module, ScopeHandle scope, UiId id, SurfacePlacement.Rect bounds);

    /** Request to protect an area from the adaptive live surface. This capability affects only its own area. */
    interface Reservation extends AutoCloseable {
        /** Replaces the bounds on the UI thread. Throws if this reservation is closed. */
        void update(SurfacePlacement.Rect bounds);
        /** Releases the reservation on the UI thread. Idempotent. */
        @Override void close();
    }

    /** Arbitration result. Primary and secondary are null when no activity occupies those slots. */
    record Plan(ActivityService.Snapshot primary, ActivityService.Snapshot secondary, List<UiId> waiting,
                SurfacePresentation requested, boolean interactive, boolean mayPreempt) {
        public static final Plan EMPTY = new Plan(null, null, List.of(), SurfacePresentation.HIDDEN, false, true);

        public Plan { waiting = List.copyOf(waiting); }
    }

    /** Target, settled logical state and currently drawn geometry remain independent. */
    record View(Plan plan, SurfacePresentation target, SurfacePresentation settled,
                SurfaceGeometry primary, SurfaceGeometry secondary, boolean interactive, boolean transitioning) {
        public static final View EMPTY = new View(Plan.EMPTY, SurfacePresentation.HIDDEN, SurfacePresentation.HIDDEN,
                SurfaceGeometry.HIDDEN, SurfaceGeometry.HIDDEN, false, false);

        public View(Plan plan, SurfacePresentation target, SurfacePresentation settled,
                    SurfaceGeometry primary, SurfaceGeometry secondary, boolean interactive) {
            this(plan, target, settled, primary, secondary, interactive, false);
        }
    }

    /** Number of live resources, coalesced and rejected updates, and recent CPU work in nanoseconds. */
    record Diagnostics(int activities, int notifications, long coalescedUpdates, long rejectedUpdates,
                       long stateNanos, long layoutNanos, long animationNanos, long renderNanos, long inputNanos) {}
}
