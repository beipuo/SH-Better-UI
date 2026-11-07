<!-- 作者：山海／dishanhai -->
# 按钮与界面切换反馈

## 文件与职责

| 文件 | 位置 | 内容 |
| --- | --- | --- |
| UiButton.java | client/gui/scaled | 公开原生按钮、悬停功能提示、选中状态、选中旁白与短暂强调 |
| UiAttention.java | client/gui/scaled | 按稳定内容键切换，指示条连续移动，输出内容淡入、6 GUI 单位纵移及目标亮边强度 |
| DarkModeTheme.java | button / selectionIndicator / attentionTarget | 普通按钮也有悬停柔光，增强悬停边框与底色，绘制持续选中标记和切换强调 |
| GuiRenderUtil.java | withAttentionContent | 应用内容淡入与位移，合成并恢复外层 RGBA，异常时恢复矩阵 |
| UiDisclosure.java | button | 展开状态驱动选中标记，按钮名称和默认提示同步 |
| JeiSearchPanel.java | button / renderOverlays / drawRecipe | 获取方式、用途、打开与选用按钮使用共用反馈，分页驱动内容转场 |
| zh_cn.json / en_us.json | assets/sh_better_ui/lang | 选中状态及四个 JEI 操作的功能说明 |

## 原生按钮

每个按钮使用独立实例。按钮保留原生输入、焦点导航及 tooltip，`setPreview` 传入本地化组件。未设置说明时，默认提示显示完整按钮名称，修改名称时提示同步更新。

```java
var uses = new UiButton(x, y, width, height,
        Component.translatable("sh_better_ui.jei.uses"),
        button -> setDirection(Direction.USES), preferences, false);
uses.setPreview(Component.translatable("sh_better_ui.jei.uses.preview"));
uses.setSelected(direction == Direction.USES);
addRenderableWidget(uses);
```

`setSelected` 由画面的实际状态驱动。每次渲染重复设置同一值不会重播强调。服务器控制的操作应在收到状态后更新选择。`UiDisclosure` 的展开状态属于本地显示状态，直接更新选择。

## 自绘页签与内容切换

每组页签保存一个 `UiAttention`。每帧使用相同的单调时钟，提交当前选中控件的矩形。内容键可以包含页签与条目 ID，相等的键只调整布局。第一次显示直接建立当前位置，后续键改变时触发内容进入。切换指示条复用 `Spring.INDICATOR`，中途改目标保留当前位置和速度。

```java
private final UiAttention attention = new UiAttention();

// 在 render 中，先绘制按钮，再绘制内容和指示条。
long now = PageTransition.nowMs();
attention.select(pageKey, tabX, tabY, tabWidth, tabHeight, now);
var frame = attention.sample(now, preferences);
DarkModeTheme.selectionIndicator(graphics, frame, preferences);
DarkModeTheme.attentionTarget(graphics, contentX, contentY, contentWidth, contentHeight, frame, preferences);
GuiRenderUtil.withAttentionContent(graphics, frame, () -> renderPage(graphics));

// 内容本身需要交互时，使用同一帧变换回内容坐标。
double contentMouseY = frame.contentMouseY(mouseY);
```

消费画面保留内容裁切，并将同一帧的偏移用于绘制、命中和拖动。原生控件的 tooltip 应在内容变换结束后绘制。对于内部维护绝对坐标的渲染器，可将偏移加入其实际绘制坐标，再调用 `withAttentionContent(graphics, frame.contentAlpha(), 0f, draw)` 仅合成透明度。JEI 已采用此方式，提示使用实际配方位置，绘制裁切外不会触发配方提示。

移除或隐藏整个分组时调用 `attention.clear()`。减少动效时，`sample` 立即完成位移与淡入，选中边框和指示条持续显示。高对比时，指示条使用浅蓝实色。

这些接口由消费画面接入。库内已接入展开按钮和 JEI 面板。玻璃材质、自绘控件及其他模组画面可以复用状态帧，使用各自色板绘制。

## 验证与预览

- Forge 和 NeoForge 共用上述 Java 源码与本地化资源。
- 覆盖相同键不重播、布局变化、快速反向、内容淡入、非法矩形、清理及减少动效的状态测试。
- 原生按钮测试覆盖默认／自定义提示、标签改变、调用者确认选择及回调次数。JEI 面板与展开控件测试覆盖真实入口的选中状态。
- 离线预览将实际 `DarkModeTheme` 绘图命令交给 Java2D 后端，实际 `UiAttention` 和 `Interaction` 提供逐帧状态。预览使用系统字体。
- 同一离线后端执行外层 RGBA 合成和内容回调抛异常的恢复探针。它只验证调用层状态管理。
- Minecraft 的原生 tooltip、旁白、焦点、裁切、GUI scale 与 GPU 渲染仍需实机验收，详见 `TODO.md`。

2026-11-07 最终执行 `test build --offline --no-daemon --console=plain`，Forge 177 项、NeoForge 179 项测试，均为 0 failures/errors/skipped，两个平台均 `BUILD SUCCESSFUL`。

Forge 正式包与 `build/reobfJar/output.jar` 的 SHA-256 一致，为 `AEBCCB363BB38FFAD317FA1C6EDD7D0394E65EDC8BC9A1E6B5CA84E955BFF245`。NeoForge 正式包 SHA-256 为 `873C686D0FD78664C876F268E65B4E1EF986C77D177355F5C9D8DC58CE27C4B3`。
