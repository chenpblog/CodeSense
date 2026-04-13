# IDEA AI Agent 插件 — 执行计划

> 文档编号: `idea-ai-plugin-plan`
> 创建时间: 2026-04-03

## 一、项目概述

### 1.1 目标
开发一款 IntelliJ IDEA 插件（Kotlin 全新开发），提供以下核心能力：
- **代码审查/解释**：基于 Git Diff（主干对比 / 时间维度对比）调用 LLM 对变更代码进行 Review 和解释
- **影响范围分析**：利用 IDEA 内置的 Call Hierarchy API（PSI），自动分析代码修改的上下游调用链和影响范围
- **Agent 自主执行**：AI 可以自主调用工具（读写文件、执行命令、搜索代码）来完成复杂任务
- **商业模型接入**：支持 MiniMax、智谱 GLM 等国产商业大模型

### 1.2 插件命名（暂定）
**CodeSense AI** — 代码感知 AI 助手

---

## 二、技术选型

| 维度 | 选型 | 说明 |
|------|------|------|
| **开发语言** | Kotlin | JetBrains 官方推荐 |
| **构建工具** | Gradle (Kotlin DSL) | `build.gradle.kts` + IntelliJ Platform Gradle Plugin 2.x |
| **目标 IDE 版本** | IntelliJ IDEA 2024.3+ | 兼容较新版本，使用 Kotlin Coroutines API |
| **UI 框架** | Swing + JCEF | 侧边栏 Chat 使用 JCEF(Chromium) 渲染富文本；设置页用 Kotlin UI DSL |
| **LLM 通信** | OkHttp + Kotlin Coroutines | 统一的 OpenAI 兼容 HTTP 客户端（MiniMax/GLM 均兼容此格式） |
| **Git 操作** | `git4idea` API | 使用 IDEA 内置的 Git API，非原生 JGit |
| **代码分析** | PSI + Call Hierarchy API | 利用 `CallerMethodsTreeStructure` 等分析调用链 |
| **序列化** | kotlinx.serialization | JSON 请求/响应序列化 |

---

## 三、系统架构

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────────────┐
│                    IntelliJ IDEA                        │
│  ┌──────────────────────────────────────────────────┐   │
│  │              CodeSense AI Plugin                 │   │
│  │                                                  │   │
│  │  ┌─────────────┐  ┌──────────────┐  ┌────────┐  │   │
│  │  │  UI Layer   │  │ Action Layer │  │Settings│  │   │
│  │  │ (ToolWindow)│  │  (Menu/KB)   │  │  Page  │  │   │
│  │  └──────┬──────┘  └──────┬───────┘  └───┬────┘  │   │
│  │         │                │              │        │   │
│  │  ┌──────▼────────────────▼──────────────▼────┐   │   │
│  │  │           Core Service Layer              │   │   │
│  │  │  ┌──────────┐ ┌──────────┐ ┌───────────┐  │   │   │
│  │  │  │   Git    │ │   PSI    │ │   Agent   │  │   │   │
│  │  │  │ Service  │ │ Analyzer │ │  Engine   │  │   │   │
│  │  │  └────┬─────┘ └────┬─────┘ └─────┬─────┘  │   │   │
│  │  │       │            │             │         │   │   │
│  │  │  ┌────▼────────────▼─────────────▼─────┐   │   │   │
│  │  │  │         LLM Client Layer            │   │   │   │
│  │  │  │  ┌─────────┐  ┌─────────┐           │   │   │   │
│  │  │  │  │ MiniMax │  │   GLM   │  ...more  │   │   │   │
│  │  │  │  │Provider │  │Provider │           │   │   │   │
│  │  │  │  └─────────┘  └─────────┘           │   │   │   │
│  │  │  └─────────────────────────────────────┘   │   │   │
│  │  └────────────────────────────────────────────┘   │   │
│  └──────────────────────────────────────────────────┘   │
│                                                         │
│  IDE APIs: git4idea, PSI, VFS, TerminalService, etc.    │
└─────────────────────────────────────────────────────────┘
         │                              │
         ▼                              ▼
  ┌──────────────┐              ┌──────────────┐
  │  MiniMax API │              │   GLM API    │
  │ api.minimax  │              │ open.bigmodel│
  │  .io/v1      │              │  .cn/api/v4  │
  └──────────────┘              └──────────────┘
```

### 3.2 分层说明

| 层级 | 职责 | 关键类 |
|------|------|--------|
| **UI Layer** | Chat 侧边栏、Diff Review 面板、Impact 结果展示 | `ChatToolWindowFactory`, `ReviewPanel` |
| **Action Layer** | 右键菜单、快捷键、Editor 操作 | `ReviewDiffAction`, `AnalyzeImpactAction` |
| **Settings** | API Key 配置、模型选择、参数调整 | `CodeSenseSettingsConfigurable` |
| **Git Service** | Git diff 获取、分支/时间维度对比 | `GitDiffService` |
| **PSI Analyzer** | Call Hierarchy 分析、影响范围计算 | `CallHierarchyAnalyzer` |
| **Agent Engine** | Tool Calling 循环、上下文管理 | `AgentExecutor`, `ToolRegistry` |
| **LLM Client** | 统一的模型调用接口、流式响应处理 | `LlmClient`, `MiniMaxProvider`, `GlmProvider` |

---

## 四、核心模块详细设计

### 4.1 模块一：LLM 客户端层

#### 设计思路
MiniMax 和 GLM 都兼容 OpenAI 的 Chat Completions API 格式，因此抽象出统一接口：

```kotlin
// 统一 LLM 提供者接口
interface LlmProvider {
    val name: String
    val baseUrl: String
    
    suspend fun chatCompletion(request: ChatRequest): ChatResponse
    suspend fun chatCompletionStream(request: ChatRequest): Flow<ChatChunk>
    
    // Tool Calling 支持
    suspend fun chatWithTools(
        request: ChatRequest, 
        tools: List<ToolDefinition>
    ): ChatResponse
}

// MiniMax 实现
class MiniMaxProvider(config: ProviderConfig) : LlmProvider {
    override val baseUrl = config.baseUrl  // 从配置读取，默认 https://api.minimax.io/v1
    // ...
}

