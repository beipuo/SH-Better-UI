package com.dishanhai.sh_better_ui.client.gui.collision;

/** 山海：一次脏组求解的观察结果。视口事件的 second 为 null。 */
public record CollisionEvent(Type type, CollisionBody first, CollisionBody second) {
    public enum Type { CONTACT, UNRESOLVED, BOUNDARY_CLAMPED, MINIMUM_CLIPPED }
}
