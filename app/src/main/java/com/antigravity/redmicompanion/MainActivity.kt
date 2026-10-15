package com.antigravity.redmicompanion

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.antigravity.redmicompanion.databinding.ActivityMainBinding
import com.antigravity.redmicompanion.service.GeminiService
import com.antigravity.redmicompanion.util.NotificationHelper
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val geminiService = GeminiService()

    private val PREFS_NAME = "redmi_companion_prefs"
    private val KEY_API_KEY = "gemini_api_key"

    // Bộ nhận diện giọng nói tiếng Việt
    private val speechRecognizerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenTextList = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = spokenTextList?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                binding.etPrompt.setText(spokenText)
                askGeminiAndNotifyWatch(spokenText)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        NotificationHelper.createNotificationChannels(this)
        loadSavedApiKey()
        requestAppPermissions()

        setupListeners()

        // Xử lý nếu được gọi từ thanh Quick Settings Tile
        if (intent.getBooleanExtra("EXTRA_START_VOICE", false)) {
            startVoiceInput()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun setupListeners() {
        // Nút mở cài đặt cấp quyền Notification Listener
        binding.btnGrantPermission.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        // Nút test bắn thông báo chỉ đường Maps mẫu sang đồng hồ
        binding.btnTestMapsNotify.setOnClickListener {
            NotificationHelper.sendMapsNotification(
                context = this,
                arrowEmoji = "➡️",
                title = "Sau 200m rẽ phải vào đường Nguyễn Huệ",
                distanceInfo = "200m • 5 phút"
            )
            Toast.makeText(this, "Đã gửi thông báo chỉ đường mẫu!", Toast.LENGTH_SHORT).show()
        }

        // Nút nhận diện giọng nói tiếng Việt
        binding.btnVoiceInput.setOnClickListener {
            startVoiceInput()
        }

        // Nút hỏi Gemini bằng text
        binding.btnAskGemini.setOnClickListener {
            val prompt = binding.etPrompt.text?.toString()?.trim() ?: ""
            if (prompt.isBlank()) {
                Toast.makeText(this, "Vui lòng nhập câu hỏi!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveApiKey()
            askGeminiAndNotifyWatch(prompt)
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale("vi", "VN"))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Đang lắng nghe câu hỏi của bạn...")
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Thiết bị không hỗ trợ nhận diện giọng nói", Toast.LENGTH_SHORT).show()
        }
    }

    private fun askGeminiAndNotifyWatch(prompt: String) {
        val apiKey = binding.etApiKey.text?.toString()?.trim() ?: ""
        if (apiKey.isBlank()) {
            Toast.makeText(this, "Vui lòng điền Gemini API Key!", Toast.LENGTH_LONG).show()
            return
        }

        binding.tvGeminiStatus.text = "⏳ Đang hỏi Gemini AI..."
        binding.btnAskGemini.isEnabled = false

        lifecycleScope.launch {
            val result = geminiService.askGemini(apiKey, prompt)
            binding.btnAskGemini.isEnabled = true

            result.onSuccess { answer ->
                binding.tvGeminiStatus.text = "✅ Đã gửi phản hồi lên đồng hồ:\n$answer"
                // Đẩy thông báo lên màn hình Redmi Watch 5
                NotificationHelper.sendGeminiNotification(
                    context = this@MainActivity,
                    question = prompt,
                    answer = answer
                )
            }.onFailure { error ->
                binding.tvGeminiStatus.text = "❌ Thất bại: ${error.message}"
            }
        }
    }

    private fun updatePermissionStatus() {
        val isGranted = isNotificationListenerEnabled()
        if (isGranted) {
            binding.tvPermissionStatus.text = "✅ Đã cấp quyền lắng nghe thông báo"
            binding.tvPermissionStatus.setTextColor(Color.parseColor("#2E7D32"))
            binding.btnGrantPermission.isEnabled = false
            binding.btnGrantPermission.text = "Quyền đã sẵn sàng"
        } else {
            binding.tvPermissionStatus.text = "⚠️ Chưa cấp quyền lắng nghe thông báo"
            binding.tvPermissionStatus.setTextColor(Color.parseColor("#D32F2F"))
            binding.btnGrantPermission.isEnabled = true
            binding.btnGrantPermission.text = "Mở cài đặt cấp quyền thông báo"
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val packages = NotificationManagerCompat.getEnabledListenerPackages(this)
        return packages.contains(packageName)
    }

    private fun loadSavedApiKey() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedKey = prefs.getString(KEY_API_KEY, "")
        binding.etApiKey.setText(savedKey)
    }

    private fun saveApiKey() {
        val key = binding.etApiKey.text?.toString()?.trim() ?: ""
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_API_KEY, key).apply()
    }

    private fun requestAppPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
        }
    }
}