// GLM 实现
class GlmProvider(config: ProviderConfig) : LlmProvider {
    override val baseUrl = config.baseUrl  // 从配置读取，默认 https://open.bigmodel.cn/api/paas/v4
    // ...
}
```

#### 关键文件
| 文件 | 说明 |
|------|------|
| `llm/LlmProvider.kt` | 统一接口定义 |
| `llm/LlmClient.kt` | HTTP 客户端封装（OkHttp + Coroutines） |
| `llm/models.kt` | ChatRequest、ChatResponse 等数据模型 |
| `llm/providers/MiniMaxProvider.kt` | MiniMax 适配实现 |
| `llm/providers/GlmProvider.kt` | GLM 适配实现 |
| `llm/providers/OpenAiCompatProvider.kt` | 通用 OpenAI 兼容实现（方便后续扩展） |

---

### 4.2 模块二：Git Diff 代码审查

#### 功能描述
- **场景 A — 分支对比**：将当前分支与主干 (main/master) 对比，获取所有变更文件和 diff 内容
- **场景 B — 时间维度**：获取某个时间范围内的所有提交变更
- **场景 C — 单文件对比**：对当前编辑器中打开的文件与指定版本的对比

#### 技术实现
```kotlin
class GitDiffService(private val project: Project) {

    // 获取两个分支之间的 diff
    suspend fun getDiffBetweenBranches(
        sourceBranch: String, 
        targetBranch: String
    ): List<FileDiff>

    // 获取指定时间范围内的变更
    suspend fun getDiffByTimeRange(
        since: LocalDateTime, 
        until: LocalDateTime
    ): List<FileDiff>

    // 获取当前未提交的变更
    suspend fun getUncommittedChanges(): List<FileDiff>
}

data class FileDiff(
    val filePath: String,
    val changeType: ChangeType, // ADDED, MODIFIED, DELETED, RENAMED
    val oldContent: String?,
    val newContent: String?,
    val unifiedDiff: String     // 标准 unified diff 格式
)
```

#### 使用 IDEA 内置 API
```kotlin
// 核心依赖的 IDEA Git API
import git4idea.repo.GitRepositoryManager
import git4idea.history.GitHistoryUtils
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.diff.DiffRequestFactory
```

#### LLM Review Prompt 设计
```
你是一位资深的 Java/Kotlin 代码审查专家。请审查以下 Git Diff 变更：

## 变更文件列表
{file_list}

## Diff 内容
{unified_diff}

请从以下维度进行审查：
1. 🐛 潜在 Bug 和逻辑缺陷
2. 🔒 安全风险
3. ⚡ 性能问题
4. 📐 代码规范和最佳实践
5. 📝 总结：本次变更的核心目的和影响
```

---

### 4.3 模块三：Call Hierarchy 影响范围分析

#### 4.3.1 两种分析模式

| 模式 | 触发方式 | 输入 | 说明 |
|------|---------|------|------|
| **模式 A — Git Diff 批量分析** | `Tools → CodeSense AI → 分析变更影响范围` 或快捷键 `Ctrl+Shift+I` | 选择对比分支或时间范围 | 自动发现所有被修改方法，批量分析调用链和影响范围 |
| **模式 B — 指定方法分析** | 编辑器中右键 → `CodeSense AI → 分析此方法影响范围` 或快捷键 `Ctrl+Shift+M` | 光标所在的方法 | 直接分析该方法的完整上下游调用链 |

#### 4.3.2 用户 UI 操作步骤

**模式 A — Git Diff 批量分析：**

```
1️⃣  点击顶部菜单 → Tools → CodeSense AI → 分析变更影响范围
    （或使用快捷键 Ctrl+Shift+I）

2️⃣  弹出配置对话框：
    ┌──────────────────────────────────────────┐
    │  📊 影响范围分析                          │
    │                                          │
    │  对比方式:  ○ 分支对比   ○ 时间范围       │
    │                                          │
    │  ── 分支对比 ──                           │
    │  当前分支:  [feature/user-refactor  ▾]    │
    │  目标分支:  [main                  ▾]    │
    │                                          │
    │  ── 或 时间范围 ──                        │
    │  开始时间:  [2026-04-01 00:00     ]      │
    │  结束时间:  [2026-04-03 14:00     ]      │
    │                                          │
    │  ── 分析选项 ──                           │
    │  最大追溯深度:  [10       ]               │
    │  ☑ 包含代码片段                           │
    │  ☑ 使用 AI 生成风险评估                   │
    │  ☐ 仅分析 Java/Kotlin 文件                │
    │                                          │
    │          [取消]        [开始分析]          │
    └──────────────────────────────────────────┘

3️⃣  底部状态栏显示进度：
    「CodeSense AI: 正在分析... 已发现 5 个修改方法，正在追溯调用链 (3/5)」

4️⃣  分析完成后，右侧侧边栏自动打开 "影响分析报告" Tab
    工具栏: [📋 复制 Markdown]  [💾 导出文件]  [🔄 刷新]

5️⃣  点击 [💾 导出文件] → 选择保存路径 → 生成 .md 文件
```

**模式 B — 指定方法分析：**

```
1️⃣  在编辑器中，将光标放在某个方法名上
    例如光标在 "updateUserBalance" 上

    public void updateUserBalance(Long userId, BigDecimal amount) {
                 ^^^^^^^^^^^^^^^^  ← 光标在此
        ...
    }

2️⃣  右键 → CodeSense AI → 分析此方法的影响范围
    （或使用快捷键 Ctrl+Shift+M）

3️⃣  弹出轻量确认弹窗：
    ┌──────────────────────────────────────┐
    │  🔍 分析方法影响范围                  │
    │                                      │
    │  方法: UserService.updateUserBalance  │
    │  分析方向:  ○ 仅向上(谁调用了我)      │
    │            ○ 仅向下(我调用了谁)      │
    │            ● 双向(完整链路)          │
    │  最大深度:  [10    ]                 │
    │  ☑ 包含代码片段                      │
    │  ☑ 使用 AI 生成风险评估              │
    │                                      │
    │        [取消]       [开始分析]        │
    └──────────────────────────────────────┘

4️⃣  右侧侧边栏打开报告（与模式 A 同一个面板）

