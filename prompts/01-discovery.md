<!-- 作者：山海／dishanhai -->
# 材质查找与选型 Prompt

读取用户的视觉描述、目标用途、Loader、Java、性能预算与需要的输入/减少动效约束。读取 `catalog/generated/catalog.json` 和 `docs/material-coverage-report.md`，用 `node catalog/find.mjs "<描述>"` 离线查找。稳定 ID、旧画廊短 ID、登记别名、中文/英文名称、公开符号、分类、标签和视觉特征都可作为检索证据。

每个候选给出稳定 ID、用途、实际匹配词与能力差距、源码、公开入口、参数建议、预设、Web 预览状态、Forge/NeoForge 预览 ID 和各自验证状态。检索为空时区分未收录、当前 Loader 不可用和源码确认无此效果。优先保留可复用的真实候选和一份来源明确的备选。
