package com.dishanhai.sh_better_ui.client.glass;

import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects;
import com.dishanhai.sh_better_ui.client.gui.scaled.DarkModeTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.LiquidGlassTheme;
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import com.dishanhai.sh_better_ui.theme.ThemeSnapshot;
import com.dishanhai.sh_better_ui.theme.token.ThemeTokens;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 商店界面主题（山海署名，客户端）：只管「结构色」——外框、底板、盒子、格子、按钮、滚动条轨道。
 * 文字色（金/青/绿/红这类语义色）不随主题变，买卖成败的提示色在两套主题下含义一致。
 *
 * <p>{@link #CLASSIC} 是原来的 KE 金框配色，数值与改动前逐个相同；{@link #GLASS} 沿用山海样板生成器的
 * 动态玻璃 token（{@link GlassPalette}）：半透明分层底板 + 左上受光亮边 + 根板外缘柔影，
 * hover/按下的弹簧动效两套主题共用。由调用方选择主题，库不读取任何业务配置。</p>
 */
public class GlassTheme {

    /** 山海：與星律樣板總成共用的中性煙灰材質；根板取樣一次，內層只疊薄灰。 */
    public static final GlassTheme SMOKE = new GlassTheme(GlassColors.SMOKE);

    /** 共用玻璃绘制，同时各自读取对应的主题材质覆写。 */
    public static final GlassTheme SLATE = new GlassTheme("slate", DarkModeTheme.ACCENT,
            DarkModeTheme.BORDER, DarkModeTheme.BACKGROUND, DarkModeTheme.SURFACE,
            0xFF182437, 0xFF293B54, DarkModeTheme.SURFACE, 0xFF334B6B);
    public static final GlassTheme LIQUID_GLASS = new GlassTheme("liquidGlass", LiquidGlassTheme.ACCENT,
            0xA6CBD4E3, 0xE6252B34, 0x40252B34, 0x80252B34, 0xB83A4655, 0xA6252B34, 0xCC39495D);
    public static final GlassTheme NETWORK = new GlassTheme("network", 0xFF86C5F4,
            0xFF316E9B, 0xCC090E16, 0x50111720, 0xA0111720, 0xD01B3549, 0xD0111720, 0xE5234C68);

    /** 山海：染色玻璃沿用 clearGlass 参数协议，可被所有消费端独立复用。 */
    public static final GlassTheme AURORA = new GlassTheme(GlassColors.tintedDark(0x80E3BD, 0x0C2524));
    public static final GlassTheme AMETHYST = new GlassTheme(GlassColors.tintedDark(0xC6ACFF, 0x22172F));
    public static final GlassTheme ROSE = new GlassTheme(GlassColors.tintedDark(0xF5B3CD, 0x301C29));
    public static final GlassTheme EMBER = new GlassTheme(GlassColors.tintedDark(0xFFCB8C, 0x2D2118));

    /** 原 KE 金框配色。 */
    public static final GlassTheme CLASSIC = new GlassTheme(false,
            -22016, -7710208, -267382768, -266724838, -300674028, -300476649, -299555547, -299421158,
            -14935012, -12303292, -15967888, -14935012, -13661393);

    /**
     * 动态玻璃：色相沿用样板生成器（{@link GlassPalette}），但底板改成低不透明度——面板后面先做实时高斯
     * （{@link #backdrop}），底板只负责压一层冷色调，世界的色块能透出来。卡片与按钮略实一些保证图标和字的对比度。
     */
    public static final GlassTheme GLASS = new GlassTheme(true,
            GlassPalette.ACCENT, GlassPalette.BUTTON_EDGE,
            0x590E1726, // 根板 35%
            0x26091221, // 内层显示区 15%
            0x260B1424, // 网格/盒子底 15%
            0x99111B2D, // 商品卡 60%
            0xB319324A, // 卡片悬停 70%
            0xCC1A3549, // 选中卡 80%
            0xA6183049, // 按钮 65%
            0xD9214A6B, // 按钮悬停 85%
            GlassPalette.ROW_EDGE,
            0x801F3347, // 数字栏底 / 滚动条轨道 50%
            GlassPalette.ROW_EDGE);

    public final boolean glass;
    public final GlassColors colors;
    private final String materialGroup;
    /** 亮框：选中格、选中页签、按钮 hover 边、滚动条把手。经典=金，玻璃=青。 */
    public final int frame;
    /** 暗框：面板外框、盒子边、按钮常态边。 */
    public final int frameDark;
    public final int panelBg;
    public final int panelInner;
    public final int boxBg;
    public final int rowBg;
    public final int rowHover;
    public final int selectBg;
    public final int buttonBg;
    public final int buttonHover;
    public final int barBorder;
    /** 数字栏底 / 滚动条轨道。 */
    public final int barBg;
    /** 商品格常态边（经典=暗绿）。 */
    public final int cellEdge;

    private GlassTheme(boolean glass, int frame, int frameDark, int panelBg, int panelInner, int boxBg,
                      int rowBg, int rowHover, int selectBg, int buttonBg, int buttonHover,
                      int barBorder, int barBg, int cellEdge) {
        this(glass, GlassColors.DARK, frame, frameDark, panelBg, panelInner, boxBg,
                rowBg, rowHover, selectBg, buttonBg, buttonHover, barBorder, barBg, cellEdge);
    }

    private GlassTheme(GlassColors colors) {
        this(colors, colors == GlassColors.SMOKE ? "smokeGlass" : "clearGlass");
    }

    private GlassTheme(GlassColors colors, String materialGroup) {
        this(true, colors, colors.ACCENT, colors.BUTTON_EDGE, colors.PANEL_FILL,
                colors.DISPLAY_FILL, colors.DISPLAY_FILL, colors.ROW_FILL, colors.ROW_FILL_HOVER,
                colors.ROW_FILL_HOVER, colors.BUTTON_FILL, colors.BUTTON_FILL_HOVER,
                colors.ROW_EDGE, colors.DISPLAY_FILL, colors.ROW_EDGE, materialGroup);
    }

    /** Projects the shared semantic palette onto the glass controls used by host screens. */
    public static GlassTheme fromSnapshot(ThemeSnapshot theme) {
        var colors = GlassColors.fromSnapshot(theme);
        return new GlassTheme(true, colors, colors.ACCENT, colors.BUTTON_EDGE, colors.PANEL_FILL,
                colors.DISPLAY_FILL, colors.DISPLAY_FILL, colors.ROW_FILL, colors.ROW_FILL_HOVER,
                theme.resolve(ThemeTokens.SELECTED).argb(), colors.BUTTON_FILL, colors.BUTTON_FILL_HOVER,
                colors.ROW_EDGE, colors.DISPLAY_FILL, colors.ROW_EDGE, "clearGlass");
    }

    private GlassTheme(String group, int accent, int edge, int panel, int inner, int row, int hover,
                       int button, int buttonHover) {
        this(true, GlassColors.DARK, accent, edge, panel, inner, inner, row, hover, hover,
                button, buttonHover, edge, inner, edge, group);
    }

    private GlassTheme(boolean glass, GlassColors colors, int frame, int frameDark, int panelBg, int panelInner, int boxBg,
                       int rowBg, int rowHover, int selectBg, int buttonBg, int buttonHover,
                       int barBorder, int barBg, int cellEdge) {
        this(glass, colors, frame, frameDark, panelBg, panelInner, boxBg, rowBg, rowHover, selectBg,
                buttonBg, buttonHover, barBorder, barBg, cellEdge,
                !glass ? "classic" : colors == GlassColors.SMOKE ? "smokeGlass" : "clearGlass");
    }

    private GlassTheme(boolean glass, GlassColors colors, int frame, int frameDark, int panelBg, int panelInner, int boxBg,
                       int rowBg, int rowHover, int selectBg, int buttonBg, int buttonHover,
                       int barBorder, int barBg, int cellEdge, String materialGroup) {
        this.glass = glass;
        this.colors = colors;
        this.materialGroup = materialGroup;
        this.frame = frame;
        this.frameDark = frameDark;
        this.panelBg = panelBg;
        this.panelInner = panelInner;
        this.boxBg = boxBg;
        this.rowBg = rowBg;
        this.rowHover = rowHover;
        this.selectBg = selectBg;
        this.buttonBg = buttonBg;
        this.buttonHover = buttonHover;
        this.barBorder = barBorder;
        this.barBg = barBg;
        this.cellEdge = cellEdge;
    }

    /** 山海：供消费端保留原有主题类型与选择入口，共用同一套绘制。 */
    protected GlassTheme(GlassTheme source) {
        this(source.glass, source.colors, source.frame, source.frameDark, source.panelBg, source.panelInner,
                source.boxBg, source.rowBg, source.rowHover, source.selectBg, source.buttonBg,
                source.buttonHover, source.barBorder, source.barBg, source.cellEdge, source.materialGroup);
    }

    public String materialGroup() { return materialGroup; }

    public UiStyle controlStyle() {
        return switch (materialGroup) {
            case "liquidGlass" -> UiStyle.LIQUID_GLASS;
            case "network" -> UiStyle.NETWORK;
            default -> UiStyle.DEFAULT;
        };
    }

    private boolean modern() {
        return materialGroup.equals("slate") || materialGroup.equals("liquidGlass") || materialGroup.equals("network");
    }

    /**
     * 带边框的盒子。玻璃主题下四条边各自画、内芯半透明（内芯下面是背后的世界而不是边框色），
     * 再补左上受光亮边，同 {@code GlassPanelTexture} 的 plate。
     */
    private ThemeEffects.Material material() {
        return ThemeEffects.material(materialGroup);
    }

    public void box(GuiGraphics g, int x, int y, int w, int h, int border, int fill) {
        if (w <= 0 || h <= 0) return;
        var preferences = UiPreferences.configured();
        if (materialGroup.equals("liquidGlass")) {
            LiquidGlassTheme.plate(g, x, y, w, h, border, fill, preferences);
            return;
        }
        var material = material();
        border = material.edge(border);
        fill = material.fill(fill);
        if (preferences.highContrast()) {
            border = 0xFFFFFFFF;
            fill = 0xFF0F172A;
        }
        if (modern()) {
            RoundedCorners.draw(g, x, y, w, h, Math.min(4f, Math.min(w, h) / 2f),
                    preferences.highContrast() ? 2f : 1f, border, fill);
            return;
        }
        if (!glass) {
            g.fill(x, y, x + w, y + h, border);
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
            return;
        }
        if (w < 2 || h < 2) {
            g.fill(x, y, x + w, y + h, border);
            return;
        }
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y + 1, x + 1, y + h - 1, border);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        if (!preferences.highContrast() && w > 4 && h > 4) {
            g.fill(x + 1, y + 1, x + w - 1, y + 2, colors.LIT);
            g.fill(x + 1, y + 2, x + 2, y + h - 1,
                    colors == GlassColors.SMOKE ? colors.LIT : GlassPalette.LIT_SOFT);
        }
    }

    // ===== 按钮/页签文字：玻璃主题下改用生成器色板，去掉 § 色码与文字阴影 =====

    /** 玻璃底上的语义色（MC 的 16 色在深蓝玻璃上不是太暗就是太艳，这里换成同色相的浅色）。 */
    private static final int GLASS_RED = 0xFFFF9A9A;
    private static final int GLASS_GOLD = 0xFFFFD98A;
    private static final int GLASS_PURPLE = 0xFFE2B3FF;

    /** 按钮/页签上显示的文字：经典原样；玻璃去掉 § 格式码（颜色改由 {@link #ink} 给）。 */
    public String label(String text) {
        if (!glass || text == null || text.indexOf('§') < 0) return text;
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) { i++; continue; }
            sb.append(c);
        }
        return sb.toString();
    }

    /** 按片段适配原生文本颜色，保留修饰、参数展开结果和原 Component。 */
    public net.minecraft.network.chat.Component label(net.minecraft.network.chat.Component text, int fallback) {
        if (!glass) return text;
        var result = net.minecraft.network.chat.Component.empty();
        text.visit((style, fragment) -> {
            int color = ink(null, fallback);
            if (style.getColor() != null) {
                int rgb = style.getColor().getValue();
                boolean mapped = false;
                for (net.minecraft.ChatFormatting format : net.minecraft.ChatFormatting.values()) {
                    if (format.getColor() != null && format.getColor() == rgb) {
                        color = ink("§" + format.getChar(), fallback);
                        mapped = true;
                        break;
                    }
                }
                if (!mapped && !UiPreferences.configured().highContrast()) color = 0xFF000000 | rgb;
            }
            result.append(net.minecraft.network.chat.Component.literal(fragment).setStyle(style.withColor(color & 0xFFFFFF)));
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return result;
    }

    /**
     * 按钮文字色：经典返回 fallback（§ 色码自己生效）；玻璃按标签第一个色码换成玻璃色板里的对应色，
     * 「关」态常用的 §8/§7 映射成仍清晰可读的灰蓝，不再是深底上的深灰。
     */
    public int ink(String text, int fallback) {
        if (!glass) return material().text(fallback);
        char code = 'f';
        if (text != null) {
            for (int i = 0; i + 1 < text.length(); i++) {
                if (text.charAt(i) != '§') continue;
                char c = Character.toLowerCase(text.charAt(i + 1));
                if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) { code = c; break; }
            }
        }
        var material = material();
        if (ThemeEffects.settings().highContrast() && (code == '0' || code == '8' || code == '7' || code == 'f')) {
            return 0xFFFFFFFF;
        }
        return switch (code) {
            case '0', '8' -> material.text(colors.TEXT_DIM);
            case '7' -> material.text(colors.TEXT_MUTED);
            case 'a', '2' -> colors.OK;
            case 'b', '3', '9', '1' -> material.accent(modern() ? frame : colors.ACCENT);
            case 'c', '4' -> GLASS_RED;
            case 'e', '6' -> GLASS_GOLD;
            case 'd', '5' -> GLASS_PURPLE;
            default -> material.text(colors.TEXT);
        };
    }

    /** 页签/普通文字的常态、悬停、选中色（玻璃主题；经典调用方仍用自己的灰/白/金）。 */
    public int tabIdle() { return material().text(colors.TEXT_MUTED); }
    public int tabHover() { return material().text(colors.TEXT); }
    public int tabActive() { return material().accent(colors.ACCENT); }

    /** 居中画按钮文字：经典带阴影（原样）；玻璃无阴影，同样板生成器。 */
    public void drawCentered(GuiGraphics g, net.minecraft.client.gui.Font font, String text, int cx, int y, int color) {
        if (!glass) {
            g.drawCenteredString(font, text, cx, y, color);
            return;
        }
        int x = cx - font.width(text) / 2;
        if (colors == GlassColors.SMOKE) GlassSurface.drawString(g, font, text, x, y, color);
        else g.drawString(font, text, x, y, color, false);
    }

    /** 原版界面背景那层 75%~80% 的黑幕会把玻璃盖成实心；玻璃主题换成很淡的一层冷色。返回 false 表示调用方照旧画原版背景。 */
    public boolean renderBackdropDim(GuiGraphics g, int width, int height) {
        if (!glass) return false;
        if (colors == GlassColors.SMOKE) g.fillGradient(0, 0, width, height, 0x30202020, 0x50202020);
        else g.fillGradient(0, 0, width, height, 0x30101828, 0x50101828);
        return true;
    }

    /** 毛玻璃：面板画上去之前，把 (x,y,w,h)（当前 pose 下的 GUI 坐标）后面的画面实时糊掉。经典主题不做。 */
    public void backdrop(GuiGraphics g, int x, int y, int w, int h) {
        backdrop(g, x, y, w, h, 1f);
    }

    /** 同上，strength 0..1：二级界面开关时跟着弹簧淡入淡出。 */
    public void backdrop(GuiGraphics g, int x, int y, int w, int h, float strength) {
        if (!glass || !UiPreferences.configured().useBackdropBlur() || materialGroup.equals("slate")
                || materialGroup.equals("network")) return;
        if (colors == GlassColors.SMOKE) {
            GlassBlur.backdrop(g, new GuiRect(x, y, w, h), strength,
                    com.dishanhai.sh_better_ui.client.blur.GlassStyle.FROSTED,
                    GlassSurface.SMOKE_BLUR_RADIUS);
            return;
        }
        com.dishanhai.sh_better_ui.client.glass.GlassBlur.backdrop(g,
                new com.dishanhai.sh_better_ui.client.glass.GuiRect(x, y, w, h), strength);
    }

    /** 区域分隔的模糊半径（虚拟 GUI 单位）：和根板同量级，区域里只剩色块。 */
    private static final float REGION_BLUR_RADIUS = 9f;
    /** 按钮/页签的模糊半径：按钮小，半径过大整块只剩一个平均色，6 刚好糊掉背后的字。 */
    private static final float CONTROL_BLUR_RADIUS = 6f;

    /**
     * 区域分隔（余额栏、网格区、详情区）的毛玻璃：在根板之上再糊一层并压暗去色，必须在区域底盒之前调。
     * 采样夹在区域内，不会吸进相邻区域的边框和字。经典主题不做。
     */
    public void regionBackdrop(GuiGraphics g, int x, int y, int w, int h) {
        if (!glass || colors == GlassColors.SMOKE || modern() || !UiPreferences.configured().useBackdropBlur()) return;
        com.dishanhai.sh_better_ui.client.glass.GlassBlur.backdrop(g,
                new com.dishanhai.sh_better_ui.client.glass.GuiRect(x, y, w, h), 1f,
                com.dishanhai.sh_better_ui.client.blur.GlassStyle.REGION, REGION_BLUR_RADIUS);
    }

    /** 同上，strength 0..1：飞行卡片这类随动画淡入淡出的元素用。 */
    public void controlBackdrop(GuiGraphics g, int x, int y, int w, int h, float strength) {
        if (!glass || colors == GlassColors.SMOKE || modern() || !UiPreferences.configured().useBackdropBlur()) return;
        com.dishanhai.sh_better_ui.client.glass.GlassBlur.backdrop(g,
                new com.dishanhai.sh_better_ui.client.glass.GuiRect(x, y, w, h), strength,
                com.dishanhai.sh_better_ui.client.blur.GlassStyle.CONTROL, CONTROL_BLUR_RADIUS);
    }

    /** 玻璃 tooltip 底：毛玻璃 + 高不透明度深蓝底 + 青色细边，文字压在上面要读得清（底比按钮更实）。 */
    public void tooltipPanel(GuiGraphics g, int x, int y, int w, int h) {
        if (!UiPreferences.configured().useBackdropBlur()) {
            box(g, x, y, w, h, frameDark, panelBg | 0xFF000000);
            return;
        }
        if (modern()) {
            backdrop(g, x, y, w, h);
            box(g, x, y, w, h, frameDark, panelBg | 0xE6000000);
            return;
        }
        if (colors == GlassColors.SMOKE) {
            GlassSurface.smokePanel(g, x, y, w, h);
            return;
        }
        controlBackdrop(g, x, y, w, h);
        box(g, x, y, w, h, colors.BUTTON_EDGE,
                colors == GlassColors.DARK ? 0xE60A1322 : 0xE6000000 | panelBg & 0xFFFFFF);
    }

    /** 按钮/页签底的毛玻璃：比区域压得更深，保证按钮字的对比度。在按钮底盒之前、按钮动效的 pose 里调。 */
    public void controlBackdrop(GuiGraphics g, int x, int y, int w, int h) {
        if (!glass || colors == GlassColors.SMOKE || modern() || !UiPreferences.configured().useBackdropBlur()) return;
        com.dishanhai.sh_better_ui.client.glass.GlassBlur.backdrop(g,
                new com.dishanhai.sh_better_ui.client.glass.GuiRect(x, y, w, h), 1f,
                com.dishanhai.sh_better_ui.client.blur.GlassStyle.CONTROL, CONTROL_BLUR_RADIUS);
    }

    // ===== 子页面（货币中心 / 兑换中心 / 银会中心 / 确认弹窗）共用的三件套 =====

    /**
     * 子页面根板 + 内层显示区（内层给绝对坐标）。经典：原 KE 四层嵌套（暗金→金→暗底→内层），数值同改动前；
     * 玻璃：外缘柔影 → 毛玻璃底 → 低不透明度根板 → 内层显示区，同商店主界面。
     */
    public void rootPanel(GuiGraphics g, int x, int y, int w, int h, int ix, int iy, int iw, int ih) {
        rootPanel(g, x, y, w, h, ix, iy, iw, ih, frameDark, frame, colors.ROOT_EDGE);
    }

    /** 同上，自选外框（警告弹窗的红框）：经典用 outer/line 两层，玻璃用 glassEdge 当根板边。 */
    public void rootPanel(GuiGraphics g, int x, int y, int w, int h, int ix, int iy, int iw, int ih,
                          int outer, int line, int glassEdge) {
        if (glass) {
            rootShadow(g, x, y, w, h);
            backdrop(g, x, y, w, h);
            box(g, x, y, w, h, glassEdge, panelBg);
            box(g, ix, iy, iw, ih, colors.DISPLAY_EDGE, panelInner);
            return;
        }
        var material = material();
        g.fill(x, y, x + w, y + h, material.edge(outer));
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, material.accent(line));
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, material.fill(panelBg));
        g.fill(ix, iy, ix + iw, iy + ih, material.fill(panelInner));
    }

    /** 区域底（列表区、详情区）。经典：原来的纯色块；玻璃：区域毛玻璃 + 细边 + 半透明底，分区一眼可见。 */
    public void region(GuiGraphics g, int x, int y, int w, int h) {
        region(g, x, y, w, h, boxBg);
    }

    /** 同上，自选底色（如会员档位行的悬停 {@link #rowBg}）。 */
    public void region(GuiGraphics g, int x, int y, int w, int h, int fill) {
        if (!glass) {
            g.fill(x, y, x + w, y + h, material().fill(fill));
            return;
        }
        regionBackdrop(g, x, y, w, h);
        box(g, x, y, w, h, colors.ROW_EDGE, fill);
    }

    /**
     * 子页面按钮：先糊按钮背后（玻璃），再画底盒与居中文字。border/fill 由调用方按自己的 hover 规则算
     * （传本主题的 frameDark/frame/buttonBg/buttonHover），经典主题下与改动前逐像素一致。
     */
    public void button(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, int w, int h,
                       String text, int border, int fill, int textColor) {
        controlBackdrop(g, x, y, w, h);
        box(g, x, y, w, h, border, fill);
        drawCentered(g, font, label(text), x + w / 2, y + (h - 8) / 2, ink(text, textColor));
    }

    /** 本地化原生控件入口，保留 Component 中的参数、样式与语义色。 */
    public void button(GuiGraphics g, net.minecraft.client.gui.Font font, int x, int y, int w, int h,
                       net.minecraft.network.chat.Component text, int border, int fill, int textColor) {
        controlBackdrop(g, x, y, w, h);
        box(g, x, y, w, h, border, fill);
        var label = label(text, textColor);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, textColor, !glass);
    }

    /** 根板外缘三圈渐淡的下偏柔影（玻璃主题才画）。 */
    public void rootShadow(GuiGraphics g, int x, int y, int w, int h) {
        if (!glass || UiPreferences.configured().highContrast()) return;
        for (int i = 1; i <= 3; i++) {
            int alpha = 0x30 >> (i - 1);
            g.fill(x - i, y - i + 2, x + w + i, y + h + i + 2, alpha << 24);
        }
    }

    /** 自绘宿主的可见键盘焦点，使用与当前材质一致的边框和高对比宽度。 */
    public void focus(GuiGraphics g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        boolean contrast = UiPreferences.configured().highContrast();
        RoundedCorners.draw(g, x, y, w, h, modern() ? Math.min(4f, Math.min(w, h) / 2f) : 0,
                contrast ? 2f : 1f, contrast ? 0xFFBCDFFF : material().accent(0xFF8AAFCB), 0);
    }
}
