package com.deeptek.ai.idea.git

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Git Diff 服务
 *
 * 封装 IDEA 内置的 VCS 和 Git API，提供获取差分数据的功能。
 */
@Service(Service.Level.PROJECT)
class GitDiffService(private val project: Project) {

    private val logger = Logger.getInstance(GitDiffService::class.java)

    /**
     * 获取所有未提交的变更 (包括暂存和未暂存)
     */
    suspend fun getUncommittedChanges(): List<FileDiff> = withContext(Dispatchers.IO) {
        logger.info("Getting uncommitted changes")
        val manager = ChangeListManager.getInstance(project)
        val changes = manager.allChanges
        changes.mapNotNull { it.toFileDiff() }
    }

    /**
     * 获取指定文件的未提交变更
     */
    suspend fun getFileDiff(file: VirtualFile): FileDiff? = withContext(Dispatchers.IO) {
        val manager = ChangeListManager.getInstance(project)
        val change = manager.getChange(file)
        change?.toFileDiff()
    }

    private fun Change.toFileDiff(): FileDiff? {
        val bRev = this.beforeRevision
        val aRev = this.afterRevision
        
        val filePath = (aRev?.file ?: bRev?.file)?.path ?: return null
        
        val type = when {
            bRev == null && aRev != null -> ChangeType.ADDED
            bRev != null && aRev == null -> ChangeType.DELETED
            bRev != null && aRev != null -> ChangeType.MODIFIED
            else -> ChangeType.MODIFIED
        }

        // Try getting content safely
        val beforeContent = try { bRev?.content } catch (e: Exception) { null }
        val afterContent = try { aRev?.content } catch (e: Exception) { null }

        return FileDiff(
            filePath = filePath,
            changeType = type,
            oldContent = beforeContent,
            newContent = afterContent
        )
    }

    companion object {
        fun getInstance(project: Project): GitDiffService {
            return project.getService(GitDiffService::class.java)
        }
    }
}

/**
 * 文件 Diff 数据类
 */
data class FileDiff(
    val filePath: String,
    val changeType: ChangeType,
    val oldContent: String? = null,
    val newContent: String? = null
)

/**
 * 变更类型
 */
enum class ChangeType {
    ADDED,
    MODIFIED,
    DELETED,
    RENAMED
}
