# 📊 影响范围分析报告

| 项目 | 值 |
|------|---|
| **分析模式** | Git Diff 分支对比 |
| **当前分支** | `feature/user-balance-refactor` |
| **目标分支** | `main` |
| **分析时间** | 2026-04-03 14:30:22 |
| **变更文件数** | 6 |
| **变更方法数** | 7 |
| **受影响入口点** | 8 |
| **风险等级** | ⚠️ 中等风险 |

---

## 一、变更概要

| # | 文件 | 方法签名 | 变更类型 | 行号 |
|---|------|---------|---------|------|
| 1 | `UserService.java` | `updateUserBalance(Long, BigDecimal)` | MODIFIED | L45-L78 |
| 2 | `UserService.java` | `getUserInfo(Long)` | MODIFIED | L85-L102 |
| 3 | `UserDao.java` | `updateBalance(UserEntity)` | MODIFIED | L23-L35 |
| 4 | `OrderService.java` | `createOrder(OrderCreateDTO)` | MODIFIED | L110-L148 |
| 5 | `NotifyService.java` | `sendBalanceNotify(Long, BigDecimal, BigDecimal)` | ADDED | L1-L42 |
| 6 | `BalanceLogService.java` | `recordChange(Long, BigDecimal, BigDecimal)` | ADDED | L1-L38 |
| 7 | `UserController.java` | `updateBalance(BalanceUpdateRequest)` | MODIFIED | L55-L72 |

---

## 二、调用链分析

### 2.1 UserService.updateUserBalance(Long, BigDecimal)

**向上调用链（谁调用了我）：**

```
链路 1:
[HTTP] POST /api/user/balance/update
  └── UserController.updateBalance(BalanceUpdateRequest)           ← @PostMapping
        └── ★ UserService.updateUserBalance(Long, BigDecimal)     ← 被修改方法

链路 2:
[HTTP] POST /api/order/create
  └── OrderController.createOrder(OrderCreateDTO)                  ← @PostMapping
        └── OrderService.createOrder(OrderCreateDTO)
              └── PaymentService.processPayment(PaymentInfo)
                    └── ★ UserService.updateUserBalance(Long, BigDecimal)  ← 被修改方法

链路 3:
[定时任务] 每日 02:00 执行
  └── SettlementJob.dailySettle()                                  ← @Scheduled(cron="0 0 2 * * ?")
        └── SettlementService.processSettle(LocalDate)
              └── SettlementService.settleOneUser(Long, BigDecimal)
                    └── ★ UserService.updateUserBalance(Long, BigDecimal)  ← 被修改方法

链路 4:
[MQ] topic: REFUND_ORDER_TOPIC
  └── RefundOrderListener.onMessage(RefundOrderMessage)            ← @RocketMQMessageListener
        └── RefundService.processRefund(Long)
              └── ★ UserService.updateUserBalance(Long, BigDecimal)  ← 被修改方法

链路 5:
[Dubbo RPC] UserAccountFacade.adjustBalance
  └── UserAccountFacadeImpl.adjustBalance(AdjustBalanceRequest)    ← @DubboService
        └── ★ UserService.updateUserBalance(Long, BigDecimal)      ← 被修改方法
```

**向下调用链（我调用了谁）：**

```
★ UserService.updateUserBalance(Long, BigDecimal)
  ├── UserDao.selectById(Long)                                     ← MyBatis 查询
  ├── UserDao.updateBalance(UserEntity)                            ← MyBatis 更新（被修改）
  ├── BalanceLogService.recordChange(Long, BigDecimal, BigDecimal) ← 新增调用
  │     └── BalanceLogDao.insert(BalanceLogEntity)                 ← MyBatis 插入
  └── NotifyService.sendBalanceNotify(Long, BigDecimal, BigDecimal)← 新增调用
        ├── SmsService.sendSms(String, String)                     ← 短信通知
        └── AppPushService.pushMessage(Long, PushMessage)          ← APP 推送
```

**关键代码片段（diff 对比）：**

```diff
// UserService.java (L45-L78)
// 方法: updateUserBalance(Long userId, BigDecimal amount)

  @Transactional(rollbackFor = Exception.class)
  public void updateUserBalance(Long userId, BigDecimal amount) {
      UserEntity user = userDao.selectById(userId);
+     if (user == null) {
+         throw new BusinessException("用户不存在: " + userId);
+     }
-     user.setBalance(user.getBalance().add(amount));
-     userDao.updateBalance(user);
+     BigDecimal oldBalance = user.getBalance();
+     BigDecimal newBalance = oldBalance.add(amount);
+
+     // 新增：余额不足校验
+     if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
+         throw new InsufficientBalanceException(
+             "余额不足, 当前: " + oldBalance + ", 变动: " + amount
+         );
+     }
+
+     user.setBalance(newBalance);
+     userDao.updateBalance(user);
+
+     // 新增：记录余额变动日志
+     balanceLogService.recordChange(userId, oldBalance, newBalance);
+
+     // 新增：发送余额变动通知
+     notifyService.sendBalanceNotify(userId, oldBalance, newBalance);
  }
```