5️⃣  同样支持 [📋 复制] 和 [💾 导出]
```

#### 4.3.3 整体技术工作流

```
模式 A: Git Diff 批量分析                    模式 B: 指定方法分析

  菜单 / 快捷键触发                            编辑器中右键 / 快捷键
       │                                          │
       ▼                                          ▼
  ┌──────────┐                              ┌──────────┐
  │ 配置对话框 │                              │ 确认弹窗  │
  │ 选择分支   │                              │ 选择方向  │
  │ 选择时间   │                              │ 选择深度  │
  └────┬─────┘                              └────┬─────┘
       │                                          │
       ▼                                          ▼
  ┌──────────┐                              ┌──────────┐
  │ Git Diff  │                              │ 获取光标  │
  │ 获取变更   │                              │ 下PsiMethod│
  └────┬─────┘                              └────┬─────┘
       │                                          │
       ▼                                          │
  ┌──────────┐                                    │
  │ PSI 解析  │                                    │
  │ Diff→方法  │                                    │
  └────┬─────┘                                    │
       │                                          │
       ▼                                          ▼
  ┌────────────────────────────────────────────────┐
  │   Call Hierarchy 递归分析（两种模式共用）        │
  │   CallerMethodsTreeStructure  (向上)           │
  │   CalleeMethodsTreeStructure  (向下)           │
  └────────────────────┬───────────────────────────┘
                       │
                       ▼
                ┌──────────┐
                │ 入口点检测 │  ← @RequestMapping / @Scheduled /
                │ 路径提取   │     @DubboService / @RocketMQMessageListener 等
                └────┬─────┘
                     │
                     ▼
                ┌──────────┐
                │ LLM 分析  │  ← 将调用链 + 代码片段发给 AI
                │ 风险评估   │  ← 生成风险等级和建议
                └────┬─────┘
                     │
                     ▼
                ┌──────────┐
                │ 渲染报告  │  ← ToolWindow 面板 + 可导出 .md
                └──────────┘
```

#### 4.3.4 技术实现

```kotlin
class CallHierarchyAnalyzer(private val project: Project) {

    // 分析某个方法的完整调用层级（模式 B 核心）
    fun analyzeCallers(psiMethod: PsiMethod, maxDepth: Int = 10): CallTree

    // 分析某个方法的下游被调用方法
    fun analyzeCallees(psiMethod: PsiMethod, maxDepth: Int = 10): CallTree

    // 双向分析
    fun analyzeBidirectional(psiMethod: PsiMethod, maxDepth: Int = 10): BidirectionalCallTree

    // 结合 Git Diff，自动分析所有被修改方法的影响范围（模式 A 核心）
    suspend fun analyzeImpactFromDiff(diffs: List<FileDiff>): ImpactReport
}

data class MethodInfo(
    val className: String,          // 类名，如 "UserService"
    val methodName: String,         // 方法名，如 "updateUserBalance"
    val signature: String,          // 完整签名，如 "updateUserBalance(Long, BigDecimal)"
    val filePath: String,           // 文件路径
    val lineNumber: Int,            // 行号
    val packageName: String,        // 包名
    val annotations: List<String>,  // 方法上的注解列表
    val changeType: String? = null   // 变更类型（仅 Git Diff 模式）: MODIFIED / ADDED
)

data class CallTree(
    val method: MethodInfo,
    val callers: List<CallTree>,   // 递归结构
    val isEntryPoint: Boolean       // 是否是入口（Controller 等）
)

data class BidirectionalCallTree(
    val method: MethodInfo,
    val callerTree: CallTree,      // 向上调用链
    val calleeTree: CallTree       // 向下调用链
)

data class ImpactReport(
    val modifiedMethods: List<MethodInfo>,
    val callChains: List<CallTree>,
    val entryPoints: List<EntryPointInfo>,
    val aiSummary: String?           // LLM 生成的影响分析总结
)

data class EntryPointInfo(
    val method: MethodInfo,
    val type: EntryPointType,        // HTTP_API, DUBBO_RPC, SCHEDULED, MQ_LISTENER, EVENT_LISTENER
    val path: String?,               // HTTP: "POST /api/user/update", Dubbo: "UserAccountFacade.adjustBalance"
    val triggerCondition: String?    // 如 "cron=0 0 2 * * ?"
)

enum class EntryPointType {
    HTTP_API,          // Spring MVC: @RequestMapping, @GetMapping, @PostMapping 等
    DUBBO_RPC,         // Dubbo: @DubboService, @org.apache.dubbo.config.annotation.Service
    SCHEDULED,         // 定时任务: @Scheduled
    MQ_LISTENER,       // 消息消费: @RocketMQMessageListener, @KafkaListener
    EVENT_LISTENER     // 事件监听: @EventListener, @TransactionalEventListener
}
```

#### 4.3.5 入口点判定与 URL 路径提取

```kotlin
fun isEntryPoint(psiMethod: PsiMethod): Boolean {
    // 方法级别的入口注解
    val methodEntryAnnotations = setOf(
        // Spring MVC
        "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping",
        // 定时任务
        "Scheduled",
        // 消息队列
        "RocketMQMessageListener", "KafkaListener",
        // 事件监听
        "EventListener", "TransactionalEventListener",
        "PostConstruct"
    )
    val methodMatch = psiMethod.annotations.any { anno ->
        methodEntryAnnotations.any { anno.qualifiedName?.endsWith(it) == true }
    }
    if (methodMatch) return true

    // 类级别的入口注解（Dubbo）：如果方法所在类有 @DubboService，
    // 且方法是接口中声明的 public 方法，则视为 RPC 入口
    val containingClass = psiMethod.containingClass ?: return false
    val isDubboService = containingClass.annotations.any { anno ->
        val qn = anno.qualifiedName ?: ""
        qn.endsWith("DubboService") ||
        qn == "org.apache.dubbo.config.annotation.Service" ||
        qn == "com.alibaba.dubbo.config.annotation.Service"
    }
    if (isDubboService && psiMethod.isPublic() && isInterfaceMethod(psiMethod)) {
        return true
    }

    return false
}

// 判断方法是否来自接口定义（Dubbo RPC 方法必须是接口声明的）
fun isInterfaceMethod(psiMethod: PsiMethod): Boolean {
    val containingClass = psiMethod.containingClass ?: return false
    return containingClass.interfaces.any { iface ->
        iface.findMethodsByName(psiMethod.name, false).isNotEmpty()
    }
}

