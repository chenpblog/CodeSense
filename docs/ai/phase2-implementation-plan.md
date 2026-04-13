# Phase 2 执行计划 — Git 代码审查

> 文档编号: `phase2-implementation-plan`
> 创建时间: 2026-04-13
> 状态: 待执行

---

## 一、Phase 2 目标

实现基于 Git Diff 的 AI 代码审查（Review）核心功能：
1. **获取 Git 变更**：实现 `GitDiffService`，利用 IntelliJ 内置 `git4idea` API 获取版本对比的 Diff 内容。
2. **AI 审查引擎**：实现 `CodeReviewService`，构造 Prompt 并调用 Phase 1 已实现的 LLM 客户端层。
3. **交互入口与 UI**：完善 `ReviewFileAction`、新增全局的审查入口，实现 `ReviewResultPanel`（审查结果展示面板）。

---

## 二、详细实施步骤

### Step 1: 完善 GitDiffService

**文件**: `src/main/kotlin/com/deeptek/ai/idea/git/GitDiffService.kt`
- 依赖于 `git4idea` 插件 API (`GitHistoryUtils`, `GitChanges`, 等)。
- **实现方法**:
  - `getDiffBetweenBranches(project, sourceBranch, targetBranch)`: 通过 `GitHistoryUtils` 获取两个分支的 diff，返回包含修改内容和文件路径的对象 `FileDiff`。
  - `getUncommittedChanges(project)`: 使用 `ChangeListManager` API 获取工作区和暂存区的未提交改动。
  - `getFileDiff(project, virtualFile)`: 获取指定文件的未提交或最新 commit 变动（用于 `ReviewFileAction` 审查单文件）。
- 工具类或扩展函数: 封装提取 Unified Diff 文本。

### Step 2: 实现代码审查逻辑

**文件**: `src/main/kotlin/com/deeptek/ai/idea/review/CodeReviewService.kt`
- 使用项目级别的 Service 注解。
- 注入 `CodeSenseSettings` (以获取 maxReviewFiles 和是否启用的开关等)。
- **核心方法**: 
  - `reviewChanges(diffs: List<FileDiff>): Flow<String>`：调用 `LlmProviderFactory.createDefault()` 获取大模型，通过预定义的 Prompt 发起流式审查调用。
  - `reviewSingleFile(fileDiff: FileDiff): Flow<String>`
- 添加结构化输出的 Prompt：包含潜在 Bug、安全风险、性能问题、代码规范及总结。

**文件**: `src/main/kotlin/com/deeptek/ai/idea/review/ReviewPromptBuilder.kt`
- 构建 Prompt 的工具类，处理 Diff 文本截断，防止 Token 超出模型上限。

### Step 3: UI 面板和交互 Action

**文件**: `src/main/kotlin/com/deeptek/ai/idea/ui/review/ReviewResultPanel.kt`
- ToolWindow 中的一个新的 Tab 或弹出的对话框（暂定利用 ToolWindow 中追加一个 `Review` 面板或利用 `ChatPanel` 直接返回，考虑到独立展示可能更好，可以新建一个 Review 面板用来单独展示结构化 Markdown，就像影响分析一样）。
- 提供复制 Markdown、导出文件等功能。

**文件**: `src/main/kotlin/com/deeptek/ai/idea/actions/ReviewFileAction.kt`
- 读取选中的文件，调用 `GitDiffService` 获取 Diff。
- 打开 ToolWindow 侧边栏，将任务提交给后台。
- 调用 `CodeReviewService` 并把流式结果输出到 `ReviewResultPanel`。

**文件**: `src/main/kotlin/com/deeptek/ai/idea/actions/ReviewGitChangesAction.kt`
- 在顶部工具栏/VCS 菜单增加动作，触发布局中的 Git 批量对比。
- 弹窗让用户选择对比规则（工作区未提交 vs HEAD 等等）。

### Step 4: 注册相关组件

**文件**: `src/main/resources/META-INF/plugin.xml`
- 注册 `ReviewGitChangesAction`。
- 修改或注册相关的 Service (Review 服务)。

---

## 三、验证标准

1. **单文件审查**：在有改动的文件右键 -> 审查此文件，可以通过 LLM 获取详细的代码 Review 结果（Markdown格式）。
2. **多文件审查**：能够获取 Git Changes 并批量发送给 LLM（考虑到 Token 限制，可能有截断或分批）；至少展示请求日志成功。
3. **UI 展示**：流式展示响应正常，界面不卡死（后台 Coroutines 线程调度正常）。
