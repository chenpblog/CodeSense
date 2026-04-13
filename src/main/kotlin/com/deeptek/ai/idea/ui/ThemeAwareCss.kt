package com.deeptek.ai.idea.ui

import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import javax.swing.text.html.HTMLEditorKit
import javax.swing.text.html.StyleSheet

/**
 * 主题感知的 CSS 样式工具
 *
 * 根据当前 IDE 主题（Darcula 深色 / IntelliJ Light 浅色）
 * 动态生成适配的 CSS 样式，确保在深色和浅色模式下均有良好的可读性。
 */
object ThemeAwareCss {

    private val isDark: Boolean get() = !JBColor.isBright()

    // ====== 基础颜色 ======

    private val bgColor get() = if (isDark) "#2b2b2b" else "#ffffff"
    private val fgColor get() = if (isDark) "#bababa" else "#333333"
    private val mutedColor get() = if (isDark) "#787878" else "#999999"

    // 消息块
    private val userMsgBg get() = if (isDark) "#2d3748" else "#e3f2fd"
    private val aiMsgBg get() = if (isDark) "#1e1e1e" else "#f5f5f5"
    private val toolMsgBg get() = if (isDark) "#3d2e1e" else "#fff3e0"
    private val toolBorderColor get() = if (isDark) "#e69500" else "#ff9800"

    // 角色颜色
    private val userRoleColor get() = if (isDark) "#64b5f6" else "#1565c0"
    private val aiRoleColor get() = if (isDark) "#81c784" else "#2e7d32"
    private val toolRoleColor get() = if (isDark) "#ffb74d" else "#e65100"
    private val errorColor get() = if (isDark) "#ef5350" else "#c62828"

    // 代码块
    private val preBg get() = if (isDark) "#1a1a2e" else "#263238"
    private val preFg get() = if (isDark) "#c3cee3" else "#eeffff"
    private val codeBg get() = if (isDark) "#3c3f41" else "#e0e0e0"

    // 标题
    private val h1Color get() = if (isDark) "#7986cb" else "#1a237e"
    private val h2Color get() = if (isDark) "#9fa8da" else "#283593"
    private val h3Color get() = if (isDark) "#b0bec5" else "#303f9f"

    // 表格
    private val thBg get() = if (isDark) "#37474f" else "#e8eaf6"
    private val tdBorder get() = if (isDark) "#555555" else "#ddd"
    private val thBorder get() = if (isDark) "#666666" else "#ccc"

    // 分隔线
    private val hrColor get() = if (isDark) "#4a4a4a" else "#e0e0e0"

    // 审查面板
    private val reviewRoleColor get() = if (isDark) "#ef5350" else "#d32f2f"
    private val reviewRoleBorder get() = if (isDark) "#444444" else "#eee"

    // 风险
    private val riskHigh get() = if (isDark) "#ef5350" else "#c62828"
    private val riskMedium get() = if (isDark) "#ffa726" else "#ef6c00"
    private val riskLow get() = if (isDark) "#ffee58" else "#f9a825"

    /**
     * 为 ChatPanel 创建主题感知的 HTMLEditorKit
     */
    fun createChatEditorKit(): HTMLEditorKit {
        return HTMLEditorKit().apply {
            val ss = this.styleSheet
            ss.addRule("body { font-family: sans-serif; font-size: 13pt; padding: 10px; color: $fgColor; background: $bgColor; }")
            ss.addRule(".user-msg { background: $userMsgBg; padding: 8px; margin: 6px 0; }")
            ss.addRule(".ai-msg { background: $aiMsgBg; padding: 8px; margin: 6px 0; }")
            ss.addRule(".tool-msg { background: $toolMsgBg; padding: 6px; margin: 4px 0; border-width: 0 0 0 3; border-style: solid; border-color: $toolBorderColor; font-size: 11pt; }")
            ss.addRule(".role { font-weight: bold; color: $userRoleColor; margin-bottom: 4px; }")
            ss.addRule(".ai-role { font-weight: bold; color: $aiRoleColor; margin-bottom: 4px; }")
            ss.addRule(".tool-role { font-weight: bold; color: $toolRoleColor; margin-bottom: 4px; }")
            ss.addRule(".error { color: $errorColor; font-weight: bold; }")
            ss.addRule("pre { background: $preBg; color: $preFg; padding: 10px; }")
            ss.addRule("code { background: $codeBg; padding: 2px; font-family: monospace; }")
        }
    }

    /**
     * 为 ReviewResultPanel 创建主题感知的 HTMLEditorKit
     */
    fun createReviewEditorKit(): HTMLEditorKit {
        return HTMLEditorKit().apply {
            val ss = this.styleSheet
            ss.addRule("body { font-family: sans-serif; font-size: 13pt; padding: 15px; color: $fgColor; background: $bgColor; }")
            ss.addRule(".ai-msg { background: $aiMsgBg; margin: 6px 0; }")
            ss.addRule(".ai-role { font-weight: bold; font-size: 14pt; color: $reviewRoleColor; margin-bottom: 8px; border-bottom-width: 1; border-bottom-style: solid; border-bottom-color: $reviewRoleBorder; padding-bottom: 4px; }")
            ss.addRule("h1, h2, h3 { color: $h1Color; }")
            ss.addRule("pre { background: $preBg; color: $preFg; padding: 10px; }")
            ss.addRule("code { background: $codeBg; padding: 2px; font-family: monospace; }")
        }
    }

    /**
     * 为 ImpactResultPanel 创建主题感知的 HTMLEditorKit
     */
    fun createImpactEditorKit(): HTMLEditorKit {
        return HTMLEditorKit().apply {
            val ss = this.styleSheet
            ss.addRule("body { font-family: sans-serif; font-size: 13pt; padding: 15px; color: $fgColor; background: $bgColor; }")
            ss.addRule("h1 { color: $h1Color; border-bottom-width: 2; border-bottom-style: solid; border-bottom-color: $h1Color; padding-bottom: 8px; }")
            ss.addRule("h2 { color: $h2Color; border-bottom-width: 1; border-bottom-style: solid; border-bottom-color: $hrColor; padding-bottom: 4px; margin-top: 20px; }")
            ss.addRule("h3 { color: $h3Color; }")
            ss.addRule("table { width: 100%; margin: 10px 0; }")
            ss.addRule("th { background: $thBg; padding: 8px; border-width: 1; border-style: solid; border-color: $thBorder; text-align: left; color: $fgColor; }")
            ss.addRule("td { padding: 6px; border-width: 1; border-style: solid; border-color: $tdBorder; color: $fgColor; }")
            ss.addRule("pre { background: $preBg; color: $preFg; padding: 12px; font-family: monospace; font-size: 11pt; }")
            ss.addRule("code { background: $codeBg; padding: 2px; font-family: monospace; }")
            ss.addRule(".risk-high { color: $riskHigh; font-weight: bold; }")
            ss.addRule(".risk-medium { color: $riskMedium; font-weight: bold; }")
            ss.addRule(".risk-low { color: $riskLow; font-weight: bold; }")
        }
    }
}
