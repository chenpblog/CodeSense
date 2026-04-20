package com.deeptek.ai.idea.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// ====== Chat Request / Response Models ======

/**
 * Chat 请求体 — 兼容 OpenAI Chat Completions API
 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val tools: List<ToolDefinition>? = null,
    val stream: Boolean = false,
    val temperature: Double? = null,
    @SerialName("max_tokens")
    val maxTokens: Int? = null,
    @SerialName("tool_choice")
    val toolChoice: String? = null  // "auto" | "none"
)

/**
 * Chat 消息
 */
@Serializable
data class ChatMessage(
    val role: String,  // "system" | "user" | "assistant" | "tool"
    val content: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCallInfo>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null
) {
    companion object {
        fun system(content: String) = ChatMessage(role = "system", content = content)
        fun user(content: String) = ChatMessage(role = "user", content = content)
        fun assistant(content: String) = ChatMessage(role = "assistant", content = content)
        fun toolResult(toolCallId: String, content: String) = ChatMessage(
            role = "tool",
            content = content,
            toolCallId = toolCallId
        )
    }
}

/**
 * Chat 完整响应
 */
@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<ChatChoice> = emptyList(),
    val usage: Usage? = null
) {
    /** 获取第一个选择的消息内容 */
    val content: String?
        get() = choices.firstOrNull()?.message?.content

    /** 是否包含工具调用 */
    fun hasToolCalls(): Boolean {
        return choices.firstOrNull()?.message?.toolCalls?.isNotEmpty() == true
    }

    /** 获取工具调用列表 */
    val toolCalls: List<ToolCallInfo>
        get() = choices.firstOrNull()?.message?.toolCalls ?: emptyList()

    /** 获取 assistant 消息（包含工具调用） */
    val assistantMessage: ChatMessage
        get() = choices.firstOrNull()?.message ?: ChatMessage.assistant("")
}

@Serializable
data class ChatChoice(
    val index: Int = 0,
    val message: ChatMessage,
    @SerialName("finish_reason")
    val finishReason: String? = null  // "stop" | "tool_calls" | "length"
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int = 0,
    @SerialName("completion_tokens")
    val completionTokens: Int = 0,
    @SerialName("total_tokens")
    val totalTokens: Int = 0
)

// ====== Streaming Response ======

/**
 * 流式 SSE 数据块
 */
@Serializable
data class ChatChunk(
    val id: String? = null,
    val choices: List<ChunkChoice> = emptyList()
) {
    /** 获取增量内容 */
    val deltaContent: String?
        get() = choices.firstOrNull()?.delta?.content

    /** 获取增量思考内容（reasoning_content，GLM-5 等思考模型使用） */
    val deltaReasoningContent: String?
        get() = choices.firstOrNull()?.delta?.reasoningContent

    /** 获取增量工具调用 */
    val deltaToolCalls: List<ToolCallInfo>?
        get() = choices.firstOrNull()?.delta?.toolCalls

    /** 是否是结束标记 */
    val isFinished: Boolean
        get() = choices.firstOrNull()?.finishReason != null
}

@Serializable
data class ChunkChoice(
    val index: Int = 0,
    val delta: DeltaMessage = DeltaMessage(),
    @SerialName("finish_reason")
    val finishReason: String? = null
)

@Serializable
data class DeltaMessage(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content")
    val reasoningContent: String? = null,
    @SerialName("tool_calls")
    val toolCalls: List<ToolCallInfo>? = null
)

// ====== Tool Calling Models ======

/**
 * 工具定义 — 发给 LLM 描述可用工具
 */
@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: FunctionDefinition
)

@Serializable
data class FunctionDefinition(
    val name: String,
    val description: String,
    val parameters: JsonElement? = null  // JSON Schema
)

/**
 * LLM 返回的工具调用信息
 */
@Serializable
data class ToolCallInfo(
    val id: String = "",
    val type: String = "function",
    val function: FunctionCall = FunctionCall()
)

@Serializable
data class FunctionCall(
    val name: String = "",
    val arguments: String = ""  // JSON string
)

// ====== Error Response ======

@Serializable
data class ApiError(
    val error: ApiErrorDetail? = null
)

@Serializable
data class ApiErrorDetail(
    val message: String = "",
    val type: String? = null,
    val code: String? = null
)
