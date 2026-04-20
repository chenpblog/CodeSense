# JSON to Java Bean 单元测试案例文档

> 本文档覆盖 CodeSense 中 **粘贴JSON → 解析节点树 → 生成Bean** 完整链路的测试案例。
> 确认后将编写 Kotlin 单元测试代码。

---

## 被测组件概览

| 组件 | 类路径 | 核心方法 | 职责 |
|------|--------|---------|------|
| **JsonImporter** | `util/JsonImporter.kt` | `parseJsonToTree(json)` | 将 JSON 字符串解析为 `JsonPropertyNode` 树 |
| **JsonDemoGenerator** | `util/JsonDemoGenerator.kt` | `generateDemoJson(rootNode, isRootList)` | 从节点树生成 Demo JSON 字符串 |
| **JsonToBeanService** | `service/JsonToBeanService.kt` | `generateJavaBeanFromMixed(className, englishJson, rootNode)` | 从英文 JSON + 中文描述节点树生成 Java Bean 代码 |

---

## 一、JsonImporter 测试用例（粘贴JSON解析）

### TC-01: 简单扁平对象
**输入JSON:**
```json
{
  "name": "张三",
  "age": 25,
  "active": true
}
```
**预期:**
- `isRootList = false`
- rootNode 有 3 个子节点
- name → type=`String`, age → type=`Decimal`, active → type=`Boolean`

---

### TC-02: 根节点为数组
**输入JSON:**
```json
[
  {"code": "001", "value": 1.5},
  {"code": "002", "value": 2.0}
]
```
**预期:**
- `isRootList = true`
- rootNode.type = `List<Object>`
- rootNode 有 2 个子节点：code(`String`), value(`Decimal`)

---

### TC-03: 嵌套对象
**输入JSON:**
```json
{
  "user": {
    "name": "test",
    "email": "test@example.com"
  },
  "score": 99.5
}
```
**预期:**
- rootNode 有 2 个子节点
- user → type=`Object`, 有 2 个子节点（name, email 均为 `String`）
- score → type=`Decimal`

---

### TC-04: 嵌套数组对象    
**输入JSON:**
```json
{
  "名称": "基金组合",
  "基金列表": [
    {"代码": "001", "名称": "汇添富"},
    {"代码": "002", "名称": "易方达"}
  ]
}
```
**预期:**
- rootNode 有 2 个子节点
- 名称 → `String`
- 基金列表 → type=`List<Object>`, 有 2 个子节点（代码, 名称 均为 `String`）

---

### TC-05: 多层嵌套（3层）
**输入JSON:**
```json
{
  "department": {
    "teams": [
      {
        "teamName": "Alpha",
        "members": [
          {"name": "Alice", "role": "dev"}
        ]
      }
    ]
  }
}
```
**预期:**
- department → `Object` → teams → `List<Object>` → teamName(`String`) + members(`List<Object>`) → name(`String`) + role(`String`)

---

### TC-06: 空JSON对象
**输入JSON:**
```json
{}
```
**预期:**
- `isRootList = false`
- rootNode 无子节点（childCount = 0）

---

### TC-07: 空JSON数组
**输入JSON:**
```json
[]
```
**预期:**
- `isRootList = true`
- rootNode.type = `List<Object>`
- rootNode 无子节点

---

### TC-08: 空字符串 → 异常
**输入:** `""`
**预期:** 抛出 `IllegalArgumentException`，消息包含"不能为空"

---

### TC-09: 非法JSON → 异常
**输入:** `"this is not json"`
**预期:** 抛出 `IllegalArgumentException`，消息包含"格式错误"

---

### TC-10: 含null值字段
**输入JSON:**
```json
{
  "name": "test",
  "address": null,
  "age": 30
}
```
**预期:**
- address → type=`String`（null 值默认推断为 String）

---

### TC-11: 二层嵌套数组 `[[{...}]]`
**输入JSON:**
```json
[
  [
    {"id": 1, "name": "nested"}
  ]
]
```
**预期:**
- `isRootList = true`
- rootNode.type = `List<Object>`
- `findFirstJsonObject` 递归深入（depth 0→1），正确提取到内层对象
- rootNode 有 2 个子节点：id(`Decimal`), name(`String`)

---

### TC-12: 三层嵌套数组 `[[[{...}]]]`
**输入JSON:**
```json
[
  [
    [
      {"code": "deep", "level": 3}
    ]
  ]
]
```
**预期:**
- `isRootList = true`
- `findFirstJsonObject` 递归 3 层（depth 0→1→2）深入，正确提取对象
- rootNode 有 2 个子节点：code(`String`), level(`Decimal`)

