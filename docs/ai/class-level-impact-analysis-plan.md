# Class 级别影响范围分析 — 执行计划

> 文档编号: `class-level-impact-analysis-plan`
> 创建时间: 2026-05-20

---

## 一、需求背景

### 当前状态

现有影响范围分析功能**仅支持方法级别**：

- `AnalyzeImpactAction`：光标必须放在 `PsiMethod` 上，否则提示 "请将光标放在一个 Java/Kotlin 方法名上"
- `CallHierarchyAnalyzer`：核心方法 `analyzeCallers`/`analyzeCallees` 只接受 `PsiMethod` 参数
- `AnalyzeBranchImpactAction`：Git Diff 模式也只提取方法级变更

### 需求目标

支持 **Class 级别**的影响范围分析：

1. 在类名上右键 → 分析该类所有公共方法的影响范围
2. 汇总展示整个类的受影响入口点和调用链
3. 生成 Class 维度的 Markdown 报告，包含：
   - 类的基本信息（包路径、注解、实现接口、继承关系）
   - 每个公共方法的调用链分析
   - 汇总的受影响入口点
   - AI 风险评估

---

## 二、设计方案

### 2.1 新增分析模式

在 `AnalysisMode` 枚举中新增 `SINGLE_CLASS` 模式：

```kotlin
enum class AnalysisMode(val displayName: String) {
    GIT_DIFF("Git Diff 分支对比"),
    SINGLE_METHOD("指定方法分析"),
    SINGLE_CLASS("指定类分析")        // ← 新增
}
```

### 2.2 新增 ClassInfo 数据模型

```kotlin
data class ClassInfo(
    val className: String,              // 类名
    val qualifiedName: String,          // 完全限定名
    val packageName: String,            // 包路径
    val filePath: String,               // 文件路径
    val lineNumber: Int,                // 类起始行号
    val annotations: List<String>,      // 类级注解
    val superClassName: String?,        // 父类名
    val interfaces: List<String>,       // 实现的接口
    val publicMethodCount: Int,         // 公共方法数
    val docComment: String?             // 类级 JavaDoc 注释
)
```

### 2.3 扩展 CallHierarchyAnalyzer

新增 Class 级别的分析方法：

```kotlin
fun analyzeClass(psiClass: PsiClass, maxDepth: Int = 10): ClassImpactResult {
    // 1. 提取类的基本信息 (ClassInfo)
    // 2. 获取类中所有公共方法
    // 3. 对每个方法执行 analyzeBidirectional()
    // 4. 汇总所有入口点（去重）
    // 5. 返回 ClassImpactResult
}

fun findClassAtCaret(psiFile: PsiFile, offset: Int): PsiClass? {
    // 根据光标偏移量获取 PsiClass
}
```

新增数据模型：

```kotlin
data class ClassImpactResult(
    val classInfo: ClassInfo,
    val methodResults: Map<MethodInfo, BidirectionalCallTree>,
    val allEntryPoints: List<EntryPointInfo>
)
```

### 2.4 扩展 AnalyzeImpactAction — 智能识别光标位置

修改现有 Action 的逻辑，**优先尝试方法，回退到类**：

```
光标位置判断逻辑：
1. 先尝试 findMethodAtCaret() → 找到 PsiMethod → 走现有方法级分析
2. 找不到 PsiMethod → 尝试 findClassAtCaret() → 找到 PsiClass → 走新的 Class 级分析
3. 都找不到 → 提示用户
```

这样对用户是**无感知升级**，同一个右键菜单项自动识别分析粒度。

### 2.5 新增 Class 报告生成

在 `ReportGenerator` 中新增 `generateClassReport()` 方法：

```markdown
# 🏗 类影响范围分析报告

| 项目 | 值 |
|------|---|
| **分析模式** | 指定类分析 |
| **目标类** | `UserService` |
| **包路径** | `com.example.service` |
| **所在文件** | `service/UserService.java` |
| **公共方法数** | 5 |
| **受影响入口点** | 8 |
| **风险等级** | ⚠️ 中等风险 |

---

## 一、类详情

**类注解：**
- `@Service`
- `@Transactional`

**继承/实现：**
- 父类：`BaseService`
- 接口：`IUserService`

---

## 二、各方法调用链分析

### 2.1 updateUserBalance(Long, BigDecimal)

**向上调用链：**
```
链路 1:
[HTTP API] POST /api/user/balance/update
  └── UserController.updateBalance()     ← @PostMapping
        └── ★ UserService.updateUserBalance()  ← 目标方法
