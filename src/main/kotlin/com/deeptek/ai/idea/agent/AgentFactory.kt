package com.deeptek.ai.idea.agent

import com.deeptek.ai.idea.agent.tools.*

/**
 * Agent 工厂 — 创建已注册所有工具的 ToolRegistry
 */
object AgentFactory {

    /**
     * 创建并注册所有内置工具
     */
    fun createToolRegistry(): ToolRegistry {
        val registry = ToolRegistry()
        registry.register(ReadFileTool())
        registry.register(WriteFileTool())
        registry.register(SearchCodeTool())
        registry.register(RunCommandTool())
        registry.register(GitDiffTool())
        registry.register(CallHierarchyTool())
        registry.register(FileStructureTool())
        registry.register(ListFilesTool())
        return registry
    }
}
