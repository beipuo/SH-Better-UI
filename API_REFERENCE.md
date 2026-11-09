<!-- 作者：山海／dishanhai -->
# SH-Better-UI API 总览

这份索引以当前源码为准，基线为 `86e383f`，两端构建版本均为 `1.0.3`。带 **工作区新增** 标记的入口来自尚未提交的实现。接入现成 jar 时，以该 jar 包含的接口为准。

## 统一调用入口

**工作区新增** `com.dishanhai.sh_better_ui.client.api.ShBetterUiApi` 是 Forge 与 NeoForge 的共同入口。每个分组是无状态单例，工厂返回现有组件类型。界面持有自己的控件、动画和手势状态，并按下文对应的绘制、输入与释放约定调用。[统一入口源码](src/main/java/com/dishanhai/sh_better_ui/client/api/ShBetterUiApi.java)

| 分组 | 主要入口 |
| --- | --- |
| `settings()` | `preferences()`、`current()`、`backdrop()`、`save()`、`saveBackdrop()` 与内置面板工厂 |
| `controls()` | 按钮、滑块、开关、分区、详情、状态、骨架与进度 |
| `interactions()` | 覆层、全屏预览、确认、抽屉、刷新、滑动确认、鼠标快照、指针捕获、视口、历史和轻提示 |
| `search()` | `index()`、`field()`、`jeiPanel()`，以及 JEI 查询、打开和侧栏避让 |
| `ingredients()` | 素材来源、筛选、物品包装、选取草稿与 `openPicker()` |
| `materials()` | 玻璃主题、Slate、液态玻璃、槽位和圆角绘制 |
| `blur()` | GUI 坐标即时模糊、像素坐标帧末请求、渐进清晰、渐变和前景回调 |
| `graphics()` | 图标、文字、图文布局、网络风格界面、电光轮廓与反重力粒子 |
| `layout()` | [视觉对齐](VISUAL_ALIGNMENT.md)，包含光学度量、动态约束、像素吸附和调试叠层 |
| `motion()` | 时钟、弹簧、可打断动画、切页、胶囊过渡、`screenCrossfade()` 旧页淡出、点阵、图案波面、形状波面与帧时间轴 |
| `audio()` | `systemOutput()` 显式开启 Windows 输出回环，`analyzer()` 独立分析 PCM。`motion().musicMotion()` 映射单个频段，`motion().musicRhythm()` 提供多层律动帧，可配合 `MusicHud` 绘制像素面板，或配合 `MusicIsland` 绘制可展开的音乐胶囊。见 [音乐律动](docs/music-rhythm.md) |
| `lyrics()` | `localFeed()`、`saltPlayer()`、`renderer()`、`draw()`、`settings()` 与 `position()`。支持逐字同步、译文与多行排版。见 [同步歌词](docs/lyrics.md) |
| `platform()` | 当前加载器的 `fluid()`、`renderTooltip()` 与可选 `ldlib()` 桥接 |

下面在 `Screen.init()` 中注册一个原生按钮。`label` 和 `onPress` 由宿主提供。

```java
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;

var area = ShBetterUiApi.graphics().bounds(x, y, 120, 20);
var button = addRenderableWidget(ShBetterUiApi.controls().button(area, label, onPress));
```

需要兼容没有 JEI 的客户端时，在打开选择器的回调中使用 `automaticCatalog()`。宿主提供当前有效的返回焦点及提交函数。

```java
var ingredients = ShBetterUiApi.ingredients();
var request = ingredients.singleRequest(pickerTitle, ingredients.automaticCatalog());
ingredients.openPicker(this, request, this::currentPickerButton, this::applySelection);
```

枚举、请求、布局和配置继续使用已有类型。`platform().ldlib()` 下的方法按各端安装的 LDLib 版本调用，常用分组在 JEI 和 LDLib 缺席时也可加载。[共享分组源码](src/main/java/com/dishanhai/sh_better_ui/client/api/) · [NeoForge 平台分组](neoforge/src/main/java/com/dishanhai/sh_better_ui/client/api/PlatformUiApi.java)

## 全库客户端主题

在控制面板的「配置 → 显示」切换深色、浅色或自定义主题。面板立即预览草稿，点击「应用」后，Slate、液态玻璃、网络控件及读取共用材质的画面会使用新配色。自定义主题从深色或浅色基底继承未填写的颜色。

```java
var settings = ShBetterUiApi.settings();
var materials = ShBetterUiApi.materials();

boolean saved = settings.saveTheme(settings.theme().withMode(ThemeSettings.Mode.LIGHT));
var palette = materials.theme(); // 读取当前绘制用配色，供自绘标题和底板使用
var icon = materials.themeIcon(settings.theme().mode());
```

通过 `settings.saveTheme(new ThemeSettings(mode, base, background, surface, border, text, accent))` 保存自定义主题。颜色接受六位 RGB，例如 `#F6EFE6`，空字符串继承基底。也可编辑 `config/sh_better_ui-client.toml` 的 `[ui]` 段，其中包含 `themeMode`、`themeCustomBase` 及五个 `themeCustom*` 色值。自绘画面可调用 `materials.resolveTheme(settings)` 获取预览配色。显式选择的 `GlassTheme.CLASSIC`、`GLASS` 和 `SMOKE` 各有独立的材质覆写分组。

在控制面板的「配置 → 显示」选中自定义，切换分组并填写 JSON。可填入模板查看全部字段，删掉要继承默认值的字段。点击「预览分组设置」将当前内容存入草稿，最后点击「应用」。空对象 `{}` 会清除该组覆写。

全量分组由 `settings.themeGroups()` 列出，`settings.themeTemplate(group)` 返回对应字段。TOML 的 `[ui].themeCustomEffects` 储存一份分组 JSON，例如：

```toml
themeCustomEffects = '{"glass":{"strength":0.65},"dotMatrix":{"grid":{"spacing":12},"palette":{"dotArgb":"#7755AA"}}}'
```

`settings.saveTheme(theme.withEffects(new ThemeOverrides(json)))` 校验并保存。通过 `materials.themedProfile(group, fallback)` 可将自定义值合并进宿主显式提供的 Profile。空字段继承传入值，深色和浅色模式保留自定义配置供稍后切回。

## 通用接入与 API 协作

`ShBetterUiApi.client()` 返回可传递的 `UiClientApi` 实例，Java 与 KubeJS 共用 `UiApi` 分组访问契约。需要增加独立能力时，可通过 `extensions()` 注册工厂，再由 `UiApiContext` 取得实例。现有分组保持类型化调用。

```java
// 在调用方的客户端初始化中登记，持有句柄，在调用方卸载时 close()。
var registration = ShBetterUiApi.extensions().register(
        "your_mod:waves", PatternWaves.class, api -> api.motion().patternWaves());

ShBetterUiApi.screen(Component.translatable("your_mod.ui.title"))
        .onRender((screen, frame) -> {
            var waves = screen.context().extension("your_mod:waves", PatternWaves.class);
            waves.draw(frame.graphics(), frame.viewport(), frame.mouse(), frame.preferences());
        })
        .open();
```