// 从注解中提取入口点详细信息
fun extractEntryPointInfo(psiMethod: PsiMethod): EntryPointInfo? {
    // 先检查方法自身的注解
    for (anno in psiMethod.annotations) {
        val name = anno.qualifiedName ?: continue
        when {
            // === Spring MVC ===
            name.endsWith("GetMapping") || name.endsWith("PostMapping") ||
            name.endsWith("PutMapping") || name.endsWith("DeleteMapping") ||
            name.endsWith("RequestMapping") -> {
                val httpMethod = when {
                    name.endsWith("GetMapping") -> "GET"
                    name.endsWith("PostMapping") -> "POST"
                    name.endsWith("PutMapping") -> "PUT"
                    name.endsWith("DeleteMapping") -> "DELETE"
                    else -> extractHttpMethod(anno) ?: "REQUEST"
                }
                val path = anno.findAttributeValue("value")?.text?.trim('"')
                val classPath = extractClassLevelPath(psiMethod.containingClass)
                return EntryPointInfo(
                    method = psiMethod.toMethodInfo(),
                    type = EntryPointType.HTTP_API,
                    path = "$httpMethod ${classPath}${path}",
                    triggerCondition = null
                )
            }
            // === 定时任务 ===
            name.endsWith("Scheduled") -> {
                val cron = anno.findAttributeValue("cron")?.text?.trim('"')
                val fixedRate = anno.findAttributeValue("fixedRate")?.text
                return EntryPointInfo(
                    method = psiMethod.toMethodInfo(),
                    type = EntryPointType.SCHEDULED,
                    path = null,
                    triggerCondition = cron?.let { "cron=$it" }
                        ?: fixedRate?.let { "fixedRate=${it}ms" }
                )
            }
            // === RocketMQ ===
            name.endsWith("RocketMQMessageListener") -> {
                val topic = anno.findAttributeValue("topic")?.text?.trim('"')
                val group = anno.findAttributeValue("consumerGroup")?.text?.trim('"')
                return EntryPointInfo(
                    method = psiMethod.toMethodInfo(),
                    type = EntryPointType.MQ_LISTENER,
                    path = null,
                    triggerCondition = "topic=$topic, group=$group"
                )
            }
            // === Kafka ===
            name.endsWith("KafkaListener") -> {
                val topics = anno.findAttributeValue("topics")?.text?.trim('"')
                return EntryPointInfo(
                    method = psiMethod.toMethodInfo(),
                    type = EntryPointType.MQ_LISTENER,
                    path = null,
                    triggerCondition = "topics=$topics"
                )
            }
        }
    }

    // === Dubbo RPC ===
    // 检查类级别的 @DubboService 注解
    val containingClass = psiMethod.containingClass ?: return null
    val dubboAnno = containingClass.annotations.firstOrNull { anno ->
        val qn = anno.qualifiedName ?: ""
        qn.endsWith("DubboService") ||
        qn == "org.apache.dubbo.config.annotation.Service" ||
        qn == "com.alibaba.dubbo.config.annotation.Service"
    }
    if (dubboAnno != null && psiMethod.isPublic() && isInterfaceMethod(psiMethod)) {
        // 提取 Dubbo 接口名，从实现类的 interfaces 中获取
        val interfaceName = containingClass.interfaces
            .firstOrNull { it.findMethodsByName(psiMethod.name, false).isNotEmpty() }
            ?.qualifiedName?.substringAfterLast('.') ?: "UnknownFacade"
        return EntryPointInfo(
            method = psiMethod.toMethodInfo(),
            type = EntryPointType.DUBBO_RPC,
            path = "$interfaceName.${psiMethod.name}",
            triggerCondition = null
        )
    }

    return null
}
```

#### 4.3.6 报告格式设计

> 以下为最终确认的报告格式规范，以两份 demo 为准：
> - 模式 A 基准：[demo-report-git-diff.md](docs/ai/demo-report-git-diff.md)
> - 模式 B 基准：[demo-report-single-method.md](docs/ai/demo-report-single-method.md)

---

**模式 A（Git Diff 批量分析）报告结构：**

| 序号 | 章节 | 内容要求 |
|------|------|---------|
| 头部 | 📊 影响范围分析报告 | 元信息表：分析模式、当前分支、目标分支、分析时间、变更文件/方法数、受影响入口点数、风险等级 |
| 一 | **变更概要** | 表格列出所有变更方法：文件名、方法签名、变更类型(MODIFIED/ADDED)、行号 |
| 二 | **调用链分析** | 按每个被修改方法分小节（2.1, 2.2...），每个方法包含：<br>• 向上调用链（树形，标注入口类型和路径）<br>• 向下调用链（树形，标注调用类型）<br>• 关键代码片段（diff 格式，红绿对比）<br>⚠️ **新增方法（ADDED）** 只展示调用链，**不展示代码**，用引用块简述功能 |
| 三 | **受影响入口点汇总** | 表格列：#、入口类型(🌐HTTP/⏰定时/📨MQ/🔗Dubbo)、类.方法、路径/触发条件、影响的变更方法、**AI 说明**、**人工说明**(留空) |
| 四 | **AI 风险评估** | 4.1 风险等级 + 概述<br>4.2 发现的问题（🐛潜在Bug + ⚡性能影响 + ✅正面评价）<br>4.3 建议措施表格（优先级 P0/P1/P2、建议、涉及方法、影响入口） |
| 五 | **分析元信息** | 表格：插件版本、LLM 模型、分析耗时、追溯深度、分析范围、报告生成时间 |

---

**模式 B（指定方法分析）报告结构：**

| 序号 | 章节 | 内容要求 |
|------|------|---------|
| 头部 | 🔍 方法影响范围分析报告 | 元信息表：分析模式、目标方法、所在文件、行号、分析方向、分析时间、上游调用者数、受影响入口点数、风险等级 |
| 一 | **目标方法详情** | 完整签名、注解信息、所在类信息（注入依赖）、**方法完整实现代码** |
| 二 | **向上调用链（谁调用了我）** | 全部链路（树形，标注入口类型）+ 向上调用链总结表（层级、调用者、入口类型、方式） |
| 三 | **受影响入口点汇总** | 表格列：#、入口类型(🌐HTTP/⏰定时/📨MQ/🔗Dubbo)、类.方法、路径/触发条件、调用链深度、**AI 说明**、**人工说明**(留空) |
| 四 | **关键调用者代码片段** | 仅展示直接调用者的代码（如 Controller 层），不展示下游方法代码 |
| 五 | **AI 分析与建议** | 5.1 方法职责分析<br>5.2 潜在风险（⚠️标记 + 代码示例）<br>5.3 建议措施表格（优先级 P0/P1/P2、建议、说明） |
| 六 | **分析元信息** | 表格：插件版本、LLM 模型、分析耗时、追溯深度、分析范围、向上链路数、报告生成时间 |

---

**两种模式的差异对照：**

| 维度 | 模式 A (Git Diff) | 模式 B (指定方法) |
|------|:-:|:-:|
| 报告标题 | 📊 影响范围分析报告 | 🔍 方法影响范围分析报告 |
| 变更概要表 | ✅ 列出所有变更方法 | ❌ 不需要 |
| 目标方法完整代码 | ❌ 不需要 | ✅ 完整实现 |
| 调用链展示 | 每个修改方法分别展示上下游 | 仅展示向上调用链 |
| 代码片段展示 | `diff` 格式（红绿对比） | 调用者代码（当前代码） |
| 新增方法处理 | 只展示调用链，**不展示代码** | 不适用 |
| 向下调用链 | ✅ 展示 | ❌ 不展示 |
| 入口点汇总表 | 含 AI 说明 + 人工说明 | 含 AI 说明 + 人工说明 |
| AI 评估 | 聚焦变更引入的问题 | 聚焦方法设计缺陷 |

**通用格式规则：**

1. **入口点类型图标**：`🌐` HTTP API、`⏰` 定时任务、`📨` MQ 消费、`🔗` Dubbo RPC
2. **调用链树形格式**：使用 `└──`、`├──` 缩进，入口行标注 `[HTTP]`/`[定时任务]`/`[MQ]`/`[Dubbo RPC]`，目标方法用 `★` 标记
3. **风险等级**：`🔴 高风险`、`⚠️ 中等风险`、`🟡 低风险`、`🟢 无风险`
4. **建议优先级**：`🔴 P0` 必须修复、`🟡 P1` 建议修复、`🟢 P2` 可选优化
5. **入口点汇总表必须包含 AI 说明列**（由 LLM 生成）和**人工说明列**（留空供人工填写）
6. **所有代码块必须指定语言**（`java`、`kotlin`、`diff`、`sql` 等）以支持语法高亮

#### 4.3.7 报告面板 UI 布局

```
┌─────────────────────────────────────────────────────────────────┐
│ [📊 影响分析]                                          [×]     │
│ ──────────────────────────────────────────────────────────────  │
│                                                                 │
│  工具栏:                                                        │
│  ┌────────────────────────────────────────────────────────┐     │
│  │ [📋 复制MD] [💾 导出.md] [🔄 重新分析] [⚙️ 设置]       │     │
│  └────────────────────────────────────────────────────────┘     │
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐     │
│  │                                                        │     │
│  │   （Markdown 渲染区域，支持滚动）                       │     │
│  │                                                        │     │
│  │   # 📊 影响范围分析报告                                │     │
│  │   | 分析模式 | Git Diff 分支对比 |                      │     │
│  │   ...                                                  │     │
│  │                                                        │     │
│  │   ## 二、调用链分析                                    │     │
│  │   [点击方法名可跳转到源码]                              │     │
│  │                                                        │     │
│  │   UserController.updateBalance()  ← 可点击跳转         │     │
│  │     └── UserService.updateUserBalance()                │     │
│  │                                                        │     │
│  └────────────────────────────────────────────────────────┘     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**面板交互功能：**

