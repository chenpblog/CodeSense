# CodeSense AI - 基于 UI 的 JSON to Java Bean 生成器设计与执行计划

## 1. 需求背景与目标
为提升接口对接和对象声明的效率，设计并实现一个基于可视化 UI（Tree Table）的工具。
用户可以通过输入中英文字段名及其类型构造 JSON 结构对象，并自动生成标准化测试 JSON 报文；随后交由集成的大模型（LLM）将其分析并转换为符合工程规范的 Java Bean 类（支持 Lombok、Javadoc 和内部类嵌套）。

## 2. 交互流程设计 (UI Flow)

### 2.1 入口设定 (Entry Point)
支持多种快捷入口，方便用户随时唤起该工具：
1. **项目目录视图右键菜单**：项目结构 (Project View) 下任意包/文件夹右键 -> `New` -> `CodeSense Design: JSON to Java Bean`。
2. **代码编辑器内容区**：快捷键 `Alt+Insert` (Generate) -> 选择 `CodeSense: Generate from JSON Design`。
3. **顶层主菜单 (Main Menu)**：顶部系统菜单条 `Tools` -> `CodeSense AI` -> 增加子菜单项 `JSON to Java Bean 设计器`。

### 2.2 第一步：设计与配置界面 (JSON Design Dialog)
此界面为核心的树形表格（Tree Table）区域：
* **核心组件**：
  * 使用 IntelliJ Platform 提供的 `TreeTableView` 或 `JBTreeTable` 组件。
  * **列定义**：
    1. **字段名 (Field Name)**: 文本输入框。支持中文（如“用户名”）或英文（如“userName”）。
    2. **数据类型 (Type)**: 下拉选择框。包含基础类型如 `String`, `Integer`, `Long`, `Boolean`, `BigDecimal`, 以及复杂类型 `Object`, `List`, `List<Object>`, `List<String>` 等。支持自定义扩展或输入。
    3. **描述/说明 (Description)**: 文本输入框（选填）。辅助 AI 生成注释更精确。
* **操作区 (Toolbar)**：
  * `+` 新增同级节点 (Add sibling)
  * `++` 新增子节点 (Add child) —— 仅当选中节点类型为 `Object` 或包含对象的 `List` 时可用。
  * `-` 删除所选节点
* **底部配置区与操作按钮**：
  * **Root Element Type (根节点类型)**: 下拉框，可选 `Object ({...})`（默认）或 `List ([{...}])`。
    * *说明*：若选 `List`，可视化表格设计的实际上是数组内部单个元素的结构。
  * **Class Name (类名)**: 输入生成的 Root Class（或 List 内的元素 Class）的名称（例如 `UserInfoRes`）。
  * **`Generate Demo & Java Bean` (生成数据与代码)**：点击后触发分析与拼装。

### 2.3 拼装与 AI 处理阶段
1. **JSON 生成**：遍历用户编辑的 TreeTableView 结构，根据字段类型赋默认的 Demo 值（例如：String 提供 "demo_string"，Int 提供 `0`，Object 递归构建）。如果是未填写的英文字段，取中文拼音或直接将中文输出为 JSON Key。
2. **发起后台任务**：使用 IntelliJ 的 `ProgressManager` 开启后台阻塞任务（进度条提示："正在请求大模型生成代码..."）。
3. **调用 LLM**：将拼装好的 JSON 发送给插件当前配置的大模型完成代码转化。

### 2.4 第二步：结果预览与导出界面 (Preview & Export Dialog)
大模型成功响应后，弹出最终结果界面的 Dialog，结构如下：
* **主区域分为左右/上下拆分视图 (Splitter)**：
  * **左侧面板**：JSON Demo 预览区域（`Editor` 组件，带有 JSON 语法高亮）。
  * **右侧面板**：Java Bean 源码预览区域（带有 Java 语法高亮，可以二次编辑或微调）。
* **底部操作按钮区**：
  1. **`Copy JSON`**: 一键复制 JSON Demo 到剪贴板。
  2. **`Copy Java Bean`**: 一键复制右侧的 Java Bean 源码到剪贴板。
  3. **`Generate to Project File...`**: 
     * 点击后弹出一个包选择器 (Package Chooser Dialog)。
     * 如果用户从某个具体的 Package 单击右键进入的，这里会带入默认包路径。
     * 选择确认后，自动在该包下写入对应的 `.java` 文件，并自动在 IDE 内打开以供查看。

---

## 3. 核心 Prompt 提示词设计
在将 JSON 发送给 AI 时，将采用以下强约束的系统提示词：
```text
你是一个专业的 Java 架构师。请根据以下 JSON 结构与字段特征，生成一个标准的 Java 类（Java Bean / DTO / VO）。

要求与约束（务必遵守）：
1. 框架依赖：仅使用 Lombok 的 @Data 注解，不需要写 Get/Set 方法。
2. 类结构：若 JSON 中包含多层的 Object，强行使用 【静态内部类（public static class）】 的方式实现嵌套，不拆分成多个外部类文件。
3. 字典翻译：若 JSON Key 中存在中文（如 "用户名"），请用标准的驼峰格式将其翻译为英文变量（如 username），并将中文放进属性的 Javadoc 注释中。如果有描述信息，合并到注释里。
4. 纯净代码：只输出 Java 源码文本，不要带多余的 Markdown 代码块或额外说明文字，也不要带有 package 路径（包路径由客户端补全）。
```

## 4. 示例演示 (Demo Showcase)

### 4.1 用户在 Tree Table 的输入示例
假设用户最终想生成的 Root 类名为 `UserInfoRes`，在可视化表格中录入了以下结构：
- **用户名** (类型: `String`, 描述: "登录名")
- **年龄** (类型: `Integer`, 描述: 空白)
- **联系方式** (类型: `Object`, 描述: "用户联系信息")
  - **手机号** (类型: `String`, 描述: "11位数字")
  - **email** (类型: `String`, 描述: "电子邮箱")
- **tags** (类型: `List<String>`, 描述: "用户标签列表")

### 4.2 插件拼接出的 JSON Demo (结果左侧预览区)
```json
{
  "用户名": "demo_string",
  "年龄": 0,
  "联系方式": {
    "手机号": "demo_string",
    "email": "demo_string"
  },
  "tags": [
    "demo_string"
  ]
}
```

### 4.3 AI 生成的预期 Java Bean (结果右侧预览区)
```java
import lombok.Data;
import java.util.List;

/**
 * UserInfoRes 
 */
@Data
public class UserInfoRes {

    /**
     * 用户名 (登录名)
     */
    private String username;

    /**
     * 年龄
     */
    private Integer age;

    /**
     * 联系方式 (用户联系信息)
     */
    private ContactInfo contactInfo;

    /**
     * tags (用户标签列表)
     */
    private List<String> tags;

    @Data
    public static class ContactInfo {
        /**
         * 手机号 (11位数字)
         */
        private String phoneNumber;

        /**
         * email (电子邮箱)
         */
        private String email;
    }
}
```

## 5. 技术链路落地规划
1. **UI 组件库开发**：实现并自定义 `JsonTreeTableModel`，包装 `JBTreeTable` 的数据模型。
2. **数据转换器**：实现 `Tree -> JsonNode` 和 `JsonNode -> Pretty String` 的相关逻辑（可能需要引入 Jackson 解析器或依赖插件已有的 gson/fastjson 等依赖）。
3. **IDE Action & Dialog**：实现配置框、预览框。
4. **包路径推断与文件写入**：使用 JetBrains OpenAPI (`PsiDirectory`, `PsiJavaFile`, `WriteCommandAction`) 写入用户工程源文件目录。
