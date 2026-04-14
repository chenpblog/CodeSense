# 审查当前分支与主干 — 分支 Diff 对比审查

## 需求
1. 去掉右键菜单中的"审查此文件"和"审查全量 Git 未提交变更"
2. VCS 菜单保留"审查当前分支与主干"入口
3. 点击后弹出分支选择对话框（当前分支默认当前，主干默认 main/master）
4. 获取两个分支之间的 diff 发送给 LLM 审查

## 修改文件

### [MODIFY] plugin.xml
- 从 EditorPopupMenu 组移除 ReviewFileAction 和 ReviewGitChangesAction
- ReviewGitChangesAction 仅作为独立 action 挂载在 VcsGroups

### [MODIFY] GitDiffService.kt
- 新增 `getBranchDiff(currentBranch, targetBranch)` 方法
- 使用 `git diff targetBranch...currentBranch` 命令获取分支间差异
- 新增 `getLocalBranches()` 和 `detectMainBranch()` 辅助方法

### [NEW] BranchSelectDialog.kt
- 分支选择对话框，使用 IntelliJ DialogWrapper
- 两个下拉框：当前分支（默认当前活跃分支）、主干分支（默认 main/master）
- 返回用户选择的两个分支名

### [MODIFY] ReviewGitChangesAction.kt
- 先在 EDT 弹出 BranchSelectDialog
- 用户确认后在后台线程获取 diff 并发送审查请求
