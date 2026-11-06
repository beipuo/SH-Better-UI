# SH-Better-UI

作者：山海／dishanhai。

Forge 1.20.1 的共用玻璃 UI 庫，提供深色與中性淺色材質、局部高斯／方向模糊、彈簧動畫及可選 LDLib 材質接口。以獨立模組 jar 發行，消費模組不內嵌 UI 庫，更新時直接替換 UI 庫 jar。

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

正式包為 `build/libs/sh_better_ui-1.0.3.jar`，原始重映射結果為 `build/reobfJar/output.jar`。開發包為 `build/libs/sh_better_ui-1.0.3-dev.jar`，只能用於編譯與開發環境。`raw` 包是建置中間產物，不能放入遊戲。

## 消費端接入

編譯時使用 dev 包，發行時同時安裝獨立正式 UI 庫。可透過 `publishToMavenLocal` 發布 `com.dishanhai:sh_better_ui:1.0.2:dev` 及 sources，或使用本地檔案依賴：

```groovy
implementation files('../SH-Better-UI/build/libs/sh_better_ui-1.0.3-dev.jar')
```

消費端 `mods.toml` 宣告 `sh_better_ui` 版本範圍 `[1.0.2,2.0.0)`，不得再打入同一份 UI 類別或 shader。`1.0.1` 提供星律煙灰材質及雙樣式 tooltip API；原 `1.0.0` 不具備這些入口。`1.0.2` 將煙灰面板調為中等模糊（12 GUI 單位半徑），保留其他樣式。消費端在客戶端初始化時以 `GaussBlur.allowScreen(predicate)` 註冊允許的畫面；庫負責唯一的幀末事件與資源生命週期。

`gt_shanhai:ultimate_terminal` 的設定與報告畫面、`gt_shanhai:pattern_generator` 的生成畫面使用本庫的玻璃材質、模糊、彈簧與按鈕回饋。換頁時間軸由 `PageTransition` 提供；生成預覽的錯峰入場及捲動模糊強度由 `GuiMotion` 提供。畫面的欄位、物品資料與操作流程仍由 `gt_shanhai` 處理。

資源重載會在渲染執行緒釋放舊管線；若目前仍開著已註冊的玻璃畫面，立即以新資源預熱管線，無須關閉重開。登出則只釋放資源。遊戲驗收應包含保持畫面開啟時 F3+T，確認模糊恢復且文字／圖示仍清晰。

`1.0.3` 新增 `GlassTheme.SMOKE`，共用星律的 `GlassColors.SMOKE` 與 12 GUI 單位背景模糊。消費端使用此主題時，最低依賴版本須為 `[1.0.3,2.0.0)`。商店可並存經典、深藍動態玻璃與中性煙灰；選擇器跟隨所選主題，煙灰內層控件不重複模糊。

通用接口位於 `com.dishanhai.sh_better_ui.client.glass`、`client.blur` 與 `client.gui.scaled`。在面板內容之前繪製玻璃底，再畫文字與圖示：

```java
var theme = GlassTheme.GLASS;
theme.rootShadow(graphics, x, y, width, height);
theme.backdrop(graphics, x, y, width, height);
theme.box(graphics, x, y, width, height, GlassPalette.ROOT_EDGE, theme.panelBg);
```

`compat.ldlib.GlassPanelTexture` 提供 `root()`、`panel()`、`display()`、`page()` 與 `button()` 材質工廠。只有 LDLib 消費端引用此套件；每個按鈕各自建立 `button()`，讓 hover 彈簧保持獨立。

### 中性淺色玻璃

參考 `src_collection/collection/liquid-glass-react-master` 的背景模糊、邊緣折射與受光亮邊思路，沿用本庫既有 shader 實作。`GlassStyle.CLEAR` 保持背景本色並輕微提亮；`GlassSurface.panel()` 與 `control()` 提供原生 Screen 的完整底板，文字及圖示須在其後繪製。底板的不透明度足以在深色世界背景上承載深色文字；shader 不可用時仍有淺色半透明底。

```java
GlassSurface.panel(graphics, x, y, width, height);
graphics.drawString(font, title, x + 8, y + 8, GlassColors.CLEAR.TEXT, false);
```

LDLib 可用 `GlassPanelTexture.clearPanel()` 與 `clearButton()`；按鈕已位於毛玻璃面板內時用 `clearButtonOverlay()`，避免每個按鈕重複執行背景模糊。`GlassColors.select(clear)` 提供相應文字、卡片與控制項顏色。原有深色入口與色板保持不變。

主模組設定頁新增「終極終端與樣板生成工具使用淺色玻璃」選項，寫入本機 `config/gt_shanhai/ui-client.toml`，預設關閉，重新開啟相關畫面後生效；終極終端的設定與報告畫面共用此選項。

庫不包含交易、機器、配方、AE 或 GTCEu 邏輯。遊戲內外觀、第三方 shader 相容及多種 GUI scale 仍須實機驗收。