```

（... 后续方法同模式 B 格式 ...）

---

## 三、受影响入口点汇总（去重）

| # | 入口类型 | 类.方法 | 路径/触发条件 | AI 说明 |
|---|---------|--------|-------------|---------|
| 1 | 🌐 HTTP API | `UserController.updateBalance:L55` | `POST /api/user/balance/update` | ... |

---

## 四、AI 风险评估

（流式生成）

---

## 分析元信息
```

---

## 三、涉及修改的文件

### 3.1 数据模型层

#### [MODIFY] [models.kt](file:///Users/chenping/mac_2026/dev/CodeSense-AI/src/main/kotlin/com/deeptek/ai/idea/analysis/models.kt)

- 新增 `AnalysisMode.SINGLE_CLASS` 枚举值
- 新增 `ClassInfo` 数据类
- 新增 `ClassImpactResult` 数据类

### 3.2 分析引擎层

#### [MODIFY] [CallHierarchyAnalyzer.kt](file:///Users/chenping/mac_2026/dev/CodeSense-AI/src/main/kotlin/com/deeptek/ai/idea/analysis/CallHierarchyAnalyzer.kt)

- 新增 `analyzeClass(psiClass: PsiClass, maxDepth: Int): ClassImpactResult` 方法
- 新增 `findClassAtCaret(psiFile: PsiFile, offset: Int): PsiClass?` 方法
- 新增 `PsiClass.toClassInfo(): ClassInfo` 扩展函数

### 3.3 报告生成层

#### [MODIFY] [ReportGenerator.kt](file:///Users/chenping/mac_2026/dev/CodeSense-AI/src/main/kotlin/com/deeptek/ai/idea/analysis/ReportGenerator.kt)

- 新增 `generateClassReport(report: ImpactReport, classResult: ClassImpactResult): String` 方法
- 包含类信息头部、各方法调用链、汇总入口点表格、AI 分析占位

### 3.4 Action 层

#### [MODIFY] [AnalyzeImpactAction.kt](file:///Users/chenping/mac_2026/dev/CodeSense-AI/src/main/kotlin/com/deeptek/ai/idea/actions/AnalyzeImpactAction.kt)

- 修改 `actionPerformed()`：先尝试 `findMethodAtCaret()`，找不到则尝试 `findClassAtCaret()`
- 新增 `performClassAnalysis()` 方法，与现有方法级分析逻辑并行
- Class 分析流程：
  1. 调用 `analyzer.analyzeClass(psiClass, maxDepth)` 获取 ClassImpactResult
  2. 构建 ImpactReport（mode = SINGLE_CLASS）
  3. 调用 `ReportGenerator.generateClassReport()` 渲染报告
  4. AI 风险评估 + 入口点短评（复用现有逻辑）

---

## 四、实现顺序

| 步骤 | 内容 | 预计工作量 |
|------|------|-----------|
| 1 | 数据模型层：新增 ClassInfo、ClassImpactResult、AnalysisMode.SINGLE_CLASS | 小 |
| 2 | 分析引擎：CallHierarchyAnalyzer 新增 analyzeClass + findClassAtCaret | 中 |
| 3 | 报告生成：ReportGenerator.generateClassReport() | 中 |
| 4 | Action 层：AnalyzeImpactAction 支持 Class 分析 + AI 调用 | 中 |
| 5 | 编译验证 | 小 |

---

## 五、设计决策

### Q1：是否新增独立的 Action 菜单项？

**决策：不新增**。直接在现有 "分析影响范围" Action 中做智能判断：
- 光标在方法上 → 方法级分析
- 光标在类名上（非方法内） → 类级分析
- 都不在 → 弹出提示

理由：保持用户体验简洁，减少菜单选项认知成本。

### Q2：Class 分析时分析哪些方法？

**决策：所有公共方法（public）**。理由：
- private/protected 方法不会被外部直接调用
- 分析所有 public 方法能完整覆盖类的对外暴露面

### Q3：Git Diff 模式是否也支持 Class 级别？

**本期不做**。Git Diff 模式目前已经支持批量方法分析，Class 粒度的批量分析意义不大。后续可考虑在报告中按类分组展示。

---

## 六、验证计划

### 编译验证

```bash
./gradlew build
```

### 手动测试场景

1. 光标在类名上（非方法内）→ 右键 → 分析影响范围 → 应生成 Class 级报告
2. 光标在方法上 → 右键 → 分析影响范围 → 应生成方法级报告（不影响现有功能）
3. 光标在空行/注释上 → 应提示用户
4. Class 包含多个 public 方法 → 报告应包含所有方法的调用链
5. AI 风险评估和入口点短评正常工作
