package com.deeptek.ai.idea.actions

import com.deeptek.ai.idea.analysis.*
import com.deeptek.ai.idea.llm.ChatMessage
import com.deeptek.ai.idea.llm.LlmProviderFactory
import com.deeptek.ai.idea.settings.CodeSenseSettings
import com.deeptek.ai.idea.ui.impact.ImpactResultPanel
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.*

/**
 * 分析影响范围 Action（模式 B — 指定方法分析）
 *
 * 在编辑器中将光标放在方法名上，右键 → CodeSense AI → 分析影响范围
 * 分析当前方法的调用链和影响范围，在 ToolWindow 中展示结构化报告。
 */
class AnalyzeImpactAction : AnAction() {

    private val logger = Logger.getInstance(AnalyzeImpactAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val offset = editor.caretModel.offset
        val analyzer = CallHierarchyAnalyzer.getInstance(project)
        val settings = CodeSenseSettings.getInstance()

        // 获取光标下的 PsiMethod
        val psiMethod = analyzer.findMethodAtCaret(psiFile, offset)
        if (psiMethod == null) {
            Messages.showWarningDialog(
                project,
                "请将光标放在一个 Java/Kotlin 方法名上",
                "CodeSense AI — 分析影响范围"
            )
            return
        }

        val methodName = ReadAction.compute<String, Throwable> {
            "${psiMethod.containingClass?.name ?: ""}.${psiMethod.name}"
        }

        // 打开结果面板
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("CodeSense AI") ?: return
        toolWindow.show()

        val resultPanel = ImpactResultPanel(project)
        val content = ContentFactory.getInstance()
            .createContent(resultPanel.getComponent(), "🔍 $methodName", false)
        content.isCloseable = true
        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)

        resultPanel.showLoading("正在分析 $methodName 的影响范围...")

        // 后台执行分析
        val maxDepth = settings.state.maxCallHierarchyDepth
        val enableAi = settings.state.enableAiRiskAssessment

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val startTime = System.currentTimeMillis()

                // Step 1: PSI 分析调用链
                logger.info("Analyzing impact for $methodName, maxDepth=$maxDepth")
                val biTree = analyzer.analyzeBidirectional(psiMethod, maxDepth)

                // 收集入口点
                val entryPoints = biTree.callerTree.collectEntryPoints()

                val duration = System.currentTimeMillis() - startTime

                // Step 2: 构建 ImpactReport
                val report = ImpactReport(
                    mode = AnalysisMode.SINGLE_METHOD,
                    modifiedMethods = listOf(biTree.method),
                    callChains = mapOf(biTree.method to biTree),
                    entryPoints = entryPoints,
                    analysisDuration = duration,
                    maxDepth = maxDepth
                )

                // Step 3: 生成 Markdown 报告
                val markdown = ReportGenerator.generateSingleMethodReport(report, biTree)
                resultPanel.setMarkdownContent(markdown)

                // Step 4: 可选 — AI 风险评估
                if (enableAi) {
                    try {
                        val provider = LlmProviderFactory.createDefault()
                        val prompt = buildAiRiskPrompt(markdown, methodName)
                        val messages = listOf(
                            ChatMessage.system("你是一位资深的 Java/Kotlin 后端架构师，正在审查代码影响范围分析报告。"),
                            ChatMessage.user(prompt)
                        )

                        val aiBuilder = StringBuilder()
                        provider.chatCompletionStream(messages).collect { chunk ->
                            val deltaContent = chunk.deltaContent
                            if (deltaContent != null) {
                                aiBuilder.append(deltaContent)
                                // 流式更新 AI 部分
                                val updatedMarkdown = markdown.replace(
                                    "*AI 分析将在分析完成后自动生成...*",
                                    aiBuilder.toString()
                                )
                                resultPanel.setMarkdownContent(updatedMarkdown)
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("AI risk assessment failed", e)
                        resultPanel.appendMarkdown("\n\n> ⚠️ AI 风险评估生成失败: ${e.message}")
                    }
                }

            } catch (e: Exception) {
                logger.error("Impact analysis failed", e)
                resultPanel.showError("分析失败: ${e.message}")
            }
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = editor != null && psiFile != null
    }

    private fun buildAiRiskPrompt(reportMarkdown: String, methodName: String): String {
        return """
请基于以下影响范围分析报告，对方法 $methodName 给出风险评估和建议。

分析要求：
1. **方法职责分析**：简要分析该方法的核心职责
2. **潜在风险**：标注可能的问题（⚠️ 标记），包括并发、数据一致性、性能等方面
3. **建议措施**：以表格形式给出优先级（🔴 P0 必须修复 / 🟡 P1 建议修复 / 🟢 P2 可选优化）

以下是报告内容：

$reportMarkdown
        """.trimIndent()
    }
}
