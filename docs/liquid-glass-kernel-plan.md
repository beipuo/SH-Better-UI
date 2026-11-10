<!-- 作者：山海／dishanhai -->
# Unified UI Kernel × Liquid Glass 实施计划

继续使用现有 `UIEngine`、`ClientUiKernel` 和两端构建。每阶段留下可运行的交付和验证记录，GPU 与 Minecraft 资源由客户端拥有。依赖图和设计依据见 [架构审计](liquid-glass-kernel-audit.md)。

以下路径以仓库根目录为准。后续新文件为拟用位置，按每阶段实际改动更新。

| 阶段 | 目标与文件 | 依赖与验收条件 |
| --- | --- | --- |
| 0 · 审计 | `docs/liquid-glass-kernel-audit.md`、本计划。核对工作区、Java 源码集、两端构建与原生测试入口。 | 查阅 Apple 公开设计资料，双端 `kernelTest` 建立基线，区分源码、既有记录和本轮证据。 |
| 1 · 内核 | `kernel/internal/ServiceRegistry.java`、`KernelHost.java`，共享 `LifecycleContractTest`、`NativeBridgeContractTest`。复用 `UnifiedUI`、`APIRegistry`、`ModuleDiscovery`、`DependencyGraph` 和 `UILifecycle`。 | 服务替换者启动前可读已启动的原提供者，失败、停止与依赖隔离后目录仍一致。保留类型、契约、权限和一次性发布的保障。两端跑完整内核契约。 |
| 2 · 渲染基础 | `kernel/internal/ClientRender.java`、`ClientUiKernel.java`、`GlassSceneCapture.java` 和 `GlassDrawState.java`，沿用 `ClientResources` 和两端原生回调。 | 依赖已发布的原生渲染阶段和资源世代。验收共享捕获、屏幕裁剪、GL 状态、尺寸变化、重载、退出与失败后备。两端原生 smoke 检查能力与 FBO。 |
| 3 · 光学 | `kernel/api/glass/`、`UIEngine.java`、`RenderService.java`、`ClientGlassRender.java`、`GlassProgram.java`、`assets/sh_better_ui/shaders/managed_glass.*` 和独立示例。 | 使用 Phase 2 捕获的背景。验证可见透射、圆角与边界折射、边缘聚光、柔影、对比保护以及前景内容顺序。两端真实 Screen 和世界 HUD 截图。 |
| 4 · 动画与交互 | `AnimationService`、`ClientAnimation`、`Spring`、`FluidMorph`、`InputService`、`FocusService`，共享控件与玻璃几何。 | 用同一呈现几何驱动绘制和命中。测试 retarget 保留速度、取消释放捕获、按键焦点、默认行为及减少动效。 |
| 5 · 主题与空间 | `ThemeService`、动态主题 schema、`AccessibilityService`、`GlassTheme`、`LayoutService`、`CollisionService`、Spatial 与 Live Surface 的分层和避让接线。 | 覆盖 token 插值、局部覆盖边界、高对比、降低透明度、裁剪与展开保留空间。核对资源重载及 GUI 缩放。 |
| 6 · 组件库 | `ControlsApi`、`UiButton`、`UiToggleButton`、`UiSlider`、图库及 `examples/kernel-extension`。接入分段控件、工具栏、导航、弹出层、菜单、通知、进度、Live Activity／灵动岛。 | 依赖同一材质、动画和输入。逐个验证原生操作、停用、旁白、返回、关闭和状态还原。消费方以独立 JAR 接入。 |
| 7 · 性能 | `src/kernelBenchmarkClient/`、`gradle/kernel-client-benchmark.init.gradle`、`docs/performance.md` 或独立玻璃测量报告。 | 固定硬件、分辨率和质量。测 1／20／100 控件、多层弹出、持续形变、换主题、缩放、重载和后备路径。记录 CPU/GPU 时间、绘制次数、显存、堆分配、GC、Shader 和 FBO 分配。 |
| 8 · 交付 | `docs/public-api.md`、`docs/platform-compatibility.md`、`examples/kernel-extension/README.md`、`TODO.md`。 | 双端构建、单元／集成、原生 smoke 与截图、可复现性能数据、资源释放与 ABI。标注每个能力的实现和验证状态。 |

## Phase 1 验证步骤

- [x] 在共享 `LifecycleContractTest` 复现提供者初始化前的读取、失败后的恢复，以及运行时停止后的原子切换。
- [x] 在 `NativeBridgeContractTest` 验证被依赖图拒绝的有效模块可在快照中读取 `BLOCKED`，其登记和启动回调均未执行。
- [x] 在 `ServiceRegistry` 保留经过仲裁、同键的前任提供者。只从当前启动视图中选取服务，撤销生命周期范围时一并移除对应记录。
- [x] 在 `KernelHost` 将发现的有效候选和依赖计划对齐，记录被隔离的模块状态。
- [x] 双端执行 `kernelTest test build`。修复前两条用例均失败。修复后 Forge 通过 134 项内核及 841 项客户端单元测试，NeoForge 通过 134 项内核及 843 项客户端单元测试。两端构建和发包边界检查通过。