`PatternWaves` 位于 `client.gui.scaled`。扩展工厂接收上下文，可通过 `api.extension(otherId, OtherType.class)` 组合其他扩展。注册 ID 必须包含 `namespace:path`，重复 ID 会报错，关闭注册句柄后可重新登记。每个上下文按需创建并缓存自己的扩展，已解析实例保留到上下文关闭。工厂失败后可重试，循环依赖会报告解析路径。

扩展实现 `AutoCloseable` 时，上下文会自动管理其释放。其他资源可用 `context.manage(resource)` 登记，也可以用 `context.onClose(task)` 登记释放函数。同一个资源按对象身份去重，并按依赖创建的相反顺序释放。手动创建上下文时使用 `try-with-resources`。上下文由客户端线程持有，工厂执行期间不占用注册表锁。[注册表](src/main/java/com/dishanhai/sh_better_ui/client/api/UiExtensionRegistry.java) · [协作上下文](src/main/java/com/dishanhai/sh_better_ui/client/api/UiApiContext.java)

`screen()` 构建器提供默认返回顶栏、原生控件输入与旁白、父页及有效焦点恢复。`onInit` 在打开和重布局时注册控件，`onRender` 在控件之前绘制，`onForeground` 在控件之后绘制，两者共享一个 `UiFrame`。`frame.viewport()` 是扣除返回栏后的内容区域。离开页面会调用 `onRemoved` 并关闭上下文，返回同一页面时重新初始化。跨页面保留的业务草稿由调用方持有。`dispose()` 可永久释放单页，`disposeAll()` 用于一并释放存在父子关系的页面。

通过 `ShBetterUiApi.client().run(task)` 把异步回执交给客户端线程。`screen().open()` 在执行时捕获当前父页，显式 `parent(null)` 可返回游戏，`backButton(false)` 由调用方安排返回控件。父页重建入口控件时，传入 `returnFocus(() -> currentButton)`，关闭后会取得新的有效焦点。自绘背景与控件均裁切到内容区域，点击与滚轮使用同一边界。[界面构建器](src/main/java/com/dishanhai/sh_better_ui/client/api/UiScreenBuilder.java) · [帧快照](src/main/java/com/dishanhai/sh_better_ui/client/api/UiFrame.java)

Java 调用方可用 `onError((screen, failure) -> ...)` 接收构建器生命周期及便捷按钮回调的运行时异常，页面会先永久关闭并释放资源。省略时沿用 Java 异常传播。KubeJS 构建器默认启用日志报告和页面释放。

KubeJS 客户端脚本使用自动绑定的 `SHUI`，提供同一套分组和构建器，并按脚本重载周期清理登记的扩展、界面及上下文。完整示例、双端版本和生命周期约定见 [KubeJS 接入](KUBEJS.md)。

## 双端接入

对外入口位于 `com.dishanhai.sh_better_ui` 下。原生 `Screen` 的控件、主题、搜索、选择器和动效大多共用源码。在客户端线程创建和更新 UI，在渲染回调中绘制。界面负责本地化 `Component`、业务数据和服务端回执。

| 平台 | 环境 | 接入产物 |
| --- | --- | --- |
| Forge 1.20.1 | Java 21，Forge 47.4.16 起，外部 KotlinForForge `[4.12.0,5.0.0)` | 开发用 `sh_better_ui-1.0.3-dev.jar`，游戏用 `sh_better_ui-1.0.3.jar` |
| NeoForge 1.21.1 | Java 21，NeoForge 21.1.251 起，外部 KotlinForForge `[5.10.0,6)` | 开发和游戏均使用 `sh_better_ui-neoforge-1.21.1-1.0.3.jar` |

- 接入模组声明依赖 `sh_better_ui`，游戏中单独安装对应平台的 UI 库。NeoForge 复用根目录的 Java、Kotlin 与资源，并替换加载器事件、渲染和素材适配所需的类。[接入与构建](README.md) · [Forge 元数据](src/main/resources/META-INF/mods.toml) · [NeoForge 元数据](neoforge/src/main/resources/META-INF/neoforge.mods.toml) · [NeoForge 源集](neoforge/build.gradle)
- 共用控件坐标使用 GUI 逻辑单位。布局可复用 `GraphicDisplayLayout.Bounds`，读取当前高对比、减少动效与控件模糊偏好可用 `UiPreferences.configured()`。事件和绘制中的动画时间统一使用 `PageTransition.nowMs()`。[坐标与布局](src/main/java/com/dishanhai/sh_better_ui/client/gui/display/GraphicDisplayLayout.java) · [界面偏好](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiPreferences.java)
- `ClientUiSettings.current()` 与 `GlassBackdropSettings.current()` 读取客户端配置快照。`ShBetterUi.saveClientUiSettings(settings)`、`saveGlassBackdrop(settings)` 返回保存结果，对应 `config/sh_better_ui-client.toml` 的 `[ui]` 与 `[glassBackdrop]` 配置。[设置类型](src/main/java/com/dishanhai/sh_better_ui/config/) · [Forge 保存实现](src/main/java/com/dishanhai/sh_better_ui/ShBetterUi.java) · [NeoForge 保存实现](neoforge/src/main/java/com/dishanhai/sh_better_ui/ShBetterUi.java)

## 主题、玻璃与模糊

背景先画，玻璃材质和模糊随后，前景文字及图标最后画。已在玻璃工具栏内的控件可调用 `setSampleBackground(false)` 共用底层取样。

| 用途 | 入口与调用约定 |
| --- | --- |
| 玻璃面板、按钮与槽位 | `GlassTheme.CLASSIC / GLASS / SMOKE` 提供 `rootPanel()`、`backdrop()`、`region()`、`button()` 等。`GlassSurface.panel()/control()` 提供中性浅色玻璃，`smokePanel()/smokeControl()` 提供烟灰玻璃，`drawString()` 绘制带字形柔光的文字。`GlassColors`、`GlassPalette` 与 `GlassCell.draw()` 提供配色和槽位底板。 |
| Slate 暗色和液态玻璃 | `DarkModeTheme` 用 `Interaction.card()/control()` 采样控件状态，再调用 `card()/button()/input()` 绘制。`LiquidGlassTheme.toolbar()/popover()/input()/button()` 用于自绘材质。原生 `UiButton` 与 `SearchField` 可直接选 `UiStyle.LIQUID_GLASS`。 |
| 矩形或圆角即时毛玻璃 | `GlassBlur.backdrop()/roundedBackdrop()/tryBackdrop()` 接收 `GuiRect` 和 GUI 单位半径，`tryBackdrop()` 返回取样是否成功。材质参数来自 `GlassStyle`，需要自行换算时可用 `GlassGeometry` 和 `PixelRect`。 |
| 局部、换页与帧末模糊 | `GlassBlur.requestPage()/requestPopup()` 提交帧末效果，`pageSlideNow()` 在当前绘制层执行。直接使用 `GaussBlur.request*()` 时，客户端初始化以 `allowScreen(predicate)` 放行宿主，并在需要效果的帧提交请求。`blurNow()/sampleBackdropNow()/revealNow()` 使用左上为原点的 framebuffer 像素。`deferTopLayer(owner, draw)` 每帧保留最后一次登记，同一前景层可合并到一个回调。 |
| 玻璃 tooltip | `GlassTooltip.render(event[, smoke])` 接收当前平台的 `RenderTooltipEvent.Pre`。宿主决定哪些界面使用玻璃提示，方法成功绘制后取消原事件。 |
| 渐进模糊、贴图与径向渐变 | `VisualReveal.blurRegion()/drawTexture()/drawRadialGradient()` 与 `RevealProfile` 控制模糊半径和清晰区间。已画区域可用 `GlassBlur.revealRegion()`，贴图可独立绘制渐进清晰效果。 |

