# 影响范围分析 — 报告格式 & 操作流程设计

> 文档编号: `impact-analysis-report-design`
> 创建时间: 2026-04-03

---

## 一、两种分析模式

| 模式 | 触发方式 | 说明 |
|------|---------|------|
| **模式 A — Git Diff 批量分析** | VCS 菜单 / 工具栏按钮 | 对比分支或时间范围，自动发现所有被修改的方法，批量分析 |
| **模式 B — 指定方法分析** | 在方法名上右键 | 用户光标定位到某个方法，直接分析该方法的完整调用链 |

---

## 二、用户 UI 操作步骤

### 模式 A：Git Diff 批量分析

```
操作步骤：

1️⃣  点击顶部菜单 → Tools → CodeSense AI → 分析变更影响范围
    （或使用快捷键 Ctrl+Shift+I）

2️⃣  弹出配置对话框：
    ┌──────────────────────────────────────────┐
    │  📊 影响范围分析                          │
    │                                          │
    │  对比方式:  ○ 分支对比   ○ 时间范围       │
    │                                          │
    │  ── 分支对比 ──                           │
    │  当前分支:  [feature/user-refactor  ▾]    │
    │  目标分支:  [main                  ▾]    │
    │                                          │
    │  ── 或 时间范围 ──                        │
    │  开始时间:  [2026-04-01 00:00     ]      │
    │  结束时间:  [2026-04-03 14:00     ]      │
    │                                          │
    │  ── 分析选项 ──                           │
    │  最大追溯深度:  [10       ]               │
    │  ☑ 包含代码片段                           │
    │  ☑ 使用 AI 生成风险评估                   │
    │  ☐ 仅分析 Java/Kotlin 文件                │
    │                                          │
    │          [取消]        [开始分析]          │
    └──────────────────────────────────────────┘

3️⃣  插件在后台执行分析，底部状态栏显示进度：
    「CodeSense AI: 正在分析... 已发现 5 个修改方法，正在追溯调用链 (3/5)」

4️⃣  分析完成后，在右侧侧边栏自动打开 "影响分析报告" Tab 页：
    ┌──────────────────────────────────────────────────────┐
    │ 📊 影响分析报告  feature/user-refactor → main       │
    │ ─────────────────────────────────────────────────── │
    │                                                      │
    │  [Markdown 渲染的完整报告内容，见下方模板]            │
    │                                                      │
    │ ─────────────────────────────────────────────────── │
    │ 工具栏:  [📋 复制 Markdown]  [💾 导出文件]  [🔄 刷新] │
    └──────────────────────────────────────────────────────┘

5️⃣  点击 [💾 导出文件] → 选择保存路径 → 生成 .md 文件
```

---

### 模式 B：指定方法分析

```
操作步骤：

1️⃣  在编辑器中，将光标放在某个方法名上
    例如光标在 "updateUserBalance" 上

    public void updateUserBalance(Long userId, BigDecimal amount) {
                 ^^^^^^^^^^^^^^^^  ← 光标在此
        ...
    }

2️⃣  右键 → CodeSense AI → 分析此方法的影响范围
    （或使用快捷键 Ctrl+Shift+M）

3️⃣  弹出轻量确认弹窗（可选跳过）：
    ┌──────────────────────────────────────┐
    │  🔍 分析方法影响范围                  │
    │                                      │
    │  方法: UserService.updateUserBalance  │
    │  分析方向:  ○ 仅向上(谁调用了我)      │
    │            ○ 仅向下(我调用了谁)      │
    │            ● 双向(完整链路)          │
    │  最大深度:  [10    ]                 │
    │  ☑ 包含代码片段                      │
    │  ☑ 使用 AI 生成风险评估              │
    │                                      │
    │        [取消]       [开始分析]        │
    └──────────────────────────────────────┘

4️⃣  右侧侧边栏打开报告（与模式 A 同一个面板）

5️⃣  同样支持 [📋 复制] 和 [💾 导出]
```

---

## 三、Markdown 报告格式模板

### 模式 A：Git Diff 批量分析报告

