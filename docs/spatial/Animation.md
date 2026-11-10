<!-- 作者：山海／dishanhai -->
# 空间属性动画

`SpatialUI.motion` 将一个标量轨道绑定到节点和属性 ID。传空节点可绑定场景级属性，例如光源强度。setter 可驱动高度、变换、阴影参数、材质或灯光。节点关闭会关闭对应轨道。

```java
var lift = spatial.motion(scene, card, new UiId("example", "lift"), (float) card.elevation(),
        SpatialUI.SpringSpec.INTERACTION, value -> card.elevation(Math.max(0, Math.min(64, value))));
lift.play(8, SpatialUI.MotionPolicy.RETARGET, 0); // Hover.
lift.play(2, SpatialUI.MotionPolicy.RETARGET, 0); // Press before hover settles.
```

示例将有回弹的取样值限制在高度范围内。弹簧使用现有固定子步积分。RETARGET 继承当前速度，REPLACE 使用 90 ms 短缓动，BLEND 使用默认平滑混合，QUEUE 排队，IGNORE 拒绝同优先级的新请求。另有 `tween` 支持线性和缓入缓出。完成标记按代次领取，取消或替换后旧代次失效。

保持拖动跟手时，按下或拖动直接更新位置。释放时对弹簧重定向。装饰变换用 `visualTransform` 固定原命中变换。客户端开启减少动效后，轨道取样会直接到达目标，事件路由和焦点仍由内核处理。
