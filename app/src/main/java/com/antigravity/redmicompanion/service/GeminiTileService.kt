package com.antigravity.redmicompanion.service

import android.content.Intent
import android.service.quicksettings.TileService
import com.antigravity.redmicompanion.MainActivity

class GeminiTileService : TileService() {

    override fun onClick() {
        super.onClick()
        // Khi người dùng chạm vào icon "Hỏi Gemini" trên thanh cài đặt nhanh của điện thoại
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_START_VOICE", true)
        }
        startActivityAndCollapse(intent)
    }
}
