package com.deeptek.ai.idea.ui.json2bean.service

import com.deeptek.ai.idea.ui.json2bean.JsonBeanPreviewDialog
import com.deeptek.ai.idea.ui.json2bean.model.JsonPropertyNode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object JsonToBeanService {
    private val logger = Logger.getInstance(JsonToBeanService::class.java)

    fun generateJavaBeanAndPreview(
        project: Project,
        className: String,
        demoJson: String,
        rootNode: JsonPropertyNode,
        targetDirectory: VirtualFile?
    ) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // 不使用大模型，直接在本地计算与代码生成
                val builder = StringBuilder()
                val innerClasses = mutableListOf<String>()
                val imports = mutableSetOf("lombok.Data")

                builder.append("/**\n")
                if (rootNode.description.isNotEmpty()) {
                    builder.append(" * ${rootNode.description}\n")
                }
                builder.append(" */\n")
                builder.append("@Data\n")
                builder.append("public class $className {\n\n")

                for (i in 0 until rootNode.childCount) {
                    val child = rootNode.getChildAt(i) as JsonPropertyNode
                    generateField(child, builder, innerClasses, imports)
                }

                builder.append("\n")
                // Append Subclasses
                innerClasses.forEach {
                    builder.append(it)
                    builder.append("\n")
                }

                builder.append("}\n")
                
                val importsBlock = imports.sorted().joinToString("\n") { "import $it;" }
                val javaCode = importsBlock + "\n\n" + builder.toString()

                ApplicationManager.getApplication().invokeLater {
                    val previewDialog = JsonBeanPreviewDialog(project, className, demoJson, javaCode, rootNode, targetDirectory)
                    previewDialog.show()
                }
            } catch (e: Exception) {
                logger.warn("Error generating Java Bean locally: ${e.message}")
                ApplicationManager.getApplication().invokeLater {
                    com.intellij.openapi.ui.Messages.showErrorDialog(
                        project,
                        "生成 Java Bean 失败:\n${e.message}",
                        "Generation Error"
                    )
                }
            }
        }
    }

    private fun generateField(node: JsonPropertyNode, builder: StringBuilder, innerClasses: MutableList<String>, imports: MutableSet<String>) {
        val fName = node.fieldName.takeIf { it.isNotBlank() } ?: "unknownField"
        
        // 注释
        builder.append("    /**\n")
        if (node.description.isNotBlank()) {
            builder.append("     * ${node.description}\n")
        } else {
            builder.append("     * $fName\n")
        }
        builder.append("     */\n")

        val typeStr = resolveTypeStr(node, innerClasses, imports)
        builder.append("    private $typeStr $fName;\n\n")
    }

    private fun resolveTypeStr(node: JsonPropertyNode, innerClasses: MutableList<String>, imports: MutableSet<String>): String {
        return when (node.type) {
            "String" -> "String"
            "Decimal" -> {
                imports.add("java.math.BigDecimal")
                "BigDecimal"
            }
            "Boolean" -> "Boolean"
            "Object" -> {
                val innerName = capitalize(node.fieldName)
                buildInnerClass(innerName, node, innerClasses, imports)
                innerName
            }
            "List<Object>" -> {
                imports.add("java.util.List")
                val innerName = capitalize(node.fieldName)
                buildInnerClass(innerName, node, innerClasses, imports)
                "List<$innerName>"
            }
            else -> "String"
        }
    }

    private fun buildInnerClass(className: String, node: JsonPropertyNode, innerClasses: MutableList<String>, imports: MutableSet<String>) {
        val safeName = className.takeIf { it.isNotBlank() } ?: "InnerClass"
        val builder = StringBuilder()
        builder.append("    @Data\n")
        builder.append("    public static class $safeName {\n")

        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as JsonPropertyNode
            // 嵌套字段处理
            val fName = child.fieldName.takeIf { it.isNotBlank() } ?: "unknown"
            builder.append("        /**\n")
            if (child.description.isNotBlank()) {
                builder.append("         * ${child.description}\n")
            } else {
                builder.append("         * $fName\n")
            }
            builder.append("         */\n")
            
            val typeStr = resolveTypeStr(child, innerClasses, imports)
            builder.append("        private $typeStr $fName;\n\n")
        }

        builder.append("    }\n")
        innerClasses.add(builder.toString())
    }

    private fun capitalize(s: String): String {
        if (s.isEmpty()) return "Unknown"
        return s.substring(0, 1).uppercase() + s.substring(1)
    }

    fun generateJavaBeanFromMixed(className: String, englishJson: String, rootNode: JsonPropertyNode): String {
        val rootElement = com.google.gson.JsonParser.parseString(englishJson)
        val cloneRoot = cloneAndMergeNode(rootNode, rootElement)

        val builder = StringBuilder()
        val innerClasses = mutableListOf<String>()
        val imports = mutableSetOf("lombok.Data")

        builder.append("/**\n")
        if (rootNode.description.isNotEmpty()) {
            builder.append(" * ${rootNode.description}\n")
        }
        builder.append(" */\n")
        builder.append("@Data\n")
        builder.append("public class $className {\n\n")

        for (i in 0 until cloneRoot.childCount) {
            val child = cloneRoot.getChildAt(i) as JsonPropertyNode
            generateField(child, builder, innerClasses, imports)
        }

        builder.append("\n")
        innerClasses.forEach {
            builder.append(it)
            builder.append("\n")
        }

        builder.append("}\n")
        
        val importsBlock = imports.sorted().joinToString("\n") { "import $it;" }
        return importsBlock + "\n\n" + builder.toString()
    }

    private fun cloneAndMergeNode(node: JsonPropertyNode, jsonElement: com.google.gson.JsonElement): JsonPropertyNode {
        // 保存原版的中文名称与描述作为最终 JavaDoc
        val desc = if (node.description.isNotBlank()) "${node.fieldName} - ${node.description}" else node.fieldName
        
        val clone = JsonPropertyNode(node.fieldName, node.type, desc)
        
        // 尝试提取 JSON 对象（支持直接对象、数组、嵌套数组等场景）
        val obj = extractFirstJsonObject(jsonElement)
        if (obj == null) {
            logger.info("[cloneAndMerge] 无法提取 JsonObject, field=${node.fieldName}, type=${jsonElement.javaClass.simpleName}")
            return clone
        }

        val entries = obj.entrySet().toList()
        
        // 日志记录数量不匹配的情况
        if (node.childCount != entries.size) {
            logger.warn("[cloneAndMerge] 子节点数量不匹配: field=${node.fieldName}, " +
                "原始节点子数=${node.childCount}, 英文JSON键数=${entries.size}")
        }
        
        // 安全迭代：取两者的最小值，避免 IndexOutOfBoundsException
        val mergeCount = Math.min(node.childCount, entries.size)
        for (i in 0 until mergeCount) {
            val child = node.getChildAt(i) as JsonPropertyNode
            val entry = entries[i]
            
            val childClone = cloneAndMergeNode(child, entry.value)
            // 将真正的属性名覆写为 JSON 里的英文键名
            childClone.fieldName = entry.key
            clone.add(childClone)
        }
        
        // 如果英文 JSON 有多余的键（AI 可能新增了字段），记录但不处理
        if (entries.size > node.childCount) {
            logger.warn("[cloneAndMerge] 英文 JSON 有 ${entries.size - node.childCount} 个多余的键未合并: " +
                entries.drop(node.childCount).map { it.key })
        }
        
        return clone
    }

    /**
     * 从 JsonElement 中递归提取第一个 JsonObject
     *
     * 支持场景：
     * - 直接 JsonObject → 返回自身
     * - [{ }] → 返回首个 Object
     * - [[{ }]] → 递归深入嵌套数组，直到找到 Object
     * - 基本类型 / 空数组 → 返回 null
     */
    private fun extractFirstJsonObject(element: com.google.gson.JsonElement, depth: Int = 0): com.google.gson.JsonObject? {
        if (depth > 5) return null  // 防止无限递归
        return when {
            element.isJsonObject -> element.asJsonObject
            element.isJsonArray && element.asJsonArray.size() > 0 -> {
                extractFirstJsonObject(element.asJsonArray.get(0), depth + 1)
            }
            else -> null
        }
    }
}
