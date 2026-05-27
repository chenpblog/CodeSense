package com.deeptek.ai.idea.ui.chat

import com.deeptek.ai.idea.agent.AgentContext
import com.deeptek.ai.idea.agent.AgentEvent
import com.deeptek.ai.idea.agent.AgentExecutor
import com.deeptek.ai.idea.agent.AgentFactory
import com.deeptek.ai.idea.llm.ChatMessage
import com.deeptek.ai.idea.llm.LlmException
import com.deeptek.ai.idea.llm.LlmProviderFactory
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*

/**
 * Chat 面板 — 支持普通对话 + Agent 模式
 *
 * 基于 Swing 的 Chat 界面，包含：
 * - 消息显示区域（HTML 渲染）
 * - 输入框 + 发送按钮
 * - 模式切换（Chat / Agent）
 * - Agent 工具调用过程实时展示
 */
class ChatPanel(private val project: Project) {

    private val logger = Logger.getInstance(ChatPanel::class.java)

    // 对话历史（普通 Chat 模式）
    private val messages = mutableListOf<ChatMessage>()
    private val systemPrompt = ChatMessage.system(
        """你是 CodeSense AI，一个专业的代码助手。你运行在 IntelliJ IDEA 插件中。
你的职责是帮助开发者理解代码、审查代码、分析代码影响范围。
请使用中文回答。回答要专业、简洁、有条理。"""
    )

    // Agent 上下文
    private var agentContext: AgentContext? = null
    private var isAgentMode = false

    // UI 组件
    private val rootPanel = JPanel(BorderLayout())
    private val messageDisplay: JEditorPane
    private val inputField: JTextArea
    private val sendButton: JButton
    private val statusLabel: JLabel
    private val modeToggle: JToggleButton

    // HTML 内容拼接
    private val htmlContent = StringBuilder()

    // 协程
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var currentJob: Job? = null

