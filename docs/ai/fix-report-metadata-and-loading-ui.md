# 修复报告元信息动态读取 & 分析 UI Loading 状态

## 问题清单

### 1. 插件版本硬编码
- `ReportGenerator.kt` 第 233 行硬编码 `"CodeSense AI v0.1.0"`
- **修复**: 通过 `PluginManagerCore.getPlugin()` 动态读取插件版本

### 2. LLM 模型名称
- 当前 `report.metadata["llmModel"]` 存储的是 `displayName`（如 "GLM (智谱)"）
- **修复**: 改为存储 `"displayName (modelName)"` 格式，如 "GLM (智谱) / glm-4.7"

### 3. GLM 场景 AI 说明未生成（根因已修复）
- **根因**: `LlmProviderFactory.create()` 硬编码返回 `AnthropicCompatProvider`，GLM 应使用 `OpenAiCompatProvider`
- **已修复**: `LlmProviderFactory.kt` 已根据 `apiProtocol` 自动选择正确的 Provider

### 4. UI Tab 标题增加 Loading/完成标识
- 分析开始时：Tab 标题增加 `⏳` loading 前缀
- AI 处理中：Tab 标题增加 `🔄` 标识
- 全部完成时：Tab 标题改为 `✅` 完成标识

## 涉及文件

| 文件 | 修改内容 |
|------|---------|
| `ReportGenerator.kt` | 插件版本动态读取 |
| `AnalyzeBranchImpactAction.kt` | LLM 模型名 + Tab 标题状态 |
| `AnalyzeImpactAction.kt` | LLM 模型名 + Tab 标题状态 |
