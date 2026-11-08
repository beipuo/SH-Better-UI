<!-- 作者：山海／dishanhai -->
# 对话框、气泡提示、动作面板、底部抽屉、下拉刷新与滑动确认

Forge 1.20.1 和 NeoForge 1.21.1 共用 `com.dishanhai.sh_better_ui.client.gui.scaled` 下的接口。画面持有一个 `UiOverlayHost`，每个可刷新的列表独立持有一个 `UiPullRefresh`。从客户端线程调用。按钮和正文传入 `Component.translatable(...)`。

## 原生 Screen 覆层

```java
// Screen 字段。构造时传入 this，打开时画面须已显示。
private final UiOverlayHost overlays = new UiOverlayHost(this, UiPreferences.DEFAULT);

// 在控件回调中打开。
overlays.showNotice(title, details);
overlays.showDialog(new UiConfirmation.Prompt(title, consequences, confirm, cancel), this::submit);
overlays.showActions(menuTitle, List.of(
        new UiOverlayHost.Action(editLabel, this::openEditor),
        new UiOverlayHost.Action(deleteLabel, this::submitDelete, deleteConfirmation)));

// 由悬停、聚焦或业务状态决定显示时机。getAnchorBounds 返回当前 GUI 坐标。
overlays.showBubble(this::getAnchorBounds, helpText);
overlays.hideBubble();
```

`getAnchorBounds()` 返回 `Rect2i`，控件移除、隐藏或滚出裁切区时返回 `null`。气泡跟随锚点，靠近边缘时翻转或收窄，可与原有控件操作并存。

对话框与动作面板在同一个 Screen 中打开，库负责绘制和优先输入分派。Tab、Shift+Tab、Enter、空格和 Esc 操作前景控件。动作带 `UiConfirmation.Prompt` 时，先显示确认框，取消后返回动作列表。请求应从确认回调中发出。普通关闭归还有效焦点，主动调用 `close()` 会一并清除气泡。容器的原生拖拽进行中应延后打开模态。

### 胶囊按钮展开为对话框

将 `UiStyle.LIQUID_GLASS` 按钮注册为画面的原生控件，在按下回调中传给 `showDialogFrom`。

```java
addRenderableWidget(new UiButton(x, y, width, height, label,
        button -> overlays.showDialogFrom((UiButton) button, prompt, this::submit),
        preferences, false, UiStyle.LIQUID_GLASS));
```

同一块玻璃底板随过阻尼弹簧改变位置、尺寸和圆角。Esc 可以在展开途中收回。展开到位后可操作确认和取消，确认回调立即执行，收合结束后归还源按钮焦点。开启减少动效时直接切换。源按钮被移除或移出视口后，对话框使用居中布局，关闭时直接结束。

## 16×16 px 图标

提示框、确认框、气泡和动作面板的标题分别使用 `INFO`、`WARNING`、`BUBBLE` 和 `LIST`。确认、取消与动作项可以同时显示图标和文字。带二次确认的动作默认用警告图标，其他动作可按用途指定图标。窄画面优先显示文字。

```java
var helpIcon = new GraphicIcon(ResourceLocation.tryParse("your_mod:textures/gui/help.png"));
overlays.showBubble(this::getAnchorBounds, helpText, helpIcon);
overlays.showActions(menuTitle, List.of(
        new UiOverlayHost.Action(editLabel, this::openEditor, null, GraphicIcon.SETTINGS),
        new UiOverlayHost.Action(deleteLabel, this::submitDelete, deleteConfirmation, GraphicIcon.REMOVE)));
```

`showNotice`、`showDialog`、`showActions`、`showBubble` 以及独立的 `UiConfirmation.open` 可在最后一个参数传入自定义图标，传 `null` 时标题或气泡只显示文字。自定义资源使用 16×16 px 透明 PNG，白色像素支持着色。原生 `UiButton` 可用 `setLeadingIcon(icon)` 同时显示图标与名称。

## 底部抽屉