| 交互 | 说明 |
|------|------|
| 点击方法名 | 跳转到 IDEA 编辑器中的对应源码位置 |
| 📋 复制 MD | 将原始 Markdown 文本复制到剪贴板 |
| 💾 导出 .md | 弹出文件选择器，保存为 `.md` 文件 |
| 🔄 重新分析 | 用最新代码重新执行分析 |
| 代码块 | 语法高亮显示，支持一键复制 |

---

### 4.4 模块四：Agent 引擎（Tool Calling）

#### 功能描述
实现 ReAct（Reasoning + Acting）循环，让 LLM 具备自主使用工具的能力。

#### Agent 工具清单

| 工具名 | 功能 | 实现方式 |
|--------|------|---------|
| `read_file` | 读取项目中的文件内容 | `VirtualFileSystem` API |
| `write_file` | 写入/修改文件 | `WriteCommandAction` |
| `search_code` | 在项目中搜索代码 | `FindManager` / `PsiSearchHelper` |
| `run_command` | 执行终端命令 | `GeneralCommandLine` + `ProcessHandler` |
| `get_git_diff` | 获取 Git 变更 | `GitDiffService` |
| `analyze_call_hierarchy` | 分析方法调用链 | `CallHierarchyAnalyzer` |
| `get_file_structure` | 获取文件/类/方法结构 | PSI 遍历 |
| `list_files` | 列出目录结构 | `VirtualFile.children` |

#### Agent 执行循环
```kotlin
class AgentExecutor(
    private val llmClient: LlmClient,
    private val toolRegistry: ToolRegistry
) {
    suspend fun execute(userMessage: String, context: AgentContext): Flow<AgentEvent> = flow {
        val messages = mutableListOf<ChatMessage>()
        messages.add(ChatMessage.system(AGENT_SYSTEM_PROMPT))
        messages.add(ChatMessage.user(userMessage))
        
        // ReAct 循环
        while (true) {
            val response = llmClient.chatWithTools(
                ChatRequest(messages = messages, tools = toolRegistry.definitions)
            )
            
            if (response.hasToolCalls()) {
                // 执行工具调用
                for (toolCall in response.toolCalls) {
                    emit(AgentEvent.ToolCallStart(toolCall))
                    val result = toolRegistry.execute(toolCall)
                    emit(AgentEvent.ToolCallResult(toolCall, result))
                    messages.add(ChatMessage.toolResult(toolCall.id, result))
                }
                messages.add(response.assistantMessage)
            } else {
                // 最终回复，退出循环
                emit(AgentEvent.FinalAnswer(response.content))
                break
            }
        }
    }
}
```

---

### 4.5 模块五：UI 界面

#### 5.1 侧边栏 Chat（ToolWindow）

