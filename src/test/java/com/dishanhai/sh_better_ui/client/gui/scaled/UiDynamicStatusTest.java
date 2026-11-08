package com.dishanhai.sh_better_ui.client.gui.scaled;

import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicIconMotion;
import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback.Status;
import com.dishanhai.sh_better_ui.client.gui.scaled.ButtonFeedback.Tone;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：实时读取、进度文案以及状态材质的契约。 */
class UiDynamicStatusTest {
    @Test
    void drawsTheLatestSnapshotAndOnlyReadsTheSourceOncePerRender() {
        var feedback = new ButtonFeedback();
        var calls = new AtomicInteger();
        var now = new AtomicInteger();
        var area = new Bounds(0, 0, 0, 0);
        var display = new UiDynamicStatus(() -> {
            calls.incrementAndGet();
            return new UiDynamicStatus.Snapshot(feedback.status(1, now.get()));
        });

        var ready = display.render(null, null, area, UiPreferences.DEFAULT);
        assertEquals(Status.READY, ready.status());
        feedback.pending(1, 42, now.incrementAndGet());
        var waiting = display.render(null, null, area, UiPreferences.DEFAULT);
        assertEquals(Status.PENDING, waiting.status());
        assertTrue(feedback.resolve(42, Tone.SUCCESS, now.incrementAndGet()));
        assertEquals(Status.SUCCEEDED, display.render(null, null, area, UiPreferences.DEFAULT).status());
        assertEquals(Status.PENDING, waiting.status());
        assertEquals(3, calls.get());
    }

    @Test
    void formatsDeterminateProgressWithTheCustomLabel() {
        var label = Component.translatable("options.title");
        var unknown = new UiDynamicStatus.Snapshot(Status.PENDING, label);
        assertSame(label, unknown.text());
        var progress = new UiDynamicStatus.Snapshot(Status.PENDING, label, 42);
        var text = assertInstanceOf(TranslatableContents.class, progress.text().getContents());
        assertEquals("sh_better_ui.status.progress", text.getKey());
        assertArrayEquals(new Object[]{label, 42}, text.getArgs());
        assertEquals(0, new UiDynamicStatus.Snapshot(Status.PENDING, 0).progressPercent());
        assertEquals(100, new UiDynamicStatus.Snapshot(Status.SUCCEEDED, 100).progressPercent());
        assertThrows(IllegalArgumentException.class, () -> new UiDynamicStatus.Snapshot(Status.PENDING, -2));
        assertThrows(IllegalArgumentException.class, () -> new UiDynamicStatus.Snapshot(Status.PENDING, 101));
    }

    @Test
    void eachStateHasItsOwnNamedMaterialInBothSizes() {
        var textures = new HashSet<ResourceLocation>();
        for (Status status : Status.values()) {
            var label = new UiDynamicStatus.Snapshot(status).text();
            var translation = assertInstanceOf(TranslatableContents.class, label.getContents());
            assertEquals(status.translationKey(), translation.getKey());
            var icon = UiDynamicStatus.icon(status);
            var texture = icon.texture();
            assertTrue(textures.add(texture));
            String path = "/assets/" + texture.getNamespace() + "/" + texture.getPath();
            assertNotNull(UiDynamicStatus.class.getResource(path), path);
            assertNotNull(UiDynamicStatus.class.getResource(path.replace("/display/", "/display_64/")), path);
            assertSame(status == Status.PENDING ? GraphicIconMotion.SPIN : null, icon.motion());
        }
    }
}