游戏中按 F8 打开 SHBUI 主控面板，进入「开发者面板 → 组件演示 → 底部抽屉」。从低档慢拖并停一下再松手，回到低档后快速向上甩，比较两次的落点。切到高档后继续上拉可试越界回弹。底部的「减少动效」开关只影响这页示例。

每个抽屉持有一个 `UiBottomSheet`。档位是从底边向上露出的视口比例，按 `0..1` 严格递增，`0` 表示完全隐藏。下面的抽屉初始停在第二档，露出视口的 25%。视口可以是整个画面，也可以是留给抽屉的局部区域。

```java
private final UiBottomSheet drawer = new UiBottomSheet(1, 0f, 0.25f, 0.55f, 0.85f);
private UiBottomSheet.Frame drawerFrame;

// 在原画面内容之后绘制。drawDrawerContents 接收正文的绝对 GUI 坐标。
drawerFrame = drawer.render(graphics, new Bounds(0, 0, width, height), mouseX, mouseY,
        PageTransition.nowMs(), preferences, this::drawDrawerContents);

// 在 Screen.mouseClicked 中，先分派正文点击，再检查把手。
if (drawerFrame != null && drawerFrame.content().contains(x, y)) {
    clickDrawerContents(x, y, button);
    return true;
}
if (drawer.beginCaptured(this, drawerFrame, button, x, y)) return true;
if (drawerFrame != null && drawerFrame.viewport().contains(x, y)
        && drawerFrame.panel().contains(x, y)) return true;
return super.mouseClicked(x, y, button);
```

`Bounds` 是 `GraphicDisplayLayout.Bounds`。绘制回调和正文输入共用裁切后的 `Frame.content()`，按下使用最近一次绘制返回的 `Frame`。原生 `Screen` 用 `beginCaptured` 接收拖动、放开、Esc 和失焦取消。自行分派输入的容器用 `begin`、`drag`、`release` 和 `cancelGesture`，事件与绘制统一使用 `PageTransition.nowMs()`。画面重建时清除旧的 `drawerFrame`，新视口由下一帧提供。

拖到最小或最大档位之外时，额外位移越大，阻力越强。松手时纳入放开坐标，用近 100 毫秒的纵向速度投影落点，选中距投影位置最近的档位。快速甩动可跨档，停住后按当前位置吸附。拖动时的短线和 `Frame.targetAnchor()` 预览落点，松手后可用 `drawer.targetAnchor()` 读取选定档位。减少动效时仍可跟手拖动，边界直接止住，放开即到位。

将本地化的展开和收起按钮注册到 `Screen` 的焦点顺序中，从按钮回调调用 `drawer.animateTo(index, PageTransition.nowMs())`。需要从隐藏档打开时，把展开按钮放在抽屉外。通过 `snapTo(index)` 可以直接重置档位和手势。

## 原生 Screen 下拉刷新

在列表顶部留一个独立、可见的拖拽区域，避开槽位、滚动条及普通按钮。通过 `refresh.render(graphics, font, handleBounds, PageTransition.nowMs(), preferences)` 画把手和提示，再用相同区域命中。键盘替代入口是 `refresh.createButton(buttonBounds, preferences)`，将它作为原生控件注册。

```java
private final UiPullRefresh refresh = new UiPullRefresh(this::sendRefreshRequest);

// 从 Screen.mouseClicked 调用，handleContains 只匹配预留的把手。
private boolean beginPull(double x, double y, int button) {
    if (!handleContains(x, y) || !refresh.begin(button, x, y, listAtTop(), PageTransition.nowMs())) {
        return false;
    }
    boolean captured = UiPointerCapture.start(this, button, new UiPointerCapture.Handler() {
        public void drag(double x, double y) {
            refresh.drag(button, x, y, releaseAreaContains(x, y) && listAtTop(), PageTransition.nowMs());
        }
        public void release(double x, double y) {
            refresh.release(button, x, y, releaseAreaContains(x, y) && listAtTop(), PageTransition.nowMs());
        }
        public void cancel() {
            refresh.cancelGesture(PageTransition.nowMs());
        }
    });
    if (!captured) refresh.cancelGesture(PageTransition.nowMs());
    return captured;
}
```

