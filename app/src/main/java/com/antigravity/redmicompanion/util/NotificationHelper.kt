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
    private const val CALL_CHANNEL_ID = "redmi_voip_call_channel"

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

            // 3. Kênh cuộc gọi Zalo / Messenger: Rung liên tục để biết có người gọi
            val callChannel = NotificationChannel(
                CALL_CHANNEL_ID,
                "Cuộc gọi Zalo / Messenger",
                NotificationManager.IMPORTANCE_MAX
            ).apply {
                description = "Hiển thị tên người gọi Zalo và Messenger lên Redmi Watch"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 500)
                lightColor = Color.GREEN
                enableLights(true)
            }

            manager.createNotificationChannel(mapsChannel)
            manager.createNotificationChannel(geminiChannel)
            manager.createNotificationChannel(callChannel)
        }
    }

    /**
     * Gửi chỉ đường Google Maps: Tối ưu hiển thị trực quan (Mũi tên + Khoảng cách + Tên đường)
     */
    fun sendMapsNotification(context: Context, arrowEmoji: String, title: String, distanceInfo: String, timeRemaining: String = "") {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Tiêu đề to rõ: "↰ 11 m • Man Thiện"
        val topTitle = "$arrowEmoji $distanceInfo"
        val roadAndEta = if (timeRemaining.isNotBlank()) "$title (Còn: $timeRemaining)" else title

        val builder = NotificationCompat.Builder(context, MAPS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
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
     * Bắn thông báo cuộc gọi Zalo / Messenger kèm tên người gọi lên đồng hồ
     */
    fun sendVoipCallNotification(context: Context, appName: String, callerName: String, callType: String) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val title = "📞 $appName: $callerName"
        val content = if (callType.isNotBlank()) callType else "Đang gọi đến..."

        val builder = NotificationCompat.Builder(context, CALL_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$content"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVibrate(longArrayOf(0, 600, 250, 600, 250, 600))
            .setOngoing(true)
            .setAutoCancel(false)

        manager.notify(VOIP_CALL_NOTIF_ID, builder.build())
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
