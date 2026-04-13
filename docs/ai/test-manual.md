# CodeSense AI 插件 — 全面测试手册

> 文档编号: `test-manual`
> 创建时间: 2026-04-13
> 适用版本: v0.1.0

---

## 〇、前置准备

### 环境要求
```bash
# 1. 切换 JDK
sdk use java 21.0.9-amzn

# 2. 编译（确认 BUILD SUCCESSFUL）
./gradlew build

# 3. 启动沙盒 IDE（会自动下载并启动一个带插件的 IDEA 实例）
./gradlew runIde
```

### 沙盒 IDE 中准备测试项目
在沙盒 IDEA 启动后，**打开或创建**一个包含以下内容的 Java 项目进行测试：

创建文件 `src/main/java/com/example/UserController.java`:
```java
package com.example;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/update-balance")
    public String updateBalance(@RequestParam Long userId, @RequestParam double amount) {
        userService.updateUserBalance(userId, amount);
        return "success";
    }

    @GetMapping("/info")
    public String getUserInfo(@RequestParam Long userId) {
        return userService.getUserInfo(userId);
    }
}
```

创建文件 `src/main/java/com/example/UserService.java`:
```java
package com.example;

public class UserService {

    private final UserDao userDao;

    public UserService(UserDao userDao) {
        this.userDao = userDao;
    }

    public void updateUserBalance(Long userId, double amount) {
        // 查询用户
        String user = userDao.findById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在: " + userId);
        }
        // 更新余额
        userDao.updateBalance(userId, amount);
        // TODO: 发送通知
    }

    public String getUserInfo(Long userId) {
        return userDao.findById(userId);
    }
}
```

创建文件 `src/main/java/com/example/UserDao.java`:
```java
package com.example;

public class UserDao {
    public String findById(Long userId) {
        return "user_" + userId;
    }

    public void updateBalance(Long userId, double amount) {
        // 模拟数据库操作
    }
}
```

> ⚠️ 不需要能运行，只需要代码结构正确、能被 IntelliJ 索引即可。

---

## 一、插件加载测试

### TC-001: 插件是否成功加载
| 项目 | 内容 |
|------|------|
| **步骤** | 启动沙盒 IDE 后观察 |
| **预期** | 1. 右侧侧边栏出现 "CodeSense AI" 图标<br>2. 点击后展开 Chat 面板<br>3. 显示欢迎信息 "你好！我是 CodeSense AI" |
| **通过** | ☐ |

### TC-002: Settings 页面可访问
| 项目 | 内容 |
|------|------|
| **步骤** | 打开 Settings (Cmd+,) → Tools → CodeSense AI |
| **预期** | 1. 显示"模型配置"区域，预置 MiniMax 和 GLM<br>2. 显示"功能设置"区域，含 5 个选项<br>3. [添加] [编辑] [删除] [设为默认] [测试连接] 按钮可见 |
| **通过** | ☐ |

---

## 二、Settings 模块测试

### TC-003: 添加新的模型配置
| 项目 | 内容 |
|------|------|
| **步骤** | 1. Settings → CodeSense AI → 点击 [添加]<br>2. 选择 "DeepSeek"<br>3. 填写 API Key<br>4. 点击 [保存] |
| **预期** | 新增的 DeepSeek 出现在列表中 |
| **通过** | ☐ |

### TC-004: 编辑已有模型
| 项目 | 内容 |
|------|------|
| **步骤** | 选中一个模型 → 点击 [编辑] → 修改模型名称 → 保存 |
| **预期** | 列表中显示更新后的名称 |
| **通过** | ☐ |

### TC-005: 设为默认模型
| 项目 | 内容 |
|------|------|
| **步骤** | 选中一个非默认模型 → 点击 [设为默认] |
| **预期** | 下拉框中默认模型改变 |
| **通过** | ☐ |

### TC-006: 删除模型
| 项目 | 内容 |
|------|------|
| **步骤** | 选中一个模型 → 点击 [删除] |
| **预期** | 模型从列表中移除 |
| **通过** | ☐ |

### TC-007: 测试连接（需要有效 API Key）
| 项目 | 内容 |
|------|------|
| **步骤** | 填入有效 API Key → 点击 [测试连接] |
| **预期** | 成功提示或失败错误信息（不应崩溃） |
| **通过** | ☐ |

