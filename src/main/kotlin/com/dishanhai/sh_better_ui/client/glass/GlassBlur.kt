package com.dishanhai.sh_better_ui.client.glass

import com.dishanhai.sh_better_ui.client.gui.scaled.ThemeEffects

import com.dishanhai.sh_better_ui.client.blur.GaussBlur
import com.dishanhai.sh_better_ui.client.blur.GlassStyle
import com.dishanhai.sh_better_ui.client.gui.scaled.RoundedCorners
import com.dishanhai.sh_better_ui.config.GlassBackdropSettings
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

/**
 * 配方修改器接商店模糊的门面。
 *
 * 模糊本体仍是 [GaussBlur]（同一条 PostChain、同一份着色器、同一个 Post 事件），
 * 这里只做四件事：登记放行谓词、预热后处理、把 LDLib 部件的 GUI 坐标换成 framebuffer 像素、界面关闭时收掉残留请求。
 * 所有 `request*` 只能在客户端渲染线程里调（LDLib 的 drawInBackground / drawInForeground 本来就只在客户端跑）。
 */
object GlassBlur {

    /** 编辑器打开后的首帧预热，避免第一次换页才编译着色器造成卡顿。 */
    @JvmStatic
    fun warmUp() {
        GaussBlur.warmUp()
    }

    /**
     * 换页期间每帧调用。[viewport] 是页面的静止区域（不随 slide 平移），
     * 强度 / 半径 / 方向来自 [GlassMotion]。后处理资源不可用时静默跳过，调用方退回纯平移。
     */
    @JvmStatic
    fun requestPage(graphics: GuiGraphics, viewport: GuiRect, sample: GlassMotion.Sample) {
        if (!sample.isActive || viewport.isEmpty || !GaussBlur.isReady()) return
        val view = capture(graphics) ?: return
        val px = view.toPixels(viewport)
        if (px.isEmpty) return
        GaussBlur.requestDirectional(
            px.x, px.y, px.w, px.h,
            sample.strength, sample.radiusVirtual * view.pixelScale,
            sample.axisX, sample.axisY,
            GlassStyle.SWAP,
        )
    }

    /** 山海：页面画完后即时施加速度模糊，保持当前裁切，让随后绘制的工具栏和提示清晰。 */
    @JvmStatic
    fun pageSlideNow(graphics: GuiGraphics, viewport: GuiRect, sample: GlassMotion.Sample): Boolean {
        if (!sample.isActive || viewport.isEmpty) return false
        val view = capture(graphics) ?: return false
        val px = view.toPixels(viewport)
        val radius = sample.radiusVirtual * view.pixelScale * minOf(view.scaleX, view.scaleY)
        return GaussBlur.directionalNow(graphics, px.x, px.y, px.w, px.h, sample.strength, radius,
            sample.axisX, sample.axisY)
    }

    private const val POPUP_STRENGTH = 0.85f
    // 外圈要宽到盖住 13 点核的有效半径（约 4×BLUR_RADIUS_VIRTUAL），否则外缘处的取样会全夹在矩形边上拖出竖纹。
    private const val POPUP_MARGIN_VIRTUAL = 24f
    // 软边宽度略小于外扩量：Hole 外缘处淡出系数恰为 1，外缘处严格等于原图，没有一圈生硬的矩形边。
    private const val POPUP_FEATHER_VIRTUAL = 20f
    private const val MIN_STRENGTH = 0.01f

    /**
     * 下拉 / 历史弹层打开时：[popup]（弹层本体，调用方可并上锚点按钮）保持清晰并带柔影（Hole），
     * 周围一圈被糊开（Rect = [popup] 外扩 [POPUP_MARGIN_VIRTUAL]），外缘向外淡出到原图、取样绕开弹层本体。
     * [openAmount] 是 0..1 的打开量。
     * 走第二矩形槽位；`GaussBlur.request*` 会清掉第二槽位，所以必须在同一帧的换页主请求之后登记
     * （Panel.drawInBackground 先 requestSlideBlur 再 super.drawInBackground，弹层在后面的遍历里登记）。
     */
    @JvmStatic
    fun requestPopup(graphics: GuiGraphics, popup: GuiRect, openAmount: Float) {
        val strength = openAmount.coerceIn(0f, 1f) * POPUP_STRENGTH
        if (strength <= MIN_STRENGTH || popup.isEmpty || !GaussBlur.isReady()) return
        val view = capture(graphics) ?: return
        val hole = view.toPixels(popup)
        val ring = view.toPixels(GlassGeometry.expand(popup, POPUP_MARGIN_VIRTUAL))
        if (hole.isEmpty || ring.isEmpty) return
        GaussBlur.requestExtra(
            ring.x, ring.y, ring.w, ring.h,
            strength, GaussBlur.BLUR_RADIUS_VIRTUAL * view.pixelScale,
            hole.x, hole.y, hole.w, hole.h,
            GlassStyle.POPUP,
            POPUP_FEATHER_VIRTUAL * view.pixelScale,
        )
    }

