# Phase 1 执行计划 — 基础骨架搭建

> 文档编号: `phase1-implementation-plan`
> 创建时间: 2026-04-13
> 状态: 待执行

---

## 一、Phase 1 目标

搭建 CodeSense AI 插件的基础骨架，确保以下能力可用：
1. ✅ Gradle 项目初始化，可在 IDEA sandbox 中成功加载插件
2. ✅ Settings 页面完成（API Key 配置、模型选择、功能开关）
3. ✅ LLM 客户端层完成（统一接口 + OpenAI 兼容实现 + 流式支持）
4. ✅ 最简 Chat ToolWindow（Swing 文本区域，可与 LLM 对话）
5. ✅ 验证：能在 IDEA sandbox 中加载插件并成功调用 LLM

---

## 二、技术选型确认

| 维度 | 选型 |
|------|------|
| 开发语言 | Kotlin |
| 构建工具 | Gradle (Kotlin DSL) + IntelliJ Platform Gradle Plugin 2.x |
| 目标 IDE | IntelliJ IDEA 2024.3+ |
| HTTP 客户端 | OkHttp 4.x + Kotlin Coroutines |
| 序列化 | kotlinx.serialization |
| UI | Swing (Kotlin UI DSL v2) |
| 包名 | `com.deeptek.ai.idea` |

---

## 三、详细实施步骤

### Step 1: Gradle 项目初始化

**目标**：创建标准的 IntelliJ Platform Plugin 项目骨架。

#### 1.1 创建项目结构

```
codesense-ai-plugin/
├── build.gradle.kts
├── gradle.properties
├── settings.gradle.kts
├── src/
│   └── main/
│       ├── kotlin/com/deeptek/ai/idea/
│       └── resources/
│           └── META-INF/
│               └── plugin.xml
```

#### 1.2 `settings.gradle.kts`
- 项目名称: `codesense-ai-plugin`

#### 1.3 `gradle.properties`
```properties
pluginGroup = com.deeptek.ai.idea
pluginName = CodeSense AI
pluginVersion = 0.1.0
platformType = IC
platformVersion = 2024.3
```

#### 1.4 `build.gradle.kts`
- 使用 `org.jetbrains.intellij.platform` 插件 2.x
- 添加 `kotlin("jvm")` 和 `kotlin("plugin.serialization")` 插件
- 依赖项:
  - `intellij-platform` (IntelliJ IDEA Community 2024.3)
  - `bundledPlugin("Git4Idea")`
  - `bundledPlugin("com.intellij.java")`
  - `com.squareup.okhttp3:okhttp:4.12.0`
  - `org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3`
  - `org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0`

#### 1.5 `plugin.xml`
- 注册 ToolWindow（ChatToolWindowFactory）
- 注册 Settings（CodeSenseSettingsConfigurable）
- 注册 ApplicationService（CodeSenseSettings）
- 声明依赖: `com.intellij.modules.platform`, `Git4Idea`, `com.intellij.java`

---

### Step 2: Settings 持久化与配置页面

**目标**：实现模型配置的持久化存储和 Settings UI。

#### 2.1 数据模型 — `settings/ProviderConfig.kt`
- `ProviderConfig`: id, displayName, type, baseUrl, apiKey, modelName, maxTokens, temperature, supportToolCalling, supportStreaming
- `ProviderType` 枚举: MINIMAX, GLM, DEEPSEEK, QWEN, CUSTOM（含默认 baseUrl 和 modelName）

#### 2.2 持久化 — `settings/CodeSenseSettings.kt`
- `@State` 注解，存储到 `codesense-ai.xml`
- `PersistentStateComponent<CodeSenseSettings.State>`
- State 包含: defaultProviderId, providers 列表, 功能开关, 参数配置
- 预置 MiniMax 和 GLM 默认配置
- 提供 `getDefaultProvider()` 方法

#### 2.3 Settings UI — `settings/CodeSenseSettingsConfigurable.kt`
- 使用 Kotlin UI DSL v2 (`panel {}`)
- 模型列表 (JBTable) + 添加/编辑/删除/设为默认/测试连接 按钮
- 功能设置区域: 启用代码审查、启用影响分析、AI 风险评估开关、最大文件数、追溯深度

#### 2.4 添加/编辑模型对话框 — `settings/ProviderConfigDialog.kt`
- 提供者类型下拉框（选择后自动填充默认值）
- 显示名称、Base URL、API Key(密码框)、模型名称
- 高级选项: maxTokens, temperature, Tool Calling, 流式输出
- 测试连接按钮

---

### Step 3: LLM 客户端层

**目标**：实现统一的 LLM 调用接口，支持流式和 Tool Calling。

#### 3.1 数据模型 — `llm/models.kt`
```kotlin
// 核心数据类（使用 kotlinx.serialization）
@Serializable data class ChatMessage(role, content, toolCalls?, toolCallId?)
@Serializable data class ChatRequest(model, messages, tools?, stream?, temperature?, maxTokens?)
@Serializable data class ChatResponse(id, choices, usage)
@Serializable data class ChatChunk(id, choices) // 流式 chunk
@Serializable data class ToolDefinition(type="function", function)
@Serializable data class ToolCall(id, type, function)
@Serializable data class FunctionCall(name, arguments)
```

