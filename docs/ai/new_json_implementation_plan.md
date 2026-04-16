# JSON to Java Bean 设计器技术实施计划

此实施计划基于已定稿的产品设计《 docs/ai/json_designer_to_bean_plan.md 》，细化了开发过程中的技术落地细节与文件变动。

## Proposed Changes

### 1. 注册核心入口功能 (Action)
在 `plugin.xml` 中将生成器的入口注册到指定的位置，即 `Tools -> CodeSense AI` 以及项目目录右键菜单。
#### [MODIFY] [plugin.xml](file:///Users/chenping/mac_2026/dev/CodeSense-AI/src/main/resources/META-INF/plugin.xml)
- 新增 `<action>` 节点 `com.deeptek.ai.idea.actions.JsonToBeanAction`
- 分别将其加入 `CodeSense.ToolsMenu` 和 `ProjectViewPopupMenu` 组。

### 2. 界面视图组件 (Dialog & UI)
使用 IntelliJ Platform (Swing) 构建配置树与预览对话框。
#### [NEW] [JsonDesignDialog.kt](file:///Users/chenping/mac_2026/dev/CodeSense-AI/src/main/kotlin/com/deeptek/ai/idea/ui/json2bean/JsonDesignDialog.kt)
- **主设计面板**: 基于 `DialogWrapper`，核心采用 `TreeTableView` 或 `JBTreeTable` 组件实现层级化属性编辑。
- **用户交互**: 底部的「生成动作」拦截器，并包含 `Root Element Type` 的配置选项。
#### [NEW] [JsonBeanPreviewDialog.kt](file:///Users/chenping/mac_2026/dev/CodeSense-AI/src/main/kotlin/com/deeptek/ai/idea/ui/json2bean/JsonBeanPreviewDialog.kt)
- **双窗预览**: 使用 `JBSplitter` 拆分成左右两块编辑器。左侧展示 JSON 结果，右侧展示大模型生成的 Java 源码。
- **操作按钮**: "Copy JSON", "Copy Java Bean", 和 "Generate to File..."（集成 JetBrains 的 `PackageChooserDialog`）。

### 3. 数据层建模与核心生成逻辑 (Model & Service)
负责维护层级节点的状态、映射关系与 LLM 的请求装配。
#### [NEW] [JsonNodeModel.kt](file:///Users/chenping/mac_2026/dev/CodeSense-AI/src/main/kotlin/com/deeptek/ai/idea/ui/json2bean/model/JsonNodeModel.kt)
- 定义树形节点模型 `JsonPropertyNode` (包含字段名、数据类型、描述和子节点)。
- 树表格适配器：实现 `TreeTableModel` 或类似接口绑定到界面视图。
#### [NEW] [JsonDemoGenerator.kt](file:///Users/chenping/mac_2026/dev/CodeSense-AI/src/main/kotlin/com/deeptek/ai/idea/ui/json2bean/util/JsonDemoGenerator.kt)
- 负责递归遍历 `JsonPropertyNode` 并借由 `Gson` 或 `Jackson` 根据字段数据类型填充真实的 Mock 值并生成排版后的 Demo String 文本。
#### [NEW] [JsonToBeanService.kt](file:///Users/chenping/mac_2026/dev/CodeSense-AI/src/main/kotlin/com/deeptek/ai/idea/ui/json2bean/service/JsonToBeanService.kt)
- 负责调用现有的大模型接口，结合已定义好的强约束 Prompt 执行真正的生成并处理响应解析。

## User Review Required

> [!IMPORTANT]  
> 此次开发涉及引入树形表格扩展。IntelliJ 原生的 `TreeTableView` 直接使用较为繁琐。由于您之前开发过的功能中未涉及到此类复杂组件，这里默认继续使用 Kotlin Swing 体系（并参考 IDEA 原生的 `ColumnInfo` API）。
> 另外，生成 Demo 的库将首选 Kotlin/Java 里面主流的 JSON 库能力（考虑到 IDE 内部往往通过 gson 或 jackson 构建），请确认项目中是否有特定的 JSON 库偏好，如果没有就使用原生支持库。

## Verification Plan

### Manual Verification
- 启动 SandBox 插件调试环境。
- 测试打开 Tools 菜单，检查是否存在 `JSON to Java Bean 设计器` 菜单。
- 打开设计器弹框并录入带有中文 Key 和两层结构的数据。
- 确认生成的 JSON 正确映射了层级模型，且 AI 的回调输出了规范的 Lombok Java Bean 类文本。 
- 点击 `Generate to File` 并将其写入沙盒的测试包内，确保正常。
