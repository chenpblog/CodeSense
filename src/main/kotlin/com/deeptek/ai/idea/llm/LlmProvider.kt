package com.deeptek.ai.idea.llm

import kotlinx.coroutines.flow.Flow

/**
 * 统一的 LLM 提供者接口
 *
 * 所有 LLM 模型（MiniMax、GLM、DeepSeek 等）均通过此接口接入。
 * 由于这些提供者都兼容 OpenAI 的 Chat Completions API，
 * 目前统一使用 OpenAiCompatProvider 实现。
 */
interface LlmProvider {

    /** 提供者显示名称 */
    val name: String

    /** 模型名称 */
    val modelName: String

    /**
     * 非流式 Chat Completion
     *
     * @param messages 对话消息列表
     * @param tools 可用工具定义列表（可选，用于 Tool Calling）
     * @param temperature 温度参数（可选，覆盖默认值）
     * @param maxTokens 最大 token 数（可选，覆盖默认值）
     * @return 完整的 Chat 响应
     */
    suspend fun chatCompletion(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>? = null,
        temperature: Double? = null,
        maxTokens: Int? = null
    ): ChatResponse

    /**
     * 流式 Chat Completion
     *
     * @param messages 对话消息列表
     * @param tools 可用工具定义列表（可选）
     * @param temperature 温度参数（可选）
     * @param maxTokens 最大 token 数（可选）
     * @return SSE 数据流
     */
    suspend fun chatCompletionStream(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>? = null,
        temperature: Double? = null,
        maxTokens: Int? = null
    ): Flow<ChatChunk>
}
