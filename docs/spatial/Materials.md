<!-- 作者：山海／dishanhai -->
# 材质

`Material` 包含基础 ARGB、透明度、粗糙度、高光、边缘高光、表面深度和背景模糊半径。库提供 `FLAT`、`SOFT_RAISED`、`SOFT_INSET`、`GLASS`、`METALLIC` 和 `HOLOGRAPHIC` 作为参数预设，使用同一个表面 Shader。

## 玻璃面板

```java
panel.material(SpatialStyle.Material.GLASS);
panel.elevation(SpatialStyle.Elevation.OVERLAY);
```

模糊在绘制该表面之前复用现有 `GlassBlur` 取样主帧缓冲。每次场景提交至多尝试一次背景采样，先出现的合格玻璃节点获得采样。适用几何为等比正向缩放、无旋转和无父级裁剪的矩形或等圆角矩形。质量为 LOW、用户关闭模糊或背景采样不可用时，仍绘制半透明表面。
