// Author: dishanhai
package com.dishanhai.sh_better_ui.client.gui.scaled;

/** Copies initial values and velocities into the existing spring model without touching live tracks. */
public final class SpringProjection {
    private SpringProjection() {}

    public static float[][] project(float[] initial, float[] target, float[] velocity, Spring.Preset preset,
                                     double durationSeconds, int samples) {
        if (initial.length != target.length || initial.length != velocity.length || initial.length > 16
                || samples < 2 || samples > 1024 || !Double.isFinite(durationSeconds)
                || durationSeconds <= 0 || durationSeconds > 10) {
            throw new IllegalArgumentException("Invalid spring projection budget");
        }
        var springs = new Spring[initial.length];
        for (int i = 0; i < springs.length; i++) {
            if (!Float.isFinite(initial[i]) || !Float.isFinite(target[i]) || !Float.isFinite(velocity[i])) {
                throw new IllegalArgumentException("Non-finite spring projection state");
            }
            springs[i] = new Spring(preset, initial[i]);
            springs[i].setTarget(target[i], velocity[i], 0);
        }
        var values = new float[samples][initial.length];
        System.arraycopy(initial, 0, values[0], 0, initial.length);
        long current = 0;
        for (int sample = 1; sample < samples; sample++) {
            long end = Math.round(durationSeconds * 1000 * sample / (samples - 1));
            while (current < end) {
                current = Math.min(end, current + 4);
                for (var spring : springs) spring.update(current);
            }
            for (int i = 0; i < springs.length; i++) values[sample][i] = springs[i].value();
        }
        return values;
    }
}
