<!-- 作者：山海／dishanhai -->
# UI Intelligence & Correctness 實施計畫

審計前置：[已完成的架構審計](intelligence-audit.md)。本需求已授權設計、實作、整合及測試；沿用 `UnifiedUI`／`UIEngine`，不新增中央入口、事件總線、玻璃引擎或動畫時鐘。

## 設計與檔案責任

1. **公共契約與中央服務**：`src/kernel/java/.../kernel/api/intelligence/` 放節點宣告、座標／快照、正確性契約、結構化報告及 service key。`UIEngine` 僅新增 default accessor。`kernel/internal/CorrectnessRuntime` 負責作用域、版本、dirty、原子提交、修復後複驗及呈現回執。所有新增公共核心維持 Java 17，沒有 Minecraft classpath。
2. **空間／運動分析**：在上述 package 增加 `SpatialIntelligence`，作為現有碰撞／空間引擎的宣告和預測分析能力。矩形解算優先抽取並重用現有純 Java collision 類（FQCN 不變），不能抄寫算法。`SpatialGeometry` 是形狀／矩陣的權威。不同空間的 bounds 不得隱式比較。預測器不推進 live animation，輸出保守程度、誤差和碰撞區間。
3. **視覺分析**：`VisualIntelligence` 管理帶來源的樣本、對比度與啟發式分欄、局部材質修復、滯回及恢復。重用 `ContrastPolicy`，必要的純 Java helper 保持 FQCN 移入 kernel source set。`ClientBackgroundSampler` 負責受限 ROI／GPU reduction／PBO fence 非阻塞讀回；不把背景模型稱為最終字形量測。
4. **原生整合**：`ClientCorrectness` 將 viewport、原版安全區、主題版本／減少動效和 resource generation 接入中央流程。`ClientUiKernel` 註冊唯一新服務並在既有相位準備快照。`RenderService`／`ClientRender` 增加受管節點呈現入口，按已提交版本繪製及回報；命中只查最後實際呈現的同版資料。
5. **診斷**：結構化 JSON 匯出和可選 inspector 讀同一不可變報告；Layout、Motion、Visual、Correctness 不重新運算另一份狀態。高成本像素／軌跡疊層預設停用。所有使用者可見標籤使用語言鍵。
6. **效能與範例**：沿用 kernel benchmark source set，補 dirty idle／dense solve／motion／visual／correction 的 CPU 實測與分配量。範例依賴庫，不嵌入核心。兩平台分開構建和測試。

## 流程與安全邊界

`register/update → versioned declaration → dirty constraint collection → layout → motion envelope → visual analysis → validation → bounded correction → revalidate → immutable commit → platform draw → presented receipt / background observation → targeted invalidation`。

- 修復優先序：輸入安全、關鍵可讀性、安全區、可見性、穩定布局、連續動畫、設計一致性、裝飾。Hard > Soft > Preference 不可由低階策略覆寫。
- 修復有最大輪數／節點數／時間；無解時關閉不安全節點的互動與呈現，保留衝突報告。沒有有效快照時拒絕繪製。
- 所有結果帶宣告版本、scope generation、資源／主題版本。晚到回執與 observation 不可覆寫新版。修復計畫可回滾，但回滾也要經硬約束複驗。
- 觀測範圍有限：未知第三方 HUD、非確定未來輸入、保守包圍體、缺失／過期 GPU 樣本均在報告說明；不聲稱絕對無碰撞或真實可讀。
- 既有 imperative UI 仍可用原 API；新 correctness 保證只適用於已註冊且走快照呈現的節點。舊介面不被悄悄改變命中語義。

## 執行與驗證順序

- [x] 完成架構審計和設計邊界；確認既有未提交內容。
- [x] 公共模型／報告的非法幾何、不可變性、座標、版本測試先紅後綠。
- [x] 空間約束、確定性避讓、無解降級、動畫中段／旋轉／spring 包絡和中斷連續性測試先紅後綠。
- [x] 視覺來源、最差對比度、透明合成、修復序與動態保持時間測試先紅後綠。
- [x] 中央服務測試：作用域所有權、過期更新／計畫／觀測、idle cache、有限複驗、修復回滾、最後呈現版本命中。
- [x] 原生適配測試：服務發現、RENDER_PREPARATION 接線、snapshot draw、resource reload／resize／close、平台邊界。
- [x] 執行 Forge `kernelTest test build`、NeoForge `kernelTest test build`，檢查報告、封裝邊界與 Java 17 核心。
- [x] 執行固定負載 CPU 基準並保存原始資料；GPU／原生畫面僅依實際執行結果記錄。
- [x] 補齊接入文件、驗證報告和 `TODO.md` 的未驗證客戶端項目。

Git Bash 直接啟動本機 Gradle 8.8（Forge）及 NeoForge wrapper 所指定 Gradle；不使用會刪除 `build/libs` 的根 `gradlew.bat` 包裝器。所有日誌放 `build/correctness-verification/`。不提交、推送或部署。
