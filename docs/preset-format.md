<!-- 作者：山海／dishanhai -->
# 材质预设交换格式

网页和原生画廊交换同一份 UTF-8 JSON，大小上限 64 KiB。导出使用稳定 ID 和当前材质修订。省略的参数按目录声明填入默认值。

```json
{
  "schemaVersion": "1.0.0",
  "catalogVersion": "1.0.0",
  "materialId": "sh_better_ui:liquid_glass",
  "materialRevision": "1.0.0",
  "parameters": {
    "refraction": 4.6,
    "blurRadius": 1.2,
    "quality": "BALANCED"
  }
}
```

- 参数的 `step` 是控件步长。连续数值按实际材质允许的区间验证。颜色用 `#RRGGBB` 或 `#AARRGGBB`。向量使用定长数字数组，纹理值使用目录列出的资源 ID。
- 渐变使用等距颜色数组，或按 `offset` 升序的 `{offset, color}`；默认是空数组的材质可保留空渐变。曲线使用按 `time` 升序的 `{time, value}`。点的位置在 0 到 1 之间。
- 附加约束是 `eq`、`neq`、`lt`、`lte`、`gt`、`gte`。比较目标为常量或另一个参数。两端都会先解析并验证完整文档，再由编辑器原子替换当前草稿。
- 预设导入接受不高于当前发行且主版本相同的目录版本。通过显式迁移路径的旧草稿可跨主版本。仓库里的旧 Liquid Glass 草稿夹具使用 `opticalBlur`，当前迁移将它重命名为 `blurRadius`，并把旧短 ID 解析成稳定 ID。 [迁移夹具](../catalog/fixtures/presets/liquid-glass-draft.json)
- 网页分享链接保留版本和参数。导入方仍须通过本地目录校验。预设 JSON 可直接在两个 Loader 的开发者画廊中通过剪贴板交换。

[预设 Schema](../catalog/schema/preset.schema.json) · [参数 Schema](../catalog/schema/parameter.schema.json)
