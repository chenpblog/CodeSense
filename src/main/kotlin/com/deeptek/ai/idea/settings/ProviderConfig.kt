package com.deeptek.ai.idea.settings

import java.util.UUID

/**
 * 模型提供者配置数据类
 *
 * 封装某个 LLM 提供者的完整连接信息和参数。
 * 所有支持 OpenAI 兼容格式的模型服务都可以通过此配置接入。
 */
data class ProviderConfig(
    var id: String = UUID.randomUUID().toString(),
    var displayName: String = "",
    var type: ProviderType = ProviderType.MINIMAX,
    var apiProtocol: ApiProtocol = ApiProtocol.ANTHROPIC_COMPATIBLE,
    var baseUrl: String = "",
    var apiKey: String = "",
    var modelName: String = "",
    var maxTokens: Int = 4096,
    var temperature: Double = 0.7,
    var supportToolCalling: Boolean = true,
    var supportStreaming: Boolean = true
) {
    /** 用于 Settings 列表展示 */
    override fun toString(): String = "$displayName ($modelName)"

    /** 深拷贝，用于编辑对话框 */
    fun copy(): ProviderConfig = ProviderConfig(
        id = id,
        displayName = displayName,
        type = type,
        apiProtocol = apiProtocol,
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelName = modelName,
        maxTokens = maxTokens,
        temperature = temperature,
        supportToolCalling = supportToolCalling,
        supportStreaming = supportStreaming
    )
}

/**
 * API 协议类型
 */
enum class ApiProtocol {
    OPENAI_COMPATIBLE,   // OpenAI Chat Completions 格式（Authorization: Bearer）
    ANTHROPIC_COMPATIBLE // Anthropic Messages API 格式（x-api-key）
}

/**
 * 模型提供者类型枚举
 *
 * 每个类型预置了默认的 Base URL、模型名称和协议类型，
 * 用户选择类型后自动填充，只需补充 API Key 即可使用。
 */
enum class ProviderType(
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val apiProtocol: ApiProtocol = ApiProtocol.ANTHROPIC_COMPATIBLE
) {
    MINIMAX(
        displayName = "MiniMax",
        defaultBaseUrl = "https://api.minimaxi.com/anthropic/v1/messages",
        defaultModel = "MiniMax-M2.7",
        apiProtocol = ApiProtocol.ANTHROPIC_COMPATIBLE
    ),
    GLM(
        displayName = "GLM (智谱)",
        defaultBaseUrl = "https://open.bigmodel.cn/api/coding/paas/v4/chat/completions",
        defaultModel = "glm-4",
        apiProtocol = ApiProtocol.ANTHROPIC_COMPATIBLE
    ),
    DEEPSEEK(
        displayName = "DeepSeek",
        defaultBaseUrl = "https://api.deepseek.com/v1/chat/completions",
        defaultModel = "deepseek-chat",
        apiProtocol = ApiProtocol.OPENAI_COMPATIBLE
    ),
    QWEN(
        displayName = "通义千问",
        defaultBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions",
        defaultModel = "qwen-plus",
        apiProtocol = ApiProtocol.OPENAI_COMPATIBLE
    ),
    MINIMAX_ANTHROPIC(
        displayName = "MiniMax (Anthropic 协议)",
        defaultBaseUrl = "https://api.minimaxi.com/anthropic/v1/messages",
        defaultModel = "MiniMax-M2.7",
        apiProtocol = ApiProtocol.ANTHROPIC_COMPATIBLE
    ),
    CUSTOM(
        displayName = "自定义 (Anthropic 兼容)",
        defaultBaseUrl = "",
        defaultModel = "",
        apiProtocol = ApiProtocol.ANTHROPIC_COMPATIBLE
    );

    companion object {
        fun fromDisplayName(name: String): ProviderType {
            return entries.firstOrNull { it.displayName == name } ?: CUSTOM
        }
    }
}
