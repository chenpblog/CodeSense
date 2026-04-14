package com.deeptek.ai.idea.actions

import com.deeptek.ai.idea.git.GitDiffService
import com.deeptek.ai.idea.review.CodeReviewService
import com.deeptek.ai.idea.ui.review.ReviewToolWindowManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 批量审查未提交的文件
 */
class ReviewGitChangesAction : AnAction() {

    private val logger = Logger.getInstance(ReviewGitChangesAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val gitDiffService = GitDiffService.getInstance(project)
                val diffs = gitDiffService.getUncommittedChanges()

                if (diffs.isEmpty()) {
                    Messages.showInfoMessage(project, "工作区目前没有未提交的变更。", "CodeSense AI")
                    return@launch
                }

                val reviewPanel = ReviewToolWindowManager.openReviewTab(project, "Review: Git Changes")
                reviewPanel.appendMarkdown("\n分析工作区中 ${diffs.size} 个文件的未提交变更...\n\n")

                try {
                    val reviewService = CodeReviewService.getInstance(project)
                    reviewService.reviewChanges(diffs).collect { chunk ->
                        reviewPanel.appendChunk(chunk)
                    }
                } catch (e: Exception) {
                    logger.warn("Batch code review streaming failed", e)
                    reviewPanel.reportError(e.message ?: "未知网络错误")
                }
            } catch (e: Exception) {
                logger.error("Failed to trigger code review", e)
            }
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
