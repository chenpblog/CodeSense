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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * 分析影响范围 Action（支持方法级和类级两种分析）
 *
 * 在编辑器中：
 * - 光标在方法名上 → 分析该方法的调用链和影响范围（模式 B）
 * - 光标在类名上（非方法内部）→ 分析该类所有公共方法的影响范围（模式 C）
 *
 * 右键 → CodeSense AI → 分析影响范围
 */
class AnalyzeImpactAction : AnAction() {

    private val logger = Logger.getInstance(AnalyzeImpactAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val offset = editor.caretModel.offset
        val analyzer = CallHierarchyAnalyzer.getInstance(project)

        // 智能识别：优先方法级，回退到类级
        val psiMethod = analyzer.findMethodAtCaret(psiFile, offset)
        if (psiMethod != null) {
            // 模式 B：方法级分析
            performMethodAnalysis(project, psiMethod, analyzer)
            return
        }

        val psiClass = analyzer.findClassAtCaret(psiFile, offset)
        if (psiClass != null) {
            // 模式 C：类级分析
            performClassAnalysis(project, psiClass, analyzer)
            return
        }

        // 都找不到
        Messages.showWarningDialog(
            project,
            "请将光标放在一个 Java/Kotlin 的类名或方法名上",
            "CodeSense AI — 分析影响范围"
        )
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = editor != null && psiFile != null
    }

    // ====== 方法级分析（原有逻辑） ======

    private fun performMethodAnalysis(project: Project, psiMethod: PsiMethod, analyzer: CallHierarchyAnalyzer) {
        val settings = CodeSenseSettings.getInstance()

        val methodName = ReadAction.compute<String, RuntimeException> {
            "${psiMethod.containingClass?.name ?: ""}.${psiMethod.name}"
        }

        // 打开结果面板
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("CodeSense AI") ?: return
        toolWindow.show()

        var content: com.intellij.ui.content.Content? = null
        val tabBaseTitle = methodName
        val resultPanel = ImpactResultPanel(project) {
            content?.let { toolWindow.contentManager.removeContent(it, true) }
        }
        content = ContentFactory.getInstance()
            .createContent(resultPanel.getComponent(), "⏳ $tabBaseTitle", false)
        content.isCloseable = true
        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)

        // Tab 标题状态更新辅助函数
        val contentRef = content
        fun updateTabTitle(icon: String) {
            ApplicationManager.getApplication().invokeLater {
                contentRef.displayName = "$icon $tabBaseTitle"
            }
        }

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

                val llmModelLabel = try {
                    val cfg = settings.getDefaultProvider()
                    "${cfg.displayName} / ${cfg.modelName}"
                } catch (e: Exception) { "未使用" }

                // Step 2: 构建 ImpactReport
                val report = ImpactReport(
                    mode = AnalysisMode.SINGLE_METHOD,
                    modifiedMethods = listOf(biTree.method),
                    callChains = mapOf(biTree.method to biTree),
                    entryPoints = entryPoints,
                    analysisDuration = duration,
                    maxDepth = maxDepth,
                    metadata = mapOf("llmModel" to llmModelLabel)
                )

                // Step 3: 生成初始 Markdown 报告
                resultPanel.setMarkdownContent(ReportGenerator.generateSingleMethodReport(report, biTree))

