# 受影响入口点汇总表增强 — 执行计划

## 背景

当前"受影响入口点汇总"表格存在三个问题：
1. **类.方法 缺少代码行数** — 仅显示 `ClassName.methodName`，无法快速定位
2. **路径/触发条件不准确** — OTHER 类型入口点显示 `-`，未从代码中实际提取
3. **AI 说明为"暂无特别说明"** — 当 AI 返回结果不完整或无结果时，fallback 逻辑不够智能

## 改动方案

### 改动 1：类.方法 补充代码行数（models.kt + ReportGenerator.kt）

**当前**：表格列 `类.方法` 显示 `ClassName.methodName`  
**目标**：显示 `ClassName.methodName:L42`

- `EntryPointInfo` 的 `method: MethodInfo` 已包含 `lineNumber` 字段
- 只需在 `ReportGenerator.generateEntryPointTable()` 中拼接行号

### 改动 2：路径/触发条件 — 从代码中提取（models.kt + EntryPointDetector.kt + CallHierarchyAnalyzer.kt）

**当前问题**：
- `CallTree.collectEntryPoints()` 中非入口点的叶子节点 `path = "-"`，`triggerCondition = null`
- `EntryPointDetector` 对 OTHER 类型不提取任何信息

**目标**：
- OTHER 类型入口点也应提取有意义的路径信息（文件路径 + 行号）
- 从方法注解中提取更多信息（如 `@Override`、接口实现等）

### 改动 3：AI 说明增强（models.kt + AnalyzeImpactAction.kt + CallHierarchyAnalyzer.kt）

**三层 fallback 策略**：
1. **有注释** → 从方法 JavaDoc/KDoc 注释中提取，标注 `【注释】`
2. **无注释** → AI 结合上下文生成说明（已有逻辑，增强 prompt）
3. **AI 失败** → 基于方法名和上下文给出默认推测

**实现方式**：
- `MethodInfo` 新增 `docComment: String?` 字段，在 `toMethodInfo()` 中从 PsiMethod 提取
- `EntryPointInfo` 新增 `codeComment: String?` 字段，存储代码注释
- `AnalyzeImpactAction` 中先检查注释，有注释直接用，无注释才调 AI
- AI prompt 增强：提供更多上下文（方法签名、注解、路径信息）

## 涉及文件

| 文件 | 改动类型 | 说明 |
|------|---------|------|
| `models.kt` | MODIFY | MethodInfo 新增 docComment；EntryPointInfo 新增 codeComment |
| `CallHierarchyAnalyzer.kt` | MODIFY | toMethodInfo() 提取 JavaDoc 注释 |
| `EntryPointDetector.kt` | MODIFY | OTHER 类型提取文件路径信息 |
| `ReportGenerator.kt` | MODIFY | 表格渲染补充行号、优化 AI 说明展示格式 |
| `AnalyzeImpactAction.kt` | MODIFY | AI 说明三层 fallback 策略；增强 prompt |

## 验证方式

1. `./gradlew build` 编译通过
2. `./gradlew runIde` 启动测试 IDE，对有注释和无注释的方法分别分析，验证三种情况
