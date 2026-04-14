package com.deeptek.ai.idea.analysis

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Markdown 报告生成器
 *
 * 按照设计文档规范（demo-report-git-diff.md / demo-report-single-method.md）
 * 将 ImpactReport 渲染为结构化 Markdown 文本。
 */
object ReportGenerator {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * 生成模式 A 报告 — Git Diff 批量分析
     */
    fun generateGitDiffReport(report: ImpactReport): String {
        val sb = StringBuilder()
        val now = LocalDateTime.now().format(dateFormatter)

        // 头部元信息
        sb.appendLine("# 📊 影响范围分析报告")
        sb.appendLine()
        sb.appendLine("| 项目 | 值 |")
        sb.appendLine("|------|---|")
        sb.appendLine("| **分析模式** | ${report.mode.displayName} |")
        report.metadata["sourceBranch"]?.let { sb.appendLine("| **当前分支** | `$it` |") }
        report.metadata["targetBranch"]?.let { sb.appendLine("| **目标分支** | `$it` |") }
        sb.appendLine("| **分析时间** | $now |")
        sb.appendLine("| **变更方法数** | ${report.modifiedMethods.size} |")
        sb.appendLine("| **受影响入口点** | ${report.entryPoints.size} |")
        sb.appendLine("| **风险等级** | ${inferRiskLevel(report)} |")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        // 一、变更概要
        sb.appendLine("## 一、变更概要")
        sb.appendLine()
        sb.appendLine("| # | 文件 | 方法签名 | 变更类型 | 行号 |")
        sb.appendLine("|---|------|---------|---------|------|")
        report.modifiedMethods.forEachIndexed { index, method ->
            val fileName = method.filePath.substringAfterLast('/')
            val changeType = method.changeType ?: "MODIFIED"
            sb.appendLine("| ${index + 1} | `$fileName` | `${method.signature}` | $changeType | L${method.lineNumber} |")
        }
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        // 二、调用链分析
        sb.appendLine("## 二、调用链分析")
        sb.appendLine()
        report.callChains.entries.forEachIndexed { index, (method, biTree) ->
            sb.appendLine("### 2.${index + 1} ${method.displayName}(${method.signature.substringAfter('(')})")
            sb.appendLine()

            // 向上调用链
            sb.appendLine("**向上调用链（谁调用了我）：**")
            sb.appendLine()
            sb.appendLine("```")
            renderCallerTree(sb, biTree.callerTree, method, 0)
            sb.appendLine("```")
            sb.appendLine()

            // 向下调用链
            sb.appendLine("**向下调用链（我调用了谁）：**")
            sb.appendLine()
            sb.appendLine("```")
            renderCalleeTree(sb, biTree.calleeTree, 0)
            sb.appendLine("```")
            sb.appendLine()
        }
        sb.appendLine("---")
        sb.appendLine()

        // 三、受影响入口点汇总
        generateEntryPointTable(sb, report)

        // 四、AI 风险评估（占位，由流式 LLM 填充）
        sb.appendLine("## 四、AI 风险评估")
        sb.appendLine()
        if (report.aiSummary != null) {
            sb.appendLine(report.aiSummary)
        } else {
            sb.appendLine("*AI 风险评估将在分析完成后自动生成...*")
        }
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        // 五、分析元信息
        generateMetadata(sb, report)

        return sb.toString()
    }