---

### 2.2 UserService.getUserInfo(Long)

**向上调用链（谁调用了我）：**

```
链路 1:
[HTTP] GET /api/user/info/{id}
  └── UserController.getUserInfo(Long)                             ← @GetMapping
        └── ★ UserService.getUserInfo(Long)                       ← 被修改方法

链路 2:
[HTTP] GET /api/order/detail/{id}
  └── OrderController.getOrderDetail(Long)                         ← @GetMapping
        └── OrderService.getOrderDetail(Long)
              └── OrderService.buildOrderVO(OrderEntity)
                    └── ★ UserService.getUserInfo(Long)            ← 被修改方法

链路 3:
[HTTP] GET /api/user/account/summary
  └── AccountController.getAccountSummary()                        ← @GetMapping
        └── AccountService.buildSummary(Long)
              └── ★ UserService.getUserInfo(Long)                  ← 被修改方法
```

**向下调用链（我调用了谁）：**

```
★ UserService.getUserInfo(Long)
  ├── UserCacheService.getFromCache(Long)                          ← 新增：Redis 缓存查询
  ├── UserDao.selectById(Long)                                     ← MyBatis 查询
  ├── UserConverter.toVO(UserEntity)                               ← 实体转换
  └── UserCacheService.putToCache(Long, UserVO)                    ← 新增：写入缓存
```

**关键代码片段（diff 对比）：**

```diff
// UserService.java (L85-L102)
// 方法: getUserInfo(Long userId)

  public UserVO getUserInfo(Long userId) {
+     // 新增：优先从缓存获取
+     UserVO cached = userCacheService.getFromCache(userId);
+     if (cached != null) {
+         return cached;
+     }
+
      UserEntity user = userDao.selectById(userId);
      if (user == null) {
          throw new BusinessException("用户不存在");
      }
      UserVO vo = UserConverter.toVO(user);
+
+     // 新增：写入缓存，过期时间 30 分钟
+     userCacheService.putToCache(userId, vo);
+
      return vo;
  }
```

---

### 2.3 OrderService.createOrder(OrderCreateDTO)

**向上调用链（谁调用了我）：**

```
链路 1:
[HTTP] POST /api/order/create
  └── OrderController.createOrder(OrderCreateDTO)                  ← @PostMapping
        └── ★ OrderService.createOrder(OrderCreateDTO)            ← 被修改方法
```

**向下调用链（我调用了谁）：**

```
★ OrderService.createOrder(OrderCreateDTO)
  ├── OrderValidator.validate(OrderCreateDTO)                      ← 参数校验
  ├── ProductService.getProduct(Long)                              ← 商品查询
  ├── OrderDao.insert(OrderEntity)                                 ← MyBatis 插入
  ├── PaymentService.processPayment(PaymentInfo)                   ← 支付处理
  │     └── UserService.updateUserBalance(Long, BigDecimal)        ← 余额扣减（被修改）
  └── OrderEventPublisher.publishCreated(OrderEvent)               ← 新增：事件发布
```

**关键代码片段（diff 对比）：**

```diff
// OrderService.java (L110-L148)
// 方法: createOrder(OrderCreateDTO dto)

  @Transactional(rollbackFor = Exception.class)
  public OrderVO createOrder(OrderCreateDTO dto) {
      // 参数校验
      orderValidator.validate(dto);

      // 查询商品信息
      ProductEntity product = productService.getProduct(dto.getProductId());

      // 创建订单
      OrderEntity order = OrderEntity.builder()
          .userId(dto.getUserId())
          .productId(dto.getProductId())
          .amount(product.getPrice().multiply(BigDecimal.valueOf(dto.getQuantity())))
          .status(OrderStatus.CREATED)
          .build();
      orderDao.insert(order);

      // 支付处理（内部会调用 updateUserBalance）
      paymentService.processPayment(new PaymentInfo(
          dto.getUserId(), order.getAmount(), order.getId()
      ));

+     // 新增：发布订单创建事件
+     orderEventPublisher.publishCreated(new OrderEvent(
+         order.getId(), dto.getUserId(), OrderEvent.Type.CREATED
+     ));

      return OrderConverter.toVO(order);
  }
```

---