---

### TC-13: 四层嵌套数组 `[[[[{...}]]]]`
**输入JSON:**
```json
[
  [
    [
      [
        {"key": "four_deep", "count": 42}
      ]
    ]
  ]
]
```
**预期:**
- `isRootList = true`
- `findFirstJsonObject` 递归 4 层（depth 0→1→2→3），正确提取对象
- rootNode 有 2 个子节点：key(`String`), count(`Decimal`)

---

### TC-14: 五层嵌套数组（极限深度）`[[[[[{...}]]]]]`
**输入JSON:**
```json
[[[[[{"id": "deepest", "ok": true}]]]]]
```
**预期:**
- `isRootList = true`
- `findFirstJsonObject` 递归 5 层（depth 0→1→2→3→4），**恰好在限制内**
- rootNode 有 2 个子节点：id(`String`), ok(`Boolean`)

> [!NOTE]
> 代码中 `depth > 5` 的判断，当 depth=5 时会返回 null。初始调用 depth=0，5层数组意味着递归到 depth=4 时找到对象，在限制内。

---

### TC-15: 六层嵌套数组（超出深度限制）`[[[[[[{...}]]]]]]`
**输入JSON:**
```json
[[[[[[ {"id": "too_deep"} ]]]]]]
```
**预期:**
- `isRootList = true`
- `findFirstJsonObject` 递归到 depth=5 时触发 `depth > 5` 返回 null
- rootNode **无子节点**（childCount = 0），结构无法提取

---

### TC-16: 基本类型数组（无对象）`[1, 2, 3]`
**输入JSON:**
```json
[1, 2, 3]
```
**预期:**
- `isRootList = true`
- 首元素是数字不是 JsonObject，`findFirstJsonObject` 返回 null
- rootNode 无子节点（childCount = 0）

---

### TC-17: 字符串数组 `["a", "b"]`
**输入JSON:**
```json
["hello", "world"]
```
**预期:**
- `isRootList = true`
- 首元素是字符串不是 JsonObject/JsonArray，`findFirstJsonObject` 返回 null
- rootNode 无子节点（childCount = 0）

---

### TC-18: 空嵌套数组 `[[]]`
**输入JSON:**
```json
[[]]
```
**预期:**
- `isRootList = true`
- 首元素是空数组 `[]`，递归深入后 `array.size() == 0` 返回 null
- rootNode 无子节点（childCount = 0）

---

### TC-19: 嵌套基本类型数组 `[["a","b"]]`
**输入JSON:**
```json
[["a", "b", "c"]]
```
**预期:**
- `isRootList = true`
- 首元素是数组，递归深入一层后首元素是字符串不是 JsonObject/JsonArray
- `findFirstJsonObject` 返回 null
- rootNode 无子节点（childCount = 0）

---

### TC-20: 混合嵌套（数组嵌套+对象嵌套+数组字段）
**输入JSON:**
```json
[
  [
    {
      "category": "混合",
      "detail": {
        "score": 95.5,
        "tags": [
          {"label": "A", "weight": 0.8}
        ]
      }
    }
  ]
]
```
**预期:**
- `isRootList = true`
- rootNode 有 2 个子节点：category(`String`), detail(`Object`)
- detail → `Object` → score(`Decimal`) + tags(`List<Object>`)
- tags 子节点：label(`String`), weight(`Decimal`)
- 验证根层二层嵌套数组 + 对象内嵌套 + 对象内数组字段的组合能正确解析

---

### TC-21: 账户诊断出参（实际业务JSON）
**输入JSON:**（来自项目文档 `账户诊断v1.md` 出参）
```json
[
  {
    "名称": "demo_string",
    "持仓比例": "demo_string",
    "持仓资产": "demo_string",
    "基金列表": [
      {"代码": "demo_string", "名称": "demo_string"}
    ],
    "类型": "demo_string"
  }
]
```
**预期:**
- `isRootList = true`
- rootNode 有 5 个子节点：名称, 持仓比例, 持仓资产, 基金列表, 类型
- 基金列表 → `List<Object>`, 子节点: 代码(`String`), 名称(`String`)

---

## 二、JsonDemoGenerator 测试用例（生成Demo JSON）

