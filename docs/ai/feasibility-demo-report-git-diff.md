# 📊 Demo 报告各章节 — IDEA 插件可行性分析

> 文档编号: `feasibility-demo-report-git-diff`
> 创建时间: 2026-04-14
> 对标文档: `docs/demo-report-git-diff.md`

---

## 总览

将 `demo-report-git-diff.md` 中的报告拆解为 5 大章节，逐一评估是否可通过 IDEA 插件开发实现。

| # | 报告章节 | 能否插件实现 | 实现方式 | 现有代码基础 | 复杂度 |
|---|---------|:-----------:|---------|------------|:------:|
| 1 | 头部元信息（分支、时间、统计） | ✅ 可以 | PSI + Git API，纯本地 | ✅ 已有 | 低 |
| 2 | 一、变更概要 | ✅ 可以 | Git Diff → PSI 解析方法 | ⚠️ 部分有 | 中 |
| 3 | 二、调用链分析 | ✅ 可以 | IDEA Call Hierarchy API | ✅ 已有 | 中 |
| 4 | 三、受影响入口点汇总 | ✅ 可以 | 注解检测 + 调用链终点 | ✅ 已有 | 低 |
| 5 | 四、AI 风险评估 | ✅ 可以 | LLM API 调用 | ✅ 已有 | 中 |
| 6 | 五、分析元信息 | ✅ 可以 | 本地数据拼装 | ✅ 已有 | 低 |

**结论：Demo 报告中的所有章节均可通过 IDEA 插件实现，且大部分功能已有代码基础。**

---

## 一、头部元信息 — ✅ 已实现

### Demo 中的内容
```markdown
| **分析模式** | Git Diff 分支对比 |
| **当前分支** | `feature/user-balance-refactor` |
| **目标分支** | `main` |
| **分析时间** | 2026-04-03 14:30:22 |
| **变更文件数** | 6 |
| **变更方法数** | 7 |
| **受影响入口点** | 8 |
| **风险等级** | ⚠️ 中等风险 |
```

### 现有代码基础
- `ReportGenerator.generateGitDiffReport()` — 已实现全部字段输出
- `GitDiffService.getCurrentBranch()` — 获取当前分支
- `GitDiffService.getBranchDiff()` — 获取分支间变更文件
- 风险等级由 `inferRiskLevel()` 自动推断

### 差距
- **变更文件数**：当前只输出变更方法数，缺少独立的"变更文件数"统计 → 需要从 `FileDiff` 列表中去重统计
- 基本无缺口，改动极小

---

## 二、变更概要 — ⚠️ 需要补充 Diff 到方法的映射

### Demo 中的内容
```markdown
| # | 文件 | 方法签名 | 变更类型 | 行号 |
|---|------|---------|---------|------|
| 1 | `UserService.java` | `updateUserBalance(Long, BigDecimal)` | MODIFIED | L45-L78 |
| 5 | `NotifyService.java` | `sendBalanceNotify(Long, BigDecimal, BigDecimal)` | ADDED | L1-L42 |
```

### 现有代码基础
- `ReportGenerator` 的变更概要表已实现（第 40-51 行）
- `GitDiffService.getBranchDiff()` 可获取每个文件的 old/new 内容
- `AnalyzeImpactAction` 已走通"PSI 解析 → 方法提取"的流程

### 差距与实现方案

**核心问题：如何从 Git Diff 中精确定位"哪些方法被修改了"？**

当前插件已有 `GitDiffService` 获取文件级别 diff，但缺少 **Diff → 方法级别映射** 的中间模块。需要开发：

```
Git Diff (文件级)
  → PSI 解析新旧文件 (PsiJavaFile / KtFile)
  → 对比新旧 PsiMethod 列表
  → 输出：MethodInfo + changeType(MODIFIED/ADDED/DELETED) + 行号范围
```

**实现方式：**
1. 对 `FileDiff.newContent` 和 `FileDiff.oldContent` 分别用 `PsiFileFactory` 创建临时 PsiFile
2. 遍历两个 PsiFile 中的 `PsiMethod`，按方法签名做 diff 比对
3. 方法签名相同但 body 不同 → MODIFIED；只在新文件有 → ADDED；只在旧文件有 → DELETED
4. 行号范围从 PsiMethod 的 `textRange` 计算

**IDEA PSI API 支持度：** ✅ 完全支持
- `PsiFileFactory.createFileFromText()` — 从字符串创建临时 PsiFile
- `PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java)` — 提取方法
- `JvmPsiConversionHelper` — Kotlin 文件方法提取

**复杂度：中等。** 需要新建一个 `DiffMethodExtractor` 工具类。

