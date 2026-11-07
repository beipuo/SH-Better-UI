# NeoForge 1.21.1 驗證記錄

作者：dishanhai；日期：2026-11-07。

## 初次平台接入的環境與產物

- Java 21.0.11；Gradle 9.2.1；ModDevGradle 2.0.148；NeoForge 21.1.265。
- Kotlin compiler 2.2.21，外部 KotlinForForge NeoForge 5.10.0（Kotlin 2.2.20）。
- 正式 jar：`build/libs/sh_better_ui-neoforge-1.21.1-1.0.3.jar`，87,816 bytes。
- SHA256：`DAF607E8C6E888A766FE478268FDDC7A888F845280D43F80417A36B448D37D52`。

## 已執行

在 `neoforge/`：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.11'
$env:TEMP = 'C:\jtmp'
$env:TMP = 'C:\jtmp'
.\gradlew.bat test build --no-daemon --console=plain
```

- `BUILD SUCCESSFUL`，109 項 JUnit 測試，0 失敗／錯誤／跳過。包含共用入口的伺服器 ClassLoader 隔離、新增 NeoForge 入口註解測試及實際打包 shader uniform 契約測試。
- `verifyLibraryJar` 確認必要 class、NeoForge 模組資訊及 shader 存在，不包含 Kotlin／KFF、GT、LDLib adapter 或舊 Forge `mods.toml`。
- `pack.mcmeta` 已輸出 Minecraft 1.21.1 resource pack format 34。
- 證據：`build-verified.log`（108 項初次平台測試）、`shader-verified.log`（109 項最終結果）、`build/test-results/test/TEST-*.xml`。

根目錄另執行 `gradle-install/gradle-8.8/bin/gradle.bat test build --offline --no-daemon --console=plain`，`BUILD SUCCESSFUL`（8 秒），原有測試由 Gradle 判定 UP-TO-DATE；未修改根平台 Java／Kotlin／shader。

## 紅燈與修正

- `compile-red.log`：5.12.0 的 Kotlin 2.4 metadata 與現有 compiler 不相容，改接已核對官方 POM 的 5.10.0。
- `compile-api-red.log`：Kotlin 通過後，舊 Forge 入口／事件、ResourceLocation constructor、Minecraft partial tick 等 35 個錯誤；平台覆寫後編譯通過。
- `build-green.log`：測試缺少主來源集的 Minecraft／NeoForge API 及 ASM；接回主 compile/runtime classpath 並新增 test-only ASM 後 108 項通過。
- `shader-red.log`：新增實際資源契約測試先驗出 `WaveOrigin` 的 JSON `float/count=2` 與 GLSL `float[2]` 不匹配；NeoForge `processResources` 限定改為 `vec2` 並調整讀取，`shader-verified.log` 109 項通過。原 Forge shader 未改。資源任務新增輸入屬性確保既有 Gradle cache 也會套用修正。

## 尚未驗證與平台範圍

- 未啟動 Minecraft 客戶端驗收玻璃外觀、OpenGL shader、F3+T、關閉／重開、GUI scale、tooltip 或第三方 shader 相容性。單元測試不等同視覺與 GPU 證據。
- Nexus 專用伺服器整合由消費專案分開記錄；本庫入口隔離單元測試只證明 client／LDLib 不會因共同入口而載入。
- NeoForge 不包含 Forge 1.20.1 專用 LDLib adapter；原生 Screen 公開 API 保持。
- 根目錄 `CODE_REVIEW-20261107.md`／`TODO.md` 的既有渲染與動效待辦仍由原專案維護。WaveOrigin 僅修正 NeoForge 打包資源；其餘問題未擴修，不能以本次編譯／測試宣稱全部修復。
- 初次平台接入驗證時未部署、未改動既有整合包，尚未提交或推送 Git。

## 共用動畫與玻璃維護後的驗證

- 2026-11-07 後續加入可打斷動畫與中性玻璃維護，共用來源與平台模糊接口一併回歸。
- NeoForge `test build --offline --no-daemon --console=plain` 成功，137 項 JUnit；Forge 同一命令成功，135 項 JUnit；均為 0 failures/errors。
- NeoForge 正式包 SHA-256：`015EE3D6CCBF3E9BCD14B90DA8C1A2AD219CB7B2F0FCBA26A3CA5A607C599BFE`。初次接入的 109 項／舊 hash 僅作歷史證據。
- 共用玻璃 shader 已完成離屏 GPU 預覽；字體、完整介面、平台事件與遊戲效果仍待 Minecraft 驗收。詳見 [玻璃維護紀錄](../docs/neutral-glass-maintenance.md) 與 [TODO](../TODO.md)。