[玻璃 Java 接口](src/main/java/com/dishanhai/sh_better_ui/client/glass/) · [玻璃 Kotlin 接口](src/main/kotlin/com/dishanhai/sh_better_ui/client/glass/) · [模糊源码](src/main/java/com/dishanhai/sh_better_ui/client/blur/) · [可选效果与渐进模糊](docs/effects.md)

## 原生控件与界面交互

在 `Screen.init()` 注册原生控件。拖拽控制器按实际绘制区域命中，事件返回已消费时结束该次分派。库的客户端事件负责 `UiOverlayHost`、`UiPointerCapture` 与 `UiToast` 的通用调度。

| 用途 | 入口与调用约定 |
| --- | --- |
| 按钮、图标与模式 | `UiButton` 支持 `setSelected()`、`setPreview()`、`setIcon()`、`setLeadingIcon()`、`setModeIndicators()`。液态玻璃按钮可用 `setBackgroundBlurRadius()` 调整 GUI 单位半径。`UiToggleButton.button()` 返回可注册的双态控件，`UiSectionBar.buttons()` 和 `select()` 用于分区页签。 |
| 数值滑块 | `UiSlider` 传入范围、步长、当前值和变更回调，用 `value()/setValue()` 读取或同步数值。 |
| 底线分页 | `controls().underlinedTabs()` 返回单个 `UiUnderlinedTabs`，注册后绘制标签行和当前短文字面板。自定义标签可用 `UiUnderlinedTabs.Tab(label, panel, enabled)`。 |
| 编号步骤器 | `controls().stepper()` 返回单个 `UiStepper`。宿主验证通过后调用 `progress().advance()`，也可传入自己的步骤名称和 `UiStepperState`。 |
| 原页覆层 | 每个宿主持有一个 `UiOverlayHost`，用 `showNotice()/showDialog()/showActions()/showBubble()` 打开提示、确认、动作菜单或跟随控件的气泡。`showDialogFrom()` 从源按钮展开，关闭后归还有效焦点。`UiConfirmation.open()` 也可开启独立的模态 `Screen`。 |
| 局部模态扩展 | 每个 `Screen` 保留一个 `interactions().localModal(screen, preferences)`。从原生 `UiButton` 的回调调用 `openSlide()` 或 `open()`，由前景层绘制并拦截当前轮廓内的输入。 |
| 抽屉、下拉刷新与滑动确认 | `UiBottomSheet` 按 `0..1` 的递增比例定义档位，用最近一次 `render()` 的 `Frame` 调用 `beginCaptured()`。`UiPullRefresh` 用 `requestRefresh()` 或拖拽启动，`createButton()` 提供键盘入口。回执通过 `complete(requestId, tone, nowMs)` 匹配，收到成功且该方法返回 `true` 后应用结果，移除或换数据源时 `reset()`。`UiSlideConfirm` 可直接注册，拖到终点松开后确认，支持键盘和 `reset()`。**工作区新增** `UiBottomSheet.capsule()` 与 `UiSlideConfirm`。 |
| 拖拽、缩放与换页 | `PointerDrag.begin()/move()/release()/cancel()` 维护手势。`UiPointerCapture.start()` 绑定原生 Screen 的拖动、释放与取消。独立按下监听可用 `registerPress()`，持有返回的注册对象并在控件移除时关闭。`ViewportTransform.zoomAt()` 保留指针锚点，`GuiRenderUtil.applyViewport()` 与 `contentX()/contentY()` 对应绘制和命中。`ScrollPageTurn.dragOffset()/targetPage()` 处理拖动边界和滚轮换页。 |
| 逐步显示与撤销 | `UiDisclosure.button()` 提供可聚焦的展开入口。`UiTextDetails.layout()/inlineButton()/pageButton()/page()` 用于长文本截断、原处展开与独立详情。`UiHistory<S>.remember()/restore()` 保存宿主自己的返回或撤销快照。**工作区新增** `UiTextDetails.pageFrom()` 和 `page(..., returnFocus)`。 |
| 任务状态与轻提示 | `ButtonFeedback.tryBegin()/pending()/resolve()` 用请求 ID 匹配回执，`status()/visual()` 提供状态和视觉反馈。`UiDynamicStatus` 用 `Supplier<Snapshot>` 读取当前状态和实际进度。`UiToast.show()/taskCompleted()` 提交无需焦点的 Screen 或 HUD 提示。 |
| 等待与进度 | `UiSkeleton.card()/draw()` 按实际区域绘制占位。`UiLoading.spinner()/edgeSpinner()/topBar()/progressBar()` 支持等待提示或真实进度。**工作区新增** `UiSkeleton.draw(..., opacity)` 淡出重载。 |

[控件与交互源码](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/) · [覆层、抽屉与刷新接入示例](SCREEN_INTERACTIONS.md) · [按钮和注意力反馈](docs/attention-feedback.md)

### 局部模态扩展

将 `UiButton` 注册为画面的普通控件。源按钮在展开时保留在布局和焦点顺序中，其绘制交给前景层。

```java
private final UiLocalModal local = ShBetterUiApi.interactions().localModal(this, UiPreferences.configured());

private void addExpandable(int x, int y, Component actionLabel, Runnable onConfirm) {
    addRenderableWidget(new UiButton(x, y, 64, 22, actionLabel,
            source -> local.openSlide((UiButton) source, new Bounds(0, 0, width, height),
                    UiExpansionController.anchored(180, 22, 0, 0),
                    UiExpansionController.Config.DEFAULT, UiSlideConfirm.LocalConfig.DEFAULT,
                    Component.translatable("sh_better_ui.slide_confirm.label"), onConfirm)));
}
```

