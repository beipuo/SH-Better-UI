// Adapted by dishanhai from Link-Lyric (beipuo).
// Source: https://github.com/beipuo/Link-Lyric/tree/1a7cd274c3e7afbb33c08c66414ec0def944643b
// See META-INF/third-party/link-lyric for licenses and upstream notices.
package com.dishanhai.sh_better_ui.client.lyrics.common.i18n;

/**
 * 界面文案的语言键。
 *
 * <p>键名集中在此，调用方只引用常量，避免字符串散落在界面代码中。
 * 实际文案由各加载器的 {@code lang/*.json} 提供。
 */
public final class LangKeys {

    /** 配置界面标题。 */
    public static final String CONFIG_TITLE = "sh_better_ui.lyrics.config.title";
    public static final String FEATHER = "sh_better_ui.lyrics.config.feather";
    public static final String FEATHER_ON = "sh_better_ui.lyrics.config.feather.on";
    public static final String FEATHER_OFF = "sh_better_ui.lyrics.config.feather.off";
    /** 超长行折行开关。 */
    public static final String WRAP = "sh_better_ui.lyrics.config.wrap";
    public static final String TRANSITION = "sh_better_ui.lyrics.config.transition";
    public static final String SHADOW = "sh_better_ui.lyrics.config.shadow";
    public static final String SHADOW_ON = "sh_better_ui.lyrics.config.shadow.on";
    public static final String SHADOW_OFF = "sh_better_ui.lyrics.config.shadow.off";
    public static final String FONT_COLOR = "sh_better_ui.lyrics.config.fontColor";
    public static final String MASTER_SWITCH = "sh_better_ui.lyrics.config.master";
    public static final String LYRICS_ON = "sh_better_ui.lyrics.config.lyrics.on";
    public static final String LYRICS_OFF = "sh_better_ui.lyrics.config.lyrics.off";
    public static final String SOURCE_ENABLED = "sh_better_ui.lyrics.config.source.enabled";
    public static final String SOURCE_DISABLED = "sh_better_ui.lyrics.config.source.disabled";
    public static final String LINK_MC = "sh_better_ui.lyrics.link_mc.name";
    public static final String LINK_MC_HELP = "sh_better_ui.lyrics.link_mc.help";
    public static final String LINK_MC_COPY = "sh_better_ui.lyrics.link_mc.copy";
    public static final String LINK_MC_COPIED = "sh_better_ui.lyrics.link_mc.copied";
    public static final String LINK_MC_DISABLED = "sh_better_ui.lyrics.link_mc.disabled";
    public static final String LINK_MC_NO_WORLD = "sh_better_ui.lyrics.link_mc.no_world";
    public static final String LINK_MC_WAITING = "sh_better_ui.lyrics.link_mc.waiting";
    public static final String LINK_MC_NO_LYRICS = "sh_better_ui.lyrics.link_mc.no_lyrics";
    public static final String LINK_MC_SYNCED = "sh_better_ui.lyrics.link_mc.synced";
    public static final String ADJUST_POSITION = "sh_better_ui.lyrics.config.adjustPosition";
    public static final String RESET_DEFAULT = "sh_better_ui.lyrics.config.reset";
    public static final String DONE = "sh_better_ui.lyrics.config.done";
    public static final String CANCEL = "sh_better_ui.lyrics.config.cancel";
    public static final String SAVE = "sh_better_ui.lyrics.config.save";

    /** 配置界面的分组标题。 */
    public static final String GROUP_DISPLAY = "sh_better_ui.lyrics.config.group.display";
    public static final String GROUP_APPEARANCE = "sh_better_ui.lyrics.config.group.appearance";
    public static final String GROUP_LAYOUT = "sh_better_ui.lyrics.config.group.layout";
    public static final String GROUP_ACTION = "sh_better_ui.lyrics.config.group.action";

    /** 选项悬浮说明。此前是硬编码中文字面量，英文界面会露出中文。 */
    public static final String TIP_LINE_COUNT = "sh_better_ui.lyrics.tip.lineCount";
    public static final String TIP_LINE_GAP = "sh_better_ui.lyrics.tip.lineGap";
    public static final String TIP_OPACITY = "sh_better_ui.lyrics.tip.opacity";
    public static final String TIP_LINE_SCALE = "sh_better_ui.lyrics.tip.lineScale";
    public static final String TIP_MAX_WIDTH = "sh_better_ui.lyrics.tip.maxWidth";
    public static final String TIP_TRANSITION = "sh_better_ui.lyrics.tip.transition";
    public static final String TIP_OFFSET = "sh_better_ui.lyrics.tip.offset";
    public static final String TIP_WEIGHT = "sh_better_ui.lyrics.tip.weight";
    public static final String TIP_STROKE_PX = "sh_better_ui.lyrics.tip.strokePx";
    public static final String TIP_STROKE_ALPHA = "sh_better_ui.lyrics.tip.strokeAlpha";