```markdown
# 📊 影响范围分析报告

| 项目 | 值 |
|------|---|
| **分析模式** | Git Diff 分支对比 |
| **当前分支** | `feature/user-refactor` |
| **目标分支** | `main` |
| **分析时间** | 2026-04-03 14:30:00 |
| **变更文件数** | 4 |
| **变更方法数** | 6 |
| **受影响入口点** | 3 |

---

## 一、变更概要

| # | 文件 | 方法 | 变更类型 | 行号 |
|---|------|------|---------|------|
| 1 | `UserService.java` | `updateUserBalance()` | MODIFIED | L45-L72 |
| 2 | `UserService.java` | `getUserInfo()` | MODIFIED | L80-L95 |
| 3 | `UserDao.java` | `updateBalance()` | MODIFIED | L23-L30 |
| 4 | `OrderService.java` | `createOrder()` | MODIFIED | L110-L140 |
| 5 | `NotifyService.java` | `sendBalanceNotify()` | ADDED | L1-L35 |
| 6 | `UserController.java` | `updateBalance()` | MODIFIED | L55-L68 |

---

## 二、调用链分析

### 2.1 UserService.updateUserBalance()

**向上调用链（谁调用了我）：**

```
[HTTP] POST /api/user/balance/update
  └── UserController.updateBalance()                     ← @PostMapping
        └── ★ UserService.updateUserBalance()            ← 被修改方法
```
```
[HTTP] POST /api/order/create
  └── OrderController.createOrder()                      ← @PostMapping
        └── OrderService.createOrder()
              └── ★ UserService.updateUserBalance()      ← 被修改方法
```
```
[定时任务] 每日 02:00
  └── SettlementJob.dailySettle()                        ← @Scheduled
        └── SettlementService.processSettle()
              └── ★ UserService.updateUserBalance()      ← 被修改方法
```

**向下调用链（我调用了谁）：**

```
★ UserService.updateUserBalance()
  ├── UserDao.updateBalance()                            ← DB操作
  ├── UserDao.selectById()                               ← DB操作
  ├── BalanceLogService.recordChange()                   ← 日志记录
  └── NotifyService.sendBalanceNotify()                  ← 新增调用
```

**关键代码片段：**

```java
// UserService.java (L45-L72) — 修改前后对比

// ===== 删除的代码 =====
- public void updateUserBalance(Long userId, BigDecimal amount) {
-     UserEntity user = userDao.selectById(userId);
-     user.setBalance(user.getBalance().add(amount));
-     userDao.updateBalance(user);
- }

// ===== 新增的代码 =====
+ public void updateUserBalance(Long userId, BigDecimal amount) {
+     UserEntity user = userDao.selectById(userId);
+     if (user == null) {
+         throw new BusinessException("用户不存在: " + userId);
+     }
+     BigDecimal oldBalance = user.getBalance();
+     BigDecimal newBalance = oldBalance.add(amount);
+     if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
+         throw new BusinessException("余额不足");
+     }
+     user.setBalance(newBalance);
+     userDao.updateBalance(user);
+     // 新增：记录余额变动日志
+     balanceLogService.recordChange(userId, oldBalance, newBalance);
+     // 新增：发送余额变动通知
+     notifyService.sendBalanceNotify(userId, oldBalance, newBalance);
+ }
```

---

### 2.2 UserService.getUserInfo()

**向上调用链：**

```
[HTTP] GET /api/user/info/{id}
  └── UserController.getUserInfo()                       ← @GetMapping
        └── ★ UserService.getUserInfo()                  ← 被修改方法
```
```
[HTTP] GET /api/order/detail/{id}
  └── OrderController.getOrderDetail()                   ← @GetMapping
        └── OrderService.getOrderDetail()
              └── ★ UserService.getUserInfo()             ← 被修改方法
```

**向下调用链：**

```
★ UserService.getUserInfo()
  ├── UserDao.selectById()                               ← DB操作
  └── UserCacheService.getFromCache()                    ← 新增缓存查询
```

**关键代码片段：**

```java
// UserService.java (L80-L95)