    /**
     * 生成模式 B 报告 — 指定方法分析
     */
    fun generateSingleMethodReport(report: ImpactReport, biTree: BidirectionalCallTree): String {
        val sb = StringBuilder()
        val now = LocalDateTime.now().format(dateFormatter)
        val method = biTree.method

        // 头部元信息
        sb.appendLine("# 🔍 方法影响范围分析报告")
        sb.appendLine()
        sb.appendLine("| 项目 | 值 |")
        sb.appendLine("|------|---|")
        sb.appendLine("| **分析模式** | ${report.mode.displayName} |")
        sb.appendLine("| **目标方法** | `${method.displayName}(${method.signature.substringAfter('(')})` |")
        sb.appendLine("| **所在文件** | `${method.filePath.substringAfter("/src/")}` |")
        sb.appendLine("| **所在行号** | L${method.lineNumber} |")
        sb.appendLine("| **分析方向** | 双向（向上 + 向下） |")
        sb.appendLine("| **分析时间** | $now |")
        sb.appendLine("| **上游调用者数** | ${countNodes(biTree.callerTree)} |")
        sb.appendLine("| **受影响入口点** | ${report.entryPoints.size} |")
        sb.appendLine("| **风险等级** | ${inferRiskLevel(report)} |")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        // 一、目标方法详情
        sb.appendLine("## 一、目标方法详情")
        sb.appendLine()
        sb.appendLine("**完整签名：** `${method.qualifiedName}(${method.signature.substringAfter('(')})`")
        sb.appendLine()
        if (method.annotations.isNotEmpty()) {
            sb.appendLine("**注解信息：**")
            method.annotations.forEach { anno ->
                sb.appendLine("- `@$anno`")
            }
            sb.appendLine()
        }
        sb.appendLine("**所在类信息：**")
        sb.appendLine("- 类名：`${method.className}`")
        sb.appendLine("- 包路径：`${method.packageName}`")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        // 二、向上调用链
        sb.appendLine("## 二、向上调用链（谁调用了我）")
        sb.appendLine()
        sb.appendLine("```")
        renderCallerTree(sb, biTree.callerTree, method, 0)
        sb.appendLine("```")
        sb.appendLine()

        // 向上调用链总结表
        sb.appendLine("**向上调用链总结：**")
        sb.appendLine()
        sb.appendLine("| 链路编号 | 链路深度 | 最上层调用者 | 入口类型 | 调用方式 |")
        sb.appendLine("|--------|--------|------------|---------|---------|")
        val chains = biTree.callerTree.collectChainTrees()
        chains.forEachIndexed { chainIndex, path ->
            val topNode = path.last()
            val entryType = if (topNode.isEntryPoint) {
                topNode.entryPointInfo?.type?.displayName ?: EntryPointType.OTHER.displayName
            } else {
                EntryPointType.OTHER.displayName
            }
            sb.appendLine("| ${chainIndex + 1} | ${path.size - 1} | `${topNode.method.displayName}` | $entryType | 直接调用 |")
        }
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        // 三、受影响入口点汇总
        generateEntryPointTable(sb, report)

        // 四、AI 分析与建议
        sb.appendLine("## 四、AI 分析与建议")
        sb.appendLine()
        if (report.aiSummary != null) {
            sb.appendLine(report.aiSummary)
        } else {
            sb.appendLine("*AI 分析将在分析完成后自动生成...*")
        }
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        // 五、分析元信息
        generateMetadata(sb, report)

        return sb.toString()
    }

    // ====== 通用渲染辅助 ======

    private fun generateEntryPointTable(sb: StringBuilder, report: ImpactReport) {
        sb.appendLine("## 三、受影响入口点汇总")
        sb.appendLine()
        sb.appendLine("| # | 入口类型 | 类.方法 | 路径/触发条件 | AI 说明 |")
        sb.appendLine("|---|---------|--------|-------------|---------|")
        report.entryPoints.forEachIndexed { index, ep ->
            val typeIcon = ep.type.icon
            val typeName = ep.type.displayName
            val pathOrTrigger = ep.path ?: ep.triggerCondition ?: "-"

            // AI 说明三层 fallback：
            // 1. 有代码注释 → 【注释】标记在前
            // 2. AI 已生成 → 【AI】标记在前
            // 3. 均无 → 显示"待 AI 生成"
            val aiExplanation = when {
                !ep.codeComment.isNullOrBlank() && (ep.aiExplanation.isNullOrBlank() || ep.aiExplanation == "暂无特别说明") ->
                    "【注释】${ep.codeComment}"
                !ep.aiExplanation.isNullOrBlank() && ep.aiExplanation != "暂无特别说明" -> {
                    if (!ep.codeComment.isNullOrBlank()) {
                        "【注释】${ep.codeComment}；【AI】${ep.aiExplanation}"
                    } else {
                        "【AI】${ep.aiExplanation}"
                    }
                }
                else -> "*待 AI 生成*"
            }

            sb.appendLine("| ${index + 1} | $typeIcon $typeName | `${ep.method.displayNameWithLine}` | `$pathOrTrigger` | $aiExplanation |")
        }
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()
    }