- `Placement` 以绝对 GUI 坐标接收源矩形和可用视口。使用 `anchored(width, height, anchorX, anchorY)` 时，锚点可取 `0`（左或上）、`0.5`（中心）、`1`（右或下）。目标在视口内优先保留锚点，空间不足时调整位置和尺寸。滑动条至少需要 20 高和 64 的可滑动距离。
- `UiExpansionController.Config` 可分别指定提升、展开、确认、回弹、收缩的 `Phase(durationMs, easing)`，以及中间轮廓的 `Morph` 和圆角半径。默认时长为 100、220、160、160、220 毫秒。提升和展开同时开始，默认轮廓以浮点 GUI 坐标绘制及命中。减少动效时几何切换立即完成，成功反馈静态显示。
- 轮廓从 `ACTIVATING → EXPANDING → ACTIVE` 展开，确认和取消分别经 `CONFIRMING` 或 `CANCELLING` 进入 `COLLAPSING → IDLE`。展开中按 Esc 从当前轮廓收回。收合期间的重复打开请求返回 `false`。同一画面中，相交的预留区域由先打开的会话占用，互不相交的会话可以共存。
- 滑动条在展开后从滑钮开始拖动，移出轮廓后继续捕获该手势。聚焦源按钮时也可长按右方向键。松开时按最终位置判断阈值，默认拖到终点。 `LocalConfig(threshold, keyTravelMs)` 可设置阈值与键盘行程。确认回调在状态进入 `CONFIRMING` 后执行一次。
- `open()` 接收自定义 `Content` 工厂，内容通过 `Session.confirm()`、`Session.cancel()` 结束交互，并可在 `Content.close()` 释放资源。内容可通过 `animatesContents()` 使用滑动条的交叉淡化。过渡期间按目标坐标绘制，管理器缩放并裁切到当前轮廓。已注册的底层控件保留原有数据，屏幕级路由负责点击、拖动、滚轮和放开。 `UiScreen` 与图库已屏蔽被覆盖的 hover。独立 `Screen` 的自绘组件用 `blocksPointer()` 过滤 hover 坐标，用 `filterPointer()` 过滤轮询快照。
- Forge 与 NeoForge 的 Screen 事件处理关闭、窗口失焦、重布局及源按钮移除。若宿主在同一个 Screen 内切换页面或滚动重建布局，可在切换前调用 `local.interrupt()` 立即中断。

游戏内可从「主控面板 → 开发者面板 → 控件图库 → 局部模态扩展」查看双向展开、区域冲突、覆盖外计数和可复制代码。 [交互实现](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiLocalModal.java) · [画廊示例](src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/LocalModalGalleryPreview.java)

### 底线分页与编号步骤器

在 `Screen.init()` 中分别注册需要的控件。

```java
var controls = ShBetterUiApi.controls();
var tabs = addRenderableWidget(controls.underlinedTabs(tabsBounds, index -> {}));
var steps = addRenderableWidget(controls.stepper(stepperBounds, 0, index -> showStep(index)));
```

- 底线分页默认提供 Overview、Billing、Settings。首项默认选中。聚焦标签栏后，左右键循环切换并跳过停用项。自定义时传入至少一个可用的 `Tab`，初始下标须指向可用项。建议给短文字面板预留至少 44 个 GUI 单位的总高度。
- 步骤器默认提供 Cart、Address、Payment、Review。示例从 Cart 开始。宿主验证当前步骤后可调用 `steps.progress().advance()`，返回 `true` 时再用 `currentIndex()` 更新宿主内容。回看已完成的步骤时，已解锁的后续步骤仍可返回。较窄布局可增加高度，以容纳换行后的步骤名称。
- 下标从 `0` 开始。控件的 `onSelect` 只在键鼠实际切换后触发。宿主可独立使用 `UiTabSelection` 和 `UiStepperState`，也可通过 `tabs.selection()` 或 `steps.progress()` 同步，直接更新状态时由宿主负责同步自己的内容。

### 全屏预览

**工作区新增** `fullscreenPreview()` 创建覆盖游戏 GUI 全区域的预览界面，适合查看图片、物品或详细信息。预览自带顶栏，可用返回按钮或 Esc 关闭。`Content.layout()` 在初始化和尺寸变化时接收 GUI 坐标区域 `viewport`。调用方可以在区域内注册原生控件，也可以返回在控件后方绘制的 `Renderable`。绘制会裁切在 `viewport` 内，预览状态由调用方保存。

下面在调用方的 `Screen` 中居中显示 `stack`，`preferences` 为当前界面偏好。

```java
var preview = ShBetterUiApi.interactions().fullscreenPreview(
        this, Component.translatable("your_mod.preview.title"), preferences,
        (viewport, addWidget) -> (graphics, mouseX, mouseY, partialTick) ->
                graphics.renderItem(stack, viewport.x() + (viewport.width() - 16) / 2,
                        viewport.y() + (viewport.height() - 16) / 2));
Minecraft.getInstance().setScreen(preview); // 客户端线程
```

只需要原生控件时，在 `layout()` 中用 `addWidget.accept(widget)` 注册并返回 `null`。关闭时切回原父 `Screen` 实例，默认恢复创建预览时的焦点。宿主重建控件时，可用带 `Supplier<? extends GuiEventListener> returnFocus` 的重载取得新的焦点入口。`parent` 可为 `null`，此时由 Minecraft 决定返回目标。

开发者面板的控件与材质图库均可通过预览下方的展开图标放大当前示例。用 Esc 或「退出全屏」回到同一个图库示例。底线分页和编号步骤器位于控件图库前两项。需要从自己的界面直接打开内置示例时，可用以下入口。

```java
var settings = ShBetterUiApi.settings();
var sampleIds = settings.developerSampleIds();
Minecraft.getInstance().setScreen(settings.developerSampleFullscreen(this, "underlined_tabs"));
```

`developerSampleIds()` 返回依图库顺序排列的只读 ID 列表，覆盖控件和材质两组。`developerSampleFullscreen()` 直接创建指定示例的交互界面，关闭后回到传入的父页。若父页会重建入口控件，可额外传入 `UiPreferences` 和 `returnFocus` Supplier。内置示例接受原有短 ID，扩展示例使用带命名空间的 ID。通用 `interactions().fullscreenPreview()` 仍用于接入自定义内容。

### 登记开发者预览

在客户端初始化时登记一次。开发者图库和全屏入口会读取同一条目，排序依次使用分类、`order` 和 ID。下例在控件分类显示一个能切换选中状态的按钮，标题和按钮标签由资源包提供。

```java
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.api.preview.PreviewSpec;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiButton;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiStyle;
import net.minecraft.network.chat.Component;

var registration = ShBetterUiApi.client().registerDeveloperPreview(new PreviewSpec(
        "your_mod:sample_button", PreviewSpec.Category.CONTROLS, 100,
        "your_mod.preview.sample_button", () -> host -> {
            var area = host.viewport();
            int width = Math.min(140, area.width() - 12);
            host.add(ShBetterUiApi.controls().button(
                    new Bounds(area.x() + (area.width() - width) / 2, area.y() + 6, width, 22),
                    Component.translatable("gui.done"),
                    pressed -> {
                        var button = (UiButton) pressed;
                        button.setSelected(!button.isSelected());
                    }, host.preferences(), false, UiStyle.DEFAULT));
        }));
```

