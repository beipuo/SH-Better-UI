<!-- 作者：山海／dishanhai -->
# UMIVP 性能基线

本地 Windows x64。目录包含 42 条记录，未压缩 JSON 为 245,889 字节。Node.js 24.15 的离线基准以 30 个批次测量墙钟时间。解析与索引按独立实例取样，查询与排序预热后取样。

| 操作 | 中位数 | P95 |
| --- | ---: | ---: |
| JSON 解析 | 0.435 ms | 0.496 ms |
| 搜索索引首次构建 | 0.316 ms | 0.966 ms |
| 稳定 ID / 别名查询 | 0.124 µs | 0.214 µs |
| 短 ID、名称和中英文视觉描述排序 | 0.149 ms | 0.190 ms |

复现命令为 `node catalog/benchmark.mjs`，原始样本汇总位于 `build/material-catalog/catalog-benchmark.json`。

## WebGL 2

Chromium 157 固定 640×384 RGBA8 夹具。ANGLE SwiftShader 样本的首个可用预览 585.2 ms，GLSL 编译与链接 0.8 ms，CPU 提交中位数 0.1 ms，单次可用 timer query 为 5.8849 ms。ANGLE D3D11 / RTX 4060 Laptop GPU 样本首个可用预览 449.6 ms，编译与链接 2.8 ms，单次 GPU query 为 0.02048 ms。硬件路径的 CPU 中位数低于浏览器计时分辨率。单次 GPU 样本用于说明量测路径，帧耗时分布待采集。

1× 场景捕获目标 983,040 字节，并排 A/B 增加一个可复用的同尺寸合成目标。2× 单场景捕获目标 3,932,160 字节。正常绘制复用目标，切换尺寸才重新分配。浏览器测试检查关闭后释放和上下文恢复。以上字节数是 RGBA8 目标容量，驱动额外内存待测。 [原始证据](../build/material-catalog/web/evidence.json) · [复现命令](web-rendering-guide.md)

## 原生

两端实际客户端用相同夹具预热两个查询批次，随后记录五批，每批 10,000 次，稳定 ID 与别名各半。

| 量测 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | ---: | ---: |
| 稳定 ID / 别名查询中位数 | 89.13 ns | 108.8 ns |
| 打开画面至首个光学绘制 | 77.18 ms | 78.15 ms |
| 资源重载墙钟 | 3,030.29 ms | 3,028.25 ms |
| 捕获纹理容量 | 1,635,200 B | 1,635,200 B |
| 进程 Java 堆瞬时用量 | 1,352,323,832 B | 734,694,376 B |

重载包含游戏资源，堆用量包含整个 Minecraft 客户端。烟测关闭后检查捕获容量归零。原生每帧 GPU/CPU 时间、驱动实际显存和长时间稳定性仍需取样。 [Forge 原始样本](../build/kernel-verification/client-forge-material-catalog-final/material-catalog-performance.json) · [NeoForge 原始样本](../build/kernel-verification/client-neoforge-material-catalog/material-catalog-performance.json)