                // Step 4: 可选 — AI 风险评估与短评 (方案 B: 双重异步请求)
                if (enableAi) {
                    updateTabTitle("🔄")
                    try {
                        val provider = LlmProviderFactory.createDefault()
                        val aiTaskCount = AtomicInteger(0)
                        val totalAiTasks = AtomicInteger(0)

                        // 计算总 AI 任务数（必须与下方 launch 的 if 条件一致）
                        if (report.entryPoints.isNotEmpty()) totalAiTasks.incrementAndGet()
                        totalAiTasks.incrementAndGet() // 风险评估始终有

                        fun onAiTaskDone() {
                            if (aiTaskCount.incrementAndGet() >= totalAiTasks.get()) {
                                synchronized(report) {
                                    report.analysisDuration = System.currentTimeMillis() - startTime
                                    report.isComplete = true
                                    resultPanel.setMarkdownContent(ReportGenerator.generateSingleMethodReport(report, biTree), true)
                                }
                                updateTabTitle("✅")
                            }
                        }

                        // 异步请求 1：所有受影响入口点的短评（三层 fallback）
                        if (report.entryPoints.isNotEmpty()) {
                            launch {
                                try {
                                    // 第一层：有代码注释的入口点，直接使用注释，不调 AI
                                    // （ReportGenerator 渲染时已处理注释标注【注释】）

                                    // 过滤出需要 AI 生成说明的入口点（无注释的）
                                    val needAiEntryPoints = report.entryPoints.filter { it.codeComment.isNullOrBlank() }

                                    if (needAiEntryPoints.isNotEmpty()) {
                                        // 第二层：调用 AI 生成无注释入口点的说明
                                        val epPrompt = buildEntryPointPrompt(needAiEntryPoints, methodName)
                                        val epMessages = listOf(
                                            ChatMessage.system("你是一个专业的代码影响评估助手，擅长分析方法变更对上游调用者的影响。"),
                                            ChatMessage.user(epPrompt)
                                        )
                                        val response = provider.chatCompletion(epMessages)
                                        // 提取以 ENTRY_ 开头的行
                                        val lines = response.content.orEmpty().lines()
                                            .filter { it.contains("ENTRY_") }
                                            .map { it.substringAfter(":", "").trim() }

                                        synchronized(report) {
                                            needAiEntryPoints.forEachIndexed { i, ep ->
                                                val aiResult = lines.getOrNull(i)
                                                ep.aiExplanation = if (!aiResult.isNullOrBlank()) aiResult else null
                                            }
                                            resultPanel.setMarkdownContent(ReportGenerator.generateSingleMethodReport(report, biTree))
                                        }
                                    } else {
                                        // 所有入口点都有注释，刷新一次报告即可
                                        synchronized(report) {
                                            resultPanel.setMarkdownContent(ReportGenerator.generateSingleMethodReport(report, biTree))
                                        }
                                    }
                                } catch (e: Exception) {
                                    logger.warn("AI entry point explanation failed", e)
                                } finally {
                                    onAiTaskDone()
                                }
                            }
                        }

                        // 异步请求 2：全局代码风险评估 (流式)
                        launch {
                            try {
                                val initMarkdown = ReportGenerator.generateSingleMethodReport(report, biTree)
                                val prompt = buildAiRiskPrompt(initMarkdown, methodName)
                                val messages = listOf(
                                    ChatMessage.system("你是一位资深的 Java/Kotlin 后端架构师，正在审查代码影响范围分析报告。"),
                                    ChatMessage.user(prompt)
                                )

                                val aiBuilder = StringBuilder()
                                provider.chatCompletionStream(messages).collect { chunk ->
                                    val deltaContent = chunk.deltaContent
                                    if (deltaContent != null) {
                                        aiBuilder.append(deltaContent)
                                        synchronized(report) {
                                            report.aiSummary = aiBuilder.toString()
                                            resultPanel.setMarkdownContent(ReportGenerator.generateSingleMethodReport(report, biTree))
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                logger.warn("AI main risk assessment failed", e)
                                synchronized(report) {
                                    report.aiSummary = "\n\n> ⚠️ AI 风险评估生成失败: ${e.message}"
                                    resultPanel.setMarkdownContent(ReportGenerator.generateSingleMethodReport(report, biTree))
                                }
                            } finally {
                                onAiTaskDone()
                            }
                        }

                    } catch (e: Exception) {
                        logger.warn("AI provider initialization failed", e)
                        synchronized(report) {
                            report.analysisDuration = System.currentTimeMillis() - startTime
                            report.isComplete = true
                            resultPanel.setMarkdownContent(ReportGenerator.generateSingleMethodReport(report, biTree), true)
                        }
                        updateTabTitle("✅")
                    }
                } else {
                    synchronized(report) {
                        report.isComplete = true
                        resultPanel.setMarkdownContent(ReportGenerator.generateSingleMethodReport(report, biTree), true)
                    }
                    updateTabTitle("✅")
                }

            } catch (e: Exception) {
                logger.error("Impact analysis failed", e)
                resultPanel.showError("分析失败: ${e.message}")
                updateTabTitle("❌")
            }
        }
    }

