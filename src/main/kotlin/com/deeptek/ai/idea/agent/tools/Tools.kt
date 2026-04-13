package com.deeptek.ai.idea.agent.tools

import com.deeptek.ai.idea.agent.AgentTool
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.serialization.json.*

/**
 * 读取文件工具
 */
class ReadFileTool : AgentTool {
    override val name = "read_file"
    override val description = "读取项目中指定文件的内容。参数: path - 相对于项目根目录的文件路径"
    override val parametersSchema: JsonElement = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "相对于项目根目录的文件路径")
            }
        }
        putJsonArray("required") { add("path") }
    }

    override suspend fun execute(project: Project, arguments: String): String {
        val args = Json.parseToJsonElement(arguments).jsonObject
        val path = args["path"]?.jsonPrimitive?.content
            ?: return "错误：缺少 path 参数"

        val basePath = project.basePath ?: return "错误：无法获取项目根目录"
        val fullPath = "$basePath/$path"
        val file = LocalFileSystem.getInstance().findFileByPath(fullPath)
            ?: return "错误：文件不存在: $path"

        if (file.isDirectory) return "错误：路径是一个目录: $path"
        if (file.length > 500_000) return "错误：文件过大 (${file.length} bytes)，请指定更具体的文件"

        return String(file.contentsToByteArray(), Charsets.UTF_8)
    }
}

/**
 * 写入文件工具
 */
class WriteFileTool : AgentTool {
    override val name = "write_file"
    override val description = "创建或修改项目中的文件。参数: path - 文件路径, content - 文件内容"
    override val parametersSchema: JsonElement = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "相对于项目根目录的文件路径")
            }
            putJsonObject("content") {
                put("type", "string")
                put("description", "要写入的文件内容")
            }
        }
        putJsonArray("required") { add("path"); add("content") }
    }

    override suspend fun execute(project: Project, arguments: String): String {
        val args = Json.parseToJsonElement(arguments).jsonObject
        val path = args["path"]?.jsonPrimitive?.content ?: return "错误：缺少 path 参数"
        val content = args["content"]?.jsonPrimitive?.content ?: return "错误：缺少 content 参数"

        val basePath = project.basePath ?: return "错误：无法获取项目根目录"
        val fullPath = "$basePath/$path"

        return try {
            val file = java.io.File(fullPath)
            file.parentFile?.mkdirs()
            file.writeText(content)
            LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath)
            "文件已写入: $path (${content.length} 字符)"
        } catch (e: Exception) {
            "写入文件失败: ${e.message}"
        }
    }
}

/**
 * 搜索代码工具
 */
class SearchCodeTool : AgentTool {
    override val name = "search_code"
    override val description = "在项目中搜索代码。参数: query - 搜索关键词, file_pattern - 可选的文件名模式"
    override val parametersSchema: JsonElement = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("query") {
                put("type", "string")
                put("description", "搜索的代码关键词或文本")
            }
            putJsonObject("file_pattern") {
                put("type", "string")
                put("description", "可选，文件名匹配模式，如 '*.java'")
            }
        }
        putJsonArray("required") { add("query") }
    }

    override suspend fun execute(project: Project, arguments: String): String {
        val args = Json.parseToJsonElement(arguments).jsonObject
        val query = args["query"]?.jsonPrimitive?.content ?: return "错误：缺少 query 参数"

        val basePath = project.basePath ?: return "错误：无法获取项目根目录"

        // 使用 grep 进行简单搜索
        return try {
            val process = ProcessBuilder("grep", "-rn", "--include=*.java", "--include=*.kt", "--include=*.xml", query, basePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            if (output.isBlank()) {
                "未找到匹配 '$query' 的代码"
            } else {
                // 将绝对路径替换为相对路径，并限制结果
                val lines = output.lines()
                    .map { it.replace(basePath, ".") }
                    .take(30)
                "搜索结果 (${lines.size} 条):\n${lines.joinToString("\n")}"
            }
        } catch (e: Exception) {
            "搜索失败: ${e.message}"
        }
    }
}

