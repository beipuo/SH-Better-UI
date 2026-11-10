// Author: dishanhai
package com.dishanhai.shbui_kernel_example;

import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.live.ActivityService;
import com.dishanhai.sh_better_ui.kernel.api.live.NotificationService;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.Connection;

import java.time.Duration;
import java.util.List;

import static com.dishanhai.shbui_kernel_example.ExampleUiModule.id;
import static com.dishanhai.shbui_kernel_example.LiveActivityExamples.icon;
import static com.dishanhai.shbui_kernel_example.LiveActivityExamples.text;

/** Opt-in module-scoped watch that can follow the real Minecraft connection screens. */
final class ConnectionExample implements AutoCloseable {
    private final ModuleContext module;
    private final ActivityService activities;
    private final NotificationService notifications;
    private boolean enabled;
    private ActivityService.Handle pending;
    private Connection connected;

    ConnectionExample(ModuleContext module, ActivityService activities, NotificationService notifications) {
        this.module = module;
        this.activities = activities;
        this.notifications = notifications;
    }

    boolean enabled() { return enabled; }

    void toggle() {
        if (enabled) close();
        else enabled = true;
    }

    void screen(Screen screen) {
        if (!enabled) return;
        if (screen instanceof ConnectScreen && (pending == null || pending.snapshot().lifecycle().terminal())) {
            var content = new ActivityService.Content(text("connection_connecting"), text("connection_wait"),
                    icon("network"), -1, false, List.of(), List.of());
            try {
                pending = activities.publish(module, module.scope(), new ActivityService.Request(id("live/connection"),
                        content, ActivityService.Priority.NORMAL, Duration.ofSeconds(90), true, null));
            } catch (IllegalStateException unavailable) {
                notice("unavailable");
            }
        } else if (pending != null && screen instanceof DisconnectedScreen) {
            cancelPending();
            notice("connection_failed");
        } else if (pending != null && (screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen)) {
            cancelPending();
            notice("connection_cancelled");
        }
    }

    void joined(Connection connection) {
        if (!enabled || connection == null || connection.isMemoryConnection()) return;
        if (pending != null && !pending.snapshot().lifecycle().terminal()) pending.complete();
        pending = null;
        connected = connection;
        notice("connection_joined");
    }

    void left(Connection connection) {
        if (connected != null && (connection == null || connected == connection)) {
            connected = null;
            notice("connection_left");
        }
    }

    private void cancelPending() {
        LiveActivityExamples.cancel(pending);
        pending = null;
    }

    private void notice(String key) {
        LiveActivityExamples.notify(module, module.scope(), notifications, key, key, "network");
    }

    @Override public void close() {
        enabled = false;
        connected = null;
        cancelPending();
        if (module.scope().isOpen()) notifications.dismissAll(module, module.scope());
    }
}
