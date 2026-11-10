<!-- 作者：山海／dishanhai -->
# Intelligence CPU 基準（1.0.6）

2026-11-10，Windows 11，Eclipse Adoptium Java 17.0.18；G1、512 MiB heap、`ActiveProcessorCount=4`，seed `20261110`。每情境暖機 2000 次、採樣 2000 次，使用 `System.nanoTime` 與當前執行緒的 `ThreadMXBean` 配置量。量測期間沒有同時啟動本次 Minecraft 測試。這些不是 GPU 時間或 FPS。

## 重放

使用專案 Gradle 的 `correctnessBenchmark`；可設 `correctnessBenchmarkWarmup`、`correctnessBenchmarkSamples`、`correctnessBenchmarkScenario` 和 `correctnessBenchmarkOutput`。Java 17 source set 與既有 benchmark 共用，沒有新增 benchmark 相依。

原始產物均含 `raw.csv`、`summary.csv`、`work.csv`、`summary.json` 和 `environment.json`：

- [完整十情境基線](../build/reports/correctness-benchmark-before-layout-only/summary.csv)
- [最終版 32 節點](../build/reports/correctness-benchmark-layout-only/summary.csv)／[處理量](../build/reports/correctness-benchmark-layout-only/work.csv)
- [最終版 128 節點](../build/reports/correctness-benchmark-layout-only-128/summary.csv)／[處理量](../build/reports/correctness-benchmark-layout-only-128/work.csv)

## 可比較的優化結果

純分析重用 `CollisionWorld.layoutOnly`，不建立四條不播放的 Spring 通道；祖先檢查改為有界走訪，省去每次比較的臨時集合。原有建構式與動畫模式保留，兩項新增等價測試及既有碰撞／動畫測試通過。

| 32 節點密集布局 | 優化前 | 優化後 |
| --- | ---: | ---: |
| P50 | 1.3993 ms | 0.9082 ms |
| P95 | 2.5746 ms | 1.6448 ms |
| P99 | 2.7778 ms | 2.2077 ms |
| 每次配置 P50 | 547400 B | 344648 B |
| placements 總數 | 64000 | 64000 |
| 可繪節點總數 | 36000 | 36000 |
| 預算耗盡樣本 | 0 | 0 |
| 降級樣本 | 2000 | 2000 |

同一處理量下，配置 P50 約減少 **37.0%**。每次仍只有 18／32 個節點可安全呈現；這不是 32 節點完整排版達標。延遲是本機一次固定負載結果，不作硬體無關保證。

## 其他實測與限制

完整基線的靜止 1／32／128 節點均為 2000 個有效樣本、0 次重求解，P50 均約 0.3 µs，當前執行緒配置 P50 64 B。旋轉預測 P50 7.2 µs，Spring 副本投影 18.4 µs，256 個合成像素評估 11.9 µs，局部材質修復 28.5 µs，32 節點驗證＋修復 125.9 µs。像素輸入為固定 seed 的 CPU 合成資料，不能當 Minecraft 畫面證據。

最終版 128 節點：P50 **4.6662 ms**、P95 **4.9542 ms**、P99 **5.2732 ms**，配置 P50 **1144544 B**；2000／2000 樣本觸發 4 ms 協作式布局預算並降級。平均約 22 個節點安全呈現。優化前後的 deadline 截斷點和處理量不同，因此不據此宣稱 128 節點速度或配置改善。總 pipeline 可以超過 solver deadline，單次 Java 方法不是硬搶占。

Runtime `metrics()` 分開布局、運動、對比度、修復、準備、cache／revalidation 和採樣 CPU 累計值。GPU 採樣時間及執行期每幀配置尚未量測，維持 `-1`。真實 GPU 區域回收與狀態還原另有原生測試，不能將 CPU readback 成本當作 GPU 耗時。