### TC-008: 设置持久化
| 项目 | 内容 |
|------|------|
| **步骤** | 修改设置 → Apply → 关闭设置 → 重新打开设置页 |
| **预期** | 之前的修改仍然保留 |
| **通过** | ☐ |

---

## 三、Chat 对话测试（需要有效 API Key）

### TC-009: 普通对话
| 项目 | 内容 |
|------|------|
| **前置** | 已配置有效 API Key 并设为默认 |
| **步骤** | 1. 打开 Chat 面板<br>2. 输入 "请解释什么是单例模式"<br>3. 按 Enter 发送 |
| **预期** | 1. 用户消息出现在面板中<br>2. AI 开始流式回复<br>3. 回复内容为中文，格式合理<br>4. 回复完成后输入框可继续输入 |
| **通过** | ☐ |

### TC-010: Shift+Enter 换行
| 项目 | 内容 |
|------|------|
| **步骤** | 在输入框按 Shift+Enter |
| **预期** | 换行而不是发送 |
| **通过** | ☐ |

### TC-011: 空消息拦截
| 项目 | 内容 |
|------|------|
| **步骤** | 输入框为空时按 Enter |
| **预期** | 不发送，无任何反应 |
| **通过** | ☐ |

### TC-012: 无 API Key 时的错误处理
| 项目 | 内容 |
|------|------|
| **步骤** | 清空 API Key → 尝试发送消息 |
| **预期** | 显示友好的错误提示（非崩溃） |
| **通过** | ☐ |

---

## 四、Agent 模式测试（需要有效 API Key）

### TC-013: Agent 模式切换
| 项目 | 内容 |
|------|------|
| **步骤** | 点击 Chat 面板底部的 "🤖 Agent" 按钮 |
| **预期** | 1. 按钮变为选中状态<br>2. 状态栏显示 "Agent 模式（可使用工具）" |
| **通过** | ☐ |

### TC-014: Agent 读取文件
| 项目 | 内容 |
|------|------|
| **步骤** | Agent 模式下输入 "请读取 UserService.java 的内容" |
| **预期** | 1. 界面显示工具调用过程：🔧 read_file<br>2. 显示工具执行结果：✅ read_file<br>3. AI 给出基于文件内容的回答 |
| **通过** | ☐ |

### TC-015: Agent 列出文件
| 项目 | 内容 |
|------|------|
| **步骤** | Agent 模式下输入 "列出项目 src 目录结构" |
| **预期** | 使用 list_files 工具并展示目录结构 |
| **通过** | ☐ |

### TC-016: Agent 搜索代码
| 项目 | 内容 |
|------|------|
| **步骤** | Agent 模式下输入 "搜索项目中所有使用了 updateBalance 的地方" |
| **预期** | 使用 search_code 工具并展示搜索结果 |
| **通过** | ☐ |

---

## 五、代码审查测试（需要有效 API Key + Git 仓库）

### TC-017: 单文件审查
| 项目 | 内容 |
|------|------|
| **前置** | 项目已 git init 且 UserService.java 有未提交修改 |
| **步骤** | 1. 打开 UserService.java<br>2. 右键 → CodeSense AI → 审查此文件 |
| **预期** | 1. 右侧打开新的 "Review: UserService.java" Tab<br>2. 显示加载提示<br>3. AI 开始流式输出审查意见<br>4. 包含 Bug/性能/安全/规范等维度 |
| **通过** | ☐ |

### TC-018: 无修改文件审查
| 项目 | 内容 |
|------|------|
| **步骤** | 对没有 Git 变动的文件右键审查 |
| **预期** | 提示 "当前文件没有未提交的 Git 变动" |
| **通过** | ☐ |

### TC-019: 批量 Git 审查
| 项目 | 内容 |
|------|------|
| **前置** | 项目有多个文件有未提交的修改 |
| **步骤** | 右键 → CodeSense AI → 审查全量 Git 未提交变更 |
| **预期** | 1. 打开 "Review: Git Changes" Tab<br>2. 显示文件数量<br>3. AI 流式输出批量审查意见 |
| **通过** | ☐ |

---

## 六、影响范围分析测试