在客户端线程处理回执，先调用 `complete(requestId, tone, PageTransition.nowMs())`。只有返回 `true` 且业务回执成功时才应用新的列表结果。回执超时或失败后可以重试。画面移除或切换数据源时调用 `reset(PageTransition.nowMs())`，以作废旧回执。等待时保留当前列表、搜索与选取，交由接入方决定成功后的数据更新方式。

把手以箭头提示向下拖动，达到门槛后切换刷新图标，实际回执决定完成、警告或失败图标。`new UiPullRefresh(config, onRefresh, frame -> ...)` 可替换状态图标，`frame.icon()` 返回默认图标，返回 `null` 时只显示该状态的文字。

## 原生 Screen 滑动确认

在游戏内打开「开发者面板 → 控件」，翻到「数字滑块」后的「滑动确认」。可直接拖动圆钮，点右侧「重置」后再试。

```java
// 在 Screen.init() 注册。Bounds 来自 GraphicDisplayLayout。
var slide = addRenderableWidget(new UiSlideConfirm(
        this, new Bounds(x, y, 160, 24), preferences, this::submit));
```

高度至少为 20 个 GUI 单位，宽度至少为高度加 64。需要说明具体操作时，可在 `Bounds` 后传入本地化的 `Component` 作为初始标签。

按住圆钮后，轨道填充跟随它的横向行程。到终点显示「松开确认」，松开时仍在终点才执行回调。提前松手或按 Esc 会回到起点，开启减少动效时直接归位。键盘可用 Tab 聚焦，按住右方向键到达终点后松键。

回调执行时 `isCompleted()` 已为 `true`，文字立即显示「已完成」。圆钮停在终点，对勾从小到大出现，圆钮与填充渐变为成功色。高对比模式沿用高对比配色，减少动效时直接显示完成态。调用 `reset()` 可立即复位并开始下一次确认。

## Forge LDLib 1.x Widget

使用 `com.dishanhai.sh_better_ui.compat.ldlib` 中的类，编译版本为 LDLib `1.0.33.b`。

```java
var overlays = new LdLibOverlays(modularUiGuiContainer, preferences);
overlays.showBubble(widget, helpText);
overlays.showActions(menuTitle, actions);

var handle = new LdLibPullRefreshWidget(x, y, width, height, refresh, preferences,
        () -> scroll.getScrollYOffset() == 0, scroll::toRectangleBox);
root.addWidget(handle);
```

将把手放在滚动组旁的固定区域，使用父 Widget 的局部坐标。`releaseBounds` 用绝对 GUI 坐标限制放开区域。默认提供 F5 刷新，在同画面的一个把手启用即可。也可在接入方自己的按钮回调中调用 `handle.requestRefresh()`。服务端继续使用原有消息处理路径。

`LdLibOverlays` 的四种浮层方法同样支持在最后一个参数指定 `GraphicIcon`。两种 LDLib 把手从传入的 `UiPullRefresh` 读取图标状态。

## NeoForge LDLib2 UIElement

使用 `com.dishanhai.sh_better_ui.compat.ldlib2.LdLib2UiBridge`，编译版本为 LDLib2 `2.2.44`。第一次构建 NeoForge 时联网解析该编译依赖。使用此桥接的客户端同时安装 LDLib2。

```java
var overlays = LdLib2UiBridge.overlay(owner, preferences);
overlays.showBubble(LdLib2UiBridge.anchor(owner, element), helpText);
overlays.showDialog(prompt, this::submit);

var handle = LdLib2UiBridge.createPullRefreshHandle(owner, viewport, refresh, this::listAtTop, preferences);
layoutAboveViewport.addChild(handle);
// 在宿主 Screen.init() 注册，以接入 Tab、Enter、空格和旁白。
addWidget(handle.focusTarget());
```

把手占用列表顶部独立的布局区域。`viewport` 是放开时允许提交的列表可见区，气泡锚点会读取 `UIElement` 的当前变换与裁切。原生 `Screen` 使用者只需 SH-Better-UI 的共用接口。
