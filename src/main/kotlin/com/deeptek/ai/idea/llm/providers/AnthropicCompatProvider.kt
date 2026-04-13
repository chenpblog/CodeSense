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
            put("max_tokens", config.maxTokens)
            put("stream", true)
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

        try {
            val response = withContext(Dispatchers.IO) { call.execute() }

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw LlmException(
                    "LLM API 流式错误 (HTTP ${response.code})\nURL: ${config.baseUrl}\n$errorBody"
                )
            }

            val reader = BufferedReader(
                InputStreamReader(response.body!!.byteStream(), Charsets.UTF_8)
            )

            withContext(Dispatchers.IO) {
                var currentEvent = ""
                var line: String?

                while (reader.readLine().also { line = it } != null) {
                    val data = line ?: continue

                    // Anthropic SSE 格式:
                    // event: content_block_delta
                    // data: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}
                    when {
                        data.startsWith("event: ") -> {
                            currentEvent = data.removePrefix("event: ").trim()
                        }
                        data.startsWith("data: ") -> {
                            val payload = data.removePrefix("data: ").trim()
                            if (payload.isEmpty()) continue

                            try {
                                val jsonObj = json.parseToJsonElement(payload).jsonObject
                                val type = jsonObj["type"]?.jsonPrimitive?.content ?: ""

                                when (type) {
                                    "content_block_delta" -> {
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
                                        }
                                    }
                                    "message_stop" -> {
                                        // 流式结束
                                        break
                                    }
                                    "error" -> {
                                        val errorMsg = jsonObj["error"]?.jsonObject
                                            ?.get("message")?.jsonPrimitive?.content
                                            ?: payload
                                        throw LlmException("Anthropic API Error: $errorMsg")
                                    }
                                }
                            } catch (e: LlmException) {
                                throw e
                            } catch (e: Exception) {
                                logger.warn("Failed to parse Anthropic SSE: $payload", e)
                            }
                        }
                    }
                }
            }

            close()
        } catch (e: java.io.IOException) {
            close(LlmException("网络连接失败: ${e.message}", e))
        } catch (e: LlmException) {
            close(e)
        } catch (e: Exception) {
            close(e)
        }

        awaitClose { call.cancel() }
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
            put("max_tokens", config.maxTokens)
            put("stream", false)
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
        }

        val bodyStr = requestBody.toString()

        val httpRequest = Request.Builder()
            .url(config.baseUrl)
            .header("x-api-key", config.apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("Content-Type", "application/json")
            .post(bodyStr.toRequestBody(jsonMediaType))
            .build()

        val response = withContext(Dispatchers.IO) { httpClient.newCall(httpRequest).execute() }
        val responseBody = response.body?.string() ?: throw LlmException("Empty response body")

        if (!response.isSuccessful) {
            throw LlmException("LLM API 错误 (HTTP ${response.code})\nURL: ${config.baseUrl}\n$responseBody")
        }

        // 解析 Anthropic 响应并转换为 OpenAI 格式的 ChatResponse
        val jsonObj = json.parseToJsonElement(responseBody).jsonObject
        val contentArray = jsonObj["content"]?.jsonArray
        val textContent = contentArray
            ?.filter { it.jsonObject["type"]?.jsonPrimitive?.content == "text" }
            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.content ?: "" }
            ?: ""

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

        return systemPrompt to converted
    }
}
