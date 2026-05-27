# 版本升级到 0.3.2 执行计划 (已更新)

本计划旨在将 CodeSense AI 插件版本从 `0.3.1` 升级至 `0.3.2`，并在变更日志和项目说明中完整补充刚刚优化完成的**全新 Markdown 渲染引擎**与 **Thinking 深度思考渲染**，同时在 `AGENTS.md` 中沉淀标准化的版本升级操作指南。

## 待修改的文件与内容

为了将版本完整地更新到 `0.3.2`，需要对以下文件进行修改：

---

### 1. 构建配置

#### [MODIFY] [gradle.properties](file:///Users/chenping/mac_2026/dev/CodeSense-AI/gradle.properties) (已完成)
- **修改点**：
  将 `pluginVersion = 0.3.1` 修改为 `pluginVersion = 0.3.2`。

---

### 2. 插件元数据

#### [MODIFY] [plugin.xml](file:///Users/chenping/mac_2026/dev/CodeSense-AI/src/main/resources/META-INF/plugin.xml)
- **修改点**：
  在 `<change-notes>` 标签的首行（`0.3.1` 之上）添加完整的 `0.3.2` 的变更记录，突出 Markdown 渲染和 Thinking 过程展示支持：
  ```html
  <h3>0.3.2 (2026-05-27)</h3>
  <ul>
      <li><b>Markdown 渲染引擎</b>: Chat 面板手写实现极简高性能 Markdown 渲染器，完美支持代码块（支持语法高亮）、多级标题、表格、无序列表、水平分割线，以及内联粗体、斜体、代码和超链接</li>
      <li><b>Thinking 深度思考渲染</b>: 深度适配 DeepSeek-R1 等具有思考链的大模型，专设思维链（Thinking）展示区域，搭配精美的主题自适应微调边框和背景</li>
      <li><b>AI 协作优化</b>: 完善 <code>AGENTS.md</code>，提供清晰的版本升级与项目构建标准化操作指南，提升后续 AI 协作效率</li>
  </ul>
  ```

---

### 3. 项目说明文档

#### [MODIFY] [README.md](file:///Users/chenping/mac_2026/dev/CodeSense-AI/README.md)
- **修改点**：
  1. 将 `### v0.3.1（当前版本）` 变更为 `### v0.3.1`。
  2. 在其上方新增 `0.3.2` 版本记录，描述上述两项重磅功能：
     ```markdown
     ### v0.3.2（当前版本）

     **💬 Chat 面板 Markdown 解析 & Thinking 深度思考渲染支持**

     - ✅ **全新 Markdown 渲染引擎** — Chat 面板手写实现极简高性能 Markdown 渲染器，完美支持代码块（支持语法高亮）、多级标题、表格、无序列表、水平分割线，以及内联粗体、斜体、代码和超链接
     - ✅ **Thinking 深度思考渲染** — 深度适配 DeepSeek-R1 等具有思考链的大模型，专设思维链（Thinking）展示区域，搭配精美的主题自适应微调边框和背景，提供极致的思考过程查阅体验
     - ✅ **AI 协作优化** — 补充与完善 `AGENTS.md`，为 AI 开发者提供更详尽的本地构建、JDK 版本切换及版本升级指导
     ```

---

### 4. Agent 指南文档

#### [MODIFY] [AGENTS.md](file:///Users/chenping/mac_2026/dev/CodeSense-AI/AGENTS.md) (已完成)
- **修改点**：
  增加“版本升级指南”章节。详细列出每次升级版本时，需要更新的三个核心文件以及更新的格式要求。

---

## 验证计划

为了验证升级是否成功，且没有引入任何配置或构建错误，我们将进行以下验证：

### 自动化验证
1. 在 Terminal 中执行 SDKMAN 初始化并切换到 Java 21：
   `source ~/.sdkman/bin/sdkman-init.sh && sdk use java 21.0.9-amzn`
2. 运行 Gradle 编译与插件验证：
   `./gradlew buildPlugin`
   检查是否成功输出 `build/distributions/codesense-ai-plugin-0.3.2.zip`。