    // 毛玻璃底的模糊半径（虚拟 GUI 单位）：比换页的方向模糊更大，背景要糊到看不清细节只剩色块
    private const val BACKDROP_RADIUS_VIRTUAL = 9f

    /**
     * 毛玻璃底：立刻把 [rect] 后面已经画好的世界/背景糊掉（见 [GaussBlur.blurNow]），
     * 必须在面板本体画之前调用。商店玻璃主题每帧用一次。
     */
    @JvmStatic
    fun backdrop(graphics: GuiGraphics, rect: GuiRect) = backdrop(graphics, rect, 1f)

    /** 同上，[strength] 0..1 给弹层开关时随弹簧淡入淡出。 */
    @JvmStatic
    fun backdrop(graphics: GuiGraphics, rect: GuiRect, strength: Float) =
        backdrop(graphics, rect, strength, GlassStyle.BACKDROP, BACKDROP_RADIUS_VIRTUAL)

    /** 同上，自选玻璃样式与半径（虚拟 GUI 单位）：按钮/区域在根板毛玻璃之上再压一层深色调做对比度。 */
    @JvmStatic
    fun backdrop(graphics: GuiGraphics, rect: GuiRect, strength: Float, style: GlassStyle, radiusVirtual: Float) {
        if (rect.isEmpty || strength <= MIN_STRENGTH || !GaussBlur.isReady()) return
        val view = capture(graphics) ?: return
        val px = view.toPixels(rect)
        if (px.isEmpty) return
        GaussBlur.blurNow(graphics, px.x, px.y, px.w, px.h, strength.coerceIn(0f, 1f),
            radiusVirtual * view.pixelScale, style)
    }

    /** 山海：即时绘制圆角背景，返回是否成功完成取样。 */
    @JvmStatic
    fun roundedBackdrop(graphics: GuiGraphics, rect: GuiRect, style: GlassStyle,
                        radiusVirtual: Float, cornerRadius: Int): Boolean =
        roundedBackdrop(graphics, rect, style, radiusVirtual, cornerRadius.toFloat())

    @JvmStatic
    fun roundedBackdrop(graphics: GuiGraphics, rect: GuiRect, style: GlassStyle,
                        radiusVirtual: Float, cornerRadius: Float): Boolean {
        if (!(cornerRadius > 0f)) return false
        return sampleBackdrop(graphics, rect, 1f, style, radiusVirtual, cornerRadius, legacyRadius = true)
    }

    /**
     * 山海：在绘制前景之前取样当前主画面。坐标、半径与圆角使用当前 pose 下的 GUI 单位。
     * 支持正向轴对齐缩放、平移和当前裁切。零半径保留材质处理，零圆角使用矩形。
     * 未绘制后处理时返回 false，由调用方绘制底板与内容。
     */
    @JvmStatic
    @JvmOverloads
    fun tryBackdrop(graphics: GuiGraphics, rect: GuiRect, strength: Float, style: GlassStyle,
                    radiusVirtual: Float, cornerRadius: Float = 0f): Boolean =
        tryBackdrop(graphics, rect, strength, style, radiusVirtual, cornerRadius, ThemeEffects.glass())

    /** 山海：使用独立设置快照预览玻璃取样。 */
    @JvmStatic
    fun tryBackdrop(graphics: GuiGraphics, rect: GuiRect, strength: Float, style: GlassStyle,
                    radiusVirtual: Float, cornerRadius: Float, settings: GlassBackdropSettings): Boolean {
        if (!strength.isFinite() || strength <= MIN_STRENGTH || !radiusVirtual.isFinite() || radiusVirtual < 0f
            || !cornerRadius.isFinite() || cornerRadius < 0f) return false
        return sampleBackdrop(graphics, rect, strength.coerceAtMost(1f), style, radiusVirtual.coerceAtMost(32f),
            cornerRadius, legacyRadius = false, settings = settings)
    }

