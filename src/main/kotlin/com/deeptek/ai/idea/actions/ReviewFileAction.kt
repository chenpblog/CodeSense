package com.deeptek.ai.idea.actions

import com.deeptek.ai.idea.git.GitDiffService
import com.deeptek.ai.idea.review.CodeReviewService
import com.deeptek.ai.idea.ui.review.ReviewToolWindowManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 审查当前文件 Action
 *
 * 在编辑器中右键 → CodeSense AI → 审查此文件
 * 将当前文件内容发送给 LLM 进行代码审查。
 */
class ReviewFileAction : AnAction() {

    private val logger = Logger.getInstance(ReviewFileAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val gitDiffService = GitDiffService.getInstance(project)
                val diff = gitDiffService.getFileDiff(file)

                if (diff == null) {
                    Messages.showInfoMessage(project, "当前文件没有未提交的 Git 变动，无需审查。", "CodeSense AI")
                    return@launch
                }

                // 开启 UI 渲染线程
                val reviewPanel = ReviewToolWindowManager.openReviewTab(project, "Review: ${file.name}")
                reviewPanel.appendMarkdown("\n分析文件 **${file.name}** 的 ${diff.changeType.name} 变更...\n\n")

                try {
                    val reviewService = CodeReviewService.getInstance(project)
                    reviewService.reviewSingleFile(diff).collect { chunk ->
                        reviewPanel.appendChunk(chunk)
                    }
                } catch (e: Exception) {
                    logger.warn("Code review streaming failed", e)
                    reviewPanel.reportError(e.message ?: "未知网络错误")
                }
            } catch (e: Exception) {
                logger.error("Failed to trigger code review", e)
            }
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = file != null
    }
}