    init {
        // 消息显示区域
        messageDisplay = JEditorPane().apply {
            isEditable = false
            contentType = "text/html"
            editorKit = com.deeptek.ai.idea.ui.ThemeAwareCss.createChatEditorKit()
        }
        updateDisplay()

        val scrollPane = JBScrollPane(messageDisplay).apply {
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        }

        // 输入区域
        inputField = JTextArea(3, 0).apply {
            lineWrap = true
            wrapStyleWord = true
            font = Font("Microsoft YaHei", Font.PLAIN, 13)
            border = JBUI.Borders.empty(8)
            addKeyListener(object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                        e.consume()
                        onSend()
                    }
                }
            })
        }

        sendButton = JButton("发送").apply { addActionListener { onSend() } }

        statusLabel = JLabel(" ").apply {
            foreground = JBColor.GRAY
            font = font.deriveFont(11f)
            border = JBUI.Borders.emptyLeft(8)
        }

        modeToggle = JToggleButton("🤖 Agent").apply {
            toolTipText = "切换 Agent 模式（启用工具调用）"
            addActionListener {
                isAgentMode = isSelected
                statusLabel.text = if (isAgentMode) "Agent 模式（可使用工具）" else " "
            }
        }

        // 底部布局
        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(4)
            add(modeToggle)
            add(Box.createHorizontalGlue())
            add(sendButton)
        }

        val inputPanel = JPanel(BorderLayout()).apply {
            add(statusLabel, BorderLayout.NORTH)
            add(JBScrollPane(inputField).apply { preferredSize = Dimension(0, 80) }, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }

        rootPanel.apply {
            add(scrollPane, BorderLayout.CENTER)
            add(inputPanel, BorderLayout.SOUTH)
        }

        messages.add(systemPrompt)
    }

    fun getComponent(): JComponent = rootPanel

    private fun onSend() {
        val text = inputField.text?.trim() ?: return
        if (text.isEmpty()) return

        inputField.text = ""
        inputField.isEnabled = false
        sendButton.isEnabled = false

        appendUserMessage(text)

        if (isAgentMode) {
            onSendAgent(text)
        } else {
            onSendChat(text)
        }
    }

    // ====== 普通 Chat 模式 ======

    private fun onSendChat(text: String) {
        messages.add(ChatMessage.user(text))
        logger.info("[Chat] 用户发送消息: ${text.take(50)}..., 历史消息数: ${messages.size}")

        currentJob = scope.launch {
            try {
                setStatus("AI 正在思考...")

                // 创建 Provider（降级处理）
                val provider = try {
                    logger.info("[Chat] 开始创建 Provider...")
                    val p = LlmProviderFactory.createDefault()
                    logger.info("[Chat] Provider 创建成功: ${p.name}, model=${p.modelName}")
                    p
                } catch (e: Exception) {
                    logger.warn("[Chat] Provider 创建失败: ${e.message}")
                    finishAiMessage()
                    appendErrorMessage("模型初始化失败: ${e.message}\n\n请检查 Settings → Tools → CodeSense AI 中的模型配置。")
                    setStatus("配置错误")
                    enableInput()
                    return@launch
                }

                val responseBuilder = StringBuilder()
                var hasReasoningContent = false
                startAiMessage()

                // 流式请求（降级处理）
                var streamError: String? = null
                logger.info("[Chat] 开始调用 chatCompletionStream...")
                try {
                    provider.chatCompletionStream(messages)
                        .catch { e ->
                            logger.error("[Chat] Stream error: ${e.javaClass.simpleName}: ${e.message}", e)
                            streamError = e.message ?: "未知流式错误"
                        }
                        .collect { chunk ->
                            // 处理思考过程内容（GLM-5 等思考模型）
                            chunk.deltaReasoningContent?.let {
                                hasReasoningContent = true
                                appendThinkingChunk(it)
                            }
                            // 处理正式回复内容
                            chunk.deltaContent?.let {
                                responseBuilder.append(it)
                                appendAiChunk(it)
                            }
                        }
                } catch (e: Exception) {
                    logger.error("[Chat] Stream 调用异常: ${e.javaClass.simpleName}: ${e.message}", e)
                    streamError = e.message ?: "未知错误"
                }
                logger.info("[Chat] Stream 完成, 响应长度: ${responseBuilder.length}, streamError=$streamError")

                finishAiMessage()
                val fullResponse = responseBuilder.toString()

                when {
                    // 有流式错误
                    streamError != null && fullResponse.isEmpty() -> {
                        appendErrorMessage("请求失败: $streamError")
                        setStatus("请求失败")
                    }
                    streamError != null && fullResponse.isNotEmpty() -> {
                        // 部分内容已接收，追加错误提示
                        messages.add(ChatMessage.assistant(fullResponse))
                        appendErrorMessage("⚠️ 回复可能不完整: $streamError")
                        setStatus("回复不完整")
                    }
                    // 流正常结束但响应为空
                    fullResponse.isEmpty() && hasReasoningContent -> {
                        // 模型有思考过程但没产出最终回答
                        appendErrorMessage(
                            "模型完成了思考过程，但未生成最终回答。\n" +
                            "这可能是因为问题过于简单或模型处理异常。\n" +
                            "建议：请重新提问或换一种方式描述问题。"
                        )
                        setStatus("仅有思考过程")
                    }
                    fullResponse.isEmpty() -> {
                        appendErrorMessage(
                            "AI 未返回有效回复内容。\n\n" +
                            "可能原因：\n" +
                            "• 当前模型 (${provider.modelName}) 返回了不兼容的响应格式\n" +
                            "• API 响应被截断\n\n" +
                            "建议：尝试切换模型或重新提问。"
                        )
                        setStatus("未收到回复")
                    }
                    // 正常
                    else -> {
                        messages.add(ChatMessage.assistant(fullResponse))
                        setStatus(" ")
                    }
                }
            } catch (e: LlmException) {
                logger.warn("[Chat] LlmException: ${e.message}")
                finishAiMessage()
                appendErrorMessage("请求失败: ${e.message}")
                setStatus("发送失败")
            } catch (e: Exception) {
                logger.warn("[Chat] Exception: ${e.javaClass.simpleName}: ${e.message}")
                finishAiMessage()
                appendErrorMessage("发生错误: ${e.javaClass.simpleName}: ${e.message}")
                setStatus("发送失败")
            } finally {
                enableInput()
            }
        }
    }

    // ====== Agent 模式 ======

    private fun onSendAgent(text: String) {
        currentJob = scope.launch {
            try {
                setStatus("🤖 Agent 正在工作...")
                val provider = LlmProviderFactory.createDefault()
                val toolRegistry = AgentFactory.createToolRegistry()
                val executor = AgentExecutor(provider, toolRegistry, project)

                if (agentContext == null) {
                    agentContext = AgentContext()
                }

                executor.execute(text, agentContext!!).collect { event ->
                    when (event) {
                        is AgentEvent.Thinking -> {
                            appendThinkingChunk(event.text)
                        }
                        is AgentEvent.ToolCallStart -> {
                            appendToolMessage("🔧 调用工具", "${event.toolName}(${event.arguments.take(100)}...)")
                        }
                        is AgentEvent.ToolCallResult -> {
                            val preview = event.result.take(200)
                            appendToolMessage("✅ ${event.toolName}", "$preview (${event.durationMs}ms)")
                        }
                        is AgentEvent.FinalChunk -> {
                            appendAiChunk(event.text)
                        }
                        is AgentEvent.Done -> {
                            finishAiMessage()
                            setStatus("Agent 完成 (${event.totalRounds} 轮)")
                        }
                        is AgentEvent.Error -> {
                            appendErrorMessage(event.message)
                            setStatus("Agent 执行失败")
                        }
                    }
                }
            } catch (e: Exception) {
                logger.warn("Agent 错误: ${e.message}")
                appendErrorMessage("Agent 错误: ${e.message}")
                setStatus("Agent 执行失败")
            } finally {
                enableInput()
            }
        }
    }

    // ====== HTML 渲染 ======

    private fun updateDisplay() {
        invokeLater {
            messageDisplay.text = buildHtml("""
                <div class="ai-msg">
                    <div class="ai-role">🤖 CodeSense AI</div>
                    你好！我是 CodeSense AI，你的代码助手。<br>
                    开启右下角 <b>🤖 Agent</b> 可让 AI 自主使用工具完成复杂任务。
                </div>
            """.trimIndent())
        }
    }

    private fun appendUserMessage(text: String) {
        val escaped = text.replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
        htmlContent.append("""<div class="user-msg"><div class="role">👤 You</div>$escaped</div>""")
        startAiMessage()
        refreshDisplay(force = true)
    }

    private fun appendToolMessage(label: String, content: String) {
        val escaped = content.replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")
        htmlContent.append("""<div class="tool-msg"><div class="tool-role">$label</div>$escaped</div>""")
        refreshDisplay(withCurrentAi = true, force = true)
    }

    private var currentAiMarkdown = StringBuilder()
    private var currentThinkingMarkdown = StringBuilder()

    private fun startAiMessage() {
        currentAiMarkdown = StringBuilder()
        currentThinkingMarkdown = StringBuilder()
    }

    private fun appendAiChunk(content: String) {
        currentAiMarkdown.append(content)
        refreshDisplay(withCurrentAi = true)
    }

    private fun appendThinkingChunk(content: String) {
        currentThinkingMarkdown.append(content)
        refreshDisplay(withCurrentAi = true)
    }

    private fun finishAiMessage() {
        if (currentAiMarkdown.isNotEmpty() || currentThinkingMarkdown.isNotEmpty()) {
            htmlContent.append(renderAiMessage(currentAiMarkdown.toString(), currentThinkingMarkdown.toString()))
            currentAiMarkdown = StringBuilder()
            currentThinkingMarkdown = StringBuilder()
            refreshDisplay(force = true)
        }
    }

    private fun appendErrorMessage(error: String) {
        htmlContent.append("""<div class="ai-msg"><div class="error">⚠️ 错误</div>${markdownToHtml(error)}</div>""")
        refreshDisplay(force = true)
    }

    @Volatile private var lastRefreshTime = 0L
    @Volatile private var pendingWithAi = false

    private fun refreshDisplay(withCurrentAi: Boolean = false, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRefreshTime < 200) {
            if (withCurrentAi) pendingWithAi = true
            return
        }
        val actuallyWithAi = withCurrentAi || pendingWithAi
        pendingWithAi = false
        lastRefreshTime = now

        invokeLater {
            val aiPart = if (actuallyWithAi && (currentAiMarkdown.isNotEmpty() || currentThinkingMarkdown.isNotEmpty())) {
                renderAiMessage(currentAiMarkdown.toString(), currentThinkingMarkdown.toString())
            } else ""

            messageDisplay.text = buildHtml("""
                <div class="ai-msg"><div class="ai-role">🤖 CodeSense AI</div>你好！开启 Agent 模式可使用工具。</div>
                $htmlContent $aiPart
            """.trimIndent())
            messageDisplay.caretPosition = messageDisplay.document.length
        }
    }

    private fun buildHtml(body: String) = "<html><body><div style='padding:8px;'>$body</div></body></html>"

    private fun renderAiMessage(markdown: String, thinking: String = ""): String {
        val thinkingHtml = if (thinking.isNotBlank()) {
            """
            <div class="thinking-msg">
                <div class="thinking-role">💭 Thinking</div>
                ${markdownToHtml(thinking)}
            </div>
            """.trimIndent()
        } else ""
        val answerHtml = if (markdown.isNotBlank()) markdownToHtml(markdown) else ""
        return """<div class="ai-msg"><div class="ai-role">🤖 CodeSense AI</div>$thinkingHtml$answerHtml</div>"""
    }

    private fun markdownToHtml(markdown: String): String {
        val html = StringBuilder()
        var inCodeBlock = false
        var inTable = false
        var inList = false
        var codeLanguage = ""

        fun closeTableIfNeeded() {
            if (inTable) {
                html.appendLine("</table>")
                inTable = false
            }
        }

        fun closeListIfNeeded() {
            if (inList) {
                html.appendLine("</ul>")
                inList = false
            }
        }

        for (rawLine in markdown.split("\n")) {
            val line = rawLine.trimEnd()
            val trimmed = line.trim()

            when {
                trimmed.startsWith("```") -> {
                    closeTableIfNeeded()
                    closeListIfNeeded()
                    if (inCodeBlock) {
                        html.appendLine("</code></pre>")
                    } else {
                        codeLanguage = trimmed.removePrefix("```").trim()
                        val languageClass = if (codeLanguage.isNotEmpty()) " class=\"language-${escapeHtml(codeLanguage)}\"" else ""
                        html.appendLine("<pre><code$languageClass>")
                    }
                    inCodeBlock = !inCodeBlock
                }
                inCodeBlock -> {
                    html.appendLine(escapeHtml(rawLine))
                }
                trimmed.startsWith("# ") -> {
                    closeTableIfNeeded()
                    closeListIfNeeded()
                    html.appendLine("<h1>${processInline(trimmed.removePrefix("# "))}</h1>")
                }
                trimmed.startsWith("## ") -> {
                    closeTableIfNeeded()
                    closeListIfNeeded()
                    html.appendLine("<h2>${processInline(trimmed.removePrefix("## "))}</h2>")
                }
                trimmed.startsWith("### ") -> {
                    closeTableIfNeeded()
                    closeListIfNeeded()
                    html.appendLine("<h3>${processInline(trimmed.removePrefix("### "))}</h3>")
                }
                trimmed.startsWith("#### ") -> {
                    closeTableIfNeeded()
                    closeListIfNeeded()
                    html.appendLine("<h4>${processInline(trimmed.removePrefix("#### "))}</h4>")
                }
                trimmed == "---" || trimmed == "***" -> {
                    closeTableIfNeeded()
                    closeListIfNeeded()
                    html.appendLine("<hr/>")
                }
                trimmed.startsWith("|") && trimmed.endsWith("|") -> {
                    closeListIfNeeded()
                    if (!inTable) {
                        html.appendLine("<table>")
                        inTable = true
                    }
                    if (!trimmed.matches(Regex("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?$"))) {
                        val cells = trimmed.trim('|').split("|").map { it.trim() }
                        html.append("<tr>")
                        cells.forEach { cell -> html.append("<td>${processInline(cell)}</td>") }
                        html.appendLine("</tr>")
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    closeTableIfNeeded()
                    if (!inList) {
                        html.appendLine("<ul>")
                        inList = true
                    }
                    html.appendLine("<li>${processInline(trimmed.drop(2))}</li>")
                }
                trimmed.isBlank() -> {
                    closeTableIfNeeded()
                    closeListIfNeeded()
                    html.appendLine("<br/>")
                }
                else -> {
                    closeTableIfNeeded()
                    closeListIfNeeded()
                    html.appendLine("<p>${processInline(line)}</p>")
                }
            }
        }

        if (inCodeBlock) html.appendLine("</code></pre>")
        closeTableIfNeeded()
        closeListIfNeeded()

        return html.toString()
    }

    private fun processInline(text: String): String {
        var result = escapeHtml(text)
        result = Regex("`([^`]+)`").replace(result) { "<code>${it.groupValues[1]}</code>" }
        result = Regex("\\*\\*(.+?)\\*\\*").replace(result) { "<b>${it.groupValues[1]}</b>" }
        result = Regex("__(.+?)__").replace(result) { "<b>${it.groupValues[1]}</b>" }
        result = Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)").replace(result) { "<em>${it.groupValues[1]}</em>" }
        result = Regex("\\[([^\\]]+)]\\(([^)]+)\\)").replace(result) {
            val label = it.groupValues[1]
            val url = it.groupValues[2]
            "<a href=\"$url\">$label</a>"
        }
        return result
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }

    private fun setStatus(text: String) { invokeLater { statusLabel.text = text } }

    private fun enableInput() {
        invokeLater {
            inputField.isEnabled = true
            sendButton.isEnabled = true
            inputField.requestFocus()
        }
    }
}