## Phase 2 验证记录

- [x] `GlassSceneCapture` 实现按原生渲染阶段共享的 RGBA8 目标。单个目标按尺寸复用，超过 32 MiB 或硬件纹理上限时降采样。驱动、分配和帧缓冲完整性失败可诊断。
- [x] 双端通过预算单元测试和构建。真实 Forge 与 NeoForge 开发客户端都验证了跨 GUI 裁剪捕获像素、同尺寸复用、调整视口、重建、删除和 GL 状态恢复。测试环境为 RTX 4060 Laptop GPU、OpenGL 4.6、窗口参数 800 × 480。
- [x] 原生运行记录在 `build/kernel-verification/client-forge-uuk-capture/` 与 `build/kernel-verification/client-neoforge-uuk-capture/`。两端均写出 `success.txt` 和 `stopped.txt`。

## Phase 3 光学基础

- [x] `UIEngine.glass()` 通过现有类型化注册表发现材质。材质和几何是 Java 17 共享契约。原生回调以 `Context.glass(List<GlassDraw>)` 绘制，之后提交前景。Shader 缓存和目标由同一客户端内核管理。
- [x] `managed_glass.fsh` 使用几何距离和法线产生折射与边缘透镜。按采样档位调整散射，按环境亮度和前景语义保护对比。支持实色回退。光学预设是项目内可调参数。
- [x] Forge 与 NeoForge 的原生测试覆盖条纹位移、中心透射、高对比黑白文字、绘制顺序、裁剪、损坏 Shader 回退、资源重载恢复、世界 HUD 和退出释放。核心 Shader 使用单份共享采样背景。
- [ ] 补齐多场景时间平滑、完整动态主题令牌、空间合成以及硬件兼容性和性能实测。

## Phase 4 第一轮接入

- [x] `GlassDraw.interpolate` 在同一进度插值呈现几何、光学和高光，形状命中按 `GlassRegion` 计算。与现有弹簧、输入捕获和活动作用域配合。第三方示例的状态条使用现有 `AnimationService.Track`，活动变化可重定向，减少动效时使用目标状态。
- [ ] 将光学状态接入所有共用交互控件，逐项覆盖聚焦、按压、拖动、取消及旁白。跨形状族的连续形变仍需扩展。

## 验证入口

在 Git Bash 设置 `JAVA_HOME` 为本机 JDK 21，让 Gradle 按各自源码集选择 Java 17／21 工具链。以下命令要求所需依赖已缓存。

```bash
./gradle-install/gradle-8.8/bin/gradle --offline --console=plain kernelTest test build
./neoforge/gradlew -p neoforge --offline --console=plain kernelTest test build
```

原生验证复用 `gradle/kernel-smoke.init.gradle` 与 `src/kernelSmoke/`，在独立游戏目录运行两端的 `runClient`。用明亮天空、暗色洞穴、高对比方块、复杂物品列表、移动世界和静态菜单记录两种前景文字的截图。性能数据附上 GPU、驱动、分辨率、GUI 比例、质量和采样窗口。构建、Shader、原生行为和视觉证据分别记载。

## 本轮交付证据

| 验证 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| 固定源码副本 `kernelTest test build` | 137 项内核、843 项客户端测试通过，重映射和发包检查通过 | 137 项内核、845 项客户端测试通过，发包检查通过 |
| 原生 `uuk-verified` | Screen 与真实世界 HUD 通过，记录退出释放 | Screen 与真实世界 HUD 通过，记录退出释放 |
| 独立示例 | 开发客户端加载 Mojmap 的库与示例 JAR，状态条与关闭回焦点通过 | 开发客户端加载正式库与示例 JAR，状态条与关闭回焦点通过 |
| 包内容 | 共享 Glass API 的 class major 为 61，两个 managed Shader 随包，原生测试夹具单独编译 | 共享 Glass API 的 class major 为 61，两个 managed Shader 随包，原生测试夹具单独编译 |

真实客户端使用 RTX 4060 Laptop GPU、OpenGL 4.6、800 × 480。禁用深度测试进入材质绘制的 Forge 红灯已复现，修复后两端 GL 状态快照一致。原生截图、运行标记、构建记录和产物哈希归档在 `build/uuk-verification/`。

固定副本包含本任务开始时的源码范围与本轮改动。原工作区同期开发 Correctness 和 Intelligence，源文件迁移及其新增测试独立验证。原工作区最后一轮两个平台都运行了 169 项内核测试，各有一项 `CorrectnessRuntimeTest.idleFrameReusesCommittedSnapshotAndScopeCloseRevokesPresentation` 因 `Cannot open UI_TREE under MODULE` 失败。固定副本的客户端结果与原工作区的测试结果分别计数。
