<!-- 作者：山海／dishanhai -->
# 同步歌词 API

实现改编自 [Link-Lyric（beipuo）](https://github.com/beipuo/Link-Lyric/tree/1a7cd274c3e7afbb33c08c66414ec0def944643b)，功能基线为 NeoForge 1.21.1 的 0.1.0。打包的版权声明、MIT 与 Lyricon Apache-2.0 许可证位于 `META-INF/third-party/link-lyric/`。

## 播放器 HUD

在 Salt Player 的插件界面导入并启用 [Link-MC（beipuo）](https://github.com/beipuo/Link-MC)，然后播放有歌词的歌曲。Link-MC 是 Salt Player 的独立 `.spmod`，可在其仓库中用 JDK 25 运行 `gradlew.bat plugin` 构建。上游的 `gradle.properties` 指定了作者本机 JDK 路径，构建时可用 `-Dorg.gradle.java.home=<本机 JDK 25 路径>` 覆盖。

在游戏内运行 `/shbetterlyrics on`，或从开发者面板打开「材质图库 → 同步歌词 → 歌词设置」，打开歌词和 Link-MC 开关。进世界后，游戏通过本机 `http://127.0.0.1:35400/lyric` 读取 v2 快照。设置会显示等待、已连接但暂无歌词、歌词就绪等状态，并提供 Link-MC 地址的复制按钮。总开关默认关闭，退出世界、关闭 HUD 或停用来源会停止内置桥接轮询。

- `/shbetterlyrics on` 启用 HUD 和 Salt Player，`/shbetterlyrics off` 关闭 HUD。
- `/shbetterlyrics location` 打开位置编辑。拖动或用方向键微调后点「保存」，Esc 会取消。`/shbetterlyrics location reset` 恢复默认位置。
- 设置页可调堆叠、弧形或环形排版，以及行数、折行、对齐、位置、字号、逐字渐变、颜色、描边、字重、阴影、行间过渡和间奏提示。布局和样式保存在 `config/sh_better_ui-lyrics.properties`。

当 Link-MC 提供当前曲目的播放位置但没有歌词时，HUD 会在该游戏实例的 `config/sh_better_ui-lyrics/` 查找 UTF-8 文件。自行创建该目录，例如为歌手「歌手」、曲名「曲名」放入 `歌手 - 曲名.ttml` 或 `歌手 - 曲名.lrc`。没有歌手时用 `曲名` 作文件名。将文件名中的控制字符与 `< > : " / \ | ? *` 替换为 `_`，文件名按 Unicode NFC 查找。

顺序是播放器歌词、有效的本地 TTML、本地 LRC。可用同名 `.translation.lrc` 为 LRC 提供译文。没有匹配项时隐藏歌词。暂停和定位沿用播放器进度，修改文件后切歌再切回来，或关闭再开启歌词 HUD，即可重新读取。

图库用静音样例演示逐字染色，可暂停并切换排版。标有「Link-Lyric · beipuo」的按钮会复制原仓库地址，提示同时标注外部提供器 Link-MC。

## 嵌入自己的画面

每个绘制区域持有独立的渲染器。宿主给出 `LyricLine` 列表、曲目身份及播放状态，时间使用毫秒。没有逐字时间时，把该行的 `words` 传为空列表即可按整行时间着色。可在 `LyricLine` 上提供译文或声部。副行优先展示同一时段的对唱歌词或当前行译文，再显示后续歌词。

```java
import com.dishanhai.sh_better_ui.client.api.ShBetterUiApi;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricLine;
import com.dishanhai.sh_better_ui.client.lyrics.core.lyric.LyricTimeline;
import com.dishanhai.sh_better_ui.client.lyrics.core.sync.TrackIdentity;

var lyrics = ShBetterUiApi.lyrics();
var feed = lyrics.localFeed("my_mod:player");
var renderer = lyrics.renderer(feed);
var track = TrackIdentity.of(title, artist, durationMs);
var timeline = new LyricTimeline(lines); // List<LyricLine>，曲目或歌词变更时重建

// 收到播放进度、暂停、定位或换曲消息时更新。
feed.publish(track, timeline, positionMs, playing);

// 在客户端绘制中传入当前 UiFrame、Font 和目标 Bounds。
lyrics.draw(renderer, frame, font, area);
```

`renderer.setStyle(...)` 设置当前区域的样式，`feed.clear()` 清空当前曲目。`LyricFeed` 也可接自定义源，后台线程实现应以同一份 `Frame` 发布曲目、位置和歌词。需要在自己画面接 Salt Player 时，可用 `lyrics.linkMc()` 创建会话并传给 `lyrics.renderer(source)`，按宿主生命周期调用 `start()` 和 `close()`。`lyrics.saltPlayer()` 是同一会话的别名。

### 使用本地 LRC

宿主选定当前曲目的文件后，用 UTF-8 读取 `.lrc` 文本。换曲或替换歌词时解析一次，再随播放器快照发布同一份时间轴。

```java
var timeline = lyrics.parseLrc(lrcText, translatedLrcText, track.durationMs());
feed.publish(track, timeline, positionMs, playing);
```

`translatedLrcText` 可传 `null`，曲长未知时传 `0`。支持 `[mm:ss.xx]` 行时间、同一行多个时间标签、`<mm:ss.xx>` 逐字时间和 `[offset:+毫秒]` 提前播放。译文按两份文件应用各自偏移后的起点精确匹配。纯时间标签可结束上一行，末行使用曲长或四秒时长，逐字末尾也可用一个空的时间标签指定结束。缺少带时间的歌词时返回空时间轴，发布后会清空旧歌词。

### 使用 TTML

`lyrics.parseTtml(ttmlText, track.durationMs())` 同样返回可发布给 `LocalLyricFeed` 的时间轴。支持 [TTML 媒体时间](https://www.w3.org/TR/ttml2/#timing-value-timeExpression) 的并行容器、时钟和时长，以及带 `itunes:timing` 的 [Apple Music／AMLL 绝对时间](https://github.com/amll-dev/applemusic-like-lyrics/blob/main/packages/docs/src/content/docs/en/guides/lyric/ttml.md)。读取 `<p>` 行、`<span>` 逐字、声部与行内 `x-translation`。帧／tick 计时、顺序容器和头部译文留待后续支持。

在自己的画面中使用 Link-MC 并启用本地查找时，调用 `lyrics.linkMc(lyricDirectory)`。

## 给 FormattedCharSequence 着色

在 Tooltip、列表或其他接收 FCS 的原版绘制入口，使用 `ShBetterUiApi.graphics().colorizeLyricText(text, progress, sungRgb, unsungRgb)`。输入为整行进度（0 到 1），或提供 `IntToDoubleFunction`，按视觉码点序号返回逐字时间进度。输出保留原文字体、粗体、斜体及其他样式，替换每个码点的颜色。FCS 每个码点携带一个颜色，歌词渲染器的着色器路径提供字形内渐变。