### 2.4 NotifyService.sendBalanceNotify(...) — 新增方法

> 📌 新增方法，不展示代码。文件：`NotifyService.java`（L1-L42），功能：发送余额变动的短信和 APP 推送通知。

**向上调用链（谁调用了我）：**

```
链路 1 (间接):
[HTTP] POST /api/user/balance/update → UserController → UserService.updateUserBalance()
  └── ★ NotifyService.sendBalanceNotify(Long, BigDecimal, BigDecimal)  ← 新增方法

链路 2 (间接):
[HTTP] POST /api/order/create → OrderController → OrderService → PaymentService → UserService.updateUserBalance()
  └── ★ NotifyService.sendBalanceNotify(Long, BigDecimal, BigDecimal)  ← 新增方法
```

**向下调用链（我调用了谁）：**

```
★ NotifyService.sendBalanceNotify(Long, BigDecimal, BigDecimal)
  ├── UserDao.selectById(Long)                                     ← 查询用户手机号
  ├── SmsService.sendSms(String, String)                           ← 短信通知
  └── AppPushService.pushMessage(Long, PushMessage)                ← APP 推送
```

---

### 2.5 BalanceLogService.recordChange(...) — 新增方法

> 📌 新增方法，不展示代码。文件：`BalanceLogService.java`（L1-L38），功能：记录每次余额变动的流水日志。

**向上调用链：**

```
链路 (间接):
所有调用 UserService.updateUserBalance() 的链路
  └── ★ BalanceLogService.recordChange(Long, BigDecimal, BigDecimal)  ← 新增方法
```

**向下调用链：**

```
★ BalanceLogService.recordChange(Long, BigDecimal, BigDecimal)
  └── BalanceLogDao.insert(BalanceLogEntity)                       ← MyBatis 插入
```

---

## 三、受影响入口点汇总

| # | 入口类型 | 类.方法 | 路径 / 触发条件 | 影响的变更方法 | AI 说明 | 人工说明 |
|---|---------|--------|----------------|--------------|--------|--------|
| 1 | 🌐 HTTP API | `UserController.updateBalance()` | `POST /api/user/balance/update` | `updateUserBalance()` | 余额更新逻辑变更，新增了校验和通知，需回归测试扣款、充值场景 | |
| 2 | 🌐 HTTP API | `UserController.getUserInfo()` | `GET /api/user/info/{id}` | `getUserInfo()` | 新增缓存层，首次查询走DB后续走Redis，需验证缓存命中和失效场景 | |
| 3 | 🌐 HTTP API | `OrderController.createOrder()` | `POST /api/order/create` | `updateUserBalance()` via `PaymentService` | 下单链路间接受影响，余额扣减校验变严格，可能触发InsufficientBalanceException | |
| 4 | 🌐 HTTP API | `OrderController.getOrderDetail()` | `GET /api/order/detail/{id}` | `getUserInfo()` | 订单详情中用户信息改为缓存获取，注意缓存不一致可能导致展示旧余额 | |
| 5 | 🌐 HTTP API | `AccountController.getAccountSummary()` | `GET /api/user/account/summary` | `getUserInfo()` | 账户汇总页同样受缓存影响，余额更新后30分钟内可能显示旧数据 | |
| 6 | ⏰ 定时任务 | `SettlementJob.dailySettle()` | `@Scheduled(cron="0 0 2 * * ?")` 每日02:00 | `updateUserBalance()` | 批量结算场景下并发风险最高，每笔结算都会额外触发短信+推送 | |
| 7 | 📨 MQ 消费 | `RefundOrderListener.onMessage()` | `topic: REFUND_ORDER_TOPIC` | `updateUserBalance()` | 退款场景会触发余额增加通知，需确认通知文案是否正确（退款 vs 充值） | |
| 8 | 🔗 Dubbo RPC | `UserAccountFacadeImpl.adjustBalance()` | `@DubboService` 接口: `UserAccountFacade` | `updateUserBalance()` | 外部系统通过 Dubbo 调用余额调整，校验逻辑变更后需通知调用方适配新异常类型 | |

---

## 四、AI 风险评估

### 4.1 风险等级: ⚠️ 中等风险

本次变更对 `UserService` 进行了较大幅度重构，新增了余额校验、变动日志和通知功能。变更直接影响 **8 个入口点**（5 个 HTTP API + 1 个 Dubbo RPC + 1 个定时任务 + 1 个 MQ 消费者），需要重点关注并发安全和缓存一致性问题。

### 4.2 发现的问题

#### 🐛 潜在 Bug

**1. 并发扣款导致余额覆盖 [高风险]**

