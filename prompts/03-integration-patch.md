<!-- 作者：山海／dishanhai -->
# 接入补丁 Prompt

读取目标项目的 settings、Gradle、toolchain、Loader 元数据、客户端入口、依赖声明和已有渲染任务。核对本地或发布的 SH-Better-UI JAR、公开类签名、目录版本和目标 Loader。沿用外部依赖、UUK 生命周期和现有渲染回调。

如果目标已经提供受支持的受管玻璃接入锚点，按 `docs/integration-guide.md` 运行只读生成器。其余目标先确定现有入口和所需权限，再生成最小公开 API 改动。通过 `git diff` 或生成器提供可审阅 Unified Diff。列明文件、依赖变化、运行入口、适用编译/静态检查/客户端测试、实际结果与回滚方式。保留目标工程当前改动。