### TC-22: 简单对象 → 生成Demo
**构建节点树:**
```
Root (Object)
  ├── name (String)
  └── age (Decimal)
```
`isRootList = false`

**预期输出:**
```json
{
  "name": "demo_string",
  "age": 0.0
}
```

---

### TC-23: List根节点 → 生成Demo
**构建节点树:**
```
Root (List<Object>)
  ├── code (String)
  └── value (Decimal)
```
`isRootList = true`

**预期输出:** JSON 数组包含 2 个相同结构的对象

---

### TC-24: 含嵌套子对象 → 生成Demo
**构建节点树:**
```
Root (Object)
  ├── title (String)
  └── detail (Object)
       ├── content (String)
       └── flag (Boolean)
```
**预期输出:**
```json
{
  "title": "demo_string",
  "detail": {
    "content": "demo_string",
    "flag": false
  }
}
```

---

### TC-25: 粘贴后再生成（round-trip）
**流程:** 用 TC-04 的JSON → `JsonImporter.parseJsonToTree()` → 得到节点树 → `JsonDemoGenerator.generateDemoJson()` → 输出JSON

**预期:** 输出JSON结构与输入一致（字段名一致，值为demo值）

---

## 三、JsonToBeanService 测试用例（生成Java Bean）

### TC-26: 简单类生成
**className:** `UserInfo`
**节点树:**
```
Root (Object, desc="用户信息")
  ├── name (String, desc="姓名")
  └── age (Decimal, desc="年龄")
```
**英文JSON:**
```json
{"userName": "test", "age": 25}
```
**预期输出验证:**
- 包含 `import java.math.BigDecimal;`
- 包含 `import lombok.Data;`
- 包含 `public class UserInfo {`
- 包含 `private String userName;`
- 包含 `private BigDecimal age;`
- 包含 `* 姓名`（JavaDoc 来自中文描述）

---

### TC-27: 含List嵌套类生成
**className:** `AccountDiagnosisResponse`
**节点树:**
```
Root (Object, desc="账户诊断出参")
  └── holdingRatios (List<Object>, desc="持仓比例")
       ├── name (String, desc="名称")
       ├── holdingRatio (String, desc="持仓比例")
       └── fundList (List<Object>, desc="基金列表")
            ├── code (String, desc="代码")
            └── name (String, desc="名称")
```
**英文JSON:**
```json
{
  "holdingRatios": [
    {
      "name": "Equity",
      "holdingRatio": "60%",
      "fundList": [
        {"code": "001", "name": "Fund A"}
      ]
    }
  ]
}
```
**预期输出验证:**
- 包含 `import java.util.List;`
- 包含 `private List<HoldingRatios> holdingRatios;`
- 包含 `public static class HoldingRatios {`
- 包含 `public static class FundList {`
- 包含 `private List<FundList> fundList;`

---

### TC-28: 含Boolean字段
**节点树:**
```
Root (Object)
  ├── enabled (Boolean, desc="是否启用")
  └── name (String, desc="名称")
```
**英文JSON:**
```json
{"enabled": true, "name": "test"}
```
**预期:** 包含 `private Boolean enabled;`

---

### TC-29: 账户诊断入参完整测试 
**使用文档中的完整入参结构**
- className: `AccountDiagnosisRequest`
- 8 个字段: fundCode, fundName, primaryType, secondaryType, holdingShares, netAssetValue, netAssetValueDate, marketValue
- 验证 BigDecimal 类型字段（holdingShares, netAssetValue, marketValue）
- 验证 import 列表包含 `java.math.BigDecimal` 和 `lombok.Data`

---

### TC-30: 空子节点 → 生成空类
**节点树:** Root 无子节点
**英文JSON:** `{}`
**预期:** 生成只有类名声明和注解的空类体

---

## 四、端到端链路测试（Paste → Parse → Generate Demo → Generate Bean）

### TC-31: 完整链路 - 账户诊断出参
**步骤:**
1. 粘贴中文JSON（TC-21 的输入）→ `JsonImporter.parseJsonToTree()`
2. 得到节点树 → `JsonDemoGenerator.generateDemoJson()` → 验证Demo JSON结构正确
3. 模拟英文JSON → `JsonToBeanService.generateJavaBeanFromMixed()` → 验证Bean代码

---

### TC-32: 完整链路 - 账户诊断入参
**步骤:**
1. 粘贴中文JSON（入参）→ 解析节点树
2. 验证 8 个字段类型正确（5个String, 3个Decimal）
3. 用英文JSON → 生成Bean → 验证字段名为英文，JavaDoc为中文

