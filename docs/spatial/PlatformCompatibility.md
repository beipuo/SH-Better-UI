<!-- 作者：山海／dishanhai -->
# 双平台兼容

| 平台 | 版本 | JDK | 渲染差异 |
| --- | --- | --- | --- |
| Forge | Minecraft 1.20.1 / Forge 47.4.16 | 游戏与共享源集 17 | `vertex`、`uv`、`color`、`endVertex` |
| NeoForge | Minecraft 1.21.1 / NeoForge 21.1.251 | 游戏 21，共享源集 17 | `addVertex`、`setUv`、`setColor` |

两个加载器复用 `POSITION_TEX_COLOR` Shader 和场景语义。Shader 在现有加载器事件注册，资源重载由 Minecraft 替换并释放程序。加载异常时降级为三角化表面，后续成功重载恢复 SDF。后处理 FBO 继续由共享模糊管线复用。

绘制阶段保存并恢复 Shader 绑定、混合、深度、面剔除和颜色写入状态。场景裁剪使用几何处理，因此不需要改写原生 scissor。背景采样沿用已有 FBO、视口与 scissor 恢复逻辑。
