// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.NativeInput;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
import org.lwjgl.system.MemoryStack;

/** Normalizes GLFW-mapped controller edges while the explicit live activity screen owns input. */
final class ClientLiveGamepad {
    private static final int[] KEYS = {
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_DELETE, GLFW.GLFW_KEY_P,
            GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_LEFT
    };
    private static final int[] BUTTONS = {
            GLFW.GLFW_GAMEPAD_BUTTON_A, GLFW.GLFW_GAMEPAD_BUTTON_B, GLFW.GLFW_GAMEPAD_BUTTON_X, GLFW.GLFW_GAMEPAD_BUTTON_Y,
            GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT,
            GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN, GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT
    };
    private int device = -1, previous;
    private LiveActivityScreen owner;

    void poll(Minecraft client) {
        if (!(client.screen instanceof LiveActivityScreen screen) || !client.isWindowActive() || client.getOverlay() != null) {
            owner = null;
            device = -1;
            previous = 0;
            return;
        }
        boolean fresh = owner != screen;
        owner = screen;
        try (var stack = MemoryStack.stackPush()) {
            var state = GLFWGamepadState.malloc(stack);
            if (device < 0 || !GLFW.glfwGetGamepadState(device, state)) {
                device = -1;
                for (int id = GLFW.GLFW_JOYSTICK_1; id <= GLFW.GLFW_JOYSTICK_LAST; id++) {
                    if (GLFW.glfwGetGamepadState(id, state)) {
                        device = id;
                        break;
                    }
                }
                if (device < 0) return;
                fresh = true;
            }
            int current = 0;
            for (int index = 0; index < BUTTONS.length; index++) {
                if (state.buttons(BUTTONS[index]) == GLFW.GLFW_PRESS) current |= 1 << index;
            }
            int pressed = fresh ? 0 : current & ~previous;
            previous = current;
            for (int index = 0; index < KEYS.length && client.screen == screen; index++) {
                if ((pressed & 1 << index) == 0) continue;
                int key = KEYS[index];
                boolean consumed = ClientUiKernel.nativeInput(screen, new NativeInput.Key(NativeInput.KeyAction.PRESS, key, 0, 0));
                if (consumed) {
                    ClientUiKernel.nativeInput(screen, new NativeInput.Key(NativeInput.KeyAction.RELEASE, key, 0, 0));
                } else if (key == GLFW.GLFW_KEY_ESCAPE) {
                    screen.onClose();
                }
            }
        }
    }
}
