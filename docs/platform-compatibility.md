<!-- 作者：山海／dishanhai -->
# Forge 與 NeoForge 平台相容性

## 選擇對應產物

| 平台 | 本專案工具鏈 | 消費端編譯依賴 | 安裝至遊戲 |
| --- | --- | --- | --- |
| Forge 1.20.1 · 47.4.16 | 本地 Gradle 8.8、Parchment 2023.09.03、Java 17 遊戲與正式類別 | `build/libs/sh_better_ui-1.0.5-dev.jar` | `build/libs/sh_better_ui-1.0.5.jar`，需 KotlinForForge `[4.12.0,5.0.0)` |
| NeoForge 1.21.1 · 21.1.251 | NeoForge wrapper 9.2.1、ModDevGradle 2.0.148、Java 21 | `neoforge/build/libs/sh_better_ui-neoforge-1.21.1-1.0.5.jar` | 同一 NeoForge JAR，需 KotlinForForge `[5.10.0,6)` |

兩個發行包的純內核以 JDK 17 編譯，`kernelTest` 亦使用 JDK 17。Forge 現有一般測試需要 JDK 21，整套驗證需兩個工具鏈。Forge `-raw` 包仍處於開發映射，`releaseJar` 會使用 `reobfJar` 的結果。消費模組在 Loader metadata 宣告依賴 `sh_better_ui`，以外部依賴方式編譯，並保留獨立的消費 JAR 和 UI 庫 JAR。 [Forge 建置](../build.gradle) · [NeoForge 建置](../neoforge/build.gradle) · [第三方範例](../examples/kernel-extension/README.md)

JEI、LDLib 和 KubeJS 維持選用。使用原生控件及內核服務時可省略。若用原有 `platform().ldlib()` 或 KubeJS 外掛，採該端對應版本和分組。平台差異限於 Loader 接線、原生入口、版本相關的 Minecraft 類別及圖形／資源適配。 [原有 API](../API_REFERENCE.md) · [KubeJS](../KUBEJS.md)

## 原生路由

- 平台於客戶端設定階段安裝內核，在實體伺服器環境 `UnifiedUI` 不會發布。原生 Screen、連線、視窗和資源重載由宿主同步。入口不依賴世界存在，標題畫面可使用。以 `ui.platform().viewport()` 讀取 GUI 邏輯尺寸、縮放比例及焦點快照。 [Forge 橋接](../src/main/java/com/dishanhai/sh_better_ui/client/ForgePlatformBridge.java) · [NeoForge 橋接](../neoforge/src/main/java/com/dishanhai/sh_better_ui/client/NeoForgePlatformBridge.java)
- 原生輸入交付 `NativeInput.Pointer`、`Key` 或 `Character`。Forge 的 wheel 只有垂直軸，轉成 `(0, dy)`。NeoForge 保留水平與垂直軸。指標座標用縮放後的 GUI 單位，drag 差值採對應 Loader 回呼。鍵盤區分 press、repeat、release，字元沿用 UTF-16 單元，補充字元以兩次交付表示。觀察型 `POINTER_MOVED` 不會取消原版移動。
- 原有模態和捕獲路由保留優先順序。只有活動 Screen 的合法輸入目標或捕獲可要求阻斷。內核沒有活動目標時，將未被既有路由處理的事件交還原版。失焦、關閉頁面或授權撤銷會釋放捕獲。配對的 release 在該手勢由庫攔截時仍會清理。 [事件與權限](permissions.md)
- 平台先執行 CPU 相位，再於相符 `ScreenEvent.Render.Post` 或 HUD 後置事件推進 `RENDER`，每幀至多一次。畫圖回呼結束後恢復 pose 和裁剪。畫面覆層遮蔽或 HUD 隱藏時延後提交的 pass。`RenderService.Context` 的 `fill`、`clip` 右邊及下邊是邊界座標。Z 偏移量接受 `-99` 至 `99`，可在 `CONTENT`、`OVERLAY`、`TOOLTIP` 各層調整深度。需要原生 `GuiGraphics` 的既有繪圖繼續放在 `UiScreen` 回呼。 [調度](scheduler.md)
- 資源重載經 Loader 的準備屏障後回到客戶端執行緒。內核只提交仍相符的世代。清除受管素材快取後更新 `ResourcesService.version()` 並通知訂閱者。資源包可提供主題及素材。擴充模組只由 Loader 的模組檔宣告發現。 [資源包與主題](DYNAMIC_THEMES.md) · [模組發現](module-development.md)

## 重跑雙端驗證

由儲存庫根目錄在 Git Bash 執行。一般建置驗證正式 JAR 和既有測試。`kernelTest` 可單獨執行，兩端讀取同一份 Java 17 契約測試。

```bash
./gradle-install/gradle-8.8/bin/gradle kernelTest build --offline --no-daemon --console=plain
./neoforge/gradlew -p neoforge kernelTest build --offline --no-daemon --console=plain
```

原生回歸使用獨立的 `runClient` 煙測。以各自 Loader 啟動 Minecraft、執行原生事件與渲染，並輸出成功與停止標記。

```bash
./gradle-install/gradle-8.8/bin/gradle -I gradle/kernel-smoke.init.gradle runClient --offline --no-daemon --console=plain
./neoforge/gradlew -p neoforge -I ../gradle/kernel-smoke.init.gradle runClient --offline --no-daemon --console=plain
```

實際第三方模組編譯、封裝及客戶端啟動命令另見 [範例專案](../examples/kernel-extension/README.md)。各階段執行結果與尚未驗證項目見 [驗證紀錄](kernel-migration-plan.md)。

## 排查

| 現象 | 先確認 |
| --- | --- |
| `NoClassDefFoundError` 或連結失敗 | 消費端與遊戲是否分別使用正確映射、Loader 版本及 JDK。確認獨立安裝的 mod ID `sh_better_ui`，以及 KotlinForForge 相依。 |
| `UnifiedUI.get()` 拋例外 | 呼叫點是否已進入客戶端遊戲回呼。模組 `start` 改用 `context.engine()`。 |
| 自訂服務未出現 | 檢查消費 JAR 的描述檔、mod ID namespace、類別所屬 Loader Module、契約版本及依賴。查看啟動日誌的拒絕原因。 |
| 畫面掛載遭拒 | 必須是由自己模組實作的活動 Screen，或以 `ScreenService.create` 建立並已開啟的 `UiScreen`。同一世代和 ID 使用已取得的樹。 |
| 圖層未畫或點擊走原版 | 核對權限、作用域、Screen／HUD 表面、遮罩、視窗焦點與 `TaskHandle` 狀態。原生事件的 `markHandled()` 只做紀錄。 |
