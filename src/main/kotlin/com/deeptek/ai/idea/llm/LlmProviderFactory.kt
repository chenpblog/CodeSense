package com.deeptek.ai.idea.llm

import com.deeptek.ai.idea.llm.providers.AnthropicCompatProvider
import com.deeptek.ai.idea.llm.providers.OpenAiCompatProvider
import com.deeptek.ai.idea.settings.CodeSenseSettings
import com.deeptek.ai.idea.settings.ProviderConfig
import com.deeptek.ai.idea.settings.ProviderType

/**
 * LLM Provider 工厂
 *
 * 根据 ProviderConfig 创建对应的 LlmProvider 实例。
 * 支持 OpenAI 兼容协议和 Anthropic 兼容协议。
 */
object LlmProviderFactory {

    /**
     * 根据配置创建 LlmProvider 实例
     */
    fun create(config: ProviderConfig): LlmProvider {
        // 全面采用 Anthropic 接口协议
        return AnthropicCompatProvider(config)
    }

    /**
     * 创建当前默认模型的 Provider
     *
     * @throws IllegalStateException 如果没有配置默认模型或 API Key 为空
     */
    fun createDefault(): LlmProvider {
        val settings = CodeSenseSettings.getInstance()
        val config = settings.getDefaultProvider()

        if (config.apiKey.isBlank()) {
            throw LlmException(
                "默认模型「${config.displayName}」尚未配置 API Key。\n" +
                "请在 Settings → Tools → CodeSense AI 中配置。"
            )
        }

        return create(config)
    }
}
