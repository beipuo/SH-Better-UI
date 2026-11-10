// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api;

/** The effective value change for a key at one atomic publication. */
public record ValueChange<T>(T previous, T current, long version) {}
