package com.deeptek.ai.idea.git

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
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

    /**
     * 获取当前活跃分支名称
     */
    fun getCurrentBranch(): String? {
        val repoManager = GitRepositoryManager.getInstance(project)
        val repo = repoManager.repositories.firstOrNull() ?: return null
        return repo.currentBranchName
    }

    /**
     * 获取本地所有分支名列表
     */
    fun getLocalBranches(): List<String> {
        val repoManager = GitRepositoryManager.getInstance(project)
        val repo = repoManager.repositories.firstOrNull() ?: return emptyList()
        return repo.branches.localBranches.map { it.name }.sorted()
    }

    /**
     * 自动检测主干分支名 (main 或 master)
     */
    fun detectMainBranch(): String {
        val branches = getLocalBranches()
        return when {
            branches.contains("main") -> "main"
            branches.contains("master") -> "master"
            else -> branches.firstOrNull() ?: "main"
        }
    }

    /**
     * 获取两个分支之间的差异
     *
     * 使用 git diff targetBranch currentBranch 获取两个分支间的全部变更
     */
    suspend fun getBranchDiff(currentBranch: String, targetBranch: String): List<FileDiff> = withContext(Dispatchers.IO) {
        logger.info("Getting branch diff: $targetBranch vs $currentBranch")
        val repoManager = GitRepositoryManager.getInstance(project)
        val repo = repoManager.repositories.firstOrNull()
            ?: throw IllegalStateException("未找到 Git 仓库")

        val root = repo.root
        logger.info("Git root: ${root.path}")

        // 1. 获取变更文件列表 (使用行监听器可靠收集输出)
        val collectedLines = mutableListOf<String>()
        val nameStatusHandler = GitLineHandler(project, root, GitCommand.DIFF)
        nameStatusHandler.addParameters("--name-status", targetBranch, currentBranch)
        nameStatusHandler.setSilent(false)
        nameStatusHandler.addLineListener(object : git4idea.commands.GitLineHandlerListener {
            override fun onLineAvailable(line: String, outputType: com.intellij.openapi.util.Key<*>) {
                if (outputType == com.intellij.execution.process.ProcessOutputTypes.STDOUT) {
                    collectedLines.add(line)
                }
            }
        })

        val nameStatusResult = Git.getInstance().runCommand(nameStatusHandler)

        logger.info("git diff --name-status exitCode=${nameStatusResult.exitCode}, success=${nameStatusResult.success()}")
        logger.info("result.output lines: ${nameStatusResult.output.size}")
        logger.info("collected lines: ${collectedLines.size}")

        if (!nameStatusResult.success()) {
            val errorMsg = nameStatusResult.errorOutputAsJoinedString
            logger.error("git diff --name-status failed: $errorMsg")
            throw RuntimeException("获取分支差异失败: $errorMsg")
        }

        // 优先使用行监听器收集的输出，兜底使用 result.output
        val outputLines = if (collectedLines.isNotEmpty()) collectedLines else nameStatusResult.output

        val fileEntries = outputLines
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split("\t")
                val status = parts[0].trim()
                val filePath = if (parts.size > 1) parts[1].trim() else ""
                status to filePath
            }

        logger.info("Parsed ${fileEntries.size} file entries from branch diff")

        if (fileEntries.isEmpty()) {
            return@withContext emptyList()
        }

        // 2. 获取每个文件的具体内容
        fileEntries.mapNotNull { (status, filePath) ->
            if (filePath.isBlank()) return@mapNotNull null

            val changeType = when {
                status.startsWith("A") -> ChangeType.ADDED
                status.startsWith("D") -> ChangeType.DELETED
                status.startsWith("M") -> ChangeType.MODIFIED
                status.startsWith("R") -> ChangeType.RENAMED
                else -> ChangeType.MODIFIED
            }

            // 获取文件在两个分支中的内容
            val oldContent = if (changeType != ChangeType.ADDED) {
                getFileContentAtBranch(root, targetBranch, filePath)
            } else null

            val newContent = if (changeType != ChangeType.DELETED) {
                getFileContentAtBranch(root, currentBranch, filePath)
            } else null

            FileDiff(
                filePath = filePath,
                changeType = changeType,
                oldContent = oldContent,
                newContent = newContent
            )
        }
    }

    /**
     * 获取指定分支中某个文件的内容
     */
    private fun getFileContentAtBranch(root: VirtualFile, branch: String, filePath: String): String? {
        return try {
            val collectedLines = mutableListOf<String>()
            val handler = GitLineHandler(project, root, GitCommand.SHOW)
            handler.addParameters("$branch:$filePath")
            handler.setSilent(false)
            handler.addLineListener(object : git4idea.commands.GitLineHandlerListener {
                override fun onLineAvailable(line: String, outputType: com.intellij.openapi.util.Key<*>) {
                    if (outputType == com.intellij.execution.process.ProcessOutputTypes.STDOUT) {
                        collectedLines.add(line)
                    }
                }
            })
            val result = Git.getInstance().runCommand(handler)
            if (result.success()) {
                val output = if (collectedLines.isNotEmpty()) collectedLines else result.output
                output.joinToString("\n")
            } else {
                logger.warn("git show $branch:$filePath failed: ${result.errorOutputAsJoinedString}")
                null
            }
        } catch (e: Exception) {
            logger.warn("Failed to get content of $filePath at $branch", e)
            null
        }
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

