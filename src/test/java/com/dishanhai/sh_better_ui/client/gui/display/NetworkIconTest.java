package com.dishanhai.sh_better_ui.client.gui.display;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：内置图标必须随库打包，并且可按 ARGB 着色。 */
class NetworkIconTest {
    @Test
    void networkTabIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.HOME, GraphicIcon.LIST, GraphicIcon.WIRELESS, GraphicIcon.TOPOLOGY,
                GraphicIcon.STATISTICS, GraphicIcon.MEMBERS, GraphicIcon.SETTINGS, GraphicIcon.ADD));
    }

    @Test
    void overlayAndRefreshIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.BUBBLE, GraphicIcon.PULL_DOWN, GraphicIcon.REFRESH, GraphicIcon.INFO,
                GraphicIcon.WARNING, GraphicIcon.LIST, GraphicIcon.SUCCESS, GraphicIcon.ERROR, GraphicIcon.CLOSE));
    }

    @Test
    void searchAndActionIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.SEARCH, GraphicIcon.FILTER, GraphicIcon.SORT, GraphicIcon.EDIT,
                GraphicIcon.COPY, GraphicIcon.DELETE, GraphicIcon.FAVORITE, GraphicIcon.LOCK, GraphicIcon.UNLOCK,
                GraphicIcon.EXPAND, GraphicIcon.COLLAPSE, GraphicIcon.UPLOAD, GraphicIcon.DOWNLOAD,
                GraphicIcon.BACK, GraphicIcon.FORWARD, GraphicIcon.SWITCH, GraphicIcon.MERGE, GraphicIcon.NEW,
                GraphicIcon.SAVE, GraphicIcon.EXPORT));
        assertSame(GraphicIcon.BUBBLE, GraphicIcon.COMMENT);
    }

    @Test
    void severityAndMaintenanceIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.WARNING, GraphicIcon.CRITICAL_WARNING, GraphicIcon.CANCEL,
                GraphicIcon.CRITICAL_CANCEL, GraphicIcon.REPAIR, GraphicIcon.RESET, GraphicIcon.DISABLE,
                GraphicIcon.ENABLE, GraphicIcon.HIDE, GraphicIcon.SHOW, GraphicIcon.ORGANIZE, GraphicIcon.SORT));
        assertSame(GraphicIcon.CLOSE, GraphicIcon.CANCEL);
    }

    @Test
    void utilityAndDeviceIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.CACHE, GraphicIcon.LOG, GraphicIcon.MONITOR, GraphicIcon.TODO,
                GraphicIcon.DOCUMENT, GraphicIcon.CODE, GraphicIcon.TEST, GraphicIcon.DEVELOPMENT,
                GraphicIcon.COLLECTION, GraphicIcon.SEPARATE, GraphicIcon.HISTORY, GraphicIcon.USER,
                GraphicIcon.SYSTEM, GraphicIcon.PHONE, GraphicIcon.COMPUTER));
    }

    @Test
    void blockItemAndDetailIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.BLOCK, GraphicIcon.ITEM, GraphicIcon.ATTRIBUTES,
                GraphicIcon.DESCRIPTION, GraphicIcon.TOOLTIP, GraphicIcon.INTERACTION));
    }

    @Test
    void runPauseAndResumeIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.RUN, GraphicIcon.PAUSE, GraphicIcon.RESUME));
    }

    @Test
    void gameAndPeripheralIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.MINECRAFT, GraphicIcon.GAMEPAD, GraphicIcon.MOUSE,
                GraphicIcon.KEYBOARD, GraphicIcon.HEADPHONES, GraphicIcon.MICROPHONE, GraphicIcon.FORGE,
                GraphicIcon.NEOFORGE));
    }

    @Test
    void modIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.AE2, GraphicIcon.MEKANISM, GraphicIcon.FTB_QUESTS,
                GraphicIcon.KUBEJS, GraphicIcon.GREGTECH));
    }

    @Test
    void archiveSecurityAndReferenceIconsAreTransparentSixteenPixelTextures() throws IOException {
        assertIconTextures(List.of(GraphicIcon.COMPRESS, GraphicIcon.DECOMPRESS, GraphicIcon.ENCRYPT,
                GraphicIcon.DECRYPT, GraphicIcon.PLUGIN, GraphicIcon.GITHUB, GraphicIcon.MCMOD,
                GraphicIcon.WIKI));
    }

    @Test
    void administrationAndInfrastructureIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.SETTINGS, GraphicIcon.CONFIG, GraphicIcon.MANAGEMENT,
                GraphicIcon.PERMISSIONS, GraphicIcon.LIST, GraphicIcon.DETAILS, GraphicIcon.EDIT, GraphicIcon.MOD,
                GraphicIcon.TEMPLATE, GraphicIcon.DATA, GraphicIcon.NETWORK, GraphicIcon.SERVER,
                GraphicIcon.DATABASE, GraphicIcon.BACKUP, GraphicIcon.MIGRATE, GraphicIcon.UPGRADE,
                GraphicIcon.SECURITY, GraphicIcon.AUDIT, GraphicIcon.REPORT));
    }

    @Test
    void executionAndTaskIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.QUEUE, GraphicIcon.POLLING, GraphicIcon.ASYNC,
                GraphicIcon.CONCURRENCY, GraphicIcon.THREAD, GraphicIcon.PROCESS, GraphicIcon.PARALLEL,
                GraphicIcon.REWARD, GraphicIcon.SCHEDULER, GraphicIcon.TASK));
    }

    @Test
    void githubNavigationIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.GITHUB_REPO, GraphicIcon.GITHUB_PULL_REQUEST, GraphicIcon.GITHUB_ISSUE,
                GraphicIcon.GITHUB_DISCUSSION, GraphicIcon.GITHUB_ACTIONS, GraphicIcon.GITHUB_README,
                GraphicIcon.GITHUB_RELEASE, GraphicIcon.GITHUB_COMMITS));
    }

    @Test
    void licenseAndProjectCreditIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.LICENSE, GraphicIcon.LICENSE_OPEN_SOURCE,
                GraphicIcon.LICENSE_CLOSED_SOURCE, GraphicIcon.AUTHORIZATION, GraphicIcon.CONTRIBUTORS,
                GraphicIcon.AFDIAN, GraphicIcon.SUPPORT, GraphicIcon.ACKNOWLEDGMENTS));
    }

    @Test
    void aiBrandIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.CHATGPT, GraphicIcon.CLAUDE, GraphicIcon.DEEPSEEK,
                GraphicIcon.GROK, GraphicIcon.GEMINI));
    }

    @Test
    void contactIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.QQ, GraphicIcon.WECHAT, GraphicIcon.X, GraphicIcon.EMAIL,
                GraphicIcon.QQ_MAIL, GraphicIcon.ICLOUD_MAIL, GraphicIcon.GMAIL, GraphicIcon.DISCORD,
                GraphicIcon.TELEGRAM, GraphicIcon.WHATSAPP, GraphicIcon.FACEBOOK));
    }

    @Test
    void videoPlatformIconsHaveBothTextureSizes() throws IOException {
        assertIconTextures(List.of(GraphicIcon.BILIBILI, GraphicIcon.DOUYIN, GraphicIcon.KUAISHOU,
                GraphicIcon.YOUTUBE));
    }

    private static void assertIconTextures(List<GraphicIcon> icons) throws IOException {
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
                assertEnlargedCopy(path.replace("/display/", "/display_64/"), image);
            }
        }
    }

    private static void assertEnlargedCopy(String path, BufferedImage original) throws IOException {
        try (var stream = GraphicIcon.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            var copy = ImageIO.read(stream);
            assertNotNull(copy, path);
            assertEquals(64, copy.getWidth(), path);
            assertEquals(64, copy.getHeight(), path);
            for (int y = 0; y < copy.getHeight(); y++) {
                for (int x = 0; x < copy.getWidth(); x++) {
                    assertEquals(original.getRGB(x / 4, y / 4), copy.getRGB(x, y), path);
                }
            }
        }
    }
}