+ public UserVO getUserInfo(Long userId) {
+     // 新增：优先从缓存获取
+     UserVO cached = userCacheService.getFromCache(userId);
+     if (cached != null) {
+         return cached;
+     }
+     UserEntity user = userDao.selectById(userId);
+     UserVO vo = UserConverter.toVO(user);
+     userCacheService.putToCache(userId, vo);
+     return vo;
+ }
```

---

*（其余方法按相同格式继续...）*

---

## 三、受影响入口点汇总

| # | 入口类型 | 方法 | 路径/触发条件 | 影响的变更方法 |
|---|---------|------|-------------|--------------|
| 1 | HTTP API | `UserController.updateBalance()` | `POST /api/user/balance/update` | `updateUserBalance()` |
| 2 | HTTP API | `UserController.getUserInfo()` | `GET /api/user/info/{id}` | `getUserInfo()` |
| 3 | HTTP API | `OrderController.createOrder()` | `POST /api/order/create` | `updateUserBalance()` |
| 4 | HTTP API | `OrderController.getOrderDetail()` | `GET /api/order/detail/{id}` | `getUserInfo()` |
| 5 | 定时任务 | `SettlementJob.dailySettle()` | `@Scheduled(cron="0 0 2 * * ?")` | `updateUserBalance()` |

---

## 四、AI 风险评估

### 4.1 风险等级: ⚠️ 中等风险

### 4.2 主要发现

**🐛 潜在问题：**

1. **并发安全风险** — `updateUserBalance()` 中先查后改（`selectById` → `updateBalance`），在高并发场景下存在余额覆盖风险。建议使用数据库乐观锁（版本号）或 `UPDATE ... SET balance = balance + ?` 原子操作。

2. **异常处理不完整** — `notifyService.sendBalanceNotify()` 如果抛出异常，会导致整个余额更新事务回滚。建议将通知逻辑移到事务之外，或使用异步消息队列。

3. **缓存一致性** — `getUserInfo()` 新增了缓存逻辑，但 `updateUserBalance()` 修改了用户数据后未清除缓存，可能导致脏读。

**⚡ 性能影响：**

4. **新增外部调用** — `updateUserBalance()` 新增了 `balanceLogService.recordChange()` 和 `notifyService.sendBalanceNotify()` 两次额外调用，会增加接口响应时间。影响 3 个入口点。

**✅ 正面评价：**

5. 新增了余额不足校验，防止余额为负的业务异常。
6. 新增了余额变动日志记录，有助于问题排查和审计。

### 4.3 建议措施

| 优先级 | 建议 | 涉及方法 |
|-------|------|---------|
| 🔴 高 | 使用乐观锁或原子 SQL 解决并发问题 | `updateUserBalance()` |
| 🔴 高 | `updateUserBalance()` 中更新后清除用户缓存 | `updateUserBalance()`, `getUserInfo()` |
| 🟡 中 | 通知逻辑改为异步，避免影响主流程 | `updateUserBalance()` |
| 🟢 低 | 给 `sendBalanceNotify()` 加 try-catch 降级 | `sendBalanceNotify()` |

---

## 五、分析元信息

| 项目 | 值 |
|------|---|
| 插件版本 | CodeSense AI v1.0.0 |
| LLM 模型 | MiniMax M2-her |
| 分析耗时 | 12.3 秒 |
| 追溯深度 | 10 层 |
| 分析范围 | 全项目 |
```

---

### 模式 B：指定方法分析报告

> 与模式 A 格式 **基本一致**，区别在于：
> - 标题和元信息不同
> - 没有"变更概要"（因为不是基于 diff）
> - 没有代码变更片段（改为展示方法当前完整实现）
> - 只分析指定的 1 个方法

```markdown
# 🔍 方法影响范围分析报告

| 项目 | 值 |
|------|---|
| **分析模式** | 指定方法分析 |
| **目标方法** | `UserService.updateUserBalance(Long, BigDecimal)` |
| **所在文件** | `src/main/java/com/example/service/UserService.java` |
| **分析时间** | 2026-04-03 14:30:00 |
| **上游调用者数** | 5 |
| **下游被调用数** | 4 |
| **受影响入口点** | 3 |

---

## 一、目标方法详情

**完整签名：**
```java
package com.example.service;

public class UserService {

