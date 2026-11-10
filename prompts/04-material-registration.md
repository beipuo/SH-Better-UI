<!-- 作者：山海／dishanhai -->
# 新材质登记 Prompt

在源码、注册、画廊、Shader/纹理、示例和测试中交叉核验真实入口。使用自己的 namespace 和稳定 ID。按 `catalog/schema/material.schema.json` 写参数类型、真实默认值、范围、动态更新和声明式约束。记录必要资源、渲染阶段、Loader、验证状态与出处。

沿现有 UUK 模块登记元数据，按模块生命周期持有原生 PreviewSpec 的撤销句柄。检查重名、替换、资源重载和活动预览撤销。Web Renderer 先经过静态注册表审阅与离线编译验证。没有 Web 适配的有效原生条目标为 Native Only，并保留参数、真实原生路由和参考资源。交付条目、验证命令与覆盖报告。