使用 JCEF（内嵌 Chromium）渲染 Chat UI，以获得丰富的 Markdown 渲染和代码高亮效果：

```kotlin
class ChatToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatPanel = ChatPanel(project)
        val content = ContentFactory.getInstance()
            .createContent(chatPanel, "CodeSense AI", false)
        toolWindow.contentManager.addContent(content)
    }
}
```

**Chat UI 核心功能：**
- 对话输入框（支持输入代码片段）
- 流式显示 LLM 响应（Markdown 渲染 + 代码高亮）
- 工具调用状态实时展示（正在读取文件...、正在执行命令...）
- 代码块支持一键插入/替换到编辑器
- 对话历史管理

#### 5.2 右键菜单 Actions

| Action | 触发方式 | 功能 |
|--------|---------|------|
| `Review This File` | 编辑器右键 | Review 当前文件用 LLM 审查 |
| `Review Git Changes` | VCS 菜单 / 工具栏 | 获取整体 diff 并审查 |
| `Analyze Impact` | 方法上右键 | 分析当前方法的影响范围 |
| `Explain Code` | 选中代码右键 | 用 LLM 解释选中的代码 |
| `Ask CodeSense` | 选中代码右键 | 带上选中代码到 Chat 对话框 |

#### 5.3 设置页面（模型配置）

**UI 原型：**

```
┌─────────────────────────────────────────────────────────────────┐
│  Settings > Tools > CodeSense AI                              │
│ ─────────────────────────────────────────────────────────────── │
│                                                               │
│  ★ 模型配置                                                    │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  默认模型:  [★ MiniMax - abab6.5s-chat          ▾]         │ │
│  │                                                         │ │
│  │  已配置的模型提供者:                                     │ │
│  │  ┌───────────────────────────────────────────────┐ │ │
│  │  │ ★ MiniMax          abab6.5s-chat     ✔ 已验证  │ │ │
│  │  │   GLM              glm-4             ✔ 已验证  │ │ │
│  │  │   深度求索(DeepSeek) deepseek-chat      ✖ 未验证 │ │ │
│  │  └───────────────────────────────────────────────┘ │ │
│  │  [➕ 添加] [✏️ 编辑] [✖ 删除] [★ 设为默认] [🔗 测试连接]  │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
│  ★ 功能设置                                                    │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  ☑ 启用代码审查                                          │ │
│  │  ☑ 启用影响分析                                          │ │
│  │  ☑ 分析时使用 AI 生成风险评估                            │ │
│  │  审查最大文件数:  [20      ]                               │ │
│  │  调用链最大追溯深度:  [10      ]                          │ │
│  └─────────────────────────────────────────────────────────┘ │
│                                                               │
│                            [Apply]  [OK]  [Cancel]            │
└─────────────────────────────────────────────────────────────────┘
```

**点击 [➕ 添加] 或 [✏️ 编辑] 时弹出的模型配置对话框：**

```
┌──────────────────────────────────────────┐
│  添加模型提供者                            │
│                                          │
│  提供者类型: [MiniMax               ▾]    │
│    └ 可选: MiniMax / GLM / DeepSeek /     │
│           通义千问 / 自定义OpenAI兼容       │
│                                          │
│  显示名称: [MiniMax              ]        │
│  Base URL: [https://api.minimax.io/v1]   │
│  API Key:  [**********************]      │
│  模型名称: [abab6.5s-chat         ]        │
│                                          │
│  ── 高级选项 ──                           │
│  最大 Token: [4096    ]                  │
│  Temperature: [0.7    ]                  │
│  ☑ 支持 Tool Calling                     │
│  ☑ 支持流式输出                            │
│                                          │
│        [🔗 测试连接]                      │
│        [取消]     [保存]                 │
└──────────────────────────────────────────┘
```

**选择提供者类型时自动填充默认值：**

| 提供者类型 | 默认 Base URL | 默认模型名 | 说明 |
|-----------|-------------|---------|------|
| MiniMax | `https://api.minimax.io/v1` | `abab6.5s-chat` | 国产主流，性价比高 |
| GLM (智谱) | `https://open.bigmodel.cn/api/paas/v4` | `glm-4` | 国产主流，Tool Calling 强 |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` | 代码能力突出 |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus` | 阿里云生态 |
| 自定义 | (用户填写) | (用户填写) | 任何 OpenAI 兼容服务 |

**实现代码：**

```kotlin
class CodeSenseSettingsConfigurable : BoundConfigurable("CodeSense AI") {
    override fun createPanel() = panel {
        group("模型配置") {
            row("默认模型:") {
                comboBox(settings.getProviderDisplayNames())
                    .bindItem(settings::defaultProviderId)
                    .comment("分析和审查时默认使用此模型")
            }
            row {
                // 模型提供者列表（JBTable）
                cell(providerListPanel)
                    .resizableColumn()
                    .align(AlignX.FILL)
            }
            row {
                button("添加") { showAddProviderDialog() }
                button("编辑") { showEditProviderDialog() }
                button("删除") { removeSelectedProvider() }
                button("设为默认") { setAsDefault() }
                button("测试连接") { testConnection() }
            }
        }
        group("功能设置") {
            row { checkBox("启用代码审查") }
            row { checkBox("启用影响分析") }
            row { checkBox("分析时使用 AI 生成风险评估") }
            row("审查最大文件数:") { intTextField(1..100).text("20") }
            row("调用链最大追溯深度:") { intTextField(1..20).text("10") }
        }
    }
}
```

---

## 五、项目结构