目录只读取声明。打开条目才创建 `PreviewScene`，缩放和图库内全屏切换会对同一个场景调用 `layout()`。自绘场景从 `render(UiFrame)` 取预览区域、鼠标和帧时间，资源可用 `host.context().manage(...)` 登记。离开条目时释放场景和上下文。需要在同一图库返回条目后恢复设置时，可用 `saveForRemount()` 保存参数。调用 `registration.close()` 撤销条目，正在显示该条目的图库在帧尾切换到可用项。

同一顶层渲染内，`UiFrame.nowMs()` 和 `motion().nowMs()` 共享单调时间。需要等待绘制结束再切屏时，用 `UiFrameScheduler.afterFrame(owner, action)`，动作提交和执行时均核对当前画面。网络请求期限继续按独立的实时单调时间推进。

## 搜索、JEI 与素材选取

| 用途 | 入口与调用约定 |
| --- | --- |
| 本地搜索与联想 | `SearchIndex<T>` 用 `Entry(value, name, ids, aliases)` 建立快照，也可用六参数重载追加 `modName, tags`。`search(query, scope)` 保留来源顺序，`suggest(query, scope, limit)` 返回可填充建议，`Suggestion.suffixFor()` 取灰字补全，`label()` 取候选行文字。数据或语言变化时重建索引。名称和别名支持汉字全拼及首字母匹配。**工作区新增行为** `@模组`、`#tag` 支持筛选与去重联想，填充保留前面的查询；模组 ID 自动取自条目 ID 命名空间，模组名称支持拼音，多词名称使用 `@"名称"`。素材面板自动接入模组与 tag 数据。 |
| 原生搜索输入框 | `SearchField<T>` 继承 `EditBox`。`setIndex()/setScope()` 更新来源和范围，`setResponder()/setOnSubmit()` 接收查询变化和 Enter。普通控件之后调用 `renderSuggestions()`。在 Screen 默认分派前调用 `mouseClickedSuggestion()/mouseScrolledSuggestions()`，Esc 先交给 `dismissSuggestions()`。存在帧末后处理时，可通过 `GaussBlur.deferTopLayer()` 绘制候选。 |
| 嵌入素材搜索与配方预览 | `JeiSearchPanel` 最小为 `144 × 132` GUI 单位。注册 `widgets()`、转发 `tick()` 和候选输入，按 `renderBackground()` → Screen 控件 → `renderOverlays()` 绘制。`snapshot()/restore()` 用于重建面板，`setOnChoose()` 在明确选用时回传素材。候选显示时可通过 `hasOpenSuggestions()` 暂停宿主底层 tooltip。`JeiIntegration.setExtraAreas(screen, panel::extraAreas)` 为 JEI 侧栏留出避让区，永久移除时传 `null`。NeoForge 滚轮转发垂直量 `scrollY`。 |
| 素材来源与筛选 | `IngredientCatalog.jei()` 取可见 JEI 素材，`registry()` 取物品默认形态及源流体，`automatic()` 优先 JEI 并可回退原生注册表，`of(candidates)` 接收自建列表。`JeiSearchPanel` 默认用 `jei()`，需要无 JEI 回退时传入 `automatic()`。`IngredientFilter.all()/items()/fluids()` 可叠加 `withKinds()/withIds()/withMods()/withTags()`，同组任一命中，跨组全部匹配。 |
| 单选、多选与写回 | `IngredientPicker.Request.single()/multiple()` 配置来源与上限，`IngredientPicker.open(parent, request, restoreFocus, onConfirm)` 在确认并回到父界面后返回不可修改列表。空列表可用于清除，取消放弃草稿。自绘页面可用 `IngredientSelection` 管理草稿。`IngredientValues.item()/fluid()` 包装原生 stack，`Ingredient.copy(type)` 取副本并保留数量及 NBT／数据组件。运行期以 `Ingredient.key()` 区分变体，持久化保存 stack 数据。 |
| 直接查 JEI | `JeiIntegration.isAvailable()/revision()/ingredients()` 用于获取当前世界的素材快照，`recipes(ingredient, direction, limit)` 和 `open()` 用于查阅取得方式或用途。无 JEI 时，查询返回空列表，打开返回 `false`。 |

[搜索源码](src/main/java/com/dishanhai/sh_better_ui/client/gui/search/) · [素材筛选](src/main/java/com/dishanhai/sh_better_ui/client/gui/filter/IngredientFilter.java) · [选取器与素材来源](src/main/java/com/dishanhai/sh_better_ui/client/gui/selection/) · [JEI 接口](src/main/java/com/dishanhai/sh_better_ui/client/jei/JeiIntegration.java)

## 图标、文字与布局

| 用途 | 入口与调用约定 |
| --- | --- |
| 图标与动效 | `GraphicIcon` 提供内置 16×16 图标，也可传自定义贴图。`withMotion(GraphicIconMotion)` 配置动作，`withShader(GraphicIconShader)` 配置着色器。自定义着色器由宿主在客户端注册，使用 `POSITION_TEX`。 |
| 图文列表、网格与分页 | `GraphicDisplayLayout.arrange()/pageCapacity()` 计算 `Bounds`、单元格和翻页区域。`GraphicDisplay.drawRows()/drawRowTooltip()/drawPageNumber()/pageButton()` 负责绘制或创建控件，宿主提供 `Entry` 及页数据。 |
| 自绘排版与文字效果 | `UiLayout.align()/row()/column()` 计算边界，`UiText.drawLeft()/drawCentered()/drawRight()` 在区域内绘制对齐文字。`UiFormattedText.draw()/colorize()` 处理格式化文字，`graphics().colorizeLyricText()` 为 FCS 提供逐字进度色；`UiTextEffects.apply()/parse()` 套用逐字效果，`registerPalette()/registerEffect()` 扩展效果。 |
| 网络风格界面 | `NetworkScreen` 根据 `Tab` 与 `Field / Edit / Status / Toggle` 等行模型建立整页控件。已有页面可独立使用 `NetworkLayout.arrange()`、`NetworkTheme` 与 `NetworkToggle`。表单内容、权限判断和提交仍由宿主提供。 |

[图形和布局源码](src/main/java/com/dishanhai/sh_better_ui/client/gui/display/) · [格式化文字源码](src/main/java/com/dishanhai/sh_better_ui/client/gui/text/) · [网络风格控件](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/NetworkTheme.java)

## 动画与画面过渡

