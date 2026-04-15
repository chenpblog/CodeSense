package com.deeptek.ai.idea.git

import com.deeptek.ai.idea.analysis.MethodInfo
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtClass

/**
 * Diff → 方法级变更提取器
 *
 * 将 GitDiffService 返回的文件级 FileDiff 精确映射到方法级 MethodInfo。
 *
 * 工作流程：
 * 1. 过滤仅 .java / .kt 文件
 * 2. 对 oldContent / newContent 使用 PsiFileFactory 创建临时 PsiFile
 * 3. 提取新旧文件中的方法列表
 * 4. 按方法签名做 diff 比对，输出 MODIFIED / ADDED / DELETED
 * 5. 返回 List<MethodDiff>，包含 MethodInfo + 变更类型 + 行号范围
 */
object DiffMethodExtractor {

    private val logger = Logger.getInstance(DiffMethodExtractor::class.java)

    /**
     * 从文件级 Diff 列表中提取方法级变更
     */
    fun extractChangedMethods(project: Project, diffs: List<FileDiff>): List<MethodDiff> {
        val result = mutableListOf<MethodDiff>()

        for (diff in diffs) {
            // 只分析 Java 和 Kotlin 文件
            if (!isAnalyzableFile(diff.filePath)) continue

            try {
                val methods = extractMethodsFromDiff(project, diff)
                result.addAll(methods)
            } catch (e: Exception) {
                logger.warn("Failed to extract methods from ${diff.filePath}", e)
            }
        }

        logger.info("Extracted ${result.size} changed methods from ${diffs.size} file diffs")
        return result
    }

    /**
     * 从单个 FileDiff 提取方法级变更
     */
    private fun extractMethodsFromDiff(project: Project, diff: FileDiff): List<MethodDiff> {
        return runReadAction {
            when (diff.changeType) {
                ChangeType.ADDED -> {
                    // 新增文件：所有方法都是 ADDED
                    val newFile = createPsiFile(project, diff.filePath, diff.newContent ?: return@runReadAction emptyList())
                    val methods = extractMethodSignatures(newFile, diff.filePath)
                    methods.map { MethodDiff(it, ChangeType.ADDED) }
                }

                ChangeType.DELETED -> {
                    // 删除文件：所有方法都是 DELETED
                    val oldFile = createPsiFile(project, diff.filePath, diff.oldContent ?: return@runReadAction emptyList())
                    val methods = extractMethodSignatures(oldFile, diff.filePath)
                    methods.map { MethodDiff(it, ChangeType.DELETED) }
                }

                ChangeType.MODIFIED, ChangeType.RENAMED -> {
                    // 修改文件：对比新旧方法列表
                    val oldContent = diff.oldContent ?: ""
                    val newContent = diff.newContent ?: ""
                    if (oldContent.isBlank() && newContent.isBlank()) return@runReadAction emptyList()

                    val oldFile = if (oldContent.isNotBlank()) createPsiFile(project, diff.filePath, oldContent) else null
                    val newFile = if (newContent.isNotBlank()) createPsiFile(project, diff.filePath, newContent) else null

                    val oldMethods = oldFile?.let { extractMethodSignatures(it, diff.filePath) } ?: emptyList()
                    val newMethods = newFile?.let { extractMethodSignatures(it, diff.filePath) } ?: emptyList()

                    diffMethods(oldMethods, newMethods, oldContent, newContent)
                }
            }
        }
    }

    /**
     * 对比新旧方法列表，输出变更
     */
    private fun diffMethods(
        oldMethods: List<MethodInfo>,
        newMethods: List<MethodInfo>,
        oldContent: String,
        newContent: String
    ): List<MethodDiff> {
        val result = mutableListOf<MethodDiff>()

        val oldMap = oldMethods.associateBy { it.signature }
        val newMap = newMethods.associateBy { it.signature }

        // 在新版本中存在的方法
        for ((sig, newMethod) in newMap) {
            val oldMethod = oldMap[sig]
            if (oldMethod == null) {
                // ADDED：只在新文件中存在
                result.add(MethodDiff(newMethod.copy(changeType = "ADDED"), ChangeType.ADDED))
            } else {
                // 检查方法体是否有变化
                val oldBody = extractMethodBody(oldContent, oldMethod)
                val newBody = extractMethodBody(newContent, newMethod)
                if (oldBody != newBody) {
                    result.add(MethodDiff(newMethod.copy(changeType = "MODIFIED"), ChangeType.MODIFIED))
                }
                // 如果 body 相同则跳过（未变更）
            }
        }

        // 在旧版本中存在但新版本不存在的方法 → DELETED
        for ((sig, oldMethod) in oldMap) {
            if (sig !in newMap) {
                result.add(MethodDiff(oldMethod.copy(changeType = "DELETED"), ChangeType.DELETED))
            }
        }

        return result
    }

