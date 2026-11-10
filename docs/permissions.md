<!-- 作者：山海／dishanhai -->
# 權限與資源所有權

內核依 Loader 驗證的 mod 檔案、模組 ID 和宿主簽發的 `ModuleContext` 判定呼叫者。`UnifiedUI.get()` 的公開視圖沒有模組操作權。原生發現預設允許在同 namespace 登記服務，並在申請後授予有限的原生輸入和繪製權限。其他權限由宿主配置。啟動時會拒絕申請了未授權權限的模組。優先級和資源宣告本身不會授權。

| 權限 | 用途與發現規則 |
| --- | --- |
| `REGISTER_SERVICE` | 登記回呼期間在自己的 namespace 註冊，宿主對每個已驗證候選授予。 |
| `CAPTURE_INPUT` | 取得原生指標捕獲所需的權限之一。原生候選可申請。 |
| `BLOCK_VANILLA_INPUT` | 允許已掛載範圍的 `consume()` 取消可取消的原生事件，亦為原生捕獲必要條件。原生候選可申請。 |
| `REGISTER_RENDER_PASS` | 提交適用於自身 Screen、模組或會話的原生繪製，以及渲染目標租約。原生候選可申請。 |
| `REPLACE_SERVICE` | 登記期替換允許替換的服務，仍需相符 ID、契約和可見性。 |
| `MODIFY_GLOBAL_CONFIG`／`MODIFY_GLOBAL_THEME` | 受管全域層與主題資源層的操作。 |
| `MODIFY_FOREIGN_SCOPE` | 有效授權範圍內的跨所有者操作，部分原生服務仍要求直接擁有。 |
| `ACCESS_INTERNAL_DIAGNOSTICS` | 查看詳細生命週期診斷。公開視圖可查閱引擎及模組狀態。 |

在可選操作前可用 `context.engine().permissions().check(new PermissionRequest(permission, scope)).allowed()`。`require` 拒絕時會記錄模組來源、操作、目標及決定，並拋出 `SecurityException`。宿主可將 grant 收窄至某個作用域及其後代或撤銷。失效權限會取消受影響的排程與訂閱，回收受影響的租約。模組自己的子節點仍受引擎簽發、有效期和操作限制約束。

權限適用於框架內合作。同一 JVM 的模組可以直接呼叫 Minecraft、Mixin 或反射，這些 API 無法將任意同進程 Java 隔離成安全沙箱。應把 `ModuleContext` 和生命週期句柄留在負責資源的模組內。 [模組開發](module-development.md)

## 宣告與取得租約

以 `ModuleDescriptor.ResourceClaim` 宣告資源類型和自己的 `UiId`。優先使用對應服務取得及釋放資源，例如 `AnimationService.bind`、`FocusService.request`、`InputService.capture` 和 `LayoutService.bind`。`ownership().acquire(new ResourceRequest(claim, scope, priority, lifetimeNanos, policy), release)` 可為受管資源取得通用租約。`ScopeHandle.claim` 提供簡單的同名獨占登記。兩種路徑都要驗證宣告、範圍和適用權限。

| 資源 | 仲裁範圍及策略 |
| --- | --- |
| `KEYBOARD_FOCUS` | 每個活動原生 Screen 一個焦點槽。焦點服務以有效 widget、原生焦點和模態層協調轉移及恢復。 |
| `POINTER_CAPTURE` | 客戶端單一原生指標槽，新舊 API 共用。服務要求實際按下的按鍵及活動 Screen。只以通用租約預留槽位，無法安裝原生處理器或阻斷原版輸入。 |
| `ANIMATION_PROPERTY` | 節點世代及屬性 ID 各有槽位。支援 `QUEUE` 和 `RETARGET`，由目前擁有者 retarget 保留同一租約和原釋放回呼。 |
| `LAYOUT_MUTATION` | 每個節點世代一個排列寫入者，依租約協調。 |
| `MODAL_LAYER` | Screen 層級，可用 `COEXIST` 登記共享租約。既有原生覆層有自己的畫面堆疊和路由。 |
| `RENDER_TARGET`／`TEXTURE_RESOURCE` | 以資源 ID 互斥；實際建立 GPU 或紋理物件仍由呼叫者在適當執行緒處理。 |
| `EVENT_SUBSCRIPTION` | 節點世代與 ID，可用 `COEXIST`。常規事件訂閱由事件總線綁定作用域。 |

`REJECT` 與 `REPLACE` 為通用策略。替換需要較高優先級，或同一所有者使用相同優先級。替換不同模組的租約還需對原租約目標具有 `MODIFY_FOREIGN_SCOPE`。共享的槽位必須全部採 `COEXIST`。`MERGE` 目前會在通用資源仲裁被拒絕。

租約可為 `ACTIVE`、`QUEUED` 或 `RELEASED`。零 `lifetimeNanos` 表示持有至顯式釋放或作用域終止。正值從受理開始計時，會在幀同步點或下次取用時清理。取得成功後由租約管理 release 回呼，包含排隊中的租約。取消、替換、逾期、撤銷或關閉都會使舊句柄失效。清理應可安全重複要求。 [生命週期](lifecycle.md)
