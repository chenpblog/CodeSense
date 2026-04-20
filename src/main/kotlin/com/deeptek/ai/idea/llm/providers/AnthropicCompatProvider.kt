package com.deeptek.ai.idea.llm.providers

import com.deeptek.ai.idea.llm.*
import com.deeptek.ai.idea.settings.ProviderConfig
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Anthropic Messages API 兼容 Provider
 *
 * 支持 MiniMax 等服务商的 Anthropic 兼容端点。
 * 请求格式、认证方式、流式解析都遵循 Anthropic Messages API 规范。
 *
 * 与 OpenAI 兼容 Provider 的主要区别：
 * - 认证头：x-api-key（非 Bearer token）
 * - system 消息作为顶级参数（非 messages 内的 role:system）
 * - 流式事件使用 event: + data: 双行格式
 * - 工具调用格式不同
 */
class AnthropicCompatProvider(private val config: ProviderConfig) : LlmProvider {

    override val name: String get() = config.displayName
    override val modelName: String get() = config.modelName

    private val logger = Logger.getInstance(AnthropicCompatProvider::class.java)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override suspend fun chatCompletionStream(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?,
        temperature: Double?,
        maxTokens: Int?
    ): Flow<ChatChunk> = callbackFlow {

        // 将 OpenAI 格式的消息转换为 Anthropic 格式
        val (systemPrompt, anthropicMessages) = convertMessages(messages)

        val requestBody = buildJsonObject {
            put("model", config.modelName)
            put("max_tokens", maxTokens ?: config.maxTokens)
            put("stream", true)
            put("temperature", temperature ?: config.temperature)
            if (systemPrompt != null) {
                put("system", systemPrompt)
            }
            putJsonArray("messages") {
                anthropicMessages.forEach { msg ->
                    addJsonObject {
                        put("role", msg.first)
                        put("content", msg.second)
                    }
                }
            }
            // Anthropic 格式的工具定义（如有）
            if (!tools.isNullOrEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("name", tool.function.name)
                            put("description", tool.function.description)
                            putJsonObject("input_schema") {
                                // 从 OpenAI schema 转为 Anthropic input_schema
                                val params = tool.function.parameters
                                if (params is JsonObject) {
                                    params.entries.forEach { (k, v) -> put(k, v) }
                                }
                            }
                        }
                    }
                }
            }
        }

        val bodyStr = requestBody.toString()
        logger.info("Anthropic Request: model=${config.modelName}, url=${config.baseUrl}")

        val httpRequest = Request.Builder()
            .url(config.baseUrl)
            .header("x-api-key", config.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(bodyStr.toRequestBody(jsonMediaType))
            .build()

        val call = httpClient.newCall(httpRequest)
        var response: Response? = null

        try {
            response = withContext(Dispatchers.IO) { call.execute() }

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                response.close()
                throw LlmException(
                    "LLM API 流式错误 (HTTP ${response.code})\nURL: ${config.baseUrl}\n$errorBody"
                )
            }

            val reader = BufferedReader(
                InputStreamReader(response.body!!.byteStream(), Charsets.UTF_8)
            )

            withContext(Dispatchers.IO) {
                try {
                    var currentEvent = ""
                    var line: String?
                    var lineCount = 0

                    while (reader.readLine().also { line = it } != null) {
                        val data = line ?: continue
                        lineCount++

                        // 前 20 行打印原始内容以便调试
                        if (lineCount <= 20) {
                            logger.info("[SSE] 第${lineCount}行原始数据: '$data'")
                        }

                        // Anthropic SSE 格式（兼容 "event: xxx" 和 "event:xxx" 两种写法）:
                        // event: content_block_delta
                        // data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
                        when {
                            data.startsWith("event:") -> {
                                currentEvent = data.removePrefix("event:").trim()
                                if (lineCount <= 20) logger.info("[SSE] event=$currentEvent")
                            }
                            data.startsWith("data:") -> {
                                val payload = data.removePrefix("data:").trim()
                                if (payload.isEmpty()) continue

                                try {
                                    val jsonObj = json.parseToJsonElement(payload).jsonObject
                                    val type = jsonObj["type"]?.jsonPrimitive?.content ?: ""

                                    if (lineCount <= 20) logger.info("[SSE] data type=$type, keys=${jsonObj.keys}")

                                    // 检测是否为 OpenAI 兼容格式（GLM、DeepSeek 等）
                                    val isOpenAiFormat = jsonObj.containsKey("choices") &&
                                        (jsonObj["object"]?.jsonPrimitive?.content == "chat.completion.chunk" || type.isEmpty())

                                    when {
                                        // ====== OpenAI 兼容格式（自动检测回退） ======
                                        isOpenAiFormat -> {
                                            try {
                                                val chunk = json.decodeFromString(ChatChunk.serializer(), payload)
                                                val deltaContent = chunk.deltaContent
                                                val deltaReasoning = chunk.deltaReasoningContent
                                                val finishReason = chunk.choices.firstOrNull()?.finishReason

                                                if (!deltaContent.isNullOrEmpty() || !deltaReasoning.isNullOrEmpty()) {
                                                    trySend(chunk)
                                                }
                                                if (finishReason == "stop") {
                                                    if (lineCount <= 20) logger.info("[SSE] OpenAI 格式收到 finish_reason=stop")
                                                }
                                            } catch (e: Exception) {
                                                // 解析 OpenAI chunk 失败时仅在前20行打印
                                                if (lineCount <= 20) logger.warn("[SSE] OpenAI 格式解析失败: ${e.message}, payload=${payload.take(200)}")
                                            }
                                        }
                                        // ====== 标准 Anthropic 格式 ======
                                        type == "content_block_delta" -> {
                                            val delta = jsonObj["delta"]?.jsonObject
                                            val deltaType = delta?.get("type")?.jsonPrimitive?.content
                                            if (deltaType == "text_delta") {
                                                val text = delta["text"]?.jsonPrimitive?.content ?: ""
                                                if (text.isNotEmpty()) {
                                                    trySend(ChatChunk(
                                                        id = "anthropic",
                                                        choices = listOf(
                                                            ChunkChoice(
                                                                index = 0,
                                                                delta = DeltaMessage(
                                                                    role = "assistant",
                                                                    content = text
                                                                ),
                                                                finishReason = null
                                                            )
                                                        )
                                                    ))
                                                }
                                            } else {
                                                if (lineCount <= 20) logger.info("[SSE] content_block_delta 但 deltaType=$deltaType (非 text_delta)")
                                            }
                                        }
                                        type == "message_stop" -> {
                                            logger.info("[SSE] 收到 message_stop, 总行数=$lineCount")
                                            break
                                        }
                                        type == "error" -> {
                                            val errorMsg = jsonObj["error"]?.jsonObject
                                                ?.get("message")?.jsonPrimitive?.content
                                                ?: payload
                                            throw LlmException("Anthropic API Error: $errorMsg")
                                        }
                                        else -> {
                                            if (lineCount <= 20) logger.info("[SSE] 未处理的 type=$type, payload=${payload.take(200)}")
                                        }
                                    }
                                } catch (e: LlmException) {
                                    throw e
                                } catch (e: Exception) {
                                    if (lineCount <= 20) {
                                        logger.warn("Failed to parse SSE: $payload", e)
                                    }
                                }
                            }
                            data.isNotBlank() -> {
                                if (lineCount <= 20) logger.info("[SSE] 非 event/data 行: '$data'")
                            }
                        }
                    }
                    logger.info("[SSE] 流式读取结束, 总行数=$lineCount")
                } finally {
                    // 确保关闭 reader 和 response body，避免连接泄漏
                    try { reader.close() } catch (_: Exception) {}
                    try { response.close() } catch (_: Exception) {}
                }
            }

            close()
        } catch (e: java.io.IOException) {
            response?.close()
            close(LlmException("网络连接失败: ${e.message}", e))
        } catch (e: LlmException) {
            response?.close()
            close(e)
        } catch (e: Exception) {
            response?.close()
            close(e)
        }

        awaitClose {
            call.cancel()
            try { response?.close() } catch (_: Exception) {}
        }
    }

    companion object {
        /** 可重试的 HTTP 状态码：服务过载、限流、服务端临时故障 */
        private val RETRYABLE_STATUS_CODES = setOf(429, 500, 502, 503, 529)
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 1000L
    }

    override suspend fun chatCompletion(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>?,
        temperature: Double?,
        maxTokens: Int?
    ): ChatResponse {
        val (systemPrompt, anthropicMessages) = convertMessages(messages)

        val requestBody = buildJsonObject {
            put("model", config.modelName)
            put("max_tokens", maxTokens ?: config.maxTokens)
            put("stream", false)
            put("temperature", temperature ?: config.temperature)
            if (systemPrompt != null) {
                put("system", systemPrompt)
            }
            putJsonArray("messages") {
                anthropicMessages.forEach { msg ->
                    addJsonObject {
                        put("role", msg.first)
                        put("content", msg.second)
                    }
                }
            }
            // Anthropic 格式的工具定义（如有）
            if (!tools.isNullOrEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("name", tool.function.name)
                            put("description", tool.function.description)
                            putJsonObject("input_schema") {
                                val params = tool.function.parameters
                                if (params is JsonObject) {
                                    params.entries.forEach { (k, v) -> put(k, v) }
                                }
                            }
                        }
                    }
                }
            }
        }

        val bodyStr = requestBody.toString()
        logger.info("Anthropic Non-Stream Request: model=${config.modelName}, url=${config.baseUrl}")
        logger.debug("Request body: $bodyStr")

        var lastException: Exception? = null

        for (attempt in 0..MAX_RETRIES) {
            if (attempt > 0) {
                val delayMs = INITIAL_DELAY_MS * (1L shl (attempt - 1)) // 指数退避: 1s, 2s, 4s
                logger.info("Anthropic Non-Stream 第 ${attempt}/${MAX_RETRIES} 次重试, 等待 ${delayMs}ms...")
                kotlinx.coroutines.delay(delayMs)
            }

            try {
                val httpRequest = Request.Builder()
                    .url(config.baseUrl)
                    .header("x-api-key", config.apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .header("Content-Type", "application/json")
                    .post(bodyStr.toRequestBody(jsonMediaType))
                    .build()

                val response = withContext(Dispatchers.IO) { httpClient.newCall(httpRequest).execute() }
                val responseBody = response.body?.string() ?: throw LlmException("Empty response body")

                logger.info("Anthropic Non-Stream Response code: ${response.code}")

                if (!response.isSuccessful) {
                    if (response.code in RETRYABLE_STATUS_CODES && attempt < MAX_RETRIES) {
                        logger.warn("Anthropic Non-Stream 可重试错误 (HTTP ${response.code}), 将进行重试: $responseBody")
                        lastException = LlmException("LLM API 错误 (HTTP ${response.code})\nURL: ${config.baseUrl}\n$responseBody")
                        continue
                    }
                    logger.error("Anthropic Non-Stream Error: $responseBody")
                    throw LlmException("LLM API 错误 (HTTP ${response.code})\nURL: ${config.baseUrl}\n$responseBody")
                }

                // 解析 Anthropic 响应并转换为 OpenAI 格式的 ChatResponse
                logger.info("Anthropic Non-Stream Response body (前500字): ${responseBody.take(500)}")
                val jsonObj = json.parseToJsonElement(responseBody).jsonObject
                val contentArray = jsonObj["content"]?.jsonArray
                logger.info("Anthropic content blocks 数量: ${contentArray?.size ?: 0}")
                contentArray?.forEachIndexed { idx, block ->
                    val blockType = block.jsonObject["type"]?.jsonPrimitive?.content
                    logger.info("  content block[$idx] type=$blockType")
                }
                val textContent = contentArray
                    ?.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
                    ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
                    ?: ""
                logger.info("Anthropic Non-Stream 提取到 textContent 长度: ${textContent.length}, 前200字: ${textContent.take(200)}")

                if (textContent.isEmpty()) {
                    logger.warn("Anthropic Non-Stream textContent 为空！完整 responseBody: $responseBody")
                }

                if (attempt > 0) {
                    logger.info("Anthropic Non-Stream 第 ${attempt} 次重试成功")
                }

                return ChatResponse(
                    id = jsonObj["id"]?.jsonPrimitive?.content ?: "anthropic",
                    choices = listOf(
                        ChatChoice(
                            index = 0,
                            message = ChatMessage(
                                role = "assistant",
                                content = textContent
                            ),
                            finishReason = jsonObj["stop_reason"]?.jsonPrimitive?.content ?: "stop"
                        )
                    ),
                    usage = null
                )
            } catch (e: LlmException) {
                lastException = e
                throw e
            } catch (e: java.io.IOException) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    logger.warn("Anthropic Non-Stream 网络异常, 将进行重试: ${e.message}")
                    continue
                }
                throw LlmException("网络连接失败 (已重试 $MAX_RETRIES 次): ${e.message}", e)
            }
        }

        // 理论上不会走到这里，但作为兜底
        throw lastException ?: LlmException("未知错误: 重试耗尽")
    }

    /**
     * 将 OpenAI 格式的消息列表转换为 Anthropic 格式
     *
     * Anthropic 的 system 是顶级参数，不在 messages 中。
     * 返回: (systemPrompt, List<Pair<role, content>>)
     */
    private fun convertMessages(messages: List<ChatMessage>): Pair<String?, List<Pair<String, String>>> {
        var systemPrompt: String? = null
        val converted = mutableListOf<Pair<String, String>>()

        for (msg in messages) {
            when (msg.role) {
                "system" -> systemPrompt = msg.content
                "user" -> converted.add("user" to (msg.content ?: ""))
                "assistant" -> converted.add("assistant" to (msg.content ?: ""))
                "tool" -> {
                    // 工具结果转为 user 消息
                    converted.add("user" to "[Tool Result] ${msg.content ?: ""}")
                }
            }
        }

        // Anthropic 要求消息必须以 user 开头
        if (converted.isEmpty() || converted.first().first != "user") {
            converted.add(0, "user" to "Hello")
        }

        // Anthropic 要求不能有连续的同角色消息，需要合并
        val merged = mutableListOf<Pair<String, String>>()
        for (msg in converted) {
            if (merged.isNotEmpty() && merged.last().first == msg.first) {
                // 合并相邻同角色消息
                val last = merged.removeAt(merged.lastIndex)
                merged.add(last.first to "${last.second}\n\n${msg.second}")
            } else {
                merged.add(msg)
            }
        }

        return systemPrompt to merged
    }
}