| 用途 | 入口与调用约定 |
| --- | --- |
| 时间、弹簧与连续反馈 | `PageTransition.advance()/progress()` 按阶段推进。`InterruptibleAnimation.transitionTo()/blendTo()/sample()` 提供属性级打断、优先级与一次性完成事件，`UiApiContext.animation()` 随页面释放。`Spring`、`SpringTable<K>`、`GuiMotion`、`GlassMotion`、`RepeatCurve` 提供弹簧、插值与长按节奏。 |
| 异步状态同步 | `UiApiContext.query()` 提供随页面释放的最新查询槽，`ShBetterUiApi.client().operation()` 提供由业务模型持有的单次操作槽。回执经客户端线程验证 UUID 与连接，超时或断线后可按服务端记录对账，模型处理回执后调用 `acknowledgeReceipt()`。 |
| 页签提示与局部效果 | `UiAttention.select()/sample()` 提供选区移动和目标提示。`ClickRipple.trigger()/sample()` 配合 `GlassEffectRenderer.drawRipple()` 绘制点击折射，`drawFlowBorder()/drawBreathingGlow()` 提供边框与聚焦效果。`GlassEffectMotion` 和 `RoundedCorners` 可供自绘复用。 |
| 单页和双页切换 | `UiPageSlide.start()/sample()/draw()` 用于四方向单页进入，可叠加立体倾斜和模糊。`UiRecipePageTransition.start()/sample()/draw()` 用于新旧页衔接。交互命中使用绘制时同一帧的新页位移。 |
| 胶囊展开与返回 | `UiCapsuleReveal.startFrom()/startAt()` 记录起点，`render()` 绘制展开，`close(preferences, onClosed)` 收回后执行一次返回动作。`UiScreenReturn.prepare()/render()` 用现有父 Screen 实例预绘返回层，父界面可实现 `Target.prepareReturnLayer()`。`FluidMorph.seed()/between()` 计算小胶囊起点和变形轮廓。**工作区新增** `UiCapsuleReveal`、`UiScreenReturn` 与公开的 `FluidMorph`。 |
| 旧页淡出 | **工作区新增** `ShBetterUiApi.motion().screenCrossfade(owner, previous, preferences)` 在切换前尝试捕获旧页。新页通过 `renderCrossfade()` 用自己的进度合成背景，切换完成或页面移除时调用 `clearHandoff()`。 |
| 帧时间轴 | **工作区新增** `UiTimeline(fps, durationFrames)` 提供 `play()/pause()/seek()/reset()/sample()`，`Frame.interpolate()` 根据当前帧计算属性，`Sequence` 用于判断子区间。每帧取一个快照供同次绘制复用，减少动效由宿主决定如何跳帧。 |
| 点阵背景 | **工作区新增** `UiDotMatrix.render()/pulse()` 绘制背景并接收波纹触发。`DotMatrixProfile` 配置色板、网格、渐变、淡出、运动与互动。先画点阵，再画原生控件。 |
| 电光轮廓 | **工作区新增** `ElectricLogo` 以图片轮廓绘制电丝与电弧，`setTexture()` 切换贴图，`draw()` 接受 `ElectricLogoProfile`，`pulse()` 接收宿主点击。每个视图持有独立实例，`draw()` 返回 `false` 时可画后备内容，移除视图时在渲染线程 `close()`。 |

`UiCapsuleReveal.setSkeletonLayout(target -> blocks)` 适用于 `render()` 和 `renderInset()`。占位块使用与正文一致的 Screen 坐标。回调会读取当前目标区域，适合随窗口尺寸和控件布局变化重新计算。正文开始淡入后，占位块与正文共用当前轮廓的裁切区。

### 旧页淡出

在 `previous` 仍是当前屏幕时创建交接，由新页持有返回的 `UiScreenReturn`。每帧传入当前动画的进度，在正文和控件之前合成底层。下面的 `owner` 是新页，`drawBackground` 绘制覆盖视口的目标背景。

```java
// 在切换 Screen 前捕获当前画面。
var transition = ShBetterUiApi.motion().screenCrossfade(owner, previous, preferences);

// 新页在过渡期间绘制。此例在动画走到 65% 时完成旧页淡出。
transition.renderCrossfade(previous, graphics, partialTick, preferences, progress, 0.65f, drawBackground);

// 本次过渡结束后及 Screen.removed() 时释放快照。
transition.clearHandoff();
```

进度从 `0` 到 `1`，允许中途反向。`fadeEnd` 取值 `(0, 1]`。省略时，有父页的胶囊过渡会在正文出现前完成淡出，从游戏进入时背景随完整进度淡入。快照不可用时，库会预绘旧页并给 `drawBackground` 设置当前帧的透明度，自定义着色器需遵循该透明度。减少动效时直接绘制目标背景。KubeJS 可通过 `SHUI.motion().screenCrossfade(...)` 调用同一入口。[淡出实现](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiScreenReturn.java)

点阵跟随波纹通过 `profile.withPointerWave(new DotMatrixProfile.PointerWave(radius, lift))` 单独开启，半径和抬升高度均为 GUI 单位。每帧把同一坐标系下的指针坐标传给 `UiDotMatrix.render()`。需要独立采集时，可复用 `UiMouseState.read()` 的 `x()/y()`。指针不可用或被模态层遮挡时传入 `Double.NaN`。减少动效时保留环形高亮，半径设为零时关闭。

### 点阵光标透镜

每个区域持有一个 `UiDotMatrix` 和一个 `UiPointerTracker`。下面是规则彩虹点阵的接入示例。

```java
private final UiDotMatrix dots = ShBetterUiApi.graphics().dotMatrix(DotMatrixProfile.VIOLET
        .withGrid(new DotMatrixProfile.Grid(8, 2, 1f, 27L))
        .withMotion(new DotMatrixProfile.Motion(DotMatrixProfile.Effect.TWINKLE, 2800, 0f, 0, 0f))
        .withInteraction(new DotMatrixProfile.Interaction(0, 0))
        .withGradient(DotMatrixProfile.Gradient.RAINBOW)
        .withPointerLens(DotMatrixProfile.PointerLens.DEFAULT));
private final UiPointerTracker pointer = ShBetterUiApi.interactions().pointerTracker();

void drawDots(GuiGraphics graphics, Bounds area, UiPreferences preferences, boolean acceptsPointer) {
    long now = PageTransition.nowMs();
    UiMouseState mouse = acceptsPointer ? ShBetterUiApi.interactions().mouseState() : null;
    boolean active = mouse != null && mouse.focused() && !mouse.grabbed() && !preferences.highContrast();
    var input = pointer.update(area, graphics.pose().last().pose(), mouse, active, now);
    dots.render(graphics, area, active ? input : null, now, preferences);
}
```

`PointerLens(radius, bulgeStrength, twistDegrees, glowRadius, glowArgb, glowIntensity, holdToZoom)` 配置透镜半径、径向强度、旋转角度、柔光半径、ARGB 柔光颜色与强度，以及左键长按是否加深效果。半径使用 GUI 单位，鼓胀强度为 −1 到 1，负值向内收缩。透镜半径为零时关闭整项效果，柔光半径为零时关闭柔光。正常移出传入区域外的帧以淡出，失焦、关闭交互或被模态层遮挡时传入 `null`。减少动效时仅保留静态柔光。绘制放在前景控件之前，指针追踪不消耗鼠标事件。

[动效源码](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/) · [可打断动画](docs/interruptible-animation.md) · [电光轮廓源码](src/main/java/com/dishanhai/sh_better_ui/client/gui/display/ElectricLogo.java)

