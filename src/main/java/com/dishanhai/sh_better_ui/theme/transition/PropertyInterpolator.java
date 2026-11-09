// Author: dishanhai
package com.dishanhai.sh_better_ui.theme.transition;

/** Interpolates an immutable property or group of properties. Progress is in [0, 1]. */
@FunctionalInterface
public interface PropertyInterpolator<T> {
    T interpolate(T from, T to, double progress);
}
