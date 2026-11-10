<!-- 作者：山海／dishanhai -->
# UI Intelligence & Correctness 接入

適用版本：SH-Better-UI **1.0.6**，Forge 1.20.1／Java 17、NeoForge 1.21.1／Java 21。純核心固定 `--release 17`。[審計](intelligence-audit.md) · [實施計畫](intelligence-plan.md) · [驗證](intelligence-verification.md) · [效能](intelligence-performance.md)

## 唯一入口與最小宣告

沿用 `UnifiedUI.get()`／模組的 `context.engine()`，新能力是同一服務登記表中的 `UICorrectnessEngine.KEY`，不是另一個中央引擎。安裝 SH-Better-UI 為外部相依，消費方不可重新封裝它的類或資源。

在模組 `start` 取得 `ModuleContext`；Screen 在 `init` 用 `UIApis.SCREENS.mount` 取得自己的 UI 樹，HUD 使用模組或掛載的會話範圍。

```java
var correctness = context.engine().correctness();
var binding = correctness.register(context, scope,
        UIElement.builder("your_mod:music_island")
                .anchor(SpatialIntelligence.Anchor.TOP_CENTER)
                .preferredSize(240, 48).minSize(120, 36).maxSize(420, 140)
                .padding(8).margin(6).priority(80)
                .respectSafeArea(true).avoidOverlap(true)
                .text("your_mod.music_island.title")
                .visualPolicy(UIElement.VisualPolicy.adaptiveGlass())
                .motion(UIElement.MotionPolicy.animated())
                .build());
```

標籤放在消費方語言檔。宣告式 renderer 自動繪製玻璃、局部保護和文字，無須再畫同一面板。`Binding.committed()` 是已提交的布局；`presented()` 只有原生繪製成功後才有值。預測不會填入 `presented()`。關閉 binding 或任一擁有範圍會清理節點、子宣告、主題訂閱及運動軌道。Screen 返回後應在新的 `init` 重新掛載；不要重用已退休的 binding。

完整可編譯消費方：[CorrectnessExamples.java](../examples/kernel-extension/common/src/main/java/com/dishanhai/shbui_kernel_example/CorrectnessExamples.java)。兩端範例都沿用外部 JAR 依賴與封裝檢查。

## 執行緒、版本與輸入

註冊、修改、失效、提交和清理在 Kernel UI 執行緒進行。`validate`、`predict`、`resolve` 可以分析脫離宿主的不可變快照。背景工作透過既有 `UIScheduler` 回到 `STATE_MUTATION`；保留取得資料時的 `binding.revision()`，使用 `binding.update(expectedRevision, next)`，晚到結果回傳 `false`。

互動元件宣告 `.interactive(true)`，再由原有輸入系統登記：

```java
var target = context.engine().require(UIApis.INPUT)
        .target(context, mountedNode, 80, binding);
```

事件仍透過原有 `InputService.EVENT`、capture → target → bubble 和原版阻斷權限處理。鍵盤焦點／旁白沿用 `FocusService` 及原生控件；`UIInteractionSnapshot` 可將宿主實測的 render/hit version、遮擋與焦點送進 `correctness.validateInteraction(...)`。任意外部 hit predicate 和未登記透明層無法自動推知，報告保留這個限制。

同版繪製和命中是預設契約。只有明確允許的 `allowIndependentHitGeometry` 策略可採用獨立幾何。安全閘停用、離頁、父節點關閉或原生繪製失敗，都撤回舊 receipt，防止隱形控件繼續接收輸入。

## 流水線與資料邊界

| 階段 | 輸入 | 輸出 |
| --- | --- | --- |
| 宣告／狀態收集 | 作用域、`UIElement`、revision | 不可變意圖、dirty scene |
| 既有 layout/motion 相位 | 約束、視口、安全區、現有軌道實值 | 目標布局、保速運動、條件式 future |
| `RENDER_PREPARATION` | 宣告版、theme/resource 版、觀測樣本 | 有界解算、空間／視覺報告、修復與複驗 |
| 提交 | 可呈現節點及無解約束 | `UISnapshot`；必要時 `SAFE_DEGRADED` |
| 原生後置渲染 | 已提交快照 | 同版繪製 receipt、局部像素工作 |
| 後續幀觀測 | PBO fence 完成的樣本 | 有來源的統計、僅視覺失效；不強迫重排 |

狀態變化不原地修改已發布快照。修復計畫必須與現行宣告、資源、主題及快照版本吻合；套用時重算提案並複驗，不能透過偽造 report、像素或命中區繞過硬約束。回滾需要該次成功套用的精確版本回執，不能覆蓋後續成功修改。

## 約束、布局與動畫

`UICorrectnessContract` 分開 Hard、Soft 和偏好。系統級非法尺寸、資源版本及互動安全檢查不能取消。修復先保護輸入／關鍵可讀性／安全區，再處理布局穩定、動畫及裝飾。無解節點會停止繪製及輸入，報告仍保留原始無解約束；隱藏不代表最小尺寸已滿足。