    /**
     * 通用开关文案。
     *
     * <p>此前所有开关都借用 {@code SHADOW_ON/OFF}，于是「描边」开关的下标
     * 显示成「阴影：开」，「羽化」开关则直接用了 {@code TRANSITION}
     * （「切换」）作为标签——标签与所控制的项完全对不上。
     */
    public static final String GENERIC_ON = "sh_better_ui.lyrics.config.on";
    public static final String GENERIC_OFF = "sh_better_ui.lyrics.config.off";

    /** 描边预设色名。 */
    public static final String PRESET_BLACK = "sh_better_ui.lyrics.preset.black";
    public static final String PRESET_WHITE = "sh_better_ui.lyrics.preset.white";
    public static final String PRESET_DARK_GRAY = "sh_better_ui.lyrics.preset.darkGray";
    public static final String PRESET_DARK_BLUE = "sh_better_ui.lyrics.preset.darkBlue";
    public static final String PRESET_DARK_BROWN = "sh_better_ui.lyrics.preset.darkBrown";

    /** 颜色选择界面。 */
    public static final String COLOR_TITLE = "sh_better_ui.lyrics.color.title";
    public static final String COLOR_HUE = "sh_better_ui.lyrics.color.hue";
    public static final String COLOR_SATURATION = "sh_better_ui.lyrics.color.saturation";
    public static final String COLOR_VALUE = "sh_better_ui.lyrics.color.value";
    public static final String COLOR_PREVIEW = "sh_better_ui.lyrics.color.preview";
    public static final String COLOR_CODE = "sh_better_ui.lyrics.color.code";

    /** 调色板当前编辑的目标提示。 */
    public static final String COLOR_ADJUSTING = "sh_better_ui.lyrics.color.adjusting";

    /** 调色板的编辑目标。 */
    public static final String SLOT_STROKE = "sh_better_ui.lyrics.color.slot.stroke";
    public static final String SLOT_SUNG = "sh_better_ui.lyrics.color.slot.sung";
    public static final String SLOT_UNSUNG = "sh_better_ui.lyrics.color.slot.unsung";
    public static final String SLOT_TRANSLATION = "sh_better_ui.lyrics.color.slot.translation";
    public static final String SLOT_RESET = "sh_better_ui.lyrics.color.slot.reset";

    /** 位置调整界面。 */
    public static final String LOCATION_TITLE = "sh_better_ui.lyrics.location.title";
    public static final String LOCATION_HINT = "sh_better_ui.lyrics.location.hint";
    public static final String LOCATION_DRAG = "sh_better_ui.lyrics.location.drag";
    public static final String LOCATION_RESET = "sh_better_ui.lyrics.location.reset";

    /** 指令反馈。 */
    public static final String COMMAND_ON = "sh_better_ui.lyrics.command.on";
    public static final String COMMAND_OFF = "sh_better_ui.lyrics.command.off";
    public static final String COMMAND_SOURCE_ON = "sh_better_ui.lyrics.command.source.on";
    public static final String COMMAND_SOURCE_OFF = "sh_better_ui.lyrics.command.source.off";
    public static final String COMMAND_OFFSET = "sh_better_ui.lyrics.command.offset";
    public static final String COMMAND_RESET = "sh_better_ui.lyrics.command.reset";
    public static final String COMMAND_STATUS = "sh_better_ui.lyrics.command.status";
    public static final String COMMAND_OPEN_CONFIG = "sh_better_ui.lyrics.command.openConfig";

    /** 样式选项名称。 */
    public static final String OPTION_LINE_COUNT = "sh_better_ui.lyrics.option.lineCount";
    public static final String OPTION_MAX_WIDTH = "sh_better_ui.lyrics.option.maxWidth";
    public static final String OPTION_TRANSITION_MS = "sh_better_ui.lyrics.option.transitionMs";
    public static final String OPTION_OPACITY = "sh_better_ui.lyrics.option.opacity";
    public static final String OPTION_ALIGN = "sh_better_ui.lyrics.option.align";
    public static final String OPTION_WEIGHT = "sh_better_ui.lyrics.option.weight";

    /** 上浮曲线切换。 */
    public static final String SHADOW_SOFT = "sh_better_ui.lyrics.config.shadow.soft";

    /** 行间距。 */
    public static final String OPTION_LINE_GAP = "sh_better_ui.lyrics.config.line_gap";

    /**
     * 纵向锚点位置。
     *
     * <p>滑条上是百分比（0% 贴底、100% 贴顶），但配置文件与
     * {@code LyricStyleConfig.anchorRatio()} 里存的是 0..1 的浮点比例。
     * 两者之间只隔一次乘 100，界面上不写「比例 0.37」这种读不出来的数字。
     */
    public static final String OPTION_ANCHOR_RATIO = "sh_better_ui.lyrics.config.anchor_ratio";
    public static final String TIP_ANCHOR_RATIO = "sh_better_ui.lyrics.tip.anchorRatio";

    /**
     * 左右留白。
     *
     * <p>与行间距分开命名（{@code pad_x} 而不是复用 {@code line_gap}）：
     * 两者都是像素，但一个是纵向的一个是横向的，合成一个键后
     * 界面上会出现两个同名的滑条。
     */
    public static final String OPTION_PAD_X = "sh_better_ui.lyrics.config.pad_x";
    public static final String TIP_PAD_X = "sh_better_ui.lyrics.tip.padX";