```
codesense-ai-plugin/
├── build.gradle.kts                 # Gradle 构建脚本
├── gradle.properties                # IDE 版本、插件元数据配置
├── settings.gradle.kts
├── src/
│   └── main/
│       ├── kotlin/com/deeptek/ai/idea/
│       │   ├── CodeSensePlugin.kt           # 插件入口
│       │   │
│       │   ├── llm/                         # === LLM 客户端层 ===
│       │   │   ├── LlmProvider.kt           # 统一接口
│       │   │   ├── LlmClient.kt             # HTTP 客户端
│       │   │   ├── models.kt                # 数据模型
│       │   │   └── providers/
│       │   │       ├── MiniMaxProvider.kt
│       │   │       ├── GlmProvider.kt
│       │   │       └── OpenAiCompatProvider.kt
│       │   │
│       │   ├── git/                         # === Git 服务层 ===
│       │   │   ├── GitDiffService.kt        # Diff 获取
│       │   │   └── models.kt               # FileDiff 等数据类
│       │   │
│       │   ├── analysis/                    # === 代码分析层 ===
│       │   │   ├── CallHierarchyAnalyzer.kt # 调用链分析
│       │   │   ├── EntryPointDetector.kt    # 入口点检测
│       │   │   ├── ReportGenerator.kt       # Markdown 报告生成
│       │   │   └── models.kt               # MethodInfo, CallTree, ImpactReport
│       │   │
│       │   ├── agent/                       # === Agent 引擎 ===
│       │   │   ├── AgentExecutor.kt         # ReAct 执行循环
│       │   │   ├── ToolRegistry.kt          # 工具注册表
│       │   │   ├── AgentContext.kt          # 上下文管理
│       │   │   └── tools/                   # 具体工具实现 (8 个)
│       │   │       ├── ReadFileTool.kt
│       │   │       ├── WriteFileTool.kt
│       │   │       ├── SearchCodeTool.kt
│       │   │       ├── RunCommandTool.kt
│       │   │       ├── GitDiffTool.kt
│       │   │       ├── CallHierarchyTool.kt
│       │   │       ├── FileStructureTool.kt
│       │   │       └── ListFilesTool.kt
│       │   │
│       │   ├── review/                      # === 代码审查 ===
│       │   │   ├── CodeReviewService.kt     # 审查服务
│       │   │   └── ReviewPromptBuilder.kt   # Prompt 构造
│       │   │
│       │   ├── ui/                          # === UI 层 ===
│       │   │   ├── chat/
│       │   │   │   ├── ChatToolWindowFactory.kt
│       │   │   │   ├── ChatPanel.kt
│       │   │   │   └── web/                 # JCEF Chat 前端资源
│       │   │   │       ├── index.html
│       │   │   │       ├── chat.css
│       │   │   │       └── chat.js
│       │   │   ├── review/
│       │   │   │   └── ReviewResultPanel.kt
│       │   │   └── impact/
│       │   │       └── ImpactResultPanel.kt
│       │   │
│       │   ├── actions/                     # === IDE Actions ===
│       │   │   ├── ReviewDiffAction.kt
│       │   │   ├── ReviewFileAction.kt
│       │   │   ├── AnalyzeImpactAction.kt
│       │   │   ├── ExplainCodeAction.kt
│       │   │   └── AskCodeSenseAction.kt
│       │   │
│       │   └── settings/                    # === 设置 ===
│       │       ├── CodeSenseSettings.kt     # 持久化配置
│       │       └── CodeSenseSettingsConfigurable.kt
│       │
│       └── resources/
│           ├── META-INF/
│           │   └── plugin.xml               # 插件注册配置
│           └── messages/
│               └── CodeSenseBundle.properties # 国际化字符串
│
└── src/test/kotlin/                         # 单元测试
```

---

## 六、开发路线图（分阶段）

### Phase 1：基础骨架 (Week 1-2)
- [ ] 使用 JetBrains 模板初始化 Gradle 项目
- [ ] 配置 `plugin.xml`、`build.gradle.kts`
- [ ] 实现 Settings 页面（API Key、模型选择）
- [ ] 实现 LLM 客户端层（MiniMax + GLM Provider）
- [ ] 实现最简 Chat ToolWindow（纯 Swing 文本区域）
- [ ] 验证：能在 IDEA sandbox 中加载插件并成功调用 LLM

### Phase 2：Git 代码审查 (Week 3-4)
- [ ] 实现 `GitDiffService`（分支对比 + 时间对比）
- [ ] 实现 `CodeReviewService` + Prompt 构造器
- [ ] 实现 `ReviewDiffAction` 右键菜单
- [ ] 实现审查结果展示面板
- [ ] 验证：能对当前分支 vs main 的 diff 进行 AI 审查

### Phase 3：影响范围分析 (Week 5-6)
- [ ] 实现 `CallHierarchyAnalyzer`（PSI 分析）
- [ ] 实现 `EntryPointDetector`
- [ ] 实现 `AnalyzeImpactAction`
- [ ] 实现影响范围结果面板（树形展示调用链）
- [ ] 与 LLM 集成：生成影响分析报告
- [ ] 验证：能对修改方法自动生成调用链和影响分析

### Phase 4：Agent 引擎 (Week 7-8)
- [ ] 实现 `ToolRegistry` 和工具接口
- [ ] 实现 8 个核心工具
- [ ] 实现 `AgentExecutor` ReAct 循环
- [ ] 升级 Chat UI 以展示工具调用过程
- [ ] 验证：能在 Chat 中让 AI 自主读文件、搜代码、执行命令

### Phase 5：UI 打磨 + 上线 (Week 9-10)
- [ ] 使用 JCEF 升级 Chat UI（Markdown 渲染、代码高亮）
- [ ] 代码解释功能 (`ExplainCodeAction`)
- [ ] 对话历史持久化
- [ ] 错误处理和用户体验优化
- [ ] 编写 `plugin.xml` Marketplace 描述
- [ ] 打包发布到 JetBrains Marketplace

---

## 七、关键技术要点

### 7.1 线程模型
```
⚠️ 关键规则：
- PSI 读取 → 必须在 ReadAction 中
- PSI 写入 → 必须在 WriteCommandAction 中
- HTTP 网络请求 → 必须在后台线程（Coroutines IO Dispatcher）
- UI 更新 → 必须在 EDT（invokeLater 或 Dispatchers.EDT）
```

### 7.2 模型管理与默认选择机制

#### 数据持久化模型