    /**
     * 根据行号范围提取方法体文本
     */
    private fun extractMethodBody(fileContent: String, methodInfo: MethodInfo): String {
        val lines = fileContent.lines()
        val startLine = (methodInfo.lineNumber - 1).coerceAtLeast(0)
        val endLine = (methodInfo.lineEndNumber - 1).coerceAtMost(lines.size - 1)
        if (startLine > endLine || startLine >= lines.size) return ""
        return lines.subList(startLine, endLine + 1).joinToString("\n").trim()
    }

    /**
     * 从 PsiFile 中提取所有方法的 MethodInfo
     */
    private fun extractMethodSignatures(psiFile: PsiFile, filePath: String): List<MethodInfo> {
        val methods = mutableListOf<MethodInfo>()

        if (filePath.endsWith(".java")) {
            // Java 文件
            val psiMethods = PsiTreeUtil.findChildrenOfType(psiFile, PsiMethod::class.java)
            for (method in psiMethods) {
                methods.add(psiMethodToInfo(method, filePath))
            }
        } else if (filePath.endsWith(".kt")) {
            // Kotlin 文件
            val ktFunctions = PsiTreeUtil.findChildrenOfType(psiFile, KtNamedFunction::class.java)
            for (func in ktFunctions) {
                methods.add(ktFunctionToInfo(func, filePath))
            }
        }

        return methods
    }

    /**
     * PsiMethod → MethodInfo (从临时 PsiFile，不使用 toMethodInfo() 扩展)
     */
    private fun psiMethodToInfo(method: PsiMethod, filePath: String): MethodInfo {
        val containingClass = method.containingClass
        val paramTypes = method.parameterList.parameters
            .joinToString(", ") { it.type.presentableText }

        val doc = method.docComment?.let { docComment ->
            val text = docComment.text
            text.removePrefix("/**")
                .removeSuffix("*/")
                .lines()
                .map { it.trim().removePrefix("*").trim() }
                .filter { it.isNotBlank() && !it.startsWith("@") }
                .firstOrNull()
                ?.take(80)
        }

        val document = method.containingFile?.viewProvider?.document
        val startLine = document?.getLineNumber(method.textRange.startOffset)?.plus(1) ?: 0
        val endLine = document?.getLineNumber(method.textRange.endOffset)?.plus(1) ?: startLine

        return MethodInfo(
            className = containingClass?.name ?: "Unknown",
            methodName = method.name,
            signature = "${method.name}($paramTypes)",
            filePath = filePath,
            lineNumber = startLine,
            lineEndNumber = endLine,
            packageName = (containingClass?.qualifiedName ?: "").substringBeforeLast('.', ""),
            annotations = method.annotations.mapNotNull { it.qualifiedName?.substringAfterLast('.') },
            docComment = doc
        )
    }

    /**
     * KtNamedFunction → MethodInfo
     */
    private fun ktFunctionToInfo(func: KtNamedFunction, filePath: String): MethodInfo {
        val containingClass = PsiTreeUtil.getParentOfType(func, KtClass::class.java)
        val paramTypes = func.valueParameters
            .joinToString(", ") { it.typeReference?.text ?: "Any" }

        val document = func.containingFile?.viewProvider?.document
        val startLine = document?.getLineNumber(func.textRange.startOffset)?.plus(1) ?: 0
        val endLine = document?.getLineNumber(func.textRange.endOffset)?.plus(1) ?: startLine

        return MethodInfo(
            className = containingClass?.name ?: func.containingKtFile.packageFqName.asString().substringAfterLast('.', "TopLevel"),
            methodName = func.name ?: "anonymous",
            signature = "${func.name ?: "anonymous"}($paramTypes)",
            filePath = filePath,
            lineNumber = startLine,
            lineEndNumber = endLine,
            packageName = func.containingKtFile.packageFqName.asString(),
            annotations = func.annotationEntries.mapNotNull { it.shortName?.asString() },
            docComment = null
        )
    }

    /**
     * 使用 PsiFileFactory 从字符串创建临时 PsiFile
     */
    private fun createPsiFile(project: Project, filePath: String, content: String): PsiFile {
        val factory = PsiFileFactory.getInstance(project)
        val fileName = filePath.substringAfterLast('/')
        val language = when {
            fileName.endsWith(".java") -> JavaLanguage.INSTANCE
            fileName.endsWith(".kt") -> KotlinLanguage.INSTANCE
            else -> JavaLanguage.INSTANCE
        }
        return factory.createFileFromText(fileName, language, content)
    }

    /**
     * 判断文件是否可分析
     */
    private fun isAnalyzableFile(filePath: String): Boolean {
        return filePath.endsWith(".java") || filePath.endsWith(".kt")
    }
}

/**
 * 方法级 Diff 数据
 */
data class MethodDiff(
    val methodInfo: MethodInfo,
    val changeType: ChangeType
)
