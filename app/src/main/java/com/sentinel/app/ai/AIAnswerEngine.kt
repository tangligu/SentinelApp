package com.sentinel.app.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sentinel.app.data.SubjectConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * AI解答引擎 - 将老师的问题发送给AI并获取答案
 */
class AIAnswerEngine(
    private val apiEndpoint: String = "https://api.openai.com/v1/chat/completions",
    private val apiKey: String = "",
    private val model: String = "gpt-3.5-turbo"
) {
    companion object {
        private const val TAG = "AIAnswerEngine"
        private const val TIMEOUT_SECONDS = 30L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // 对话历史
    private val messages = mutableListOf<Map<String, String>>()

    /**
     * 设置科目上下文（系统提示词）
     */
    fun setSubject(subject: SubjectConfig) {
        messages.clear()
        messages.add(
            mapOf(
                "role" to "system",
                "content" to subject.aiSystemPrompt
            )
        )
    }

    /**
     * 发送问题给AI，获取答案
     * @param question 老师提出的问题文本
     * @param context 额外的上下文信息（如之前的课堂讨论内容）
     * @return AI生成的答案，失败时返回错误信息
     */
    suspend fun getAnswer(question: String, context: String = ""): AnswerResult {
        return withContext(Dispatchers.IO) {
            try {
                // 构建消息
                val userMessage = buildString {
                    append("老师提出的问题：$question")
                    if (context.isNotBlank()) {
                        append("\n\n课堂上下文：$context")
                    }
                }

                // 添加用户消息
                val currentMessages = messages.toMutableList()
                currentMessages.add(mapOf("role" to "user", "content" to userMessage))

                // 构建请求体
                val requestBody = mapOf(
                    "model" to model,
                    "messages" to currentMessages,
                    "temperature" to 0.7,
                    "max_tokens" to 1024,
                    "stream" to false
                )

                val jsonBody = gson.toJson(requestBody)
                Log.d(TAG, "Sending request to AI: $apiEndpoint")

                val request = Request.Builder()
                    .url(apiEndpoint)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    Log.e(TAG, "API error: ${response.code} - $responseBody")
                    return@withContext AnswerResult(
                        success = false,
                        answer = "AI请求失败 (${response.code})",
                        errorMessage = "HTTP ${response.code}: ${parseErrorMessage(responseBody)}"
                    )
                }

                // 解析响应
                val answer = parseAIResponse(responseBody)
                Log.d(TAG, "Got answer: ${answer.take(100)}...")

                // 保存到对话历史（保留助手的回答）
                messages.add(mapOf("role" to "user", "content" to userMessage))
                messages.add(mapOf("role" to "assistant", "content" to answer))

                // 限制历史长度
                while (messages.size > 20) {
                    // 保留system消息，移除最早的对话
                    if (messages.size > 2) {
                        messages.removeAt(1)
                    }
                }

                AnswerResult(
                    success = true,
                    answer = answer
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get AI answer: ${e.message}", e)
                AnswerResult(
                    success = false,
                    answer = "网络错误: ${e.message}",
                    errorMessage = e.message
                )
            }
        }
    }

    /**
     * 解析AI响应中的回答文本
     */
    private fun parseAIResponse(json: String): String {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val choices = root.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val choice = choices[0].asJsonObject
                val message = choice.getAsJsonObject("message")
                message?.get("content")?.asString ?: "无法解析回答"
            } else {
                "AI返回了空结果"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AI response: ${e.message}")
            json.take(500) // 返回原始响应的前500字符
        }
    }

    private fun parseErrorMessage(json: String): String {
        return try {
            val root = JsonParser.parseString(json).asJsonObject
            val error = root.getAsJsonObject("error")
            error?.get("message")?.asString ?: "未知错误"
        } catch (e: Exception) {
            json.take(200)
        }
    }

    /**
     * 清空对话历史
     */
    fun clearHistory() {
        val systemMessage = messages.firstOrNull { it["role"] == "system" }
        messages.clear()
        if (systemMessage != null) {
            messages.add(systemMessage)
        }
    }

    /**
     * 检查API是否已配置
     */
    fun isConfigured(): Boolean {
        return apiKey.isNotBlank() && apiEndpoint.isNotBlank()
    }

    data class AnswerResult(
        val success: Boolean,
        val answer: String,
        val errorMessage: String? = null
    )
}