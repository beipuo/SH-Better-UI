<!-- 作者：山海／dishanhai -->
# 虚拟光照

`scene.light(id, descriptor)` 创建或原位更新灯光，`removeLight(id)` 移除灯光。支持环境、方向、点光和聚光。位置、半径和高度使用 GUI 单位，方向与锥体半角使用弧度。系统独立于游戏世界光照。

局部灯光通过区域索引筛选，在节点中心采样。每个场景最多 64 盏灯，每个节点的有效灯光上限随质量等级变化。移动灯光只重建旧范围和新范围内的绘制缓存。方向阴影随有效方向光偏移。

## 跟随鼠标的点光

```java
scene.light(new UiId("example", "cursor_light"),
        new SpatialStyle.Light(SpatialStyle.LightType.POINT, mouseX, mouseY, 24,
                .9, 80, 0xff8ccaff, 0, Math.PI));
```

在 UI 帧阶段提交最新的鼠标位置。可将强度或颜色插值进度绑定为场景动画属性，采样时用 `Light.interpolate` 计算同类型描述符并更新同一灯光 ID。
