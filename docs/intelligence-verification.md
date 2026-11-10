<!-- 作者：山海／dishanhai -->
# Intelligence 驗證紀錄（1.0.6）

2026-11-10，Windows，工作樹整合驗證。保留原有未提交內容；沒有提交、推送或部署。命令使用明確 Git Bash、本機 Gradle 8.8 與 NeoForge Gradle 9.2.1，沒有刪除 build 或快取。

## 自動測試與封裝

| 項目 | Forge 1.20.1 | NeoForge 1.21.1 |
| --- | --- | --- |
| `kernelTest`（Java 17） | 191 通過、0 失敗／跳過 | 191 通過、0 失敗／跳過 |
| 一般 `test` | 861 通過、0 失敗／跳過 | 863 通過、0 失敗／跳過 |
| `build`／`verifyLibraryJar` | 通過，含 `reobfJar` | 通過 |
| 第三方範例 `build`／JAR 邊界檢查 | 通過，依賴外部程式庫 | 通過，依賴外部程式庫 |
| 純核心 | 428 個 class，major 61；`jdeps` 僅 `java.base` | 同源純核心 Java 17 |
| 語言 | 111 個 correctness key，英／簡／繁及 placeholders 一致 | 共用同一資源及測試 |

最後封裝命令為 `kernelTest test build --offline --no-daemon --console=plain`。日誌：[Forge](../build/correctness-verification/forge-ship.log)、[NeoForge](../build/correctness-verification/neoforge-ship.log)。

範例構建日誌：[Forge](../build/correctness-verification/forge-example-build.log)、[NeoForge](../build/correctness-verification/neoforge-example-build.log)。最終正式包、Forge 開發包的大小與 SHA-256 保存在 [產物證據](../build/correctness-verification/artifact-evidence.json)。

核心回歸包含：座標空間、響應式約束、父子／安全區、最小尺寸無解、確定性優先序、solver budget、動畫中段與旋轉、Spring 非破壞投影、原有動畫與純布局模式等價、過期宣告／計畫／回滾、假命中提案、混版快照、row overflow、父關閉／停用／模態撤回、world observation reset、動態材質保持、透明前景／綠字／21:1 回退、內容及 padding 的材質樣本失效、完整 JSON 與非有限值處理。

曾出現一次 `reobfJar` 子程序 exit 1，工具日誌停在載入 inheritance，沒有明確例外。相同參數單獨重跑成功，後續完整構建也成功；沒有用改源碼或略過封裝處理，根因不作推測。詳細記錄在 `forge-remap-diagnostic.log`。

## 原生客戶端

`gradle/kernel-smoke.init.gradle` 在獨立的 `build/kernel-verification/client-*-correctness/` 啟動開發客戶端。Forge 實際 Java 17，NeoForge 實際 Java 21。既有原生 Kernel／Liquid Glass／Live Surface 煙測及新增 CorrectnessSmoke 均取得 `KERNEL_SMOKE_OK`、`CORRECTNESS_SMOKE_OK` 和正常停止標記。

最終源碼煙測完整日誌：[Forge](../build/correctness-verification/forge-native-final.log)、[NeoForge](../build/correctness-verification/neoforge-native-final.log)，兩端均正常退出且 `BUILD SUCCESSFUL`。

新增測試硬斷言：中央服務／能力查詢、已提交和實際呈現回執、真實 framebuffer 改變、原生鼠標點擊、安全區避讓、無解節點停用、GPU 底圖與材質採樣來源、實際字型量測、本地化、視窗／GUI Scale、原生資源重載、四頁 Inspector、JSON、返回焦點和舊作用域／輸入退休。既有煙測另覆蓋按鍵、拖放／取消、失焦、捕獲、世界進出及 Shader 故障降級／恢復。

- [Forge 成功紀錄](../build/kernel-verification/client-forge-correctness/correctness-success.txt)
- [NeoForge 成功紀錄](../build/kernel-verification/client-neoforge-correctness/correctness-success.txt)
- [Forge Visual 預覽](../build/kernel-verification/client-forge-correctness/correctness-inspector-visual.png)
- [NeoForge Visual 預覽](../build/kernel-verification/client-neoforge-correctness/correctness-inspector-visual.png)
- 各目錄另有 Layout／Motion／Correctness PNG、resize／scale／reload PNG 和帶像素統計的 JSON。

另有隱藏 OpenGL 3.2 實測：ROI 原點與縮圖、PENDING／fence 回收、版本淘汰、reset、GL 狀態還原與 `glGetError=0`。這不是 Minecraft 最終字形辨識或 GPU 時間的替代證據。

## 邊界

正式 JAR 已建置及檢查，原生 Correctness 煙測使用開發 classpath，不代表 Forge 正式 SRG 消費實例或所有整合包驗收。Shadow bounds 是裝飾模型；字形邊緣／紋理評分是啟發式；Spring／旋轉 envelope 保留模型與誤差標籤。未知第三方 HUD／透明攔截不能推斷。GPU 時間和執行期每幀配置量仍為 `-1`，離線配置量另有實測。剩餘整合包、長時間資源／GPU 及人工無障礙驗收列在 [TODO](../TODO.md)。
