# 山海：SH-Better-UI 共用庫

- [x] 2026-11-05 盤點主模組樞紐／商店玻璃、shader、動畫與既有呼叫者；確認本專案目前只有主模組建置骨架。
- [x] 設計草案：`docs/superpowers/specs/2026-11-05-sh-better-ui-design.md`。
- [x] 使用者確認獨立 Forge UI 依賴 jar；UI 庫不內嵌任何 jar，主模組既有 KotlinForForge runtime 封裝保留。
- [x] 實作計畫 `docs/superpowers/plans/2026-11-05-sh-better-ui.md`；抽取契約先紅後綠，共用核心／四個有效高斯 shader／可選 LDLib 材質接口已遷移。
- [x] 樞紐、商店與既有配方編輯器／picker／生成器／終端／載入畫面已接入；事件及管線只有一份。
- [x] 新庫 84 項 JUnit、7 項抽取契約全過；shader 正規化 hash 一致、色板無 ConstantValue，common 入口在禁止 client／LDLib 的 ClassLoader 中實例化成功。
- [x] 主模組來源快照編譯及 UI 定向回歸：342 通過、1 略過；另 5 項與抽取前 `0826c6d3` 的失敗 type／message 相同。非全套測試。主模組全部本次 UI 來源與快照一致。
- [x] 兩專案 reobfJar、包內類別／資源／依賴檢查通過；新庫及主模組都沒有內嵌 UI 庫，主模組不再打包已遷移的 UI 類與 shader。
- [x] 正式包 `build/libs/sh_better_ui-1.0.0.jar`，SHA-256 `E8E7824F4D3FAD940E7FC75A2B2BDBB568F7B00CD86D2D13012A6725FB402FA6`；開發包 `build/libs/sh_better_ui-1.0.0-dev.jar`，SHA-256 `24F113B7507E145A3FD6F16591DA7302538F494568F5CD67CF54BA70465EBFE0`。
- [ ] Forge 專用伺服器實際啟動；目前只有共同入口 ClassLoader 隔離證據。
- [ ] 遊戲內 UI／動畫／GUI scale／資源重載／reduceMotion 驗收。尚未部署或重啟。
