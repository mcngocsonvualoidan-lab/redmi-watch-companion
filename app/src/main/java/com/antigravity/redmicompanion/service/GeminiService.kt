package com.antigravity.redmicompanion.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    // Có thể dùng gemini-2.5-flash hoặc gemini-2.0-flash
    private val MODEL_NAME = "gemini-2.5-flash"

    suspend fun askGemini(apiKey: String, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Chưa cấu hình API Key"))
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent?key=$apiKey"

        val requestBodyJson = JSONObject().apply {
            // Chỉ thị hệ thống: Tối ưu cho màn hình đồng hồ
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "Bạn là trợ lý AI trên đồng hồ thông minh Redmi Watch 5 Lite. Trả lời cực kỳ ngắn gọn, súc tích (tối đa 1-2 câu, dưới 35 từ), đi thẳng vào câu trả lời, không dùng cú pháp markdown phức tạp vì màn hình đồng hồ nhỏ.")
                    })
                })
            })

            // Nội dung câu hỏi
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })

            // Giới hạn độ dài output để phản hồi nhanh
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 120)
                put("temperature", 0.7)
            })
        }

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = requestBodyJson.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IOException("Lỗi API (${response.code}): $responseBody")
                )
            }

            // Phân tích kết quả JSON
            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val content = firstCandidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val answerText = parts.getJSONObject(0).optString("text", "")
                    return@withContext Result.success(answerText.trim())
                }
            }

            return@withContext Result.failure(IOException("Không thể trích xuất câu trả lời từ Gemini."))
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }
}
