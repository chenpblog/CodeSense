package com.deeptek.ai.idea.actions

import com.deeptek.ai.idea.analysis.*
import com.deeptek.ai.idea.git.DiffMethodExtractor
import com.deeptek.ai.idea.git.GitDiffService
import com.deeptek.ai.idea.llm.ChatMessage
import com.deeptek.ai.idea.llm.LlmProviderFactory
import com.deeptek.ai.idea.settings.CodeSenseSettings
import com.deeptek.ai.idea.ui.impact.ImpactResultPanel
import com.deeptek.ai.idea.ui.review.BranchSelectDialog
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.*

/**
 * 分析变更影响范围 Action（模式 A — Git Diff 批量分析）
 *
 * 对比两个分支之间的 diff，自动发现所有被修改的方法，
 * 批量分析调用链和影响范围，在 ToolWindow 中展示结构化报告。
 *
 * 流程：
 * 1. 弹出分支选择对话框
 * 2. 获取分支 diff → DiffMethodExtractor 提取变更方法
 * 3. 对每个变更方法执行 CallHierarchyAnalyzer 分析
 * 4. 汇总入口点 + AI 风险评估
 * 5. 输出完整的结构化 Markdown 报告
 */
class AnalyzeBranchImpactAction : AnAction() {

    private val logger = Logger.getInstance(AnalyzeBranchImpactAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val gitDiffService = GitDiffService.getInstance(project)
        val settings = CodeSenseSettings.getInstance()

        // 获取分支信息
        val branches = gitDiffService.getLocalBranches()
        val currentBranch = gitDiffService.getCurrentBranch() ?: "HEAD"
        val mainBranch = gitDiffService.detectMainBranch()

        if (branches.isEmpty()) {
            Messages.showInfoMessage(project, "未检测到 Git 分支信息。", "CodeSense AI")
            return
        }

        // 弹出分支选择对话框（复用现有的 BranchSelectDialog）
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

        // 打开结果面板
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("CodeSense AI") ?: return
        toolWindow.show()

        var content: com.intellij.ui.content.Content? = null
        val resultPanel = ImpactResultPanel(project) {
            content?.let { toolWindow.contentManager.removeContent(it, true) }
        }
        content = ContentFactory.getInstance()
            .createContent(resultPanel.getComponent(), "📊 $selectedCurrent → $selectedMain", false)
        content.isCloseable = true
        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)

        resultPanel.showLoading("正在分析 $selectedCurrent vs $selectedMain 的变更影响范围...")

        // 后台执行分析
        val maxDepth = settings.state.maxCallHierarchyDepth
        val enableAi = settings.state.enableAiRiskAssessment

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val startTime = System.currentTimeMillis()

                // Step 1: 获取分支 diff
                resultPanel.updateLoading("正在获取分支差异...")
                val diffs = gitDiffService.getBranchDiff(selectedCurrent, selectedMain)

                if (diffs.isEmpty()) {
                    ApplicationManager.getApplication().invokeLater {
                        resultPanel.showError("分支 「$selectedCurrent」 与 「$selectedMain」 之间没有差异。")
                    }
                    return@launch
                }

                // Step 2: 提取方法级变更
                resultPanel.updateLoading("正在解析变更方法 (${diffs.size} 个文件)...")
                val methodDiffs = DiffMethodExtractor.extractChangedMethods(project, diffs)

                if (methodDiffs.isEmpty()) {
                    resultPanel.setMarkdownContent(buildNoMethodChangesReport(selectedCurrent, selectedMain, diffs.size))
                    return@launch
                }

                val changedMethods = methodDiffs.map { it.methodInfo }
                logger.info("Found ${changedMethods.size} changed methods from ${diffs.size} files")

                // Step 3: 对每个变更方法执行调用链分析
                val analyzer = CallHierarchyAnalyzer.getInstance(project)
                val callChains = mutableMapOf<MethodInfo, BidirectionalCallTree>()
                val allEntryPoints = mutableListOf<EntryPointInfo>()

