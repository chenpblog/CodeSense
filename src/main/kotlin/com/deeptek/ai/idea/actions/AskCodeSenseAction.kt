package com.deeptek.ai.idea.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.wm.ToolWindowManager

/**
 * 咨询 CodeSense Action
 *
 * 选中代码 → 右键 → CodeSense AI → 咨询 AI
 * 将选中的代码片段带入 Chat 对话框（ToolWindow 焦点切换）。
 */
class AskCodeSenseAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText ?: return

        // 打开 CodeSense AI ToolWindow
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("CodeSense AI") ?: return
        toolWindow.show()

        // 后续可以将 selectedText 传递给 ChatPanel 的输入框
        // 目前简单地将 ToolWindow 聚焦即可
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = hasSelection
    }
}
