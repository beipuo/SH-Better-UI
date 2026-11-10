<!-- 作者：山海／dishanhai -->
# 测试与复现

在仓库根目录用 Git Bash 执行 Forge 任务。NeoForge 使用 JDK 21 启动 wrapper。

```bash
gradle-install/gradle-8.8/bin/gradle build --offline --console=plain
cd neoforge
./gradlew build --offline --console=plain
```

`kernelTest` 运行 Java 17 契约测试，覆盖矩阵继承、逆变换、旋转裁剪、命中优先级、模态、透明度、阴影顺序和灯光范围。客户端服务测试覆盖作用域关闭、动画中断、减少动效和热更新。两端 `verifyLibraryJar` 检查产物与依赖隔离。

[客户端验证](../SPATIAL_VERIFICATION.md) 给出开发客户端的截图断言、资源重载和 100–5000 节点压力基准命令。截图检查使用远离抗锯齿边缘的稳定颜色采样区域。

## 最终验证

| 平台 | 内核测试 | 其余单元测试 | 客户端 |
| --- | ---: | ---: | --- |
| Forge 1.20.1 | 105 | 821 | GUI 缩放 1、2，截图、资源重载、Shader 降级与恢复 |
| NeoForge 1.21.1 | 105 | 823 | GUI 缩放 1、2，截图、资源重载、Shader 降级与恢复 |

两个发布 JAR 的 30 个公共空间类使用 Java 17 字节码且公开签名一致。3 个空间 Shader 文件字节一致。发布 JAR 的隔离检查确认开发基准夹具未被打包。最终产物由隔离的 Gradle 项目缓存和输出目录生成，位于 `build/spatial-release-final/forge/libs` 与 `build/spatial-release-final/neoforge/libs`。

缩放 1 的两次客户端运行各取得 15 帧有效生产 GPU 样本，原始数据位于对应 `native-*-spatial-scale1/spatial.csv` 的 `engine_gpu_ns` 列。
