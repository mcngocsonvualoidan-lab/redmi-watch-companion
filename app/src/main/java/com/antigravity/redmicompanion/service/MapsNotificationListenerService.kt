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
        // 2. XỬ LÝ CUỘC GỌI ZALO & MESSENGER (Chỉ bắt CUỘC GỌI, bỏ qua TIN NHẮN)
        // ==========================================
        if (pkg == ZALO_PACKAGE || pkg == MESSENGER_PACKAGE) {
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            val category = notification.category
            val actions = notification.actions

            // 1. Kiểm tra nếu là thông báo tin nhắn thông thường -> BỎ QUA NGAY
            // Tin nhắn thường có category là CATEGORY_MESSAGE hoặc có nút bấm "Trả lời" kèm ô nhập chữ (RemoteInput)
            val isTextMessage = category == Notification.CATEGORY_MESSAGE ||
                    category == "msg" ||
                    category == Notification.CATEGORY_SOCIAL ||
                    extras.containsKey("android.messagingStyleUser") ||
                    extras.containsKey(Notification.EXTRA_MESSAGES) ||
                    (actions != null && actions.any { it.remoteInputs != null && it.remoteInputs.isNotEmpty() })

            if (isTextMessage) {
                // Đây là tin nhắn văn bản thông thường, không can thiệp để Mi Fitness tự báo 1 nhịp chuẩn
                return
            }

            // 2. Nhận diện cuộc gọi VoIP thực thụ:
            // - Có category là CATEGORY_CALL
            // - Hoặc có nút bấm Từ chối / Nghe / Trả lời (KHÔNG CÓ ô gõ phím RemoteInput)
            // - Hoặc tiêu đề / nội dung chứa từ khóa cuộc gọi đến
            val hasCallCategory = category == Notification.CATEGORY_CALL

            val hasCallAction = actions != null && actions.any { action ->
                val hasRemoteInput = action.remoteInputs != null && action.remoteInputs.isNotEmpty()
                if (hasRemoteInput) return@any false
                val at = action.title?.toString()?.lowercase() ?: ""
                at.contains("từ chối") || at.contains("nghe") || at.contains("chấp nhận") ||
                at.contains("decline") || at.contains("reject") || at.contains("answer") ||
                at == "trả lời"
            }

            val hasCallKeyword = (
                title.contains("cuộc gọi đến", ignoreCase = true) ||
                text.contains("cuộc gọi đến", ignoreCase = true) ||
                title.contains("cuộc gọi thoại", ignoreCase = true) ||
                text.contains("cuộc gọi thoại", ignoreCase = true) ||
                title.contains("cuộc gọi video", ignoreCase = true) ||
                text.contains("cuộc gọi video", ignoreCase = true) ||
                title.contains("đang gọi cho bạn", ignoreCase = true) ||
                text.contains("đang gọi cho bạn", ignoreCase = true) ||
                title.contains("incoming call", ignoreCase = true) ||
                text.contains("incoming call", ignoreCase = true)
            )

            val isCall = hasCallCategory || (hasCallAction && hasCallKeyword) || (hasCallAction && actions != null && actions.size >= 2)

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
            var cycle = 0
            val maxCycles = 15 // Tự động ngắt sau ~33 giây nếu không nhấc máy để tránh rung vô tận
            while (isCallRinging && cycle < maxCycles) {
                NotificationHelper.sendVoipCallNotification(
                    context = applicationContext,
                    appName = appName,
                    callerName = caller,
                    callType = "Đang đổ chuông... (${cycle * 2}s)",
                    ringCycle = cycle
                )
                cycle++
                // Lặp lại mỗi 2.2 giây để đồng hồ Redmi Watch rung liên tục không ngắt quãng
                delay(2200)
            }
            // Tự động kết thúc nếu quá thời gian đổ chuông
            isCallRinging = false
            NotificationHelper.dismissVoipCallNotification(applicationContext)
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
            val hasActiveCall = try {
                activeNotifications?.any { activeSbn ->
                    if (activeSbn.packageName != ZALO_PACKAGE && activeSbn.packageName != MESSENGER_PACKAGE) return@any false
                    val notif = activeSbn.notification
                    val actions = notif.actions
                    val isMsg = notif.category == Notification.CATEGORY_MESSAGE ||
                            (actions != null && actions.any { it.remoteInputs != null && it.remoteInputs.isNotEmpty() })
                    if (isMsg) return@any false

                    val isCallCat = notif.category == Notification.CATEGORY_CALL
                    val hasCallAct = actions != null && actions.any { act ->
                        val hasRemoteInput = act.remoteInputs != null && act.remoteInputs.isNotEmpty()
                        val at = act.title?.toString()?.lowercase() ?: ""
                        !hasRemoteInput && (at.contains("từ chối") || at.contains("nghe") || at.contains("decline"))
                    }
                    isCallCat || hasCallAct
                } ?: false
            } catch (e: Exception) {
                false
            }

            if (!hasActiveCall) {
                Log.d(TAG, "Call ended on $pkg, stopping ringing loop")
                isCallRinging = false
                callJob?.cancel()
                callJob = null
                NotificationHelper.dismissVoipCallNotification(applicationContext)
            }
        }
    }
}
