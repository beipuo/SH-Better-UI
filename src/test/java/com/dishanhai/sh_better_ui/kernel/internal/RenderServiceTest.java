// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.internal;

import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.kernel.api.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RenderServiceTest {
    @Test
    void restoresPoseAndClipAndRevokesTheContextAfterAFailingPass() {
        try (var fixture = new ClientServiceFixture(Set.of(), Set.of(UIPermission.REGISTER_RENDER_PASS))) {
            var service = fixture.publication.current().require(UIApis.RENDER);
            assertSame(ShBetterUiApi.graphics(), service.graphics());
            assertSame(ShBetterUiApi.blur(), service.blur());
            var captured = new AtomicReference<RenderService.Context>();
            var task = service.submit(fixture.module, fixture.module.scope(), RenderService.Surface.HUD,
                    RenderService.Layer.OVERLAY, 4, canvas -> {
                        captured.set(canvas);
                        canvas.clip(1, 2, 7, 8, () -> canvas.transform(4, 5, 2, () -> {
                            canvas.fill(1, 2, 3, 4, 0xff123456);
                            throw new IllegalStateException("failing pass");
                        }));
                    });
            var backend = new TestBackend();
            var viewport = new RenderService.Frame(RenderService.Surface.HUD, 100, 80, 2, 0.5f);
            fixture.client.rendering().render(viewport, backend, fixture.client::runFrame);
            assertEquals(List.of("push", "z204.0", "clip", "push", "z0.0", "scale", "fill", "pop", "unclip", "pop"),
                    backend.trace);
            assertEquals(TaskHandle.Status.FAILED, task.status());
            assertThrows(IllegalStateException.class, () -> captured.get().fill(1, 2, 3, 4, 0));
        }
    }

    private static final class TestBackend implements ClientRender.Backend {
        final List<String> trace = new ArrayList<>();
        @Override public void push() { trace.add("push"); }
        @Override public void pop() { trace.add("pop"); }
        @Override public void translate(double x, double y, double z) { trace.add("z" + z); }
        @Override public void scale(float factor) { trace.add("scale"); }
        @Override public void clip(int x, int y, int right, int bottom) { trace.add("clip"); }
        @Override public void unclip() { trace.add("unclip"); }
        @Override public void fill(int x, int y, int right, int bottom, int color) { trace.add("fill"); }
    }
}