    /**
     * 更新用户余额
     * @param userId 用户ID
     * @param amount 变动金额（正数加/负数减）
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateUserBalance(Long userId, BigDecimal amount) {
        UserEntity user = userDao.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在: " + userId);
        }
        BigDecimal oldBalance = user.getBalance();
        BigDecimal newBalance = oldBalance.add(amount);
        if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("余额不足");
        }
        user.setBalance(newBalance);
        userDao.updateBalance(user);
        balanceLogService.recordChange(userId, oldBalance, newBalance);
        notifyService.sendBalanceNotify(userId, oldBalance, newBalance);
    }
}
```

---

## 二、向上调用链（谁调用了我）

```
[HTTP] POST /api/user/balance/update
  └── UserController.updateBalance()                     ← @PostMapping
        └── ★ UserService.updateUserBalance()            ← 目标方法

[HTTP] POST /api/order/create
  └── OrderController.createOrder()                      ← @PostMapping
        └── OrderService.createOrder()
              └── ★ UserService.updateUserBalance()      ← 目标方法

[定时任务] 每日 02:00
  └── SettlementJob.dailySettle()                        ← @Scheduled
        └── SettlementService.processSettle()
              └── ★ UserService.updateUserBalance()      ← 目标方法
```

## 三、向下调用链（我调用了谁）

```
★ UserService.updateUserBalance()
  ├── UserDao.selectById()                               ← DB查询
  ├── UserDao.updateBalance()                            ← DB更新
  ├── BalanceLogService.recordChange()                   ← 日志
  │     └── BalanceLogDao.insert()                       ← DB插入
  └── NotifyService.sendBalanceNotify()                  ← 通知
        ├── SmsService.send()                            ← 短信
        └── PushService.pushMessage()                    ← 推送
```

## 四、受影响入口点汇总

| # | 入口类型 | 方法 | 路径/触发条件 |
|---|---------|------|-------------|
| 1 | HTTP API | `UserController.updateBalance()` | `POST /api/user/balance/update` |
| 2 | HTTP API | `OrderController.createOrder()` | `POST /api/order/create` |
| 3 | 定时任务 | `SettlementJob.dailySettle()` | `@Scheduled(cron="0 0 2 * * ?")` |

## 五、AI 分析与建议

> （与模式 A 的"AI 风险评估"章节格式相同）

### 5.1 方法职责分析
此方法负责更新用户余额，涉及数据校验、数据库操作、日志记录和通知发送四个职责...

### 5.2 潜在风险
...

### 5.3 建议措施
| 优先级 | 建议 | 说明 |
|-------|------|------|
| ... | ... | ... |

---

## 六、分析元信息

| 项目 | 值 |
|------|---|
| 插件版本 | CodeSense AI v1.0.0 |
| LLM 模型 | GLM-4 |
| 分析耗时 | 6.8 秒 |
| 追溯深度 | 10 层 |
```

---

## 四、报告面板 UI 布局

```
┌─────────────────────────────────────────────────────────────────┐
│ [📊 影响分析]                                          [×]     │
│ ──────────────────────────────────────────────────────────────  │
│                                                                 │
│  工具栏:                                                        │
│  ┌────────────────────────────────────────────────────────┐     │
│  │ [📋 复制MD] [💾 导出.md] [🔄 重新分析] [⚙️ 设置]       │     │
│  └────────────────────────────────────────────────────────┘     │
│                                                                 │
│  ┌────────────────────────────────────────────────────────┐     │
│  │                                                        │     │
│  │   （Markdown 渲染区域，支持滚动）                       │     │
│  │                                                        │     │
│  │   # 📊 影响范围分析报告                                │     │
│  │   | 分析模式 | Git Diff 分支对比 |                      │     │
│  │   ...                                                  │     │
│  │                                                        │     │
│  │   ## 二、调用链分析                                    │     │
│  │   [点击方法名可跳转到源码]                              │     │
│  │                                                        │     │
│  │   UserController.updateBalance()  ← 可点击跳转         │     │
│  │     └── UserService.updateUserBalance()                │     │
│  │                                                        │     │
│  │   ## 四、AI 风险评估                                   │     │
│  │   🐛 1. 并发安全风险 ...                               │     │
│  │                                                        │     │
│  └────────────────────────────────────────────────────────┘     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**报告面板核心交互：**

| 交互 | 说明 |
|------|------|
| 点击方法名 | 跳转到 IDEA 编辑器中的对应源码位置 |
| 📋 复制 MD | 将原始 Markdown 文本复制到剪贴板 |
| 💾 导出 .md | 弹出文件选择器，保存为 `.md` 文件 |
| 🔄 重新分析 | 用最新代码重新执行分析 |
| 代码块 | 语法高亮显示，支持一键复制 |

---

## 五、两种模式的整体工作流对比

```
模式 A: Git Diff 批量分析                    模式 B: 指定方法分析
                                             
  菜单 / 快捷键触发                            编辑器中右键 / 快捷键
       │                                          │
       ▼                                          ▼
  ┌──────────┐                              ┌──────────┐
  │ 配置对话框 │                              │ 确认弹窗  │
  │ 选择分支   │                              │ 选择方向  │
  │ 选择时间   │                              │ 选择深度  │
  └────┬─────┘                              └────┬─────┘
       │                                          │
       ▼                                          ▼
  ┌──────────┐                              ┌──────────┐
  │ Git Diff  │                              │ 获取光标  │
  │ 获取变更   │                              │ 下PsiMethod│
  └────┬─────┘                              └────┬─────┘
       │                                          │
       ▼                                          │
  ┌──────────┐                                    │
  │ PSI 解析  │                                    │
  │ Diff→方法  │                                    │
  └────┬─────┘                                    │
       │                                          │
       ▼                                          ▼
  ┌────────────────────────────────────────────────┐
  │        Call Hierarchy 递归分析（共用）           │
  │        CallerMethodsTreeStructure              │
  │        CalleeMethodsTreeStructure              │
  └────────────────────┬───────────────────────────┘
                       │
                       ▼
                ┌──────────┐
                │ 入口点检测 │
                │ 注解匹配   │
                └────┬─────┘
                     │
                     ▼
                ┌──────────┐
                │ LLM 分析  │
                │ 风险评估   │
                └────┬─────┘
                     │
                     ▼
                ┌──────────┐
                │ 渲染报告  │
                │ ToolWindow│
                └──────────┘
```
