<!-- 作者：山海／dishanhai -->
# 实时表面验证记录

2026-11-10，基线 `d87e31e`。在同一工作区复用未提交的 Spatial 引擎与 Shader。开发验证在各 Loader 独立运行目录，使用 Java 17 的共享契约和各自的原生工具链。

## 编译、回归和封装

以下命令在项目根目录的 Git Bash 执行。Gradle 由 JDK 21 启动，两端共用的内核和 Forge 游戏使用 JDK 17 工具链。

```bash
./gradle-install/gradle-8.8/bin/gradle build --offline --no-daemon --console=plain
./neoforge/gradlew -p neoforge build --offline --no-daemon --console=plain
git -c core.safecrlf=false diff --check
```

| 平台 | 回归 | 正式包 |
| --- | --- | --- |
| Forge 47.4.16 / Minecraft 1.20.1 | 841 项常规测试 + 133 项共享契约，0 失败 | `build/libs/sh_better_ui-1.0.6.jar` |
| NeoForge 21.1.251 / Minecraft 1.21.1 | 843 项常规测试 + 133 项共享契约，0 失败 | `neoforge/build/libs/sh_better_ui-neoforge-1.21.1-1.0.6.jar` |

两端运行了各自的 `verifyLibraryJar`，Forge 另运行 `reobfJar`。逐个比较 45 个共享公开类型的 `javap -public -s` 输出，两端发行包签名一致。62 个共享实时表面及网络类均为 Java 17。 `jdeps` 验证共享实现依赖 JDK 与库内的内核和 Spatial 契约。两个发行包包含对应 Loader 网络适配，没有打入冒烟夹具。英文和简中各 15 个实时表面语言键一致，无重复键。

共享公开 API 的 Javadoc 通过文档检查，生成于 `build/live-surface-verification/javadoc/`。

```bash
javadoc --release 17 -Xdoclint:all,-missing -encoding UTF-8 -charset UTF-8 \
  -classpath build/libs/sh_better_ui-1.0.6-dev.jar -sourcepath src/kernel/java \
  -d build/live-surface-verification/javadoc \
  com.dishanhai.sh_better_ui.kernel.api.live com.dishanhai.sh_better_ui.kernel.api.live.network
```

## 原生客户端与集成服务端

```bash
./gradle-install/gradle-8.8/bin/gradle --offline --no-daemon --console=plain \
  -I gradle/kernel-smoke.init.gradle -PkernelSmokeLabel=live-stress runClient
./neoforge/gradlew -p neoforge --offline --no-daemon --console=plain \
  -I ../gradle/kernel-smoke.init.gradle -PkernelSmokeLabel=live-stress runClient
```

两端客户端均通过，退出码 0。两端独立创建集成世界，验证 HUD 的双栏与像素、点击展开、键盘执行本地动作、模拟窗口失焦、32 次连续切换动画目标、网络快照到达和实际按钮操作。服务器按真实发送者验证并执行一次，客户端收到结果通知。窗口调整到 864 × 512，GUI Scale 由 2 切到 1，三次资源重载后仍可见远程活动。登出和退出清理活动、通知、会话和内核入口。

结果位于 `build/kernel-verification/client-forge-live-stress/` 与 `client-neoforge-live-stress/`。每端含 `success.txt`、`stopped.txt`、双栏和展开截图、远程操作前截图、重载后截图、原生 CPU 采样与运行环境。 [性能报告](live-surface-benchmark.md)

## 专用服务器

```bash
./gradle-install/gradle-8.8/bin/gradle --offline --no-daemon --console=plain \
  -I gradle/live-surface-server-smoke.init.gradle runServer
./neoforge/gradlew -p neoforge --offline --no-daemon --console=plain \
  -I ../gradle/live-surface-server-smoke.init.gradle runServer
```

NeoForge 使用开发启动器的 IDE 条件，在回环临时端口达到 `Done`，正常保存退出。首次回环运行耗时 6.035 秒，复用该隔离世界复验耗时 0.908 秒。类加载日志显示两条 PLAY 端点已注册，未加载客户端桥接类。首轮曾短暂监听默认地址，已停止该实例并检查端口关闭，复验限定回环。Forge 能发现 SH-Better-UI，随后在 `eula=false` 处退出，完整专用服加载待验。原有 EULA 文件保持原值。证据见 `build/live-surface-verification/server-{forge,neoforge}/`。

## 外部示例模组

```bash
./gradle-install/gradle-8.8/bin/gradle -p examples/kernel-extension/forge build --offline --no-daemon --console=plain --max-workers=2
./neoforge/gradlew -p examples/kernel-extension/neoforge build --offline --no-daemon --console=plain --max-workers=2
./gradle-install/gradle-8.8/bin/gradle -p examples/kernel-extension/forge runClient -PexampleSmoke --offline --no-daemon --console=plain --max-workers=2
./neoforge/gradlew -p examples/kernel-extension/neoforge runClient -PexampleSmoke --offline --no-daemon --console=plain --max-workers=2
```

两端示例独立构建及 `verifyExampleJar` 均通过。两个开发客户端分别加载示例和库 JAR，使用真实按钮触发音乐发布、暂停和继续、本地合成取消及完成通知、机器能量和进度、抢占和恢复，并检查作用域清理和连接监听开关跨页面保留。两端均写出 `success.txt` 和 `stopped.txt`。证据在每个示例工程的 `build/example-smoke/`。真实多人服务器的连接示例和音乐听感仍待人工验证。

## 已修正的验证失败

- 首次整合构建拒绝以核心候选身份注册的 `SERVICE` 模块。改为受信任的普通服务后，两端完整回归通过。
- 无人操作的 Forge 窗口失焦时，实时表面正确暂停，早期冒烟未模拟恢复焦点。调整独立夹具的窗口状态后，HUD 检查通过。
- 早期探针将上一收敛形态当作当前控件就绪信号，并在淡入阶段取样。加入 `transitioning()` 并等待几何与不透明度收敛后，按钮和截图复验通过。
- Forge 重映射会重写类文件。最终对比发行包的公开签名，并在 Forge 开发 JAR 与 NeoForge JAR 之间验证 62 个共享类字节一致。

## 尚待覆盖

- 物理手柄、旁白、高对比、极窄视口以及安装 JEI、REI、EMI 后的真实空间预约。计分板和聊天使用保守矩形。
- 独立远程服务器的延迟、重连与跨维度行为。Forge 专用服务器在用户已接受 EULA 的运行实例中完整启动。
- 可插拔逐元素动画、长时间 GPU 计时，以及使用真实 AE2 合成网的示例后端。通用内容过渡已覆盖容器几何、裁剪和交叉淡入淡出。 [待办](../TODO.md)