`updateUserBalance()` 采用"先查后改"模式（`selectById` → 计算 → `updateBalance`），在高并发场景下，两个线程可能同时读到相同的旧余额，导致其中一笔扣款被覆盖。

```java
// 问题代码 (L50-L55)
UserEntity user = userDao.selectById(userId);         // 线程A和线程B同时读到 balance=100
BigDecimal newBalance = oldBalance.add(amount);        // A: 100-50=50, B: 100-30=70
user.setBalance(newBalance);
userDao.updateBalance(user);                           // A写入50, B覆盖为70 → 少扣了50
```

**建议修复**：使用数据库原子操作或乐观锁：
```sql
-- 方案1: 原子 SQL
UPDATE user SET balance = balance + #{amount} WHERE id = #{userId} AND balance + #{amount} >= 0

-- 方案2: 乐观锁
UPDATE user SET balance = #{newBalance}, version = version + 1
WHERE id = #{userId} AND version = #{oldVersion}
```

---

**2. 缓存与数据库不一致 [高风险]**

`getUserInfo()` 新增了 Redis 缓存，但 `updateUserBalance()` 修改用户余额后**没有清除缓存**。这将导致：
- 用户通过 `POST /api/user/balance/update` 充值后
- 立即通过 `GET /api/user/info/{id}` 查看，看到的仍然是旧余额

**建议修复**：在 `updateUserBalance()` 成功后清除缓存：
```java
userDao.updateBalance(user);
userCacheService.evictCache(userId);  // ← 新增这一行
```

---

**3. 通知异常影响主流程 [中风险]**

`sendBalanceNotify()` 在 `updateUserBalance()` 的 `@Transactional` 事务内被调用。如果短信服务或推送服务抛出异常，整个余额更新事务会被回滚。

```java
// 当前代码：通知在事务内
@Transactional(rollbackFor = Exception.class)
public void updateUserBalance(...) {
    ...
    userDao.updateBalance(user);
    balanceLogService.recordChange(...);
    notifyService.sendBalanceNotify(...);  // ← 如果这里抛异常，前面全部回滚
}
```

**建议修复**：
```java
// 方案1: try-catch 降级
try {
    notifyService.sendBalanceNotify(userId, oldBalance, newBalance);
} catch (Exception e) {
    log.error("余额通知发送失败, userId={}", userId, e);
}

// 方案2(更优): 使用 @TransactionalEventListener 异步通知
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void onBalanceChanged(BalanceChangedEvent event) {
    notifyService.sendBalanceNotify(...);
}
```

---

#### ⚡ 性能影响

**4. 接口响应时间增加 [中风险]**

`updateUserBalance()` 新增了 2 个同步调用：
- `balanceLogService.recordChange()` — 一次 DB INSERT
- `notifyService.sendBalanceNotify()` — 一次 DB SELECT + 一次短信 + 一次 APP 推送

这将使以下接口的响应时间增加约 200-500ms：
- `POST /api/user/balance/update`
- `POST /api/order/create`

**建议**：将日志和通知改为异步处理（MQ 或 `@Async`）。

---

#### ✅ 正面评价

**5.** 新增余额不足校验（`InsufficientBalanceException`），防止余额变为负值 ✅

**6.** 新增余额变动日志（`BalanceLogService`），便于对账和审计 ✅

**7.** `getUserInfo()` 增加缓存，减轻数据库压力 ✅（但需修复一致性问题）

### 4.3 建议措施

| 优先级 | 建议 | 涉及方法 | 影响入口 |
|-------|------|---------|---------|
| 🔴 P0 | 使用乐观锁或原子 SQL 解决并发余额覆盖 | `updateUserBalance()` | 全部 7 个 |
| 🔴 P0 | 余额更新后清除用户缓存 | `updateUserBalance()` + `getUserInfo()` | 全部 7 个 |
| 🟡 P1 | 通知逻辑移出事务或加 try-catch | `updateUserBalance()` + `sendBalanceNotify()` | 4 个写入口 |
| 🟡 P1 | 日志和通知改为异步处理 | `updateUserBalance()` | 4 个写入口 |
| 🟢 P2 | 补充 `BalanceLogService` 的单元测试 | `recordChange()` | — |
| 🟢 P2 | 缓存过期时间建议做成可配置项 | `getUserInfo()` | 3 个读入口 |

---

## 五、分析元信息

| 项目 | 值 |
|------|---|
| 插件版本 | CodeSense AI v1.0.0 |
| LLM 模型 | MiniMax M2-her |
| 分析耗时 | 14.7 秒 |
| 追溯深度 | 10 层 |
| 分析范围 | 全项目 |
| 报告生成时间 | 2026-04-03 14:30:36 |
