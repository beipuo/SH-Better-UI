<!-- 作者：山海／dishanhai -->
# 材质目录兼容性

| 路径 | 运行条件 | 兼容范围 |
| --- | --- | --- |
| Forge 1.20.1 | Forge 47.4.16，Java 17 游戏运行时 | 共用目录与现有画廊。受管 Liquid Glass 使用原生 OpenGL 管线 |
| NeoForge 1.21.1 | NeoForge 21.1.251，Java 21 游戏运行时 | 同一材质 ID、快照和预设格式 |
| Web | WebGL 2，GLSL ES 3.00。Node.js 22+ 构建与本地预览 | 受管 Liquid Glass 可动态渲染，标记 Web Approximation。其余条目保留参数和现有原生预览路线 |
| 无 WebGL 2 或 Shader 不可用 | 可运行静态站的浏览器 | 条目、参数、公开入口和参考内容仍可查看 |

目录中的 `source-verified` 表示已核对两端源码中的实际公开入口。具体客户端、浏览器与系统环境以 [验证记录](validation-report.md) 为准。
