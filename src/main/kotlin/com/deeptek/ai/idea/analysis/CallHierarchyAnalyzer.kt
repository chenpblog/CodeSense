package com.deeptek.ai.idea.analysis

import com.intellij.ide.hierarchy.HierarchyBrowserBaseEx
import com.intellij.ide.hierarchy.HierarchyNodeDescriptor
import com.intellij.ide.hierarchy.call.CalleeMethodsTreeStructure
import com.intellij.ide.hierarchy.call.CallerMethodsTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

/**
 * Call Hierarchy 分析器
 *
 * 利用 IDEA 的 PSI 和 Call Hierarchy API 自动分析方法的上下游调用链和影响范围。
 *
 * 核心功能：
 * - 向上分析：谁调用了指定方法 (CallerMethodsTreeStructure)
 * - 向下分析：指定方法调用了谁 (CalleeMethodsTreeStructure)
 * - 入口点检测：识别 HTTP API / 定时任务 / MQ / Dubbo RPC 等入口
 * - 报告生成：生成结构化 Markdown 报告
 */
@Service(Service.Level.PROJECT)
class CallHierarchyAnalyzer(private val project: Project) {

    private val logger = Logger.getInstance(CallHierarchyAnalyzer::class.java)

    /**
     * 分析某个方法的上游调用者（谁调用了我）
     */
    fun analyzeCallers(psiMethod: PsiMethod, maxDepth: Int = 10): CallTree {
        logger.info("Analyzing callers of ${psiMethod.name}, maxDepth=$maxDepth")
        return ReadAction.compute<CallTree, Throwable> {
            val methodInfo = psiMethod.toMethodInfo()
            val isEntry = EntryPointDetector.isEntryPoint(psiMethod)
            val entryInfo = if (isEntry) EntryPointDetector.extractEntryPointInfo(psiMethod, methodInfo) else null

            val callers = findCallers(psiMethod, maxDepth, mutableSetOf())
            CallTree(
                method = methodInfo,
                children = callers,
                isEntryPoint = isEntry,
                entryPointInfo = entryInfo
            )
        }
    }

    /**
     * 分析某个方法的下游被调用者（我调用了谁）
     */
    fun analyzeCallees(psiMethod: PsiMethod, maxDepth: Int = 10): CallTree {
        logger.info("Analyzing callees of ${psiMethod.name}, maxDepth=$maxDepth")
        return ReadAction.compute<CallTree, Throwable> {
            val methodInfo = psiMethod.toMethodInfo()
            val callees = findCallees(psiMethod, maxDepth, mutableSetOf())
            CallTree(
                method = methodInfo,
                children = callees,
                isEntryPoint = false
            )
        }
    }

    /**
     * 双向分析
     */
    fun analyzeBidirectional(psiMethod: PsiMethod, maxDepth: Int = 10): BidirectionalCallTree {
        val methodInfo = ReadAction.compute<MethodInfo, Throwable> { psiMethod.toMethodInfo() }
        val callerTree = analyzeCallers(psiMethod, maxDepth)
        val calleeTree = analyzeCallees(psiMethod, maxDepth)
        return BidirectionalCallTree(
            method = methodInfo,
            callerTree = callerTree,
            calleeTree = calleeTree
        )
    }

    /**
     * 根据光标所在偏移量获取 PsiMethod
     */
    fun findMethodAtCaret(psiFile: PsiFile, offset: Int): PsiMethod? {
        return ReadAction.compute<PsiMethod?, Throwable> {
            val element = psiFile.findElementAt(offset)
            PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
        }
    }

    // ====== 递归查找调用者 ======

