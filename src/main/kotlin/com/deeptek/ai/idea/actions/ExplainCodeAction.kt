package com.deeptek.ai.idea.actions

import com.deeptek.ai.idea.llm.ChatMessage
import com.deeptek.ai.idea.llm.LlmProviderFactory
import com.deeptek.ai.idea.ui.review.ReviewToolWindowManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 解释代码 Action
 *
 * 选中代码 → 右键 → CodeSense AI → 解释代码
 * 将选中代码发给 LLM 进行详细解释。
 */
class ExplainCodeAction : AnAction() {

    private val logger = Logger.getInstance(ExplainCodeAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return

        val selectedText = editor.selectionModel.selectedText
        if (selectedText.isNullOrBlank()) {
            return
        }

        val fileName = e.getData(CommonDataKeys.VIRTUAL_FILE)?.name ?: "unknown"

        val panel = ReviewToolWindowManager.openReviewTab(project, "💡 解释: $fileName")
        panel.appendHtml("<b>正在解释选中的代码...</b><br><br>")

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val provider = LlmProviderFactory.createDefault()
                val messages = listOf(
                    ChatMessage.system("你是一位专业的代码解读专家，运行在 IntelliJ IDEA 插件中。请用中文详细解释用户选中的代码。"),
                    ChatMessage.user("""
请详细解释以下代码的功能和实现逻辑：

文件: $fileName

```
$selectedText
```

请从以下角度分析：
1. **功能概述**：这段代码做了什么
2. **逐行解析**：关键语句的作用
3. **设计模式**：使用了哪些设计模式或编程范式
4. **注意事项**：潜在问题或优化建议
                    """.trimIndent())
                )

                provider.chatCompletionStream(messages).collect { chunk ->
                    chunk.deltaContent?.let { panel.appendChunk(it) }
                }
            } catch (ex: Exception) {
                logger.warn("Explain code failed", ex)
                panel.reportError(ex.message ?: "未知错误")
            }
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = hasSelection
    }
}
