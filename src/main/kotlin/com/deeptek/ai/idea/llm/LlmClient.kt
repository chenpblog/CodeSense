package com.deeptek.ai.idea.llm

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * 统一 HTTP 客户端 — 负责与 LLM API 的通信
 *
 * 封装 OkHttp，提供:
 * - 非流式 POST 请求
 * - SSE 流式请求
 * - 统一的错误处理
 *
 * 所有模型都使用 OpenAI 兼容的 Chat Completions API 格式。
 */
class LlmClient private constructor() {

    private val logger = Logger.getInstance(LlmClient::class.java)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    companion object {
        /** 可重试的 HTTP 状态码：服务过载、限流、服务端临时故障 */
        private val RETRYABLE_STATUS_CODES = setOf(429, 500, 502, 503, 529)
        private const val MAX_RETRIES = 3
        private const val INITIAL_DELAY_MS = 1000L

        @Volatile
        private var instance: LlmClient? = null

        fun getInstance(): LlmClient {
            return instance ?: synchronized(this) {
                instance ?: LlmClient().also { instance = it }
            }
        }
    }

    /**
     * 非流式 Chat Completion 请求（含自动重试）
     */
    suspend fun chatCompletion(
        baseUrl: String,
        apiKey: String,
        request: ChatRequest
    ): ChatResponse {
        val requestBody = json.encodeToString(ChatRequest.serializer(), request.copy(stream = false))
        logger.debug("LLM Request: model=${request.model}, messages=${request.messages.size}")

        var lastException: Exception? = null

        for (attempt in 0..MAX_RETRIES) {
            if (attempt > 0) {
                val delayMs = INITIAL_DELAY_MS * (1L shl (attempt - 1)) // 指数退避: 1s, 2s, 4s
                logger.info("LLM Non-Stream 第 ${attempt}/${MAX_RETRIES} 次重试, 等待 ${delayMs}ms...")
                kotlinx.coroutines.delay(delayMs)
            }

            try {
                val httpRequest = Request.Builder()
                    .url(baseUrl)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(requestBody.toRequestBody(jsonMediaType))
                    .build()

                val response = withContext(Dispatchers.IO) { httpClient.newCall(httpRequest).execute() }
                val responseBody = response.body?.string()
                    ?: throw LlmException("Empty response body")

                if (!response.isSuccessful) {
                    if (response.code in RETRYABLE_STATUS_CODES && attempt < MAX_RETRIES) {
                        logger.warn("LLM Non-Stream 可重试错误 (HTTP ${response.code}), 将进行重试: $responseBody")
                        lastException = LlmException(
                            "LLM API 错误 (HTTP ${response.code}): $responseBody"
                        )
                        continue
                    }
                    val error = try {
                        json.decodeFromString(ApiError.serializer(), responseBody)
                    } catch (e: Exception) {
                        null
                    }
                    throw LlmException(
                        "LLM API 错误 (HTTP ${response.code}): " +
                        (error?.error?.message ?: responseBody)
                    )
                }

                val chatResponse = json.decodeFromString(ChatResponse.serializer(), responseBody)
                logger.debug("LLM Response: choices=${chatResponse.choices.size}, " +
                    "usage=${chatResponse.usage?.totalTokens} tokens")

                if (attempt > 0) {
                    logger.info("LLM Non-Stream 第 ${attempt} 次重试成功")
                }

                return chatResponse
            } catch (e: LlmException) {
                lastException = e
                throw e
            } catch (e: java.io.IOException) {
                lastException = e
                if (attempt < MAX_RETRIES) {
                    logger.warn("LLM Non-Stream 网络异常, 将进行重试: ${e.message}")
                    continue
                }
                throw LlmException("网络连接失败 (已重试 $MAX_RETRIES 次): ${e.message}", e)
            }
        }

        throw lastException ?: LlmException("未知错误: 重试耗尽")
    }

    /**
     * 流式 Chat Completion 请求 (SSE)
     *
     * 返回 Flow<ChatChunk>，每个 chunk 包含一个增量内容片段。
     * 调用方通过 collect 收集流式数据。
     */
    suspend fun chatCompletionStream(
        baseUrl: String,
        apiKey: String,
        request: ChatRequest
    ): Flow<ChatChunk> = callbackFlow {
        val requestBody = json.encodeToString(ChatRequest.serializer(), request.copy(stream = true))

        logger.debug("LLM Stream Request: model=${request.model}")

        val fullUrl = baseUrl

        val httpRequest = Request.Builder()
            .url(fullUrl)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .post(requestBody.toRequestBody(jsonMediaType))
            .build()

        val call = httpClient.newCall(httpRequest)
        var response: Response? = null

        try {
            response = withContext(Dispatchers.IO) { call.execute() }

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                response.close()
                throw LlmException("LLM API 流式错误 (HTTP ${response.code})\nURL: $fullUrl\n$errorBody")
            }

            val reader = BufferedReader(
                InputStreamReader(response.body!!.byteStream(), Charsets.UTF_8)
            )

            withContext(Dispatchers.IO) {
                try {
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val data = line ?: continue

                        // SSE 格式: "data: {...}" 或 "data: [DONE]"
                        if (!data.startsWith("data: ")) continue

                        val payload = data.removePrefix("data: ").trim()
                        if (payload == "[DONE]") break
                        if (payload.isEmpty()) continue

                        try {
                            val chunk = json.decodeFromString(ChatChunk.serializer(), payload)
                            trySend(chunk)
                        } catch (e: Exception) {
                            logger.warn("Failed to parse SSE chunk: $payload", e)
                        }
                    }
                } finally {
                    // 确保关闭 reader 和 response body，避免连接泄漏
                    try { reader.close() } catch (_: Exception) {}
                    try { response.close() } catch (_: Exception) {}
                }
            }

            close()
        } catch (e: IOException) {
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
}

/**
 * LLM 调用异常
 */
class LlmException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
