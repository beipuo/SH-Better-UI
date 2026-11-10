<!-- 作者：山海／dishanhai -->
# UMIVP 材质覆盖报告

目录记录的基本单位是公开且可独立调用的效果。共享 renderer 的近似预设可归于同一条，例如 Spatial 表面的六种预设。画廊中使用不同光学参数的透明和磨砂玻璃各自保留 ID。源代码、渲染通道与旧预览的逐项核对见 [审计](material-catalog-audit.md)。完整字段与动态生成的兼容性数据见 [源条目](../catalog/materials/sh_better_ui/) 和 [目录快照](../catalog/generated/catalog.json)。

## 已收录

表中 ID 省略 `sh_better_ui:`。共 42 条，含 24 个具有独立 `PreviewSpec` 路由的条目。材质组合包含的通道和组件在 `renderPassDependencies` 标记。

| 范围 | 目录 ID |
| --- | --- |
| 玻璃与通用表面 | `liquid_glass`、`liquid_glass_surface`、`backdrop_blur`、`clear_glass`、`frosted_glass`、`classic_glass_themes`、`slate_surface`、`network_surface`、`dynamic_theme` |
| 空间、照明与阴影 | `spatial_surface`、`spatial_shadow`、`spatial_lighting`、`glass_panel_shadow` |
| 扭曲、遮罩与合成 | `directional_blur`、`refraction_wave`、`visual_reveal`、`radial_gradient` |
| 动态背景 | `dot_matrix`、`antigravity`、`light_rays`、`laser_flow`、`code_rain`、`ascii_field`、`pattern_waves`、`shape_waves`、`electric_logo`。第十个背景注册 ID `reveal` 通过 `visual_reveal` 查找，背景层附加的底色归 `radial_gradient` |
| 局部反馈与页面过渡 | `click_ripple`、`flow_border`、`breathing_glow`、`attention_indicator`、`page_slide`、`recipe_transition`、`capsule_reveal`、`screen_crossfade` |
| 加载 | `loading_spinner`、`loading_progress`、`loading_skeleton` |
| 图标与文字 | `icon_shader`、`icon_motion`、`formatted_text_effects`、`glass_text_glow`、`floating_lyrics` |

## 缺少独立原生预览

| 当前入口 | 目录 ID 或差距 |
| --- | --- |
| 效果编辑器或设置内演示 | `backdrop_blur`、`breathing_glow`、`capsule_reveal`、`classic_glass_themes`、`click_ripple`、`flow_border`、`radial_gradient`、`screen_crossfade`。`screen_crossfade` 示例只演示淡出样式。 |
| 历史原生测试场景 | `spatial_surface`、`spatial_shadow`、`spatial_lighting`。空间测试与 Shader 能力检测可复用，需登记相应 `PreviewSpec`。 |
| 示例、调用点或历史静态资源 | `attention_indicator`、`directional_blur`、`refraction_wave`、`formatted_text_effects`、`glass_panel_shadow`、`glass_text_glow`、`icon_shader`。 |
| 部分预设通过现有画廊展示 | `icon_motion` 的 `status_icon` 展示 `SPIN`。`slate_surface` 默认路由 `gradient_card`，`dark_button` 仍是旧画廊条目及搜索别名。`liquid_glass_surface` 的 `liquid_button` 展示按钮。`visual_reveal` 使用 RGBA 贴图路径，区域后处理仍需独立场景。 |

`liquid_glass` 走新增受管 Glass 场景。旧 `liquid_button` 使用旧版控件材质，`clear_glass` 与 `frosted_glass` 走高斯样式和各自的画廊预览。`AppearanceGalleryPreview` 为 `ascii_field`、`shape_waves`、`visual_reveal` 提供同名的独立 `PreviewSpec`，`effect_appearance` 作为通用设置入口。仅可从效果编辑器打开的局部示例保留空 `nativePreviewId`。

## 缺少 Web 适配

受管 `liquid_glass` 提供 `web-approximation`，图形行为以现有受管实现为参照，真实像素对照需要固定场景在各端执行。其余 41 条标为 `native-only`，可查询兼容性、源文件、参数和原生预览信息。Web 版本状态由源条目的 `webCompatibility` 与 `webRendererId` 确定。

## 待核实及未独立收录

| 状态 | 范围与证据 |
| --- | --- |
| 待核实 | 独立歌词 HUD 特效对外契约、字体/纹理生成的一致性，以及 Linux、macOS 和更多 GPU 的原生视觉能力。`FloatingLyricRenderer` 整体已有公开入口。 |
| 缺少公开 API | 受管 Glass 的内部捕获、着色程序对象、空间网格和 GPU 查询、图案波的 field FBO、视觉递进的横向中间 pass、电光场纹理及歌词 SDF pass。它们按所属效果保留渲染依赖。 |
| 不适合独立复用 | 画廊专用诊断、配置和编辑器页面，以及按钮、步骤器、局部模态、底部弹层、音乐 HUD、业务实时表面等完整控件和场景。可单独绘制的阴影、动效、文字、玻璃和图标已按公开入口收录。 |
| 已废弃 | 本次源条目均无已核实的弃用标记。旧玻璃与受管玻璃同时存在，兼容入口继续使用自己的材质 ID。 |

Forge 和 NeoForge 兼容字段分别记录。源条目普遍为 `source-verified`，表示共用源码或两端适配已有真实接线。原生历史记录、UMIVP 目录/路由自动化结果与实际 Web 对照独立记录，测试覆盖和内容版本以对应验证结果为准。
