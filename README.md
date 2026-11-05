# SH-Better-UI

作者：山海／dishanhai。

Forge 1.20.1 的共用玻璃 UI 庫，提供深色半透明材質、局部高斯／方向模糊、彈簧動畫及可選 LDLib 材質接口。以獨立模組 jar 發行，消費模組不內嵌 UI 庫，更新時直接替換 UI 庫 jar。

## 環境與依賴

- Minecraft 1.20.1、Forge 47.4.16 或更高、Java 21。
- 必需 KotlinForForge 4.12.0（2.2.21 runtime），由整合包單獨提供；本庫不內嵌任何 jar。
- 使用 LDLib 接口時另裝 `ldlib-forge-1.20.1-1.0.33.b.jar`；原生 Screen 使用者無須安裝 LDLib。
- 開發時將 `kotlinforforge-4.12.0-all.jar` 及 `ldlib-forge-1.20.1-1.0.33.b.jar` 放入 `libs/`。

## 建置

執行 `build-local.ps1` 或 `簡易構建.bat`，均使用本地 Gradle 8.8／JDK 21，包含 JUnit 與 reobfJar。

`簡易構建.bat` 使用 UTF-8 無 BOM／Windows CRLF 換行，請保留此格式。雙擊執行後會保留結果視窗；自動化呼叫可傳入 `--no-pause`，退出碼沿用建置結果。

```powershell
.\gradle-install\gradle-8.8\bin\gradle.bat build --offline --no-daemon --console=plain
```

正式包為 `build/libs/sh_better_ui-1.0.0.jar`，原始重映射結果為 `build/reobfJar/output.jar`。開發包為 `build/libs/sh_better_ui-1.0.0-dev.jar`，只能用於編譯與開發環境。`raw` 包是建置中間產物，不能放入遊戲。

## 消費端接入

編譯時使用 dev 包，發行時同時安裝獨立正式 UI 庫。可透過 `publishToMavenLocal` 發布 `com.dishanhai:sh_better_ui:1.0.0:dev` 及 sources，或使用本地檔案依賴：

```groovy
implementation files('../SH-Better-UI/build/libs/sh_better_ui-1.0.0-dev.jar')
```

消費端 `mods.toml` 宣告 `sh_better_ui` 版本範圍 `[1.0.0,2.0.0)`，不得再打入同一份 UI 類別或 shader。消費端在客戶端初始化時以 `GaussBlur.allowScreen(predicate)` 註冊允許的畫面；庫負責唯一的幀末事件與資源生命週期。

`gt_shanhai:ultimate_terminal` 的設定與報告畫面、`gt_shanhai:pattern_generator` 的生成畫面使用本庫的玻璃材質、模糊、彈簧與按鈕回饋。換頁時間軸由 `PageTransition` 提供；生成預覽的錯峰入場及捲動模糊強度由 `GuiMotion` 提供。畫面的欄位、物品資料與操作流程仍由 `gt_shanhai` 處理。

資源重載會在渲染執行緒釋放舊管線；若目前仍開著已註冊的玻璃畫面，立即以新資源預熱管線，無須關閉重開。登出則只釋放資源。遊戲驗收應包含保持畫面開啟時 F3+T，確認模糊恢復且文字／圖示仍清晰。

通用接口位於 `com.dishanhai.sh_better_ui.client.glass`、`client.blur` 與 `client.gui.scaled`。在面板內容之前繪製玻璃底，再畫文字與圖示：

```java
var theme = GlassTheme.GLASS;
theme.rootShadow(graphics, x, y, width, height);
theme.backdrop(graphics, x, y, width, height);
theme.box(graphics, x, y, width, height, GlassPalette.ROOT_EDGE, theme.panelBg);
```

`compat.ldlib.GlassPanelTexture` 提供 `root()`、`panel()`、`display()`、`page()` 與 `button()` 材質工廠。只有 LDLib 消費端引用此套件；每個按鈕各自建立 `button()`，讓 hover 彈簧保持獨立。

庫不包含交易、機器、配方、AE 或 GTCEu 邏輯。遊戲內外觀、第三方 shader 相容及多種 GUI scale 仍須實機驗收。
