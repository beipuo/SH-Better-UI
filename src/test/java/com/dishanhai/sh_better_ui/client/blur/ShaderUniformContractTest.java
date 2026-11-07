package com.dishanhai.sh_better_ui.client.blur;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 山海：验证两平台的构建资源与 Minecraft float count=2 的 glUniform2 上传契约。 */
class ShaderUniformContractTest {
    @Test
    void waveOriginMatchesTheTwoComponentFloatUpload() throws Exception {
        String root = "/assets/sh_better_ui/shaders/program/shop_gauss_blur";
        try (var stream = getClass().getResourceAsStream(root + ".json")) {
            assertNotNull(stream);
            var uniforms = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonArray("uniforms");
            var origin = java.util.stream.StreamSupport.stream(uniforms.spliterator(), false)
                    .map(element -> element.getAsJsonObject())
                    .filter(uniform -> uniform.get("name").getAsString().equals("WaveOrigin"))
                    .findFirst().orElseThrow();
            assertEquals("float", origin.get("type").getAsString());
            assertEquals(2, origin.get("count").getAsInt());
        }
        try (var stream = getClass().getResourceAsStream(root + ".fsh")) {
            assertNotNull(stream);
            String fragment = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(fragment.matches("(?s).*uniform\\s+vec2\\s+WaveOrigin\\s*;.*"),
                    "float/count=2 is uploaded with glUniform2 and requires GLSL vec2 WaveOrigin");
        }
    }
}
