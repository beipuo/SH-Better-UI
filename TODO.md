<!-- Author: dishanhai -->
# 待辦

2026-11-07 複核發現，詳見 `CODE_REVIEW-20261107.md`；進度如下。

- [ ] 對齊 `WaveOrigin` 的 GLSL 宣告與 Minecraft uniform 上傳類型。
- [ ] 模糊 process 異常後釋放舊 PostChain，再進入重試。
- [ ] `withPanelMotion` 保存、合成及還原外層 RGBA。
- [x] 延遲彈簧在目標生效前保持原軌跡，跨過生效時間時分段推進。
- [ ] 更新 Python 抽取契約的版本解析與檢查入口。
- [ ] Minecraft 驗收新效果：連點、移動／縮放、裁剪、多控件、減少動效、三種玻璃配色；確認文字圖示清晰。

## 可打斷動畫（2026-11-07）

- [ ] Minecraft 消費畫面驗收 `InterruptibleAnimation`：連續開關、快速反向、多頁位移、延遲換目標、停止／重開與減少動效，含多 GUI scale；關閉動畫完成前保留渲染回調。

## 中性玻璃顯示維護（2026-11-07）

- [ ] Minecraft 驗收淺灰／煙灰的 65 點高斯、低強度提亮、邊緣受光與字形柔光；包含 GUI scale、亮暗背景、剪裁、動畫淡出、字體資源包及第三方 shader。
- [ ] 消費端原生 `graphics.drawString()` 按需接入 `GlassSurface.drawString()`；本輪庫內已接入煙灰主題居中文字，未改消費模組。
- [ ] 遊戲中量測高解析度、多層面板的模糊成本；離屏 shader 預覽不代表實機效能或介面驗收。

## NeoForge 1.21.1（2026-11-07）

- [x] 獨立 Gradle 9.2.1／ModDevGradle 2.0.148 建置，Forge 1.20.1 保留。
- [x] NeoForge 入口、tooltip、資源重載與模糊 API 適配；外部 KFF NeoForge 5.10.0。
- [x] 109 項單元測試與正式 jar 隔離檢查通過；根 Forge `test build` 回歸成功。
- [x] NeoForge 打包修正 WaveOrigin vec2 上傳契約，新增資源測試先紅後綠；原 Forge shader 待辦保留。
- [ ] Minecraft 1.21.1 客戶端玻璃／模糊／文字圖示與 tooltip 實機驗收，含 F3+T、關閉／重開、GUI scale 與第三方 shader。
- [ ] LDLib NeoForge 接口另行支援；目前 NeoForge 包只提供原生 Screen API。
