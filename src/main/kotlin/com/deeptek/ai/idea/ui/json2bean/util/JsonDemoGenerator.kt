package com.deeptek.ai.idea.ui.json2bean.util

import com.deeptek.ai.idea.ui.json2bean.model.JsonPropertyNode
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

object JsonDemoGenerator {
    
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * 生成包含属性和类型的提示数据，用于发送给 AI，同时可作为 Demo 展示
     */
    fun generateDemoJson(rootNode: JsonPropertyNode, isRootList: Boolean): String {
        val rootElement = traverseNode(rootNode)
        
        // 如果根节点声明为 List，则将生成的 Object 包装在一层 Array 内
        val finalElement = if (isRootList) {
            val array = JsonArray()
            array.add(rootElement)
            array.add(traverseNode(rootNode)) // 生成第2个元素
            array
        } else {
            rootElement
        }
        
        return gson.toJson(finalElement)
    }

    private fun traverseNode(node: JsonPropertyNode): JsonElement {
        when {
            node.type == "Object" -> {
                val obj = JsonObject()
                for (i in 0 until node.childCount) {
                    val child = node.getChildAt(i) as JsonPropertyNode
                    obj.add(child.fieldName, traverseNode(child))
                }
                return obj
            }
            node.type == "List<Object>" -> {
                val array = JsonArray()
                // 生成 2 个元素，让 demo 更直观地体现 List 结构
                repeat(2) {
                    val itemObj = JsonObject()
                    for (i in 0 until node.childCount) {
                        val child = node.getChildAt(i) as JsonPropertyNode
                        itemObj.add(child.fieldName, traverseNode(child))
                    }
                    array.add(itemObj)
                }
                return array
            }
            else -> {
                // 基本类型直接返回 Demo 数据
                return when (node.type) {
                    "String" -> JsonPrimitive("demo_string")
                    "Decimal" -> JsonPrimitive(0.0)
                    "Boolean" -> JsonPrimitive(false)
                    else -> JsonPrimitive("demo")
                }
            }
        }
    }
}
