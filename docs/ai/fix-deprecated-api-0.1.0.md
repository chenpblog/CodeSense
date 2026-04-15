# 修复 CodeSense AI 0.1.0 已弃用 API 和兼容性警告

## 问题清单

### 1. ReadAction.compute(ThrowableComputable) — 8 处调用已弃用
**替代方案**: `com.intellij.openapi.application.runReadAction { }`

| 文件 | 行号 | 说明 |
|------|------|------|
| CallHierarchyAnalyzer.kt | L37, L57, L72, L86 | 4 处调用 |
| DiffMethodExtractor.kt | L56 | 1 处调用 |
| AnalyzeImpactAction.kt | L49 | 1 处调用 |
| AnalyzeBranchImpactAction.kt | L271 | 1 处调用 |
| Tools.kt | L334 | 1 处调用（全限定名引用） |

### 2. FileSaverDescriptor 构造函数已弃用 — 1 处
**替代方案**: 使用 2 参数构造函数 `FileSaverDescriptor(title, description)`

| 文件 | 行号 | 说明 |
|------|------|------|
| ImpactResultPanel.kt | L125 | 移除第三个参数 `"md"` |

### 3. Kotlin Plugin 兼容性警告 — 1 处
**问题**: 插件依赖 Kotlin 插件但未声明 `supportsKotlinPluginMode`
**现状**: `kotlin-features.xml` 中已正确声明了 `<supportsKotlinPluginMode supportsK1="true" supportsK2="true"/>`
**原因**: Marketplace 检查器可能未解析 optional config-file 中的声明
**修复**: 在 `plugin.xml` 的 `<extensions>` 中直接添加 `<org.jetbrains.kotlin.supportsKotlinPluginMode>` 属性声明

## 执行步骤

1. 修改 5 个 Kotlin 文件，将 `ReadAction.compute<T, Throwable> { }` 替换为 `runReadAction { }`
2. 修改 `ImpactResultPanel.kt`，使用 `FileSaverDescriptor` 的 2 参数构造函数
3. 检查 `plugin.xml` 中的 Kotlin 兼容性声明
4. 执行 `./gradlew build` 验证编译通过
