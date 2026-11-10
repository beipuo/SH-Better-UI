<!-- 作者：山海／dishanhai -->
# 能力系統

服務是可呼叫的型別介面，以 `ServiceKey<T>` 取得。能力是功能契約，以 `CapabilityKey` 查詢。提供者可組合數個服務達成同一功能。即使能力與服務共用相同 `UiId`，使用端仍須分別驗證能力條件和所需服務。

## 版本與解析

模組透過 `ModuleDescriptor.builder().providedCapability(key, version)` 宣告供給。完整的 `CapabilityOffer` 可指定優先級和 `fallback`。`UiVersion` 接受三段非負整數，例如 `1.0.0`。`VersionRange.between(a, b)` 使用含下界、不含上界的區間，`atLeast(a)` 可省略上界。

解析先篩選版本及適用的提供者，依主要實作先於 fallback、優先級由高至低、版本由高至低、提供者 `UiId` 順序選擇。同一請求只能把一個鍵列為必要或選用。`available()` 列出已啟動模組的所有供給，`resolve()` 傳回每個鍵的選擇與 `requiredMissing()`、`optionalMissing()`。

| 狀態 | 行為 |
| --- | --- |
| `AVAILABLE` | 有符合條件的主要供給。 |
| `DEGRADED` | 只找到相容的 fallback。`supports(key)` 也傳回 `true`。 |
| `UNAVAILABLE` | 無相容供給。缺少選用能力時可走本地路徑。 |

```java
import com.dishanhai.sh_better_ui.kernel.api.CapabilityRequest;
import com.dishanhai.sh_better_ui.kernel.api.CapabilityResolution;
import com.dishanhai.sh_better_ui.kernel.api.UIApis;
import com.dishanhai.sh_better_ui.kernel.api.UIEngine;
import com.dishanhai.sh_better_ui.kernel.api.UiVersion;
import com.dishanhai.sh_better_ui.kernel.api.VersionRange;
import java.util.Map;

final class FeatureRequirements {
    static CapabilityResolution check(UIEngine ui) {
        return ui.capabilities().resolve(new CapabilityRequest(
                Map.of(UIApis.DYNAMIC_THEME, VersionRange.between(new UiVersion(1, 0, 0), new UiVersion(2, 0, 0))),
                Map.of(UIApis.SYSTEM_SCREEN_READER, VersionRange.ANY)));
    }
}
```

`supports(key)` 與 `status(key)` 適合只需要可用性時呼叫。有最低版本或必須取得主要實作的需求時，用 `resolve(request)` 的版本條件和 `status(key)`。客戶端提供 `MINECRAFT_NARRATION`，`SYSTEM_SCREEN_READER` 目前沒有內建提供者。從 Minecraft 旁白回呼發布名稱、角色和提示。 [服務清單](public-api.md)

## 模組順序及生命週期

必要能力在啟動圖中綁定到一個相容提供者。選用能力可增加啟動順序，遇到缺失或循環時記錄診斷並跳過該選用邊。`start` 中只能看見已啟動模組的能力，自己的供給在本模組啟動完成後才可被其他模組解析。宿主移除提供者時，必要依賴方隨之停止。選用依賴方可再解析並走 fallback。

目前供給在模組級發布。`DYNAMIC_THEME` 宣告能切換主題，切換時應監看主題或資源世代。頁面、元件及連線的狀態由其作用域和對應服務管理。目前沒有各層能力登記、能力變更訂閱或執行期重新綁定必要依賴的公開 API。 [模組開發](module-development.md) · [生命週期](lifecycle.md)
