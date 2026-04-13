# Phase 4 执行计划 — Agent 引擎（Tool Calling / ReAct）

> 文档编号: `phase4-implementation-plan`
> 创建时间: 2026-04-13
> 状态: 执行中

---

## 一、Phase 4 目标

实现 ReAct（Reasoning + Acting）循环的 **AI Agent 引擎**，让 LLM 具备自主使用工具的能力：

1. **Tool 接口与注册表**：定义统一的 Tool 接口和 ToolRegistry
2. **8 个核心工具**：read_file, write_file, search_code, run_command, get_git_diff, analyze_call_hierarchy, get_file_structure, list_files
3. **AgentExecutor**：ReAct 循环执行器，支持多轮工具调用
4. **AgentContext**：上下文管理（对话历史、工具调用记录）
5. **Chat UI 升级**：展示工具调用过程

---

## 二、详细实施步骤

### Step 1: Tool 基础接口 + ToolRegistry
### Step 2: 实现 8 个核心 Tool
### Step 3: AgentExecutor ReAct 循环
### Step 4: AgentContext 上下文管理
### Step 5: 升级 ChatPanel 展示工具调用
### Step 6: 构建验证