    private fun sampleBackdrop(graphics: GuiGraphics, rect: GuiRect, strength: Float, style: GlassStyle,
                               radiusVirtual: Float, cornerRadius: Float, legacyRadius: Boolean,
                               settings: GlassBackdropSettings = ThemeEffects.glass()): Boolean {
        if (rect.isEmpty) return false
        val pose = graphics.pose().last().pose()
        if (pose.m01() != 0f || pose.m10() != 0f) return false
        val view = capture(graphics) ?: return false
        val window = Minecraft.getInstance().window
        if (window.width <= 0 || window.height <= 0) return false
        // 原生正交投影使用未取整的 GUI 宽高，不能使用向上取整的 guiScaledWidth / Height。
        val pixelScaleX = window.guiScale.toFloat() * view.fbW / window.width
        val pixelScaleY = window.guiScale.toFloat() * view.fbH / window.height
        val scaleX = pixelScaleX * view.scaleX
        val scaleY = pixelScaleY * view.scaleY
        if (!(scaleX > 0f && scaleY > 0f)) return false
        val px = GlassGeometry.applyAffine(rect, scaleX, scaleY,
            view.translateX * pixelScaleX, view.translateY * pixelScaleY)
        val radius = radiusVirtual * minOf(scaleX, scaleY)
        val corner = RoundedCorners.fit(rect.w, rect.h, cornerRadius) * scaleX
        return if (legacyRadius) {
            GaussBlur.blurRoundedNow(graphics, px.x, px.y, px.w, px.h, radius, style, corner, scaleY / scaleX)
        } else {
            GaussBlur.sampleBackdropNow(graphics, px.x, px.y, px.w, px.h, strength, radius, style, corner,
                scaleY / scaleX, settings)
        }
    }

    /** 山海：保留原矩形的过渡位置，交由后处理与当前 scissor 取交集。 */
    @JvmStatic
    fun revealRegion(graphics: GuiGraphics, rect: GuiRect, radiusVirtual: Float,
                     fullBlurUntil: Float, sharpFrom: Float): Boolean =
        revealRegion(graphics, rect, radiusVirtual, fullBlurUntil, sharpFrom, GaussBlur.RevealDirection.LEFT_TO_RIGHT)

    /** 山海：从指定边缘到另一侧的空间渐进模糊，继续沿用当前裁切。 */
    @JvmStatic
    fun revealRegion(graphics: GuiGraphics, rect: GuiRect, radiusVirtual: Float,
                     fullBlurUntil: Float, sharpFrom: Float, direction: GaussBlur.RevealDirection): Boolean {
        if (rect.isEmpty || radiusVirtual <= 0f) return false
        val pose = graphics.pose().last().pose()
        if (pose.m01() != 0f || pose.m10() != 0f) return false
        val view = capture(graphics) ?: return false
        val px = view.toPixels(rect)
        if (px.isEmpty) return false
        val scale = view.pixelScale * minOf(view.scaleX, view.scaleY)
        return GaussBlur.revealNow(graphics, px.x, px.y, px.w, px.h,
            radiusVirtual * scale, fullBlurUntil, sharpFrom, direction)
    }

    /** 一帧内的换算上下文：framebuffer 尺寸、GUI 尺寸，以及当前 pose 的缩放与平移。 */
    private class View(
        val fbW: Int,
        val fbH: Int,
        val guiW: Int,
        val guiH: Int,
        val scaleX: Float,
        val scaleY: Float,
        val translateX: Float,
        val translateY: Float,
    ) {
        /** framebuffer 像素 / GUI 单位，用来把虚拟半径换成像素半径。 */
        val pixelScale: Float get() = fbW.toFloat() / guiW

        fun toPixels(rect: GuiRect): PixelRect =
            GlassGeometry.toPixels(
                GlassGeometry.applyAffine(rect, scaleX, scaleY, translateX, translateY),
                fbW, fbH, guiW, guiH,
            )
    }

    private fun capture(graphics: GuiGraphics): View? {
        val mc = Minecraft.getInstance()
        val target = mc.mainRenderTarget ?: return null
        val guiW = mc.window.guiScaledWidth
        val guiH = mc.window.guiScaledHeight
        if (guiW <= 0 || guiH <= 0) return null
        val pose = graphics.pose().last().pose()
        return View(target.width, target.height, guiW, guiH, pose.m00(), pose.m11(), pose.m30(), pose.m31())
    }
}
