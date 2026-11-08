<!-- Author: dishanhai -->
# 可打斷動畫

`client.gui.scaled.InterruptibleAnimation` 是每個控件各自持有的純 Java 動畫入口，Forge 1.20.1 與 NeoForge 1.21.1 共用同一實作。底層復用 `Spring`，無新增依賴。此接口隨本次源碼更新提供，消費端須重新構建並使用包含該類別的 dev／正式包。

換目標會先將舊軌跡推進到事件時間。一般重定向保留位置與速度，明確反向保留位置和速率並調整方向。彈簧何時停止由預設和實際運動決定。

## 事件與繪製

每個屬性持有一個實例，事件與繪製都使用 `PageTransition.nowMs()` 或 `UiFrame.nowMs()` 的同一單調毫秒時鐘。在客戶端執行緒操作，事件時間應單調不減。暫停遊戲時 UI 仍按實際時間運動。失焦後若仍繪製就繼續運動，暫停繪製後恢復時彈簧單次最多追趕 250ms，再拆成不超過 4ms 的子步。定時混合按實際經過時間取樣。兩者都獨立於遊戲 tick 與畫面更新率。

下列是放在消費端 Screen 內的方法片段；`drawContents` 包含原有面板內容，`reducedMotion` 由消費端設定提供：

```java
private final InterruptibleAnimation panel = new InterruptibleAnimation(Spring.PANEL);
private long closeCommand;

private void setPanelOpen(boolean open) {
    long command = panel.transitionTo(open ? 1f : 0f, PageTransition.nowMs(),
            InterruptibleAnimation.Policy.REVERSE, 30);
    if (command != 0L) closeCommand = open ? 0L : command;
}

private void renderPanel(GuiGraphics graphics, float centerX, float centerY,
                         boolean reducedMotion, Runnable drawContents, Runnable afterClose) {
    float progress = panel.sample(PageTransition.nowMs(), reducedMotion);
    if (progress > 0f) GuiRenderUtil.withPanelMotion(graphics, centerX, centerY, progress, drawContents);
    if (panel.takeCompletion(closeCommand)) afterClose.run();
}
```

以上類別從 `com.dishanhai.sh_better_ui.client.gui.scaled` 匯入；`GuiGraphics` 使用對應 Minecraft 平台類別。原有 `withPanelMotion` 的外層 RGBA 合成待辦仍存在，此示例適用於預設白色／不透明的繪製入口。

開啟後再次點擊關閉、關閉途中重新開啟，都操作同一個 `panel`。關閉動畫完成前保留渲染入口，取樣後以 `takeCompletion` 領取目前命令的完成事件。命令被新目標取代或軌道被銷毀後，舊代號失效。膠囊浮層已接入同樣的流程，宿主可以用 `UiOverlayHost.reopenModal()` 將明確的開啟意圖送給正在收起的浮層。

主控面板展開時可按 Esc 收起，收起途中按已綁定的開啟快捷鍵可反向展開。重開會取消本次返回並保留同一個畫面的設定草稿、頁籤及捲動位置。啟用減少動效時，開關會立即完成。

主控頁籤和開發者圖庫的連續切換會保留當前合成畫面，將正文漸變到最後選取的頁面。子面板返回途中點擊父頁原入口，或按 Enter／空格，可讓同一個膠囊反向展開。

LDLib 的點擊回調同樣調用 `setOpen`／`toggle`，繪製回調使用 `sample` 的值；LDLib 控件由其原有流程移除，庫不自動攔截操作或銷毀控件。NeoForge 包維持原生 Screen 接口，不新增 LDLib 前置。

## 位移與延遲目標

可直接用像素作為動畫值，適用於捲動、選中指示器與多頁內容平移：

```java
private final InterruptibleAnimation offset = new InterruptibleAnimation(Spring.SCROLL);

private void moveToPage(int pageIndex, int pageWidth) {
    offset.animateTo((float) pageIndex * pageWidth, PageTransition.nowMs());
}

private float pageOffset(boolean reducedMotion) {
    return offset.sample(PageTransition.nowMs(), reducedMotion);
}
```

