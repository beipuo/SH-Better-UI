package com.dishanhai.sh_better_ui.client.gui.display;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：内置网络图标必须随库打包，并且可按 ARGB 着色。 */
class NetworkIconTest {
    @Test
    void networkTabIconsAreTransparentSixteenPixelTextures() throws IOException {
        var icons = List.of(GraphicIcon.HOME, GraphicIcon.LIST, GraphicIcon.WIRELESS, GraphicIcon.TOPOLOGY,
                GraphicIcon.STATISTICS, GraphicIcon.MEMBERS, GraphicIcon.SETTINGS, GraphicIcon.ADD);
        for (var icon : icons) {
            var id = icon.texture();
            String path = "/assets/" + id.getNamespace() + "/" + id.getPath();
            try (var stream = GraphicIcon.class.getResourceAsStream(path)) {
                assertNotNull(stream, path);
                var image = ImageIO.read(stream);
                assertNotNull(image, path);
                assertEquals(16, image.getWidth(), path);
                assertEquals(16, image.getHeight(), path);
                boolean transparent = false;
                boolean painted = false;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int pixel = image.getRGB(x, y);
                        if ((pixel >>> 24) == 0) {
                            transparent = true;
                        } else {
                            assertEquals(0xFFFFFF, pixel & 0xFFFFFF, path);
                            painted = true;
                        }
                    }
                }
                assertTrue(transparent && painted, path);
            }
        }
    }
}
