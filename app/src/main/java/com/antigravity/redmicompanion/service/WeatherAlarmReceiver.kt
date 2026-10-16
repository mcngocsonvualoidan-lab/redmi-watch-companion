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
        val apiKey = if (!savedKey.isNullOrBlank() && savedKey != "AIzaSyCAz4kmmUrYKqRCtzynOTICNPkutUkxSHE") savedKey else ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val weatherService = WeatherService()
                val gemini = GeminiService()
                val summary = weatherService.generateWeatherSummary(location, apiKey, gemini)

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