---

## 验证清单汇总

### JsonImporter（20 个）

| 测试编号 | 场景 | 类型 | 嵌套深度 |
|---------|------|------|---------|
| TC-01 | 简单扁平对象 | 正常 | 0 |
| TC-02 | 根节点为数组 `[{...}]` | 正常 | 1层数组 |
| TC-03 | 嵌套对象 | 正常 | 对象嵌套 |
| TC-04 | 嵌套数组对象 | 正常 | 字段内数组 |
| TC-05 | 多层嵌套（3层对象+数组） | 正常 | 混合3层 |
| TC-06 | 空JSON对象 `{}` | 边界 | 0 |
| TC-07 | 空JSON数组 `[]` | 边界 | 1层数组 |
| TC-08 | 空字符串 | 异常 | — |
| TC-09 | 非法JSON | 异常 | — |
| TC-10 | 含null值字段 | 边界 | 0 |
| **TC-11** | **二层嵌套数组 `[[{...}]]`** | **边界** | **2层** |
| **TC-12** | **三层嵌套数组 `[[[{...}]]]`** | **边界** | **3层** |
| **TC-13** | **四层嵌套数组 `[[[[{...}]]]]`** | **边界** | **4层** |
| **TC-14** | **五层嵌套数组（极限）`[[[[[{...}]]]]]`** | **边界** | **5层（depth=4 ✓）** |
| **TC-15** | **六层嵌套数组（超限）`[[[[[[{...}]]]]]]`** | **边界** | **6层（depth=5 ✗）** |
| **TC-16** | **基本类型数组 `[1,2,3]`** | **边界** | **首元素非对象** |
| **TC-17** | **字符串数组 `["a","b"]`** | **边界** | **首元素非对象** |
| **TC-18** | **空嵌套数组 `[[]]`** | **边界** | **内层空数组** |
| **TC-19** | **嵌套基本类型数组 `[["a"]]`** | **边界** | **内层非对象** |
| **TC-20** | **混合嵌套（数组+对象+数组字段）** | **正常** | **2层+对象+字段数组** |
| TC-21 | 实际业务JSON（账户诊断出参） | 集成 | 1层+字段数组 |

### JsonDemoGenerator（4 个）

| 测试编号 | 场景 | 类型 |
|---------|------|------|
| TC-22 | 简单对象Demo | 正常 |
| TC-23 | List根节点Demo | 正常 |
| TC-24 | 嵌套子对象Demo | 正常 |
| TC-25 | Round-trip | 集成 |

### JsonToBeanService（5 个）

| 测试编号 | 场景 | 类型 |
|---------|------|------|
| TC-26 | 简单类生成 | 正常 |
| TC-27 | 含List嵌套类 | 正常 |
| TC-28 | Boolean字段 | 正常 |
| TC-29 | 完整入参结构 | 集成 |
| TC-30 | 空子节点 | 边界 |

### E2E（2 个）

| 测试编号 | 场景 | 类型 |
|---------|------|------|
| TC-31 | 完整链路-出参 | 端到端 |
| TC-32 | 完整链路-入参 | 端到端 |

> [!IMPORTANT]
> **嵌套数组完整覆盖：1层(TC-02) → 2层(TC-11) → 3层(TC-12) → 4层(TC-13) → 5层极限(TC-14) → 6层超限(TC-15)，加上空嵌套(TC-18)、基本类型(TC-16/17)、嵌套基本类型(TC-19)。**
>
> **请确认以上 32 个测试案例是否覆盖足够。确认后我将编写 Kotlin 单元测试代码并运行验证。**

**输入JSON:**
```json
[1, 2, 3]
```
**预期:**
- `isRootList = true`
- `findFirstJsonObject` 递归后找不到 JsonObject，返回 null
- rootNode 无子节点（childCount = 0）

---

### TC-14: 五层嵌套数组（极限深度）
**输入JSON:**
```json
[[[[[{"id": "deepest", "ok": true}]]]]]
```
**预期:**
- `isRootList = true`
- `findFirstJsonObject` 递归 5 层（depth 0→1→2→3→4），**恰好在限制内**
- rootNode 有 2 个子节点：id(`String`), ok(`Boolean`)

> [!NOTE]
> 代码中 `depth > 5` 的判断，当 depth=5 时会返回 null。初始调用 depth=0，5层数组意味着递归到 depth=4 时找到对象，在限制内。

---

