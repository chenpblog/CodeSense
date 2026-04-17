# 🧠 CodeSense AI — IntelliJ IDEA 代码感知 AI 助手

[![IntelliJ Platform](https://img.shields.io/badge/IntelliJ-2024.3~2026.3-blue?logo=intellij-idea)](https://www.jetbrains.com/idea/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.25-purple?logo=kotlin)](https://kotlinlang.org/)
[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green)](LICENSE)

> 一站式的 AI 代码助手 IntelliJ IDEA 插件，集成代码审查、影响范围分析、代码解释、Agent 自主执行等能力，支持多种国产大模型。

---

## ✨ 核心功能

| 功能                  | 说明                                                                               | 入口                                        |
| --------------------- | ---------------------------------------------------------------------------------- | ------------------------------------------- |
| 🔍 **影响范围分析**   | 选中方法 → 自动追溯调用链 → 生成受影响入口点报告（HTTP API / 定时任务 / MQ / RPC） | 右键 → CodeSense AI → 分析影响范围          |
| 👀 **代码审查**       | 基于 Git Diff 的 AI 代码 Review，支持单文件和全量未提交变更                        | 右键 → 审查此文件 / 审查全量 Git 未提交变更 |
| 💡 **解释代码**       | 选中代码片段 → AI 详细解析功能、设计模式、潜在问题                                 | 右键 → 解释代码                             |
| 💬 **AI 对话**        | 带上代码上下文的交互式 Chat                                                        | 右键 → 咨询 AI / Chat 面板                  |
| 🤖 **Agent 自主执行** | AI 可自主调用工具完成复杂任务                                                      | Chat 面板                                   |

### 影响范围分析报告示例

报告包含：

- 📊 **调用链深度树** — 双向展示上游调用者和下游被调者
- 📋 **受影响入口点汇总** — 带行号、路径、触发条件，AI/注释智能说明
- 🚨 **AI 风险评估** — 流式输出的全局风险分析与优先级建议

## 🏗 技术架构

```
src/main/kotlin/com/deeptek/ai/idea/
├── actions/          # Action 层 — 编辑器右键菜单触发入口
│   ├── AnalyzeImpactAction     # 影响范围分析
│   ├── ExplainCodeAction       # 代码解释
│   ├── ReviewFileAction        # 单文件审查
│   ├── ReviewGitChangesAction  # Git 全量审查
│   └── AskCodeSenseAction      # 咨询 AI
├── analysis/         # 分析引擎
│   ├── CallHierarchyAnalyzer   # 调用链分析（PSI 遍历）
│   ├── EntryPointDetector      # 入口点识别（HTTP/Scheduled/MQ/Dubbo等）
│   ├── ReportGenerator         # Markdown 报告生成
│   └── models                  # 数据模型（MethodInfo, CallTree, ImpactReport）
├── agent/            # Agent 系统 — Tool Use 架构
│   ├── AgentExecutor           # Agent 执行器
│   ├── ToolRegistry            # 工具注册中心
│   └── tools/                  # 内置工具集
├── llm/              # LLM 抽象层
│   ├── LlmProvider             # 统一接口（chat / stream）
│   ├── LlmProviderFactory      # 工厂（根据配置创建 Provider）
│   └── providers/
│       ├── OpenAiCompatProvider      # OpenAI 兼容协议（MiniMax/DeepSeek/通义千问/GLM等）
│       └── AnthropicCompatProvider   # Anthropic Messages API 协议（Claude）
├── git/              # Git 集成 — Diff 提取
├── review/           # 代码审查服务
├── settings/         # 设置面板 — 多 Provider、多模型管理
└── ui/               # UI 层
    ├── chat/         # Chat 面板（ToolWindow）
    ├── impact/       # 影响分析报告面板
    └── review/       # 审查结果面板
```

## 🔌 支持的 LLM 模型

支持 **Anthropic Messages API** 和 **OpenAI Chat Completions** 两种协议，可在每个 Provider 配置中独立选择。默认采用 Anthropic 协议。

| Provider     | 推荐协议   | 推荐模型            | 配置 Base URL 示例                                               |
| ------------ | ------------ | ------------------- | ---------------------------------------------------------------- |
| MiniMax      | Anthropic    | MiniMax-M2.7        | `https://api.minimaxi.com/anthropic/v1/messages`                 |
| 智谱 GLM     | OpenAI       | glm-4               | `https://open.bigmodel.cn/api/paas/v4/chat/completions`          |
| DeepSeek     | OpenAI       | deepseek-chat       | `https://api.deepseek.com/v1/chat/completions`                   |
| 通义千问     | OpenAI       | qwen-plus           | `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` |
| 通义 Qwen3   | Anthropic    | qwen3-coder-plus    | `https://coding.dashscope.aliyuncs.com/apps/anthropic/v1/messages` |
| Claude       | Anthropic    | claude-3-5-sonnet   | `https://api.anthropic.com/v1/messages`                          |
| Kimi         | OpenAI       | moonshot-v1-128k    | `https://api.moonshot.cn/v1/chat/completions`                    |
| 自定义       | 自选         | 任意模型              | 根据实际接口填写                                                 |

> ⚠️ **Base URL 配置提示**：在 Settings 设置时需要填写**完整的终端地址**，切勿只填域名。Anthropic 协议地址通常以 `/v1/messages` 结尾，OpenAI 协议地址通常以 `/chat/completions` 结尾。

---

## 🚀 快速开始

### 环境要求

| 依赖              | 版本                                    |
| ----------------- | --------------------------------------- |
| **Java (JDK)**    | 21+（推荐 Amazon Corretto 21）          |
| **Gradle**        | 8.13+（项目自带 Wrapper，无需手动安装） |
| **IntelliJ IDEA** | 2024.3 ~ 2026.3.x                       |

### 1. 克隆项目

```bash
git clone https://github.com/chenping/CodeSense-AI.git
cd CodeSense-AI
```

### 2. 配置 JDK

推荐使用 [SDKMAN](https://sdkman.io/) 管理 JDK：

```bash
# 安装 JDK 21
sdk install java 21.0.9-amzn

# 切换到 JDK 21
sdk use java 21.0.9-amzn

# 验证
java -version
# openjdk version "21.0.9" ...
```

也可以手动设置：

```bash
export JAVA_HOME=/path/to/jdk-21
```

### 3. 编译

```bash
# 编译项目（自动下载 Gradle Wrapper 和所有依赖）
./gradlew build
```

首次编译会下载 IntelliJ Platform SDK，耗时较长（约 5-10 分钟），请耐心等待。

### 4. 本地运行（启动沙箱 IDE）

```bash
./gradlew runIde
```

这会启动一个独立的 IntelliJ IDEA 实例，插件已预装。你可以在这个沙箱 IDE 中测试所有功能。

### 5. 配置 LLM

在沙箱 IDE 中：

1. 打开侧边栏 **CodeSense AI** 面板
2. 点击标题栏齿轮图标 ⚙️ 或前往 **Settings → Tools → CodeSense AI**
3. 添加 Provider：填写 API Base URL、API Key、模型名称
4. 保存即可开始使用

---

## 📦 打包

### 生成可分发的插件 ZIP

```bash
./gradlew buildPlugin
```

构建产物位于：

```
build/distributions/codesense-ai-plugin-0.2.1.zip
```

这个 ZIP 文件可以直接在 IntelliJ IDEA 中安装：

- **Settings → Plugins → ⚙️ → Install Plugin from Disk...**
- 选择生成的 ZIP 文件

### 修改版本号

编辑 `gradle.properties`：

```properties
pluginVersion = 0.2.1
```

### 调整兼容的 IDE 版本范围

编辑 `build.gradle.kts`：

```kotlin
intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "243"      // 最低支持版本（2024.3）
            untilBuild = "263.*"    // 最高支持版本（2026.3.x）
        }
    }
}
```

> 版本号对照：`241` = 2024.1, `242` = 2024.2, `243` = 2024.3, `263` = 2026.3

---

## 🏪 发布到 JetBrains Marketplace

### 前置准备

1. **注册 JetBrains 账号**  
   前往 [JetBrains Marketplace](https://plugins.jetbrains.com/) 注册开发者账号

2. **获取 Marketplace Token**  
   登录后访问 [My Tokens](https://plugins.jetbrains.com/author/me/tokens)  
   → 点击 **Generate Token** → 复制保存

3. **首次上传需手动提交**  
   第一次发布必须在网页端手动上传 ZIP 文件（后续可用 Gradle 自动化）

### 方式一：网页手动上传（首次）

1. 执行 `./gradlew buildPlugin` 生成 ZIP
2. 访问 [Upload Plugin](https://plugins.jetbrains.com/plugin/add)
3. 上传 `build/distributions/CodeSense AI-x.x.x.zip`
4. 填写插件信息、截图、分类
5. 提交审核（通常 1-2 个工作日）

### 方式二：Gradle 自动发布（后续版本）

在 `build.gradle.kts` 中添加发布配置：

```kotlin
intellijPlatform {
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}
```

然后执行：

```bash
# 设置 Token 环境变量
export PUBLISH_TOKEN="perm:xxxxxxxx"

# 一键发布
./gradlew publishPlugin
```

### 方式三：CI/CD 自动化（推荐）

创建 `.github/workflows/publish.yml`：

```yaml
name: Publish Plugin

on:
  push:
    tags:
      - "v*"

jobs:
  publish:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: "corretto"
          java-version: "21"

      - name: Publish Plugin
        env:
          PUBLISH_TOKEN: ${{ secrets.PUBLISH_TOKEN }}
        run: ./gradlew publishPlugin
```

在 GitHub 仓库 Settings → Secrets 中添加 `PUBLISH_TOKEN`。

---

## 🧑‍💻 开发指南

### 常用 Gradle 命令

| 命令                      | 说明                                                 |
| ------------------------- | ---------------------------------------------------- |
| `./gradlew build`         | 编译 + 测试                                          |
| `./gradlew runIde`        | 启动沙箱 IDE（带插件）                               |
| `./gradlew buildPlugin`   | 打包为可分发的 ZIP                                   |
| `./gradlew publishPlugin` | 发布到 JetBrains Marketplace                         |
| `./gradlew verifyPlugin`  | 验证插件兼容性（详见下方说明）                       |
| `./gradlew clean`         | 清理构建产物                                         |

### 验证插件兼容性 (verifyPlugin)

在发布插件前，强烈建议验证插件与目标 IntelliJ 版本的兼容性（检查 API 废弃、二进制破坏等）。因为验证过程会下载完整 IDE，**请务必配置好指定的版本后再运行，避免下载过多不需要的 IDE 版本占用大量磁盘空间。**

**1. 配置要验证的版本** (编辑 `build.gradle.kts`)：

```kotlin
intellijPlatform {
    pluginVerification {
        ides {
            // 验证当前的开发版本（通过属性构建 "IC-2024.3"）
            val type = providers.gradleProperty("platformType").getOrElse("IC")
            val ver = providers.gradleProperty("platformVersion").get()
            ide("$type-$ver")
            
            // 可以手动加入需要额外验证的历史或未来特定版本，格式为：<类型>-<版本>
            // ide("IC-2024.1.4") 
            // ide("IU-2024.2.3")
        }
    }
}
```

**2. 运行验证命令**：

```bash
./gradlew verifyPlugin
```

> **提示**：运行后，控制台会输出兼容性检查结果，成功即可安心打包发布。如果有不兼容的方法调用，请根据提示进行代码修改。

### 项目配置文件

| 文件                                     | 用途                                |
| ---------------------------------------- | ----------------------------------- |
| `build.gradle.kts`                       | 构建脚本（依赖、插件配置）          |
| `gradle.properties`                      | 版本号、Platform 版本               |
| `settings.gradle.kts`                    | 项目名称                            |
| `src/main/resources/META-INF/plugin.xml` | 插件元数据（Action 注册、服务声明） |

### 调试技巧

```bash
# 带调试端口启动（可在 IDEA 中 Remote Debug）
./gradlew runIde --debug-jvm

# 查看详细构建日志
./gradlew build --info

# 清理重建
./gradlew clean build
```

---

## 📋 版本历史

### v0.2.1（当前版本）

**📥 JSON 设计器增强**
- ✅ 新增「📋 粘贴 JSON」功能 — 直接粘贴原始 JSON 对象/数组，自动还原为设计树
- ✅ AI 翻译 Class Name 按钮优化 — 支持反复调用、错误状态可见（服务繁忙/网络失败/超时等）

**🔄 LLM 调用健壮性**
- ✅ 非流式请求自动重试 — 429/500/502/503/529 等瞬态错误指数退避重试（3 次，1s→2s→4s）
- ✅ 网络 IO 异常自动重试 — 连接超时、断网等场景自动恢复
- ✅ SSE 解析兼容性修复 — 同时支持 `data: {...}` 和 `data:{...}` 两种 SSE 格式

**⚙️ 模型配置增强**
- ✅ 新增 API 协议选择 — 每个 Provider 可独立选择 Anthropic 或 OpenAI 协议，默认 Anthropic
- ✅ 协议列显示 — 模型列表新增「协议」列，一目了然
- ✅ 测试连接功能 — 发送 "hi" 给模型，实时显示测试状态和模型回复
- ✅ 修复协议配置持久化问题 — 重启 IDE 后用户选择的协议不再被重置

### v0.2.0

- ✅ JSON to Java Bean 设计器 — 可视化树表设计 JSON 结构，一键生成 Java Bean
- ✅ AI 智能翻译 — 中文字段名自动翻译为英文
- ✅ 多协议支持 — 同时支持 OpenAI 和 Anthropic 两种 API 协议
- ✅ Chat 面板优化 — Markdown 渲染、代码高亮、流式输出

### v0.1.0

- ✅ 影响范围分析 — 调用链追溯 + 入口点识别 + AI 风险评估
- ✅ 代码审查 — 基于 Git Diff 的单文件 / 全量审查
- ✅ 代码解释 — 选中代码的 AI 详细解析
- ✅ 多模型支持 — OpenAI 兼容协议 + Anthropic 协议
- ✅ Agent 系统 — Tool Use 架构
- ✅ Chat 面板 — 交互式 AI 对话

---

## 📄 License

Apache License 2.0 — 详见 [LICENSE](LICENSE)
