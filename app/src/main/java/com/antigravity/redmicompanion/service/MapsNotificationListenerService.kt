package com.antigravity.redmicompanion.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.antigravity.redmicompanion.util.NotificationHelper

class MapsNotificationListenerService : NotificationListenerService() {

    private val TAG = "MapsListenerService"
    private val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

    private var lastInstruction: String = ""
    private var lastDistance: String = ""

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // Chỉ xử lý thông báo đến từ Google Maps
        if (sbn.packageName == GOOGLE_MAPS_PACKAGE) {
            val extras = sbn.notification.extras ?: return

            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

            // Bỏ qua nếu thông báo không chứa thông tin dẫn đường
            if (title.isBlank() && text.isBlank()) return

            // Nhận diện hướng di chuyển để thêm icon trực quan cho đồng hồ
            val arrowEmoji = detectTurnArrow(title + " " + text)

            // Khoảng cách và hướng rẽ
            val distanceInfo = if (subText.isNotBlank()) subText else "Google Maps"
            val instruction = if (title.isNotBlank()) title else text

            // Chống spam: Nếu chỉ đường giống hệt lần trước thì không đẩy lại
            if (instruction == lastInstruction && distanceInfo == lastDistance) {
                return
            }

            lastInstruction = instruction
            lastDistance = distanceInfo

            Log.d(TAG, "Navigating: $arrowEmoji | $distanceInfo | $instruction")

            // Gửi thông báo định dạng riêng cho Redmi Watch 5
            NotificationHelper.sendMapsNotification(
                context = applicationContext,
                arrowEmoji = arrowEmoji,
                title = instruction,
                distanceInfo = distanceInfo
            )
        }
    }

    private fun detectTurnArrow(content: String): String {
        val lower = content.lowercase()
        return when {
            lower.contains("chếch sang phải") || lower.contains("slight right") -> "↗️"
            lower.contains("chếch sang trái") || lower.contains("slight left") -> "↖️"
            lower.contains("rẽ phải") || lower.contains("quẹo phải") || lower.contains("turn right") -> "➡️"
            lower.contains("rẽ trái") || lower.contains("quẹo trái") || lower.contains("turn left") -> "⬅️"
            lower.contains("quay đầu") || lower.contains("u-turn") -> "🔄"
            lower.contains("đi thẳng") || lower.contains("tiếp tục") || lower.contains("straight") -> "⬆️"
            lower.contains("đến nơi") || lower.contains("arrived") -> "🏁"
            else -> "🧭"
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn?.packageName == GOOGLE_MAPS_PACKAGE) {
            lastInstruction = ""
            lastDistance = ""
        }
    }
}