### TC-15: 六层嵌套数组（超出深度限制）
**输入JSON:**
```json
[[[[[[{"id": "too_deep"}]]]]]]
```
**预期:**
- `isRootList = true`
- `findFirstJsonObject` 递归超过 5 层深度限制，返回 null
- rootNode **无子节点**（childCount = 0），结构无法提取

---

### TC-16: 混合嵌套（数组嵌套+对象嵌套+数组字段）
**输入JSON:**
```json
[
  [
    {
      "category": "混合",
      "detail": {
        "score": 95.5,
        "tags": [
          {"label": "A", "weight": 0.8}
        ]
      }
    }
  ]
]
```
**预期:**
- `isRootList = true`
- rootNode 有 2 个子节点：category(`String`), detail(`Object`)
- detail → `Object` → score(`Decimal`) + tags(`List<Object>`)
- tags 子节点：label(`String`), weight(`Decimal`)
- 验证根层二层嵌套数组 + 对象内嵌套 + 对象内数组字段的组合能正确解析

---

### TC-17: 账户诊断出参（实际业务JSON）
**输入JSON:**（来自项目文档 `账户诊断v1.md` 出参）
```json
[
  {
    "名称": "demo_string",
    "持仓比例": "demo_string",
    "持仓资产": "demo_string",
    "基金列表": [
      {"代码": "demo_string", "名称": "demo_string"}
    ],
    "类型": "demo_string"
  }
]
```
**预期:**
- `isRootList = true`
- rootNode 有 5 个子节点：名称, 持仓比例, 持仓资产, 基金列表, 类型
- 基金列表 → `List<Object>`, 子节点: 代码(`String`), 名称(`String`)

---

## 二、JsonDemoGenerator 测试用例（生成Demo JSON）

### TC-18: 简单对象 → 生成Demo
**构建节点树:**
```
Root (Object)
  ├── name (String)
  └── age (Decimal)
```
`isRootList = false`

**预期输出:**
```json
{
  "name": "demo_string",
  "age": 0.0
}
```

---

### TC-19: List根节点 → 生成Demo
**构建节点树:**
```
Root (List<Object>)
  ├── code (String)
  └── value (Decimal)
```
`isRootList = true`

**预期输出:** JSON 数组包含 2 个相同结构的对象

---

### TC-20: 含嵌套子对象 → 生成Demo
**构建节点树:**
```
Root (Object)
  ├── title (String)
  └── detail (Object)
       ├── content (String)
       └── flag (Boolean)
```
**预期输出:**
```json
{
  "title": "demo_string",
  "detail": {
    "content": "demo_string",
    "flag": false
  }
}
```

---

### TC-21: 粘贴后再生成（round-trip）
**流程:** 用 TC-04 的JSON → `JsonImporter.parseJsonToTree()` → 得到节点树 → `JsonDemoGenerator.generateDemoJson()` → 输出JSON

**预期:** 输出JSON结构与输入一致（字段名一致，值为demo值）

---

## 三、JsonToBeanService 测试用例（生成Java Bean）

### TC-22: 简单类生成
**className:** `UserInfo`
**节点树:**
```
Root (Object, desc="用户信息")
  ├── name (String, desc="姓名")
  └── age (Decimal, desc="年龄")
```
**英文JSON:**
```json
{"userName": "test", "age": 25}
```
**预期输出验证:**
- 包含 `import java.math.BigDecimal;`
- 包含 `import lombok.Data;`
- 包含 `public class UserInfo {`
- 包含 `private String userName;`
- 包含 `private BigDecimal age;`
- 包含 `* 姓名`（JavaDoc 来自中文描述）

---

### TC-23: 含List嵌套类生成
**className:** `AccountDiagnosisResponse`
**节点树:**
```
Root (Object, desc="账户诊断出参")
  └── holdingRatios (List<Object>, desc="持仓比例")
       ├── name (String, desc="名称")
       ├── holdingRatio (String, desc="持仓比例")
       └── fundList (List<Object>, desc="基金列表")
            ├── code (String, desc="代码")
            └── name (String, desc="名称")
```
**英文JSON:**
```json
{
  "holdingRatios": [
    {
      "name": "Equity",
      "holdingRatio": "60%",
      "fundList": [
        {"code": "001", "name": "Fund A"}
      ]
    }
  ]
}
```
**预期输出验证:**
- 包含 `import java.util.List;`
- 包含 `private List<HoldingRatios> holdingRatios;`
- 包含 `public static class HoldingRatios {`
- 包含 `public static class FundList {`
- 包含 `private List<FundList> fundList;`

