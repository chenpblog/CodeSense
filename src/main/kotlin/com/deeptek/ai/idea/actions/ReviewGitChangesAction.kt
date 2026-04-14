package com.deeptek.ai.idea.actions

import com.deeptek.ai.idea.git.GitDiffService
import com.deeptek.ai.idea.review.CodeReviewService
import com.deeptek.ai.idea.ui.review.BranchSelectDialog
import com.deeptek.ai.idea.ui.review.ReviewResultPanel
import com.deeptek.ai.idea.ui.review.ReviewToolWindowManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 审查当前分支与主干 Action
 *
 * 弹出分支选择对话框，获取两个分支之间的 diff，发送给 LLM 审查。
 * 仅挂载在 VCS 菜单中。
 */
class ReviewGitChangesAction : AnAction() {

    private val logger = Logger.getInstance(ReviewGitChangesAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val gitDiffService = GitDiffService.getInstance(project)

        // 获取分支信息（这些操作是读取内存缓存，在 EDT 可以安全调用）
        val branches = gitDiffService.getLocalBranches()
        val currentBranch = gitDiffService.getCurrentBranch() ?: "HEAD"
        val mainBranch = gitDiffService.detectMainBranch()

        if (branches.isEmpty()) {
            Messages.showInfoMessage(project, "未检测到 Git 分支信息。", "CodeSense AI")
            return
        }

        // 在 EDT 弹出分支选择对话框
        val dialog = BranchSelectDialog(project, branches, currentBranch, mainBranch)
        if (!dialog.showAndGet()) {
            return // 用户取消
        }

        val selectedCurrent = dialog.selectedCurrentBranch
        val selectedMain = dialog.selectedMainBranch

        if (selectedCurrent == selectedMain) {
            Messages.showInfoMessage(project, "当前分支与主干分支相同，无需比较。", "CodeSense AI")
            return
        }

        // 后台执行 diff + 审查
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val diffs = gitDiffService.getBranchDiff(selectedCurrent, selectedMain)

                if (diffs.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(project, "分支 「$selectedCurrent」 与 「$selectedMain」 之间没有差异。", "CodeSense AI")
                    }
                    return@launch
                }

                var reviewPanel: ReviewResultPanel? = null
                ApplicationManager.getApplication().invokeAndWait {
                    reviewPanel = ReviewToolWindowManager.openReviewTab(
                        project, "审查: $selectedCurrent vs $selectedMain"
                    )
                }
                val panel = reviewPanel ?: return@launch

                panel.appendMarkdown(
                    "\n## 分支对比审查\n\n" +
                    "- **当前分支**: `$selectedCurrent`\n" +
                    "- **主干分支**: `$selectedMain`\n" +
                    "- **变更文件数**: ${diffs.size}\n\n" +
                    "---\n\n" +
                    "正在请求 AI 审查，请稍候...\n\n"
                )

                try {
                    val reviewService = CodeReviewService.getInstance(project)
                    reviewService.reviewChanges(diffs).collect { chunk ->
                        panel.appendChunk(chunk)
                    }
                } catch (e: Exception) {
                    logger.warn("Branch diff code review streaming failed", e)
                    panel.reportError(e.message ?: "未知网络错误")
                }
            } catch (e: Exception) {
                logger.error("Failed to get branch diff", e)
                ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        project,
                        "获取分支差异失败: ${e.message}",
                        "CodeSense AI"
                    )
                }
            }
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
