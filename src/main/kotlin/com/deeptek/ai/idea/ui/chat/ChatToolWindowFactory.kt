package com.deeptek.ai.idea.ui.chat

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Chat ToolWindow 工厂
 *
 * 在 IDEA 右侧侧边栏注册 "CodeSense AI" 面板，
 * 提供与 LLM 的交互式对话功能。
 */
class ChatToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatPanel = ChatPanel(project)
        val content = ContentFactory.getInstance()
            .createContent(chatPanel.getComponent(), "Chat", false)
        toolWindow.contentManager.addContent(content)

        // 添加 Settings 设置按钮到工具窗口的标题栏 "..." 菜单或顶部
        val settingsAction = object : com.intellij.openapi.actionSystem.AnAction("设置 CodeSense AI", "打开设置面板", com.intellij.icons.AllIcons.General.Settings) {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                com.intellij.openapi.options.ShowSettingsUtil.getInstance().showSettingsDialog(project, "CodeSense AI")
            }
        }
        toolWindow.setTitleActions(listOf(settingsAction))
    }
}
