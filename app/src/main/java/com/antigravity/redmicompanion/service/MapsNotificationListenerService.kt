package com.antigravity.redmicompanion.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.antigravity.redmicompanion.util.NotificationHelper

class MapsNotificationListenerService : NotificationListenerService() {

    private val TAG = "CompanionListener"
    private val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
    private val ZALO_PACKAGE = "com.zing.zalo"
    private val MESSENGER_PACKAGE = "com.facebook.orca"

    private var lastInstruction: String = ""
    private var lastDistance: String = ""
    private var isCallRinging: Boolean = false

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName
        val notification = sbn.notification
        val extras = notification.extras ?: return

        // ==========================================
        // 1. XỬ LÝ CHỈ ĐƯỜNG GOOGLE MAPS
        // ==========================================
        if (pkg == GOOGLE_MAPS_PACKAGE) {
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString() ?: ""

            if (title.isBlank() && text.isBlank()) return

            // Nhận diện mũi tên chuẩn xác (↰, ↱, ⬆️, 🔄,...)
            val arrowEmoji = detectTurnArrow(title + " " + text)

            // Trích xuất khoảng cách và thời gian còn lại
            val distanceInfo = if (subText.isNotBlank()) subText else "Google Maps"
            val instruction = if (title.isNotBlank()) title else text

            // Chống spam thông báo trùng
            if (instruction == lastInstruction && distanceInfo == lastDistance) {
                return
            }

            lastInstruction = instruction
            lastDistance = distanceInfo

            Log.d(TAG, "Navigating: $arrowEmoji $distanceInfo - $instruction")

            // Gửi chỉ đường định dạng trực quan cho Redmi Watch
            NotificationHelper.sendMapsNotification(
                context = applicationContext,
                arrowEmoji = arrowEmoji,
                title = instruction,
                distanceInfo = distanceInfo
            )
            return
        }

        // ==========================================
        // 2. XỬ LÝ CUỘC GỌI ZALO ĐẾN (Hiện tên người gọi)
        // ==========================================
        if (pkg == ZALO_PACKAGE) {
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val category = notification.category

            val isZaloCall = category == Notification.CATEGORY_CALL ||
                    text.contains("cuộc gọi", ignoreCase = true) ||
                    text.contains("đang gọi", ignoreCase = true) ||
                    text.contains("incoming call", ignoreCase = true)

            if (isZaloCall && title.isNotBlank()) {
                isCallRinging = true
                Log.d(TAG, "Zalo Call from: $title")
                NotificationHelper.sendVoipCallNotification(
                    context = applicationContext,
                    appName = "Zalo",
                    callerName = title,
                    callType = text.ifBlank { "Cuộc gọi đến..." }
                )
            }
            return
        }

        // ==========================================
        // 3. XỬ LÝ CUỘC GỌI MESSENGER ĐẾN (Hiện tên người gọi)
        // ==========================================
        if (pkg == MESSENGER_PACKAGE) {
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val category = notification.category

            val isMessengerCall = category == Notification.CATEGORY_CALL ||
                    text.contains("cuộc gọi", ignoreCase = true) ||
                    text.contains("calling", ignoreCase = true) ||
                    text.contains("đang gọi", ignoreCase = true)

            if (isMessengerCall && title.isNotBlank()) {
                isCallRinging = true
                Log.d(TAG, "Messenger Call from: $title")
                NotificationHelper.sendVoipCallNotification(
                    context = applicationContext,
                    appName = "Messenger",
                    callerName = title,
                    callType = text.ifBlank { "Cuộc gọi thoại/video đến..." }
                )
            }
        }
    }

    private fun detectTurnArrow(content: String): String {
        val lower = content.lowercase()
        return when {
            lower.contains("chếch sang phải") || lower.contains("slight right") -> "↗️"
            lower.contains("chếch sang trái") || lower.contains("slight left") -> "↖️"
            lower.contains("rẽ phải") || lower.contains("quẹo phải") || lower.contains("turn right") -> "↱"
            lower.contains("rẽ trái") || lower.contains("quẹo trái") || lower.contains("turn left") -> "↰"
            lower.contains("quay đầu") || lower.contains("u-turn") -> "🔄"
            lower.contains("đi thẳng") || lower.contains("tiếp tục") || lower.contains("straight") -> "⬆️"
            lower.contains("vòng xuyến") || lower.contains("roundabout") -> "⭕"
            lower.contains("đến nơi") || lower.contains("arrived") -> "🏁"
            else -> "🧭"
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return

        val pkg = sbn.packageName
        if (pkg == GOOGLE_MAPS_PACKAGE) {
            lastInstruction = ""
            lastDistance = ""
        } else if (pkg == ZALO_PACKAGE || pkg == MESSENGER_PACKAGE) {
            if (isCallRinging) {
                isCallRinging = false
                NotificationHelper.dismissVoipCallNotification(applicationContext)
            }
        }
    }
}
