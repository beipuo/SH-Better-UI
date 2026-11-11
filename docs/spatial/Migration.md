<!-- 作者：山海／dishanhai -->
# 接入与迁移

既有 `GraphicsApi`、`BlurApi`、`AnimationService` 和主题控件仍可正常使用。逐个把需要阴影或受光的自定义组件接入场景。原有控件继续负责布局、交互、焦点与旁白。

1. 用 `engine.find(SpatialUI.KEY)` 检查可选能力。库版本缺失时保留原绘制路径。
2. 通过 `ScreenService` 获得原生挂载作用域。场景与渲染任务使用同一个作用域。
3. 把原布局结果传入节点位置和形状。使用旧控件层级映射 `z`，独立设置输入优先级。
4. 把自定义 Shader 和阴影绘制替换为 `Material`、`Shadow` 和 `RenderService.Context.spatial`。
5. 保持现有输入订阅和键盘焦点。需要时用 `scene.hitTest` 做指针命中，视觉动效用 `visualTransform` 保持命中稳定。

API 与能力契约当前为第 1 版。消费方应依赖对应平台的库产物，并将库作为独立模组安装。
