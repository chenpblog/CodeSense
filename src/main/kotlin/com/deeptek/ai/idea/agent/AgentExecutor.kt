package com.deeptek.ai.idea.agent

import com.deeptek.ai.idea.llm.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Agent 事件 — 用于 UI 展示 Agent 执行过程
 */
sealed class AgentEvent {
    /** 正在思考（流式文本） */
    data class Thinking(val text: String) : AgentEvent()

    /** 开始调用工具 */
    data class ToolCallStart(val toolName: String, val arguments: String) : AgentEvent()

    /** 工具调用完成 */
    data class ToolCallResult(val toolName: String, val result: String, val durationMs: Long) : AgentEvent()

    /** 最终回答（流式文本） */
    data class FinalChunk(val text: String) : AgentEvent()

    /** 完成 */
    data class Done(val totalRounds: Int) : AgentEvent()

    /** 错误 */
    data class Error(val message: String) : AgentEvent()
}

/**
 * Agent 执行器 — ReAct 循环
 *
 * 实现 Reasoning + Acting 循环：
 * 1. 将用户问题发给 LLM（带上可用工具定义）
 * 2. 如果 LLM 返回 tool_calls，执行工具并将结果回传
 * 3. 重复直到 LLM 给出最终回答（无 tool_calls）或达到最大轮次
 */
class AgentExecutor(
    private val provider: LlmProvider,
    private val toolRegistry: ToolRegistry,
    private val project: Project
) {

    private val logger = Logger.getInstance(AgentExecutor::class.java)

    companion object {
        const val AGENT_SYSTEM_PROMPT = """你是 CodeSense AI Agent，一个运行在 IntelliJ IDEA 插件中的智能编程助手。

你可以使用以下工具来帮助用户完成编程任务：
- read_file: 读取项目中的文件内容
- write_file: 创建或修改文件
- search_code: 在项目中搜索代码
- run_command: 执行终端命令
- get_git_diff: 获取 Git 变更
- analyze_call_hierarchy: 分析方法调用链
- get_file_structure: 获取文件/类的结构
- list_files: 列出目录内容

工作原则：
1. 先思考需要什么信息，使用工具获取
2. 基于获取的信息进行分析和推理
3. 给出专业、具体、可操作的建议
4. 使用中文回答
5. 对代码修改要谨慎，先理解再修改"""
    }

    /**
     * 执行 Agent 任务
     *
     * @param userMessage 用户输入
     * @param context Agent 上下文（可复用以支持多轮对话）
     * @return AgentEvent 事件流
     */
    fun execute(userMessage: String, context: AgentContext): Flow<AgentEvent> = flow {
        // 初始化消息
        if (context.messages.isEmpty()) {
            context.addMessage(ChatMessage.system(AGENT_SYSTEM_PROMPT))
        }
        context.addMessage(ChatMessage.user(userMessage))

        // ReAct 循环
        while (!context.isMaxRoundsReached()) {
            context.currentRound++
            logger.info("Agent round ${context.currentRound}")

            try {
                // 调用 LLM（非流式，因为需要获取完整的 tool_calls）
                val response = provider.chatCompletion(
                    messages = context.messages,
                    tools = toolRegistry.definitions
                )

                if (response.hasToolCalls()) {
                    // 有工具调用 — 执行工具
                    val assistantMsg = response.assistantMessage
                    context.addMessage(assistantMsg)

                    // 如果 assistant 消息里有文本内容（表示思考过程）
                    assistantMsg.content?.let { thinking ->
                        if (thinking.isNotBlank()) {
                            emit(AgentEvent.Thinking(thinking))
                        }
                    }

                    // 执行每个工具调用
                    for (toolCall in response.toolCalls) {
                        val toolName = toolCall.function.name
                        val arguments = toolCall.function.arguments

                        emit(AgentEvent.ToolCallStart(toolName, arguments))

                        val startTime = System.currentTimeMillis()
                        val result = toolRegistry.execute(project, toolName, arguments)
                        val duration = System.currentTimeMillis() - startTime

                        emit(AgentEvent.ToolCallResult(toolName, result, duration))

                        // 记录工具调用
                        context.recordToolCall(ToolCallRecord(toolName, arguments, result, duration))

                        // 将工具结果加入消息历史
                        context.addMessage(ChatMessage.toolResult(toolCall.id, result))
                    }

                    // 继续循环，让 LLM 基于工具结果继续推理
                } else {
                    // 没有工具调用 — 最终回答
                    val finalContent = response.content ?: ""
                    context.addMessage(ChatMessage.assistant(finalContent))

                    // 流式输出最终回答（分段发送以模拟流式效果）
                    val chunkSize = 50
                    for (i in finalContent.indices step chunkSize) {
                        val chunk = finalContent.substring(i, minOf(i + chunkSize, finalContent.length))
                        emit(AgentEvent.FinalChunk(chunk))
                    }

                    emit(AgentEvent.Done(context.currentRound))
                    return@flow
                }
            } catch (e: Exception) {
                logger.error("Agent execution error at round ${context.currentRound}", e)
                emit(AgentEvent.Error("Agent 执行错误: ${e.message}"))
                return@flow
            }
        }

        // 达到最大轮次
        emit(AgentEvent.Error("Agent 达到最大执行轮次 (${context.maxRounds})，已自动停止。"))
    }
}
