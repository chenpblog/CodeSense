# 实现计划：Git Diff 模式影响范围分析

> 文档编号: `impl-git-diff-impact-analysis`
> 创建时间: 2026-04-14

## 目标

实现 Demo 报告（`demo-report-git-diff.md`）中定义的完整 Git Diff 批量影响分析流程。
用户选择两个分支后，插件自动：
1. 获取分支 diff → 精确定位到变更方法
2. 对每个变更方法执行调用链分析
3. 汇总入口点 + AI 风险评估
4. 输出完整的结构化报告

## 核心模块

### 1. DiffMethodExtractor（新建）
**路径:** `src/main/kotlin/com/deeptek/ai/idea/git/DiffMethodExtractor.kt`

**职责:** 将 `GitDiffService` 返回的文件级 `FileDiff` 映射到方法级 `MethodInfo`

**实现步骤:**
1. 过滤仅 `.java` / `.kt` 文件
2. 对 `oldContent` / `newContent` 使用 `PsiFileFactory.createFileFromText()` 创建临时 PsiFile
3. 使用 `PsiTreeUtil.findChildrenOfType(file, PsiMethod::class.java)` 提取方法列表
4. 方法匹配规则：
   - 签名相同 + body 不同 → `MODIFIED`
   - 只在新文件中 → `ADDED`
   - 只在旧文件中 → `DELETED`
5. 从 PsiMethod 的 `textRange` 提取行号范围
6. 将结果转为 `MethodInfo` (复用 `toMethodInfo()` 扩展)

### 2. AnalyzeBranchImpactAction（新建）
**路径:** `src/main/kotlin/com/deeptek/ai/idea/actions/AnalyzeBranchImpactAction.kt`

**职责:** Git Diff 批量影响分析 Action（模式 A）

**流程:**
1. 弹出分支选择对话框 (复用 `BranchSelectDialog`)
2. 调用 `GitDiffService.getBranchDiff()` 获取文件级 diff
3. 调用 `DiffMethodExtractor` 从 diff 提取变更方法
4. 对每个变更方法调用 `CallHierarchyAnalyzer.analyzeBidirectional()`
5. 汇总入口点、填充 `affectedMethods`
6. 用 `ReportGenerator.generateGitDiffReport()` 生成初始报告
7. 异步双路 AI 请求（入口点短评 + 全局风险评估）
8. 在 `ImpactResultPanel` 中展示报告

### 3. ReportGenerator 增强
- 调用链节点旁标注注解 (`← @PostMapping`)
- 变更概要增加"变更文件数"统计
- Git Diff 模式的 AI 评估 Prompt 优化

### 4. plugin.xml 注册
- 注册 `AnalyzeBranchImpactAction` 到 VCS 菜单和 Tools → CodeSense AI 菜单

## 不在本次范围
- 代码片段 diff 渲染（`+/-` 标记）→ 后续迭代
- "人工说明"可编辑列 → 后续迭代
- 时间范围对比模式 → 后续迭代

## 验证方式
- `./gradlew runIde` 启动沙盒 IDE
- 打开一个有多分支的项目
- VCS 菜单 → 分析变更影响范围
- 验证报告格式与 demo-report-git-diff.md 对标