                changedMethods.forEachIndexed { index, methodInfo ->
                    resultPanel.updateLoading("正在分析调用链 (${index + 1}/${changedMethods.size}): ${methodInfo.displayName}...")

                    try {
                        // 在当前项目中查找对应的 PsiMethod
                        val psiMethod = findPsiMethodInProject(project, methodInfo)
                        if (psiMethod != null) {
                            val biTree = analyzer.analyzeBidirectional(psiMethod, maxDepth)
                            callChains[methodInfo] = biTree

                            // 收集入口点并标记影响的变更方法
                            val entryPoints = biTree.callerTree.collectEntryPoints()
                            entryPoints.forEach { ep ->
                                ep.affectedMethods.toMutableList().let { list ->
                                    // 通过创建新对象来追加 affectedMethods
                                }
                            }
                            allEntryPoints.addAll(entryPoints)
                        } else {
                            logger.warn("Cannot find PsiMethod for ${methodInfo.displayName} in current project")
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to analyze call chain for ${methodInfo.displayName}", e)
                    }
                }

                // 去重入口点（同一个入口方法可能被多个变更方法引用）
                val uniqueEntryPoints = allEntryPoints
                    .distinctBy { "${it.method.className}.${it.method.methodName}" }

                val duration = System.currentTimeMillis() - startTime

                val defaultProviderName = try {
                    settings.getDefaultProvider().displayName
                } catch (e: Exception) { "未使用" }

                // Step 4: 构建报告
                val changedFileCount = diffs.count {
                    it.filePath.endsWith(".java") || it.filePath.endsWith(".kt")
                }

                val report = ImpactReport(
                    mode = AnalysisMode.GIT_DIFF,
                    modifiedMethods = changedMethods,
                    callChains = callChains,
                    entryPoints = uniqueEntryPoints,
                    analysisDuration = duration,
                    maxDepth = maxDepth,
                    metadata = mapOf(
                        "sourceBranch" to selectedCurrent,
                        "targetBranch" to selectedMain,
                        "llmModel" to defaultProviderName,
                        "changedFileCount" to changedFileCount.toString()
                    )
                )

                // Step 5: 生成初始报告
                resultPanel.setMarkdownContent(ReportGenerator.generateGitDiffReport(report))

                // Step 6: AI 风险评估（可选）
                if (enableAi && callChains.isNotEmpty()) {
                    try {
                        val provider = LlmProviderFactory.createDefault()

                        // 异步请求 1：入口点短评
                        if (uniqueEntryPoints.isNotEmpty()) {
                            launch {
                                try {
                                    val needAiEntryPoints = uniqueEntryPoints.filter { it.codeComment.isNullOrBlank() }
                                    if (needAiEntryPoints.isNotEmpty()) {
                                        val epPrompt = buildEntryPointPrompt(needAiEntryPoints, changedMethods)
                                        val epMessages = listOf(
                                            ChatMessage.system("你是一个专业的代码影响评估助手，擅长分析方法变更对上游调用者的影响。"),
                                            ChatMessage.user(epPrompt)
                                        )
                                        val response = provider.chatCompletion(epMessages)
                                        val lines = response.content.orEmpty().lines()
                                            .filter { it.contains("ENTRY_") }
                                            .map { it.substringAfter(":", "").trim() }

                                        synchronized(report) {
                                            needAiEntryPoints.forEachIndexed { i, ep ->
                                                ep.aiExplanation = lines.getOrNull(i)?.takeIf { it.isNotBlank() }
                                            }
                                            resultPanel.setMarkdownContent(ReportGenerator.generateGitDiffReport(report))
                                        }
                                    }
                                } catch (e: Exception) {
                                    logger.warn("AI entry point explanation failed", e)
                                }
                            }
                        }

                        // 异步请求 2：全局风险评估（流式）
                        launch {
                            try {
                                val reportMarkdown = ReportGenerator.generateGitDiffReport(report)
                                val prompt = buildAiRiskPrompt(reportMarkdown, selectedCurrent, selectedMain)
                                val messages = listOf(
                                    ChatMessage.system("你是一位资深的 Java/Kotlin 后端架构师，正在审查分支合并的影响范围分析报告。"),
                                    ChatMessage.user(prompt)
                                )

                                val aiBuilder = StringBuilder()
                                provider.chatCompletionStream(messages).collect { chunk ->
                                    val delta = chunk.deltaContent
                                    if (delta != null) {
                                        aiBuilder.append(delta)
                                        synchronized(report) {
                                            report.aiSummary = aiBuilder.toString()
                                            resultPanel.setMarkdownContent(ReportGenerator.generateGitDiffReport(report))
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                logger.warn("AI risk assessment failed", e)
                                synchronized(report) {
                                    report.aiSummary = "\n\n> ⚠️ AI 风险评估生成失败: ${e.message}"
                                    resultPanel.setMarkdownContent(ReportGenerator.generateGitDiffReport(report))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("AI provider initialization failed", e)
                    }
                }

            } catch (e: Exception) {
                logger.error("Branch impact analysis failed", e)
                resultPanel.showError("分析失败: ${e.message}")
            }
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    // ====== 辅助方法 ======

    /**
     * 在当前项目中查找与 MethodInfo 匹配的 PsiMethod
     */
    private fun findPsiMethodInProject(project: Project, methodInfo: MethodInfo): PsiMethod? {
        return runReadAction {
            try {
                val scope = GlobalSearchScope.projectScope(project)
                val cache = PsiShortNamesCache.getInstance(project)

                // 按方法名查找
                val methods = cache.getMethodsByName(methodInfo.methodName, scope)

                // 匹配类名
                methods.firstOrNull { method ->
                    method.containingClass?.name == methodInfo.className
                }
            } catch (e: Exception) {
                logger.warn("Failed to find PsiMethod for ${methodInfo.displayName}", e)
                null
            }
        }
    }

    /**
     * 构建无方法变更时的简要报告
     */
    private fun buildNoMethodChangesReport(current: String, main: String, fileCount: Int): String {
        return """
# 📊 影响范围分析报告

| 项目 | 值 |
|------|---|
| **分析模式** | Git Diff 分支对比 |
| **当前分支** | `$current` |
| **目标分支** | `$main` |
| **变更文件数** | $fileCount |
| **变更方法数** | 0 |

---

> ℹ️ 本次分支差异中未检测到 Java/Kotlin 方法级变更。
> 变更可能仅涉及配置文件、资源文件或前端代码。
        """.trimIndent()
    }

    /**
     * 构建 AI 风险评估 Prompt（Git Diff 模式专用）
     */
    private fun buildAiRiskPrompt(reportMarkdown: String, currentBranch: String, targetBranch: String): String {
        return """
请基于以下分支合并影响范围分析报告，给出综合风险评估和建议。

分支信息：
- 当前分支: $currentBranch
- 目标分支: $targetBranch

分析要求：
1. **风险等级判断**：根据变更方法数、受影响入口点数和变更复杂度，给出 🔴 高风险 / ⚠️ 中等风险 / 🟡 低风险 / 🟢 无风险
2. **发现的问题**：
   - 🐛 潜在 Bug（并发、空指针、数据不一致等）
   - ⚡ 性能影响（额外调用、循环效率等）
   - ✅ 正面评价（好的实践）
3. **建议措施**：以表格形式给出优先级（🔴 P0 必须修复 / 🟡 P1 建议修复 / 🟢 P2 可选优化），包含涉及方法和影响入口数

注意：请只基于报告中展示的变更信息和调用链进行分析，不要臆测报告中未提及的内容。

以下是报告内容：

$reportMarkdown
        """.trimIndent()
    }

    /**
     * 构建入口点短评 Prompt
     */
    private fun buildEntryPointPrompt(entryPoints: List<EntryPointInfo>, changedMethods: List<MethodInfo>): String {
        val methodNames = changedMethods.joinToString(", ") { it.displayName }

        val epList = entryPoints.mapIndexed { index, ep ->
            val pathInfo = ep.path ?: ep.triggerCondition ?: "-"
            val annoInfo = if (ep.method.annotations.isNotEmpty()) {
                " 注解: ${ep.method.annotations.joinToString(", ") { "@$it" }}"
            } else ""
            "${index + 1}. [${ep.type.displayName}] ${ep.method.displayNameWithLine} | 签名: ${ep.method.signature} | 路径: $pathInfo$annoInfo"
        }.joinToString("\n")

        return """
基于方法变更 `$methodNames`，请分别为以下受影响的业务入口点提供一句话（20字以内）的影响短评。

要求：
1. 根据入口点的类型、方法名、签名和路径信息，推断该入口点受变更影响后可能出现的具体问题
2. 短评应具体化，例如"用户详情接口返回数据异常"、"定时同步任务执行报错"
3. 避免笼统描述，要结合方法名和路径给出有针对性的分析
4. 必须严格按以下格式返回，每个入口点一行：
ENTRY_1: xxx
ENTRY_2: yyy

入口点列表：
$epList
        """.trimIndent()
    }
}
