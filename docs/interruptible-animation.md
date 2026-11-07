<!-- Author: dishanhai -->
# 可打斷動畫

`client.gui.scaled.InterruptibleAnimation` 是每個控件各自持有的純 Java 動畫入口，Forge 1.20.1 與 NeoForge 1.21.1 共用同一實作。底層復用 `Spring`，無新增依賴。此接口隨本次源碼更新提供，消費端須重新構建並使用包含該類別的 dev／正式包。

開關、反向與換目標時，先推進舊軌跡到事件時間，再保留當前位置和速度；不從起點重播。保留速度代表反向瞬間可能仍朝原方向短暫移動，再平滑轉向。彈簧時長由預設決定，沒有固定完成毫秒數。

## 事件與繪製

每個面板建立一個實例，事件與繪製都使用 `PageTransition.nowMs()`；同一實例不能混用 `System.currentTimeMillis()` 與 `nanoTime` 時鐘。僅在客戶端執行緒操作，事件時間應單調不減；重複取樣同一時間不會重複積分，倒退的取樣時間會被忽略。

下列是放在消費端 Screen 內的方法片段；`drawContents` 包含原有面板內容，`reducedMotion` 由消費端設定提供：

```java
private final InterruptibleAnimation panel = new InterruptibleAnimation(Spring.PANEL);

private void setPanelOpen(boolean open) {
    panel.setOpen(open, PageTransition.nowMs());
}

private void togglePanel() {
    panel.toggle(PageTransition.nowMs());
}

private void renderPanel(GuiGraphics graphics, float centerX, float centerY,
                         boolean reducedMotion, Runnable drawContents) {
    float progress = panel.sample(PageTransition.nowMs(), reducedMotion);
    if (!panel.running() && panel.target() == 0f) return;
    GuiRenderUtil.withPanelMotion(graphics, centerX, centerY, progress, drawContents);
}
```

以上類別從 `com.dishanhai.sh_better_ui.client.gui.scaled` 匯入；`GuiGraphics` 使用對應 Minecraft 平台類別。原有 `withPanelMotion` 的外層 RGBA 合成待辦仍存在，此示例適用於預設白色／不透明的繪製入口。

開啟後再次點擊關閉、關閉途中重新開啟，都操作同一個 `panel`。不要每幀重新建立實例，也不要先 `snapTo(0)` 再開始關閉。關閉動畫完成前須保留控件與渲染回調；若是關閉整個 Screen，消費端應在 `sample` 後確認 `target()==0f && !running()` 才執行原本的 Screen 關閉。按鈕是否可點、命中區及業務提交仍由原控件控制。

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

`animateToAfter(target, nowMs, delayMs)` 排程新的目的地；等待期間繼續朝舊目標移動，到期才切換。再次排程會替換尚未生效的命令，直接 `animateTo` 則取消延遲命令。延遲是事件式操作，請勿每幀重新排程。同一幀跨過生效時間時，舊、新軌跡分段推進；保留既有每段最多 50ms 的防卡頓積分限制。

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
| `target()` | 查詢最終目的地，包含延遲目標 |
| `running()` | 有運動或延遲命令時為 true；不推進時間 |

`Spring.PANEL` 用於面板，`HOVER` 用於懸停，`SCROLL`／`INDICATOR` 用於像素值，`STAGGER` 用於無回彈入場。原始值不裁切，透明度應使用 `GuiMotion.clamp01`，縮放可復用 `GuiMotion.panelScale`。非有限目標與 `snapTo` 值會忽略，不取消既有命令；非有限初值正規化為 0。

控件移除時可調用 `snapTo(0f)`；沒有定時器、靜態表或全局監聽器需要釋放。完整遊戲外觀、連點手感與 GUI scale 仍須在消費畫面實機驗收。
