<!-- 作者：山海／dishanhai -->
# UUK 材质目录 API

在 UUK 发布后通过 `UnifiedUI.get()` 取得引擎。扩展也可以在 `UIModule.start(ModuleContext)` 中使用 `context.engine()`。

```java
var engine = UnifiedUI.get();
var catalog = engine.require(UIApis.MATERIAL_CATALOG);
var glass = catalog.find("sh_better_ui:liquid_glass").orElseThrow();
String preset = catalog.exportPreset(glass.id(), Map.of("refraction", 4.6));
MaterialPreset draft = catalog.importPreset(preset);
```

| 方法 | 契约 |
| --- | --- |
| `find(String)` | 按稳定 ID 或显式别名查询。接受 NFKC、首尾空白和大小写变化。退役且设置替代 ID 时追随重定向 |
| `entries()`、`snapshot()` | 返回不可变记录和集合。快照包含发布版本、内置内容 SHA-256 和运行时世代。已持有的快照可在注册撤销后继续读取 |
| `byCategory(String)`、`byCapability(String)` | 精确过滤目录元数据。实际运行能力继续用 `UIEngine.capabilities()` 核对 |
| `MaterialEntry.validateParameters(Map)` | 验证约束并填入未提供的默认值。返回不可变值，供调用方一次替换草稿 |
| `importPreset(String)`、`exportPreset(String, Map)` | 跨端 JSON 读写。格式、版本或参数非法时抛出 `IllegalArgumentException` |
| `validate(ModuleContext, MaterialEntry)`、`register(ModuleContext, MaterialEntry)` | 校验与登记扩展。需要活跃模块上下文和客户端所有者线程。注册句柄支持同 ID 替换和撤销 |

查询路径只读 Java 17 元数据。材质渲染由条目记录的公开 API 完成。原生预览按 `nativePreviewId` 解析已登记的 `PreviewSpec`。客户端线程可以调用 `ShBetterUiApi.settings().developerMaterialFullscreen(parent, id)` 构造预览画面。

```java
Screen preview = ShBetterUiApi.settings().developerMaterialFullscreen(parent, glass.id());
Minecraft.getInstance().setScreen(preview);
```

参数的数字单位、兼容状态和验证证据均可从生成目录读取。可运行的绘制接入见 [消费示例](integration-guide.md)。 [Schema](../catalog/schema/material.schema.json) · [目录快照](../catalog/generated/catalog.json)
