# 修复 GLM-5 SSE 响应解析失败问题

## 问题描述

GLM-5 聊天时返回"AI 未返回有效回复内容"，响应长度为 0。

## 根因分析

### 问题 1：协议路由错误
- 用户保存的 GLM `ProviderConfig` 的 `apiProtocol = ANTHROPIC_COMPATIBLE`（反序列化时使用了默认值）
- 实际 GLM API 返回的是 **OpenAI 兼容格式**（`choices[].delta.content`）
- `AnthropicCompatProvider` 期望 `type: content_block_delta`，无法解析 OpenAI 格式

### 问题 2：缺少 reasoning_content 支持
- GLM-5 是思考模型，先发 `reasoning_content`（思考过程），再发 `content`（最终回答）
- `DeltaMessage` 缺少 `reasoning_content` 字段

## 修改文件

### 1. `models.kt` — 添加 reasoning_content 字段 ✅
- `DeltaMessage` 新增 `@SerialName("reasoning_content") val reasoningContent: String? = null`
- `ChatChunk` 新增 `deltaReasoningContent` 便捷属性

### 2. `AnthropicCompatProvider.kt` — 自动检测 OpenAI 格式 SSE ✅
- 检测条件：`jsonObj.containsKey("choices") && (object == "chat.completion.chunk" || type 为空)`
- 自动回退：用 `ChatChunk.serializer()` 反序列化，提取 `content` 和 `reasoning_content`
- 保留标准 Anthropic 解析逻辑，两种格式互不影响

### 3. `ChatPanel.kt` — UI 支持思考过程显示 ✅
- 新增 `isThinking` / `hasReasoningContent` 状态变量
- 思考过程显示"💭 思考中…"灰色提示
- 区分"仅有思考无回复"和"完全无内容"两种空响应场景

## 验证状态
- ⚠️ 本机无 Java 17，无法本地 Gradle 编译
- ✅ 代码结构、import、类型引用经逐行代码审查确认无误
- 待用户在 IntelliJ IDEA 中编译验证