### TC-020: 分析指定方法影响
| 项目 | 内容 |
|------|------|
| **步骤** | 1. 打开 UserService.java<br>2. 将光标放在 `updateUserBalance` 方法名上<br>3. 右键 → CodeSense AI → 分析影响范围 |
| **预期** | 1. 右侧打开 "🔍 UserService.updateUserBalance" Tab<br>2. 显示加载提示<br>3. 生成包含以下内容的报告：<br>   — 方法详情<br>   — 向上调用链<br>   — 受影响入口点汇总<br>   — AI 风险评估（如已配置 API Key） |
| **通过** | ☐ |

### TC-021: 光标不在方法上
| 项目 | 内容 |
|------|------|
| **步骤** | 将光标放在空行或 import 语句上 → 右键查看 |
| **预期** | "分析影响范围" 菜单项变灰或提示 "请将光标放在方法名上" |
| **通过** | ☐ |

### TC-022: 报告复制 Markdown
| 项目 | 内容 |
|------|------|
| **前置** | 已生成一份影响分析报告 |
| **步骤** | 点击报告面板上方的 [📋 复制 Markdown] |
| **预期** | 剪贴板中包含原始 Markdown 文本，可粘贴到其他编辑器中 |
| **通过** | ☐ |

### TC-023: 报告导出 .md 文件
| 项目 | 内容 |
|------|------|
| **步骤** | 点击 [💾 导出 .md] → 选择保存路径 → 保存 |
| **预期** | 生成的 .md 文件内容完整，可用 Markdown 编辑器正常查看 |
| **通过** | ☐ |

---

## 七、代码解释测试（需要有效 API Key）

### TC-024: 解释选中代码
| 项目 | 内容 |
|------|------|
| **步骤** | 1. 选中 `updateUserBalance` 方法的全部代码<br>2. 右键 → CodeSense AI → 解释代码 |
| **预期** | 1. 打开新 Tab "💡 解释: UserService.java"<br>2. AI 流式输出代码解释<br>3. 内容包含：功能概述、逐行解析、设计模式、注意事项 |
| **通过** | ☐ |

### TC-025: 未选中代码时
| 项目 | 内容 |
|------|------|
| **步骤** | 未选中任何代码时右键查看菜单 |
| **预期** | "解释代码" 和 "咨询 AI" 菜单项不可见或变灰 |
| **通过** | ☐ |

---

## 八、异常场景测试

### TC-026: 网络超时
| 项目 | 内容 |
|------|------|
| **步骤** | 断开网络 → 发送 Chat 消息 |
| **预期** | 显示错误提示（含超时信息），不崩溃，输入框可恢复 |
| **通过** | ☐ |

### TC-027: 无效 API Key
| 项目 | 内容 |
|------|------|
| **步骤** | 设置一个无效的 API Key → 发送消息 |
| **预期** | 显示 401/403 错误提示，不崩溃 |
| **通过** | ☐ |

### TC-028: 非 Git 项目审查
| 项目 | 内容 |
|------|------|
| **步骤** | 在没有 .git 的项目中右键审查文件 |
| **预期** | 提示没有 Git 变动或友好错误信息 |
| **通过** | ☐ |

### TC-029: 并发操作
| 项目 | 内容 |
|------|------|
| **步骤** | 同时触发审查 + 影响分析 |
| **预期** | 各自独立运行，不冲突，IDE 不冻结 |
| **通过** | ☐ |

### TC-030: IDE 关闭后重启
| 项目 | 内容 |
|------|------|
| **步骤** | 关闭沙盒 IDE → 重新 `./gradlew runIde` |
| **预期** | 插件正常加载，之前保存的 Settings 仍在 |
| **通过** | ☐ |

---

## 测试结果汇总

| 类别 | 用例数 | 通过 | 失败 | 备注 |
|------|-------|------|------|------|
| 插件加载 | 2 | | | |
| Settings | 6 | | | |
| Chat 对话 | 4 | | | |
| Agent 模式 | 4 | | | |
| 代码审查 | 3 | | | |
| 影响分析 | 4 | | | |
| 代码解释 | 2 | | | |
| 异常场景 | 5 | | | |
| **合计** | **30** | | | |

---

## 快速启动检查清单

```bash
# Step 1: 切换 JDK
sdk use java 21.0.9-amzn

# Step 2: 构建
./gradlew build

# Step 3: 启动沙盒 IDEA
./gradlew runIde

# Step 4: 在沙盒中
# → Settings → Tools → CodeSense AI → 配置 API Key
# → 打开/创建一个 Java 项目（参照上方测试代码）
# → git init && git add . && 修改文件 → 开始测试
```
