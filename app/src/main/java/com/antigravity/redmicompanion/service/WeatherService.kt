package com.antigravity.redmicompanion.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

data class RealWeatherData(
    val locationName: String,
    val currentTemp: Double,
    val apparentTemp: Double,
    val minTemp: Double,
    val maxTemp: Double,
    val rainProbability: Int,
    val weatherCode: Int,
    val conditionDescription: String,
    val icon: String
)

class WeatherService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getLiveWeather(location: String): Result<RealWeatherData> = withContext(Dispatchers.IO) {
        try {
            val trimmedLoc = location.trim().ifEmpty { "TP. Hồ Chí Minh" }
            val (lat, lon, resolvedName) = geocodeLocation(trimmedLoc)

            val url = "https://api.open-meteo.com/v1/forecast?" +
                    "latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                    "&timezone=auto"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (!response.isSuccessful || body.isBlank()) {
                return@withContext Result.failure(Exception("Không lấy được dữ liệu thời tiết (${response.code})"))
            }

            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")

            val currentTemp = current.optDouble("temperature_2m", 28.0)
            val apparentTemp = current.optDouble("apparent_temperature", currentTemp)
            val weatherCode = current.optInt("weather_code", daily.optJSONArray("weather_code")?.optInt(0) ?: 0)

            val maxTempArray = daily.optJSONArray("temperature_2m_max")
            val minTempArray = daily.optJSONArray("temperature_2m_min")
            val rainArray = daily.optJSONArray("precipitation_probability_max")

            val maxTemp = maxTempArray?.optDouble(0) ?: (currentTemp + 4.0)
            val minTemp = minTempArray?.optDouble(0) ?: (currentTemp - 4.0)
            val rainProbability = rainArray?.optInt(0) ?: 20

            val (conditionDesc, icon) = mapWmoCode(weatherCode, rainProbability)

