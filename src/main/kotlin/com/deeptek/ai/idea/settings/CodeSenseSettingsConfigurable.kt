package com.deeptek.ai.idea.settings

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

    // 模型列表表格
    private val tableModel = ProviderTableModel()
    private val providerTable = JBTable(tableModel).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        columnModel.getColumn(0).preferredWidth = 40   // ★
        columnModel.getColumn(1).preferredWidth = 150  // 名称
        columnModel.getColumn(2).preferredWidth = 150  // 模型
        columnModel.getColumn(3).preferredWidth = 80   // 状态
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
        // TODO: 调用 LlmClient 发送一个简单请求测试连接
        Messages.showInfoMessage(
            "连接测试功能将在 LLM 客户端实现后启用。\n" +
            "Base URL: ${selected.baseUrl}\n" +
            "Model: ${selected.modelName}",
            "测试连接"
        )
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
        private val columns = arrayOf("★", "名称", "模型", "状态")

        override fun getRowCount(): Int = settings.state.providers.size
        override fun getColumnCount(): Int = columns.size
        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val provider = settings.state.providers[rowIndex]
            return when (columnIndex) {
                0 -> if (provider.id == settings.state.defaultProviderId) "★" else ""
                1 -> provider.displayName
                2 -> provider.modelName
                3 -> if (provider.apiKey.isNotBlank()) "✔ 已配置" else "✖ 未配置"
                else -> ""
            }
        }
    }
}
