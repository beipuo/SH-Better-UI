<!-- 作者：山海／dishanhai -->
# 用目录生成 Liquid Glass 接入补丁

`catalog/integration.mjs` 审计目标工程并输出 Unified Diff。现有模板支持 `sh_better_ui:liquid_glass`，使用 `GlassOptics`、`GlassMaterial`、`GlassDraw` 和 `RenderService.Context.glass(...)`。目录别名和导出的预设可作为输入。示例工程的可替换位置使用受管 UUK Screen pass，完整作用域和入口见 [CatalogConsumerModule](../examples/material-catalog-consumer/common/src/main/java/com/dishanhai/shbui_catalog_consumer/CatalogConsumerModule.java)。

## 目标形状

- Forge 1.20.1 / Java 17 和 NeoForge 1.21.1 / Java 21 各有独立 Gradle 项目。工具读取 `settings.gradle`、`build.gradle`、`gradle.properties`、Loader TOML 和 `META-INF/sh_better_ui.modules`。支持模块本地 `src/main/java` 和样例中明确声明的 `../common/src/main/java`。支持的组合见 [Loader 兼容表](../catalog/compatibility/loaders.json)。
- 通过 `def shBetterUiJar = file(providers.gradleProperty('shBetterUiJar').get())` 和 `compileOnly files(shBetterUiJar)` 声明外部库，Loader TOML 要求客户端的 `sh_better_ui` 依赖。工具检查 JAR 的 Loader、Minecraft 和版本范围，再通过 JDK 的 `jar` 和 `javap` 检查公开签名及 classfile 版本。JDK 工具需位于 `PATH`。
- 源码使用已发现的 `UIModule`、`REGISTER_RENDER_PASS` 权限和已挂载的 Screen 作用域。样例通过 `ScreenService.create(module, builder)` 关联模块所有权，再打开页面并在 `onInit` 挂载。锚点是 `require(UIApis.RENDER).submit(..., RenderService.Surface.SCREEN, RenderService.Layer.OVERLAY, ... , pass -> { ... })` 的第一行。源代码已有 `GlassRegion` 局部变量、同一 pass 内的前景文字，以及关闭 Screen 时的作用域清理。
- 锚点使用 `// shbui-material-anchor: liquid_glass region=region foreground=LIGHT`。`region` 是真实局部变量名。`LIGHT` 与白色前景配合，`DARK` 与黑色前景配合。生成器校验预设前景和已存在的文字颜色，在锚点后和前景前插入一个绘制调用。

当前实现只处理上面可静态识别的 Groovy Gradle 和 Java 形状。动态构造的 Loader 配置、其他源集、未挂载的 HUD 或其他材质需手工接入。CLI 运行本地 JDK 的只读符号检查，Gradle 编译与实际运行由调用者另行执行。生成的补丁沿用已有任务及作用域。

## 审计和生成

从库仓库根目录在 Git Bash 运行。`--audit` 输出 Loader、版本、JAR、源文件和编译参数，省略时输出 diff。缺少 `--preset` 时使用目录默认参数。

```bash
sample=examples/material-catalog-consumer
source_file=common/src/main/java/com/dishanhai/shbui_catalog_consumer/CatalogConsumerModule.java

node catalog/integration.mjs --target "$sample" --module forge \
  --source "$source_file" --material sh_better_ui:liquid_glass --audit

patch_file="$(mktemp -t shbui-liquid-glass.XXXXXX.patch)"
node catalog/integration.mjs --target "$sample" --module forge \
  --source "$source_file" --material sh_better_ui:liquid_glass \
  --preset build/material-catalog/consumer-preset.json > "$patch_file"
```

用 `--module neoforge` 校验相同共享源码的 NeoForge 目标。用 `--library` 覆盖检查的外部 JAR 后，编译也需传入 `-PshBetterUiJar=<绝对路径>`。实际目标可传项目根下的任意经声明的模块目录。省略 `--module` 表示项目根同时是 Loader 工程。应用前应先核对补丁和前景绘制顺序，再在目标工作副本根执行 `git apply --check`。

## 隔离编译并检查产物

样例可保留原锚点，以便用不同预设重复生成。以下命令将评审过的 diff 应用到临时源码，随后构建两端正式消费 JAR。

```bash
check_dir="$(mktemp -d -t shbui-consumer.XXXXXX)"
mkdir -p "$check_dir/common/src/main"
cp -R "$sample/common/src/main/java" "$check_dir/common/src/main/java"
git -C "$check_dir" apply --check "$patch_file"
git -C "$check_dir" apply "$patch_file"
consumer_source="$(cygpath -m "$check_dir/common/src/main/java")"

./gradle-install/gradle-8.8/bin/gradle -p "$sample/forge" build --offline --no-daemon --console=plain \
  "-PcatalogConsumerSource=$consumer_source"
./neoforge/gradlew -p "$sample/neoforge" build --offline --no-daemon --console=plain \
  "-PcatalogConsumerSource=$consumer_source"
```

`verifyConsumerJar` 检查 Loader 描述、模块清单、语言文件和产物边界。Forge 在开发编译中使用库的 `-dev.jar`，正式安装使用重新映射后的库发布 JAR。NeoForge 使用对应库发布 JAR。消费 JAR 位于对应样例的 `build/libs/`。

客户端检查可在独立实例安装对应的消费 JAR、SH-Better-UI 发布 JAR 和 KotlinForForge。打开标题页的「材质接入示例」，检查蓝色条纹上的胶囊玻璃与前景文字，关闭后焦点回到标题页入口，再打开验证作用域重新挂载。开启与关闭背景效果分别查看光学和实色后备。

## 验证记录

- `node --test catalog/tests/integration.test.mjs` 通过 6 项，包括两端外部 JAR、LF/CRLF diff 应用、无写入、重复接入和拒绝无效目标。
- 使用 `build/material-catalog/web/downloads/sh_better_ui-liquid_glass-a.json` 的浏览器导出作为 `build/material-catalog/consumer-preset.json`。两端审计生成相同的 `build/material-catalog/consumer.patch`，补丁中的折射为 `8` GUI 单位。`git apply --check` 在临时源码通过。
- 关联 Screen 所有权后，Forge 构建用时 20 秒，执行 6 项任务，另 3 项已是最新。NeoForge 构建用时 6 秒，执行 3 项任务，另 2 项已是最新。两端 `compileJava`、打包和 `verifyConsumerJar` 均通过，日志为 `build/material-catalog/consumer-ownership-forge.log` 与 `build/material-catalog/consumer-ownership-neoforge.log`。Java 测试任务为 `NO-SOURCE`。两端产物经 `javap -c -p` 检查，确认 `ScreenService.create` 在 `UiScreen.open` 之前调用，绘制使用 `8.0f` 和 `RenderService.Context.glass`。
- 两端隔离开发客户端加载了编译后的独立消费 JAR。烟测通过键盘打开标题入口，验证实际光学绘制计数增长，按 Escape 返回标题页，并通过 Java 读取浏览器导出的预设。Forge 开发烟测 JAR 从样例编译产物和处理后的资源组装，保留 Mojmap 命名。NeoForge 使用样例发布 JAR。 [Forge 证据](../build/kernel-verification/client-forge-material-consumer/material-catalog-consumer.txt) · [NeoForge 证据](../build/kernel-verification/client-neoforge-material-consumer/material-catalog-consumer.txt)
