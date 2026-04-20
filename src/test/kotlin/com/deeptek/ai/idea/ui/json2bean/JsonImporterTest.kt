package com.deeptek.ai.idea.ui.json2bean

import com.deeptek.ai.idea.ui.json2bean.model.JsonPropertyNode
import com.deeptek.ai.idea.ui.json2bean.util.JsonImporter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JsonImporterTest {

    // ─── 辅助方法 ───────────────────────────────────────────────────────────────

    /** 获取 rootNode 的第 index 个子节点 */
    private fun child(root: JsonPropertyNode, index: Int): JsonPropertyNode =
        root.getChildAt(index) as JsonPropertyNode

    // ─── TC-01: 简单扁平对象 ─────────────────────────────────────────────────────

    @Test
    fun `TC-01 简单扁平对象_三种基本类型`() {
        val json = """{"name": "张三", "age": 25, "active": true}"""
        val (root, isList) = JsonImporter.parseJsonToTree(json)

        assertFalse(isList)
        assertEquals(3, root.childCount)

        val name   = child(root, 0)
        val age    = child(root, 1)
        val active = child(root, 2)

        assertEquals("name",   name.fieldName);   assertEquals("String",  name.type)
        assertEquals("age",    age.fieldName);    assertEquals("Decimal", age.type)
        assertEquals("active", active.fieldName); assertEquals("Boolean", active.type)
    }

    // ─── TC-02: 根节点为数组 ──────────────────────────────────────────────────────

    @Test
    fun `TC-02 根节点为数组_isList=true`() {
        val json = """[{"code": "001", "value": 1.5}, {"code": "002", "value": 2.0}]"""
        val (root, isList) = JsonImporter.parseJsonToTree(json)

        assertTrue(isList)
        assertEquals("List<Object>", root.type)
        assertEquals(2, root.childCount)

        assertEquals("code",  child(root, 0).fieldName); assertEquals("String",  child(root, 0).type)
        assertEquals("value", child(root, 1).fieldName); assertEquals("Decimal", child(root, 1).type)
    }

    // ─── TC-03: 嵌套对象 ─────────────────────────────────────────────────────────

    @Test
    fun `TC-03 嵌套对象_Object类型`() {
        val json = """{"user": {"name": "test", "email": "test@example.com"}, "score": 99.5}"""
        val (root, isList) = JsonImporter.parseJsonToTree(json)

        assertFalse(isList)
        assertEquals(2, root.childCount)

        val user  = child(root, 0)
        val score = child(root, 1)

        assertEquals("user",  user.fieldName);  assertEquals("Object",  user.type)
        assertEquals("score", score.fieldName); assertEquals("Decimal", score.type)

        assertEquals(2, user.childCount)
        assertEquals("name",  child(user, 0).fieldName); assertEquals("String", child(user, 0).type)
        assertEquals("email", child(user, 1).fieldName); assertEquals("String", child(user, 1).type)
    }

    // ─── TC-04: 嵌套数组对象 ─────────────────────────────────────────────────────

    @Test
    fun `TC-04 嵌套数组对象_ListObject类型`() {
        val json = """
            {
              "名称": "基金组合",
              "基金列表": [
                {"代码": "001", "名称": "汇添富"},
                {"代码": "002", "名称": "易方达"}
              ]
            }
        """.trimIndent()
        val (root, isList) = JsonImporter.parseJsonToTree(json)

        assertFalse(isList)
        assertEquals(2, root.childCount)

        val name     = child(root, 0)
        val fundList = child(root, 1)

        assertEquals("名称",   name.fieldName);     assertEquals("String", name.type)
        assertEquals("基金列表", fundList.fieldName); assertEquals("List<Object>", fundList.type)

        assertEquals(2, fundList.childCount)
        assertEquals("代码", child(fundList, 0).fieldName); assertEquals("String", child(fundList, 0).type)
        assertEquals("名称", child(fundList, 1).fieldName); assertEquals("String", child(fundList, 1).type)
    }

    // ─── TC-05: 多层嵌套（3层 对象+数组） ───────────────────────────────────────────

    @Test
    fun `TC-05 多层嵌套_对象内数组内对象内数组`() {
        val json = """
            {
              "department": {
                "teams": [
                  {
                    "teamName": "Alpha",
                    "members": [{"name": "Alice", "role": "dev"}]
                  }
                ]
              }
            }
        """.trimIndent()
        val (root, _) = JsonImporter.parseJsonToTree(json)

        val department = child(root, 0)
        assertEquals("Object", department.type)

        val teams = child(department, 0)
        assertEquals("List<Object>", teams.type)

        val teamName = child(teams, 0)
        val members  = child(teams, 1)
        assertEquals("String",      teamName.type)
        assertEquals("List<Object>", members.type)

        val memberName = child(members, 0)
        val memberRole = child(members, 1)
        assertEquals("String", memberName.type)
        assertEquals("String", memberRole.type)
    }

    // ─── TC-06: 空JSON对象 ────────────────────────────────────────────────────────

    @Test
    fun `TC-06 空JSON对象_无子节点`() {
        val (root, isList) = JsonImporter.parseJsonToTree("{}")
        assertFalse(isList)
        assertEquals(0, root.childCount)
    }

    // ─── TC-07: 空JSON数组 ────────────────────────────────────────────────────────

    @Test
    fun `TC-07 空JSON数组_isList且无子节点`() {
        val (root, isList) = JsonImporter.parseJsonToTree("[]")
        assertTrue(isList)
        assertEquals("List<Object>", root.type)
        assertEquals(0, root.childCount)
    }

    // ─── TC-08: 空字符串 → 异常 ───────────────────────────────────────────────────

    @Test
    fun `TC-08 空字符串_抛出IllegalArgumentException`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            JsonImporter.parseJsonToTree("")
        }
        assertTrue(ex.message?.contains("不能为空") == true, "消息应包含\"不能为空\"，实际: ${ex.message}")
    }

    // ─── TC-09: 非法JSON → 异常 ───────────────────────────────────────────────────

    @Test
    fun `TC-09 非法JSON_抛出IllegalArgumentException`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            JsonImporter.parseJsonToTree("this is not json")
        }
        assertTrue(ex.message?.contains("格式错误") == true, "消息应包含\"格式错误\"，实际: ${ex.message}")
    }

    // ─── TC-10: 含null值字段 ─────────────────────────────────────────────────────

    @Test
    fun `TC-10 null值字段_默认推断为String`() {
        val json = """{"name": "test", "address": null, "age": 30}"""
        val (root, _) = JsonImporter.parseJsonToTree(json)

        assertEquals(3, root.childCount)
        val address = child(root, 1)
        assertEquals("address", address.fieldName)
        assertEquals("String", address.type)  // null → String
    }

    // ─── TC-11: 二层嵌套数组 [[{...}]] ────────────────────────────────────────────

    @Test
    fun `TC-11 二层嵌套数组_递归深入正确提取`() {
        val json = """[[{"id": 1, "name": "nested"}]]"""
        val (root, isList) = JsonImporter.parseJsonToTree(json)

        assertTrue(isList)
        assertEquals("List<Object>", root.type)
        assertEquals(2, root.childCount)
        assertEquals("id",   child(root, 0).fieldName); assertEquals("Decimal", child(root, 0).type)
        assertEquals("name", child(root, 1).fieldName); assertEquals("String",  child(root, 1).type)
    }

    // ─── TC-12: 三层嵌套数组 [[[{...}]]] ──────────────────────────────────────────

    @Test
    fun `TC-12 三层嵌套数组_depth0到2递归`() {
        val json = """[[[ {"code": "deep", "level": 3} ]]]"""
        val (root, isList) = JsonImporter.parseJsonToTree(json)

        assertTrue(isList)
        assertEquals(2, root.childCount)
        assertEquals("code",  child(root, 0).fieldName); assertEquals("String",  child(root, 0).type)
        assertEquals("level", child(root, 1).fieldName); assertEquals("Decimal", child(root, 1).type)
    }

    // ─── TC-13: 四层嵌套数组 [[[[{...}]]]] ────────────────────────────────────────

    @Test
    fun `TC-13 四层嵌套数组_depth0到3递归`() {
        val json = """[[[[ {"key": "four_deep", "count": 42} ]]]]"""
        val (root, isList) = JsonImporter.parseJsonToTree(json)

        assertTrue(isList)
        assertEquals(2, root.childCount)
        assertEquals("key",   child(root, 0).fieldName); assertEquals("String",  child(root, 0).type)
        assertEquals("count", child(root, 1).fieldName); assertEquals("Decimal", child(root, 1).type)
    }

    // ─── TC-14: 五层嵌套数组（极限深度 depth=4）─────────────────────────────────────

    @Test
    fun `TC-14 五层嵌套数组_depth4在限制内`() {
        val json = """[[[[[ {"id": "deepest", "ok": true} ]]]]]"""
        val (root, isList) = JsonImporter.parseJsonToTree(json)

        assertTrue(isList)
        // depth > 5 才返回 null，depth=4 恰好在限制内
        assertEquals(2, root.childCount)
        assertEquals("id", child(root, 0).fieldName); assertEquals("String",  child(root, 0).type)
        assertEquals("ok", child(root, 1).fieldName); assertEquals("Boolean", child(root, 1).type)
    }

    // ─── TC-15: 七层嵌套数组（超出深度限制 depth=6 > 5）───────────────────────────

    @Test
    fun `TC-15 七层嵌套数组_超出深度限制_无子节点`() {
        // 7个括号： findFirstJsonObject 在 depth=6 时 6>5=true 返回 null
        // 6个括号时 depth=5，5>5=false，对象仍能被提取
        val json = """[[[[[[[{"id": "too_deep"}]]]]]]]"""
        val (root, isList) = JsonImporter.parseJsonToTree(json)

        assertTrue(isList)
        // depth=6 触发 depth>5 限制，返回 null，无子节点
        assertEquals(0, root.childCount)
    }

    // ─── TC-16: 基本类型数组 [1,2,3] ──────────────────────────────────────────────

    @Test
    fun `TC-16 基本类型数组_无子节点`() {
        val json = """[1, 2, 3]"""
        val (root, isList) = JsonImporter.parseJsonToTree(json)

        assertTrue(isList)
        assertEquals(0, root.childCount)  // 首元素是数字，非 Object/Array
    }

    // ─── TC-17: 字符串数组 ["a","b"] ─────────────────────────────────────────────

    @Test
    fun `TC-17 字符串数组_无子节点`() {
        val json = """["hello", "world"]"""
        val (root, isList) = JsonImporter.parseJsonToTree(json)

        assertTrue(isList)
        assertEquals(0, root.childCount)
    }

    // ─── TC-18: 空嵌套数组 [[]] ───────────────────────────────────────────────────

    @Test
    fun `TC-18 空嵌套数组_内层空数组_无子节点`() {
        val json = """[[]]"""
        val (root, isList) = JsonImporter.parseJsonToTree(json)

        assertTrue(isList)
        // 首元素是空数组，size()==0 递归返回 null
        assertEquals(0, root.childCount)
    }

    // ─── TC-19: 嵌套基本类型数组 [["a","b"]] ──────────────────────────────────────

    @Test
    fun `TC-19 嵌套基本类型数组_内层非Object_无子节点`() {
        val json = """[["a", "b", "c"]]"""
        val (root, isList) = JsonImporter.parseJsonToTree(json)

        assertTrue(isList)
        // 递归进去是字符串，非 JsonObject/JsonArray，返回 null
        assertEquals(0, root.childCount)
    }

    // ─── TC-20: 混合嵌套（根数组嵌套+对象嵌套+对象内数组字段）────────────────────────────

    @Test
    fun `TC-20 混合嵌套_根二层数组加对象嵌套加字段数组`() {
        val json = """
            [
              [
                {
                  "category": "混合",
                  "detail": {
                    "score": 95.5,
                    "tags": [{"label": "A", "weight": 0.8}]
                  }
                }
              ]
            ]
        """.trimIndent()
        val (root, isList) = JsonImporter.parseJsonToTree(json)

        assertTrue(isList)
        assertEquals(2, root.childCount)

        val category = child(root, 0)
        val detail   = child(root, 1)
        assertEquals("category", category.fieldName); assertEquals("String", category.type)
        assertEquals("detail",   detail.fieldName);   assertEquals("Object", detail.type)

        assertEquals(2, detail.childCount)
        val score = child(detail, 0)
        val tags  = child(detail, 1)
        assertEquals("score", score.fieldName); assertEquals("Decimal",      score.type)
        assertEquals("tags",  tags.fieldName);  assertEquals("List<Object>", tags.type)

        assertEquals(2, tags.childCount)
        assertEquals("label",  child(tags, 0).fieldName); assertEquals("String",  child(tags, 0).type)
        assertEquals("weight", child(tags, 1).fieldName); assertEquals("Decimal", child(tags, 1).type)
    }

    // ─── TC-21: 实际业务JSON - 账户诊断出参 ─────────────────────────────────────────

    @Test
    fun `TC-21 实际业务JSON_账户诊断出参`() {
        val json = """
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
        """.trimIndent()
        val (root, isList) = JsonImporter.parseJsonToTree(json)

        assertTrue(isList)
        assertEquals(5, root.childCount)

        assertEquals("名称",   child(root, 0).fieldName); assertEquals("String", child(root, 0).type)
        assertEquals("持仓比例", child(root, 1).fieldName); assertEquals("String", child(root, 1).type)
        assertEquals("持仓资产", child(root, 2).fieldName); assertEquals("String", child(root, 2).type)
        assertEquals("类型",   child(root, 4).fieldName); assertEquals("String", child(root, 4).type)

        val fundList = child(root, 3)
        assertEquals("基金列表",      fundList.fieldName)
        assertEquals("List<Object>", fundList.type)
        assertEquals(2,              fundList.childCount)
        assertEquals("代码", child(fundList, 0).fieldName)
        assertEquals("名称", child(fundList, 1).fieldName)
    }
}
