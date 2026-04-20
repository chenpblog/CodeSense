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

                        【任务】将下方 JSON 的所有中文 Key 翻译为英文驼峰命名(camelCase)，同时合理 Mock 英文 Value。

                        【严格约束 - 违反任何一条都是失败】
                        1. 层级结构：与原 JSON 完全一致，不可新增、删除、合并或拆分任何层级
                        2. 键的顺序：每一层的键(Key)顺序必须与原 JSON 完全一致
                        3. 数组元素：数组中的每个元素都必须保留，元素个数不可改变
                        4. 值类型：数字保留为数字，字符串保留为字符串，布尔保留为布尔
                        5. Mock 规则：如果 Value 是类似 "demo_string"、"mock_string" 等占位文本，替换为符合字段含义的真实英文样例数据。数字型的值（如 0.0）保留原值
                        6. 输出要求：只输出纯 JSON，不要 markdown 标记（无 ```），不要任何解释文字，必须是带缩进的格式化 JSON

                        【原始 JSON】
                        $jsonContent
                    """.trimIndent()
                    
                    val response = provider.chatCompletion(
                        listOf(
                            com.deeptek.ai.idea.llm.ChatMessage.system("你是一个 JSON 格式转换引擎。只能输出纯 JSON，不能输出任何其他内容。"),
                            com.deeptek.ai.idea.llm.ChatMessage.user(prompt)
                        )
                    )
                    
                    var rawResult = response.content ?: ""
                    logger.info("[AI转换] API 原始结果长度=${rawResult.length}, 前200字: ${rawResult.take(200)}")

                    // ★ 智能提取 JSON：处理 markdown 包裹、多余文字等脏数据
                    val englishJson = extractAndFormatJson(rawResult)
                    logger.info("[AI转换] 格式化后长度=${englishJson.length}, 前200字: ${englishJson.take(200)}")

                    // ★ 结构校验：检查翻译后 JSON 的顶层键数量是否与原 JSON 一致
                    val structureWarning = validateJsonStructure(jsonContent, englishJson)

                    javax.swing.SwingUtilities.invokeLater {
                        try {
                            logger.info("[AI转换] invokeLater 开始执行, 线程=${Thread.currentThread().name}")
                            if (isDisposed || jsonEnglishEditor.isDisposed) {
                                logger.info("[AI转换] 对话框或编辑器已 disposed，跳过 UI 更新")
                                return@invokeLater
                            }
                            safeSetEditorText(jsonEnglishEditor, englishJson)
                            
                            if (englishJson.isEmpty()) {
                                setStatus("⚠ AI 返回了空内容，请检查模型配置或重试", isError = true)
                            } else if (structureWarning != null) {
                                setStatus("⚠ 翻译完成，但结构可能有差异: $structureWarning", isError = true, autoClear = false)
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

    /**
     * 从 AI 返回的原始文本中智能提取 JSON 并格式化
     *
     * 处理场景：
     * 1. 纯 JSON
     * 2. ```json ... ``` 包裹的 JSON
     * 3. JSON 前后有多余解释文字
     * 4. 多层 ``` 嵌套
     */
    private fun extractAndFormatJson(raw: String): String {
        var text = raw.trim()
        
        // 1. 去除 markdown 代码块标记（支持 ```json, ```, 多次嵌套）
        val codeBlockPattern = Regex("```(?:json)?\\s*\\n?(.*?)\\n?\\s*```", RegexOption.DOT_MATCHES_ALL)
        val codeBlockMatch = codeBlockPattern.find(text)
        if (codeBlockMatch != null) {
            text = codeBlockMatch.groupValues[1].trim()
        }
        
        // 2. 如果还有残留的 ``` 标记，逐个清理
        text = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        
        // 3. 尝试从文本中提取 JSON 部分（找到第一个 { 或 [ 到最后一个 } 或 ]）
        if (!text.startsWith("{") && !text.startsWith("[")) {
            val jsonStart = minOfNullable(text.indexOf('{'), text.indexOf('['))
            if (jsonStart != null && jsonStart >= 0) {
                text = text.substring(jsonStart)
            }
        }
        if (!text.endsWith("}") && !text.endsWith("]")) {
            val jsonEndBrace = text.lastIndexOf('}')
            val jsonEndBracket = text.lastIndexOf(']')
            val jsonEnd = maxOf(jsonEndBrace, jsonEndBracket)
            if (jsonEnd > 0) {
                text = text.substring(0, jsonEnd + 1)
            }
        }
        
        // 4. 使用 Gson 解析并 pretty-print 格式化
        return try {
            val gson = com.google.gson.GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create()
            val element = com.google.gson.JsonParser.parseString(text)
            gson.toJson(element)
        } catch (e: Exception) {
            logger.warn("[AI转换] JSON 格式化失败，返回原始文本: ${e.message}")
            text  // 格式化失败时返回提取后的原文
        }
    }
    
    /** 辅助函数：两个可能为 -1 的索引取最小正值 */
    private fun minOfNullable(a: Int, b: Int): Int? {
        val validA = if (a >= 0) a else null
        val validB = if (b >= 0) b else null
        return when {
            validA != null && validB != null -> minOf(validA, validB)
            validA != null -> validA
            validB != null -> validB
            else -> null
        }
    }

    /**
     * 校验翻译后 JSON 的结构是否与原 JSON 一致
     * 
     * 返回 null 表示结构一致，否则返回警告信息
     */
    private fun validateJsonStructure(originalJson: String, translatedJson: String): String? {
        try {
            val original = com.google.gson.JsonParser.parseString(originalJson)
            val translated = com.google.gson.JsonParser.parseString(translatedJson)
            
            return compareStructure(original, translated, "$")
        } catch (e: Exception) {
            return "无法校验结构: ${e.message}"
        }
    }
    
    private fun compareStructure(original: com.google.gson.JsonElement, translated: com.google.gson.JsonElement, path: String): String? {
        return when {
            original.isJsonObject && translated.isJsonObject -> {
                val origKeys = original.asJsonObject.keySet().toList()
                val transKeys = translated.asJsonObject.keySet().toList()
                if (origKeys.size != transKeys.size) {
                    return "路径 $path: 键数量不匹配 (原始=${origKeys.size}, 翻译后=${transKeys.size})"
                }
                // 按位置递归检查子元素结构
                for (i in origKeys.indices) {
                    val origChild = original.asJsonObject.get(origKeys[i])
                    val transChild = translated.asJsonObject.get(transKeys[i])
                    if (transChild == null) {
                        return "路径 $path: 翻译后缺少第${i+1}个键对应的值"
                    }
                    val childWarning = compareStructure(origChild, transChild, "$path.${transKeys[i]}")
                    if (childWarning != null) return childWarning
                }
                null
            }
            original.isJsonArray && translated.isJsonArray -> {
                val origArr = original.asJsonArray
                val transArr = translated.asJsonArray
                if (origArr.size() != transArr.size()) {
                    return "路径 $path: 数组元素数量不匹配 (原始=${origArr.size()}, 翻译后=${transArr.size()})"
                }
                for (i in 0 until origArr.size()) {
                    val childWarning = compareStructure(origArr[i], transArr[i], "$path[$i]")
                    if (childWarning != null) return childWarning
                }
                null
            }
            original.isJsonPrimitive && translated.isJsonPrimitive -> null  // 基本类型不检查
            original.isJsonNull && translated.isJsonNull -> null
            else -> {
                "路径 $path: 类型不匹配 (原始=${original.javaClass.simpleName}, 翻译后=${translated.javaClass.simpleName})"
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
