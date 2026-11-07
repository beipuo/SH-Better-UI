<!-- Author: dishanhai -->
# SH-Better-UI 代碼複核（2026-11-07）

範圍：現有主程式、shader、可選 LDLib 接口、測試與構建契約；新增效果另行驗證。下列記錄複核當時的問題與基線，後續修正另外標示。沒有進行 Minecraft 實機驗收；中性玻璃的後續離屏 GPU 證據見 [維護紀錄](docs/neutral-glass-maintenance.md)。

## [P2] 點擊波原點的 uniform 類型與上傳接口不匹配

- 位置：`src/main/resources/assets/sh_better_ui/shaders/program/shop_gauss_blur.fsh:22`、同目錄 `shop_gauss_blur.json:15`；`client/blur/GaussBlur.java:640`。
- 觸發：後處理上傳 `WaveOrigin`，尤其是調用 `requestWave`／`setWaveOrigin` 時。
- 問題：GLSL 宣告 `float WaveOrigin[2]`，但 JSON 的 `type=float,count=2` 經 Minecraft `EffectInstance.parseUniformNode` 轉為二分量向量類型，`Uniform.uploadAsFloat` 使用 `RenderSystem.glUniform2`；它與 scalar float array 不相容，波心不能按請求更新。`getUniform != null` 的預檢無法識別此錯誤。
- 證據：對本庫實際 Forge 47.4.16／Minecraft 1.20.1 映射 jar 執行 `javap -c -p`，核對 `PostPass`、`EffectInstance` 與 `Uniform` 的調用鏈。這是類型與字節碼證據，未聲稱已觀察到遊戲畫面或 GL 日誌。
- 建議：將 GLSL 改為 `vec2 WaveOrigin` 並直接引用向量；`CellRects[32]` 的上傳分支不同，不應一併改成向量陣列。

## [P2] 模糊執行異常後丟失未釋放的 GPU 資源

- 位置：`src/main/java/com/dishanhai/sh_better_ui/client/blur/GaussBlur.java:575-580`。
- 觸發：`post.process` 拋出例外後，五秒重試再次建立管線。
- 問題：catch 直接 `chain = null`，沒有對失敗的 `post` 調用 `close()`。後續 `release()` 也無法再找到舊管線；重複失敗會遺留渲染目標及 effect 資源。
- 建議：在渲染執行緒保留舊引用、盡力關閉，並保留原有 framebuffer／scissor 恢復及重試節流。
- 證據邊界：控制流複核；未注入 OpenGL 執行異常或量測顯存。

## [P2] 面板動效破壞外層透明度與色調

- 位置：`src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/GuiRenderUtil.java:172-176`。
- 觸發：在已有 `g.setColor` 或另一個 `withPanelMotion` 的範圍內繪製面板。
- 問題：方法覆蓋全域 shader 顏色為白色／自身 alpha，結束時又固定重設為不透明白色。內層不繼承外層透明度，返回後同一外層剩餘內容也失去淡入與色調。
- 建議：flush 後保存原 RGBA，alpha 乘上原 alpha，finally flush 後還原原值；保留 pose 的 push/pop。
- 證據邊界：狀態讀寫複核；未做嵌套畫面實機驗收。

## [已修正 P2] 延遲改目標會凍結正在移動的彈簧

- 位置：`src/main/java/com/dishanhai/sh_better_ui/client/gui/scaled/Spring.java:111-114`。
- 觸發：彈簧仍在運動時，調用 `setTargetAfter` 排程另一個目標。
- 問題：到期前 `update` 直接返回，與 API 所述「之前保持當前軌跡」不符。已有測試僅覆蓋初始靜止的錯峰入場。
- 重現：兩根 HOVER 彈簧在 1000ms 朝 1 移動，1020ms 更新後值均為 `0.43749738`；其中一根排程 100ms 後朝 0 移動。1060ms 時排程者仍為 `0.43749738`，對照者為 `0.88865423`。
- 建議：等待新目標時繼續積分舊目標；跨越排程時間時分段計算，避免在新目標上積分整個舊時間段。
- 後續修正：`Spring` 已依事件時間取樣、等待期間保持原軌跡並在生效時間分段推進，新增 `SpringInterruptionTest` 8 項與 `InterruptibleAnimationTest` 14 項測試。以下驗證紀錄仍保留複核當時的版本／雜湊；最新結果見玻璃維護紀錄。

## [P3] 抽取契約測試寫死舊版本且未接入 build

- 位置：`src/test/python/test_extraction_contract.py:18`；`build.gradle` 的 `build` 依賴列表。
- 問題：斷言要求消費端存在字面量 `[1.0.0,2.0.0)`，實際已使用 `${sh_better_ui_version}`。原始基線執行 7 項，6 項通過、1 項失敗；Gradle `test` 並不執行這組 Python 檢查。
- 建議：讀取版本變量並解析目標依賴段，驗證實際版本範圍；明確接入檢查流程或記錄獨立命令。不可透過放寬消費端最低版本來讓測試變綠。

## 驗證紀錄

- 基線：Gradle `test --offline --no-daemon --console=plain` 成功，但 `test` 為 UP-TO-DATE。
- 基線：`python -m unittest discover -s src/test/python -v`，7 項／1 失敗，原因如上。
- 純 Java 探針：`jshell --class-path build/classes/java/main`，重現延遲彈簧凍結。
- 最終：Gradle `build --offline --no-daemon --console=plain`，12 秒成功；`test` 實際執行，18 組／107 項通過，0 failures／errors，包含 17 項新效果測試。
- `releaseJar`／`verifyLibraryJar` 通過；`build/libs/sh_better_ui-1.0.3.jar` 與 `build/reobfJar/output.jar` 的 SHA-256 均為 `F6B72C8DA3EAFD866FAB5E3C35FC6A6843E7D456EF14E8D2F36E4BC45095637D`。
- 新效果已驗證時間軸、點擊邊界、減少動效、四邊及四角覆蓋、控件重排、矩形裁切和繪製量上限；未部署、未重啟、未進行遊戲驗收。
