package com.deeptek.ai.idea.llm

import com.deeptek.ai.idea.llm.providers.AnthropicCompatProvider
import com.deeptek.ai.idea.llm.providers.OpenAiCompatProvider
import com.deeptek.ai.idea.settings.CodeSenseSettings
import com.deeptek.ai.idea.settings.ProviderConfig
import com.intellij.openapi.diagnostic.Logger

/**
 * LLM Provider 工厂
 *
 * 根据 ProviderConfig 创建对应的 LlmProvider 实例。
 * 当前全部使用 Anthropic Messages API 协议。
 */
object LlmProviderFactory {

    private val logger = Logger.getInstance(LlmProviderFactory::class.java)

    /**
     * 根据配置创建 LlmProvider 实例
     */
    fun create(config: ProviderConfig): LlmProvider {
        logger.info("创建 LlmProvider: name=${config.displayName}, model=${config.modelName}, url=${config.baseUrl}")
        return AnthropicCompatProvider(config)
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
