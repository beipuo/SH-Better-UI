<!-- 作者：山海／dishanhai -->
# 中央調度與失效

平台橋在客戶端執行緒推進幀。在 `ModuleContext` 取得 `UIScheduler`，用 `submit(UITask)` 提交給開啟中的作用域。公開查詢視圖可讀取時間、執行緒及 `SchedulerSnapshot`。只有帶模組身分的視圖可提交。`nowNanos()` 在 UI 幀中使用穩定的幀時間，其他時候取單調時鐘。排程的 delay 和 timeout 從提交時開始計算。

## 相位

| 順序 | 相位 | 工作 |
| --- | --- | --- |
| 1 | `INPUT` | 原生輸入路由及當下的取消判斷。同步路由也可由原生 Screen 在幀外觸發。 |
| 2 | `EVENT_DISPATCH` | 送出透過 `events().post(...)` 排隊的事件。 |
| 3 | `STATE_MUTATION` | 提交命令、狀態及設定交易，送出變更通知，執行背景回執。 |
| 4 | `STYLE_RESOLUTION` | 更新既有主題流程。節點樣式變更可標記局部 layout 或 paint。 |
| 5 | `MEASURE` | 呼叫已綁定的髒節點量測。 |
| 6 | `LAYOUT` | 呼叫已綁定的髒節點排列。 |
| 7 | `ANIMATION` | 用同一幀時間推進活躍的動畫軌。 |
| 8 | `GEOMETRY_UPDATE` | 更新變動的碰撞呈現幾何和命中資料。 |
| 9 | `RENDER_PREPARATION` | 準備純資料與背景回執，派發可執行的背景工作。 |
| 10 | `RENDER` | 在相符的原生 Screen 或 HUD 後置回呼執行單次工作。 |
| 11 | `CLEANUP` | 交付已入列的幀尾工作。關閉作用域時也會即時失效與清理。 |

狀態和設定只在幀間或 `STATE_MUTATION` 提交。在輸入、渲染和其他相位需要修改資料時，提交後續任務。UI 回呼在正在推進的幀重入相同或較早相位時，下幀才有資格執行。較後相位可在當幀執行。原生覆層、隱藏 HUD 或缺少相符表面時，`RENDER` 工作等待匹配的畫面。需畫圖時用 `RenderService.submit`，在回呼期間用其 `Context`。 [平台橋](platform-compatibility.md)

## 容量和取消

每個相位都有獨立容量。各回呼先通過生命週期、權限、延遲和依賴檢查，再依有效優先級及穩定提交序執行。久候工作提升優先級，一般相位以輪替保留名額。`INPUT`、`RENDER` 和 `CLEANUP` 使用個別配額。相同模組在每相位也有上限。時間配額在回呼之間檢查，單個回呼應短且不阻塞。`SchedulerSnapshot` 回報各相位執行次數、耗時及超時預算次數，另有佇列、成功、取消、逾時、失敗、拒絕及遞延統計。

| 操作 | 規則 |
| --- | --- |
| `delayed(nanos)`／`withTimeout(nanos)` | 延遲到最早執行時間，逾時前仍未執行的工作可變成 `TIMED_OUT`。零逾時表示不限。 |
| `after(task)` | 同一模組、同一內核的前置任務。前置失敗、取消或逾時會取消後續任務。 |
| `replacing(id)` | 同一呼叫者、目標世代和鍵只保留新的工作。容量、權限和依賴驗證先於取消被替換工作。 |
| `TaskHandle.cancel()` | 冪等。已開始的 UI 回呼執行到返回。背景工作以狀態協作退出，取消後不交付 UI 回執。 |
| 作用域或模組關閉 | 取消相關工作，關閉後不能提交。宿主撤銷授權會取消不再合法的工作。 |

## 背景回執

只把不可變輸入交給背景工作。工作傳回 `Runnable` 時，回執安排在 `STATE_MUTATION` 或 `RENDER_PREPARATION`，最早是派工後的下一幀。傳回 `null` 表示在背景完成。回執執行前會核對作用域及期限。將 Minecraft 控件、渲染物件及其他可變 UI 狀態留在客戶端執行緒。

```java
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.TaskHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIPhase;
import com.dishanhai.sh_better_ui.kernel.api.UIPriority;
import com.dishanhai.sh_better_ui.kernel.api.UITask;
import java.time.Duration;
import java.util.function.IntConsumer;

final class BackgroundWork {
    static TaskHandle countCodePoints(ModuleContext module, ScopeHandle node, String text, IntConsumer publish) {
        return module.engine().scheduler().submit(UITask.background(node, UIPhase.STATE_MUTATION,
                UIPriority.BACKGROUND, handle -> {
                    if (handle.status().isTerminal()) return null;
                    int count = text.codePointCount(0, text.length());
                    return () -> publish.accept(count);
                }).withTimeout(Duration.ofSeconds(2).toNanos()));
    }
}
```

## 局部失效

`invalidations().invalidate(node, flags)` 接受模組可操作的 `UI_TREE` 或 `COMPONENT`。以節點世代與旗標合併，單幀同旗標只出列一次，超額及自失效遞延。下表是內核傳遞規則。

| 旗標 | 方向及衍生標記 |
| --- | --- |
| `STYLE` | 往下到子節點。 |
| `MEASURE` | 往上，量測先子後父。 |
| `LAYOUT` | 往下，並標 `TRANSFORM`。 |
| `TRANSFORM` | 往下，並標 `HIT_TEST` 和子樹 `PAINT`。 |
| `HIT_TEST` | 往下。 |
| `PAINT` | 往上。 |
| `ACCESSIBILITY` | 往上，先子後父。 |

內核提供失效合併和順序。實際的重算由各服務處理，例如 `LayoutService.bind(...)` 登記的量測和排列，以及 `styles(...)` 將 `ThemeScope` 變更轉成對應旗標。自繪或原生控件可繼續在原有回呼繪製。 [效能基準](performance.md)
