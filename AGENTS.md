# Agent Constraints

- 运行 Gradle 验证前，使用 SDKMAN 切换到 Java 21：`sdk use java 21.0.9-amzn`。
- 非交互式 shell 可能需要先初始化 SDKMAN：`source ~/.sdkman/bin/sdkman-init.sh`。

## 📦 版本升级标准化指南

当接收到升级插件版本的任务时，AI 必须更新以下相关文件：

### 1. 修改核心版本号
- **文件**：[gradle.properties](file:///Users/chenping/mac_2026/dev/CodeSense-AI/gradle.properties)
- **操作**：将 `pluginVersion = x.x.x` 更新为新的目标版本号。

### 2. 更新插件变更日志 (Change Notes)
- **文件**：[plugin.xml](file:///Users/chenping/mac_2026/dev/CodeSense-AI/src/main/resources/META-INF/plugin.xml)
- **操作**：在 `<change-notes>` CDATA 首行，添加新版本号及其更新点的 HTML 结构，例如：
  ```html
  <h3>x.x.x (YYYY-MM-DD)</h3>
  <ul>
      <li><b>模块/功能名</b>: 具体更新说明</li>
  </ul>
  ```

### 3. 更新项目版本历史
- **文件**：[README.md](file:///Users/chenping/mac_2026/dev/CodeSense-AI/README.md)
- **操作**：
  1. 将原本标记有 `（当前版本）` 的旧版本历史标题（例如 `### v0.3.1（当前版本）`）中的 `（当前版本）` 后缀移除，修改为 `### v0.3.1`。
  2. 在 `## 📋 版本历史` 最上方，新增当前目标版本的 `### vx.x.x（当前版本）` 条目，并列出对应的更新特性列表。

### 4. 核心链路计划约束
- **全局规则**：在执行任何核心链路代码或配置变更前，必须首先在根目录的 `docs/ai/` 目录下输出并保存 Markdown 格式的执行计划，并采用唯一的命名方式（例如 `docs/ai/version_upgrade_x_x_x_plan.md`）。

### 5. 构建与验证
- **验证命令**：
  在初始化 SDKMAN 并切换至 Java 21 后，执行 `./gradlew buildPlugin`。
  确保构建输出中，`build/distributions/` 文件夹下成功生成了对应新版本号的 `.zip` 插件包。
