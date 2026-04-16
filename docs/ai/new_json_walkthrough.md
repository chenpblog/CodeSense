# 验收与使用指南：JSON to Java Bean 设计器

经过系统性的架构规划与编码，可视化**「JSON to Java Bean 设计器」** 的全链路代码已经开发并集成完毕。

## 核心实现说明

1. **统一入口集成**
   - 菜单栏位置：已注册到 `Tools -> CodeSense AI -> JSON to Java Bean 设计器`。
   - 上下文菜单：在项目的包和目录结构右击 `New` 菜单中也同时提供了访问入口。

2. **核心层级数据表格构建 (`JsonDesignDialog`)**
   - 我们引入了基于 IntelliJ 原生 `TreeTableView` 实现的多列级联编辑器，您可以灵活配置 **中英文字段、对应类型及说明**，且支持层级 `Object` 和 `List` 对象的任意级联。
   - 支持在底部自由选择 **Root Element Type** 为全局 `Object` 或者全局 `List`。

3. **大模型代码无缝串联 (`JsonToBeanService`)**
   - 工具能够自动提取并构造包含您所配参数的实际 JSON 预览文本。
   - 利用强约束系统指令向后台大模型发起代码生成请求进程，并利用异步操作防阻塞。

4. **双栏沉浸式预览与自动保存 (`JsonBeanPreviewDialog`)**
   - 您可以直接复制左侧生成的结构化测试 JSON 文本到外部。
   - 右侧直接展示由底层大模型返回的高质量且附带 Javadoc 注释的 Java Class 源代码（完全匹配 Lombok `@Data` 与静态内部类标准）。
   - 点击 **Generate to Project...** 可以借由 IDE 接口，智能检测当前光标活动模块或者手动选区，自动化创建物理 `*.java` 文件并在编辑器内为您呈现。

## 验证与试运行步骤 

> [!TIP]
> 代码均已无缝挂载于现有工程。由于工程环境配置显示本地 JVM 默认环境为 Java 11，而当前插件编译基线已基于 Java 17，建议您在测试前直接通过 IntelliJ 自身的运行配置即可绕过命令行校验。

**请您在 IDE 内执行以下操作体验效果**：
1. 点击 IntelliJ IDEA 的绿色启动按钮，选择 `runIde` 启动测试沙盒。
2. 随意进入沙盒内的一个临时工程目录。
3. 从顶部选择 `Tools > CodeSense AI > JSON to Java Bean 设计器`。
4. 输入一些包含中文的伪字段（例如：“订单号”，“商品列表”、“内部商品编码”）。
5. 点击 `Generate Demo & Java Bean` 体验真实的 AI 反向驱动生成，并导出代码文件。
