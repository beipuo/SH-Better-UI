<!-- 作者：山海／dishanhai -->
# UI Intelligence & Correctness 架構審計

審計日期：2026-11-10。以目前工作樹為準，包含既有尚未提交的 Liquid Glass、Spatial、Live Surface 工作。本報告先於實施計畫建立；舊文件中的測試數及遊戲證據不視為本次驗收。

## 中央核心與可重用能力

| 領域 | 實際入口／位置 | 結論 |
| --- | --- | --- |
| 中央入口 | `src/kernel/java/.../kernel/api/UnifiedUI.java`、`UIEngine.java`、`internal/KernelHost.java` | 已存在；保留入口和 contract version 1，以新增 service key 和 default accessor 擴充。不能建立第二個 Kernel。 |
| 模組與能力 | `ModuleDiscovery`、`DependencyGraph`、`ServiceRegistry`、`CapabilityIndex` | 已有來源驗證、穩定依賴排序、循環診斷、能力版本和替換策略；新服務走同一註冊窗口。 |
| 生命週期 | `ScopeLifecycle`、`ResourceOwnership`、`CleanupManager` | 已有引擎／模組／會話／Screen／UI 樹／元件世代；新節點和 GPU 資源必須綁定作用域。 |
| 狀態／配置／事件 | `StateStore`、`ConfigCenter`、`MutationCoordinator`、`EventBus` | 已有版本交易、過期結果拒收和有界通知；不能另建事件總線或狀態中心。 |
| 調度／失效 | `CentralScheduler`、`DirtyWork`、`SchedulerBudget` | 已有相位、活動工作、每節點每旗標每幀至多一次和回呼配額。新流程接入 `RENDER_PREPARATION`，不能無條件重建樹。 |
| 原生橋接 | `src/main/.../kernel/internal/ClientUiKernel.java` | 現有 STYLE_RESOLUTION → MEASURE → LAYOUT → ANIMATION → GEOMETRY_UPDATE → RENDER_PREPARATION → 原生 RENDER → CLEANUP 是唯一幀泵。 |
| 渲染 | `ClientRender`、`RenderService` | 已有受管回呼、表面、圖層、clip、transform 和原生上下文有效期。新宣告式呈現須消費已提交快照。 |

以上縮寫路徑中的 `...` 為 `com/dishanhai/sh_better_ui`。實際目錄來自倉庫盤點，並非預設多模組範本。

## 視覺與材質

`ClientGlassRender` → `GlassSceneCapture` → `GlassProgram` 是既有的光學材質路徑。`paint` 在 `GuiGraphics.flush()` 後、玻璃及前景之前懶捕獲場景；每 native pass 重用捕獲，32 MiB 上限，使用 GPU blit/downsample，沒有完整螢幕 CPU 讀回。`managed_glass.fsh` 的 `protectForeground` 對合成底板做線性亮度保護。它是材質保護，不能當成字形最終像素的實測報告。

`GlassMaterial`、`GlassOptics`、`GlassDraw`、`GlassRegion` 已是 Java 17 純資料契約；透明度、模糊、折射及品質須由它們和既有材質 pass 控制。Correctness 只能產生局部解析結果，不能直接改 OpenGL 全域狀態。

`theme/accessibility/ContrastPolicy` 已有 sRGB alpha 合成及對比度計算。擴充時應保持其 FQCN／簽章並重用，不能抄出第二份 WCAG 公式。`ThemeSnapshot.version()` 和 `changedFrom` 可用於視覺失效；`ThemeManager.snapshot()` 是目前過渡值。Adaptive token 是有效值覆層，不回寫使用者 theme 定義。

缺口：有來源／時間／幀號的區域背景統計、非阻塞 CPU 可用樣本、局部最差對比度、紋理及字形啟發式的獨立標識、滯回／最小保持時間、材質修復前後紀錄。讀回必須在 GPU fence 完成後取得，失敗或過期明確回報 unavailable/stale。

## 空間、動畫與輸入

`SpatialUI`／`SpatialScene`／`spatial.internal.Scene` 已有 Java 17 幾何、形狀、變換、clip、z/elevation、陰影與遮擋。`visualTransform` 和 `hitTransform` 刻意可分開，不能將所有差異都判錯。陰影、布局、可見和互動區域必須分欄。

`CollisionService` → `UiCollisionLayout` → `CollisionWorld`／`LayoutSolver` 已有優先級、避讓、縮放、分組 dirty、確定性投影、64 輪預設上限和不可解事件。`UiCollisionLayout` 將原生 widget 的繪製和命中幾何同步套用，具有行索引。新的約束宣告應適配這套求解器，而非複製另一套矩形碰撞引擎。

