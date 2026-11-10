<!-- 作者：山海／dishanhai -->
# 設定、狀態與命令

用設定保存偏好和可覆寫的預設，用狀態保存目前業務值，用命令請求修改，用事件通知已發生的事。從 `context.engine()` 取得操作視圖。可跨執行緒讀取已發布的值和快照。定義、提交交易和訂閱由客戶端執行緒持有。幀內的資料提交集中在 `STATE_MUTATION`。

## 設定 schema 與覆蓋

`ConfigKey<T>` 宣告 ID、型別、預設值、可寫層、可變性、持久化分類、版本、說明、驗證器、複製器及遷移函式。以模組 namespace 定義，在 `start` 呼叫 `configs().define(KEY)`。呼叫端共用登記過的鍵實例。`ConfigKey.of(...)` 適用不可變值，其預設為 `RUNTIME`、`SESSION`、版本 1。`STARTUP_ONLY` 的寫入僅在引擎初始化期間允許。驗證、遷移和複製函式須為純函式，複製器還須支援背景讀取。

優先級由低至高。每個鍵須明列可寫的層。對同一層的多個祖先取最近的值，之後回退到較低層。

| 層 | 位置與驗證 |
| --- | --- |
| `ENGINE_DEFAULTS` | `ConfigKey.defaultValue()`，由 schema 提供。 |
| `MODULE_DEFAULTS` | 引擎根，限 schema 擁有者或宿主。 |
| `RESOURCE_THEME` | 引擎根，需要全域設定與全域主題授權。 |
| `USER_GLOBAL` | 引擎根，需要全域設定授權。 |
| `SCREEN` | `SCREEN` 作用域，核對所有者。 |
| `COMPONENT` | `COMPONENT` 作用域，核對所有者。 |
| `RUNTIME_OVERRIDE` | 指定的有效作用域。引擎根需要全域設定授權。 |

`set(key, value)` 寫入目前模組作用域的 `RUNTIME_OVERRIDE`。由 `ScreenService.mount` 取得的原生掛載是模組擁有的 `UI_TREE`，可在樹上寫 `RUNTIME_OVERRIDE`，也可在子組件寫 `COMPONENT`。原生 `SCREEN` 由平台持有。樹掛載在平台的原生 Screen 下，與模組作用域有清理連結。若需要自己的原生頁面預設，使用樹上的覆寫或讀取模組預設後明確套用。

```java
import com.dishanhai.sh_better_ui.kernel.api.ConfigKey;
import com.dishanhai.sh_better_ui.kernel.api.ConfigLayer;
import com.dishanhai.sh_better_ui.kernel.api.ConfigScope;
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.UiId;
import java.util.Set;

final class PageSettings {
    static final ConfigKey<Integer> STEP = ConfigKey.of(UiId.parse("example_mod:step"),
            Integer.class, 1, Set.of(ConfigLayer.COMPONENT, ConfigLayer.RUNTIME_OVERRIDE),
            step -> step >= 1 && step <= 10);

    static void define(ModuleContext context) {
        context.engine().configs().define(STEP);
    }

    static int setStep(ModuleContext context, ScopeHandle component, int value) {
        var config = context.engine().configs();
        config.beginTransaction().set(new ConfigScope(component, ConfigLayer.COMPONENT), STEP, value).commit();
        return config.get(component, STEP);
    }
}
```

## 交易、通知與持久化

- `beginTransaction()` 擷取基線。以 `set`、`clear`、`load` 暫存同一批修改，再呼叫 `commit()`。型別、允許層、權限、作用域與基線版本都會驗證。失敗保留先前發布值，過期基線會拋出 `ConcurrentModificationException`。重新讀取後建立新交易。
- `observe(scope, key, listener)` 以該作用域的有效值發出 `ValueChange<T>`。多次暫存同一鍵以最終有效值通知。關閉觀察的作用域或模組會移除訂閱。監聽器可排隊或提交下一批，內核診斷循環因果並限制連續通知批次。快照只讀，保留取得時的版本和值。
- `ConfigTransaction.load(...)` 在 `USER_GLOBAL` 層載入 `Persistence.USER_GLOBAL` 的鍵，逐版應用同型別遷移再驗證。這是 schema 和載入契約。第三方資料的磁碟格式與儲存由自己的持久化接線管理。SH-Better-UI 的 `UIApis.CLIENT_SETTINGS` 鏡像 Loader 的客戶端 TOML 快照，既有設定保存仍透過 `ShBetterUiApi.settings()`。資源包主題沿用主題解析與資源重載。 [原有設定入口](API_REFERENCE.md#全库客户端主题) · [平台相容性](platform-compatibility.md)

## 狀態與派生資料

使用 `states().define(StateKey)` 和 `states().beginTransaction()`。`GLOBAL` 的值隨鍵的擁有模組，`SCREEN` 需要 `SCREEN`，`COMPONENT` 需要 `COMPONENT`，`EPHEMERAL` 存在指定作用域。將原生掛載的暫存值放在 `UI_TREE` 時可用 `EPHEMERAL`。關閉相應作用域會移除暫存值及訂閱。

`derive(scope, result, dependencies, compute)` 註冊純計算。依賴由同一擁有者宣告，來源可用全域或同一作用域值。派生函式只能讀宣告的輸入。依賴改變後重算受影響的派生圖，註冊時拒絕循環。派生值以輸入更新，不能直接寫入。設定與狀態各有發布版本和交易。

## 命令與副作用

用 `CommandKey<T>` 和 `UICommand<T>` 定義操作。`commands().register(...)` 在模組的 namespace 登記處理器，可要求權限。`execute(command)` 同步提交，`submit(command, timeoutNanos)` 回傳 `CommandHandle`，經 `STATE_MUTATION` 執行。命令處理器收到目標參照和純資料。交易以呼叫者權限處理，提供者的身分不會轉給呼叫者。

`commands().transaction(tx -> …)` 可在一個提交內暫存狀態更新、髒旗標及後續任務。成功才發布狀態，並回傳 `CommandResult` 與每個後續 `TaskHandle`。`afterCommit` 接受獨立任務，沒有前置依賴或替換鍵。若外部副作用稍後失敗、逾時或被取消，查看任務句柄狀態。已發布的狀態版本仍然有效。 [調度](scheduler.md) · [權限](permissions.md)