---

### TC-24: 含Boolean字段
**节点树:**
```
Root (Object)
  ├── enabled (Boolean, desc="是否启用")
  └── name (String, desc="名称")
```
**英文JSON:**
```json
{"enabled": true, "name": "test"}
```
**预期:** 包含 `private Boolean enabled;`

---

### TC-25: 账户诊断入参完整测试 
**使用文档中的完整入参结构**
- className: `AccountDiagnosisRequest`
- 8 个字段: fundCode, fundName, primaryType, secondaryType, holdingShares, netAssetValue, netAssetValueDate, marketValue
- 验证 BigDecimal 类型字段（holdingShares, netAssetValue, marketValue）
- 验证 import 列表包含 `java.math.BigDecimal` 和 `lombok.Data`

---

### TC-26: 空子节点 → 生成空类
**节点树:** Root 无子节点
**英文JSON:** `{}`
**预期:** 生成只有类名声明和注解的空类体

---

## 四、端到端链路测试（Paste → Parse → Generate Demo → Generate Bean）

### TC-27: 完整链路 - 账户诊断出参
**步骤:**
1. 粘贴中文JSON（TC-17 的输入）→ `JsonImporter.parseJsonToTree()`
2. 得到节点树 → `JsonDemoGenerator.generateDemoJson()` → 验证Demo JSON结构正确
3. 模拟英文JSON → `JsonToBeanService.generateJavaBeanFromMixed()` → 验证Bean代码

---

### TC-28: 完整链路 - 账户诊断入参
**步骤:**
1. 粘贴中文JSON（入参）→ 解析节点树
2. 验证 8 个字段类型正确（5个String, 3个Decimal）
3. 用英文JSON → 生成Bean → 验证字段名为英文，JavaDoc为中文

---

## 验证清单汇总

| 测试编号 | 组件 | 场景 | 类型 |
|---------|------|------|------|
| TC-01 | JsonImporter | 简单扁平对象 | 正常 |
| TC-02 | JsonImporter | 根节点为数组 | 正常 |
| TC-03 | JsonImporter | 嵌套对象 | 正常 |
| TC-04 | JsonImporter | 嵌套数组对象 | 正常 |
| TC-05 | JsonImporter | 多层嵌套（3层） | 正常 |
| TC-06 | JsonImporter | 空JSON对象 | 边界 |
| TC-07 | JsonImporter | 空JSON数组 | 边界 |
| TC-08 | JsonImporter | 空字符串 | 异常 |
| TC-09 | JsonImporter | 非法JSON | 异常 |
| TC-10 | JsonImporter | 含null值字段 | 边界 |
| TC-11 | JsonImporter | 二层嵌套数组 `[[{...}]]` | 边界 |
| TC-12 | JsonImporter | 三层嵌套数组 `[[[{...}]]]` | 边界 |
| TC-13 | JsonImporter | 基本类型数组 `[1,2,3]` | 边界 |
| **TC-14** | **JsonImporter** | **五层嵌套数组（极限深度）** | **边界** |
| **TC-15** | **JsonImporter** | **六层嵌套数组（超限 → 无子节点）** | **边界** |
| **TC-16** | **JsonImporter** | **混合嵌套（数组+对象+数组字段）** | **正常** |
| TC-17 | JsonImporter | 实际业务JSON | 集成 |
| TC-18 | JsonDemoGenerator | 简单对象Demo | 正常 |
| TC-19 | JsonDemoGenerator | List根节点Demo | 正常 |
| TC-20 | JsonDemoGenerator | 嵌套子对象Demo | 正常 |
| TC-21 | JsonDemoGenerator | Round-trip | 集成 |
| TC-22 | JsonToBeanService | 简单类生成 | 正常 |
| TC-23 | JsonToBeanService | 含List嵌套类 | 正常 |
| TC-24 | JsonToBeanService | Boolean字段 | 正常 |
| TC-25 | JsonToBeanService | 完整入参结构 | 集成 |
| TC-26 | JsonToBeanService | 空子节点 | 边界 |
| TC-27 | E2E | 完整链路-出参 | 端到端 |
| TC-28 | E2E | 完整链路-入参 | 端到端 |

> [!IMPORTANT]
> **请确认以上 28 个测试案例是否覆盖足够。确认后我将编写 Kotlin 单元测试代码并运行验证。**
