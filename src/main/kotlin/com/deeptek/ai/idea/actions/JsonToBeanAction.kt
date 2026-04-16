package com.deeptek.ai.idea.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class JsonToBeanAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 若从 Project View 触发，尝试获取选中的包或目录，传递给设计器以方便后续代码写入
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        
        // 打开 JsonDesignDialog
        val dialog = com.deeptek.ai.idea.ui.json2bean.JsonDesignDialog(project, virtualFile)
        dialog.show()
    }
}
