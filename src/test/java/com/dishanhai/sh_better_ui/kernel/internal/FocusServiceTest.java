// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.kernel.api.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class FocusServiceTest {
    private static final UiId FOCUS = UiId.parse("example:focus");
    private static final ModuleDescriptor.ResourceClaim CLAIM =
            new ModuleDescriptor.ResourceClaim(ModuleDescriptor.ResourceKind.KEYBOARD_FOCUS, FOCUS);
    private static final UiId TREE = UiId.parse("example:tree");

    @Test
    void sharesNativeFocusAndRestoresOnlyWhileTheRequestedWidgetStillHasFocus() {
        try (var fixture = new ClientServiceFixture(Set.of(CLAIM), Set.of())) {
            var screen = new TestScreen();
            var first = screen.button();
            var second = screen.button();
            var third = screen.button();
            screen.setFocused(first);
            var nativeScope = fixture.client.screens().show(screen);
            var tree = fixture.client.screens().mountTrusted(fixture.module, screen, TREE);
            var service = fixture.publication.current().require(UIApis.FOCUS);
            var seen = new ArrayList<NativeInput>();
            service.target(fixture.module, tree, second, 0);
            fixture.module.engine().events().subscribe(InputService.EVENT, tree, UIEventPhase.TARGET, 0,
                    event -> seen.add(event.payload()));

            var lease = service.request(fixture.module, tree, FOCUS, second, 0).orElseThrow();
            assertSame(second, screen.getFocused());
            var key = new NativeInput.Key(NativeInput.KeyAction.PRESS, 65, 1, 0);
            fixture.client.dispatch(nativeScope, key);
            assertEquals(List.of(key), seen);
            lease.close();
            assertSame(first, screen.getFocused());

            var next = service.request(fixture.module, tree, FOCUS, second, 0).orElseThrow();
            screen.setFocused(third);
            fixture.client.reconcileFocus();
            assertFalse(next.isActive());
            assertSame(third, screen.getFocused());

            var closing = service.request(fixture.module, tree, FOCUS, second, 0).orElseThrow();
            fixture.client.screens().show(null);
            assertFalse(closing.isActive());
            assertFalse(tree.isOpen());
        }
    }

    @Test
    void replacementLeavesTheNewScreenFocusedWhenRestoringTheOldWidgetSwitchesScreens() {
        try (var fixture = new ClientServiceFixture(Set.of(CLAIM), Set.of())) {
            var screen = new TestScreen();
            var previous = screen.button();
            var current = screen.button();
            var replacement = screen.button();
            screen.setFocused(previous);
            var screens = fixture.client.screens();
            screens.show(screen);
            var tree = screens.mountTrusted(fixture.module, screen, TREE);
            var service = fixture.publication.current().require(UIApis.FOCUS);
            var firstLease = service.request(fixture.module, tree, FOCUS, current, 0).orElseThrow();

            var nextScreen = new TestScreen();
            var nextWidget = nextScreen.button();
            var nextLease = new AtomicReference<OwnershipLease>();
            screen.whenFocused(previous, () -> {
                screens.show(nextScreen);
                var nextTree = screens.mountTrusted(fixture.module, nextScreen, TREE);
                nextLease.set(service.request(fixture.module, nextTree, FOCUS, nextWidget, 0).orElseThrow());
            });

            assertTrue(service.request(fixture.module, tree, FOCUS, replacement, 1).isEmpty());
            assertFalse(firstLease.isActive());
            assertFalse(tree.isOpen());
            assertSame(previous, screen.getFocused());
            assertSame(nextWidget, nextScreen.getFocused());
            fixture.client.reconcileFocus();
            assertTrue(nextLease.get().isActive());
            nextLease.get().close();
            assertNull(nextScreen.getFocused());
        }
    }

    @Test
    void requestReturnsEmptyWhenTheNativeFocusSetterClosesItsScreen() {
        try (var fixture = new ClientServiceFixture(Set.of(CLAIM), Set.of())) {
            var screen = new TestScreen();
            var widget = screen.button();
            var screens = fixture.client.screens();
            screens.show(screen);
            var tree = screens.mountTrusted(fixture.module, screen, TREE);
            var service = fixture.publication.current().require(UIApis.FOCUS);
            screen.whenFocused(widget, () -> screens.show(new TestScreen()));

            assertTrue(service.request(fixture.module, tree, FOCUS, widget, 0).isEmpty());
            assertFalse(tree.isOpen());
        }
    }

    private static final class TestScreen extends Screen {
        private GuiEventListener callbackTarget;
        private Runnable focusCallback;

        TestScreen() { super(Component.empty()); }

        Button button() {
            return addRenderableWidget(Button.builder(Component.empty(), button -> {}).bounds(0, 0, 12, 12).build());
        }

        void whenFocused(GuiEventListener widget, Runnable callback) {
            callbackTarget = widget;
            focusCallback = callback;
        }

        @Override
        public void setFocused(GuiEventListener widget) {
            super.setFocused(widget);
            if (focusCallback != null && widget == callbackTarget) {
                var callback = focusCallback;
                focusCallback = null;
                callback.run();
            }
        }
    }
}
