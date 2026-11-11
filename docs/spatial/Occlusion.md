<!-- 作者：山海／dishanhai -->
# 遮挡与命中

`clipChildren` 按父节点的局部矩形裁剪子树，支持多级嵌套、旋转、镜像和非等比缩放。视觉裁剪使用实际矩阵，命中裁剪使用命中矩阵。阴影可选择 NODE、PARENT、WINDOW 或 OVERFLOW。WINDOW 与 OVERFLOW 都允许越出父级，最终仍受窗口边界和调用方原生 scissor 限制。

`occlusion(id)` 给出裁剪后的边界多边形，并分别报告可见、裁剪、被单个完全不透明的前景节点覆盖、被模态阻断。覆盖判断是保守检测。`hitTest` 按逆变换检查形状，优先比较命中优先级，再比较绘制顺序。阴影不扩大命中范围。

```java
var input = module.engine().require(UIApis.INPUT);
input.target(module, tree, 0, event ->
        event instanceof NativeInput.Pointer pointer
                && scene.hitTest(pointer.x(), pointer.y()).isPresent());
```

在已注册的内核输入订阅中处理事件。原生捕获、焦点、旁白和事件冒泡沿用现有服务与权限规则。使用 `hitTransparency(VISIBLE_SURFACE)` 可让透明表面不参与命中，默认保留透明交互区域。
