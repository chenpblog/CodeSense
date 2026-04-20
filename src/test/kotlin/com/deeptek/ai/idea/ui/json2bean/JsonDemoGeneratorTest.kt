package com.deeptek.ai.idea.ui.json2bean

import com.deeptek.ai.idea.ui.json2bean.model.JsonPropertyNode
import com.deeptek.ai.idea.ui.json2bean.util.JsonDemoGenerator
import com.deeptek.ai.idea.ui.json2bean.util.JsonImporter
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JsonDemoGeneratorTest {

    // ─── 辅助：构建节点树 ────────────────────────────────────────────────────────

    private fun buildRoot(type: String = "Object", vararg children: Pair<String, String>): JsonPropertyNode {
        val root = JsonPropertyNode.createRoot("Root")
        root.type = type
        children.forEach { (name, t) -> root.add(JsonPropertyNode(name, t, "")) }
        return root
    }

    // ─── TC-22: 简单对象 → 生成Demo ──────────────────────────────────────────────

    @Test
    fun `TC-22 简单对象_生成Demo_字段值正确`() {
        val root = buildRoot("Object", "name" to "String", "age" to "Decimal")
        val json = JsonDemoGenerator.generateDemoJson(root, isRootList = false)

        val obj = JsonParser.parseString(json).asJsonObject
        assertTrue(obj.isJsonObject)
        assertEquals("demo_string", obj.get("name").asString)
        assertEquals(0.0, obj.get("age").asDouble)
    }

    // ─── TC-23: List根节点 → 生成Demo ────────────────────────────────────────────

    @Test
    fun `TC-23 List根节点_生成Demo_数组含2个元素`() {
        // root.type="Object" + isRootList=true：traverseNode 返回一个 Object，
        // generateDemoJson 包装为包含 2 个相同 Object 的数组
        // （若 root.type="List<Object>"，traverseNode 已返回 array，isRootList=true 会再包一层，变成 [[],[]]）
        val root = buildRoot("Object", "code" to "String", "value" to "Decimal")
        val json = JsonDemoGenerator.generateDemoJson(root, isRootList = true)

        val arr = JsonParser.parseString(json).asJsonArray
        assertTrue(arr.isJsonArray)
        assertEquals(2, arr.size())  // generateDemoJson 固定生成 2 个元素

        val first = arr[0].asJsonObject
        assertEquals("demo_string", first.get("code").asString)
        assertEquals(0.0,           first.get("value").asDouble)
    }

    // ─── TC-24: 含嵌套子对象 → 生成Demo ─────────────────────────────────────────

    @Test
    fun `TC-24 嵌套子对象_递归生成Demo`() {
        val root = JsonPropertyNode.createRoot("Root")
        root.type = "Object"
        root.add(JsonPropertyNode("title", "String", ""))

        val detail = JsonPropertyNode("detail", "Object", "")
        detail.add(JsonPropertyNode("content", "String", ""))
        detail.add(JsonPropertyNode("flag", "Boolean", ""))
        root.add(detail)

        val json = JsonDemoGenerator.generateDemoJson(root, isRootList = false)
        val obj = JsonParser.parseString(json).asJsonObject

        assertEquals("demo_string", obj.get("title").asString)

        val detailObj = obj.getAsJsonObject("detail")
        assertNotNull(detailObj)
        assertEquals("demo_string", detailObj.get("content").asString)
        assertFalse(detailObj.get("flag").asBoolean)
    }

    // ─── TC-25: round-trip（粘贴后再生成，字段名一致）────────────────────────────────

    @Test
    fun `TC-25 RoundTrip_粘贴JSON再生成_字段名一致`() {
        val input = """
            {
              "名称": "基金组合",
              "基金列表": [
                {"代码": "001", "名称": "汇添富"}
              ]
            }
        """.trimIndent()

        val (root, isList) = JsonImporter.parseJsonToTree(input)
        val demoJson = JsonDemoGenerator.generateDemoJson(root, isRootList = isList)

        val obj = JsonParser.parseString(demoJson).asJsonObject
        assertTrue(obj.has("名称"))
        assertTrue(obj.has("基金列表"))

        val arr = obj.getAsJsonArray("基金列表")
        assertEquals(2, arr.size())  // List<Object> 生成 2 个元素
        val first = arr[0].asJsonObject
        assertTrue(first.has("代码"))
        assertTrue(first.has("名称"))
    }
}
