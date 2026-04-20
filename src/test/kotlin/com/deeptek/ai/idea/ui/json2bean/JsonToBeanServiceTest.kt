package com.deeptek.ai.idea.ui.json2bean

import com.deeptek.ai.idea.ui.json2bean.model.JsonPropertyNode
import com.deeptek.ai.idea.ui.json2bean.service.JsonToBeanService
import com.deeptek.ai.idea.ui.json2bean.util.JsonImporter
import com.deeptek.ai.idea.ui.json2bean.util.JsonDemoGenerator
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JsonToBeanServiceTest {

    // ─── TC-26: 简单类生成 ────────────────────────────────────────────────────────

    @Test
    fun `TC-26 简单类生成_import和字段正确`() {
        val root = JsonPropertyNode.createRoot("Root")
        root.description = "用户信息"
        root.add(JsonPropertyNode("name", "String",  "姓名"))
        root.add(JsonPropertyNode("age",  "Decimal", "年龄"))

        val englishJson = """{"userName": "test", "age": 25}"""
        val code = JsonToBeanService.generateJavaBeanFromMixed("UserInfo", englishJson, root)

        assertTrue(code.contains("import java.math.BigDecimal;"), "应含BigDecimal import")
        assertTrue(code.contains("import lombok.Data;"),          "应含lombok.Data import")
        assertTrue(code.contains("public class UserInfo {"),      "应含类声明")
        assertTrue(code.contains("private String userName;"),     "应含String字段")
        assertTrue(code.contains("private BigDecimal age;"),      "应含BigDecimal字段")
        // cloneAndMergeNode 生成的描述格式是 "fieldName - description"，即 "name - 姓名"
        // JavaDoc 为 "* name - 姓名"，所以展宽断言为包含中文即可
        assertTrue(code.contains("姓名"),                       "JavaDoc应含中文注释")
        assertTrue(code.contains("年龄"),                       "JavaDoc应含中文注释")
    }

    // ─── TC-27: 含List嵌套类生成 ──────────────────────────────────────────────────

    @Test
    fun `TC-27 含List嵌套类生成_内部类和import正确`() {
        val root = JsonPropertyNode.createRoot("Root")
        root.description = "账户诊断出参"

        val holdingRatios = JsonPropertyNode("holdingRatios", "List<Object>", "持仓比例")
        holdingRatios.add(JsonPropertyNode("name",         "String", "名称"))
        holdingRatios.add(JsonPropertyNode("holdingRatio", "String", "持仓比例"))

        val fundList = JsonPropertyNode("fundList", "List<Object>", "基金列表")
        fundList.add(JsonPropertyNode("code", "String", "代码"))
        fundList.add(JsonPropertyNode("name", "String", "名称"))
        holdingRatios.add(fundList)
        root.add(holdingRatios)

        val englishJson = """
            {
              "holdingRatios": [
                {
                  "name": "Equity",
                  "holdingRatio": "60%",
                  "fundList": [{"code": "001", "name": "Fund A"}]
                }
              ]
            }
        """.trimIndent()

        val code = JsonToBeanService.generateJavaBeanFromMixed("AccountDiagnosisResponse", englishJson, root)

        assertTrue(code.contains("import java.util.List;"),                    "应含List import")
        assertTrue(code.contains("private List<HoldingRatios> holdingRatios;"),"应含List<HoldingRatios>字段")
        assertTrue(code.contains("public static class HoldingRatios {"),       "应含HoldingRatios内部类")
        assertTrue(code.contains("public static class FundList {"),            "应含FundList内部类")
        assertTrue(code.contains("private List<FundList> fundList;"),          "应含List<FundList>字段")
    }

    // ─── TC-28: 含Boolean字段 ────────────────────────────────────────────────────

    @Test
    fun `TC-28 含Boolean字段_生成Boolean类型`() {
        val root = JsonPropertyNode.createRoot("Root")
        root.add(JsonPropertyNode("enabled", "Boolean", "是否启用"))
        root.add(JsonPropertyNode("name",    "String",  "名称"))

        val englishJson = """{"enabled": true, "name": "test"}"""
        val code = JsonToBeanService.generateJavaBeanFromMixed("FlagConfig", englishJson, root)

        assertTrue(code.contains("private Boolean enabled;"), "应含Boolean字段")
        assertTrue(code.contains("private String name;"),     "应含String字段")
        // Boolean 不需要额外 import
        assertFalse(code.contains("import java.lang.Boolean"), "Boolean无需显式import")
    }

    // ─── TC-29: 账户诊断入参完整结构 ─────────────────────────────────────────────

    @Test
    fun `TC-29 账户诊断入参_8个字段_类型和import正确`() {
        val root = JsonPropertyNode.createRoot("Root")
        root.description = "账户诊断入参"
        root.add(JsonPropertyNode("基金代码", "String",  "基金代码"))
        root.add(JsonPropertyNode("基金名称", "String",  "基金名称"))
        root.add(JsonPropertyNode("一级类型", "String",  "一级类型"))
        root.add(JsonPropertyNode("二级类型", "String",  "二级类型"))
        root.add(JsonPropertyNode("持仓份额", "Decimal", "持仓份额"))
        root.add(JsonPropertyNode("净值",    "Decimal", "净值"))
        root.add(JsonPropertyNode("净值日期", "String",  "净值日期"))
        root.add(JsonPropertyNode("市值",    "Decimal", "市值"))

        val englishJson = """
            {
              "fundCode": "FU000001",
              "fundName": "E Fund Mixed Fund",
              "primaryType": "Equity",
              "secondaryType": "Growth",
              "holdingShares": 1500.50,
              "netAssetValue": 1.2500,
              "netAssetValueDate": "2023-10-26",
              "marketValue": 1875.63
            }
        """.trimIndent()

        val code = JsonToBeanService.generateJavaBeanFromMixed("AccountDiagnosisRequest", englishJson, root)

        // import 验证
        assertTrue(code.contains("import java.math.BigDecimal;"), "应含BigDecimal import")
        assertTrue(code.contains("import lombok.Data;"),          "应含lombok.Data import")

        // 字段验证
        assertTrue(code.contains("private String fundCode;"),          "应含fundCode")
        assertTrue(code.contains("private String fundName;"),          "应含fundName")
        assertTrue(code.contains("private String primaryType;"),       "应含primaryType")
        assertTrue(code.contains("private String secondaryType;"),     "应含secondaryType")
        assertTrue(code.contains("private BigDecimal holdingShares;"), "应含holdingShares")
        assertTrue(code.contains("private BigDecimal netAssetValue;"), "应含netAssetValue")
        assertTrue(code.contains("private String netAssetValueDate;"), "应含netAssetValueDate")
        assertTrue(code.contains("private BigDecimal marketValue;"),   "应含marketValue")

        // JavaDoc 中文注释验证
        assertTrue(code.contains("* 基金代码"), "JavaDoc应包含基金代码")
        assertTrue(code.contains("* 持仓份额"), "JavaDoc应包含持仓份额")
    }

    // ─── TC-30: 空子节点 → 生成空类体 ────────────────────────────────────────────

    @Test
    fun `TC-30 空子节点_生成空类体`() {
        val root = JsonPropertyNode.createRoot("Root")
        // 不添加任何子节点

        val code = JsonToBeanService.generateJavaBeanFromMixed("EmptyClass", "{}", root)

        assertTrue(code.contains("public class EmptyClass {"), "应含类声明")
        assertTrue(code.contains("import lombok.Data;"),        "应含Data注解import")
        assertTrue(code.contains("@Data"),                      "应含@Data注解")
        // 不应有任何 private 字段
        assertFalse(code.contains("private "),                  "空类不应含字段")
    }

    // ─── TC-31: E2E - 账户诊断出参完整链路 ────────────────────────────────────────

    @Test
    fun `TC-31 E2E_账户诊断出参_粘贴到生成Bean`() {
        // Step 1: 粘贴JSON → 解析节点树
        val chineseJson = """
            [
              {
                "名称": "权益资产",
                "持仓比例": "60.00%",
                "持仓资产": "1500000.00",
                "基金列表": [
                  {"代码": "FU001", "名称": "Vanguard Total Stock Market ETF"}
                ],
                "类型": "Equity"
              }
            ]
        """.trimIndent()

        val (root, isList) = JsonImporter.parseJsonToTree(chineseJson)

        // 验证解析结果
        assertTrue(isList)
        assertEquals(5, root.childCount)

        // Step 2: 生成Demo JSON（验证结构正确）
        val demoJson = JsonDemoGenerator.generateDemoJson(root, isRootList = isList)
        assertTrue(demoJson.startsWith("["), "Demo JSON应为数组")

        // Step 3: 用英文JSON生成Bean
        val englishJson = """
            {
              "holdingRatios": [
                {
                  "name": "Equity Assets",
                  "holdingRatio": "60.00%",
                  "holdingAssets": "1500000.00",
                  "fundList": [{"code": "FU001", "name": "Vanguard Total Stock Market ETF"}],
                  "type": "Equity"
                }
              ]
            }
        """.trimIndent()

        // 构建匹配英文JSON结构的root（模拟AI翻译后的结构）
        val translatedRoot = JsonPropertyNode.createRoot("Root")
        translatedRoot.description = "账户诊断出参"
        val ratios = JsonPropertyNode("holdingRatios", "List<Object>", "持仓比例列表")
        ratios.add(JsonPropertyNode("name",         "String", "名称"))
        ratios.add(JsonPropertyNode("holdingRatio",  "String", "持仓比例"))
        ratios.add(JsonPropertyNode("holdingAssets", "String", "持仓资产"))
        val fl = JsonPropertyNode("fundList", "List<Object>", "基金列表")
        fl.add(JsonPropertyNode("code", "String", "代码"))
        fl.add(JsonPropertyNode("name", "String", "名称"))
        ratios.add(fl)
        ratios.add(JsonPropertyNode("type", "String", "类型"))
        translatedRoot.add(ratios)

        val code = JsonToBeanService.generateJavaBeanFromMixed("AccountDiagnosisResponse", englishJson, translatedRoot)

        assertTrue(code.contains("import java.util.List;"),                     "应含List import")
        assertTrue(code.contains("private List<HoldingRatios> holdingRatios;"), "应含holdingRatios字段")
        assertTrue(code.contains("public static class HoldingRatios {"),        "应含HoldingRatios类")
        assertTrue(code.contains("public static class FundList {"),             "应含FundList类")
    }

    // ─── TC-32: E2E - 账户诊断入参完整链路 ────────────────────────────────────────

    @Test
    fun `TC-32 E2E_账户诊断入参_粘贴到生成Bean`() {
        // Step 1: 粘贴中文JSON → 解析节点树
        val chineseJson = """
            [
              {
                "基金代码": "demo_string",
                "基金名称": "demo_string",
                "一级类型": "demo_string",
                "二级类型": "demo_string",
                "持仓份额": 0.0,
                "净值": 0.0,
                "净值日期": "demo_string",
                "市值": 0.0
              }
            ]
        """.trimIndent()

        val (root, isList) = JsonImporter.parseJsonToTree(chineseJson)

        // Step 2: 验证8个字段类型
        assertTrue(isList)
        assertEquals(8, root.childCount)
        assertEquals("String",  (root.getChildAt(0) as JsonPropertyNode).type) // 基金代码
        assertEquals("String",  (root.getChildAt(1) as JsonPropertyNode).type) // 基金名称
        assertEquals("String",  (root.getChildAt(2) as JsonPropertyNode).type) // 一级类型
        assertEquals("String",  (root.getChildAt(3) as JsonPropertyNode).type) // 二级类型
        assertEquals("Decimal", (root.getChildAt(4) as JsonPropertyNode).type) // 持仓份额
        assertEquals("Decimal", (root.getChildAt(5) as JsonPropertyNode).type) // 净值
        assertEquals("String",  (root.getChildAt(6) as JsonPropertyNode).type) // 净值日期
        assertEquals("Decimal", (root.getChildAt(7) as JsonPropertyNode).type) // 市值

        // Step 3: 用英文JSON生成Bean → 字段名为英文，JavaDoc为中文
        val englishJson = """
            {
              "fundCode": "FU000001",
              "fundName": "E Fund Mixed Fund",
              "primaryType": "Equity",
              "secondaryType": "Growth",
              "holdingShares": 1500.50,
              "netAssetValue": 1.2500,
              "netAssetValueDate": "2023-10-26",
              "marketValue": 1875.63
            }
        """.trimIndent()

        val code = JsonToBeanService.generateJavaBeanFromMixed("AccountDiagnosisRequest", englishJson, root)

        // 字段名为英文
        assertTrue(code.contains("private String fundCode;"),          "字段名应为英文")
        assertTrue(code.contains("private BigDecimal holdingShares;"), "字段名应为英文")
        assertTrue(code.contains("private BigDecimal marketValue;"),   "字段名应为英文")

        // JavaDoc 中文注释（来自原始中文fieldName）
        assertTrue(code.contains("* 基金代码"), "JavaDoc应含中文")
        assertTrue(code.contains("* 持仓份额"), "JavaDoc应含中文")
        assertTrue(code.contains("* 市值"),    "JavaDoc应含中文")
    }
}
