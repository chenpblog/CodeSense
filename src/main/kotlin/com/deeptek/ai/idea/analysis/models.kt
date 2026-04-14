package com.deeptek.ai.idea.analysis

/**
 * 方法元信息
 */
data class MethodInfo(
    val className: String,
    val methodName: String,
    val signature: String,
    val filePath: String,
    val lineNumber: Int,
    val packageName: String,
    val annotations: List<String>,
    val changeType: String? = null,  // MODIFIED / ADDED (仅 Git Diff 模式)
    val docComment: String? = null   // JavaDoc/KDoc 注释（首行摘要）
) {
    /** 完全限定名 */
    val qualifiedName: String get() = "$packageName.$className.$methodName"

    /** 简短显示名 */
    val displayName: String get() = "$className.$methodName"

    /** 带行号的显示名 */
    val displayNameWithLine: String get() = "$className.$methodName:L$lineNumber"
}

/**
 * 递归调用树
 */
data class CallTree(
    val method: MethodInfo,
    val children: List<CallTree>,
    val isEntryPoint: Boolean,
    val entryPointInfo: EntryPointInfo? = null
) {
    /** 获取这棵树中所有的入口点 */
    fun collectEntryPoints(): List<EntryPointInfo> {
        val result = mutableListOf<EntryPointInfo>()
        if (isEntryPoint && entryPointInfo != null) {
            result.add(entryPointInfo)
        } else if (children.isEmpty()) {
            // OTHER 类型：从代码中提取文件路径信息
            val fileName = method.filePath.substringAfterLast('/')
            val otherPath = if (fileName.isNotBlank()) "$fileName:L${method.lineNumber}" else "-"
            result.add(
                EntryPointInfo(
                    method = method,
                    type = EntryPointType.OTHER,
                    path = otherPath,
                    triggerCondition = null,
                    codeComment = method.docComment
                )
            )
        }
        children.forEach { result.addAll(it.collectEntryPoints()) }
        return result
    }

    /** 获取所有叶子到根的完整链路 */
    fun collectChainPaths(): List<List<MethodInfo>> {
        if (children.isEmpty()) return listOf(listOf(method))
        val paths = mutableListOf<List<MethodInfo>>()
        for (child in children) {
            for (subPath in child.collectChainPaths()) {
                paths.add(listOf(method) + subPath)
            }
        }
        return paths
    }

    /** 获取所有叶子到根的完整链路（包含 CallTree 节点信息） */
    fun collectChainTrees(): List<List<CallTree>> {
        if (children.isEmpty()) return listOf(listOf(this))
        val paths = mutableListOf<List<CallTree>>()
        for (child in children) {
            for (subPath in child.collectChainTrees()) {
                paths.add(listOf(this) + subPath)
            }
        }
        return paths
    }
}

/**
 * 双向调用树
 */
data class BidirectionalCallTree(
    val method: MethodInfo,
    val callerTree: CallTree,
    val calleeTree: CallTree
)

/**
 * 完整的影响分析报告
 */
data class ImpactReport(
    /** 分析模式 */
    val mode: AnalysisMode,
    /** 被修改/目标方法列表 */
    val modifiedMethods: List<MethodInfo>,
    /** 每个方法的调用链分析 */
    val callChains: Map<MethodInfo, BidirectionalCallTree>,
    /** 所有受影响的入口点 */
    val entryPoints: List<EntryPointInfo>,
    /** AI 生成的风险评估（可选，流式填充） */
    var aiSummary: String? = null,
    /** 分析耗时 (ms) */
    val analysisDuration: Long = 0,
    /** 追溯深度 */
    val maxDepth: Int = 10,
    /** 额外元信息 */
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 分析模式
 */
enum class AnalysisMode(val displayName: String) {
    GIT_DIFF("Git Diff 分支对比"),
    SINGLE_METHOD("指定方法分析")
}

/**
 * 入口点信息
 */
data class EntryPointInfo(
    val method: MethodInfo,
    val type: EntryPointType,
    val path: String?,              // HTTP: "POST /api/user/update", Dubbo: "UserFacade.method"
    val triggerCondition: String?,   // cron 表达式, topic 等
    val affectedMethods: List<String> = emptyList(),  // 影响的变更方法名
    val chainDepth: Int = 0,          // 调用链深度
    var aiExplanation: String? = null, // AI 生成的影响说明
    val codeComment: String? = null   // 从代码注释中提取的说明
)

/**
 * 入口点类型
 */
enum class EntryPointType(val icon: String, val displayName: String) {
    HTTP_API("🌐", "HTTP API"),
    DUBBO_RPC("🔗", "Dubbo RPC"),
    SCHEDULED("⏰", "定时任务"),
    MQ_LISTENER("📨", "MQ 消费"),
    EVENT_LISTENER("📡", "事件监听"),
    OTHER("⚙️", "其他顶级方法")
}
