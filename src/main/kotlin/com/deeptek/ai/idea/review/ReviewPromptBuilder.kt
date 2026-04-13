package com.deeptek.ai.idea.review

import com.deeptek.ai.idea.git.FileDiff

/**
 * 审查 Prompt 构建器
 *
 * 负责将 FileDiff 对象转换为发给大模型的 Prompt 文本。
 */
object ReviewPromptBuilder {

    /**
     * 构建单文件审查的 Prompt
     */
    fun buildSingleFilePrompt(fileDiff: FileDiff): String {
        val sb = StringBuilder()
        sb.append("请作为一位资深的研发工程师，对以下代码变更进行严格的代码审查。\n\n")
        
        sb.append("审查重点（包含但不限于）：\n")
        sb.append("1. **潜在 Bug**：空指针、越界、并发问题等\n")
        sb.append("2. **性能问题**：不高效的循环、冗余资源分配等\n")
        sb.append("3. **设计与可读性**：命名规范、单一职责、是否可以重构优化\n")
        sb.append("4. **安全风险**：注入漏洞、敏感信息泄露等\n\n")
        
        sb.append("请使用 Markdown 格式组织你的回答，先给出总体评估，然后分条列出发现的问题，如果发现问题，请给出改进前后的代码片段。\n\n")
        
        sb.append("--- 【文件：${fileDiff.filePath}】 ---\n")
        sb.append("变更类型：${fileDiff.changeType.name}\n\n")

        if (fileDiff.newContent != null) {
            sb.append("【修改后的最新代码】:\n```\n${fileDiff.newContent}\n```\n\n")
        }
        
        if (fileDiff.oldContent != null && fileDiff.newContent != fileDiff.oldContent) {
            sb.append("【原代码参考】(用于对比变更):\n```\n${fileDiff.oldContent}\n```\n\n")
        }

        return sb.toString()
    }

    /**
     * 构建多文件批量审查的 Prompt
     */
    fun buildBatchReviewPrompt(diffs: List<FileDiff>): String {
        val sb = StringBuilder()
        sb.append("请作为一位资深的研发工程师，对以下一组代码变更进行代码审查。\n\n")
        sb.append("本次变更涉及 ${diffs.size} 个文件。请重点关注这些文件之间的依赖关系和整体业务逻辑是否完整。\n\n")
        
        for (diff in diffs) {
            sb.append("--- 【文件：${diff.filePath}】 ---\n")
            sb.append("变更类型：${diff.changeType.name}\n")
            if (diff.newContent != null) {
                sb.append("最新代码:\n```\n${diff.newContent}\n```\n\n")
            }
        }
        
        sb.append("请使用 Markdown 输出审查报告。先进行整体评估，然后对每个有问题的文件给出具体意见。\n")
        return sb.toString()
    }
}
