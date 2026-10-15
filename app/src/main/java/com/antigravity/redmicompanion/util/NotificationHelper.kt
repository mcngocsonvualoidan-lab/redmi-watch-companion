package com.antigravity.redmicompanion.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.antigravity.redmicompanion.R

object NotificationHelper {

    private const val TAG = "NotificationHelper"

    private const val MAPS_CHANNEL_ID = "redmi_maps_nav_channel"
    private const val GEMINI_CHANNEL_ID = "redmi_gemini_channel"
    private const val CALL_CHANNEL_ID = "redmi_voip_call_channel_v3"

    private const val MAPS_NOTIF_ID = 1001
    private const val GEMINI_NOTIF_ID = 2002
    private const val VOIP_CALL_NOTIF_ID = 3003

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 1. Kênh chỉ đường Google Maps: Rung nhịp kép, ưu tiên cao
            val mapsChannel = NotificationChannel(
                MAPS_CHANNEL_ID,
                "Chỉ đường Google Maps cho Redmi Watch",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Chuyển tiếp chỉ đường từ Google Maps sang Redmi Watch 5"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300)
                lightColor = Color.CYAN
                enableLights(true)
            }

            // 2. Kênh thông báo Gemini AI
            val geminiChannel = NotificationChannel(
                GEMINI_CHANNEL_ID,
                "Trợ lý AI Gemini cho Redmi Watch",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Kết quả phản hồi từ Gemini AI gửi đến đồng hồ"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 400)
                lightColor = Color.MAGENTA
                enableLights(true)
            }

            // 3. Kênh cuộc gọi Zalo / Messenger: Rung liên tục với IMPORTANCE_MAX
            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                "Cuộc gọi Zalo / Messenger Đến",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Hiển thị tên người gọi và phát xung rung liên tục sang Redmi Watch"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 250, 800)
                lightColor = Color.GREEN
                enableLights(true)
            }

            manager.createNotificationChannel(mapsChannel)
            manager.createNotificationChannel(geminiChannel)
            manager.createNotificationChannel(callChannel)
        }
    }

    /**
     * Gửi chỉ đường Google Maps sang đồng hồ
     */
    fun sendMapsNotification(
        context: Context,
        arrowEmoji: String,
        title: String,
        distanceInfo: String,
        timeRemaining: String = ""
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val logoBitmap = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        val topTitle = "$arrowEmoji $title"
        val roadAndEta = if (timeRemaining.isNotBlank()) {
            "$distanceInfo • $timeRemaining"
        } else {
            distanceInfo
        }

        val builder = NotificationCompat.Builder(context, MAPS_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(logoBitmap)
            .setContentTitle(topTitle)
            .setContentText(roadAndEta)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$topTitle\n$roadAndEta"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVibrate(longArrayOf(0, 250, 100, 250))
            .setAutoCancel(true)
            .setOngoing(false)

        manager.notify(MAPS_NOTIF_ID, builder.build())
    }

    /**
     * Bắn thông báo cuộc gọi Zalo / Messenger kèm tên người gọi và kích hoạt rung liên tục
     */
    fun sendVoipCallNotification(
        context: Context,
        appName: String,
        callerName: String,
        callType: String,
        ringCycle: Int = 0
    ) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Hủy notification cũ trước nếu là nhịp lặp lại để Mi Fitness và đồng hồ nhận diện là sự kiện MỚI và rung tiếp
        if (ringCycle > 0) {
            try {
                manager.cancel(VOIP_CALL_NOTIF_ID)
            } catch (e: Exception) {}
        }

        val ringIcons = listOf("📞", "📲", "🔔")
        val ringIcon = ringIcons[ringCycle % ringIcons.size]
        val title = "$ringIcon $appName: $callerName"
        val content = if (callType.isNotBlank()) callType else "Đang gọi đến..."
        val logoBitmap = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        val builder = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(logoBitmap)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$content"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVibrate(longArrayOf(0, 800, 250, 800))
            .setOnlyAlertOnce(false)
            .setOngoing(false)
            .setAutoCancel(true)

        manager.notify(VOIP_CALL_NOTIF_ID, builder.build())

        // Đồng thời rung trên thiết bị điện thoại
        triggerDeviceVibration(context)
    }

    private fun triggerDeviceVibration(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 200, 600), -1))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 600, 200, 600), -1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Device vibration error: ${e.message}")
        }
    }

    /**
     * Hủy thông báo cuộc gọi khi người gọi cúp máy hoặc bắt máy
     */
    fun dismissVoipCallNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(VOIP_CALL_NOTIF_ID)
    }

    /**
     * Gửi phản hồi của Gemini AI tới đồng hồ
     */
    fun sendGeminiNotification(context: Context, question: String, answer: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val logoBitmap = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)

        val builder = NotificationCompat.Builder(context, GEMINI_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setLargeIcon(logoBitmap)
            .setContentTitle("🤖 Gemini: $question")
            .setContentText(answer)
            .setStyle(NotificationCompat.BigTextStyle().bigText(answer))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVibrate(longArrayOf(0, 350))
            .setAutoCancel(true)

        manager.notify(GEMINI_NOTIF_ID, builder.build())
    }
}
