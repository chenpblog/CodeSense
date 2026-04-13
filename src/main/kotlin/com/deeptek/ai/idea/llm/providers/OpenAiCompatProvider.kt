package com.deeptek.ai.idea.llm.providers

import com.deeptek.ai.idea.llm.*
import com.deeptek.ai.idea.settings.ProviderConfig
import kotlinx.coroutines.flow.Flow

/**
 * 通用 OpenAI 兼容模型提供者
 *
 * 由于 MiniMax、GLM、DeepSeek、通义千问等国产模型
 * 都高度兼容 OpenAI 的 Chat Completions API 格式，
 * 因此只需要一个通用实现，通过不同的 baseUrl 和 apiKey 区分。
 *
 * 如果某个提供者有 API 差异需要特殊处理，
 * 可以继承此类并覆盖对应方法。
 */
class OpenAiCompatProvider(
    private val config: ProviderConfig
) : LlmProvider {

    override val name: String = config.displayName
    override val modelName: String = config.modelName

    private val client = LlmClient.getInstance()

    override suspend fun chatCompletion(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?,
        temperature: Double?,
        maxTokens: Int?
    ): ChatResponse {
        val request = ChatRequest(
            model = config.modelName,
            messages = messages,
            tools = if (config.supportToolCalling) tools else null,
            stream = false,
            temperature = temperature ?: config.temperature,
            maxTokens = maxTokens ?: config.maxTokens
        )
        return client.chatCompletion(config.baseUrl, config.apiKey, request)
    }

    override suspend fun chatCompletionStream(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?,
        temperature: Double?,
        maxTokens: Int?
    ): Flow<ChatChunk> {
        val request = ChatRequest(
            model = config.modelName,
            messages = messages,
            tools = if (config.supportToolCalling) tools else null,
            stream = true,
            temperature = temperature ?: config.temperature,
            maxTokens = maxTokens ?: config.maxTokens
        )
        return client.chatCompletionStream(config.baseUrl, config.apiKey, request)
    }
}
