package com.deeptek.ai.idea.review

import com.deeptek.ai.idea.git.FileDiff
import com.deeptek.ai.idea.llm.ChatMessage
import com.deeptek.ai.idea.llm.LlmProviderFactory
import com.deeptek.ai.idea.settings.CodeSenseSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 核心代码审查服务
 *
 * 组织模型调用请求，获取审查结果。支持流式返回。
 */
@Service(Service.Level.PROJECT)
class CodeReviewService(private val project: Project) {

    private val logger = Logger.getInstance(CodeReviewService::class.java)

    /**
     * 流式审查单文件
     */
    suspend fun reviewSingleFile(fileDiff: FileDiff): Flow<String> {
        checkReviewEnabled()

        val prompt = ReviewPromptBuilder.buildSingleFilePrompt(fileDiff)
        logger.debug("Reviewing single file: ${fileDiff.filePath}")

        val provider = LlmProviderFactory.createDefault()
        
        val messages = listOf(
            ChatMessage.system("你是一个专业的智能代码审查助手，运行在 IntelliJ 插件中。"),
            ChatMessage.user(prompt)
        )

        val stream = provider.chatCompletionStream(messages)
        
        return stream.map { chunk ->
            chunk.deltaContent ?: ""
        }
    }

    /**
     * 批量审查提交的多个文件
     */
    suspend fun reviewChanges(diffs: List<FileDiff>): Flow<String> {
        checkReviewEnabled()

        val settings = CodeSenseSettings.getInstance()
        val limit = settings.state.maxReviewFiles
        
        val targetDiffs = if (diffs.size > limit) {
            logger.warn("Changes exceed max review files limit. Truncating to $limit files.")
            diffs.take(limit)
        } else diffs

        val prompt = ReviewPromptBuilder.buildBatchReviewPrompt(targetDiffs)
        logger.debug("Reviewing ${targetDiffs.size} files in batch")

        val provider = LlmProviderFactory.createDefault()
        
        val messages = listOf(
            ChatMessage.system("你是一个专业的智能代码审查助手，运行在 IntelliJ 插件中。"),
            ChatMessage.user(prompt)
        )

        return provider.chatCompletionStream(messages).map { chunk ->
            chunk.deltaContent ?: ""
        }
    }

    private fun checkReviewEnabled() {
        if (!CodeSenseSettings.getInstance().state.enableCodeReview) {
            throw IllegalStateException("代码审查功能已在设置中被禁用。")
        }
    }

    companion object {
        fun getInstance(project: Project): CodeReviewService {
            return project.getService(CodeReviewService::class.java)
        }
    }
}
