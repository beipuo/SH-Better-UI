<!-- Author: dishanhai -->
# 待辦

2026-11-07 複核發現，詳見 `CODE_REVIEW-20261107.md`；進度如下。

- [x] Forge／NeoForge 共用的 `WaveOrigin` 宣告已對齊 Minecraft 的雙分量 uniform 上傳。
- [ ] 模糊 process 異常後釋放舊 PostChain，再進入重試。
- [ ] `withPanelMotion` 保存、合成及還原外層 RGBA。
- [x] 延遲彈簧在目標生效前保持原軌跡，跨過生效時間時分段推進。
- [ ] 更新 Python 抽取契約的版本解析與檢查入口。
- [ ] Minecraft 驗收新效果：連點、移動／縮放、裁剪、多控件、減少動效、三種玻璃配色；確認文字圖示清晰。

## 可打斷動畫（2026-11-07）

- [ ] Minecraft 消費畫面驗收 `InterruptibleAnimation`：連續開關、快速反向、多頁位移、延遲換目標、停止／重開與減少動效，含多 GUI scale；關閉動畫完成前保留渲染回調。

## 捲動換頁

- [ ] 消費畫面接入 `ScrollPageTurn` 後，在 Minecraft 驗收滾輪與觸控板的邊界換頁、短頁連續捲動及子控件優先處理。

## 圖形化顯示 API

- [ ] 接入消費畫面後，在 Forge／NeoForge 遊戲內驗收 16×16 圖示、深淺配色、GUI 縮放、自訂欄數與分頁容量、長標籤裁切、翻頁邊界、鍵盤焦點及旁白。

## 小彈窗提醒 API

- [ ] 在 Forge／NeoForge 客戶端驗收 HUD 和 Screen 間切換、與原版 toast 及玻璃覆層的層級、多 GUI 比例、F1、旁白、減少動效與退出世界時清空佇列。

## 網路風格 UI API

- [ ] 接入消費畫面後，在 Forge／NeoForge 驗收 16×16 圖示與圓角描邊、多 GUI 比例、長標籤、純鍵盤與旁白、輸入框游標和選取、高對比及減少動效，以及開關等待／成功／失敗後的回饋。
- [ ] 在 Forge／NeoForge 實機驗收 `NetworkScreen` 模板的世界背景、七個頁籤與獨立新增按鈕、窄窗折行、翻頁焦點、編輯草稿保留及返回父畫面。

## 搜尋與填充

- [ ] 消費畫面接入 `SearchField` 後，在 Forge／NeoForge 遊戲內驗收灰字裁切、中文輸入、鍵盤與滑鼠焦點、候選翻轉／捲動、旁白及各 GUI 比例。
- [ ] 在 Forge／NeoForge 客戶端驗收搜尋、模組及標籤候選與控件、物品／流體、配方 tooltip 的交疊，含長候選提示、GUI 縮放和玻璃後處理畫面。
- [ ] 在 FTBQ AE 自動提交器重現長候選貼近 JEI 側欄的情境，確認提示換行後留在搜尋欄寬內，並驗收上下翻轉和候選收合後的 JEI 懸停。

## JEI 搜尋與預覽

- [ ] 在 Forge／NeoForge 的消費畫面驗收物品與流體變體、原版及模組配方預覽、JEI 側欄避讓、配方返回與退世界重進；另驗收未安裝 JEI 的畫面。

## 物品與流體選取

- [ ] 在 Forge／NeoForge 的消費畫面驗收 JEI／註冊表／自建候選、物品與流體原生提示、標籤重載、單選與多選上限、草稿撤銷／取消、來源失效提示及原生副本寫回。
- [ ] 驗收從 JEI 返回與 GUI 縮放後的搜尋、篩選、捲動和草稿，以及純鍵盤、旁白、高對比與減少動效。

## Slate 暗黑模式

- [ ] Minecraft 驗收注意力切換回饋。檢查 `UiButton` 懸停提示、長標籤與旁白，`UiDisclosure` 展開／收起標記，JEI 獲取方式／用途的快速反向切換、配方覆層命中、窄版裁切、GUI scale、純鍵盤、高對比及減少動效。離線 Java2D 預覽僅驗證繪製輸出與動畫參數。

- [ ] 消費畫面接入 `DarkModeTheme` 後，在 Minecraft 驗收鍵盤焦點、按鈕按壓、輸入框游標／選取、裁切、GUI scale 與減少動效。
- [ ] 消費畫面接入直接操作 API 後，驗收拖曳落點、失焦取消、列表反向、縮放錨點、視口裁切及鍵盤操作。
- [ ] 消費畫面接入共用控制 API 後，在兩個平台驗收確認畫面焦點與 Escape、父畫面快照還原、長翻譯捲動、旁白、高對比及模糊回退。

## Liquid Glass UI API

- [x] 加入 Forge／NeoForge 共用的 `LiquidGlassTheme`，`UiButton` 與 `SearchField` 可選用新風格。
- [x] 圓形／膠囊按鈕與焦點環改為解析距離 shader，背景圓角加入像素覆蓋率過渡；離屏檢查 1×／2×／4× 與 1.96× 按下縮放。
- [x] 新增 `UiButton.setBackgroundBlurRadius()`、getter 與 `LiquidGlassTheme.button()` 半徑重載，預設 12，0 關閉、最大 32；降低底色遮蔽並啟用密集高斯取樣。
- [ ] 在兩平台的消費畫面驗收多種 GUI 縮放、亮暗背景、裁切、快速點擊、焦點環、中文輸入、候選翻轉與原生提示層級，並檢查背景半徑 0／12／32、資源重載、停用模糊、高對比、減少動效與第三方 shader。

## 中性玻璃顯示維護（2026-11-07）

- [ ] 在 Forge／NeoForge 客戶端驗收 `sh_better_ui-client.toml` 的首次生成、重新載入及毛玻璃五項設定，並檢查關閉後的底板、文字與切頁動效。
- [ ] Minecraft 驗收淺灰／煙灰的 65 點高斯、低強度提亮、邊緣受光與字形柔光；包含 GUI scale、亮暗背景、剪裁、動畫淡出、字體資源包及第三方 shader。
- [ ] 消費端原生 `graphics.drawString()` 按需接入 `GlassSurface.drawString()`；本輪庫內已接入煙灰主題居中文字，未改消費模組。
- [ ] 遊戲中量測高解析度、多層面板的模糊成本；離屏 shader 預覽不代表實機效能或介面驗收。

## NeoForge 1.21.1（2026-11-07）

- [x] 獨立 Gradle 9.2.1／ModDevGradle 2.0.148 建置，Forge 1.20.1 保留。
- [x] NeoForge 入口、tooltip、資源重載與模糊 API 適配；外部 KFF NeoForge 5.10.0。
- [x] 109 項單元測試與正式 jar 隔離檢查通過；根 Forge `test build` 回歸成功。
- [x] WaveOrigin vec2 上傳契約與資源測試由兩平台共用。
- [ ] Minecraft 1.21.1 客戶端玻璃／模糊／文字圖示與 tooltip 實機驗收，含 F3+T、關閉／重開、GUI scale 與第三方 shader。
- [ ] LDLib NeoForge 接口另行支援；目前 NeoForge 包只提供原生 Screen API。