#### 3.2 统一接口 — `llm/LlmProvider.kt`
```kotlin
interface LlmProvider {
    val name: String
    val config: ProviderConfig
    suspend fun chatCompletion(request: ChatRequest): ChatResponse
    suspend fun chatCompletionStream(request: ChatRequest): Flow<ChatChunk>
}
```

#### 3.3 HTTP 客户端 — `llm/LlmClient.kt`
- OkHttp 单例 + 超时配置（连接30s, 读取120s）
- 统一 `Authorization: Bearer {apiKey}` 请求头
- 非流式: POST `/chat/completions` → 解析 JSON
- 流式: POST `/chat/completions` (stream=true) → 解析 SSE `data: {...}`
- 错误处理: HTTP 状态码 + API 错误码

#### 3.4 OpenAI 兼容实现 — `llm/providers/OpenAiCompatProvider.kt`
- 由于所有模型都高度兼容 OpenAI 格式，只需一个通用实现
- 根据 `ProviderConfig` 的 `baseUrl` 和 `apiKey` 区分不同提供者
- 实现 `chatCompletion` 和 `chatCompletionStream`

#### 3.5 Provider 工厂 — `llm/LlmProviderFactory.kt`
- 根据 `ProviderConfig` 创建对应的 `LlmProvider` 实例
- 目前所有类型统一返回 `OpenAiCompatProvider`（后续如有差异再特化）

---

### Step 4: Chat ToolWindow (最简版)

**目标**：实现基础的 Chat 侧边栏，能与 LLM 进行对话。

#### 4.1 ToolWindow 注册 — `ui/chat/ChatToolWindowFactory.kt`
- 实现 `ToolWindowFactory`
- 创建 `ChatPanel` 并注册到 ToolWindow

#### 4.2 Chat 面板 — `ui/chat/ChatPanel.kt`
- **布局**:
  - 上方: JBScrollPane + 消息显示区域 (JTextPane, HTML 渲染)
  - 下方: JBTextField 输入框 + 发送按钮
- **功能**:
  - 输入消息 → 调用 LLM → 流式显示响应
  - 支持 Enter 发送、Shift+Enter 换行
  - 显示加载状态 (Spinner)
  - 基本的消息历史（内存中）
- **线程模型**:
  - LLM 调用在 `Dispatchers.IO`
  - UI 更新在 `Dispatchers.EDT` (通过 `invokeLater`)

---

### Step 5: 插件图标与资源

#### 5.1 图标文件
- `resources/icons/codesense.svg` — 插件侧边栏图标 (13x13)
- 使用简单的 AI/代码图标（可用 SVG 直接编写）

---

## 四、文件清单（Phase 1 需要创建的所有文件）

| # | 文件路径 | 说明 |
|---|---------|------|
| 1 | `build.gradle.kts` | Gradle 构建脚本 |
| 2 | `gradle.properties` | 属性配置 |
| 3 | `settings.gradle.kts` | 项目名称 |
| 4 | `src/main/resources/META-INF/plugin.xml` | 插件注册 |
| 5 | `src/main/kotlin/.../settings/ProviderConfig.kt` | 模型配置数据类 |
| 6 | `src/main/kotlin/.../settings/CodeSenseSettings.kt` | 持久化配置 |
| 7 | `src/main/kotlin/.../settings/CodeSenseSettingsConfigurable.kt` | Settings UI |
| 8 | `src/main/kotlin/.../settings/ProviderConfigDialog.kt` | 添加/编辑模型对话框 |
| 9 | `src/main/kotlin/.../llm/models.kt` | LLM 数据模型 |
| 10 | `src/main/kotlin/.../llm/LlmProvider.kt` | 统一接口 |
| 11 | `src/main/kotlin/.../llm/LlmClient.kt` | HTTP 客户端 |
| 12 | `src/main/kotlin/.../llm/providers/OpenAiCompatProvider.kt` | 通用 OpenAI 兼容实现 |
| 13 | `src/main/kotlin/.../llm/LlmProviderFactory.kt` | Provider 工厂 |
| 14 | `src/main/kotlin/.../ui/chat/ChatToolWindowFactory.kt` | ToolWindow 注册 |
| 15 | `src/main/kotlin/.../ui/chat/ChatPanel.kt` | Chat 面板 UI |
| 16 | `src/main/resources/icons/codesense.svg` | 插件图标 |
| 17 | `src/main/resources/messages/CodeSenseBundle.properties` | 国际化字符串 |

> 注: `...` = `com/deeptek/ai/idea`

---

## 五、验证标准

1. **构建成功**: 执行 `./gradlew build` 无错误
2. **Sandbox 加载**: 执行 `./gradlew runIde` 能启动 IDEA sandbox 实例
3. **Settings 可用**: Settings → Tools → CodeSense AI 页面正常展示
4. **模型配置**: 能添加/编辑/删除模型配置，配置重启后保留
5. **Chat 对话**: 配置 API Key 后，在 Chat 侧边栏能与 LLM 对话
6. **流式响应**: Chat 中文字流式显示（逐字出现）

---

## 六、注意事项

1. **线程安全**: 所有 LLM 调用必须在后台线程，UI 更新必须在 EDT
2. **API Key 安全**: 使用 `CredentialAttributes` + `PasswordSafe` 存储 API Key（而非明文 XML）
3. **错误处理**: 网络超时、API 限流、Key 无效等情况需友好提示
4. **兼容性**: 目标 IDE 版本 2024.3+，使用对应版本的 API