    private fun findCallers(
        method: PsiMethod,
        remainingDepth: Int,
        visited: MutableSet<String>
    ): List<CallTree> {
        if (remainingDepth <= 0) return emptyList()

        val methodKey = method.containingClass?.qualifiedName + "." + method.name
        if (methodKey in visited) return emptyList()
        visited.add(methodKey)

        val callers = mutableListOf<CallTree>()

        try {
            val treeStructure = CallerMethodsTreeStructure(project, method, HierarchyBrowserBaseEx.SCOPE_PROJECT)
            val rootElement = treeStructure.rootElement

            val children = treeStructure.getChildElements(rootElement)
            for (child in children) {
                val descriptor = child as? NodeDescriptor<*> ?: continue
                val callerMethod = extractPsiMethod(descriptor) ?: continue

                val callerInfo = callerMethod.toMethodInfo()
                val isEntry = EntryPointDetector.isEntryPoint(callerMethod)
                val entryInfo = if (isEntry) EntryPointDetector.extractEntryPointInfo(callerMethod, callerInfo) else null

                val subCallers = findCallers(callerMethod, remainingDepth - 1, visited)

                callers.add(CallTree(
                    method = callerInfo,
                    children = subCallers,
                    isEntryPoint = isEntry,
                    entryPointInfo = entryInfo
                ))
            }
        } catch (e: Exception) {
            logger.warn("Error analyzing callers of ${method.name}", e)
        }

        return callers
    }

    // ====== 递归查找被调用者 ======

    private fun findCallees(
        method: PsiMethod,
        remainingDepth: Int,
        visited: MutableSet<String>
    ): List<CallTree> {
        if (remainingDepth <= 0) return emptyList()

        val methodKey = method.containingClass?.qualifiedName + "." + method.name
        if (methodKey in visited) return emptyList()
        visited.add(methodKey)

        val callees = mutableListOf<CallTree>()

        try {
            val treeStructure = CalleeMethodsTreeStructure(project, method, HierarchyBrowserBaseEx.SCOPE_PROJECT)
            val rootElement = treeStructure.rootElement

            val children = treeStructure.getChildElements(rootElement)
            for (child in children) {
                val descriptor = child as? NodeDescriptor<*> ?: continue
                val calleeMethod = extractPsiMethod(descriptor) ?: continue

                val calleeInfo = calleeMethod.toMethodInfo()
                val subCallees = findCallees(calleeMethod, remainingDepth - 1, visited)

                callees.add(CallTree(
                    method = calleeInfo,
                    children = subCallees,
                    isEntryPoint = false
                ))
            }
        } catch (e: Exception) {
            logger.warn("Error analyzing callees of ${method.name}", e)
        }

        return callees
    }

    // ====== 辅助方法 ======

    /**
     * 从 NodeDescriptor 中提取 PsiMethod
     */
    private fun extractPsiMethod(descriptor: NodeDescriptor<*>): PsiMethod? {
        if (descriptor is HierarchyNodeDescriptor) {
            val psi = descriptor.psiElement
            if (psi is PsiMethod) return psi
        }
        
        val element = descriptor.element
        return when {
            element is PsiMethod -> element
            descriptor.javaClass.simpleName == "CallHierarchyNodeDescriptor" -> {
                // 回退暴力反射 fallback
                try {
                    val method = descriptor.javaClass.getMethod("getEnclosingElement")
                    method.invoke(descriptor) as? PsiMethod
                } catch (e: Exception) {
                    null
                }
            }
            else -> null
        }
    }

    companion object {
        fun getInstance(project: Project): CallHierarchyAnalyzer {
            return project.getService(CallHierarchyAnalyzer::class.java)
        }
    }
}

// ====== PsiMethod 扩展函数 ======

/**
 * 将 PsiMethod 转换为 MethodInfo 数据模型
 */
fun PsiMethod.toMethodInfo(): MethodInfo {
    val containingClass = this.containingClass
    val file = this.containingFile?.virtualFile

    val paramTypes = this.parameterList.parameters
        .joinToString(", ") { it.type.presentableText }

    return MethodInfo(
        className = containingClass?.name ?: "Unknown",
        methodName = this.name,
        signature = "${this.name}($paramTypes)",
        filePath = file?.path ?: "",
        lineNumber = this.textOffset.let { offset ->
            this.containingFile?.viewProvider?.document?.getLineNumber(offset)?.plus(1) ?: 0
        },
        packageName = (containingClass?.qualifiedName ?: "").substringBeforeLast('.', ""),
        annotations = this.annotations.mapNotNull {
            it.qualifiedName?.substringAfterLast('.')
        }
    )
}
