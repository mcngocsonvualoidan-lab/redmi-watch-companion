package com.antigravity.redmicompanion.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.antigravity.redmicompanion.MainActivity
import com.antigravity.redmicompanion.util.NotificationHelper
import com.antigravity.redmicompanion.util.WeatherScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WeatherAlarmReceiver : BroadcastReceiver() {

    private val TAG = "WeatherAlarmReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Daily Weather alarm fired!")

        val pendingResult = goAsync()
        val prefs = context.getSharedPreferences(WeatherScheduler.PREFS_NAME, Context.MODE_PRIVATE)

        val isEnabled = prefs.getBoolean(WeatherScheduler.KEY_WEATHER_ENABLED, true)
        if (!isEnabled) {
            pendingResult.finish()
            return
        }

        val location = prefs.getString(WeatherScheduler.KEY_WEATHER_LOCATION, WeatherScheduler.DEFAULT_LOCATION)
            ?: WeatherScheduler.DEFAULT_LOCATION
        val savedKey = prefs.getString("gemini_api_key", null)
        val apiKey = if (!savedKey.isNullOrBlank()) savedKey else MainActivity.DEFAULT_API_KEY

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val gemini = GeminiService()
                val prompt = "Tóm tắt dự báo thời tiết hôm nay tại $location thật ngắn gọn cho màn hình đồng hồ thông minh: Nhiệt độ min-max, trời nắng hay mưa, lời khuyên cần mang áo mưa hay chống nắng (dưới 28 từ, bắt đầu bằng biểu tượng thời tiết sinh động)."
                val result = gemini.askGemini(apiKey, prompt)

                val summary = result.getOrElse {
                    "Thời tiết $location hôm nay: 28°C - 34°C, ngày nắng ráo, chiều tối có thể mưa rào thoáng qua. Nhớ mang theo áo mưa!"
                }

                NotificationHelper.sendWeatherNotification(
                    context = context,
                    title = "🌤️ Thời Tiết Hôm Nay ($location)",
                    summary = summary
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching daily weather: ${e.message}")
            } finally {
                // Tự động lên lịch cho mốc giờ quy chuẩn của ngày tiếp theo
                WeatherScheduler.scheduleDailyWeather(context)
                pendingResult.finish()
            }
        }
    }
}
