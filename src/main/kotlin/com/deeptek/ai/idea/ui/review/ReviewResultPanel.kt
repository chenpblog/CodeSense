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
class ReviewResultPanel {

    private val rootPanel = JPanel(BorderLayout())
    private val messageDisplay: JEditorPane
    private val htmlContent = StringBuilder()

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

        rootPanel.add(scrollPane, BorderLayout.CENTER)
        
        // 初始装载一个提示
        appendHtml("请求大模型审查中... 请稍候。")
    }

    fun getComponent(): JComponent = rootPanel

    /**
     * 追加 HTML 内容 (流式输出调用的简单实现，这里直接将换行符简单替换。
     * 后续如果想完美支持 Markdown，可以引入 flexmark-java 等依赖，这里做简单处理)
     */
    fun appendChunk(chunk: String) {
        val converted = chunk
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")
        
        // 粗略处理加粗
        val boldPattern = Regex("\\*\\*(.*?)\\*\\*")
        val withBold = boldPattern.replace(converted) { match ->
            "<b>${match.groupValues[1]}</b>"
        }
        
        htmlContent.append(withBold)
        refreshDisplay()
    }
    
    fun appendHtml(rawHtml: String) {
        htmlContent.append(rawHtml)
        refreshDisplay()
    }

    fun reportError(errorMsg: String) {
        htmlContent.append("<br><br><span style='color: red;'><b>审查失败:</b> $errorMsg</span>")
        refreshDisplay()
    }

    private fun refreshDisplay() {
        invokeLater {
            messageDisplay.text = """
                <html><body>
                <div class="ai-msg">
                    <div class="ai-role">🤖 CodeSense AI Code Review</div>
                    $htmlContent
                </div>
                </body></html>
            """.trimIndent()
        }
    }
}
