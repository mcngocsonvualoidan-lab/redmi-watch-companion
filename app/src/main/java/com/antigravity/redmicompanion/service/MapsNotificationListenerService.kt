package com.antigravity.redmicompanion.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.antigravity.redmicompanion.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MapsNotificationListenerService : NotificationListenerService() {

    private val TAG = "CompanionListener"
    private val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"
    private val ZALO_PACKAGE = "com.zing.zalo"
    private val MESSENGER_PACKAGE = "com.facebook.orca"

    private var lastInstruction: String = ""
    private var lastDistance: String = ""
    private var isCallRinging: Boolean = false
    private var callJob: Job? = null

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

            val arrowEmoji = detectTurnArrow(title + " " + text)
            val distanceInfo = if (subText.isNotBlank()) subText else "Google Maps"
            val instruction = if (title.isNotBlank()) title else text

            if (instruction == lastInstruction && distanceInfo == lastDistance) {
                return
            }

            lastInstruction = instruction
            lastDistance = distanceInfo

            Log.d(TAG, "Navigating: $arrowEmoji $distanceInfo - $instruction")

            NotificationHelper.sendMapsNotification(
                context = applicationContext,
                arrowEmoji = arrowEmoji,
                title = instruction,
                distanceInfo = distanceInfo
            )
            return
        }

        // ==========================================
        // 2. XỬ LÝ CUỘC GỌI ZALO & MESSENGER (Hiện tên người gọi)
        // ==========================================
        if (pkg == ZALO_PACKAGE || pkg == MESSENGER_PACKAGE) {
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val category = notification.category
            val actions = notification.actions

            // Nhận diện cuộc gọi qua nút bấm "Trả lời" / "Từ chối" hoặc category cuộc gọi
            val hasCallAction = actions != null && actions.any { action ->
                val at = action.title?.toString()?.lowercase() ?: ""
                at.contains("từ chối") || at.contains("trả lời") ||
                at.contains("decline") || at.contains("reject") ||
                at.contains("answer")
            }

            val isCall = hasCallAction ||
                    category == Notification.CATEGORY_CALL ||
                    text.contains("cuộc gọi", ignoreCase = true) ||
                    title.contains("cuộc gọi", ignoreCase = true) ||
                    text.contains("đang gọi", ignoreCase = true) ||
                    title.contains("đang gọi", ignoreCase = true) ||
                    text.contains("calling", ignoreCase = true) ||
                    text.contains("incoming", ignoreCase = true)

            if (isCall) {
                val appName = if (pkg == ZALO_PACKAGE) "Zalo" else "Messenger"
                var caller = title
                if (title.equals("Zalo", ignoreCase = true) ||
                    title.equals("Messenger", ignoreCase = true) ||
                    title.contains("cuộc gọi", ignoreCase = true) ||
                    title.isBlank()) {
                    caller = text
                }
                if (caller.contains("từ ", ignoreCase = true)) {
                    caller = caller.substringAfter("từ ").trim()
                }
                if (caller.isBlank()) {
                    caller = if (title.isNotBlank()) title else "Có người đang gọi..."
                }

                if (!isCallRinging) {
                    isCallRinging = true
                    Log.d(TAG, "$appName call ringing from: $caller")
                    startRingingLoop(appName, caller)
                }
            }
        }
    }

    private fun startRingingLoop(appName: String, caller: String) {
        callJob?.cancel()
        callJob = CoroutineScope(Dispatchers.Default).launch {
            while (isCallRinging) {
                NotificationHelper.sendVoipCallNotification(
                    context = applicationContext,
                    appName = appName,
                    callerName = caller,
                    callType = "Đang đổ chuông..."
                )
                // Lặp lại mỗi 3.5 giây để đồng hồ rung liên tục theo chuông điện thoại
                delay(3500)
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
            isCallRinging = false
            callJob?.cancel()
            callJob = null
            NotificationHelper.dismissVoipCallNotification(applicationContext)
        }
    }
}
