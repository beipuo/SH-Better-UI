<!-- 作者：山海／dishanhai -->
# 阴影

`node.shadows(List<Shadow>)` 显式指定至多八层。传 `null` 恢复主题阴影，空列表关闭此节点的阴影。外层支持 `DROP`、`AMBIENT`、`CONTACT`、`DIRECTIONAL` 和 `GLOW`，`INNER` 在表面之后绘制。同一阶段保留列表顺序。

圆角矩形按象限选半径，每个半径限制在短边的一半。圆、椭圆和胶囊共享同一渲染器。椭圆采用归一化距离近似，轮廓命中使用相同的零等值线。高质量衰减使用高斯直边积分近似，低质量使用平滑阶跃。四边形内用导数计算边缘抗锯齿。

输入颜色为 straight ARGB。颜色增强在线性空间计算并编码回 sRGB，再按 Minecraft GUI 的 straight-alpha 混合约定合成。Shader 不可用时走三角化表面，阴影能力为 `DEGRADED`。

`Shadow.interpolate` 在相同类型之间插值偏移、模糊、扩展、透明度和颜色，颜色按端点的 alpha 加权。

## 凹陷输入框

```java
field.material(SpatialStyle.Material.SOFT_INSET);
field.shadows(List.of(new SpatialStyle.Shadow(SpatialStyle.ShadowType.INNER,
        0, 2, 3, 0, .55, 0xff090e19, true)));
```

复杂路径、复合形状和蒙版纹理当前由能力查询报告为 `UNSUPPORTED`。
