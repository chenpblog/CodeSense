package com.deeptek.ai.idea.settings

import com.deeptek.ai.idea.llm.ChatMessage
import com.deeptek.ai.idea.llm.LlmProviderFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Messages
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.table.JBTable
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

/**
 * CodeSense AI Settings 配置页面
 *
 * Settings → Tools → CodeSense AI
 * 提供模型管理（添加/编辑/删除/设为默认/测试连接）和功能开关设置。
 */
class CodeSenseSettingsConfigurable : BoundConfigurable("CodeSense AI") {

    private val settings = CodeSenseSettings.getInstance()

    // 测试连接状态：key=providerId, value=状态文字
    private val testStatus = mutableMapOf<String, String>()

    // 模型列表表格
    private val tableModel = ProviderTableModel()
    private val providerTable = JBTable(tableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        columnModel.getColumn(0).preferredWidth = 40   // ★
        columnModel.getColumn(1).preferredWidth = 140  // 名称
        columnModel.getColumn(2).preferredWidth = 130  // 模型
        columnModel.getColumn(3).preferredWidth = 100  // 协议
        columnModel.getColumn(4).preferredWidth = 80   // 状态
    }

    override fun createPanel(): DialogPanel = panel {
        group("模型配置") {
            row {
                comment("在此配置 LLM 模型提供者，带 ★ 标记的为默认使用的模型。")
            }
            row {
                scrollCell(providerTable)
                    .align(AlignX.FILL)
                    .resizableColumn()
                    .comment("所有分析和审查操作将自动使用带 ★ 的默认模型")
            }.resizableRow()
            row {
                button("添加") { onAddProvider() }
                button("编辑") { onEditProvider() }
                button("删除") { onRemoveProvider() }
                button("设为默认") { onSetDefault() }
                button("测试连接") { onTestConnection() }
            }
        }

        group("功能设置") {
            row {
                checkBox("启用代码审查")
                    .bindSelected(settings.state::enableCodeReview)
            }
            row {
                checkBox("启用影响分析")
                    .bindSelected(settings.state::enableImpactAnalysis)
            }
            row {
                checkBox("分析时使用 AI 生成风险评估")
                    .bindSelected(settings.state::enableAiRiskAssessment)
            }
            row("审查最大文件数:") {
                spinner(1..100, 1)
                    .bindIntValue(settings.state::maxReviewFiles)
            }
            row("调用链最大追溯深度:") {
                spinner(1..30, 1)
                    .bindIntValue(settings.state::maxCallHierarchyDepth)
            }
        }
    }

    // ====== 按钮事件 ======

    private fun onAddProvider() {
        val dialog = ProviderConfigDialog(null)
        if (dialog.showAndGet()) {
            val config = dialog.getProviderConfig()
            settings.addProvider(config)
            tableModel.fireTableDataChanged()
        }
    }

    private fun onEditProvider() {
        val selected = getSelectedProvider() ?: return
        val dialog = ProviderConfigDialog(selected)
        if (dialog.showAndGet()) {
            val updated = dialog.getProviderConfig()
            // 更新 settings 中的对应配置
            val index = settings.state.providers.indexOfFirst { it.id == selected.id }
            if (index >= 0) {
                settings.state.providers[index] = updated
                // 清除旧的测试状态
                testStatus.remove(selected.id)
                tableModel.fireTableDataChanged()
            }
        }
    }

    private fun onRemoveProvider() {
        val selected = getSelectedProvider() ?: return
        val result = Messages.showYesNoDialog(
            "确定要删除模型「${selected.displayName}」吗？",
            "删除模型",
            Messages.getQuestionIcon()
        )
        if (result == Messages.YES) {
            testStatus.remove(selected.id)
            settings.removeProvider(selected.id)
            tableModel.fireTableDataChanged()
        }
    }

    private fun onSetDefault() {
        val selected = getSelectedProvider() ?: return
        settings.setDefaultProvider(selected.id)
        tableModel.fireTableDataChanged()
    }

    private fun onTestConnection() {
        val selected = getSelectedProvider() ?: return
        if (selected.apiKey.isBlank()) {
            Messages.showWarningDialog(
                "请先填写 API Key",
                "测试连接"
            )
            return
        }

        // 更新表格状态为"测试中..."
        testStatus[selected.id] = "⏳ 测试中..."
        tableModel.fireTableDataChanged()

        ApplicationManager.getApplication().executeOnPooledThread {
            val startTime = System.currentTimeMillis()
            try {
                val provider = LlmProviderFactory.create(selected)
                val response = kotlinx.coroutines.runBlocking {
                    provider.chatCompletion(
                        listOf(ChatMessage.user("hi"))
                    )
                }
                val elapsed = System.currentTimeMillis() - startTime
                val content = response.content?.take(200) ?: "(空响应)"

                javax.swing.SwingUtilities.invokeLater {
                    testStatus[selected.id] = "✅ ${elapsed}ms"
                    tableModel.fireTableDataChanged()
                    Messages.showInfoMessage(
                        "✅ 连接成功！\n\n" +
                        "模型: ${selected.displayName} (${selected.modelName})\n" +
                        "协议: ${selected.apiProtocol}\n" +
                        "耗时: ${elapsed}ms\n\n" +
                        "模型回复:\n$content",
                        "测试连接 - 成功"
                    )
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                val errMsg = e.message?.take(300) ?: "未知错误"

                javax.swing.SwingUtilities.invokeLater {
                    testStatus[selected.id] = "❌ 失败"
                    tableModel.fireTableDataChanged()
                    Messages.showErrorDialog(
                        "❌ 连接失败\n\n" +
                        "模型: ${selected.displayName} (${selected.modelName})\n" +
                        "协议: ${selected.apiProtocol}\n" +
                        "耗时: ${elapsed}ms\n\n" +
                        "错误信息:\n$errMsg",
                        "测试连接 - 失败"
                    )
                }
            }
        }
    }

    private fun getSelectedProvider(): ProviderConfig? {
        val row = providerTable.selectedRow
        if (row < 0) {
            Messages.showWarningDialog("请先在列表中选择一个模型", "提示")
            return null
        }
        return settings.state.providers.getOrNull(row)
    }

    // ====== 表格模型 ======

    private inner class ProviderTableModel : AbstractTableModel() {
        private val columns = arrayOf("★", "名称", "模型", "协议", "状态")

        override fun getRowCount(): Int = settings.state.providers.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val provider = settings.state.providers[rowIndex]
            return when (columnIndex) {
                0 -> if (provider.id == settings.state.defaultProviderId) "★" else ""
                1 -> provider.displayName
                2 -> provider.modelName
                3 -> when (provider.apiProtocol) {
                    ApiProtocol.ANTHROPIC_COMPATIBLE -> "Anthropic"
                    ApiProtocol.OPENAI_COMPATIBLE -> "OpenAI"
                }
                4 -> {
                    // 优先显示测试结果状态，否则显示配置状态
                    testStatus[provider.id] ?: if (provider.apiKey.isNotBlank()) "✔ 已配置" else "✖ 未配置"
                }
                else -> ""
            }
        }
    }
}
