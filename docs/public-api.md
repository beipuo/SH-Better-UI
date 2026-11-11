<!-- 作者：山海／dishanhai -->
# Unified UI 公開 API

[English API reference](API_REFERENCE.en.md#get-the-kernel-and-look-up-services)

`com.dishanhai.sh_better_ui.kernel.api` 提供 Java 17 內核契約與 Minecraft 客戶端服務。兩個發行包各自包含核心，`kernel.internal`、Loader bridge 和 mixin 由 SH-Better-UI 維護。使用兩端各自的依賴 JAR 編譯。 [平台相容性](platform-compatibility.md)

## 取得引擎和服務

遊戲客戶端完成安裝後，以 `UnifiedUI.get()` 取得共用引擎。這個視圖可讀取公開服務、能力、全域設定和狀態。在 `UIModule.start` 及後續回呼中，使用 `context.engine()` 取得含模組身分的視圖，並以 `context.scope()` 或掛載的樹作為操作目標。啟動前、專用伺服器和停止後用 `isAvailable()` 判斷。此時 `get()` 會拋出 `IllegalStateException`。 [生命週期](lifecycle.md)

```java
import com.dishanhai.sh_better_ui.kernel.api.ThemeService;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIEngine;
import com.dishanhai.sh_better_ui.kernel.api.UnifiedUI;
import java.util.Optional;

final class ThemeQuery {
    static Optional<ThemeService> currentTheme() {
        if (!UnifiedUI.isAvailable()) return Optional.empty();
        UIEngine ui = UnifiedUI.get();
        return ui.find(UIApis.THEME);
    }
}
```

`find(key)` 在目前視圖查不到相符服務時傳回 `Optional.empty()`。`require(key)` 用於必要服務，缺失時拋出含 ID、型別和契約版本的 `NoSuchElementException`。`ServiceKey<T>` 的三個欄位都須相符。`registry().snapshot()` 提供有序、不可變的可見服務描述和來源。可在所屬頁面或模組存活期間重用已解析的參考。跨生命週期時應重新解析，並檢查相關作用域。 [註冊方式](module-development.md) · [能力查詢](capability-system.md)

## 內建服務

下表的鍵皆在 `UIApis`。查詢可用性和取得服務是兩個獨立操作。 [服務鍵原始碼](../src/main/java/com/dishanhai/sh_better_ui/kernel/api/UIApis.java)

| 鍵及型別 | 公開操作 | 使用邊界 |
| --- | --- | --- |
| `THEME` · `ThemeService` | `snapshot()`、`availableThemes()`、`scope(context, node)` | 快照可跨執行緒讀。樹與組件的 token 分支可繼承與局部覆寫，由節點清理。 |
| `ANIMATION` · `AnimationService` | `motion()`、`bind(...)` | 以節點和宣告的屬性租用動畫軌。以 `Track` 播放、融合、暫停、恢復、取消與領取完成版本。現有 `MotionApi` 提供時間軸和組合。 |
| `LAYOUT` · `LayoutService` | `layout()`、`bind(...)`、`styles(...)` | 既有對齊與量測工具可獨立呼叫。以宣告的 layout writer 綁定 measure 和 arrange，並接入髒標記。 |
| `INPUT` · `InputService` | `interactions()`、`target(...)`、`capture(...)` | 活動樹的原生 Screen 輸入，透過 `events()` 訂閱 `EVENT` 或 `POINTER_MOVED`。捕獲需要兩項原生授權。 |
| `FOCUS` · `FocusService` | `request(...)`、`target(...)` | 使用原生 `GuiEventListener`，焦點取得回傳可選租約。鍵盤和字元路由跟隨實際焦點。 |
| `COLLISION` · `CollisionService` | `mount(...)`、`target(...)` | 使用既有 `UiCollisionLayout`，命中取已發布的呈現幾何及裁剪。每個 body 可設定 GUI 單位的 hit slop。 |
| `RENDER` · `RenderService` | `graphics()`、`blur()`、`submit(...)` | 單次原生 `SCREEN` 或 `HUD` pass。`Context` 在回呼內有效，提供 GUI 座標填色、裁剪及變換。 |
| `GLASS` · `GlassService` | `ui.glass().material(variant)`、`diagnostics()` | 使用 `RenderService.Context.glass(...)` 提交不可變材質和幾何，之後繪製前景。快照可跨執行緒讀取。 |
| `RESOURCES` · `ResourcesService` | `version()`、`observe(...)`、`alignmentAssets(...)` | 成功套用重載並清除受管快取後通知。素材快取隨作用域釋放。 |
| `ACCESSIBILITY` · `AccessibilityService` | `preferences(...)`、`describe(...)`、`semantics(...)`、`narrate(...)` | 可取得介面偏好，並在原生旁白回呼寫入語義角色、名稱和提示。 |
| `SCREENS` · `ScreenService` | `create(...)`、`mount(...)`、`session(...)` | 建立有模組身分的 `UiScreen`，掛載自己擁有的活動畫面，或掛載真實連線會話。 |
| `PLATFORM` · `UIPlatform` | `info()`、`viewport()`、`hasClientSession()`、`isOnUIThread()` | 可用 `ui.platform()` 取得同一服務。視口包含 GUI 邏輯尺寸、比例和視窗焦點。 |
| `ACTIVITIES` · `ActivityService` | `publish(...)`、`updateAll(...)`、版本化活動句柄 | 在模組、會話或頁面作用域發布。不可變內容可從背景提交，中央狀態相位處理合併。 |
| `NOTIFICATIONS` · `NotificationService` | `publish(...)`、`dismissAll(...)` | 使用同一個仲裁與展示引擎，支援作用域內去重、限速、逾時和結果回呼。 |
| `LIVE_SURFACES` · `LiveSurfaceService` | `view()`、`reserve(...)`、`diagnostics()` | 讀取已繪製幾何、預約避讓區域及取得近期 CPU 測量。 [即時表面接入](live-surfaces.md) |

跨平台的純 Java 消費者可使用 `UIPlatform.KEY`、`NativeInput` 和內核契約。引用 `UIApis` 的 Minecraft 服務時，依實際簽名使用該版本的 `Screen`、`GuiEventListener` 或 `ResourceManager`。可在同一畫面保留 `UiScreenBuilder`、原生按鈕、旁白及傳統渲染回呼。 [遷移指南](migration-guide.md)

### Liquid Glass

在模組描述宣告 `UIPermission.REGISTER_RENDER_PASS`，以已掛載的 Screen 樹或元件作用域提交材質。在啟動期間用 `find(GlassService.KEY)` 探測服務。GPU 能力和資源於首次光學繪製時檢查。缺少可用的場景捕獲或 Shader、使用者關閉背景效果，或選擇 `FALLBACK` 時，繪製保護前景對比的實色底板。

```java
import com.dishanhai.sh_better_ui.kernel.api.ModuleContext;
import com.dishanhai.sh_better_ui.kernel.api.RenderService;
import com.dishanhai.sh_better_ui.kernel.api.ScopeHandle;
import com.dishanhai.sh_better_ui.kernel.api.TaskHandle;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassDraw;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassRegion;
import com.dishanhai.sh_better_ui.kernel.api.glass.GlassVariant;
import net.minecraft.network.chat.Component;
import java.util.List;

final class StatusGlass {
    static TaskHandle draw(ModuleContext module, ScopeHandle node, Component label) {
        var ui = module.engine();
        var material = ui.glass().material(GlassVariant.REGULAR);
        var draw = new GlassDraw(GlassRegion.capsule(24, 18, 120, 24), material);
        return ui.require(UIApis.RENDER).submit(module, node, RenderService.Surface.SCREEN,
                RenderService.Layer.OVERLAY, 0, pass -> {
                    pass.glass(List.of(draw));
                    pass.text(label, 34, 26, material.foreground().argb());
                });
    }
}
```

- 所有玻璃命令依呼叫及批次順序繪製。每個原生 pass 在首個光學命令之前捕獲一次目前畫面。先提交共同的背景，再提交材質和前景。同一 pass 的後續玻璃會採樣同一份背景。
- 幾何與折射、厚度、模糊半徑均使用 GUI 單位，隨原生縮放與平移／正向縮放變換。支援圓角、膠囊、橢圓（等寬高為圓形）和連續角。用 `GlassRegion.contains` 驗證命中，陰影位於互動區域外。
- `REGULAR` 適用於文字及一般控件，`CLEAR` 適用於豐富背景。使用材質的 `Foreground` 顏色。高對比設定提高光學底板的最低前景對比。Shader 逐區域採樣背景，GPU 僅向 Java 提供資源及繪製計數。
- `GlassOptics`、`GlassRegion` 和 `GlassDraw` 可重用。原生繪製僅在作用域內的回呼中有效，背景、Shader 和 VAO 由內核持有並於重載或停止時釋放。GLSL 以 OpenGL 3.2 為底線，實際可用性由 Shader 編譯及幀緩衝檢查確認。
- 用 `GlassDraw.interpolate(target, progress)` 將同一軌道的進度套用到輪廓、光學和高光。變體、品質、前景和形狀族維持一致，圓角矩形與膠囊屬於同一族。控件可用同一份當前 `GlassRegion` 做繪製與輸入命中。

## 內核視圖

| 入口 | 操作 | 執行緒與權限 |
| --- | --- | --- |
| `registry()`、`capabilities()`、`lifecycle()` | 服務／能力解析、狀態與快照 | 公開視圖可查詢。詳細生命週期診斷需要授權。 |
| `scheduler()`、`invalidations()` | 提交有界工作、讀取 `SchedulerSnapshot`、標記節點 | 可從背景提交不可變工作。失效標記及 UI 回呼由客戶端執行緒處理。 |
| `configs()`、`states()` | 定義、讀取、觀察和提交交易 | 讀取發布的快照。寫入與訂閱使用已驗證的模組和作用域。 |
| `commands()` | 登記型別化處理器、同步／非同步提交、交易 | 以呼叫模組的身分驗證。交易內只能暫存更新、失效和後續任務。 |
| `events()` | 同步傳播、佇列發布、訂閱 | 訂閱和同步傳播在客戶端執行緒。回呼持有的事件控制權只在該次傳遞有效。 |
| `permissions()`、`ownership()` | 判定權限、取得宣告資源的租約 | 由內核核對目標、模組來源、資源類別和授權。 |

執行緒與提交規則詳見 [調度](scheduler.md) · [設定與狀態](configuration.md) · [權限與租約](permissions.md)。

## 事件傳播

`UIEventType<T>` 宣告型別和是否可取消。用 `events().subscribe(type, scope, phase, priority, listener)` 綁定訂閱，關閉句柄或作用域時解除。`emit` 在 UI 執行緒同步回傳 `EventResult`，`post` 將不可變 payload 排到 `EVENT_DISPATCH` 並回傳工作句柄。事件帶來源、目標及目前經過的節點。

| 階段或操作 | 行為 |
| --- | --- |
| `CAPTURE → TARGET → BUBBLE` | 先由祖先到目標，再由目標的父節點往上。相同節點與相位的回呼依優先級及登記順序執行。 |
| `stopPropagation()` | 執行完當前節點的回呼後結束傳遞。 |
| `stopImmediatePropagation()` | 結束傳遞，略過當前節點尚未執行的回呼。 |
| `preventDefault()` | 設定可取消事件的 `defaultPrevented`，由發送方處理預設操作。 |
| `markHandled()` | 記錄已處理，繼續傳遞及預設操作。 |
| `consume()` | 結束傳遞。可取消的原生事件由宿主再核對活動作用域及 `BLOCK_VANILLA_INPUT`，決定是否阻斷。 |

同步傳遞有巢狀深度和總交付次數上限。回呼例外寫入診斷，後續回呼按原傳遞條件繼續。需在回呼後修改資料時，將工作提交至 `STATE_MUTATION`。 [原生路由](platform-compatibility.md#原生路由)