/**
 * 执行命令工具
 */
class RunCommandTool : AgentTool {
    override val name = "run_command"
    override val description = "在项目根目录执行终端命令。参数: command - 要执行的命令"
    override val parametersSchema: JsonElement = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("command") {
                put("type", "string")
                put("description", "要执行的终端命令")
            }
        }
        putJsonArray("required") { add("command") }
    }

    override suspend fun execute(project: Project, arguments: String): String {
        val args = Json.parseToJsonElement(arguments).jsonObject
        val command = args["command"]?.jsonPrimitive?.content ?: return "错误：缺少 command 参数"

        // 安全检查：禁止危险命令
        val dangerous = listOf("rm -rf /", "mkfs", "dd if=", ":(){ :|:& };:")
        if (dangerous.any { command.contains(it) }) {
            return "错误：检测到危险命令，已拒绝执行"
        }

        val basePath = project.basePath ?: return "错误：无法获取项目根目录"

        return try {
            val process = ProcessBuilder("bash", "-c", command)
                .directory(java.io.File(basePath))
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()

            if (output.length > 5000) {
                "命令输出 (exit=$exitCode, 已截断):\n${output.take(5000)}\n...[输出已截断]"
            } else {
                "命令输出 (exit=$exitCode):\n$output"
            }
        } catch (e: Exception) {
            "命令执行失败: ${e.message}"
        }
    }
}

/**
 * 获取文件结构工具
 */
class FileStructureTool : AgentTool {
    override val name = "get_file_structure"
    override val description = "获取指定文件的代码结构（类、方法、字段列表）。参数: path - 文件路径"
    override val parametersSchema: JsonElement = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "相对于项目根目录的文件路径")
            }
        }
        putJsonArray("required") { add("path") }
    }

    override suspend fun execute(project: Project, arguments: String): String {
        val args = Json.parseToJsonElement(arguments).jsonObject
        val path = args["path"]?.jsonPrimitive?.content ?: return "错误：缺少 path 参数"

        val basePath = project.basePath ?: return "错误：无法获取项目根目录"
        val fullPath = "$basePath/$path"
        val file = LocalFileSystem.getInstance().findFileByPath(fullPath)
            ?: return "错误：文件不存在: $path"

        val content = String(file.contentsToByteArray(), Charsets.UTF_8)
        val lines = content.lines()

        // 简单提取结构信息
        val structure = StringBuilder()
        structure.appendLine("文件: $path")
        structure.appendLine("总行数: ${lines.size}")
        structure.appendLine()
        structure.appendLine("结构概要:")

        lines.forEachIndexed { index, line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("class ") || trimmed.startsWith("public class ") ||
                trimmed.startsWith("interface ") || trimmed.startsWith("abstract class ") ||
                trimmed.startsWith("data class ") || trimmed.startsWith("object ") ->
                    structure.appendLine("  L${index + 1}: $trimmed")

                (trimmed.startsWith("fun ") || trimmed.startsWith("public ") || trimmed.startsWith("private ") ||
                trimmed.startsWith("protected ") || trimmed.startsWith("override fun ")) &&
                trimmed.contains("(") && !trimmed.startsWith("//") ->
                    structure.appendLine("    L${index + 1}: $trimmed")
            }
        }

        return structure.toString()
    }
}

/**
 * 列出目录工具
 */
