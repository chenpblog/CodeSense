package com.deeptek.ai.idea.ui.review

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.text.html.HTMLEditorKit
import com.intellij.openapi.application.invokeLater

/**
 * 审查结果独立面板
 *
 * 用于在一个全新的 ToolWindow Tab 中展示 AI 审查返回的 Markdown 内容。
 */
class ReviewResultPanel(private val onClose: (() -> Unit)? = null) {

    private val rootPanel = JPanel(BorderLayout())
    private val messageDisplay: JEditorPane
    private var rawMarkdown: String = ""

    init {
        messageDisplay = JEditorPane().apply {
            isEditable = false
            contentType = "text/html"
            editorKit = com.deeptek.ai.idea.ui.ThemeAwareCss.createReviewEditorKit()
        }

        val scrollPane = JBScrollPane(messageDisplay).apply {
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            border = JBUI.Borders.empty()
        }

        // 工具栏
        val toolbar = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            border = JBUI.Borders.empty(4, 8)

            add(Box.createHorizontalGlue()) // 靠右对齐
            if (onClose != null) {
                add(JButton("❌ 关闭").apply {
                    addActionListener { onClose.invoke() }
                })
            }
        }

        rootPanel.add(toolbar, BorderLayout.NORTH)
        rootPanel.add(scrollPane, BorderLayout.CENTER)
        
        // 初始装载一个提示
        appendMarkdown("**请求大模型审查中... 请稍候。**")
    }

    fun getComponent(): JComponent = rootPanel

    /**
     * 追加 Markdown 内容 (流式输出调用的简单实现)
     */
    fun appendChunk(chunk: String) {
        rawMarkdown += chunk
        refreshDisplay()
    }
    
    fun appendMarkdown(markdown: String) {
        rawMarkdown += markdown
        refreshDisplay()
    }

    fun reportError(errorMsg: String) {
        rawMarkdown += "\n\n---\n\n## ⚠️ 审查失败\n\n$errorMsg"
        refreshDisplay()
    }

    private fun refreshDisplay() {
        invokeLater {
            val html = simpleMarkdownToHtml(rawMarkdown)
            messageDisplay.text = """
                <html><body>
                <div class="ai-msg">
                    <div class="ai-role">🤖 CodeSense AI Code Review</div>
                    $html
                </div>
                </body></html>
            """.trimIndent()
        }
    }

    /**
     * 简易 Markdown → HTML 转换
     */
    private fun simpleMarkdownToHtml(md: String): String {
        val lines = md.split("\n")
        val html = StringBuilder()
        var inCodeBlock = false
        var inTable = false

        for (line in lines) {
            when {
                line.startsWith("```") -> {
                    if (inCodeBlock) {
                        html.appendLine("</pre>")
                    } else {
                        html.appendLine("<pre>")
                    }
                    inCodeBlock = !inCodeBlock
                }
                inCodeBlock -> {
                    html.appendLine(line.replace("<", "&lt;").replace(">", "&gt;"))
                }
                line.startsWith("# ") -> html.appendLine("<h1>${processInline(line.removePrefix("# "))}</h1>")
                line.startsWith("## ") -> html.appendLine("<h2>${processInline(line.removePrefix("## "))}</h2>")
                line.startsWith("### ") -> html.appendLine("<h3>${processInline(line.removePrefix("### "))}</h3>")
                line.startsWith("---") -> html.appendLine("<hr/>")
                line.startsWith("|") -> {
                    if (!inTable) {
                        html.appendLine("<table>")
                        inTable = true
                    }
                    if (line.contains("---|")) {
                        // 表头分隔行，跳过
                    } else {
                        val cells = line.split("|").filter { it.isNotBlank() }
                        html.append("<tr>")
                        cells.forEach { cell ->
                            html.append("<td>${processInline(cell.trim())}</td>")
                        }
                        html.appendLine("</tr>")
                    }
                }
                line.startsWith("- ") -> {
                    html.appendLine("<li>${processInline(line.removePrefix("- "))}</li>")
                }
                line.startsWith("*") && line.endsWith("*") -> {
                    html.appendLine("<p><em>${processInline(line.trim('*', ' '))}</em></p>")
                }
                line.isBlank() -> {
                    if (inTable) {
                        html.appendLine("</table>")
                        inTable = false
                    }
                    html.appendLine("<br/>")
                }
                else -> html.appendLine("<p>${processInline(line)}</p>")
            }
        }
        if (inTable) html.appendLine("</table>")
        if (inCodeBlock) html.appendLine("</pre>")

        return html.toString()
    }

    private fun processInline(text: String): String {
        var result = text
        // 粗体 **text**
        result = Regex("\\*\\*(.*?)\\*\\*").replace(result) { "<b>${it.groupValues[1]}</b>" }
        // 行内代码 `code`
        result = Regex("`(.*?)`").replace(result) { "<code>${it.groupValues[1]}</code>" }
        return result
    }
}
