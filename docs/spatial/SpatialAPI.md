<!-- 作者：山海／dishanhai -->
# SpatialUI 公共 API

`SpatialUI.KEY` 的契约版本为 1。用 `engine.find(SpatialUI.KEY)` 发现可选依赖，用 `capability` 查询具体视觉特性。操作场景、节点和轨道应在内核 UI 线程执行。

## 普通按钮

下面的 `tree` 是 `ScreenService.mount(module, screen, id)` 返回的作用域。代码在模块运行期间执行。

```java
var spatial = module.engine().require(SpatialUI.KEY);
var scene = spatial.mount(module, tree);
var button = scene.create(new SpatialScene.Spec(
        new UiId("example", "button"), null, SpatialGeometry.Shape.rounded(92, 20, 5),
        new SpatialGeometry.Transform(18, 16, 1, 1, 0, 0, 0), 0, 0, true, false));
button.elevation(SpatialStyle.Elevation.RAISED);
var render = module.engine().require(UIApis.RENDER);

// Submit each frame from the owning module's frame hook.
render.submit(module, tree, RenderService.Surface.SCREEN, RenderService.Layer.CONTENT, 0,
        context -> context.spatial(scene));
```

在同一作用域提交绘制。节点 ID 在场景中唯一。父节点先创建，关闭父节点会销毁整个子树。关闭场景或挂载作用域会释放缓存、灯光和动画轨道。模块作用域可用于 HUD。

需要让控件内容夹在节点之间时，使用 `context.spatial(scene, (node, canvas) -> ...)`。回调接收 GUI 坐标系下的绘制上下文，按需使用 `canvas.clip`。自定义内容负责自己的变换、透明度和裁剪。

悬浮卡片、凹陷输入框、玻璃面板、动态光照和可中断动画示例分别见 [Elevation](Elevation.md)、[ShadowEngine](ShadowEngine.md)、[Materials](Materials.md)、[Lighting](Lighting.md) 和 [Animation](Animation.md)。