布局重用原有 `CollisionWorld`／`LayoutSolver`。相同約束以 priority／`UiId` 決定穩定順序；父先於子。支援錨點、尺寸上下界、margin／padding、父子局部位置及 `.breakpoint(minimumViewportWidth, constraints)`。時間預算是協作式檢查，不能搶占正在執行的單次 Java solver；未處理的節點明確回報 `LAYOUT_BUDGET`。禁止移位或停用自修復時，保留原意圖並由安全閘處理，不偷偷 clamp。

`MotionPolicy.animated()` 重用 `InterruptibleAnimation` 的 RETARGET，從目前位置／速度重定向。已知障礙採最多 12 個確定性路徑候選及有界 waypoint；無路會輸出 `UNRESOLVED`。任意平移／縮放／旋轉／尺寸組合可用 `SpatialIntelligence.Trajectory` 做非破壞性分析。原有 `SpatialUI` 仍負責其原生變換繪製；給宣告附上 trajectory 並不代表 renderer 已執行那條任意旋轉軌跡。

`Prediction` 分開 path、swept envelope、保守標記與誤差；碰撞輸出時間區間。旋轉／非矩形 envelope 可能誤報。Spring 用既有積分器的副本，`sampledModel=true`；`spatialError/timeError=-1` 表示實際 frame cadence 的連續物理誤差未知。

## 座標、空間與安全區

純分析使用同一求解器的 `CollisionWorld.layoutOnly`，不配置不會播放的 Spring 通道；既有建構式仍保留原動畫行為。32 節點同負載比較的配置量及處理／降級數見效能報告。

`SpatialIntelligence.Space` 明確區分 GUI、framebuffer、physical、local 及 world-projected。公共 bounds 使用左上角原點；OpenGL 的原點轉換在平台 sampler 完成。`UICoordinates` 必須傳入 GUI、framebuffer 和實體視窗的獨立尺寸；local／projected bounds 必須提供明確矩陣，不能隱式乘一個 GUI Scale。

快照分開 preferred／resolved／predicted／swept、clipping region、visible／interactive／occlusion 及 shadow model。陰影不參與點擊碰撞。`shadow` 是宣告的裝飾模型，並非實測光學陰影像素邊界。

共用 `ClientSafeAreas` 重用 Live Surface 的登記預約、Boss 前幀觀測、快捷欄／玩家狀態、聊天／計分板估計、字幕估計、容器和可見原生 widget。每項帶來源和幀號；未知第三方 HUD 不在保證範圍。第三方可沿用 `LiveSurfaceService.reserve` 提供範圍預約。

## 像素證據、玻璃與主題

底圖採樣在受管面板繪製前 flush 後進行；有效材質採樣在玻璃及局部保護之後、文字之前進行。GPU 降到最多 16×16 的 ROI，再透過三槽 PBO／fence 零等待輪詢回收。每幀最多選兩個節點；兩個階段分別有受限槽位。沒有完整螢幕 CPU 同步讀回。穩定畫面會降低頻率，設定上限和歷史有效期都可見。

原始底圖與有效材質樣本分開，避免把上次修復後的底色誤當本次原始背景。文字、padding、clip、位置、宣告／資源版本不符時，不重用舊區域證據。缺失、忙碌、過期或不支援的採樣狀態明確回報。

局部修復依序考慮前景、描邊、陰影、亮度隔離、模糊、染色、不透明度、遮罩及高可讀性材質；滯回、濾波、保持時間和頻率限制避免閃爍。無樣本的關鍵文字選擇數學上可讀的局部不透明保護；必要時使用黑白前景／底色。這是 **MODEL**，不標為像素實測。

`LegibilityReport` 的對比度、字形／紋理啟發式及 pixel evidence 分欄；降採樣最小值不是完整像素的絕對極值。`FINAL_COMPOSITE` 含字形，不能拿來當純背景計算對比度。沒有宣稱 OCR、完整字形邊緣量測或實測動態穩定度。

預設前景綁定現有 `sh_better_ui:text.primary` token 及其局部 scope／過渡值。解析只產生有效值，不改使用者 theme 定義；`requiredContrast` 保留高對比等設定解析後的真正目標。自訂 `VisualPolicy` 的七參數建構式代表明確前景，八參數版本可指定 token ID。

## 設定、指標與 Inspector

`UICorrectnessEngine.SETTINGS` 使用現有配置中心的 User Global 層，提供啟用、背景採樣、自修復、容量、放置次數、布局時間與採樣間隔。逐節點策略使用 `UIElement`／`UICorrectnessContract`。關閉自修復不會解除系統安全閘。

`metrics()` 提供階段 CPU 時間、cache hit、複驗／拒收及採樣 CPU 累計成本。GPU sampling time 和執行期 allocation 未量測時為 `-1`；離線 `correctnessBenchmark` 提供真正的 ThreadMXBean 配置量。沒有 FPS 保證。

用 `context.engine().require(UIApis.RENDER).openInspector(context, scope)` 開啟開發者檢查器。四頁讀取同一份 **凍結且帶幀號** 的快照，支援鍵盤、捲動、返回焦點及 JSON 複製。Visual 頁只對有效的材質前文字像素畫採樣對比熱圖，並標示字形未量測。它不在生產環境自動開啟。原始報告可用 `correctness.exportJson(context, scope)` 取得。
