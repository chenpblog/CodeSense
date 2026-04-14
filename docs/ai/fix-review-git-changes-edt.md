# 修复 Git Diff 批量审查的线程问题及菜单重命名

问题现象：通过 VCS 菜单执行 Git Diff 审查时，抛出 `RuntimeExceptionWithAttachments: Access is allowed from Event Dispatch Thread (EDT) only`，因为在后台协程中直接调用了 `Messages.showInfoMessage()` 弹窗以及 `ToolWindowManager` 的 UI 操作，这违反了 IntelliJ 的 UI 必须在 EDT（事件分发线程）被调用的约束。
同时，用户要求将菜单入口调整为“审查分支vs主干”，并在模式A下仅保留该入口（Git Diff 批量分析）。

## Proposed Changes

### [MODIFY] ReviewGitChangesAction.kt
- 修复无变更时的弹窗崩溃：将 `Messages.showInfoMessage` 使用 `ApplicationManager.getApplication().invokeLater` 包装到 EDT 中执行。
- 修复 ToolWindow 加载崩溃：将 `ReviewToolWindowManager.openReviewTab` 使用 `ApplicationManager.getApplication().invokeAndWait(Computable { ... })` 包装，确保在 UI 线程同步创建并获取返回的 `ReviewPanel`。
- 修改打开的 Tab 页标题，由 `Review: Git Changes` 改为 `审查分支vs主干`。

#### 修改文件路径
`/src/main/kotlin/com/deeptek/ai/idea/actions/ReviewGitChangesAction.kt`

### [MODIFY] plugin.xml
- 将 Action 节点 `<action id="CodeSense.ReviewGitChanges"...>` 的文字调整为 `text="审查分支vs主干"`，并且更新 `description`。
- 根据要求确保它保留在 VCS 组中（继续使用 `VcsGroups` 的 `anchor="last"`）。

#### 修改文件路径
`/src/main/resources/META-INF/plugin.xml`

## User Review Required

1. 您提到的“审查分支vs主干”已被应用于菜单显示名称（`text="审查分支vs主干"`）。
2. 当前插件是否仅需在这一个地方修改菜单名以满足“模式 A — Git Diff 批量分析 只保留这个入口”的要求？

请您确认此计划是否符合预期，确认后我将开始修改代码。
