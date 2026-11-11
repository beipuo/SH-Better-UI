<!-- 作者：山海／dishanhai -->
# 帧时间轴

Remotion 用 React 描述视频每一帧的内容。把它理解成翻页动画：第几页（当前帧）决定物体在哪里、显示多少透明度，以及哪些字幕出现。跳回某帧即可还原画面，不需要先播放前面的动画。参考 [Remotion 核心说明](https://www.remotion.dev/docs/idea) 与 [Sequence](https://www.remotion.dev/docs/sequence)。

SH-Better-UI 提供对应的原生 Java 帧动画 API `UiTimeline`。它不运行 React，也不导出视频。

```java
private final UiTimeline timeline = new UiTimeline(30, 120);
private final UiTimeline.Sequence caption = new UiTimeline.Sequence(30, 90);

// 用户操作
timeline.play(PageTransition.nowMs());
timeline.pause(PageTransition.nowMs());
timeline.seek(45); // 跳到第 45 帧并暂停
timeline.reset();  // 就绪，第 0 帧

// 每次绘制只取一个快照，再用它计算所有属性
var frame = timeline.sample(PageTransition.nowMs());
float x = frame.interpolate(0, 90, 0f, 100f);
float opacity = frame.interpolate(0, 20, 0f, 1f);
if (caption.contains(frame)) {
    int localFrame = caption.localFrame(frame); // 全局 30 对应局部 0
}
```

`30` 是时间轴 FPS，不是客户端实际渲染速度；`120` 为总帧数，可取 0–119。抵达末帧停止，播放可重新开始。插值区间外固定在两端值，序列末端不包含在内。暂停保留不足一帧的时间，重复取样不加速。屏幕移除时调用 `pause`。

开发者面板 → 控件图库 → **帧时间轴 · Remotion 思路**：播放／暂停、拖动进度或用原生滑杆键盘操作查看任意帧、重置。底部代码抽屉可复制接入示例。减少动效时播放立即到末帧，仍可手动查看其他帧。小窗口优先保留进度和状态按钮，空间足够时显示概念说明与局部帧字幕。

Forge 与 NeoForge 共用同一实现。离线预览是实际 `UiTimeline` 取样结果的 Java2D 示意，游戏 GPU、字体、焦点与 GUI 缩放须在客户端分别验收。

离线预览源位于 `src_collection/previews/`。使用带 Pillow 的 Python 运行 `frame_timeline_preview.py`，脚本会先以 JDK 21 编译真实 `UiTimeline` 和采样程序，再生成 `docs/previews/2026-11-08-frame-timeline.png`。此图明确标注为离线预览。

## 返回与切页

`UiCapsuleReveal.close(preferences, onClosed)` 从当前轮廓收回，完成后仅调用一次返回动作；减少动效时立即完成。连续切页仅接续展开，不再先缩回 48% 再等待重开。

`UiScreenReturn` 在收回过程中准备并绘制上一 `Screen`。主控和开发者面板实现 `UiScreenReturn.Target`，使用自己的胶囊／骨架和真实布局承接返回；普通父界面按可见控件位置绘制骨架并淡出。保持父实例及草稿，后层收到的指针为 `(-1,-1)`，不向父界面转发操作。最终返回后恢复入口焦点；返回世界时不额外加黑底。

最终返回合成尚未完成实机验收：本轮用户通过 Escape 停止了窗口操作，之后没有继续操作客户端。
