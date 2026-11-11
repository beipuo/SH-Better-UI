<!-- 作者：山海／dishanhai -->
# 反重力粒子 API

将 [React Bits Antigravity](https://reactbits.dev/backgrounds/antigravity) 示例适配为 Minecraft GUI API，Forge 1.20.1 与 NeoForge 1.21.1 共用配置和仿真。

## 接入

每个区域保留一个实例，在客户端 GUI 渲染回调调用：

```java
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.gui.display.GraphicDisplayLayout.Bounds;
import com.dishanhai.sh_better_ui.client.gui.scaled.Antigravity;
import com.dishanhai.sh_better_ui.client.gui.scaled.AntigravityProfile;

private final Antigravity particles = ShBetterUiApi.graphics().antigravity(
        AntigravityProfile.builder().count(1500).magnetRadius(9).ringRadius(7)
                .colors(java.util.List.of(0xFF5227FF, 0xFFFF9FFC, 0xFFB497CF)).build());

private void drawParticles(net.minecraft.client.gui.GuiGraphics graphics, Bounds area, boolean inputAllowed) {
    var mouse = inputAllowed ? ShBetterUiApi.interactions().mouseState() : null;
    particles.draw(graphics, area, mouse, ShBetterUiApi.settings().preferences());
}

@Override
public void removed() {
    particles.suspend();
    super.removed();
}
```

先绘制底板，再绘制粒子，最后绘制前景控件。背景透明。draw 返回 false 表示着色器不可用或高对比降级，宿主可保留底板或绘制本地化提示。实例不持有私有 GPU 资源，着色器由资源重载管理。

鼠标快照不消费事件。区域内有效左键按住蓄力，区域内释放爆散；离开、失焦、鼠标被捕获、输入被模态层遮挡时传 null 均取消蓄力，不爆散。首次进入时已按住的左键不会被接管。两帧之间的极短点击可能无法采到；键盘或宿主动作可调用 burst(1)。

不再绘制、关闭或重建布局时调用 suspend()，清除按压并保留粒子位置。setPaused(true/false) 暂停／继续；restart() 重新入场并恢复播放。

## 组成 API

- AntigravityProfile：不可变配置，通过 builder() 创建、toBuilder() 修改；颜色使用 ARGB，color(argb) 覆盖色板。
- ShBetterUiApi.interactions().mouseState()：共享鼠标快照。
- ShBetterUiApi.interactions().pointerTracker()：独立 UiPointerTracker，update 接受 Bounds、当前 Matrix4fc pose、鼠标快照、enabled 和时间；返回局部坐标、按下、按住、释放、取消与静止时长，可用于其他自绘控件。
- ShBetterUiApi.graphics().antigravity(profile)：组合配置、输入、仿真和绘制，各区域状态独立。

## 参数

距离及大小沿用示例世界单位，以区域高度 / 31.5 换算为 GUI 单位。粒子数限制为 1–20,000，最多 256 色；拒绝非有限参数，超范围的有限值限幅。

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| count / colors / color(argb) | 1500 / 紫粉紫 | 数量、色板或单色覆盖 |
| particleShape | CAPSULE | CAPSULE、SPHERE、BOX、TETRAHEDRON（投影三角形） |
| colorMode / colorSpeed | CONIC / 0.05 | CONIC、RADIAL、RANDOM；环形色板转速 |
| particleSize / particleVariance | 0.9 / 1 | 大小与脉动幅度 |
| magnetRadius / ringRadius / fieldStrength | 9 / 7 / 10 | 捕获范围、环半径、环带紧密度 |
| waveAmplitude / waveCount / waveSpeed | 1 / 3 / 0.4 | 环面波浪 |
| rotationSpeed / pulseSpeed / depthFactor | 0 / 3 / 1 | 轨道旋转、脉动频率、深度视差 |
| ambient / lerpSpeed / opacity | 0.25 / 0.05 / 1 | 环外可见度、跟随速度、透明度 |
| autoAnimate / interactive / clickBurst / intro | true | 空闲漂移、交互、蓄力爆散、入场 |
| lightMode | false | 调低淡色的 Oklab 明度，适配浅色背景 |

浏览器 className、style、maxDevicePixelRatio 由 GUI 区域、pose 与 Minecraft 渲染分辨率替代。所有粒子合成一个批次；局部区域精确裁切，沿用宿主 GUI scissor 与颜色调制。

减少动效时固定中央静态环，停止漂移、旋转、脉动、入场和爆散；高对比模式隐藏装饰粒子。

## 开发者预览

F8 主控面板 → 开发配置 → 开发者面板 → 控件图库 → 反重力粒子。可切换四种形状、三种配色，暂停／继续和重播，复制 Java 接入示例。画布支持鼠标蓄力／释放及聚焦后回车／空格爆散，控件沿用键盘和旁白操作。

游戏内验收条目见 TODO.md；离线 shader 预览不能替代真实客户端验收。

## 本次验证（2026-11-08）

- Forge 完整 build：528 项 JUnit 通过；NeoForge 完整 build：530 项通过，两端失败、错误和跳过均为零。本功能新增 31 项测试／平台。两个库 JAR 的 API、图库预览和 shader 资源已核对。
- 使用真实 Java 粒子状态与色板、真实 GLSL 在 RTX 4060 离屏绘制四形状；通过胶囊厚度、软边透明度、ColorModulator 和非等比缩放／旋转／平移后的局部裁切检查，GL_NO_ERROR。
- 预览制品：build/previews/antigravity.png。真实客户端的缩放、旁白、资源重载和帧率仍需按 TODO.md 验收。

## 文件与位置

| 文件 | 位置与内容 |
| --- | --- |
| Antigravity.java / AntigravityProfile.java | client/gui/scaled：组件状态与不可变参数 |
| AntigravityField.java / AntigravityPalette.java | client/gui/scaled：确定性仿真、蓄力爆散与 Oklab 色板 |
| UiPointerTracker.java | client/gui/scaled：可复用局部坐标与输入状态 |
| AntigravityRenderer.java / shaders/core/antigravity.* | 批量顶点、四形状 SDF、透明合成与裁切 |
| 两平台 VisualRevealRenderer.java | 资源重载时注册 shader，适配顶点写入 |
| GraphicsApi.java / InteractionApi.java | 总入口中的粒子场和指针跟踪工厂 |
| AntigravityGalleryPreview.java / DeveloperGalleryScreen.java / DeveloperSamples.java | 开发者控件图库预览、导航和可复制示例 |
| lang/zh_cn.json / lang/en_us.json | 预览名称、操作和旁白提示 |
| Antigravity*Test.java / UiPointerTrackerTest.java | 配置、输入、仿真、配色及总入口行为测试 |
| README.md / API_REFERENCE.md / TODO.md | 调用索引与游戏内验收待办 |
