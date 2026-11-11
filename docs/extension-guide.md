<!-- 作者：山海／dishanhai -->
# 扩展材质目录

在模块描述中申明对 `UIApis.MATERIAL_DISCOVERY` 的依赖。准备一个 `MaterialEntry`，使用自身 mod namespace 写稳定 ID 和原生预览 ID。可先校验，再在 `start` 中登记。

```java
var catalog = context.engine().require(UIApis.MATERIAL_CATALOG);
catalog.validate(context, entry);
MaterialCatalog.Registration registration = catalog.register(context, entry);
```

模块作用域关闭时，登记自动撤销。手动撤销用 `registration.close()`。替换用 `replace(nextEntry)`，保留原稳定 ID。缺失能力、缺失资源和别名冲突会拒绝登记。重载期间失去资源的条目退出当前快照，恢复后会重新发布。实时扩展可以把退役 ID 重定向到内置条目。

原生效果同时使用自己持有的 `PreviewRegistry` 句柄登记 `PreviewSpec`。将句柄纳入同一个模块生命周期。目录登记可以只包含元数据，原生预览存在时再填写 `nativePreviewId`。正在打开的目录预览同时校验材质与场景世代。

## 网页构建

通过 `web` 下的 `npm run build -- --extension /path/to/bundle` 导入本地包。每个包提供 `extension.json`，在 `files` 中登记相对路径和 `sha256:<hex>`。资源引用 `example:shaders/example.fsh` 对应 `assets/example/shaders/example.fsh`。目录版本下界包含，上界排除。检查完成后，网页合并快照并重新计算哈希。

当前导入支持 Native Only 和 Web Unsupported 扩展。随包 Shader 和其他文件用于校验，网页使用仓库内经过审阅的静态 Renderer 注册表。自定义 Renderer 需要源码审阅及本地验证后加入该注册表。扩展自行声明的客户端兼容性保留为未验证。

[扩展包 Schema](../catalog/schema/extension.schema.json) · [模块开发](module-development.md)
