package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.gui.scaled.PatternWavesProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.ShapeWavesProfile;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiTimeline;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 山海：双端门面的可选依赖隔离与有状态工厂契约。 */
class ShBetterUiApiTest {
    @Test
    void commonAndPlatformGroupsLinkWithoutOptionalIntegrations() throws ReflectiveOperationException {
        var loader = getClass().getClassLoader();
        for (String optionalClass : new String[]{"mezz.jei.api.IModPlugin",
                "com.lowdragmc.lowdraglib.gui.modular.ModularUIGuiContainer",
                "com.lowdragmc.lowdraglib2.gui.ui.UIElement",
                "dev.latvian.mods.kubejs.KubeJSPlugin",
                "dev.latvian.mods.kubejs.plugin.KubeJSPlugin"}) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName(optionalClass, false, loader));
        }

        var api = loadApi();
        for (String group : new String[]{"settings", "controls", "interactions", "search", "ingredients",
                "materials", "blur", "graphics", "motion", "audio", "platform", "client", "extensions"}) {
            var entry = api.getMethod(group).invoke(null);
            assertDoesNotThrow(() -> entry.getClass().getDeclaredMethods(), group);
        }
    }

    @Test
    void instanceFacadeAndIndependentContextsShareTheRegisteredGroups() {
        var client = ShBetterUiApi.client();
        assertSame(ShBetterUiApi.controls(), client.controls());
        assertSame(ShBetterUiApi.motion(), client.motion());
        assertSame(ShBetterUiApi.extensions(), client.extensions());
        try (var first = client.context(); var second = client.context()) {
            assertNotSame(first, second);
            assertSame(client.controls(), first.controls());
            assertSame(client.extensions(), first.extensions());
        }
    }

    @Test
    void audioFactoriesAreIndependentAndNeverStartRecordingOnCreation() {
        var audio = ShBetterUiApi.audio();
        try (var first = audio.systemOutput(); var second = audio.systemOutput()) {
            assertNotSame(first, second);
            assertEquals(com.dishanhai.sh_better_ui.client.audio.SystemAudioCapture.Status.READY, first.status());
            assertEquals(0, first.sample().rms());
            assertNotSame(audio.analyzer(), audio.analyzer());
            assertNotSame(ShBetterUiApi.motion().musicMotion(), ShBetterUiApi.motion().musicMotion());
        }
    }

    @Test
    void eachTimelineHasItsOwnPlaybackState() {
        UiTimeline first = ShBetterUiApi.motion().timeline(30, 120);
        UiTimeline second = ShBetterUiApi.motion().timeline(30, 120);

        first.seek(45);

        assertNotSame(first, second);
        assertEquals(45, first.sample(0).index());
        assertEquals(0, second.sample(0).index());
    }

    @Test
    void waveFactoriesKeepProfilesAndPlaybackStatePerInstance() {
        var motion = ShBetterUiApi.motion();
        var patternProfile = PatternWavesProfile.Preset.MESH.profile();
        var pattern = motion.patternWaves(patternProfile);
        var otherPattern = motion.patternWaves(patternProfile);
        pattern.setPaused(true);
        assertFalse(otherPattern.isPaused());
        assertSame(patternProfile, pattern.profile());
        assertSame(PatternWavesProfile.Preset.SILK.profile(), motion.patternWaves().profile());

        var shapeProfile = ShapeWavesProfile.builder().flow(2).build();
        var shape = motion.shapeWaves(shapeProfile);
        var otherShape = motion.shapeWaves(shapeProfile);
        shape.setPaused(true);
        assertFalse(otherShape.isPaused());
        assertSame(shapeProfile, shape.profile());
        assertSame(ShapeWavesProfile.DEFAULT, motion.shapeWaves().profile());
    }

    private Class<?> loadApi() {
        return assertDoesNotThrow(() -> Class.forName("com.dishanhai.sh_better_ui.client.api.ShBetterUiApi",
                true, getClass().getClassLoader()), "The shared API entry point must be loadable");
    }
}