### 反重力粒子

`ShBetterUiApi.graphics().antigravity()` 或 `antigravity(profile)` 为每个区域创建独立粒子场。不可变 `AntigravityProfile` 配置形状、色板、吸附、波浪、旋转、蓄力与入场；`ShBetterUiApi.interactions().pointerTracker()` 单独提供 pose 变换后的区域指针与按压／释放／取消状态。开发者控件图库提供四种形状、三种配色、暂停与重播。[完整接入说明](docs/antigravity-api.md)。

### 光束背景

每个 GUI 区域保留一个 `LightRays` 实例。在底板之后、文字和控件之前绘制。

```java
// Screen 字段
private final LightRays rays = ShBetterUiApi.graphics().lightRays(LightRaysProfile.builder()
        .raysOrigin(LightRaysProfile.Origin.TOP_CENTER)
        .raysColor(0xFFE4F0FF)
        .lightSpread(.5f)
        .build());

// Screen.render 内
rays.draw(graphics, ShBetterUiApi.graphics().bounds(16, 16, 240, 120));
```

- `LightRaysProfile` 提供八个光源位置。颜色使用 ARGB，长度和淡出距离以区域长边为单位，正倾角在 GUI 坐标中顺时针旋转。`lightMode(true)` 适用于浅色底板。
- `raysSpeed(0)` 冻结光束相位。`followMouse`、`cursorShadow` 和 `clickPulse` 分别控制跟随、阴影和点击波。宿主可用 `pulse()` 接入键盘动作。
- 需要共享快照或屏蔽被模态层遮住的区域时，调用 `draw(graphics, bounds, mouse, preferences)`。传 `null` 屏蔽指针。`ShBetterUiApi.interactions().pointerTracker()` 可独立用于区域按压和取消检测。
- `suspend()` 清除指针和点击波并保留入场进度，`restart()` 重播。减少动效时显示静态光束。高对比或着色器不可用时 `draw()` 返回 `false`，宿主可绘制底板。

