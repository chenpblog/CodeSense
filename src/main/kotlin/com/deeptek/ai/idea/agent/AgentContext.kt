package com.deeptek.ai.idea.agent

import com.deeptek.ai.idea.llm.ChatMessage

/**
 * Agent 上下文
 *
 * 管理一次 Agent 会话的状态：对话历史、工具调用记录等。
 */
class AgentContext {

    /** 对话消息历史 */
    val messages = mutableListOf<ChatMessage>()

    /** 工具调用记录 */
    val toolCallHistory = mutableListOf<ToolCallRecord>()

    /** 当前循环轮次 */
    var currentRound: Int = 0

    /** 最大循环轮次（防止死循环） */
    var maxRounds: Int = 15

    fun addMessage(message: ChatMessage) {
        messages.add(message)
    }

    fun recordToolCall(record: ToolCallRecord) {
        toolCallHistory.add(record)
    }

    fun isMaxRoundsReached(): Boolean = currentRound >= maxRounds
}

/**
 * 工具调用记录
 */
data class ToolCallRecord(
    val toolName: String,
    val arguments: String,
    val result: String,
    val durationMs: Long
)
