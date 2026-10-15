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
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    // Danh sách model ưu tiên từ nhanh nhất đến ổn định nhất
    private val PREFERRED_MODELS = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-2.0-flash")

    suspend fun askGemini(apiKey: String, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Chưa có Gemini API Key. Vui lòng bấm 'Lấy API Key Miễn Phí' để tạo key và dán vào ô nhập.")
            )
        }

        var lastError: Exception? = null

        // Thử lần lượt các model tương thích
        for (modelName in PREFERRED_MODELS) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$trimmedKey"

            val requestBodyJson = JSONObject().apply {
                // Chỉ thị hệ thống: Tối ưu cho màn hình đồng hồ đeo tay
                put("system_instruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "Bạn là trợ lý AI trên đồng hồ thông minh Redmi Watch 5 Lite. Hãy trả lời cực kỳ ngắn gọn, súc tích (1-2 câu, dưới 30 từ), đi thẳng vào trọng tâm câu hỏi, không dùng cú pháp markdown phức tạp vì màn hình đồng hồ nhỏ.")
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

                // Cấu hình tối ưu độ dài phản hồi
                put("generationConfig", JSONObject().apply {
                    put("maxOutputTokens", 600)
                    put("temperature", 0.7)
                    put("thinkingConfig", JSONObject().apply {
                        put("thinkingBudget", 0)
                    })
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

                if (response.isSuccessful) {
                    val root = JSONObject(responseBody)
                    val candidates = root.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val answerText = parts.getJSONObject(0).optString("text", "")
                            if (answerText.isNotBlank()) {
                                return@withContext Result.success(answerText.trim())
                            }
                        }
                    }
                } else {
                    val friendlyError = parseApiError(response.code, responseBody)
                    lastError = IOException(friendlyError)

                    // Nếu lỗi do API key (403, 400) thì dừng ngay không cần thử model khác
                    if (response.code == 403 || response.code == 400) {
                        return@withContext Result.failure(lastError)
                    }
                }
            } catch (e: Exception) {
                lastError = e
            }
        }

        return@withContext Result.failure(lastError ?: IOException("Không thể kết nối đến máy chủ Gemini AI"))
    }

    suspend fun verifyApiKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Vui lòng dán API Key vào ô trước khi kiểm tra!"))
        }

        val testUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$trimmedKey"
        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", "Hi") })
                    })
                })
            })
        }.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder().url(testUrl).post(body).build()

        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                return@withContext Result.success("✅ API Key hợp lệ và hoạt động tốt!")
            } else {
                return@withContext Result.failure(IOException(parseApiError(response.code, responseBody)))
            }
        } catch (e: Exception) {
            return@withContext Result.failure(IOException("Lỗi kết nối mạng: ${e.message}"))
        }
    }

    private fun parseApiError(statusCode: Int, responseBody: String): String {
        return try {
            val errObj = JSONObject(responseBody).optJSONObject("error")
            val msg = errObj?.optString("message", "") ?: ""

            when {
                msg.contains("leaked", ignoreCase = true) ->
                    "⚠️ API Key này đã bị Google khóa do bị lộ công khai. Vui lòng tạo key mới miễn phí tại aistudio.google.com và dán vào app."
                msg.contains("API_KEY_INVALID", ignoreCase = true) || msg.contains("API key not valid", ignoreCase = true) ->
                    "❌ API Key không hợp lệ. Vui lòng kiểm tra lại mã key."
                msg.contains("quota", ignoreCase = true) || statusCode == 429 ->
                    "⏳ API Key tạm thời hết hạn mức (Quota). Vui lòng đổi key khác hoặc thử lại sau vài phút."
                msg.isNotBlank() -> "Lỗi API ($statusCode): $msg"
                else -> "Lỗi máy chủ Google ($statusCode)"
            }
        } catch (e: Exception) {
            "Lỗi kết nối máy chủ Google ($statusCode)"
        }
    }
}
