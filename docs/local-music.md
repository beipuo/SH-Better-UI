<!-- 作者：山海／dishanhai -->
# 本机音乐 API

Forge 1.20.1 的生产类使用 Java 17，NeoForge 1.21.1 使用 Java 21。两个加载器只在物理客户端启动同一份 Java 服务。联机断开不会关闭服务；退出游戏时停止采集与后台线程。音乐数据不发给 Minecraft 服务器。客户端可加 `-Dshb.music.disabled=true` 停用系统媒体读取。

- **Windows**：通过 Minecraft 已附带的 JNA 直接读取 GSMTC（Windows 10 1809 或更新版本）。播放器和浏览器需要向系统公开媒体会话。每个 Windows 应用 ID 保留一条会话。曲目键由曲名和艺术家组成，相同字段的录音共享同一个键。
- **Linux**：通过 `playerctl` 读取当前用户 D-Bus 的 MPRIS 元数据、播放状态及时间轴。可用 `-Dshb.music.playerctl=/path/to/playerctl` 指定工具。未安装时返回不支持；当前版本不读取 MPRIS 封面。Linux 真机尚未验证。
- **macOS**：通过「音乐」App 的公开脚本字典提供实验性来源。默认关闭；使用 `-Dshb.music.macAutomation=true` 启用，并在系统提示时为 Minecraft 启动器授权「自动化 → 音乐」。只读已运行的「音乐」App；未公开的曲目字段保持未知。尚未在 Mac 真机验证，不覆盖 Spotify、浏览器或其他 macOS 播放器。

Windows 媒体读取随两个加载器的常规构建提供。默认后台轮询间隔为 750 毫秒，可用 `-Dshb.music.pollMillis=1000` 调整。轮询出错时服务最多自动重试五次，宿主也可调用 `retry()`。

## 接入界面

```java
var music = ShBetterUiApi.music();
var frame = music.getFrame(); // 同一帧的会话、封面、歌词一起读取
var session = frame.snapshot().session();
if (session != null) {
    long positionMs = session.positionAt(System.nanoTime()); // -1 表示缺失
    var texture = music.cover(frame); // 渲染线程读取；尚未上传时为空
    int lyricLine = frame.lyrics().lineAt(positionMs, 0); // 第二参数为歌词偏移（毫秒）
}
```

图库「材质 → 本机音乐」默认展示曲目信息、播放状态、时间和歌词区。空间不足时可打开全屏预览。宿主可用 `music.island()` 创建独立视图，也可用 `ShBetterUiApi.lyrics().renderer(music.lyricFeed())` 复用逐字渲染。`music.addListener(...)` 回调运行在媒体线程，界面要经自身主线程执行器更新。画面销毁时关闭返回的监听句柄。

系统媒体会话通常不提供歌词。将 `歌手 - 曲名.lrc` 或 `歌手 - 曲名.ttml` 放进该实例的 `config/sh_better_ui-lyrics/`，同名 `.translation.lrc` 可提供译文。添加或替换文件后，在面板点击「重新读取歌词」，或者调用 `music.refreshLyrics()`。支持 LRC 偏移、逐字时间及 TTML 媒体时间。默认没有在线请求，宿主可在自己的 `MusicService` 中注册授权的 `LyricsProvider`，在返回前按曲名、艺术家和已知时长校对。歌词任务独立运行，换曲时取消并丢弃旧结果。

音频采集默认关闭。在图库点「启用波形」可开启 Windows 系统输出分析，离开示例时自动停止。宿主可在用户授权后调用 `music.setSystemAudioCaptureEnabled(true)` 启用已有 WASAPI 回环，关闭时传 `false`。`getAudioFeatures()` 提供 RMS、Peak、低中高频能量与节拍数据，内部用 FFT 分频。回环是系统混音，可能包含游戏和其他程序。原始 PCM 只在采集线程中处理。
