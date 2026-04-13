package com.deeptek.ai.idea.agent

import com.deeptek.ai.idea.llm.FunctionDefinition
import com.deeptek.ai.idea.llm.ToolDefinition
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * 工具注册表
 *
 * 管理所有可用工具，提供：
 * - 工具注册与查找
 * - 生成 LLM 可识别的 ToolDefinition 列表
 * - 执行工具调用
 */
class ToolRegistry {

    private val logger = Logger.getInstance(ToolRegistry::class.java)
    private val tools = mutableMapOf<String, AgentTool>()

    /**
     * 注册一个工具
     */
    fun register(tool: AgentTool) {
        tools[tool.name] = tool
        logger.debug("Registered agent tool: ${tool.name}")
    }

    /**
     * 获取所有工具的 LLM 定义
     */
    val definitions: List<ToolDefinition>
        get() = tools.values.map { tool ->
            ToolDefinition(
                type = "function",
                function = FunctionDefinition(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.parametersSchema
                )
            )
        }

    /**
     * 执行指定工具
     */
    suspend fun execute(project: Project, toolName: String, arguments: String): String {
        val tool = tools[toolName]
            ?: return "错误：未知工具 '$toolName'。可用工具: ${tools.keys.joinToString(", ")}"

        return try {
            logger.info("Executing tool: $toolName")
            tool.execute(project, arguments)
        } catch (e: Exception) {
            logger.warn("Tool execution failed: $toolName", e)
            "工具执行失败: ${e.message}"
        }
    }

    /**
     * 获取所有已注册的工具名
     */
    val toolNames: Set<String> get() = tools.keys
}
