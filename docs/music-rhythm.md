<!-- 作者：山海／dishanhai -->
# 音乐律动

Forge 1.20.1 与 NeoForge 1.21.1 共用 `ShBetterUiApi.audio()`。首版设备采集支持 Windows，读取启动时默认播放设备的系统混音，包括音乐播放器、浏览器和 Minecraft。采集只使用 WASAPI 的输出设备回环，不打开麦克风，不保存或上传音频。系统正在播放的内容如果本来包含其他软件回放的麦克风声音，它也属于输出混音。

音频采集、PCM 分析和动画映射可以独立使用。构造会话不会启动采集，宿主应提供明确的开启和关闭入口。关闭画面或退出消费功能时释放会话。

```java
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.audio.AudioBand;
import com.dishanhai.sh_better_ui.client.audio.MusicMotion;
import com.dishanhai.sh_better_ui.client.audio.SystemAudioCapture;
import com.dishanhai.sh_better_ui.client.gui.scaled.PageTransition;
import com.dishanhai.sh_better_ui.client.gui.scaled.UiPreferences;
import net.minecraft.network.chat.Component;

// 每个消费画面独立持有；创建不会打开设备。
private final SystemAudioCapture audio = ShBetterUiApi.audio().systemOutput();
private final MusicMotion pulse =
        ShBetterUiApi.motion().musicMotion(AudioBand.BASS, 35, 180);

// 用户点击开启时调用；start 非阻塞，同一个会话只允许启动一次。
audio.start();

// 渲染回调中取一次数值快照，可供多个效果共用。
long now = PageTransition.nowMs();
var frame = audio.sample();
float scale = pulse.value(frame, now, UiPreferences.configured().reducedMotion(), 1f, 1.16f);
// 将 scale 应用到宿主的中心缩放，或把 value 映射到位置、透明度、shader 参数等。
// 也可使用 existingAnimation.animateTo(frame.level(AudioBand.BASS), now)。

Component status = Component.translatable(audio.status().translationKey());
Component tooltip = Component.translatable("sh_better_ui.audio.tooltip");

// Screen.removed() / 功能关闭时
audio.close();
```

`MusicMotion.sample()` 返回平滑的 0–1 强度，`value()` 映射到指定区间；减少动效时立即返回静止值。每个动效持有自己的平滑状态，同一时刻重复取样不会加速。静音或采集停止时回到原始动画状态，宿主仍可显示静态的音频状态。

## 独立分析

```java
var analyzer = ShBetterUiApi.audio().analyzer();
analyzer.accept(monoSamples, sampleRate, PageTransition.nowMs());
var frame = analyzer.sample(PageTransition.nowMs());
float loudness = frame.rms();
float beatPulse = frame.beat();
long onsets = frame.beatCount();
```

`monoSamples` 为归一化单声道 `float[]`，可任意分包；采样率范围 8000–384000 Hz，非有限值作为静音，超幅值截断。`accept/reset` 使用单一生产线程，`sample` 可跨线程读取不可变快照。时间须使用同一单调毫秒时钟。设备采集内部使用同一套分析器。

多声道使用 `acceptInterleaved(samples, channels, sampleRate, nowMs)`，支持 1–32 声道，每包必须包含完整交错帧。分析器逐声道 FFT 后合并功率，反相立体声仍保留正确能量和频段。超过 250ms 没有输入时丢弃半窗口与节拍基线，恢复播放可重新触发脉冲。

| 信号 | 含义 |
| --- | --- |
| `RMS` / `PEAK` | 窗口均方根音量 / 峰值 |
| `BASS` | 20–250 Hz 能量 |
| `MID` | 250–4000 Hz 能量 |
| `TREBLE` | 4000 Hz 至奈奎斯特频率的能量 |
| `BEAT` | 能量上升沿脉冲，120ms 时间常数衰减，触发间隔至少 180ms |

所有强度归一化到 0–1。2048 采样 Hann 窗 FFT 在 48kHz 下每约 43ms 更新；频段边界有有限窗口带来的过渡。没有新完整窗口超过 100ms 后，音量以 150ms 时间常数衰减到零。节拍检测不提供准确 BPM 或逐拍乐谱同步。原始 PCM 只在有界工作区临时处理。

## 状态与设备变化

`READY → STARTING → RUNNING`，调用 `close()` 后经过 `STOPPING` 到 `CLOSED`。非 Windows 返回 `UNSUPPORTED`，无输出设备、格式不可用或设备移除返回 `FAILED`；`failure()` 提供供日志使用的技术异常。界面显示状态使用本地化键，不直接显示异常字符串。

不会自动切换播放设备或重新启动。默认设备切换时，旧会话仍属于原设备；宿主应关闭旧会话，再创建并启动新会话。`close()` 最多等待工作线程一秒，若系统调用仍未返回则保持 `STOPPING`，待线程退出自行释放 COM；采集停止请求后不再对外发布音频强度。采集工作在守护线程中，渲染只读取数值。

开发接入应分别验证开启、停止、静音、移除设备、切换输出、重复关闭和画面关闭，并按消费场景测试 GUI 缩放与减少动效。

2026-11-08 验证：Forge 497 项、NeoForge 499 项测试及完整构建通过。本机真实 WASAPI 以 96kHz 双声道读取系统声音，三秒内 299 包，停止及重复释放成功。游戏内画面、设备热拔插及默认设备切换仍需按 `TODO.md` 验收。
