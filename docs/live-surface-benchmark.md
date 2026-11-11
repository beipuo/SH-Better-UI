<!-- 作者：山海／dishanhai -->
# 实时表面无头基准

基准通过真实的 `KernelHost`、作用域、`LiveSurfaceRuntime` 和调度器执行发布、更新、仲裁与清理。`placement-8-obstacles` 直接调用 `SurfacePlacement`，输入使用预先测量的布局尺寸。每次采样结束后校验完成的工作，并在调度器留有未处理、失败或非预期任务时终止运行。

## 复跑

两个 Gradle 工程共用 `liveSurfaceBenchmark` 任务，运行器使用 Java 17。以 Git Bash 从项目根目录执行，Gradle 使用 JDK 21，并可找到 Java 17 工具链。

```bash
./gradle-install/gradle-8.8/bin/gradle liveSurfaceBenchmark --offline --no-daemon \
  -PliveSurfaceBenchmarkWarmup=4000 -PliveSurfaceBenchmarkSamples=3000
./neoforge/gradlew -p neoforge liveSurfaceBenchmark --offline --no-daemon \
  -PliveSurfaceBenchmarkWarmup=4000 -PliveSurfaceBenchmarkSamples=3000
```

`liveSurfaceBenchmarkScenario` 可选择单个场景，`liveSurfaceBenchmarkPlatform` 可指定 `FORGE_1_20_1` 或 `NEOFORGE_1_21_1`，`liveSurfaceBenchmarkOutput` 可指定输出目录。默认预热和采样各 2,000 次。默认报告目录为各工程的 `build/reports/live-surface-benchmark/`。

下列独立编译方式复现本次数据。`javac` 和 `java` 使用同一套 JDK 17。

```bash
bench_classes=$(mktemp -d)
mapfile -d '' bench_sources < <(find src/kernel/java src/kernelBenchmark/java -name '*.java' -print0)
javac --release 17 -encoding UTF-8 -d "$bench_classes" "${bench_sources[@]}"
for selector in FORGE_1_20_1 NEOFORGE_1_21_1; do
  java -Xms512m -Xmx512m -XX:+UseG1GC -XX:ActiveProcessorCount=4 \
    -cp "$(cygpath -am "$bench_classes")" com.dishanhai.sh_better_ui.kernel.internal.LiveSurfaceBenchmark \
    --platform "$selector" --warmup 4000 --samples 3000 \
    --output "build/reports/live-surface-benchmark/$selector"
done
```

每次运行写出 `raw.csv`、`summary.csv`、`work.csv` 和 `environment.properties`。`raw.csv` 保留 ns 和 B，`summary.csv` 采用 nearest rank，即 `ceil(n × p)`。分配量为取样线程的 Java 堆分配差，使用 `ThreadMXBean`。不可用时，原始记录为 `-1`，汇总为 `NA`。

## 测量结果

2026-11-10，工作树包含本次 ALSE 实现，基线提交为 `d87e31e`。主机为 Intel Core i9-14900HX，32 个逻辑处理器，31.62 GiB 内存，Windows 11 Pro 10.0.26200 amd64。运行时为 Eclipse Adoptium Temurin 17.0.18+8，512 MiB 固定堆、G1GC，JVM 可见处理器数为 4。每种平台选择器使用一个独立 JVM，各场景预热 4,000 次、采样 3,000 次。采样使用固定模拟帧时钟和实际的 `System.nanoTime()`。最终 Gradle 复测与另一平台的客户端启动有时间重叠，数据保存在 `build/live-surface-verification/benchmark-forge/` 和 `benchmark-neo/`。