繪製時以 `-pageOffset` 平移內容；多頁資料、可見範圍及裁切由消費端管理。既有 `PageTransition` 保留頁面索引及 640ms 時間軸，沒有自動遷移舊畫面。需要連續改變位移目標的畫面可選用上述接口。

`animateToAfter(target, nowMs, delayMs)` 排程新的目的地，等待期間繼續朝舊目標移動，到期才切換。混合軌跡會從當前值與速度接續為彈簧。再次排程會替換尚未生效的命令，直接 `animateTo` 則取消延遲命令。同一幀跨過生效時間時，舊、新彈簧分段推進。

位移、寬高、縮放、透明度、旋轉、捲動、裁剪邊界和進度都可持有獨立標量軌道，共用一次取樣時間。簡單色彩過渡可用一條進度軌道驅動 `GuiMotion.lerpColor`。需要在任意兩色間連續改目標時，可將 ARGB 拆為四條軌道，取樣後將通道夾到 0–255 並合成。渲染與命中使用同一份取樣後的矩形，裁剪時與 viewport 取交集。縮放僅作裝飾時保留原有邏輯命中區。窗口或 GUI 比例改變時重新計算目標座標，將現有軌道重定向到新位置。

## 打斷策略與所有權

控件持有每個屬性的唯一軌道。可以按懸停 10、點擊 20、面板切換 30、頁面關閉 50 分配優先級，同一個可逆面板的開和關用同一優先級。運動中，低於當前或待執行階段優先級的命令會被拒絕。獨立的屬性各自仲裁。

| 策略 | 行為 |
| --- | --- |
| `IMMEDIATE` | 跳到目標。適用於首幀定位、直接跟手或宿主要求的立即更新。 |
| `RETARGET` | 保留值和速度。適用於懸停、捲動、改變佈局目標。 |
| `REVERSE` | 保留值及速率，初速指向相反端點。適用於展開、收起和中途重開。 |
| `BLEND` | 從取樣值按定時曲線過渡。預設 180ms，`blendTo` 可選 `LINEAR` 或 `SMOOTHSTEP`。適用於回到權威狀態及姿態混合。 |
| `QUEUE` | 按順序等待。最多八個待執行目標，滿時拒絕新命令。 |
| `IGNORE` | 軌道忙碌時，等優先級也被拒絕。空閒或嚴格更高時按重定向處理。 |
| `FINISH_THEN_SWITCH` | 等當前階段完成，只保留最新後繼目標。用於刻意要求前一階段完整播放的效果。 |

`transitionTo` 和 `blendTo` 的非零返回值是該軌道的完成代號。隊列只可領取最後階段的完成事件。後續接受的立即或重定向命令會替換整條隊列。`finish()` 和減少動效直接走到最後目標。舊版 `animateTo`、`animateToAfter`、`setOpen` 和 `toggle` 是所有者的直接命令，會清除策略隊列及代號。

## 操作契約

| 接口 | 行為 |
| --- | --- |
| `animateTo(target, nowMs)` | 任意有限標量目標，保留位置與速度 |
| `animateToAfter(target, nowMs, delayMs)` | 延遲換目標；非正延遲立即生效 |
| `setOpen(open, nowMs)` | 打開至 1、關閉至 0 |
| `toggle(nowMs)` | 以最終目標判斷開關，適用於 0..1 動畫 |
| `sample(nowMs)` | 推進並返回當前值 |
| `sample(nowMs, true)` | 減少動效：立即完成，跳過運動與延遲 |
| `stop(nowMs)` | 推進到事件時間後停在當前位置，清零速度、取消延遲 |
| `finish()` | 立即到達最終目的地，包含延遲目標 |
| `snapTo(value)` | 立即重設位置及目標，清零速度、取消延遲 |
| `value()`／`velocity()` | 查詢上次取樣的位置／速度，不推進時間 |
| `target()` | 查詢最終目的地，包含延遲或排隊目標 |
| `running()` | 有運動、延遲或排隊命令時為 true，僅查詢 |