    // ====== 类级分析（新增逻辑） ======

    private fun performClassAnalysis(project: Project, psiClass: PsiClass, analyzer: CallHierarchyAnalyzer) {
        val settings = CodeSenseSettings.getInstance()

        val className = ReadAction.compute<String, RuntimeException> {
            psiClass.name ?: "Unknown"
        }

        // 打开结果面板
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("CodeSense AI") ?: return
        toolWindow.show()

        var content: com.intellij.ui.content.Content? = null
        val tabBaseTitle = "🏗 $className"
        val resultPanel = ImpactResultPanel(project) {
            content?.let { toolWindow.contentManager.removeContent(it, true) }
        }
        content = ContentFactory.getInstance()
            .createContent(resultPanel.getComponent(), "⏳ $tabBaseTitle", false)
        content.isCloseable = true
        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)

        // Tab 标题状态更新辅助函数
        val contentRef = content
        fun updateTabTitle(icon: String) {
            ApplicationManager.getApplication().invokeLater {
                contentRef.displayName = "$icon $tabBaseTitle"
            }
        }

        resultPanel.showLoading("正在分析类 $className 的影响范围...")

        // 后台执行分析
        val maxDepth = settings.state.maxCallHierarchyDepth
        val enableAi = settings.state.enableAiRiskAssessment

