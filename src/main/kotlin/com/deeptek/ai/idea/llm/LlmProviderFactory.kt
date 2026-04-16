package com.deeptek.ai.idea.llm

import com.deeptek.ai.idea.llm.providers.AnthropicCompatProvider
import com.deeptek.ai.idea.llm.providers.OpenAiCompatProvider
import com.deeptek.ai.idea.settings.ApiProtocol
import com.deeptek.ai.idea.settings.CodeSenseSettings
import com.deeptek.ai.idea.settings.ProviderConfig
import com.intellij.openapi.diagnostic.Logger

/**
 * LLM Provider 工厂
 *
 * 根据 ProviderConfig 的协议类型自动创建对应的 LlmProvider 实例：
 * - OpenAI 兼容协议 → OpenAiCompatProvider
 * - Anthropic 兼容协议 → AnthropicCompatProvider
 */
object LlmProviderFactory {

    private val logger = Logger.getInstance(LlmProviderFactory::class.java)

    /**
     * 根据配置创建 LlmProvider 实例
     *
     * 根据 ProviderType 的 apiProtocol 自动选择：
     * - OPENAI_COMPATIBLE → OpenAiCompatProvider（Bearer token + OpenAI SSE 格式）
     * - ANTHROPIC_COMPATIBLE → AnthropicCompatProvider（x-api-key + Anthropic SSE 格式）
     */
    fun create(config: ProviderConfig): LlmProvider {
        val protocol = config.type.apiProtocol
        logger.info("创建 LlmProvider: name=${config.displayName}, model=${config.modelName}, url=${config.baseUrl}, protocol=$protocol")
        return when (protocol) {
            ApiProtocol.OPENAI_COMPATIBLE -> OpenAiCompatProvider(config)
            ApiProtocol.ANTHROPIC_COMPATIBLE -> AnthropicCompatProvider(config)
        }
    }

    /**
     * 创建当前默认模型的 Provider
     *
     * @throws IllegalStateException 如果没有配置默认模型或 API Key 为空
     */
    fun createDefault(): LlmProvider {
        val settings = CodeSenseSettings.getInstance()
        val defaultId = settings.state.defaultProviderId
        val allProviders = settings.state.providers.map { "${it.id}(${it.displayName})" }
        logger.info("获取默认 Provider: defaultId=$defaultId, 所有 Provider=$allProviders")

        val config = settings.getDefaultProvider()
        logger.info("选中默认 Provider: id=${config.id}, name=${config.displayName}, model=${config.modelName}, url=${config.baseUrl}, apiKey=${if (config.apiKey.isNotBlank()) "已配置(${config.apiKey.take(8)}...)" else "未配置"}")

        if (config.apiKey.isBlank()) {
            throw LlmException(
                "默认模型「${config.displayName}」尚未配置 API Key。\n" +
                "请在 Settings → Tools → CodeSense AI 中配置。"
            )
        }

        return create(config)
    }
}
