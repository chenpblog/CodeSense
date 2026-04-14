package com.deeptek.ai.idea.ui.impact

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.text.html.HTMLEditorKit

/**
 * 影响分析报告展示面板
 *
 * 在 ToolWindow 中打开一个 Tab 页，展示 Markdown 格式的影响分析报告。
 * 支持：复制 Markdown / 导出 .md 文件 / 重新分析
 */
class ImpactResultPanel(private val project: Project, private val onClose: (() -> Unit)? = null) {

    private val rootPanel = JPanel(BorderLayout())
    private val messageDisplay: JEditorPane
    private var rawMarkdown: String = ""

    init {
        // Markdown 渲染区(HTML)
        messageDisplay = JEditorPane().apply {
            isEditable = false
            contentType = "text/html"
            editorKit = com.deeptek.ai.idea.ui.ThemeAwareCss.createImpactEditorKit()
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

            add(JButton("📋 复制 Markdown").apply {
                addActionListener { copyMarkdownToClipboard() }
            })
            add(Box.createHorizontalStrut(8))
            add(JButton("💾 导出 .md").apply {
                addActionListener { exportToFile() }
            })
            add(Box.createHorizontalGlue())
            if (onClose != null) {
                add(JButton("❌ 关闭").apply {
                    addActionListener { onClose.invoke() }
                })
            }
        }

        rootPanel.add(toolbar, BorderLayout.NORTH)
        rootPanel.add(scrollPane, BorderLayout.CENTER)
    }

    fun getComponent(): JComponent = rootPanel

    /**
     * 设置完整的 Markdown 报告内容
     */
    fun setMarkdownContent(markdown: String) {
        rawMarkdown = markdown
        refreshDisplay()
    }

    /**
     * 追加 Markdown 内容（流式模式，用于 AI 风险评估部分）
     */
    fun appendMarkdown(chunk: String) {
        rawMarkdown += chunk
        refreshDisplay()
    }

    /**
     * 显示初始加载状态
     */
    fun showLoading(message: String = "正在分析调用链...") {
        rawMarkdown = "# ⏳ $message\n\n请稍候，分析可能需要几秒钟..."
        refreshDisplay()
    }

    /**
     * 显示错误
     */
    fun showError(error: String) {
        rawMarkdown += "\n\n---\n\n## ⚠️ 分析错误\n\n$error"
        refreshDisplay()
    }

    private fun refreshDisplay() {
        invokeLater {
            // 简易 Markdown → HTML 转换
            val html = simpleMarkdownToHtml(rawMarkdown)
            messageDisplay.text = """
                <html><body>
                $html
                </body></html>
            """.trimIndent()
        }
    }

    private fun copyMarkdownToClipboard() {
        val selection = StringSelection(rawMarkdown)
        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
    }

    private fun exportToFile() {
        val descriptor = FileSaverDescriptor("导出影响分析报告", "保存为 Markdown 文件", "md")
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
        val wrapper = dialog.save("impact-analysis-report.md") ?: return
        try {
            wrapper.file.writeText(rawMarkdown)
        } catch (e: Exception) {
            showError("导出失败: ${e.message}")
        }
    }

    /**
     * 简易 Markdown → HTML 转换
     * 处理标题、表格、代码块、粗体、分隔线等基本语法
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