---

## 三、调用链分析 — ✅ 已实现

### Demo 中的内容

```
■ 向上调用链（谁调用了我）：
链路 1:
[HTTP] POST /api/user/balance/update
  └── UserController.updateBalance(BalanceUpdateRequest)           ← @PostMapping
        └── ★ UserService.updateUserBalance(Long, BigDecimal)     ← 被修改方法

■ 向下调用链（我调用了谁）：
★ UserService.updateUserBalance(Long, BigDecimal)
  ├── UserDao.selectById(Long)                                     ← MyBatis 查询
  ├── UserDao.updateBalance(UserEntity)                            ← MyBatis 更新
  └── NotifyService.sendBalanceNotify(Long, BigDecimal, BigDecimal)← 新增调用

■ 关键代码片段（diff 对比）
```

### 现有代码基础

已经完整实现的 ✅：
- `CallHierarchyAnalyzer.analyzeCallers()` — 利用 IDEA 的 `CallerMethodsTreeStructure` 递归追溯上游调用者
- `CallHierarchyAnalyzer.analyzeCallees()` — 利用 IDEA 的 `CalleeMethodsTreeStructure` 递归追溯下游被调用者
- `CallHierarchyAnalyzer.analyzeBidirectional()` — 双向分析
- `ReportGenerator.renderCallerTree()` — 树形渲染向上链路
- `ReportGenerator.renderCalleeTree()` — 树形渲染向下链路
- `EntryPointDetector` — 自动识别入口类型（HTTP/Scheduled/MQ/Dubbo/EventListener）

### 差距

| 功能 | Demo 中的效果 | 当前实现 | 差距 |
|------|-------------|---------|------|
| 向上调用链 | 多条链路，显示注解标记 | ✅ 已实现 | 无 |
| 向下调用链 | 树形展示 | ✅ 已实现 | 无 |
| 入口类型识别 | HTTP/定时任务/MQ/Dubbo RPC | ✅ 已实现 | 无 |
| 注解旁注（← @PostMapping） | 每个节点旁标注注解 | ⚠️ 有 annotations 字段，渲染时未展示 | 小改动 |
| 关键代码片段（diff 对比） | 展示方法级别的 diff | ❌ 未实现 | 需新增 |

**关键代码片段** 的实现方案：
1. 从 `FileDiff` 的 `oldContent` / `newContent` 中，按方法行号范围截取代码
2. 做行级 diff 生成 `+` / `-` 标记
3. 可以用简单的 LCS（最长公共子序列）算法，或使用 `com.intellij.diff.comparison.ComparisonManager`（IDEA 内置 diff 引擎）

**IDEA API：** ✅ `ComparisonManager.getInstance().compareLinesInner()` 可直接使用

---

## 四、受影响入口点汇总 — ✅ 已实现

### Demo 中的内容
```markdown
| # | 入口类型 | 类.方法 | 路径/触发条件 | 影响的变更方法 | AI 说明 | 人工说明 |
|---|---------|--------|-------------|--------------|--------|--------|
| 1 | 🌐 HTTP API | UserController.updateBalance() | POST /api/user/balance/update | updateUserBalance() | ... | |
| 6 | ⏰ 定时任务 | SettlementJob.dailySettle() | @Scheduled(cron="0 0 2 * * ?") | updateUserBalance() | ... | |
| 7 | 📨 MQ 消费 | RefundOrderListener.onMessage() | topic: REFUND_ORDER_TOPIC | updateUserBalance() | ... | |
| 8 | 🔗 Dubbo RPC | UserAccountFacadeImpl.adjustBalance() | @DubboService | updateUserBalance() | ... | |
```

### 现有代码基础

完全覆盖 ✅：
- `EntryPointDetector.isEntryPoint()` — 判断是否为入口
- `EntryPointDetector.extractEntryPointInfo()` — 提取入口详细信息
- `EntryPointType` 枚举覆盖了 HTTP_API / DUBBO_RPC / SCHEDULED / MQ_LISTENER / EVENT_LISTENER / OTHER
- `ReportGenerator.generateEntryPointTable()` — 已实现表格渲染
- 已有 AI 说明三层 fallback 逻辑（代码注释 → AI 生成 → 待生成）

### 差距
- **"影响的变更方法"列**：Demo 中明确标出该入口点影响了哪个变更方法（如 `updateUserBalance()`），当前 `EntryPointInfo.affectedMethods` 字段已定义但未被填充 → 需在分析时记录关联关系
- **"人工说明"列**：Demo 中预留了人工补充说明栏 → 目前未实现，属于 UI 交互功能（可后续添加可编辑单元格）

---

