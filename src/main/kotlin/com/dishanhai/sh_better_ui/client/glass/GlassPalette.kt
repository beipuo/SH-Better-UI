package com.dishanhai.sh_better_ui.client.glass

/** 配方修改器的颜色 token（ARGB）。沿用编辑器原有的深蓝青配色，只补半透明玻璃底色。 */
object GlassPalette {
    // 文字与强调
    @JvmField val ACCENT = 0xFF69E8FF.toInt()
    @JvmField val TEXT = 0xFFEAF7FF.toInt()
    @JvmField val TEXT_MUTED = 0xFFB7C9D6.toInt()
    @JvmField val TEXT_DIM = 0xFF8AA6B9.toInt()
    @JvmField val TEXT_OFF = 0xFF6E8494.toInt()
    @JvmField val TEXT_HEAD = 0xFF9FD7FF.toInt()
    @JvmField val OK = 0xFF91F7BC.toInt()

    // 玻璃底板（半透明，能透出后面的世界）
    @JvmField val ROOT_FILL = 0xE60E1726.toInt()
    @JvmField val ROOT_EDGE = 0xFF36546A.toInt()
    // 下拉弹层的底，不透明：弹层本体在模糊 Hole 里不被糊，半透明会让后面的文字清楚地透出来
    @JvmField val PANEL_FILL = 0xFF101B2E.toInt()
    @JvmField val PANEL_EDGE = 0xFF2E4A60.toInt()
    @JvmField val DISPLAY_FILL = 0xE6091221.toInt()
    @JvmField val DISPLAY_EDGE = 0xFF1F3347.toInt()
    @JvmField val PAGE_FILL = 0xB30B1424.toInt()

    // 换页时盖住左栏 / 底部按钮的遮罩色（只取 RGB）：PAGE_FILL 叠在 ROOT_FILL 上、以及 ROOT_FILL 本身
    @JvmField val VEIL_PAGE = 0xFF0C1525.toInt()
    @JvmField val VEIL_ROOT = 0xFF0E1726.toInt()

    // 按钮
    @JvmField val BUTTON_FILL = 0xE6183049.toInt()
    @JvmField val BUTTON_FILL_HOVER = 0xE6214A6B.toInt()
    @JvmField val BUTTON_EDGE = 0xFF36546A.toInt()

    // 受光亮边
    @JvmField val LIT = 0x40BFE8FF.toInt()
    @JvmField val LIT_SOFT = 0x1ABFE8FF.toInt()

    // 卡片 / 行 / 页签 / 格子（不透明，保证图标与文字对比度）
    @JvmField val ROW_FILL = 0xFF111B2D.toInt()
    @JvmField val ROW_FILL_HOVER = 0xFF19324A.toInt()
    @JvmField val ROW_EDGE = 0xFF29445A.toInt()
    @JvmField val ROW_ICON_BG = 0xFF20374B.toInt()
    @JvmField val TAB_EDGE = 0xFF36546A.toInt()
    @JvmField val TAB_FILL = 0xFF16243A.toInt()
    @JvmField val TAB_FILL_ON = 0xFF1A3549.toInt()
    @JvmField val GROUP_FILL = 0xFF163044.toInt()
    @JvmField val CELL_FILL = 0xFF272A35.toInt()
}