        CoroutineScope(Dispatchers.Default).launch {
            try {
                val startTime = System.currentTimeMillis()

                // Step 1: 分析整个类
                logger.info("Analyzing class impact for $className, maxDepth=$maxDepth")
                val classResult = analyzer.analyzeClass(psiClass, maxDepth)

                val duration = System.currentTimeMillis() - startTime

                val llmModelLabel = try {
                    val cfg = settings.getDefaultProvider()
                    "${cfg.displayName} / ${cfg.modelName}"
                } catch (e: Exception) { "未使用" }

                // Step 2: 构建 ImpactReport
                val allMethods = classResult.methodResults.keys.toList()
                val report = ImpactReport(
                    mode = AnalysisMode.SINGLE_CLASS,
                    modifiedMethods = allMethods,
                    callChains = classResult.methodResults,
                    entryPoints = classResult.allEntryPoints,
                    analysisDuration = duration,
                    maxDepth = maxDepth,
                    metadata = mapOf("llmModel" to llmModelLabel)
                )

                // Step 3: 生成初始 Markdown 报告
                resultPanel.setMarkdownContent(ReportGenerator.generateClassReport(report, classResult))

                // Step 4: 可选 — AI 风险评估与入口点短评
                if (enableAi && classResult.methodResults.isNotEmpty()) {
                    updateTabTitle("🔄")
                    try {
                        val provider = LlmProviderFactory.createDefault()
                        val aiTaskCount = AtomicInteger(0)
                        val totalAiTasks = AtomicInteger(0)

                        if (report.entryPoints.isNotEmpty()) totalAiTasks.incrementAndGet()
                        totalAiTasks.incrementAndGet() // 风险评估始终有

                        fun onAiTaskDone() {
                            if (aiTaskCount.incrementAndGet() >= totalAiTasks.get()) {
                                synchronized(report) {
                                    report.analysisDuration = System.currentTimeMillis() - startTime
                                    report.isComplete = true
                                    resultPanel.setMarkdownContent(ReportGenerator.generateClassReport(report, classResult), true)
                                }
                                updateTabTitle("✅")
                            }
                        }

                        // 异步请求 1：入口点短评
                        if (report.entryPoints.isNotEmpty()) {
                            launch {
                                try {
                                    val needAiEntryPoints = report.entryPoints.filter { it.codeComment.isNullOrBlank() }
                                    if (needAiEntryPoints.isNotEmpty()) {
                                        val epPrompt = buildEntryPointPrompt(needAiEntryPoints, className)
                                        val epMessages = listOf(
                                            ChatMessage.system("你是一个专业的代码影响评估助手，擅长分析类变更对上游调用者的影响。"),
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
                                            resultPanel.setMarkdownContent(ReportGenerator.generateClassReport(report, classResult))
                                        }
                                    }
                                } catch (e: Exception) {
                                    logger.warn("AI entry point explanation failed for class $className", e)
                                } finally {
                                    onAiTaskDone()
                                }
                            }
                        }

                        // 异步请求 2：全局风险评估（流式）
                        launch {
                            try {
                                val initMarkdown = ReportGenerator.generateClassReport(report, classResult)
                                val prompt = buildClassAiRiskPrompt(initMarkdown, className)
                                val messages = listOf(
                                    ChatMessage.system("你是一位资深的 Java/Kotlin 后端架构师，正在审查类级别的影响范围分析报告。"),
                                    ChatMessage.user(prompt)
                                )

                                val aiBuilder = StringBuilder()
                                provider.chatCompletionStream(messages).collect { chunk ->
                                    val delta = chunk.deltaContent
                                    if (delta != null) {
                                        aiBuilder.append(delta)
                                        synchronized(report) {
                                            report.aiSummary = aiBuilder.toString()
                                            resultPanel.setMarkdownContent(ReportGenerator.generateClassReport(report, classResult))
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                logger.warn("AI risk assessment failed for class $className", e)
                                synchronized(report) {
                                    report.aiSummary = "\n\n> ⚠️ AI 风险评估生成失败: ${e.message}"
                                    resultPanel.setMarkdownContent(ReportGenerator.generateClassReport(report, classResult))
                                }
                            } finally {
                                onAiTaskDone()
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn("AI provider initialization failed", e)
                        synchronized(report) {
                            report.analysisDuration = System.currentTimeMillis() - startTime
                            report.isComplete = true
                            resultPanel.setMarkdownContent(ReportGenerator.generateClassReport(report, classResult), true)
                        }
                        updateTabTitle("✅")
                    }
                } else {
                    synchronized(report) {
                        report.isComplete = true
                        resultPanel.setMarkdownContent(ReportGenerator.generateClassReport(report, classResult), true)
                    }
                    updateTabTitle("✅")
                }

            } catch (e: Exception) {
                logger.error("Class impact analysis failed for $className", e)
                resultPanel.showError("分析失败: ${e.message}")
                updateTabTitle("❌")
            }
        }
    }

    // ====== Prompt 构建 ======

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

    private fun buildClassAiRiskPrompt(reportMarkdown: String, className: String): String {
        return """
请基于以下类级别影响范围分析报告，对类 $className 给出综合风险评估和建议。

分析要求：
1. **类职责分析**：简要分析该类的核心职责和设计模式
2. **各方法风险概览**：分析各个公共方法可能存在的问题
3. **潜在风险**：标注可能的问题（⚠️ 标记），包括并发、数据一致性、性能、类设计等方面
4. **建议措施**：以表格形式给出优先级（🔴 P0 必须修复 / 🟡 P1 建议修复 / 🟢 P2 可选优化），包含涉及的方法

以下是报告内容：

$reportMarkdown
        """.trimIndent()
    }

    private fun buildEntryPointPrompt(entryPoints: List<EntryPointInfo>, targetName: String): String {
        val epList = entryPoints.mapIndexed { index, ep ->
            val pathInfo = ep.path ?: ep.triggerCondition ?: "-"
            val annoInfo = if (ep.method.annotations.isNotEmpty()) {
                " 注解: ${ep.method.annotations.joinToString(", ") { "@$it" }}"
            } else ""
            "${index + 1}. [${ep.type.displayName}] ${ep.method.displayNameWithLine} | 签名: ${ep.method.signature} | 路径: $pathInfo$annoInfo"
        }.joinToString("\n")

        return """
基于 `$targetName` 的变更，请分别为以下受影响的业务入口点提供一句话（20字以内）的影响短评。

要求：
1. 根据入口点的类型、方法名、签名和路径信息，推断该入口点受变更影响后可能出现的具体问题
2. 短评应具体化，例如"用户详情接口返回数据异常"、"定时同步任务执行报错"、"订单消息消费失败"
3. 避免笼统描述，要结合方法名和路径给出有针对性的分析
4. 必须严格按以下格式返回，每个入口点一行：
ENTRY_1: xxx
ENTRY_2: yyy

入口点列表：
$epList
        """.trimIndent()
    }
}