    private fun generateMetadata(sb: StringBuilder, report: ImpactReport) {
        sb.appendLine("## 分析元信息")
        sb.appendLine()
        sb.appendLine("| 项目 | 值 |")
        sb.appendLine("|------|---|")
        sb.appendLine("| **插件版本** | CodeSense AI v0.1.0 |")
        sb.appendLine("| **LLM 模型** | ${report.metadata["llmModel"] ?: "未使用"} |")
        sb.appendLine("| **分析耗时** | ${report.analysisDuration}ms |")
        sb.appendLine("| **追溯深度** | ${report.maxDepth} |")
        sb.appendLine("| **报告生成时间** | ${LocalDateTime.now().format(dateFormatter)} |")
    }

    /**
     * 渲染向上调用链（树形格式）
     */
    private fun renderCallerTree(sb: StringBuilder, tree: CallTree, targetMethod: MethodInfo, depth: Int) {
        if (tree.children.isEmpty() && depth == 0) {
            sb.appendLine("★ ${targetMethod.displayName}(${targetMethod.signature.substringAfter('(')})  ← 目标方法（无上游调用者）")
            return
        }

        // 为每条链路生成路径
        val paths = tree.collectChainPaths()
        paths.forEachIndexed { chainIndex, path ->
            sb.appendLine("链路 ${chainIndex + 1}:")

            // 找出链路中的入口点
            val reversedPath = path.reversed()
            for (i in reversedPath.indices) {
                val node = reversedPath[i]
                val indent = "  ".repeat(i)
                val prefix = if (i > 0) "└── " else ""
                val isTarget = node == targetMethod

                if (i == 0 && tree.isEntryPoint && tree.entryPointInfo != null) {
                    sb.appendLine("[${tree.entryPointInfo.type.displayName}] ${tree.entryPointInfo.path ?: tree.entryPointInfo.triggerCondition ?: ""}")
                }

                val marker = if (isTarget) " ★ " else ""
                val suffix = if (isTarget) "  ← 目标方法" else ""
                sb.appendLine("$indent$prefix$marker${node.displayName}(${node.signature.substringAfter('(')})$suffix")
            }
            sb.appendLine()
        }
    }

    /**
     * 渲染向下调用链
     */
    private fun renderCalleeTree(sb: StringBuilder, tree: CallTree, depth: Int) {
        val indent = "  ".repeat(depth)
        val prefix = when {
            depth == 0 -> "★ "
            else -> "├── "
        }
        sb.appendLine("$indent$prefix${tree.method.displayName}(${tree.method.signature.substringAfter('(')})")
        tree.children.forEach { child ->
            renderCalleeTree(sb, child, depth + 1)
        }
    }

    /**
     * 推断风险等级
     */
    private fun inferRiskLevel(report: ImpactReport): String {
        val entryCount = report.entryPoints.size
        return when {
            entryCount >= 6 -> "🔴 高风险"
            entryCount >= 3 -> "⚠️ 中等风险"
            entryCount >= 1 -> "🟡 低风险"
            else -> "🟢 无风险"
        }
    }

    private fun countNodes(tree: CallTree): Int {
        return 1 + tree.children.sumOf { countNodes(it) }
    }

    private fun flattenTree(tree: CallTree, depth: Int): List<Pair<Int, CallTree>> {
        val result = mutableListOf<Pair<Int, CallTree>>()
        result.add(depth to tree)
        tree.children.forEach { result.addAll(flattenTree(it, depth + 1)) }
        return result
    }
}
