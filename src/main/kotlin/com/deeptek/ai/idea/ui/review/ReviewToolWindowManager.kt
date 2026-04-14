package com.deeptek.ai.idea.ui.review

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

object ReviewToolWindowManager {

    private const val TOOL_WINDOW_ID = "CodeSense AI"

    /**
     * 打开一个新的审查 Tab 页面并返回 ReviewResultPanel
     */
    fun openReviewTab(project: Project, tabTitle: String): ReviewResultPanel {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
            ?: throw IllegalStateException("ToolWindow $TOOL_WINDOW_ID not found")

        toolWindow.show()

        // 声明一个空的 Content，稍后赋值
        var content: com.intellij.ui.content.Content? = null
        
        val reviewPanel = ReviewResultPanel {
            content?.let { toolWindow.contentManager.removeContent(it, true) }
        }
        
        content = ContentFactory.getInstance()
            .createContent(reviewPanel.getComponent(), tabTitle, false)
        
        content.isCloseable = true
        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)

        return reviewPanel
    }
}
