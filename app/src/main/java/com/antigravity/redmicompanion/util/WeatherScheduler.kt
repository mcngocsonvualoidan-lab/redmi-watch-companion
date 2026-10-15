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
    const val KEY_WEATHER_TIME_SLOTS = "weather_time_slots"
    const val KEY_WEATHER_LOCATION = "weather_location"

    const val DEFAULT_LOCATION = "TP. Hồ Chí Minh"
    val DEFAULT_TIME_SLOTS = listOf("07:00")

    fun getTimeSlots(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_WEATHER_TIME_SLOTS, null)
        val slots = if (raw.isNullOrBlank()) {
            DEFAULT_TIME_SLOTS
        } else {
            raw.split(",").map { it.trim() }.filter { it.matches(Regex("^\\d{1,2}:\\d{2}$")) }
        }

        // Sắp xếp các khung giờ theo thứ tự thời gian tăng dần trong ngày
        return slots.distinct().sortedBy { timeStrToMinutes(it) }.ifEmpty { DEFAULT_TIME_SLOTS }
    }

    fun saveTimeSlots(context: Context, slots: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val sorted = slots.distinct().sortedBy { timeStrToMinutes(it) }
        prefs.edit().putString(KEY_WEATHER_TIME_SLOTS, sorted.joinToString(",")).apply()
    }

    fun addTimeSlot(context: Context, timeStr: String): Boolean {
        val current = getTimeSlots(context).toMutableList()
        val formatted = formatToStandardTime(timeStr) ?: return false
        if (!current.contains(formatted)) {
            current.add(formatted)
            saveTimeSlots(context, current)
            scheduleDailyWeather(context)
            return true
        }
        return false
    }

    fun removeTimeSlot(context: Context, timeStr: String): Boolean {
        val current = getTimeSlots(context).toMutableList()
        if (current.remove(timeStr)) {
            saveTimeSlots(context, current)
            scheduleDailyWeather(context)
            return true
        }
        return false
    }

    fun scheduleDailyWeather(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_WEATHER_ENABLED, true)
        if (!isEnabled) {
            Log.d(TAG, "Weather notifications disabled.")
            cancelDailyWeather(context)
            return
        }

        val slots = getTimeSlots(context)
        if (slots.isEmpty()) {
            cancelDailyWeather(context)
            return
        }

        val nextCalendar = findNextAlarmTime(slots)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

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
                    nextCalendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    nextCalendar.timeInMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled next weather alarm at: ${nextCalendar.time}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule next weather alarm: ${e.message}")
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
        Log.d(TAG, "Cancelled weather alarms")
    }

    fun getNextScheduledSummary(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean(KEY_WEATHER_ENABLED, true)
        if (!isEnabled) return "Đang tắt tự động báo thời tiết"

        val slots = getTimeSlots(context)
        if (slots.isEmpty()) return "Chưa có khung giờ báo nào"

        val nextCal = findNextAlarmTime(slots)
        val now = Calendar.getInstance()
        val isToday = nextCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        val dayLabel = if (isToday) "hôm nay" else "ngày mai"
        val timeLabel = String.format("%02d:%02d", nextCal.get(Calendar.HOUR_OF_DAY), nextCal.get(Calendar.MINUTE))

        return "⏳ Lần báo tiếp theo: $timeLabel ($dayLabel)"
    }

    private fun findNextAlarmTime(slots: List<String>): Calendar {
        val now = Calendar.getInstance()
        val nowMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        // Tìm khung giờ gần nhất còn lại trong ngày hôm nay
        for (slot in slots) {
            val slotMin = timeStrToMinutes(slot)
            if (slotMin > nowMinutes) {
                val parts = slot.split(":")
                return Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, parts[0].toInt())
                    set(Calendar.MINUTE, parts[1].toInt())
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            }
        }

        // Nếu tất cả khung giờ hôm nay đã qua, lấy khung giờ sớm nhất của ngày mai
        val firstSlot = slots.first()
        val parts = firstSlot.split(":")
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            set(Calendar.MINUTE, parts[1].toInt())
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun timeStrToMinutes(timeStr: String): Int {
        val parts = timeStr.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    private fun formatToStandardTime(timeStr: String): String? {
        val parts = timeStr.split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return String.format("%02d:%02d", h, m)
    }
}
