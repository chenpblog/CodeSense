package com.deeptek.ai.idea.ui.json2bean

import com.deeptek.ai.idea.ui.json2bean.model.JsonPropertyNode
import com.deeptek.ai.idea.ui.json2bean.util.JsonDemoGenerator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns
import com.intellij.ui.treeStructure.treetable.TreeTable
import com.intellij.ui.treeStructure.treetable.TreeTableModel
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JComboBox
import javax.swing.table.TableCellEditor
import javax.swing.AbstractCellEditor
import java.awt.Component
import javax.swing.JTable
import javax.swing.DefaultCellEditor
import javax.swing.JTextField

class JsonDesignDialog(
    private val project: Project,
    private val targetDirectory: VirtualFile?
) : DialogWrapper(project) {

    private lateinit var treeTable: TreeTable
    private lateinit var rootNode: JsonPropertyNode
    private lateinit var rootTypeComboBox: ComboBox<String>
    private lateinit var classNameField: JBTextField

    companion object {
        var savedRootNode: JsonPropertyNode? = null
        var savedClassName: String = "RootRes"
        var savedRootTypeIndex: Int = 0
    }

    init {
        title = "CodeSense AI: JSON to Java Bean 设计器"
        init()
        setOKButtonText("Generate Demo & Java Bean")
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(JBUI.scale(10), JBUI.scale(10)))

        // 1. 初始化数据与 TreeTable
        if (savedRootNode == null) {
            savedRootNode = JsonPropertyNode.createRoot("Root")
        }
        rootNode = savedRootNode!!
        
        val columns = arrayOf<ColumnInfo<*, *>>(
            TreeColumnInfo("字段名 (Field Name)"),
            TypeColumnInfo("类型 (Type)"),
            DescColumnInfo("描述/说明 (Description)")
        )

        val model = ListTreeTableModelOnColumns(rootNode, columns)
        treeTable = TreeTable(model)
        treeTable.setRootVisible(true)
        treeTable.rowHeight = JBUI.scale(26)
        treeTable.tree.isEditable = true // 支持双击编辑首列（树节点本身）
        
        // 分配列宽
        treeTable.columnModel.getColumn(0).preferredWidth = 200
        treeTable.columnModel.getColumn(1).preferredWidth = 150
        treeTable.columnModel.getColumn(2).preferredWidth = 250

        // 强行指定列的 CellEditor 以修复 TreeTable 默认行为屏蔽的问题
        // 1. 类型列强制使用下拉框
        treeTable.columnModel.getColumn(1).cellEditor = javax.swing.DefaultCellEditor(com.intellij.openapi.ui.ComboBox(JsonPropertyNode.SUPPORTED_TYPES))
        
        // 2. 字段名列强制使用文本输入框（覆盖整个单元格避免被 Tree 的展开图标干涉）
        treeTable.columnModel.getColumn(0).cellEditor = object : javax.swing.DefaultCellEditor(JBTextField()) {
            override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
                val node = treeTable.tree.getPathForRow(row)?.lastPathComponent as? JsonPropertyNode
                val comp = super.getTableCellEditorComponent(table, node?.fieldName ?: "", isSelected, row, column) as javax.swing.JTextField
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    comp.selectAll()
                }
                return comp
            }
        }

        // 包含到 ScrollPane
        val scrollPane = JBScrollPane(treeTable)
        scrollPane.preferredSize = Dimension(650, 400)
        
        // 顶部工具栏 (Add/Remove 节点)
        val toolbarPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        val addBtn = com.intellij.ui.components.JBLabel("<html><a href=''>+ 添加同级</a></html>")
        val addChildBtn = com.intellij.ui.components.JBLabel("<html><a href=''>++ 添加子级</a></html>")
        val removeBtn = com.intellij.ui.components.JBLabel("<html><a href=''>- 删除节点</a></html>")
        val newJsonBtn = com.intellij.ui.components.JBLabel("<html><a href=''>✗ 新建 JSON</a></html>")
        
        // 点击事件：添加同级
        addBtn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR))
        addBtn.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                val path = treeTable.tree.selectionPath
                if (path == null) {
                    com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder("请先选中一个节点", com.intellij.openapi.ui.MessageType.WARNING, null)
                        .setFadeoutTime(2000)
                        .createBalloon()
                        .show(com.intellij.ui.awt.RelativePoint.getCenterOf(addBtn), com.intellij.openapi.ui.popup.Balloon.Position.below)
                    return
                }
                val selectedNode = path.lastPathComponent as JsonPropertyNode
                if (selectedNode == rootNode) {
                    com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder("不能为 Root 节点添加同级", com.intellij.openapi.ui.MessageType.WARNING, null)
                        .setFadeoutTime(2000)
                        .createBalloon()
                        .show(com.intellij.ui.awt.RelativePoint.getCenterOf(addBtn), com.intellij.openapi.ui.popup.Balloon.Position.below)
                    return
                }
                val parent = selectedNode.parent as JsonPropertyNode
                val newNode = JsonPropertyNode("new_field", "String", "")
                parent.add(newNode)
                
                val newPath = path.parentPath.pathByAddingChild(newNode)
                treeTable.tree.updateUI()
                treeTable.updateUI()
                
                treeTable.tree.scrollPathToVisible(newPath)
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    val row = treeTable.tree.getRowForPath(newPath)
                    if (row != -1) {
                        treeTable.selectionModel.setSelectionInterval(row, row)
                        treeTable.editCellAt(row, 0)
                        val comp = treeTable.editorComponent
                        if (comp is javax.swing.JTextField) {
                            comp.selectAll()
                        }
                        comp?.requestFocusInWindow()
                    }
                }
            }
        })
        
        // 点击事件：添加子级
        addChildBtn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR))
        addChildBtn.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                val path = treeTable.tree.selectionPath
                if (path == null) {
                    com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder("请先选中一个节点", com.intellij.openapi.ui.MessageType.WARNING, null)
                        .setFadeoutTime(2000)
                        .createBalloon()
                        .show(com.intellij.ui.awt.RelativePoint.getCenterOf(addChildBtn), com.intellij.openapi.ui.popup.Balloon.Position.below)
                    return
                }
                val selectedNode = path.lastPathComponent as JsonPropertyNode
                if (selectedNode.type == "Object" || selectedNode.type == "List<Object>") {
                    val newNode = JsonPropertyNode("new_child_field", "String", "")
                    selectedNode.add(newNode)
                    val newPath = path.pathByAddingChild(newNode)
                    
                    treeTable.tree.updateUI()
                    treeTable.updateUI()
                    treeTable.tree.expandPath(path)
                    
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        val row = treeTable.tree.getRowForPath(newPath)
                        if (row != -1) {
                            treeTable.selectionModel.setSelectionInterval(row, row)
                            treeTable.editCellAt(row, 0)
                            val comp = treeTable.editorComponent
                            if (comp is javax.swing.JTextField) {
                                comp.selectAll()
                            }
                            comp?.requestFocusInWindow()
                        }
                    }
                } else {
                    com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder("只有 Object 或 List&lt;Object&gt; 可以添加子级", com.intellij.openapi.ui.MessageType.WARNING, null)
                        .setFadeoutTime(2000)
                        .createBalloon()
                        .show(com.intellij.ui.awt.RelativePoint.getCenterOf(addChildBtn), com.intellij.openapi.ui.popup.Balloon.Position.below)
                }
            }
        })

        // 点击事件：删除节点
        removeBtn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR))
        removeBtn.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                val path = treeTable.tree.selectionPath
                if (path == null) {
                    com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder("请先选中一个节点", com.intellij.openapi.ui.MessageType.WARNING, null)
                        .setFadeoutTime(2000)
                        .createBalloon()
                        .show(com.intellij.ui.awt.RelativePoint.getCenterOf(removeBtn), com.intellij.openapi.ui.popup.Balloon.Position.below)
                    return
                }
                val selectedNode = path.lastPathComponent as JsonPropertyNode
                if (selectedNode == rootNode) {
                    com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
                        .createHtmlTextBalloonBuilder("不能删除 Root 节点", com.intellij.openapi.ui.MessageType.WARNING, null)
                        .setFadeoutTime(2000)
                        .createBalloon()
                        .show(com.intellij.ui.awt.RelativePoint.getCenterOf(removeBtn), com.intellij.openapi.ui.popup.Balloon.Position.below)
                    return
                }
                selectedNode.removeFromParent()
                treeTable.tree.updateUI()
                treeTable.updateUI()
            }
        })
        
        // 点击事件：新建 JSON
        newJsonBtn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR))
        newJsonBtn.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                val result = com.intellij.openapi.ui.Messages.showYesNoDialog(
                    project, 
                    "确认要清空当前所有设计结构并新建 JSON 吗？", 
                    "新建 JSON", 
                    com.intellij.openapi.ui.Messages.getQuestionIcon()
                )
                if (result == com.intellij.openapi.ui.Messages.YES) {
                    rootNode.removeAllChildren()
                    rootNode.fieldName = "Root"
                    rootNode.type = "Object"
                    rootNode.description = "根节点"
                    classNameField.text = "RootRes"
                    rootTypeComboBox.selectedIndex = 0
                    savedClassName = "RootRes"
                    savedRootTypeIndex = 0
                    
                    treeTable.tree.updateUI()
                    treeTable.updateUI()
                }
            }
        })
        
        toolbarPanel.add(addBtn)
        toolbarPanel.add(JBLabel("  |  "))
        toolbarPanel.add(addChildBtn)
        toolbarPanel.add(JBLabel("  |  "))
        toolbarPanel.add(removeBtn)
        toolbarPanel.add(JBLabel("  |  "))
        toolbarPanel.add(newJsonBtn)

        val topPanel = JPanel(BorderLayout())
        topPanel.add(toolbarPanel, BorderLayout.WEST)

        mainPanel.add(topPanel, BorderLayout.NORTH)
        mainPanel.add(scrollPane, BorderLayout.CENTER)

        // 底部设置区域
        val bottomPanel = JPanel(FlowLayout(FlowLayout.LEFT))
        bottomPanel.add(JBLabel("Root Element Type:"))
        rootTypeComboBox = ComboBox(arrayOf("Object ({...})", "List ([{...}])"))
        rootTypeComboBox.selectedIndex = savedRootTypeIndex
        rootTypeComboBox.addActionListener {
            val isList = rootTypeComboBox.selectedIndex == 1
            rootNode.type = if (isList) "List<Object>" else "Object"
            treeTable.tree.updateUI()
            treeTable.updateUI()
        }
        bottomPanel.add(rootTypeComboBox)
        
        bottomPanel.add(JBLabel("    Class Name:"))
        classNameField = JBTextField(savedClassName, 15)
        bottomPanel.add(classNameField)
        
        mainPanel.add(bottomPanel, BorderLayout.SOUTH)

        return mainPanel
    }

    override fun doOKAction() {
        // 关闭编辑器状态避免丢失数据
        if (treeTable.isEditing) {
            treeTable.cellEditor.stopCellEditing()
        }

        val className = classNameField.text.trim().takeIf { it.isNotEmpty() } ?: "RootRes"
        val isList = rootTypeComboBox.selectedIndex == 1
        
        // 记录状态到内存中
        savedClassName = className
        savedRootTypeIndex = rootTypeComboBox.selectedIndex
        
        // 组装 JSON
        val demoJson = JsonDemoGenerator.generateDemoJson(rootNode, isRootList = isList)
        
        // 启动生成流程并关窗
        super.doOKAction()
        
        // 唤起 JsonToBeanService 并开启下一级 PreviewDialog
        com.deeptek.ai.idea.ui.json2bean.service.JsonToBeanService.generateJavaBeanAndPreview(project, className, demoJson, rootNode, targetDirectory)
    }

    // --- 列定义扩展 ---
    
    class TreeColumnInfo(name: String) : ColumnInfo<Any, String>(name) {
        override fun valueOf(item: Any?): String? = (item as? JsonPropertyNode)?.fieldName
        override fun getColumnClass(): Class<*> = TreeTableModel::class.java
        override fun isCellEditable(item: Any?): Boolean = item is JsonPropertyNode && item.parent != null
        override fun setValue(item: Any?, value: String?) {
            if (item is JsonPropertyNode && value != null) {
                item.fieldName = value
            }
        }
    }

    class TypeColumnInfo(name: String) : ColumnInfo<Any, String>(name) {
        override fun valueOf(item: Any?): String? = (item as? JsonPropertyNode)?.type
        override fun isCellEditable(item: Any?): Boolean = item is JsonPropertyNode && item.parent != null
        override fun setValue(item: Any?, value: String?) {
            if (item is JsonPropertyNode && value != null) {
                item.type = value
            }
        }
        override fun getEditor(item: Any?): TableCellEditor {
            val combo = JComboBox(JsonPropertyNode.SUPPORTED_TYPES)
            return DefaultCellEditor(combo)
        }
    }

    class DescColumnInfo(name: String) : ColumnInfo<Any, String>(name) {
        override fun valueOf(item: Any?): String? = (item as? JsonPropertyNode)?.description
        override fun isCellEditable(item: Any?): Boolean = item is JsonPropertyNode
        override fun setValue(item: Any?, value: String?) {
            if (item is JsonPropertyNode && value != null) {
                item.description = value
            }
        }
    }
}