class ListFilesTool : AgentTool {
    override val name = "list_files"
    override val description = "列出指定目录下的文件和子目录。参数: path - 目录路径（相对于项目根）"
    override val parametersSchema: JsonElement = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("path") {
                put("type", "string")
                put("description", "相对于项目根目录的目录路径，默认为项目根 '.'")
            }
        }
    }

    override suspend fun execute(project: Project, arguments: String): String {
        val args = Json.parseToJsonElement(arguments).jsonObject
        val path = args["path"]?.jsonPrimitive?.content ?: "."

        val basePath = project.basePath ?: return "错误：无法获取项目根目录"
        val dir = java.io.File("$basePath/$path")

        if (!dir.exists()) return "错误：目录不存在: $path"
        if (!dir.isDirectory) return "错误：路径不是目录: $path"

        val entries = dir.listFiles()
            ?.filter { !it.name.startsWith(".") && it.name != "build" && it.name != "node_modules" }
            ?.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
            ?: return "目录为空"

        val sb = StringBuilder()
        sb.appendLine("目录: $path")
        entries.forEach { f ->
            val prefix = if (f.isDirectory) "📁 " else "📄 "
            val size = if (f.isFile) " (${f.length()} bytes)" else ""
            sb.appendLine("  $prefix${f.name}$size")
        }
        return sb.toString()
    }
}

/**
 * Git Diff 工具
 */
class GitDiffTool : AgentTool {
    override val name = "get_git_diff"
    override val description = "获取当前工作区的 Git 未提交变更。无需参数。"
    override val parametersSchema: JsonElement = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {}
    }

    override suspend fun execute(project: Project, arguments: String): String {
        val basePath = project.basePath ?: return "错误：无法获取项目根目录"
        return try {
            val process = ProcessBuilder("git", "diff", "--stat")
                .directory(java.io.File(basePath))
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            if (output.isBlank()) "工作区没有未提交的变更" else "Git Diff:\n$output"
        } catch (e: Exception) {
            "获取 Git Diff 失败: ${e.message}"
        }
    }
}

/**
 * 调用链分析工具
 */
class CallHierarchyTool : AgentTool {
    override val name = "analyze_call_hierarchy"
    override val description = "分析指定方法的调用链（谁调用了它、它调用了谁）。参数: class_name - 类名, method_name - 方法名"
    override val parametersSchema: JsonElement = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("class_name") {
                put("type", "string")
                put("description", "方法所在的类名")
            }
            putJsonObject("method_name") {
                put("type", "string")
                put("description", "方法名")
            }
        }
        putJsonArray("required") { add("class_name"); add("method_name") }
    }

    override suspend fun execute(project: Project, arguments: String): String {
        val args = Json.parseToJsonElement(arguments).jsonObject
        val className = args["class_name"]?.jsonPrimitive?.content ?: return "错误：缺少 class_name 参数"
        val methodName = args["method_name"]?.jsonPrimitive?.content ?: return "错误：缺少 method_name 参数"

        // 使用 PSI 查找方法
        return try {
            com.intellij.openapi.application.ReadAction.compute<String, Throwable> {
                val javaPsiFacade = com.intellij.psi.JavaPsiFacade.getInstance(project)
                val scope = com.intellij.psi.search.GlobalSearchScope.projectScope(project)
                val psiClass = javaPsiFacade.findClasses(className, scope).firstOrNull()
                    ?: javaPsiFacade.findClasses("*.$className", scope).firstOrNull()

                if (psiClass == null) {
                    return@compute "未找到类: $className"
                }

                val methods = psiClass.findMethodsByName(methodName, false)
                if (methods.isEmpty()) {
                    return@compute "在类 $className 中未找到方法: $methodName"
                }

                val analyzer = com.deeptek.ai.idea.analysis.CallHierarchyAnalyzer.getInstance(project)
                val biTree = analyzer.analyzeBidirectional(methods[0], 5)

                val sb = StringBuilder()
                sb.appendLine("方法: ${className}.${methodName}")
                sb.appendLine()
                sb.appendLine("调用者 (callers): ${biTree.callerTree.children.size}")
                biTree.callerTree.children.forEach {
                    sb.appendLine("  - ${it.method.displayName}")
                }
                sb.appendLine()
                sb.appendLine("被调用 (callees): ${biTree.calleeTree.children.size}")
                biTree.calleeTree.children.forEach {
                    sb.appendLine("  - ${it.method.displayName}")
                }
                sb.toString()
            }
        } catch (e: Exception) {
            "调用链分析失败: ${e.message}"
        }
    }
}
