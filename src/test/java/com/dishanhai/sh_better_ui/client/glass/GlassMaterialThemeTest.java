package com.dishanhai.sh_better_ui.client.glass;

import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects;
import com.dishanhai.sh_better_ui.config.ClientUiSettings;
import com.dishanhai.sh_better_ui.config.ThemeOverrides;
import com.dishanhai.sh_better_ui.config.ThemeSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 山海：验证消费端复制主题后仍使用实际材质分组和可读的语义色。 */
class GlassMaterialThemeTest {
    @Test
    void smokeTooltipWithoutBlurDoesNotRequireARenderingClient() throws Exception {
        class RecordingGraphics extends net.minecraft.client.gui.GuiGraphics {
            java.util.List<Integer> fills;
            RecordingGraphics() { super(null, null); }
            @Override public void fill(int x1, int y1, int x2, int y2, int color) { fills.add(color); }
        }
        // 跳过 GuiGraphics 的 ItemStack 字段初始化；测试只记录矩形，不启动 Forge 注册表或 GPU。
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var graphics = (RecordingGraphics) ((sun.misc.Unsafe) field.get(null)).allocateInstance(RecordingGraphics.class);
        graphics.fills = new java.util.ArrayList<>();
        ThemeEffects.preview(ClientUiSettings.DEFAULT.withDisplay(true, false, false), () -> {
            assertDoesNotThrow(() -> GlassTheme.SMOKE.tooltipPanel(graphics, 0, 0, 40, 20));
            assertTrue(graphics.fills.stream().anyMatch(color -> color == 0xFF0F172A));
        });
    }

    @Test
    void componentLabelsKeepDecorationsAndUseReadableGlassSemanticColors() throws Exception {
        var input = net.minecraft.network.chat.Component.literal("disabled")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY, net.minecraft.ChatFormatting.BOLD);
        var method = GlassTheme.class.getMethod("label", net.minecraft.network.chat.Component.class, int.class);
        ThemeEffects.preview(ClientUiSettings.DEFAULT.withDisplay(true, false, true), () -> {
            try {
                var result = (net.minecraft.network.chat.Component) method.invoke(GlassTheme.LIQUID_GLASS, input, -1);
                result.visit((style, text) -> {
                    assertTrue(style.isBold());
                    assertEquals(0xFFFFFF, style.getColor().getValue());
                    return java.util.Optional.empty();
                }, net.minecraft.network.chat.Style.EMPTY);
                assertEquals(net.minecraft.ChatFormatting.DARK_GRAY.getColor(), input.getStyle().getColor().getValue());
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        });
    }

    @Test
    void modernMaterialCopiesKeepTheirOwnOverrides() throws Exception {
        for (String group : new String[]{"slate", "liquidGlass", "network"}) {
            GlassTheme original = (GlassTheme) GlassTheme.class.getField(switch (group) {
                case "liquidGlass" -> "LIQUID_GLASS";
                default -> group.toUpperCase(java.util.Locale.ROOT);
            }).get(null);
            GlassTheme copy = new GlassTheme(original);
            var custom = ThemeSettings.DEFAULT.withMode(ThemeSettings.Mode.CUSTOM).withEffects(
                    new ThemeOverrides("{\"" + group + "\":{\"text\":\"#DDEEFF\",\"accent\":\"#AABBCC\"}}"));
            ThemeEffects.preview(ClientUiSettings.DEFAULT.withTheme(custom), () -> {
                assertEquals(0xFFDDEEFF, copy.ink("normal", 0));
                assertEquals(0xFFAABBCC, copy.ink("§bselected", 0));
                assertNotEquals(copy.ink("normal", 0), copy.ink("§cfailure", 0));
                assertNotEquals(copy.ink("normal", 0), copy.ink("§asuccess", 0));
                assertEquals("selected", copy.label("§bselected"));
            });
        }
    }

    @Test
    void highContrastGlassUsesReadableTextWithoutErasingSuccessOrFailure() {
        var settings = ClientUiSettings.DEFAULT.withDisplay(true, false, true);
        ThemeEffects.preview(settings, () -> {
            for (GlassTheme theme : new GlassTheme[]{GlassTheme.GLASS, GlassTheme.SMOKE}) {
                assertEquals(0xFFFFFFFF, theme.ink("normal", 0));
                assertEquals(0xFFFFFFFF, theme.ink("§8disabled", 0));
                assertNotEquals(theme.ink("§asuccess", 0), theme.ink("§cfailure", 0));
            }
        });
    }
}
