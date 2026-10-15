package com.antigravity.redmicompanion.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.antigravity.redmicompanion.service.WeatherAlarmReceiver
import java.util.Calendar

object WeatherScheduler {

    private const val TAG = "WeatherScheduler"
    private const val ALARM_REQUEST_CODE = 4004

    const val PREFS_NAME = "redmi_companion_prefs"
    const val KEY_WEATHER_ENABLED = "weather_enabled"
    const val KEY_WEATHER_HOUR = "weather_hour"
    const val KEY_WEATHER_MINUTE = "weather_minute"
    const val KEY_WEATHER_LOCATION = "weather_location"

    const val DEFAULT_LOCATION = "TP. Hồ Chí Minh"
    const val DEFAULT_HOUR = 7
    const val DEFAULT_MINUTE = 0

    fun scheduleDailyWeather(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_WEATHER_ENABLED, true)
        if (!isEnabled) {
            Log.d(TAG, "Weather auto notifications disabled.")
            cancelDailyWeather(context)
            return
        }

        val hour = prefs.getInt(KEY_WEATHER_HOUR, DEFAULT_HOUR)
        val minute = prefs.getInt(KEY_WEATHER_MINUTE, DEFAULT_MINUTE)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            // Nếu mốc giờ quy chuẩn hôm nay đã qua, tự động đặt cho sáng hôm sau
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(context, WeatherAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled daily weather at ${String.format("%02d:%02d", hour, minute)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule weather alarm: ${e.message}")
        }
    }

    fun cancelDailyWeather(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, WeatherAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Cancelled daily weather schedule")
    }
}
