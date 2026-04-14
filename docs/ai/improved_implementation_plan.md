# [优化影响范围分析报告展示与信息收集]

本次变更旨在提升 CodeSense AI 插件“向上调用链分析”功能的准确性和报告友好度。主要通过以下几点改进：

## Proposed Changes

### 数据模型与抓取 (`com.deeptek.ai.idea.analysis`)

#### [MODIFY] [models.kt](file:///Users/chenping/mac_2026/dev/CodeSense-AI/src/main/kotlin/com/deeptek.ai.idea.analysis/models.kt)
- 在 `EntryPointType` 枚举中，新增 `OTHER` 选项（"⚙️", "其他顶级方法"），用于承载那些没有对应框架注解的顶层方法。
- 修改 `CallTree.collectEntryPoints()` 方法逻辑：当某个节点没有更上级的调用者（`children.isEmpty()`）并且原本未被标记为 `isEntryPoint` 时，强制将其标记为 `OTHER` 类型的起点并收集到汇总列表中。以此确保**“所有最上层都要展示出来”**。

### 报告渲染逻辑 (`com.deeptek.ai.idea.analysis`)

#### [MODIFY] [ReportGenerator.kt](file:///Users/chenping/mac_2026/dev/CodeSense-AI/src/main/kotlin/com/deeptek.ai.idea.analysis/ReportGenerator.kt)
- **向上调用链总结表**：不再使用 `flattenTree` 将树形解构为无序列表，而是采用 `biTree.callerTree.collectChainPaths()`，逐条链路列出。
  - 新增 **`链路编号`** 列，以实现表内容与上方控制台树打印出的“链路 N:”对应。
  - 仅展示每条链路的“最顶端”调用者及其类型，或者直接按完整链路路径顺序输出为表格行。
- **受影响入口点汇总**：因为上述 `models.kt` 的改动，这个汇总表格将自动捕获并展示那些没有匹配到现有注解的最上游方法（显示为“其他等级方法”）。

### 分析动作与元数据入口 (`com.deeptek.ai.idea.actions`)

#### [MODIFY] [AnalyzeImpactAction.kt](file:///Users/chenping/mac_2026/dev/CodeSense-AI/src/main/kotlin/com/deeptek.ai.idea.actions/AnalyzeImpactAction.kt)
- 获取 `CodeSenseSettings.getInstance().getDefaultProvider().displayName` 并填充到 `ImpactReport` 的实例中。修改 `generateMetadata` 以让“分析元信息”表格能够**正确展示当前的 LLM 模型名称**。

---

> [!IMPORTANT]
> ## User Review Required
> ### 关于受影响入口点【AI 说明】的生产方案讨论
>
> 目前在报告中，各个受影响入口对应一行数据，最后一列“AI 说明”处于 `*待 AI 生成*` 状态。为了实现它的自动填充，考虑到生成开销和流式响应时间，这里提出两种方案，请您决定采用哪一种落库开发：
>
> **方案 A（统一 Prompt 拦截替换方案，推荐）**
> - 发起 LLM 流式请求时，在原有的总体风险评估 Prompt 结尾增加要求：“请为下列每个涉及的入口点（提供入口列表）生成一句话分析”。
> - 客户端接收流式响应时，拦截解析对应入口点的内容，使用正则直接替换 Markdown 表格里占位用的 `*待 AI 生成*` 字符串。剩下的内容照常贴在最底部的“AI风险评估”区域。
> - **优点**：只需要请求一次大模型，用户等待时间较短。不会导致过多 API 请求而触发限流。
> 
> **方案 B（双重异步请求方案）**
> - 同时发起两个 AI 请求：
>   - 请求一：让 AI 根据变更方法和所有入口点专门生成所有入口点的短评。拿到结果后一次性批量替换表格占位符。
>   - 请求二：现有的底层方法分析请求。
> - **优点**：入口短评更为独立和精准。**缺点**：每次触发需要消耗2倍请求额度，且模型并发生成对系统资源和响应速度有要求。

## Verification Plan

### Manual Verification
1. 选取一个被多条路径调用的深层基础方法触发 `CodeSense AI -> 分析影响范围`。
2. 观察中间结果是否正常展示大模型名称。
3. 检查【向上调用链总结表】中是否出现链路编号并与打印的树对齐。
4. 验证不存在入口注解的叶子节点被归类到“其他”。
5. 在决定了 AI 说明生成方案后实施开发，最终验证对应插桩说明功能正常。
