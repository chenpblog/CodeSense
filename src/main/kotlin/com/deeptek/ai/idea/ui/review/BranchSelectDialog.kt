package com.deeptek.ai.idea.ui.review

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

/**
 * 分支选择对话框
 *
 * 用户选择当前分支和对比的主干分支，确认后触发 diff 审查。
 */
class BranchSelectDialog(
    project: Project,
    private val branches: List<String>,
    private val defaultCurrentBranch: String,
    private val defaultMainBranch: String
) : DialogWrapper(project, true) {

    private val currentBranchCombo = ComboBox<String>()
    private val mainBranchCombo = ComboBox<String>()

    var selectedCurrentBranch: String = defaultCurrentBranch
        private set
    var selectedMainBranch: String = defaultMainBranch
        private set

    init {
        title = "审查当前分支与主干"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.preferredSize = Dimension(400, 140)
        panel.border = JBUI.Borders.empty(10, 15)

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(5, 5)
        }

        // 当前分支标签
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        panel.add(JLabel("当前分支:"), gbc)

        // 当前分支下拉框
        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0
        branches.forEach { currentBranchCombo.addItem(it) }
        currentBranchCombo.selectedItem = defaultCurrentBranch
        panel.add(currentBranchCombo, gbc)

        // 主干分支标签
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
        panel.add(JLabel("主干分支:"), gbc)

        // 主干分支下拉框
        gbc.gridx = 1; gbc.gridy = 1; gbc.weightx = 1.0
        branches.forEach { mainBranchCombo.addItem(it) }
        mainBranchCombo.selectedItem = defaultMainBranch
        panel.add(mainBranchCombo, gbc)

        // 提示信息
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2; gbc.weightx = 1.0
        val hintLabel = JLabel("<html><i>将对比「主干分支」→「当前分支」的全部差异</i></html>")
        hintLabel.foreground = java.awt.Color.GRAY
        panel.add(hintLabel, gbc)

        return panel
    }

    override fun doOKAction() {
        selectedCurrentBranch = currentBranchCombo.selectedItem as? String ?: defaultCurrentBranch
        selectedMainBranch = mainBranchCombo.selectedItem as? String ?: defaultMainBranch
        super.doOKAction()
    }
}
