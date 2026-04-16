# 需求目标

针对“成果预览与导出”界面进行改造，升级为“三栏视图”模式，并利用底层大模型提供 JSON 中文 key 的长英文翻译、合理化 Mock 值的能力；最终通过纯代码解析双树（JSON 结构树与用户设计的中文配置树），混合生成拥有全英文标准类、但自带原始中文翻译注释的高质量 Java Bean。

## proposed Changes

### 1. 调整模型及接口签名 (UI / Service)
- **`JsonBeanPreviewDialog` 改造**：
  - 构造函数新增接收 `rootNode: JsonPropertyNode`。
  - 使用双层 `JBSplitter` 拼接出三列面板，平分宽度（左右比例按照 33% 分割）。
    - **左侧**：原始 JSON Demo（Chinese）
    - **中间**：JSON English（AI 翻译结果展示，含占位符状态）
    - **右侧**：Java Bean (Editable)
  - 底部工具栏新增：
    - `[AI 转换]` 按钮：触发大模型 API，具备 `Idle`, `Converting...`, `Success/Failed` 的按钮文本和禁用状态过渡。
    - `[重新生成 Bean]` 按钮：读取此时处于“中间栏”的英文 JSON 串与原始的 `rootNode` 结合，并在右侧重新生成代码。

### 2. LLM 服务调度 (`JsonToBeanService.kt`)
- 增加方法 `translateJsonToEnglish(originalJson: String)`:
  - 构造包含原始 JSON 的明确系统约束文本，要求 AI：“仅将 JSON 的 Key 翻译为标准的驼峰（camelCase）英文全称，保留所有的原始层级结构和顺序绝对不发生改变。并且根据英文 Key 给出一个符合通常直觉的英文或合理的数据 Mock Value。纯输出 JSON”。
  - 调用 `LlmProviderFactory.createDefault().chatCompletion(...)` 获取结果。

### 3. 多源树融合与原生 Bean 生成更新
- 修改代码生成核对逻辑：在重新生成 Bean 时，从中间编辑器拉取已被 AI 翻译过（或人为调整后）的 English JSON 文本并解析为 `JsonObject`。
- 与内存中的 `rootNode` 进行**按序同步深度优先遍历**（依赖 JSON 键的顺序以及强相等的节点层级），将英文 Key 作为真实的属性名（`fName`），将 `rootNode` 里面原本定义的名字和描述统筹合并为 JavaDoc。
- 通过 Java 纯代码实现重发至右侧编辑器。

## 用户评审事项

> [!WARNING]
> 1. **对齐策略**：我们在将翻译后的 English JSON 代码转化为具有中文注释的 Bean 时，**强依赖于 JSON 的属性键值对的前后顺序**与左侧/原始树严格保持一致（即第 N 个 Key 对应树里面的第 N 个字段配置）。AI 大概率能维持顺序，但如果您人为在中间的 English JSON 框内换行或打乱行序，可能会引发错配，这点不知您是否认可？
> 2. **类名处理**：如果 Root Element 本身是 Object（原名比如叫做 `RootRes`），英文生成的 JSON 只是数据不含最外层的实体名，此时顶级类的名称依然沿用您填写的 `Class Name`，只有里面的子类会根据翻译出来的英文字段名自动驼峰化作为类名（例如 `fundList` -> `FundList`）。

如果您觉得上述方案符合您对于 “三栏 UI”、“不改结构翻译”、“重新整合生成”的预期，请告诉我开始执行代码！
