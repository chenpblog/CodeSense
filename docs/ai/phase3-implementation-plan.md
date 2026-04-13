# Phase 3 执行计划 — 影响范围分析（Call Hierarchy + PSI）

> 文档编号: `phase3-implementation-plan`
> 创建时间: 2026-04-13
> 状态: 执行中

---

## 一、Phase 3 目标

实现基于 PSI（Program Structure Interface）的 **智能影响范围分析** 核心功能：

1. **CallHierarchyAnalyzer**：利用 IDEA Call Hierarchy API 递归分析方法的上下游调用链
2. **EntryPointDetector**：识别 HTTP API / 定时任务 / MQ / Dubbo RPC 等入口点
3. **ReportGenerator**：按照设计文档格式生成结构化 Markdown 报告
4. **ImpactResultPanel**：展示分析报告的 UI 面板（含复制 MD / 导出 .md 等工具栏功能）
5. **AnalyzeImpactAction**：重写分析入口 Action，支持两种模式（模式 A: Git Diff 批量 / 模式 B: 指定方法）

---

## 二、详细实施步骤

### Step 1: 数据模型 (`analysis/models.kt`)

定义 Phase 3 核心数据结构：
- `MethodInfo` — 方法元信息（类名、方法签名、文件路径、行号、注解等）
- `CallTree` — 递归调用树（向上/向下）
- `BidirectionalCallTree` — 双向调用树
- `ImpactReport` — 完整影响分析报告
- `EntryPointInfo` / `EntryPointType` — 入口点信息和类型枚举

### Step 2: 入口点检测器 (`analysis/EntryPointDetector.kt`)

支持的入口类型（参考 plan 4.3.5）：
- 🌐 `HTTP_API`: @RequestMapping, @GetMapping, @PostMapping, @PutMapping, @DeleteMapping
- ⏰ `SCHEDULED`: @Scheduled
- 📨 `MQ_LISTENER`: @RocketMQMessageListener, @KafkaListener
- 🔗 `DUBBO_RPC`: @DubboService（类级别注解 + 接口方法判定）
- 📡 `EVENT_LISTENER`: @EventListener, @TransactionalEventListener

### Step 3: 调用链分析器 (`analysis/CallHierarchyAnalyzer.kt`)

核心实现：
- `analyzeCallers()`: 使用 `CallerMethodsTreeStructure` API 递归追溯 "谁调用了我"
- `analyzeCallees()`: 使用 `CalleeMethodsTreeStructure` API 递归追溯 "我调用了谁"
- `analyzeBidirectional()`: 双向分析
- `analyzeImpactFromDiff()`: 模式 A，接收 Git Diff → 定位修改的 PsiMethod → 批量分析

### Step 4: 报告生成器 (`analysis/ReportGenerator.kt`)

按照 demo-report-git-diff.md 和 demo-report-single-method.md 的格式生成结构化 Markdown：
- 模式 A: 变更概要 → 调用链分析 → 受影响入口点汇总 → AI 风险评估 → 分析元信息
- 模式 B: 目标方法详情 → 向上调用链 → 受影响入口点汇总 → 关键调用者代码 → AI 分析 → 元信息

### Step 5: UI 面板 (`ui/impact/ImpactResultPanel.kt`)

功能：
- Markdown 渲染展示区域
- 工具栏: [📋 复制 MD] [💾 导出 .md] [🔄 重新分析]
- 流式输出 AI 风险评估部分

### Step 6: 更新 AnalyzeImpactAction

- 模式 B: 获取光标下的 PsiMethod → 弹出确认对话框 → 分析 → 展示
- 新增模式 A 的 Action（菜单 / 快捷键触发 Git Diff 批量分析）

### Step 7: plugin.xml 注册

- 注册 CodeReviewService（如未注册）
- 注册新的批量分析 Action

---

## 三、验证标准

1. 在编辑器中右键某个 Java 方法 → 分析影响范围 → 成功生成包含调用链的报告
2. 入口点识别正确（HTTP / 定时 / MQ / Dubbo / Event 等）
3. 报告格式符合 demo 文档规范
4. UI 面板正常展示，工具栏功能可用
5. `./gradlew build` 成功通过