## 五、AI 风险评估 — ✅ 已实现

### Demo 中的内容
```markdown
### 4.1 风险等级: ⚠️ 中等风险
### 4.2 发现的问题
  🐛 潜在 Bug（并发扣款、缓存不一致、通知异常）
  ⚡ 性能影响
  ✅ 正面评价
### 4.3 建议措施（优先级表格）
```

### 现有代码基础

✅ 已实现：
- `AnalyzeImpactAction` 中已有完整的 AI 双重异步请求（方案 B）
  - 异步请求 1：各入口点的短评
  - 异步请求 2：全局风险评估（流式输出）
- `buildAiRiskPrompt()` — 构建风险评估提示词
- `buildEntryPointPrompt()` — 构建入口点短评提示词
- `report.aiSummary` 字段支持流式更新
- `ImpactResultPanel` 支持流式刷新 Markdown

### 差距
- 当前 Prompt 要求 AI 自行决定输出格式，Demo 中的输出结构更精确（分 Bug/性能/正面三段 + 表格）
- → 优化 Prompt 使 AI 按 Demo 格式输出即可，无需代码大改

---

## 六、整体实现路线图

### 现有模块和 Demo 报告的映射

```
Demo 报告章节               已有代码模块                       状态
─────────────────         ─────────────────────────────     ──────
头部元信息                 ReportGenerator                    ✅ 完成
一、变更概要               GitDiffService + ReportGenerator   ⚠️ 需补充 DiffMethodExtractor
二、调用链分析
  ├─ 向上调用链            CallHierarchyAnalyzer              ✅ 完成
  ├─ 向下调用链            CallHierarchyAnalyzer              ✅ 完成
  ├─ 入口类型识别          EntryPointDetector                 ✅ 完成
  └─ 关键代码片段(diff)    —                                  ❌ 需新建
三、受影响入口点           EntryPointDetector + ReportGen     ✅ 基本完成
四、AI 风险评估            LlmProvider + AnalyzeImpactAction  ✅ 完成
五、分析元信息             ReportGenerator                    ✅ 完成
```

### 需要新开发的模块

| 优先级 | 模块 | 说明 | 复杂度 |
|:-----:|------|------|:-----:|
| 🔴 P0 | `DiffMethodExtractor` | 将 Git Diff 的文件级变更映射到方法级变更，提取 MethodInfo + 行号范围 | 中 |
| 🟡 P1 | 代码片段 diff 渲染 | 为每个变更方法展示新旧代码 diff（+/- 标记） | 中 |
| 🟡 P1 | `ReviewGitChangesAction` 增强 | 当前只做 Code Review，需增加"影响范围分析"模式 | 中 |
| 🟢 P2 | Prompt 优化 | 让 AI 输出更符合 Demo 报告的结构化格式 | 低 |
| 🟢 P2 | 入口点关联 | 填充 `EntryPointInfo.affectedMethods`，显示每个入口影响了哪个变更方法 | 低 |
| 🟢 P2 | 注解旁注渲染 | 调用链中每个节点旁显示 `← @PostMapping` 等注解标记 | 低 |

### 总工作量估算

| 类别 | 占比 | 说明 |
|-----|:----:|------|
| 已完成 | ~65% | 调用链、入口点、AI 评估、报告框架、UI 面板 |
| 需补充 | ~25% | DiffMethodExtractor + diff 代码片段 + Action 整合 |
| 需优化 | ~10% | Prompt 调优 + 报告格式打磨 + 注解旁注 |

---

## 七、结论

> **Demo 报告（`demo-report-git-diff.md`）中的所有功能均可通过 IDEA 插件实现。**

1. **变更概要** ✅ — 通过 `GitDiffService` + PSI 解析，将 Git Diff 映射到方法级别，核心是新增 `DiffMethodExtractor`
2. **调用链分析** ✅ — 完全依赖 IDEA 内置的 `CallerMethodsTreeStructure` / `CalleeMethodsTreeStructure` API，已实现
3. **入口点检测** ✅ — 基于注解匹配（HTTP/定时任务/MQ/Dubbo），已实现
4. **AI 风险评估** ✅ — 通过 LLM API 调用，已实现流式输出
5. **代码 Diff 片段** ⚠️ — 需要新增，可利用 IDEA 的 `ComparisonManager` 或简单行级 diff
6. **注解旁注** ⚠️ — 小改动，已有 annotations 数据，只需在渲染时拼接

**主要缺口在"第一章变更概要"的方法级 Diff 提取，以及"关键代码片段"的 diff 渲染。这两个功能约占总工作量的 25%，技术上都有 IDEA API 支持，无阻塞性风险。**