`MotionApi`、`InterruptibleAnimation`、`Spring` 和 `TransitionController` 已有共享時間、中斷、重新定向、速度與減少動效；缺少可公開查詢的運動包絡、碰撞時間區間和預測誤差／條件。新預測器應對現有軌道／純運動快照運算，不能將起終點聯集稱為任意旋轉或彈性動畫的完整 swept bounds。

`ClientInput`／`ClientFocus` 已處理 capture、模態、焦點租約、失焦取消與原版阻斷權限。`NativeInput.Pointer` 沒有 presentation version。`ClientLiveInput.painted` 以實際繪製 geometry 和 activity revision 更新命中，是新快照提交／呈現回執的參考。

缺口：統一 `UiId` 節點描述、座標空間標識、preferred/resolved/predicted/swept/clipped/visible/interactive/occlusion 邊界、原版 HUD 安全區觀測與未知第三方 UI 限制、跨布局／渲染／輸入版本、焦點及透明遮擋的可解釋報告。

## 正確性與重複責任風險

目前沒有統一的 Hard > Soft > Preference 契約、帶量測證據的問題報告、原子修復計畫、修復後複驗與安全降級快照。現有碰撞事件、材質保護和主題對比策略各自合理，但缺少跨領域決策。

需避免的重複：`KernelHost` 與新中央類、`EventBus` 與新通知總線、`CollisionWorld` 與新矩形解算器、`GlassProgram` 與新玻璃引擎、`ContrastPolicy` 與新色彩公式、既有 Spring 與無關的新動畫時鐘。

依賴方向應保持：純核心資料／分析 → 平台注入 → 既有 client 渲染。純核心不可依賴 `UIApis`（其位於 main 並引用 Minecraft 服務）、`ThemeManager`、`GuiGraphics` 或 Loader。將有副作用的 native backends 注入核心，禁止 core → client → core 的初始化循環。

## 雙平台與相容性

| 項目 | Forge | NeoForge |
| --- | --- | --- |
| 啟動點 | 根 `build.gradle`，MC 1.20.1／Forge 47.4.16 | 獨立 `neoforge/build.gradle`，MC 1.21.1 |
| 工具鏈 | 正式碼 Java 17；一般測試使用 21 | native 21，`kernel`／`kernelTest` 固定 `--release 17` |
| 渲染入口 | Forge `ScreenEvent.Render.Post`／HUD 後置事件 | NeoForge 對應的獨立事件適配；不能共用 Loader 類 |
| 頂點 | `vertex/uv/color/endVertex` | `addVertex/setUv/setColor`；既有 Spatial 適配已隔離 |
| 輸入 | wheel 轉成 `(0,dy)` | 保留 `(dx,dy)`；按鍵／capture 沿用原橋 |
| 資源與關閉 | `ClientResources` 世代、World／Screen scope、shutdown | 相同公共契約，平台 listener 分開接線 |

必須保留 `UnifiedUI`、`UIEngine`、所有既有 service keys、`ShBetterUiApi`、KubeJS `SHUI`、既有碰撞／動畫／玻璃／主題 API 的 FQCN、方法和行為。新增介面以 additive API 為原則。

## 測試與效能

`gradle/kernel.gradle` 已提供無 Minecraft／Loader classpath 的 Java 17 `kernelTest`；兩端一般 `test`、`verifyLibraryJar` 和 Forge `reobfJar` 各自執行。`kernelBenchmark`、`liveSurfaceBenchmark`、原生 benchmark/smoke init scripts 可重用。`SchedulerSnapshot` 有排程指標，尚缺獨立的正確性階段、cache hit、revalidation、採樣成本與分配量。

新增驗證需覆蓋：確定性解、無解硬約束、安全降級、晚到版本拒收、父子／座標轉換、動畫中段／旋轉／中斷、動態對比滯回、失效快取、實測來源、按最後呈現快照命中、作用域清理、雙端封裝。GPU 時間與實際 Minecraft 可讀性只能依原生測量報告，不能從離線 CPU 基準推算。

## 審計結論

擴充現有 Kernel 的單一 correctness service；重用既有玻璃、主題、幾何、碰撞、時鐘、所有權、事件與生命週期。先建立共享不可變契約和報告，再接入增量預測、局部材質解析、原生觀測與渲染／命中提交。實施計畫另列，所有未驗證項目保留明確狀態。