`Spring.PANEL` 用於面板，`HOVER` 用於懸停，`SCROLL`／`INDICATOR` 用於像素值，`STAGGER` 用於無回彈入場。原始值不裁切，透明度應使用 `GuiMotion.clamp01`，縮放可復用 `GuiMotion.panelScale`。非有限目標與 `snapTo` 值會忽略，不取消既有命令；非有限初值正規化為 0。

`ShBetterUiApi.motion().animation(...)` 建立獨立軌道。一般 Screen 在 `removed()` 關閉軌道，合成式 `UiScreen` 可以在每次打開時用 `screen.context().animation(preset, initial)` 自動托管。重布局復用當前上下文和軌道，離開後重新開啟時取得新上下文。關閉軌道會停止並使完成代號失效。

## 異步查詢與業務操作

客戶端意圖控制動畫，服務端結果更新業務模型，畫面從兩者讀取各自需要的狀態。使用 `screen.context().query()` 為每個獨立的唯最新查詢建立槽。發包前用 `begin(timeoutMs)` 登記 UUID，收到包後以同一 UUID 呼叫 `receive(id, accepted, value)`。這個入口可從網路執行緒呼叫，會交給 Minecraft 客戶端執行緒檢查 UUID、連接身份及超時。從 Screen tick 讀取 `snapshot()` 取得狀態和資料。狀態沒變時會復用快照，可用物件身份判斷是否已處理該次結果。

關閉面板時先讓動畫轉向，並在同一事件呼叫 `cancelQuery()`。頁面移除時上下文會釋放它持有的查詢。協議支援取消時可在 `begin(timeoutMs, cancel)` 傳入取消發包函數。它只會在原連接可用時執行。查詢被替換、超時、取消或連接更換後，晚到或重複回執不能寫回舊頁面。

業務模型在頁面外持有 `ShBetterUiApi.client().operation()`，每個需互斥的邏輯操作持有一個槽。所有點擊可立即播放局部反饋，只有 `begin` 返回 UUID 時才提交。服務端用此 ID 做幂等記錄，確認後才將 `SUCCEEDED` 視為業務成功。收到 `REJECTED` 時顯示本地化的拒絕資訊，並從當前視覺值混合回包內或最新查詢取得的權威值。業務模型處理終態後呼叫 `acknowledgeReceipt(id)`。處理前保留回執並擋住重複提交，避免下一次點擊覆蓋結果。

`TIMED_OUT` 和 `DISCONNECTED` 的未決操作仍保持鎖定。原連接上的晚到回執仍可結案，換連接後應在新連接查詢該 ID 的服務端記錄，再用 `reconcile(id, accepted, authority)` 確認。協議應保留未決 ID，讓退出或重連後能重建對帳流程。查詢取消和頁面釋放只處理本地查詢生命週期。等待、超時、離線、恢復和拒絕的提示由宿主在語言檔提供。

## 驗收

- 從「開發者面板 → 可打断动画」打開示例。連續點擊同一個按鈕，或聚焦後連按空格，檢查膠囊位移中途反向、透明度連續接續及目標文字立即更新。切換示例內的「减少动效」後重試。返回時檢查焦點回到示例入口。
- 執行兩平台的 Gradle `test`。 `InterruptibleAnimationTest` 覆蓋反向、連續懸停、相同幀打斷、優先級、隊列、長幀與 20／144 FPS。 `UiRequestTest` 覆蓋查詢亂序、超時、重複回執、斷線和對帳。 `UiScreenTest` 覆蓋重布局、關閉重開、50／200／1000ms 等待與拒絕後平滑恢復。
- 在兩平台的開發者面板打開「確認弹窗」，展開途中按 Esc。確認按下立即反向、來源按鈕在收合後獲得焦點。用宿主的重開入口在收合時呼叫 `reopenModal()`，確認當前輪廓反向。分別以 GUI 比例 2／3 和低 FPS 重複，並驗證減少動效、Tooltip 及鍵盤焦點。
- 在已接入封包的宿主依序切換 A、B、C，安排 C、A、B 的回執順序，關閉面板後再補發舊回執。為同一操作模擬 50／200／1000ms、超時、斷線重連、拒絕和重複回執。記錄服務端收到的 UUID、執行次數、對帳結果和客戶端顯示值。
