# 可选玻璃效果

作者：山海／dishanhai。

这些接口是 UI 库能力，消费端调用后才显示，不自动改变现有 Screen、LDLib 控件或主题。包名为 `com.dishanhai.sh_better_ui.client.gui.scaled`，不需要额外依赖、纹理、shader 或语言文本。

## 三种效果

- `ClickRipple` + `GlassEffectRenderer.drawRipple`：点击位置向外扩散的亮纹，420ms 后结束。每个按钮持有自己的实例，连续点击重启；不改变按钮操作、网络请求和命中区。
- `drawFlowBorder`：沿矩形四边顺时针流动的细亮边，2 秒一轮，最多 128 个 fill。
- `drawBreathingGlow`：控件外围 3 GUI 像素的柔光，2.4 秒一轮；`focusAmount` 可复用已有 hover 弹簧，0 时完全不画。

`reducedMotion=true` 时，涟漪变为均匀淡出，流光及呼吸保持静态亮度。调用方沿用自己的减少动效设置；库不新增配置文件。

## 组合接入示例

在消费端控件中保留实例，点击事件仍先按原有逻辑确认按钮启用、鼠标按键及命中；只在实际接受点击时触发效果：

```java
private final ClickRipple clickRipple = new ClickRipple();

// 原有 mouseClicked / 点击回调接受左键操作的位置：
clickRipple.trigger(mouseX, mouseY, buttonX, buttonY, buttonW, buttonH,
        System.currentTimeMillis());

// 每帧，沿用原有底板、点击缩放与 hover 弹簧；先画底板，再调用装饰，最后画文字图标：
long nowMs = System.currentTimeMillis();
int accent = 0x809DDCFF; // 示例色；实际使用主题色板颜色。
GlassEffectRenderer.drawBreathingGlow(graphics, buttonX, buttonY, buttonW, buttonH,
        hoverAmount, nowMs, reducedMotion, accent);
GlassEffectRenderer.drawFlowBorder(graphics, buttonX, buttonY, buttonW, buttonH,
        nowMs, reducedMotion, accent);
GlassEffectRenderer.drawRipple(graphics, buttonX, buttonY, buttonW, buttonH,
        clickRipple, nowMs, reducedMotion, accent);

// 原有关闭/移除回调：
clickRipple.clear();
```

多个按钮各持有一个 `ClickRipple`。原点保留为相对坐标；绘制传入控件当前矩形，窗口重排时原点随按钮移动。命中采用左上包含、右下不包含的半开区间。无效点击保持原效果，非法宽高和非有限指针坐标被忽略。

使用 `GuiRenderUtil.withButtonMotion` 时，将三个装饰调用放入原有绘制回调内，使底板、亮纹及边框一起运动；指针坐标仍使用原控件命中坐标。LDLib 在客户端绘制回调中同样可调用这些入口，坐标必须与传入的 `GuiGraphics` 当前 pose 一致。

所有绘制使用现有 `GuiGraphics.fill`，不改 scissor、pose、shader 颜色或全局后处理。外围柔光遵守调用方现有裁切，控件间应留出 3 GUI 像素空间；矩形以外不放宽鼠标命中区。

本效果是按钮内部亮纹，不替换 `GaussBlur.requestWave` 的屏幕折射波。后者仍使用既有全局单波槽；新接口可同时绘制多个局部亮纹，且不占用该槽。

## 验证边界

2026-11-07 主 agent 统一执行完整 `build --offline --no-daemon --console=plain` 成功；全库 107 项测试通过，其中新效果 17 项。测试覆盖时间轴、点击边界、相对原点、重复触发、减少动效、周期计算，以及同包 `Fill` 记录生产算法的绘制几何：四角只覆盖一次、边框完整、亮纹裁切、外光不进内容、调用数量上限。

`releaseJar`／`verifyLibraryJar` 通过，正式包与 `reobfJar/output.jar` SHA256 相同：`F6B72C8DA3EAFD866FAB5E3C35FC6A6843E7D456EF14E8D2F36E4BC45095637D`。未部署。游戏内仍需检查深蓝／烟灰／浅色底板、GUI scale 1～4、多个按钮连续点击、嵌套滚动裁切、现有点击缩放、减少动效和关闭重开。