```kotlin
@State(
    name = "CodeSenseSettings",
    storages = [Storage("codesense-ai.xml")]
)
class CodeSenseSettings : PersistentStateComponent<CodeSenseSettings.State> {

    data class State(
        var defaultProviderId: String = "minimax-default",  // 默认模型 ID
        var providers: MutableList<ProviderConfig> = mutableListOf(
            // 预置默认配置（用户只需填 API Key 即可使用）
            ProviderConfig(
                id = "minimax-default",
                displayName = "MiniMax",
                type = ProviderType.MINIMAX,
                baseUrl = "https://api.minimax.io/v1",
                modelName = "abab6.5s-chat",
                apiKey = ""
            ),
            ProviderConfig(
                id = "glm-default",
                displayName = "GLM (智谱)",
                type = ProviderType.GLM,
                baseUrl = "https://open.bigmodel.cn/api/paas/v4",
                modelName = "glm-4",
                apiKey = ""
            )
        ),
        var enableCodeReview: Boolean = true,
        var enableImpactAnalysis: Boolean = true,
        var enableAiRiskAssessment: Boolean = true,
        var maxReviewFiles: Int = 20,
        var maxCallHierarchyDepth: Int = 10
    )

    // 获取默认模型配置（分析/审查时直接调用）
    fun getDefaultProvider(): ProviderConfig {
        return state.providers.find { it.id == state.defaultProviderId }
            ?: state.providers.firstOrNull()
            ?: throw IllegalStateException("请先在 Settings 中配置至少一个模型提供者")
    }
}

data class ProviderConfig(
    var id: String = UUID.randomUUID().toString(),
    var displayName: String = "",
    var type: ProviderType = ProviderType.MINIMAX,
    var baseUrl: String = "",
    var apiKey: String = "",
    var modelName: String = "",
    var maxTokens: Int = 4096,
    var temperature: Double = 0.7,
    var supportToolCalling: Boolean = true,
    var supportStreaming: Boolean = true
)

enum class ProviderType(val displayName: String, val defaultBaseUrl: String, val defaultModel: String) {
    MINIMAX("MiniMax", "https://api.minimax.io/v1", "abab6.5s-chat"),
    GLM("GLM (智谱)", "https://open.bigmodel.cn/api/paas/v4", "glm-4"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    QWEN("通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
    CUSTOM("自定义 (OpenAI 兼容)", "", "")
}
```

#### API 差异处理

| 特性 | MiniMax | GLM | DeepSeek | 通义千问 |
|------|---------|-----|---------|--------|
| Base URL | `api.minimax.io/v1` | `open.bigmodel.cn/api/paas/v4` | `api.deepseek.com/v1` | `dashscope.aliyuncs.com/compatible-mode/v1` |
| 认证方式 | `Bearer {key}` | `Bearer {key}` | `Bearer {key}` | `Bearer {key}` |
| Chat 接口 | `/chat/completions` | `/chat/completions` | `/chat/completions` | `/chat/completions` |
| Tool Calling | ✅ | ✅ | ✅ | ✅ |
| 流式输出 | ✅ SSE | ✅ SSE | ✅ SSE | ✅ SSE |
| OpenAI 兼容性 | 高 | 高 | 高 | 高 |

> 所有提供者都高度兼容 OpenAI 格式，统一用 `OpenAiCompatProvider` 通过不同 `baseUrl` 和 `apiKey` 适配。

#### 分析时的默认模型使用流程

```
用户触发“分析影响范围”
     │
     ▼
┌────────────────────────────────┐
│ CodeSenseSettings                │
│   .getDefaultProvider()          │
└───────────┬────────────────────┘
            │
            ▼
     ┌────────────┐
     │ apiKey 为空? │
     └───┬────┬────┘
    Yes     No
     │      │
     ▼      ▼
┌───────┐  ┌──────────────────┐
│ 弹窗提示 │  │ 直接使用默认模型   │
│ 去设置  │  │ 进行 LLM 调用      │
└───────┘  │ 无需用户再次选择  │
           └──────────────────┘
```

> **核心原则**：用户在 Settings 中配置好模型并设定默认后，后续所有分析/审查操作自动使用默认模型，无需每次手动选择。

### 7.3 `plugin.xml` 核心配置
```xml
<idea-plugin>
    <id>com.deeptek.ai.idea</id>
    <name>CodeSense AI</name>
    <vendor>chenp</vendor>
    
    <depends>com.intellij.modules.platform</depends>
    <depends>Git4Idea</depends>            <!-- Git 功能依赖 -->
    <depends>com.intellij.java</depends>   <!-- Java PSI 依赖 -->
    
    <extensions defaultExtensionNs="com.intellij">
        <!-- Chat 侧边栏 -->
        <toolWindow id="CodeSense AI" 
                    anchor="right"
                    factoryClass="com.deeptek.ai.idea.ui.chat.ChatToolWindowFactory"
                    icon="/icons/codesense.svg"/>
        
        <!-- 设置页 -->
        <applicationConfigurable 
            instance="com.deeptek.ai.idea.settings.CodeSenseSettingsConfigurable"
            displayName="CodeSense AI"/>
        
        <!-- 持久化存储 -->
        <applicationService 
            serviceImplementation="com.deeptek.ai.idea.settings.CodeSenseSettings"/>
        <projectService 
            serviceImplementation="com.deeptek.ai.idea.git.GitDiffService"/>
        <projectService 
            serviceImplementation="com.deeptek.ai.idea.analysis.CallHierarchyAnalyzer"/>
    </extensions>
    
    <actions>
        <!-- 右键菜单 Actions -->
        <group id="CodeSense.EditorPopupMenu" text="CodeSense AI">
            <add-to-group group-id="EditorPopupMenu" anchor="last"/>
            <action id="CodeSense.ExplainCode" 
                    class="com.deeptek.ai.idea.actions.ExplainCodeAction"
                    text="解释代码" icon="/icons/explain.svg"/>
            <action id="CodeSense.ReviewFile" 
                    class="com.deeptek.ai.idea.actions.ReviewFileAction"
                    text="审查此文件" icon="/icons/review.svg"/>
            <action id="CodeSense.AnalyzeImpact" 
                    class="com.deeptek.ai.idea.actions.AnalyzeImpactAction"
                    text="分析影响范围" icon="/icons/impact.svg"/>
        </group>
    </actions>
</idea-plugin>
```

---

## 八、验证计划

### 自动化测试
- LLM 客户端层：Mock HTTP 响应，测试序列化/反序列化
- Agent 工具：单元测试每个 Tool 的执行逻辑
- 使用 `intellij-platform-gradle-plugin` 的 `test` 任务运行插件集成测试

### 手动验证
- 在 IDEA sandbox 中加载插件
- 配置 MiniMax / GLM API Key
- 打开一个有 Git 历史的 Java/Kotlin 项目
- 验证代码审查、影响分析、Chat Agent 各功能
