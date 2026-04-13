package com.deeptek.ai.idea.agent

import com.intellij.openapi.project.Project
import kotlinx.serialization.json.JsonElement

/**
 * 统一的 Agent 工具接口
 *
 * 所有可供 LLM 调用的工具必须实现此接口。
 * 工具通过 ToolRegistry 注册后，自动生成 JSON Schema 描述供 LLM 使用。
 */
interface AgentTool {

    /** 工具名称（需与 LLM 调用时使用的名称一致） */
    val name: String

    /** 工具描述（告诉 LLM 这个工具做什么） */
    val description: String

    /** 参数的 JSON Schema 定义 */
    val parametersSchema: JsonElement

    /**
     * 执行工具
     *
     * @param project 当前项目
     * @param arguments LLM 传入的 JSON 参数字符串
     * @return 工具执行结果（文本格式，将作为 tool_result 返回给 LLM）
     */
    suspend fun execute(project: Project, arguments: String): String
}
