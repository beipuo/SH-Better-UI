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
| `graphics()` | 图标、文字、图文布局、网络风格界面和电光轮廓 |
| `motion()` | 时钟、弹簧、可打断动画、切页、胶囊过渡、点阵、图案波面、形状波面与帧时间轴 |
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
| 原页覆层 | 每个宿主持有一个 `UiOverlayHost`，用 `showNotice()/showDialog()/showActions()/showBubble()` 打开提示、确认、动作菜单或跟随控件的气泡。`showDialogFrom()` 从源按钮展开，关闭后归还有效焦点。`UiConfirmation.open()` 也可开启独立的模态 `Screen`。 |
| 抽屉、下拉刷新与滑动确认 | `UiBottomSheet` 按 `0..1` 的递增比例定义档位，用最近一次 `render()` 的 `Frame` 调用 `beginCaptured()`。`UiPullRefresh` 用 `requestRefresh()` 或拖拽启动，`createButton()` 提供键盘入口。回执通过 `complete(requestId, tone, nowMs)` 匹配，收到成功且该方法返回 `true` 后应用结果，移除或换数据源时 `reset()`。`UiSlideConfirm` 可直接注册，拖到终点松开后确认，支持键盘和 `reset()`。**工作区新增** `UiBottomSheet.capsule()` 与 `UiSlideConfirm`。 |
| 拖拽、缩放与换页 | `PointerDrag.begin()/move()/release()/cancel()` 维护手势。`UiPointerCapture.start()` 绑定原生 Screen 的拖动、释放与取消。独立按下监听可用 `registerPress()`，持有返回的注册对象并在控件移除时关闭。`ViewportTransform.zoomAt()` 保留指针锚点，`GuiRenderUtil.applyViewport()` 与 `contentX()/contentY()` 对应绘制和命中。`ScrollPageTurn.dragOffset()/targetPage()` 处理拖动边界和滚轮换页。 |
| 逐步显示与撤销 | `UiDisclosure.button()` 提供可聚焦的展开入口。`UiTextDetails.layout()/inlineButton()/pageButton()/page()` 用于长文本截断、原处展开与独立详情。`UiHistory<S>.remember()/restore()` 保存宿主自己的返回或撤销快照。**工作区新增** `UiTextDetails.pageFrom()` 和 `page(..., returnFocus)`。 |
| 任务状态与轻提示 | `ButtonFeedback.tryBegin()/pending()/resolve()` 用请求 ID 匹配回执，`status()/visual()` 提供状态和视觉反馈。`UiDynamicStatus` 用 `Supplier<Snapshot>` 读取当前状态和实际进度。`UiToast.show()/taskCompleted()` 提交无需焦点的 Screen 或 HUD 提示。 |
| 等待与进度 | `UiSkeleton.card()/draw()` 按实际区域绘制占位。`UiLoading.spinner()/edgeSpinner()/topBar()/progressBar()` 支持等待提示或真实进度。**工作区新增** `UiSkeleton.draw(..., opacity)` 淡出重载。 |

[控件与交互源码](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/) · [覆层、抽屉与刷新接入示例](SCREEN_INTERACTIONS.md) · [按钮和注意力反馈](docs/attention-feedback.md)

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

## 搜索、JEI 与素材选取

| 用途 | 入口与调用约定 |
| --- | --- |
| 本地搜索与联想 | `SearchIndex<T>` 用 `Entry(value, name, ids, aliases)` 建立快照。`search(query, scope)` 保留来源顺序，`suggest(query, scope, limit)` 返回可填充建议，`Suggestion.suffixFor()` 取灰字补全。数据或语言变化时重建索引。**工作区新增行为** 名称和别名支持汉字全拼及首字母匹配，素材面板的模组筛选也支持拼音。 |
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
| 自绘排版与文字效果 | `UiLayout.align()/row()/column()` 计算边界，`UiText.drawLeft()/drawCentered()/drawRight()` 在区域内绘制对齐文字。`UiFormattedText.draw()/colorize()` 处理格式化文字；`UiTextEffects.apply()/parse()` 套用逐字效果，`registerPalette()/registerEffect()` 扩展效果。 |
| 网络风格界面 | `NetworkScreen` 根据 `Tab` 与 `Field / Edit / Status / Toggle` 等行模型建立整页控件。已有页面可独立使用 `NetworkLayout.arrange()`、`NetworkTheme` 与 `NetworkToggle`。表单内容、权限判断和提交仍由宿主提供。 |

[图形和布局源码](src/main/java/com/dishanhai/sh_better_ui/client/gui/display/) · [格式化文字源码](src/main/java/com/dishanhai/sh_better_ui/client/gui/text/) · [网络风格控件](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/NetworkTheme.java)

## 动画与画面过渡

| 用途 | 入口与调用约定 |
| --- | --- |
| 时间、弹簧与连续反馈 | `PageTransition.advance()/progress()` 按阶段推进。`InterruptibleAnimation.animateTo()/animateToAfter()/sample()` 可从当前轨迹接续或换向。`Spring`、`SpringTable<K>`、`GuiMotion`、`GlassMotion`、`RepeatCurve` 提供底层弹簧、插值与长按节奏。**工作区新增** `Spring.FLUID_PANEL` 预设。 |
| 页签提示与局部效果 | `UiAttention.select()/sample()` 提供选区移动和目标提示。`ClickRipple.trigger()/sample()` 配合 `GlassEffectRenderer.drawRipple()` 绘制点击折射，`drawFlowBorder()/drawBreathingGlow()` 提供边框与聚焦效果。`GlassEffectMotion` 和 `RoundedCorners` 可供自绘复用。 |
| 单页和双页切换 | `UiPageSlide.start()/sample()/draw()` 用于四方向单页进入，可叠加立体倾斜和模糊。`UiRecipePageTransition.start()/sample()/draw()` 用于新旧页衔接。交互命中使用绘制时同一帧的新页位移。 |
| 胶囊展开与返回 | `UiCapsuleReveal.startFrom()/startAt()` 记录起点，`render()` 绘制展开，`close(preferences, onClosed)` 收回后执行一次返回动作。`UiScreenReturn.prepare()/render()` 用现有父 Screen 实例预绘返回层，父界面可实现 `Target.prepareReturnLayer()`。`FluidMorph.seed()/between()` 计算小胶囊起点和变形轮廓。**工作区新增** `UiCapsuleReveal`、`UiScreenReturn` 与公开的 `FluidMorph`。 |
| 帧时间轴 | **工作区新增** `UiTimeline(fps, durationFrames)` 提供 `play()/pause()/seek()/reset()/sample()`，`Frame.interpolate()` 根据当前帧计算属性，`Sequence` 用于判断子区间。每帧取一个快照供同次绘制复用，减少动效由宿主决定如何跳帧。 |
| 点阵背景 | **工作区新增** `UiDotMatrix.render()/pulse()` 绘制背景并接收波纹触发。`DotMatrixProfile` 配置色板、网格、渐变、淡出、运动与互动。先画点阵，再画原生控件。 |
| 电光轮廓 | **工作区新增** `ElectricLogo` 以图片轮廓绘制电丝与电弧，`setTexture()` 切换贴图，`draw()` 接受 `ElectricLogoProfile`，`pulse()` 接收宿主点击。每个视图持有独立实例，`draw()` 返回 `false` 时可画后备内容，移除视图时在渲染线程 `close()`。 |

[动效源码](src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/) · [可打断动画](docs/interruptible-animation.md) · [电光轮廓源码](src/main/java/com/dishanhai/sh_better_ui/client/gui/display/ElectricLogo.java)

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
