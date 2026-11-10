<!-- 作者：山海／dishanhai -->
# 生命週期與作用域

引擎正常啟動順序為 `NEW → DISCOVERING → REGISTERING → RESOLVING → INITIALIZING → READY → RUNNING`。宿主先驗證依賴計畫，再依序登記、凍結及啟動服務。所有可用服務及能力在 `READY` 一起發布。正常停止經 `STOPPING → TERMINATED`。啟動或運行中的核心故障經 `FAILED → STOPPING → TERMINATED`，釋放已建立的作用域。

## 各層作用域

| 層 | 開啟與結束 |
| --- | --- |
| `ENGINE` | 客戶端宿主安裝至客戶端關閉。跨世界保留。 |
| `MODULE` | 驗證並開始登記模組時建立。模組停止或啟動失敗時關閉。 |
| `CLIENT_SESSION` | 平台以原生連線身分開啟。`ScreenService.session(context, id)` 提供模組自己的連線作用域。登出或停模組時關閉。 |
| `SCREEN` | 原生畫面由平台持有。活動畫面在改尺寸或 GUI Scale 時保留身分，移除再打開會取得新世代。 |
| `UI_TREE` | `ScreenService.mount(context, activeScreen, id)` 將模組的樹掛到活動畫面。畫面或模組任一結束就關閉。 |
| `COMPONENT` | 樹或其他組件可用 `openChild(ScopeKind.COMPONENT, id)` 建立。元素移除時可主動關閉。 |
| `RESOURCE_RELOAD` | 資源套用時由宿主建立並關閉。使用 `ResourcesService.observe` 取得成功套用的新世代。 |

`ScreenService.mount` 接受模組的具名 Java `Module` 內實作的活動 Screen，或以 `ScreenService.create(context, builder)` 建立、已開啟的 `UiScreen`。一次掛載只使用一個存活的 ID。原生 `init` 可能在 resize 時再執行，保留存活的樹並依需重綁新 widget。重新進入頁面時檢查 `isOpen()`，並使用新世代。可以在樹上訂閱 `ScreenService.VIEWPORT_CHANGED` 以取得寬、高、比例及焦點。 [頁面遷移示例](migration-guide.md)

在標題畫面也可掛載樹。HUD 任務若必須在離開世界時取消，先用 `ui.platform().hasClientSession()` 判斷，再取得自己在該連線下的作用域。持有模組 scope 的 HUD 工作可跨會話等待。

## 清理與回執

- `ScopeHandle` 帶 ID、擁有模組、類別、世代和有效狀態。內核驗證句柄來源與世代。事件和命令交付的是目標參照，能供具權限的操作定位目標。開啟子範圍、登記清理及主動關閉需要原始句柄。
- 關閉先失效，再釋放子作用域，最後釋放父作用域。任務、訂閱、租約、暫存配置、狀態、受管素材快取和主題分支依生命週期解除。自訂 `AutoCloseable` 可用 `scope.track(resource)` 納入清理，按物件身分去重。同個資源請固定由一個作用域管理。
- 工作執行前核對發行模組及目標存活。若需使用內核以外的工作者，攜帶不可變資料及作用域世代，將結果交回擁有執行緒後檢查 `isOpen()` 和世代。等候期間關閉頁面時捨棄舊回執。已執行中的背景工作應配合取消狀態退出。
- 模組按依賴逆序停止。停止回呼開始時模組作用域已失效，可用先前保存的參考關閉外部資源或交給 `track`。宿主撤回 `UnifiedUI` 公開入口後停止引擎。 [排程取消](scheduler.md) · [權限與資源](permissions.md)

## 失敗和診斷

不合格的外部候選會被記錄和隔離。必要依賴不成立的模組被拒絕或標記 `BLOCKED`。外部模組啟動失敗時撤銷其服務、供給及資源，必要依賴方也停止或不啟動。執行期的任務、事件或資源回呼失敗由相應服務回報，部分服務會取消受影響的綁定或手勢。宿主可隔離模組故障。公開 `UILifecycle` 供查詢狀態。停模組和重新配置宿主屬於平台的生命週期管理。

`ui.lifecycle().snapshot().modules()` 可查啟動及停止狀態。具診斷權限的視圖可讀近期詳細訊息。啟動、停止及隔離例外也寫入遊戲記錄。遇到服務消失時檢查模組狀態、版本及依賴，再查詢相容能力和 fallback。 [能力系統](capability-system.md)
