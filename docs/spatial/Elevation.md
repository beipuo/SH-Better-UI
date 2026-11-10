<!-- 作者：山海／dishanhai -->
# 视觉高度

`Elevation` 默认高度为 0、2、4、8、16、24 GUI 单位，依次对应 `SURFACE`、`RAISED`、`FLOATING`、`OVERLAY`、`MODAL`、`DRAGGING`。高度控制阴影和受光采样，`Spec.z` 控制绘制，`Spec.hitPriority` 控制命中，`scene.modal` 控制场景内的模态范围。

`Theme.heights` 可以覆盖高度令牌，`Theme.curve` 可以传入线性、分段、缓动或自定义曲线，`Theme.shadows` 接收曲线结果并返回各层参数。范围为 0–64。节点设置绝对高度后，直到再次设置令牌才跟随主题令牌变化。

## 悬浮卡片

```java
card.material(SpatialStyle.Material.SOFT_RAISED);
card.elevation(SpatialStyle.Elevation.FLOATING);
```

切换内核主题时，继承默认调色板的场景跟随表面色与阴影色的插值。场景级 `scene.theme(theme)` 优先，节点显式材质和阴影优先于场景主题。
