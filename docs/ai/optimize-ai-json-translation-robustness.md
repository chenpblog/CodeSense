# 优化 AI 转换 JSON 的格式化与结构健壮性

## 问题描述
1. AI 翻译后的 JSON 在中间面板显示为单行未格式化文本
2. AI 翻译可能改变 JSON 结构（丢失嵌套层级、改变键顺序），导致后续 Bean 生成错配
3. AI 返回脏数据（markdown 包裹、多余解释文字）时处理不够鲁棒

## 修改文件

### 1. `JsonBeanPreviewDialog.kt` ✅

#### doAiTranslation — Prompt 优化
- 从模糊的"绝对不可以改变"改为 6 条精确约束条款
- 明确要求"带缩进的格式化 JSON"
- System prompt 更强调"只能输出纯 JSON"

#### extractAndFormatJson — 新增智能 JSON 提取方法
- 正则匹配 ` ```json ... ``` ` markdown 代码块并提取内容
- 清理残留的 ` ``` ` 标记
- 从混合文本中定位 JSON 边界（第一个 `{`/`[` 到最后一个 `}`/`]`）
- 使用 Gson `setPrettyPrinting()` 格式化输出

#### validateJsonStructure / compareStructure — 新增结构校验
- 递归比较原始 JSON 与翻译后 JSON 的结构
- 检查每层对象的键数量、数组的元素数量、值类型一致性
- 不一致时在状态栏显示精确的差异路径

### 2. `JsonToBeanService.kt` — cloneAndMergeNode 增强 ✅
- 数组首元素非 JsonObject 时安全跳过（而非直接 `.asJsonObject` 崩溃）
- 子节点数量不匹配时输出 warn 日志
- 英文 JSON 有多余键时记录日志
- 安全迭代取 min(childCount, entries.size) 避免越界

## 验证
- ✅ 代码结构、import、类型引用经逐行审查确认正确
- 待用户在 IntelliJ 中编译并测试 AI 转换功能