[光束源码](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/LightRays.java) · [配置源码](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/LightRaysProfile.java) · [效果参考](https://reactbits.dev/backgrounds/light-rays)

### 激光流背景

每个 GUI 区域持有一个 `LaserFlow` 实例。先绘制底板和受光面的底色，再调用 `draw`，最后绘制文字和控件。

```java
// Screen 字段
private final LaserFlow laser = ShBetterUiApi.graphics().laserFlow(LaserFlowProfile.builder()
        .beamPosition(.6f).color(0xFF3847FF).intensity(1.8f)
        .beamWidth(1.5f).flare(1.5f).spread(1.5f).spill(1f)
        .fog(.2f).dust(1.6f).build());

// Screen.render 内按当前布局提供区域和受光面
var area = ShBetterUiApi.graphics().bounds(16, 16, 300, 180);
var surface = new LaserFlowSurface(ShBetterUiApi.graphics().bounds(70, 102, 180, 74), 12f);
laser.draw(graphics, area, surface, null);
```

- `LaserFlowProfile` 还可设置流线、点纹、脉动、速度、随机种子、浅色模式、入场和鼠标增强。用 `laser.setProfile(laser.profile().toBuilder().paused(true).build())` 可即时暂停时间轴并保留进度。速度为 0 时呈现完整静态效果，`restart()` 可重播已启用的入场。
- `beamPosition` 从绘制区域左侧开始按 0–1 定位。光束未覆盖受光面的水平范围时，按区域高度比例 `surfaceLevel` 落下。受光面的圆角、区域和指针半径使用 GUI 单位。
- 第四个参数可传 `new LaserFlowReveal(texture, radius, opacity)`。贴图使用完整资源路径并映射到绘制区域，只在指针附近的受光处显现。鼠标增强和贴图揭示可以分别启用。
- 显式传入共享的 `UiMouseState` 和 `UiPreferences` 可管理每层输入。传入空鼠标快照可屏蔽被遮挡区域的交互。减少动效时显示完整静态效果，高对比或着色器不可用时 `draw` 返回 `false`。

### 代码雨与 ASCII 字符场

每个背景区域保留一个 `ShBetterUiApi.graphics().codeRain()` 实例可绘制默认下落代码雨。用 `CodeRainProfile` 设置字符集、单元尺寸、密度、速度、拖尾长度、ARGB 颜色和随机种子。ASCII 模式使用固定网格，可按光强从稀疏到密集选字。

```java
private final CodeRainEffects.Interaction interaction =
        CodeRainEffects.interaction(true, CodeRainEffects.Click.WAVE, .12f);
private final CodeRain rain = ShBetterUiApi.graphics().codeRain(
        CodeRainProfile.builder()
                .mode(CodeRainProfile.Mode.ASCII)
                .characters(" .:=+xX0S8#@")
                .cellSize(10)
                .speed(6)
                .build(),
        CodeRainEffects.shape(CodeRainEffects.Shape.HORIZON, .55f),
        CodeRainEffects.gradient(0xFF1E3FD8, 0xFF38BDF8, 0xFFA5D8FF, 0xFFF6F8FF),
        CodeRainEffects.intro(1.4f),
        CodeRainEffects.flicker(.2f),
        CodeRainEffects.glow(.3f),
        CodeRainEffects.dotMatrix(.25f, .35f),
        interaction);

// 在底板之后、前景控件之前调用。previewBounds 使用当前 GUI pose 的局部坐标。
rain.draw(graphics, font, previewBounds, mouseSnapshot, preferences);
```

- `shape` 支持地平线 `HORIZON`、束腰 `PINCH`、底部上升 `RISE` 和日蚀 `ECLIPSE`。可独立调整软边和流动幅度。`gradient` 按当前亮度为字形采样 2–8 色，所以通常放在形状遮罩之后。
- `interaction` 可独立启用光标乱字、扩散环或局部爆发，半径按区域短边计算。输入复用 `UiMouseState` 与 `UiPointerTracker`，可传 `null` 屏蔽物理鼠标采样。持有 `CodeRainEffects.Interaction` 时可调用 `pulse(localX, localY)` 从宿主动作或键盘触发。
- `dotMatrix(size, coverage)` 把固定比例的单元改为方点。示例将 35% 的单元改为宽度为单元四分之一的亮点。覆盖率设为 `1` 可得到纯点阵代码雨。方点沿用代码雨的颜色、亮度、光晕和交互。
- 自定义 `CodeRainEffect` 可用 lambda 修改单元字形索引、亮度、颜色、发光强度或点宽。`Cell` 坐标是区域内的单元中心，实例会重复使用。需要指针的扩展可覆盖 `usesPointer()`，有状态的扩展应由一个区域独占。
- 高对比模式跳过装饰层并返回 `false`。减少动效冻结字符场、立即完成入场并清除互动。界面离开时调用 `suspend()`，重播时调用 `restart()`。

[代码雨源码](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/CodeRain.java) · [扩展接口](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/CodeRainEffect.java) · [内置扩展](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/CodeRainEffects.java)

### 图案波面与鼠标快照

每个背景区域持有一个 `PatternWaves` 实例。先画波面，再画文字与可操作控件。`Bounds`、图案间距和涟漪半径使用 GUI 逻辑单位。颜色使用 ARGB，`0x00000000` 可让底层透出。

```java
// Screen 字段
private final PatternWaves waves = ShBetterUiApi.motion().patternWaves(PatternWavesProfile.builder(Preset.SILK)
        .backgroundColor(0x00000000)
        .fade(Fade.EDGES)
        .cursorSize(50)
        .cursorStrength(.6f)
        .build());

// Screen.render 内。多个控件可共用本帧的快照。
UiMouseState mouse = ShBetterUiApi.interactions().mouseState();
waves.draw(graphics, new Bounds(16, 16, 240, 120), mouse, ShBetterUiApi.settings().preferences());
```

类型位于 `client.gui.scaled`，`Preset` 和 `Fade` 是 `PatternWavesProfile` 的嵌套枚举，`Bounds` 来自 `client.gui.display.GraphicDisplayLayout`。预设有 `SILK`、`OCEAN`、`POND`、`LINES`、`TERMINAL`、`MESH`。Builder 可覆盖图案、波形、间距、尺寸、深度、光向、亮斑、对比度、速度、尺度、方向、淡出和入场。`GLYPH` 接受最多 64 个 Unicode 码点，按疏到密传入，使用客户端的等宽字形。 [波面配置](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/PatternWavesProfile.java)

`setPaused(true)` 或将速度设为 `0` 会冻结背景波动，鼠标涟漪继续播放。`restart()` 重新播放入场。减少动效时使用静态波面，高对比模式只绘制所配置的底色。省略快照参数的 `draw(graphics, bounds)` 会自动读取鼠标和当前偏好。 [波面入口](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/PatternWaves.java)

`UiMouseState.read()` 可独立使用。`x()/y()` 是带小数的 `Screen` 坐标，`windowX()/windowY()` 是 GLFW 窗口坐标。`isDown(button)`、`buttons()`、`pressedSince(previous)` 与 `releasedSince(previous)` 使用 GLFW 按键编号或对应位掩码，`available()` 判断是否可用于 GUI 命中。两次采样之间的短按可从 `Screen.mouseClicked` 转发给 `waves.mousePressed(x, y, button)`，随后照常分派事件。模态层或自定义裁切挡住背景时，可向 `draw()` 传入 `null` 关闭该区域的鼠标效果。 [鼠标快照](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/UiMouseState.java)

游戏内可从开发者面板进入控件图库，在「电光轮廓」后翻到「图案波纹」。在预览区移动指针或点击可触发尾迹和涟漪。预设按钮循环切换六种样式，暂停只冻结背景波动。预览区获得焦点后，可按回车或空格从中心触发涟漪。 [图库预览](src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/PatternWavesGalleryPreview.java)

### 形状波面

`ShapeWaves` 在同一网格内用三角形、圆形和方形表现波段。可通过 `ShapeWavesProfile.Shapes` 指定混合模式或单一形状。`flow` 用每秒格数设置定向漂移，`speed` 调节波段随时间变化的速度，`glow` 调节单元内的柔光。移动或点击鼠标时，涟漪会改变附近的波段与颜色。

```java
// Screen 字段
private final ShapeWaves shapes = ShBetterUiApi.motion().shapeWaves();

// Screen.init 内，或显示语言发生变化后
shapes.setProfile(ShapeWavesProfile.builder()
        .text(Component.translatable("your_mod.shape_waves.title").getString())
        .shapes(ShapeWavesProfile.Shapes.MIXED)
        .cellSize(10)
        .backgroundColor(0x00000000)
        .build());

// Screen.render 内
shapes.draw(graphics, new Bounds(16, 16, 240, 120), ShBetterUiApi.interactions().mouseState(),
        ShBetterUiApi.settings().preferences());
```

`text` 接受宿主已本地化的文本，`fontFamily` 使用 Java 客户端字体族名，默认 `SansSerif`。长文字会按区域宽度缩放。`replayIntro()` 重新播放从中心向外的入场，`restart()` 同时重置波面和涟漪。`setPaused(true)` 冻结波面，鼠标反馈仍可使用。`draw()` 返回 `false` 时只绘制所配置的底色，或区域为空。 [形状波面入口](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/ShapeWaves.java) · [配置](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/ShapeWavesProfile.java)

## 平台适配与库内入口

| 接入环境 | 平台专属部分 |
| --- | --- |
| Forge LDLib 1.x | 使用 `compat.ldlib`，编译版本 `1.0.33.b`。`GlassPanelTexture` 提供面板与按钮材质工厂，`LdLibOverlays` 将 Widget 接为覆层锚点，`LdLibPullRefreshWidget` 接入拖拽把手与刷新请求。把手布局用父 Widget 的局部坐标，放开区域用绝对 GUI 坐标。 |
| NeoForge LDLib2 | 使用 `LdLib2UiBridge`，编译版本 `2.2.44`。`overlay()` 绑定宿主，`anchor()` 把 `UIElement` 转为覆层锚点，`createPullRefreshHandle()` 生成把手。把手加入布局，同时将 `focusTarget()` 注册到宿主 Screen 的焦点顺序。 |
| 原生类型差异 | `IngredientValues.fluid()` 分别接收 `net.minecraftforge.fluids.FluidStack` 与 `net.neoforged.neoforge.fluids.FluidStack`。`GlassTooltip.render()` 也分别接收对应加载器的事件类型。两端使用相同的 `JeiIntegration` 签名，JEI 与 LDLib 均按需要安装。 |

[Forge LDLib 源码](src/main/kotlin/com/dishanhai/sh_better_ui/compat/ldlib/) · [NeoForge LDLib2 源码](neoforge/src/main/java/com/dishanhai/sh_better_ui/compat/ldlib2/LdLib2UiBridge.java) · [双端覆层与刷新示例](SCREEN_INTERACTIONS.md)

`ControlPanelScreen` 是库自带的设置页，在游戏世界中默认按 F8 打开。`DeveloperPanelScreen` 和图库提供控件与材质演示。`UiClient`、`JeiPluginBridge`、`JeiPlatformTooltip`、`IconShaderRenderer`、`CapsuleRenderer`、`VisualRevealRenderer` 属于库内事件、插件或渲染接线，接入方通过上表的控件和材质入口调用。[主控与演示源码](src/main/java/com/dishanhai/sh_better_ui/client/gui/panel/) · [客户端事件](src/main/java/com/dishanhai/sh_better_ui/client/UiClient.java)

本索引按源码核对入口。GUI 缩放、键盘与焦点、资源重载、GPU 着色器及第三方模组叠层的客户端验收记录见 [TODO](TODO.md) 与 [NeoForge 验证记录](neoforge/VERIFICATION.md)。