    /** 像素单位。 */
    public static final String UNIT_PX = "sh_better_ui.lyrics.config.unit_px";

    /** 描边开关。 */
    public static final String STROKE = "sh_better_ui.lyrics.config.stroke";

    /** 描边宽度。 */
    public static final String OPTION_STROKE_PX = "sh_better_ui.lyrics.config.stroke_px";

    /** 描边不透明度。 */
    public static final String OPTION_STROKE_ALPHA = "sh_better_ui.lyrics.config.stroke_alpha";

    /** 描边颜色。 */
    public static final String STROKE_COLOR = "sh_better_ui.lyrics.config.stroke_color";

    /** 描边分组标题。 */
    public static final String GROUP_STROKE = "sh_better_ui.lyrics.config.group.stroke";

    /** 上浮曲线切换。 */
    public static final String LIFT_CURVE = "sh_better_ui.lyrics.config.lift_curve";
    public static final String LIFT_CURVE_LINEAR = "sh_better_ui.lyrics.config.lift_curve.linear";
    public static final String LIFT_CURVE_COSINE = "sh_better_ui.lyrics.config.lift_curve.cosine";
    public static final String OPTION_INTERLUDE = "sh_better_ui.lyrics.option.interlude";
    public static final String OPTION_OFFSET = "sh_better_ui.lyrics.option.offset";

    /** 选项显示格式：名称、数值、单位。 */
    public static final String OPTION_VALUE_FORMAT = "sh_better_ui.lyrics.option.value";

    public static final String OPTION_LINE_SCALE = "sh_better_ui.lyrics.option.lineScale";

    /** 单位。 */
    public static final String UNIT_MS = "sh_better_ui.lyrics.unit.ms";
    public static final String UNIT_TIMES = "sh_better_ui.lyrics.unit.times";
    public static final String UNIT_PERCENT = "sh_better_ui.lyrics.unit.percent";

    /** 颜色项名称。 */
    public static final String COLOR_TARGET_SUNG = "sh_better_ui.lyrics.color.target.sung";
    public static final String COLOR_TARGET_UNSUNG = "sh_better_ui.lyrics.color.target.unsung";
    public static final String COLOR_TARGET_TRANSLATION = "sh_better_ui.lyrics.color.target.translation";

    /** 间奏提示文本。 */
    public static final String INTERLUDE_DEFAULT = "sh_better_ui.lyrics.interlude.default";
    public static final String INTERLUDE_BLANK = "sh_better_ui.lyrics.interlude.blank";

    /** 样式枚举名称。 */
    // 只保留 REGULAR：Weight 枚举已裁到只剩常规一项，BOLD / HEAVY 两键
    // 从此没有任何 Java 引用，只是四份文案文件里的死字符串。留着它们
    // 会让人以为还存在可选字重，而渲染层早已不认这两个值。
    public static final String WEIGHT_REGULAR = "sh_better_ui.lyrics.weight.regular";
    public static final String ALIGN_LEFT = "sh_better_ui.lyrics.align.left";
    public static final String ALIGN_CENTER = "sh_better_ui.lyrics.align.center";
    public static final String ALIGN_RIGHT = "sh_better_ui.lyrics.align.right";

    /** 排布形态：界面上的选项名，以及三种形态各自的显示名。 */
    public static final String LAYOUT = "sh_better_ui.lyrics.config.layout";

    /** 排布形态的选项说明。单独一键，因为三种形态的取舍无法压进一个短名里。 */
    public static final String LAYOUT_TOOLTIP = "sh_better_ui.lyrics.config.layout.tooltip";
    public static final String LAYOUT_LINEAR = "sh_better_ui.lyrics.layout.linear";
    public static final String LAYOUT_ARC = "sh_better_ui.lyrics.layout.arc";
    public static final String LAYOUT_RING = "sh_better_ui.lyrics.layout.ring";

    /**
     * 中英混排校正。
     *
     * <p>三项分开：开关管「要不要」，两个滑条管「校多少」。
     * 不给合并成「轻/中/重」三档，理由见
     * {@code LyricStyleConfig.withMixed} 的注释——两个量在实机上
     * 可能需要反向调整，预设表达不了那一格。
     */
    public static final String MIXED_SCRIPT = "sh_better_ui.lyrics.config.mixed";
    public static final String MIXED_SCRIPT_TOOLTIP = "sh_better_ui.lyrics.config.mixed.tooltip";
    public static final String OPTION_MIXED_SCALE = "sh_better_ui.lyrics.config.mixed_scale";
    public static final String TIP_MIXED_SCALE = "sh_better_ui.lyrics.tip.mixedScale";
    public static final String OPTION_MIXED_BASELINE = "sh_better_ui.lyrics.config.mixed_baseline";
    public static final String TIP_MIXED_BASELINE = "sh_better_ui.lyrics.tip.mixedBaseline";

    private LangKeys() {
    }
}
