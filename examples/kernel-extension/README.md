<!-- 作者：山海／dishanhai -->
# 第三方模組範例

本範例是獨立的用戶端模組。Forge 1.20.1 和 NeoForge 1.21.1 共用 `common/src/main`，各自提供 Loader 入口、相依宣告和資源包格式。模組 ID 為 `shbui_kernel_example`。

## 使用方式

標題畫面右上角有「UI 核心範例」。可用滑鼠點選，也可用 Tab 移動焦點後按 Enter 開啟。計數器每次加 3，能復原上一次操作。按 Esc 或返回鍵會回到標題畫面並還原焦點。重開頁面會取得新的作用域及初始計數。

- [ExampleClient](common/src/main/java/com/dishanhai/shbui_kernel_example/ExampleClient.java) 在 UI 引擎可用時從 `UnifiedUI.get()` 查詢自訂能力，再以 `CounterService.KEY` 取得服務。
- [ExampleUiModule](common/src/main/java/com/dishanhai/shbui_kernel_example/ExampleUiModule.java) 在 `register` 註冊同命名空間的服務，在 `start` 取得 `UIApis.SCREENS` 和可選主題服務，並宣告 `STEP` 的型別和驗證範圍。遊戲中的元件層值為 3，其他作用域仍讀到預設值 1。
- 模組宣告可選的 `UIApis.SYSTEM_SCREEN_READER`。缺少該能力時，按鈕使用 Minecraft 內建旁白。動態主題不可用時，畫面使用既有預設樣式。
- [探索描述檔](common/src/main/resources/META-INF/sh_better_ui.modules) 由 Loader 從模組自己的 JAR 讀取。實體畫面關閉時，引擎關閉掛載的樹及元件作用域。引擎停止時，模組也會銷毀可能仍開啟的畫面。

## 建置

從儲存庫根目錄以 Git Bash 執行。先建置 SH-Better-UI 兩端的依賴 JAR，並將 `JAVA_HOME` 與 Gradle Java 工具鏈設為 `C:/GRAALVM/graalvm-jdk-21.0.6+8.1`。本例以 Forge Gradle 8.8 和 NeoForge 的 Gradle 9.2.1 啟動，兩端均使用 Java 21；Forge 以 `--release 17` 保留舊版位元組碼相容性，NeoForge 輸出 Java 21。Forge 依賴工作區的 `libs/kotlinforforge-4.12.0-all.jar`；NeoForge 開發啟動會使用 Maven 上的 KotlinForForge 5.10.0。依賴尚未下載時需在線執行一次。`build` 不會啟動遊戲；以下客戶端煙測命令是另行授權時的操作說明，本輪不執行。

```bash
./gradle-install/gradle-8.8/bin/gradle -p examples/kernel-extension/forge build --offline --no-daemon --console=plain
./neoforge/gradlew -p examples/kernel-extension/neoforge build --offline --no-daemon --console=plain
```

| 平台 | 編譯依賴（預設） | 正式範例成品 |
| --- | --- | --- |
| Forge | `build/libs/sh_better_ui-1.0.5-dev.jar` | `forge/build/libs/shbui-kernel-example-forge-1.0.0.jar` |
| NeoForge | `neoforge/build/libs/sh_better_ui-neoforge-1.21.1-1.0.5.jar` | `neoforge/build/libs/shbui-kernel-example-neoforge-1.0.0.jar` |

用 `-PshBetterUiJar=<絕對路徑>` 選擇其他對應 JAR，Forge 另外支援 `-PkotlinForForgeJar=<絕對路徑>`。Forge 的開發環境使用 Mojmap 的 `-dev.jar`，正式啟動器使用重新映射過的正式 JAR。正式安裝時，把範例、對應版本的 SH-Better-UI 和 KotlinForForge 各自放進 `mods/`。

## 客戶端煙測

```bash
./gradle-install/gradle-8.8/bin/gradle -p examples/kernel-extension/forge runClient -PexampleSmoke --offline --no-daemon --console=plain
./neoforge/gradlew -p examples/kernel-extension/neoforge runClient -PexampleSmoke --offline --no-daemon --console=plain
```

啟動工作將範例和程式庫 JAR 複製到各範例工程的 `build/example-smoke/mods/`。Forge 使用 Mojmap 的兩份開發 JAR，另複製 KotlinForForge 的發行 JAR。NeoForge 使用兩份正式 JAR，並由 ModDevGradle 將 KotlinForForge Maven 依賴加入啟動 classpath。煙測驗證兩個獨立的命名模組、能力降級、設定隔離、Enter／Esc、復原、畫面和模組停止，並在 `build/example-smoke/` 留下 `screen.png`、`success.txt` 及 `stopped.txt`。省略 `-PexampleSmoke` 可在 `build/example-client/` 手動操作。
