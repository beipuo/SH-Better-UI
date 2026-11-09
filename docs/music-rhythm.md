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

## 像素音乐律动 HUD

在 **开发者面板 → 材质图库 → 像素音乐律动** 可以切换内嵌或全屏预览。默认运行无声的演示信号，约 15 秒经历一次舒缓、渐强、高潮和回落；点「采集系统声音」才开启真实采集。暂停演示、停止采集和返回均可用键盘操作。

消费方可直接使用底板和波形，也可以只取律动数值，自行绘制特效。每个画面持有独立的 `MusicRhythm`，一帧取样一次并共享给多个区域。以下方法接收宿主提供的 `GuiGraphics`、GUI 区域和 `AudioFrame`：

```java
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.audio.MusicRhythm;
import com.dishanhai.sh_better_ui.client.gui.scaled.MusicHud;

private final MusicRhythm rhythm = ShBetterUiApi.motion().musicRhythm();

// 绘制时，audio 可以来自采集器、PCM 分析器或宿主自己的数据源。
var prefs = ShBetterUiApi.settings().preferences();
var motion = rhythm.sample(audio, ShBetterUiApi.motion().nowMs(),
        prefs.reducedMotion() || prefs.highContrast());
MusicHud.drawPanel(graphics, panelBounds, motion, prefs);
MusicHud.drawScope(graphics, scopeBounds, motion, prefs);
button.setIconOffsetY(-Math.round(2 * motion.lift(0.4f)));
// 底板和波形画完后，再绘制文字、按钮和焦点。按钮热区无需变换。
```

| 帧信号 | 用法 |
| --- | --- |
| `bass()` | 驱动底板呼吸、局部光晕和图标抬升。 |
| `mid()` / `wave(offset)` | 中频振幅与有符号波形。以不同弧度偏移错开图标和装饰线。 |
| `treble()` / `sparkle(offset)` | 高频强度与局部亮度。为各像素提供独立偏移。 |
| `energy()` | 慢速变化的整体能量，适合渐变和柔光增益。 |
| `pulses()` / `impact()` | 新鲜强拍的扩散进度和透明度，以及带衰减回弹的有符号冲击。相邻强拍可各自淡出；跳帧时不补播积压节拍。 |

`sample()` 只读取信号，采集由宿主管理。采集器、分析器和渲染使用同一单调时钟；换音源、跳转播放位置或离开画面时调用 `rhythm.reset()`。`drawPanel()` 和 `drawScope()` 支持静态样式，高对比使用实色轮廓；消费方的自绘动效也应传入同一偏好。`UiButton.setIconOffsetY()` 在按钮内最多移动两个 GUI 像素，保留原生焦点、文字和热区。

## 音乐胶囊

在 **开发者面板 → 材质图库 → 音乐胶囊** 点击胶囊，底板以同一条轮廓展开，曲名与波形留在原位。展开后可拖动进度、暂停或播放、切换三段无声演示曲目。Tab 移动焦点，Enter 或空格操作按钮，进度条使用方向键。空间不足时可从图库打开全屏。

`MusicIsland.outline(compact, panel, progress)` 返回共用轮廓，推荐让两种状态共用上边缘和水平中心。`drawShell()` 绘制深色半透明底板，展开进度由交互驱动。`drawWaveform()` 读取同一份 `MusicRhythm.Frame`，在至少 26×3 GUI 单位的区域居中绘制七根像素柱。左右两端偏重低频和高频，中间响应中频，各列有独立相位。高对比模式使用纯色轮廓，减少动效时直接切换布局并保留静态基线。

暂停时继续用单调时钟向 `MusicRhythm.sample()` 传入 `AudioFrame.silence(now)`，让各频段的包络逐渐收敛。若换曲需要保持波形连续，可以沿用同一 `MusicRhythm` 并直接传入新曲的音频帧，同时插值曲目主题色。胶囊只消费音频帧与展开进度，播放、切曲和音频来源由宿主管理。

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

不会自动切换播放设备或重新启动。默认设备切换时，旧会话仍属于原设备；宿主应关闭旧会话，再创建并启动新会话。`requestStop()` 可从界面调用，立即停止对外发布音频并安排后台释放设备。需要等待释放时用 `close()`，它最多等待工作线程一秒；若系统调用仍未返回则保持 `STOPPING`，待线程退出自行释放 COM。采集工作在守护线程中，渲染只读取数值。

开发接入应分别验证开启、停止、静音、移除设备、切换输出、重复关闭和画面关闭，并按消费场景测试 GUI 缩放与减少动效。

2026-11-08 验证：Forge 497 项、NeoForge 499 项测试及完整构建通过。本机真实 WASAPI 以 96kHz 双声道读取系统声音，三秒内 299 包，停止及重复释放成功。游戏内画面、设备热拔插及默认设备切换仍需按 `TODO.md` 验收。
