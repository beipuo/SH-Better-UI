<!-- 作者：山海／dishanhai -->
# 性能和配置

节点缓存世界变换、逆变换、阴影参数和裁剪后的顶点。静态场景复用绘制命令，窗口或 GUI 缩放变化会重建受影响的命令。绘制仅合并相邻且共用参数的批次，颜色走顶点属性。Minecraft 持有立即模式的缓冲，背景采样复用原有后处理目标。

客户端 TOML 的 `[ui]` 支持 `spatialEnabled`、`spatialShadows`、`spatialLighting`、`spatialBlur`、`spatialQuality` 和 `spatialProfiling`。既有配置加载时自动补齐默认值。用户的非 AUTO 质量覆盖场景请求。LOW 减少阴影层和灯光数并禁用背景模糊，HIGH 和 ULTRA 使用高斯直边近似。AUTO 按 CPU 平滑采样分级并使用迟滞，最低降到 LOW。减少动效继承 `reducedMotion`。

`scene.profile()` 提供可见节点、阴影命令、缓存命中和未命中、有效质量、空间批次数、背景采样次数和 CPU 提交时长。用户开启 `spatialProfiling` 或场景显式请求采样后，支持的客户端用异步时间戳记录最近一次已完成的整场景 GPU 耗时。数值 `-1` 表示尚未取得 GPU 数据。计时槽全部繁忙时跳过本次采样。原生基准夹具也会独立测量 GPU。

复现与原始 CSV 说明见 [客户端验证](../SPATIAL_VERIFICATION.md)。比较时应固定质量、窗口大小、GUI 缩放、预热和样本数。

## 客户端测量

2026-11-10，RTX 4060 Laptop GPU，GUI Scale 2，固定 HIGH。每端执行 27 个场景，每场景预热 8 帧、采样 20 帧。以下为 5000 节点场景的中位数，单位为毫秒。

| 场景 | Forge 提交 | Forge GPU | NeoForge 提交 | NeoForge GPU |
| --- | ---: | ---: | ---: | ---: |
| 静态阴影 | 156.06 | 154.78 | 15.12 | 50.75 |
| 位移、旋转与高度 | 97.81 | 96.94 | 16.83 | 51.70 |
| 移动点光 | 105.02 | 104.12 | 103.00 | 103.07 |
| 混合材质 | 96.48 | 96.27 | 65.45 | 64.83 |
| 嵌套裁剪 | 54.17 | 53.63 | 51.38 | 50.63 |
| 高频动画打断 | 71.82 | 72.64 | 75.52 | 77.05 |

原始结果位于 `build/kernel-benchmark/native-forge-spatial-full/frames.csv` 和 `build/kernel-benchmark/native-neoforge-spatial-full/frames.csv`。提交时间是原生基准统计的绘制阶段墙钟耗时，可能包含等待 GPU。5000 节点下的多层绘制和受光场景均超出 60 FPS 的单帧预算。