| 场景 | Forge 选择器 µs P50 / P95 / P99 | Forge B P50 / P95 | NeoForge 选择器 µs P50 / P95 / P99 | NeoForge B P50 / P95 |
| --- | ---: | ---: | ---: | ---: |
| `stable-1` | 1.60 / 2.30 / 3.30 | 48 / 48 | 0.40 / 1.20 / 4.00 | 48 / 48 |
| `stable-10` | 0.30 / 0.50 / 9.30 | 16 / 16 | 1.00 / 2.40 / 14.40 | 304 / 304 |
| `stable-100` | 0.50 / 1.30 / 39.30 | 272 / 272 | 1.00 / 2.70 / 33.20 | 304 / 304 |
| `coalesced-1000` | 27.90 / 39.70 / 65.90 | 2,040 / 2,216 | 21.00 / 36.00 / 72.50 | 2,072 / 2,168 |
| `notification-burst-32` | 15.30 / 28.20 / 47.20 | 18,512 / 18,664 | 16.70 / 30.80 / 44.40 | 18,536 / 18,688 |
| `placement-8-obstacles` | 1.00 / 5.70 / 9.70 | 216 / 976 | 0.90 / 5.40 / 8.50 | 216 / 976 |
| `scope-remount` | 13.10 / 19.60 / 31.50 | 6,064 / 6,064 | 7.50 / 20.00 / 29.30 | 6,064 / 6,064 |

两个选择器执行同一套共享 Java 代码。上述分位数反映这两次进程采样的耗时与分配。

## 已核对的工作量

下表为每种选择器各自的累计值。普通场景每个样本推进模拟时间 16,666,667 ns。通知场景每批推进 1 秒，使每批使用独立的限速窗口。

| 场景 | 样本内操作 | 已完成 | 合并 | 拒绝 |
| --- | --- | ---: | ---: | ---: |
| `stable-1` / `stable-10` / `stable-100` | 每帧校验现有主活动、次活动和等候队列 | 各 3,000 帧 | 0 | 0 |
| `coalesced-1000` | 3,000,000 次同线程内容提交及随后帧推进 | 3,000 个末值修订与观察回调 | 2,997,000 | 0 |
| `notification-burst-32` | 96,000 次发布请求，含 COUNT 合并与真实限速异常 | 24,000 个新通知 | 48,000 | 24,000 |
| `placement-8-obstacles` | 轮播空闲、压缩、最小形态和移位四种布局输入 | 3,000 次安全摆放 | 0 | 0 |
| `scope-remount` | 打开会话、发布活动、登记保留区、显示、关闭和再次推进 | 3,000 次清理，取消 3,000 个待处理任务 | 0 | 0 |

1 个活动的稳定帧没有重建计划，10 和 100 个活动的场景各重建 100 次。稳定帧 P95 大多命中快速路径，偶发仲裁反映在 P99。更新负载复用已构造的内容，每批在一帧中提交最后一个版本。通知负载在取样后关闭作用域，并校验通知均已释放。所有场景在结束时的调度队列均为空。

## 原生客户端采样

两端以同一份 `LiveSurfaceSmoke` 在实际 Minecraft 集成世界采样。主机为同一台 i9-14900HX，显卡为 NVIDIA GeForce RTX 4060 Laptop，OpenGL 4.6.0 NVIDIA 610.88。窗口 800 × 480，GUI Scale 2，使用默认 Spatial 质量和实时表面设置。屏幕上的主次活动共两个。

| 场景 | 帧数 | Forge 绘制 CPU P50 / P95 µs | NeoForge 绘制 CPU P50 / P95 µs | Forge / NeoForge 动画 CPU P95 µs |
| --- | ---: | ---: | ---: | ---: |
| 稳定双栏 HUD | 48 | 468.2 / 1,012.2 | 340.0 / 728.5 | 48.5 / 52.1 |
| 稳定展开 Screen | 48 | 212.8 / 324.4 | 198.2 / 304.7 | 19.6 / 28.3 |
| 连续重定向 | 32 | 239.9 / 346.0 | 228.2 / 464.6 | 69.3 / 136.2 |

数据来自 `client-forge-live-stress` 和 `client-neoforge-live-stress` 下的 `live-frame-samples.csv` 与 `live-environment.txt`。绘制时间包含表面、阴影和内容的 CPU 提交。表中是短样本，未采集 GPU 执行时间、整帧 FPS 或网络延迟。冒烟另外检查三个资源重载循环、窗口改为 864 × 512 和 GUI Scale 1。 [原生复跑命令](live-surface-verification.md)
