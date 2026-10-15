package com.antigravity.redmicompanion.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationHelper {

    private const val MAPS_CHANNEL_ID = "redmi_maps_nav_channel"
    private const val GEMINI_CHANNEL_ID = "redmi_gemini_channel"
    private const val MAPS_NOTIF_ID = 1001
    private const val GEMINI_NOTIF_ID = 2002

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Kênh thông báo Google Maps: Rung ngắn, độ ưu tiên cao nhất
            val mapsChannel = NotificationChannel(
                MAPS_CHANNEL_ID,
                "Chỉ đường Google Maps cho Redmi Watch",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Chuyển tiếp chỉ đường từ Google Maps sang Redmi Watch 5"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 150, 300) // Rung 2 nhịp dứt khoát
                lightColor = Color.BLUE
                enableLights(true)
            }

            // Kênh thông báo Gemini AI: Rung 1 nhịp dài báo có câu trả lời
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

            manager.createNotificationChannel(mapsChannel)
            manager.createNotificationChannel(geminiChannel)
        }
    }

    /**
     * Gửi thông báo chỉ đường tới đồng hồ (tối ưu hiển thị màn hình nhỏ)
     */
    fun sendMapsNotification(context: Context, arrowEmoji: String, title: String, distanceInfo: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val fullTitle = "$arrowEmoji $distanceInfo"
        val fullContent = title

        val builder = NotificationCompat.Builder(context, MAPS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(fullTitle)
            .setContentText(fullContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$fullTitle\n$fullContent"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setVibrate(longArrayOf(0, 250, 100, 250))
            .setAutoCancel(true)
            .setOngoing(false)

        manager.notify(MAPS_NOTIF_ID, builder.build())
    }

    /**
     * Gửi câu trả lời của Gemini AI tới màn hình đồng hồ
     */
    fun sendGeminiNotification(context: Context, question: String, answer: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val builder = NotificationCompat.Builder(context, GEMINI_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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
