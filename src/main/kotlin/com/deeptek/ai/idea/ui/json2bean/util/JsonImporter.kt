package com.deeptek.ai.idea.ui.json2bean.util

import com.deeptek.ai.idea.ui.json2bean.model.JsonPropertyNode
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive

/**
 * 从 JSON 字符串反向构建 JsonPropertyNode 树，用于"粘贴 JSON 还原设计"功能
 */
object JsonImporter {

    /**
     * 解析 JSON 字符串并构建 JsonPropertyNode 树。
     * @return Pair<JsonPropertyNode, Boolean> — 第一个元素为 rootNode，第二个元素表示根节点是否是 List
     * @throws IllegalArgumentException 当 JSON 解析失败时抛出
     */
    fun parseJsonToTree(jsonString: String): Pair<JsonPropertyNode, Boolean> {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("JSON 内容不能为空")
        }

        val rootElement: JsonElement
        try {
            rootElement = JsonParser.parseString(trimmed)
        } catch (e: Exception) {
            throw IllegalArgumentException("JSON 格式错误: ${e.message}")
        }

        val rootNode = JsonPropertyNode.createRoot("Root")

        return when {
            rootElement.isJsonObject -> {
                parseObject(rootElement.asJsonObject, rootNode)
                Pair(rootNode, false)
            }
            rootElement.isJsonArray -> {
                val array = rootElement.asJsonArray
                rootNode.type = "List<Object>"
                if (array.size() > 0 && array[0].isJsonObject) {
                    parseObject(array[0].asJsonObject, rootNode)
                }
                Pair(rootNode, true)
            }
            else -> throw IllegalArgumentException("JSON 根元素必须是 Object 或 Array")
        }
    }

    private fun parseObject(jsonObject: JsonObject, parentNode: JsonPropertyNode) {
        for ((key, value) in jsonObject.entrySet()) {
            val childNode = createNodeFromElement(key, value)
            parentNode.add(childNode)
        }
    }

    private fun createNodeFromElement(fieldName: String, element: JsonElement): JsonPropertyNode {
        return when {
            element.isJsonObject -> {
                val node = JsonPropertyNode(fieldName, "Object", "")
                parseObject(element.asJsonObject, node)
                node
            }
            element.isJsonArray -> {
                val array = element.asJsonArray
                val node = JsonPropertyNode(fieldName, "List<Object>", "")
                // 从数组第一个元素推断子结构
                if (array.size() > 0) {
                    val first = array[0]
                    if (first.isJsonObject) {
                        parseObject(first.asJsonObject, node)
                    }
                    // 如果数组内是基本类型，暂不支持（当前 SUPPORTED_TYPES 无 List<String> 等）
                }
                node
            }
            element.isJsonPrimitive -> {
                val prim = element.asJsonPrimitive
                val type = inferPrimitiveType(prim)
                JsonPropertyNode(fieldName, type, "")
            }
            element.isJsonNull -> {
                JsonPropertyNode(fieldName, "String", "")
            }
            else -> {
                JsonPropertyNode(fieldName, "String", "")
            }
        }
    }

    private fun inferPrimitiveType(primitive: JsonPrimitive): String {
        return when {
            primitive.isBoolean -> "Boolean"
            primitive.isNumber -> "Decimal"
            else -> "String"
        }
    }
}