            Result.success(
                RealWeatherData(
                    locationName = resolvedName,
                    currentTemp = currentTemp,
                    apparentTemp = apparentTemp,
                    minTemp = minTemp,
                    maxTemp = maxTemp,
                    rainProbability = rainProbability,
                    weatherCode = weatherCode,
                    conditionDescription = conditionDesc,
                    icon = icon
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun geocodeLocation(location: String): Triple<Double, Double, String> {
        val lower = location.lowercase()
        // Các địa danh lớn tại Việt Nam được định tuyến nhanh và chuẩn xác
        when {
            lower.contains("hồ chí minh") || lower.contains("hcm") || lower.contains("sài gòn") ->
                return Triple(10.823, 106.63, "TP.HCM")
            lower.contains("hà nội") || lower.contains("ha noi") || lower.contains("hn") ->
                return Triple(21.028, 105.84, "Hà Nội")
            lower.contains("đà nẵng") || lower.contains("da nang") ->
                return Triple(16.054, 108.202, "Đà Nẵng")
            lower.contains("hải phòng") || lower.contains("hai phong") ->
                return Triple(20.844, 106.688, "Hải Phòng")
            lower.contains("cần thơ") || lower.contains("can tho") ->
                return Triple(10.045, 105.746, "Cần Thơ")
            lower.contains("nha trang") ->
                return Triple(12.238, 109.196, "Nha Trang")
            lower.contains("đà lạt") || lower.contains("da lat") ->
                return Triple(11.940, 108.458, "Đà Lạt")
            lower.contains("huế") || lower.contains("hue") ->
                return Triple(16.463, 107.590, "Huế")
            lower.contains("vũng tàu") || lower.contains("vung tau") ->
                return Triple(10.346, 107.084, "Vũng Tàu")
            lower.contains("quy nhơn") || lower.contains("quy nhon") ->
                return Triple(13.782, 109.219, "Quy Nhơn")
        }

        // Tự động tìm kiếm tọa độ qua Open-Meteo Geocoding cho bất kỳ tỉnh/thành nào khác
        try {
            val encoded = URLEncoder.encode(location, "UTF-8")
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=vi&format=json"
            val req = Request.Builder().url(geoUrl).build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (resp.isSuccessful && body.isNotBlank()) {
                val json = JSONObject(body)
                val results = json.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val first = results.getJSONObject(0)
                    val lat = first.getDouble("latitude")
                    val lon = first.getDouble("longitude")
                    val name = first.optString("name", location)
                    return Triple(lat, lon, name)
                }
            }
        } catch (_: Exception) {}

        // Fallback mặc định
        return Triple(10.823, 106.63, location)
    }

    private fun mapWmoCode(code: Int, rainProb: Int): Pair<String, String> {
        return when (code) {
            0 -> Pair("Trời quang, nắng đẹp", "☀️")
            1 -> Pair("Trời ít mây, có nắng", "🌤️")
            2 -> Pair("Mây rải rác, nắng nhẹ", "⛅")
            3 -> Pair("Trời nhiều mây, râm mát", "☁️")
            45, 48 -> Pair("Có sương mù mờ ảo", "🌫️")
            51, 53, 55 -> Pair("Mưa phùn bay nhẹ", "🌦️")
            61, 63 -> Pair("Có mưa rải rác", "🌧️")
            65 -> Pair("Mưa to diện rộng", "🌧️")
            80, 81 -> Pair("Mưa rào thoáng qua", "🌦️")
            82 -> Pair("Mưa rào nặng hạt", "🌧️")
            95, 96, 99 -> Pair("Có dông sét, mưa rào", "⛈️")
            else -> {
                if (rainProb >= 60) Pair("Khả năng mưa cao", "🌧️")
                else Pair("Thời tiết ráo ráo", "🌤️")
            }
        }
    }

    suspend fun generateWeatherSummary(
        location: String,
        geminiApiKey: String,
        geminiService: GeminiService
    ): String {
        val weatherRes = getLiveWeather(location)
        val data = weatherRes.getOrNull()

        if (data != null) {
            // Nếu có Gemini API Key hợp lệ, dùng Gemini để sinh câu thoại sinh động hơn dựa trên dữ liệu thực tế
            if (geminiApiKey.isNotBlank()) {
                val prompt = "Dữ liệu khí tượng thực tế tại ${data.locationName} hôm nay: " +
                        "Nhiệt độ hiện tại ${data.currentTemp.roundToInt()}°C (cảm giác ${data.apparentTemp.roundToInt()}°C), " +
                        "Thấp nhất ${data.minTemp.roundToInt()}°C, Cao nhất ${data.maxTemp.roundToInt()}°C, " +
                        "Tình trạng: ${data.conditionDescription}, Xác suất mưa: ${data.rainProbability}%. " +
                        "Hãy viết một bản tin tóm tắt cực ngắn gọn (dưới 28 từ, 1-2 câu) cho màn hình đồng hồ thông minh đeo tay, bắt đầu bằng icon thời tiết và đưa ra lời khuyên thực tế (áo mưa, chống nắng hoặc áo ấm)."
                val aiResult = geminiService.askGemini(geminiApiKey, prompt)
                if (aiResult.isSuccess) {
                    val aiText = aiResult.getOrNull()
                    if (!aiText.isNullOrBlank()) {
                        return aiText
                    }
                }
            }

            // Fallback trực tiếp từ dữ liệu thời tiết thực tế (100% chính xác, không trùng lặp)
            val advice = when {
                data.rainProbability >= 70 -> "☔ Xác suất mưa ${data.rainProbability}%, nhớ mang theo áo mưa!"
                data.rainProbability >= 40 -> "🌦️ Có thể mưa rào (${data.rainProbability}%), nên thủ sẵn ô/áo mưa."
                data.maxTemp >= 35 -> "☀️ Nắng nóng đỉnh điểm ${data.maxTemp.roundToInt()}°C, chú ý uống nhiều nước!"
                data.minTemp <= 20 -> "🧥 Trời se lạnh (${data.minTemp.roundToInt()}°C), nên mang áo ấm nhẹ."
                else -> "🌤️ Thời tiết thuận lợi, ngày ráo ráo dễ chịu."
            }

            return "${data.icon} ${data.locationName}: ${data.minTemp.roundToInt()}°C - ${data.maxTemp.roundToInt()}°C (Hiện tại ${data.currentTemp.roundToInt()}°C). ${data.conditionDescription}. $advice"
        }

        // Trường hợp không có mạng lấy thời tiết
        return "🌤️ $location: Ngày có nắng gián đoạn, chiều tối có thể mưa rào nhẹ rải rác."
    }
}
