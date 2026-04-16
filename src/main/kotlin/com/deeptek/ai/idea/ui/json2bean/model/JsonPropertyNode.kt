package com.deeptek.ai.idea.ui.json2bean.model

import javax.swing.tree.DefaultMutableTreeNode

/**
 * 树形表格中的每一个 JSON 节点
 */
class JsonPropertyNode(
    var fieldName: String = "",
    var type: String = "String",
    var description: String = ""
) : DefaultMutableTreeNode() {

    init {
        // 设置自身为 UserObject
        userObject = this
    }
    
    // 拦截 JTree 的默认编辑回调。JTree 默认的 CellEditor 会传入一个 String。
    override fun setUserObject(userObject: Any?) {
        if (userObject is String) {
            this.fieldName = userObject
        } else {
            super.setUserObject(userObject)
        }
    }

    override fun toString(): String {
        return fieldName.takeIf { it.isNotEmpty() } ?: "未命名字段"
    }

    // 辅助方法：获取类型列表
    companion object {
        val SUPPORTED_TYPES = arrayOf(
            "String", 
            "Decimal", 
            "Boolean", 
            "Object", 
            "List<Object>"
        )
        
        fun createRoot(rootName: String): JsonPropertyNode {
            val root = JsonPropertyNode(rootName, "Object", "根节点")
            return root
        }
    }
}
