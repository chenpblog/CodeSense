package com.deeptek.ai.idea.ui.json2bean

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import com.intellij.openapi.diagnostic.Logger

class JsonBeanPreviewDialog(
    private val project: Project,
    private val className: String,
    private val jsonContent: String,
    private val javaContent: String,
    private val rootNode: com.deeptek.ai.idea.ui.json2bean.model.JsonPropertyNode,
    private val targetDirectory: VirtualFile?
) : DialogWrapper(project) {

    private lateinit var jsonEditor: EditorEx
    private lateinit var jsonEnglishEditor: EditorEx
    private lateinit var javaEditor: EditorEx
    private var modifiedJavaContent: String = javaContent
    // 底部状态提示标签（替代 Balloon 避免 Disposer 内存泄漏）
    private lateinit var statusLabel: JBLabel

    companion object {
        private val logger = Logger.getInstance(JsonBeanPreviewDialog::class.java)
    }

    init {
        title = "CodeSense AI: 结果预览与导出 ($className)"
        init()
    }
    
    override fun createActions() = emptyArray<javax.swing.Action>()

    override fun createCenterPanel(): JComponent {
        val splitPaneLeft = JBSplitter(false, 0.33f)
        splitPaneLeft.preferredSize = Dimension(1360, 600)
        
        val splitPaneRight = JBSplitter(false, 0.5f)

        // 1. 左侧 JSON Editor
        jsonEditor = createEditor(jsonContent, com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE, isViewer = true)
        val jsonPanel = JPanel(BorderLayout())
        jsonPanel.add(JBLabel("JSON Demo Preview:"), BorderLayout.NORTH)
        jsonPanel.add(jsonEditor.component, BorderLayout.CENTER)
        splitPaneLeft.firstComponent = jsonPanel

        // 2. 中间 JSON English Editor
        jsonEnglishEditor = createEditor("{\n  \"// \": \"Click 'AI 转换' below to generate english JSON here.\"\n}", com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE, isViewer = false)
        val jsonEnglishPanel = JPanel(BorderLayout())
        jsonEnglishPanel.add(JBLabel("JSON English:"), BorderLayout.NORTH)
        jsonEnglishPanel.add(jsonEnglishEditor.component, BorderLayout.CENTER)
        splitPaneRight.firstComponent = jsonEnglishPanel

        // 3. 右侧 Java Editor
        javaEditor = createEditor(javaContent, JavaFileType.INSTANCE, isViewer = false)
        val javaPanel = JPanel(BorderLayout())
        javaPanel.add(JBLabel("Generated Java Bean (Editable):"), BorderLayout.NORTH)
        javaPanel.add(javaEditor.component, BorderLayout.CENTER)
        splitPaneRight.secondComponent = javaPanel

        splitPaneLeft.secondComponent = splitPaneRight

        val rootPanel = JPanel(BorderLayout(JBUI.scale(10), JBUI.scale(10)))
        rootPanel.add(splitPaneLeft, BorderLayout.CENTER)

        // 底部操作区
        val bottomPanel = JPanel(BorderLayout())
        
        val buttonsPanel = JPanel(FlowLayout(FlowLayout.RIGHT))

        val backBtn = JButton("← 返回")
        backBtn.toolTipText = "关闭当前预览，返回 JSON 设计器继续编辑"
        backBtn.addActionListener {
            close(CANCEL_EXIT_CODE)
            ApplicationManager.getApplication().invokeLater {
                val designDialog = JsonDesignDialog(project, targetDirectory)
                designDialog.show()
            }
        }

        val aiTranslateBtn = JButton("AI 转换")
        aiTranslateBtn.toolTipText = "将原 JSON 中的中文 Key 智能翻译为英文，同时合理 Mock 英文 Value"
        aiTranslateBtn.addActionListener { doAiTranslation(aiTranslateBtn) }
        
        val generateBeanBtn = JButton("生成 Bean")
        generateBeanBtn.toolTipText = "组合当前中间栏的英文与原版中文描述，生成完美结合的代码"
        generateBeanBtn.addActionListener { doRegenerateBean(generateBeanBtn) }

        val copyJsonBtn = JButton("Copy JSON")
        copyJsonBtn.addActionListener {
            val englishJson = jsonEnglishEditor.document.text.trim()
            val hasEnglishJson = englishJson.isNotEmpty() && (englishJson.startsWith("{") || englishJson.startsWith("["))
            if (hasEnglishJson) {
                copyToClipboard(englishJson)
                setStatus("✓ 已复制 AI 转换后的 English JSON")
            } else {
                copyToClipboard(jsonContent)
                setStatus("✓ 已复制原始 JSON")
            }
        }

        val copyJavaBtn = JButton("Copy Java Bean")
        copyJavaBtn.addActionListener {
            modifiedJavaContent = javaEditor.document.text
            copyToClipboard(modifiedJavaContent)
            setStatus("✓ Java Bean 源码已复制")
        }

        val generateFileBtn = JButton("Generate to Project...")
        generateFileBtn.addActionListener {
            generateFile(generateFileBtn)
        }
        
        val closeBtn = JButton("Close")
        closeBtn.addActionListener { close(OK_EXIT_CODE) }

        buttonsPanel.add(backBtn)
        buttonsPanel.add(JBLabel(" | "))
        buttonsPanel.add(aiTranslateBtn)
        buttonsPanel.add(generateBeanBtn)
        buttonsPanel.add(JBLabel(" | "))
        buttonsPanel.add(copyJsonBtn)
        buttonsPanel.add(copyJavaBtn)
        buttonsPanel.add(generateFileBtn)
        buttonsPanel.add(closeBtn)

        // 状态文字标签（最左侧）
        statusLabel = JBLabel(" ")
        statusLabel.foreground = java.awt.Color(100, 180, 100)
        
        bottomPanel.add(statusLabel, BorderLayout.WEST)
        bottomPanel.add(buttonsPanel, BorderLayout.EAST)

        rootPanel.add(bottomPanel, BorderLayout.SOUTH)

        return rootPanel
    }

    // 当前活跃的自动清除定时器（避免多个 Timer 并发冲突）
    private var statusClearTimer: javax.swing.Timer? = null

    /**
     * 在底部状态栏显示提示文字（替代 Balloon，避免 Disposer 内存泄漏）
     * @param autoClear 是否 3 秒后自动清除，加载中的提示应传 false 保持显示
     */
    private fun setStatus(message: String, isError: Boolean = false, autoClear: Boolean = true) {
        statusClearTimer?.stop()
        statusClearTimer = null
        statusLabel.foreground = if (isError) java.awt.Color(220, 80, 80) else java.awt.Color(100, 180, 100)
        statusLabel.text = message
        if (autoClear) {
            // 3秒后自动清除
            statusClearTimer = javax.swing.Timer(3000) { statusLabel.text = " " }.apply {
                isRepeats = false
                start()
            }
        }
    }

    /**
     * 安全地设置编辑器文本内容并强制刷新视图。
     * 使用 runWriteAction（非 WriteCommandAction），因为这些 Document 是通过
     * EditorFactory.createDocument() 创建的独立文档，不关联 PSI/UndoManager。
     */
    private fun safeSetEditorText(editor: EditorEx, text: String) {
        if (editor.isDisposed) {
            logger.warn("[safeSetEditorText] 编辑器已被 disposed，跳过 setText（对话框可能已关闭）")
            return
        }
        try {
            ApplicationManager.getApplication().runWriteAction {
                editor.document.setText(text)
            }
            // ★ 强制刷新编辑器视图：setText 只更新了 Document 模型，
            //   但编辑器组件可能不会自动重绘
            editor.component.revalidate()
            editor.component.repaint()
            editor.contentComponent.revalidate()
            editor.contentComponent.repaint()
            
            logger.info("[safeSetEditorText] 成功, 新文档长度=${editor.document.textLength}, 前100字=${editor.document.text.take(100)}")
        } catch (e: Throwable) {
            logger.error("[safeSetEditorText] 失败: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    private fun createEditor(text: String, fileType: com.intellij.openapi.fileTypes.FileType, isViewer: Boolean): EditorEx {
        val factory = EditorFactory.getInstance()
        val document = factory.createDocument(text)
        val editor = factory.createEditor(document, project, fileType, isViewer) as EditorEx
        return editor
    }

    private fun copyToClipboard(text: String) {
        val selection = StringSelection(text)
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(selection, selection)
    }

    private fun generateFile(btn: JButton) {
        modifiedJavaContent = javaEditor.document.text
        
        // 使用 IntelliJ 的 Package 选择器，显示工程的 Java 包结构树
        val packageChooser = com.intellij.ide.util.PackageChooserDialog(
            "Select Package to Save $className.java", project
        )
        packageChooser.show()
        
        val selectedPackage = packageChooser.selectedPackage ?: return
        val pkgName = selectedPackage.qualifiedName
        
        // 获取该 package 对应的目录（若有多个 source root 取第一个）
        val directories = selectedPackage.directories
        if (directories.isEmpty()) {
            setStatus("✗ 无法找到包 $pkgName 对应的目录", isError = true)
            return
        }
        val destPsiDir = directories[0]
        val destDir = destPsiDir.virtualFile
        
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val existingFile = destDir.findChild("$className.java")
                val targetFile = if (existingFile != null) {
                    val result = Messages.showYesNoDialog(
                        project,
                        "文件 $className.java 已存在于 $pkgName，是否覆盖？",
                        "File Exists",
                        Messages.getWarningIcon()
                    )
                    if (result == Messages.YES) existingFile else null
                } else {
                    destDir.createChildData(this, "$className.java")
                }

                targetFile?.let {
                    // 自动补 package 声明
                    val finalContent = if (pkgName.isNotEmpty() && !modifiedJavaContent.contains("package ")) {
                        "package $pkgName;\n\n$modifiedJavaContent"
                    } else {
                        modifiedJavaContent
                    }
                    it.setBinaryContent(finalContent.toByteArray(Charsets.UTF_8))
                    
                    ApplicationManager.getApplication().invokeLater {
                        com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(it, true)
                        close(OK_EXIT_CODE)
                    }
                }
            } catch (e: Exception) {
                logger.error("[生成文件] 写入出错: ${e.message}", e)
                ApplicationManager.getApplication().invokeLater {
                    setStatus("✗ 写入文件出错: ${e.message}", isError = true)
                }
            }
        }
    }

    private fun doAiTranslation(btn: JButton) {
        logger.info("[AI转换] 开始, jsonContent长度=${jsonContent.length}")
        btn.isEnabled = false
        btn.text = "AI 转换中..."
        setStatus("⏳ 正在调用 AI 翻译...", autoClear = false)
        
        ApplicationManager.getApplication().executeOnPooledThread {
            kotlinx.coroutines.runBlocking {
                try {
                    val provider = com.deeptek.ai.idea.llm.LlmProviderFactory.createDefault()
                    logger.info("[AI转换] Provider: ${provider.name}, Model: ${provider.modelName}")
                    
                    val prompt = """
                        你是一个纯粹且极度精准的 JSON 转换工具。
                        下面附有一个 JSON，该 JSON 的键(Key)全部是中文或带中文的词句。
                        请你把所有的 JSON 键(Key)都翻译成标准的、全称的【驼峰命名法】(camelCase) 的英文。
                        如果 JSON 的值(Value)是一些随便填写的 mock_string、demo_string，请一并替换为符合翻译后字段含义的真实的、像模像样的英文 Mock 数据。但数值类型、由于是样例的结构，不能丢失。
                        【严格规定】：绝对不可以改变 JSON 自身的任何层级嵌套和字典内部的先后顺序！结构必须1:1完美映射。绝对不要输出任何 markdown 标记！！！只且只能输出一个纯粹的 JSON 字符串。
                        原始JSON：
                        $jsonContent
                    """.trimIndent()
                    
                    val response = provider.chatCompletion(
                        listOf(
                            com.deeptek.ai.idea.llm.ChatMessage.system("你是一个专业的代码和数据处理引擎，只负责按格式要求返回内容。"),
                            com.deeptek.ai.idea.llm.ChatMessage.user(prompt)
                        )
                    )
                    
                    var englishJson = response.content ?: ""
                    englishJson = englishJson.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                    logger.info("[AI转换] API 成功, 完整结果: $englishJson")

                    // ★ 使用 SwingUtilities.invokeLater 确保在 EDT 上更新（绕过 IntelliJ 的 invokeLater 可能的问题）
                    javax.swing.SwingUtilities.invokeLater {
                        try {
                            logger.info("[AI转换] invokeLater 开始执行, 线程=${Thread.currentThread().name}")
                            // 对话框已关闭时直接跳过所有 UI 操作
                            if (isDisposed || jsonEnglishEditor.isDisposed) {
                                logger.info("[AI转换] 对话框或编辑器已 disposed，跳过 UI 更新")
                                return@invokeLater
                            }
                            safeSetEditorText(jsonEnglishEditor, englishJson)
                            
                            if (englishJson.isEmpty()) {
                                setStatus("⚠ AI 返回了空内容，请检查模型配置或重试", isError = true)
                            } else {
                                setStatus("✓ AI 翻译转换成功")
                            }
                            logger.info("[AI转换] invokeLater 回调全部完成")
                        } catch (ex: Throwable) {
                            logger.error("[AI转换] invokeLater 回调异常: ${ex.javaClass.simpleName}: ${ex.message}", ex)
                            setStatus("✗ UI 更新异常: ${ex.message}", isError = true)
                        } finally {
                            btn.text = "AI 转换"
                            btn.isEnabled = true
                        }
                    }
                } catch (e: Exception) {
                    logger.error("[AI转换] 异常: ${e.javaClass.simpleName}: ${e.message}", e)
                    javax.swing.SwingUtilities.invokeLater {
                        try {
                            if (isDisposed) return@invokeLater
                            btn.text = "AI 转换"
                            btn.isEnabled = true
                            setStatus("✗ AI 转换错误: ${e.message ?: "未知错误"}", isError = true)
                        } catch (ex: Throwable) {
                            logger.error("[AI转换] 错误恢复回调也异常: ${ex.message}", ex)
                        }
                    }
                }
            }
        }
    }

    private fun doRegenerateBean(btn: JButton) {
        val englishJson = jsonEnglishEditor.document.text
        if (englishJson.isBlank() || !englishJson.trim().startsWith("{") && !englishJson.trim().startsWith("[")) {
            setStatus("✗ 中间栏必须包含合法的 JSON 数据", isError = true)
            return
        }
        
        try {
            val newJavaCode = com.deeptek.ai.idea.ui.json2bean.service.JsonToBeanService.generateJavaBeanFromMixed(className, englishJson, rootNode)
            safeSetEditorText(javaEditor, newJavaCode)
            setStatus("✓ 合并生成完毕")
        } catch (e: Exception) {
            logger.error("[生成Bean] 异常: ${e.message}", e)
            setStatus("✗ 生成代码发生错误: ${e.message}", isError = true)
        }
    }

    override fun dispose() {
        if (this::jsonEditor.isInitialized && !jsonEditor.isDisposed) {
            EditorFactory.getInstance().releaseEditor(jsonEditor)
        }
        if (this::jsonEnglishEditor.isInitialized && !jsonEnglishEditor.isDisposed) {
            EditorFactory.getInstance().releaseEditor(jsonEnglishEditor)
        }
        if (this::javaEditor.isInitialized && !javaEditor.isDisposed) {
            EditorFactory.getInstance().releaseEditor(javaEditor)
        }
        super.dispose()
    }
}
