package com.antigravity.redmicompanion

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.antigravity.redmicompanion.databinding.ActivityMainBinding
import com.antigravity.redmicompanion.service.GeminiService
import com.antigravity.redmicompanion.util.NotificationHelper
import com.antigravity.redmicompanion.util.UpdateManager
import com.antigravity.redmicompanion.util.WeatherScheduler
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        // Không lưu API Key nhạy cảm trong mã nguồn
        const val DEFAULT_API_KEY = ""
    }

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

        binding.tvCurrentVersion.text = "Phiên bản ${UpdateManager.CURRENT_VERSION}"
        checkAppUpdate(silent = true)

        setupListeners()
        setupSuggestionChips()
        setupWeatherControls()

        // Tự động lưu API Key vào thiết bị khi người dùng nhập hoặc thay đổi
        binding.etApiKey.doAfterTextChanged {
            saveApiKey()
        }

        // Xử lý nếu được gọi từ thanh Quick Settings Tile
        if (intent.getBooleanExtra("EXTRA_START_VOICE", false)) {
            startVoiceInput()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun setupWeatherControls() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val isEnabled = prefs.getBoolean(WeatherScheduler.KEY_WEATHER_ENABLED, true)
        val location = prefs.getString(WeatherScheduler.KEY_WEATHER_LOCATION, WeatherScheduler.DEFAULT_LOCATION)
            ?: WeatherScheduler.DEFAULT_LOCATION

        binding.switchWeatherEnabled.isChecked = isEnabled
        binding.etWeatherLocation.setText(location)

        // Hiển thị danh sách các khung giờ báo và lịch báo tiếp theo
        renderWeatherTimeChips()
        updateNextWeatherSchedule()

        // Khởi động lịch báo hàng ngày
        if (isEnabled) {
            WeatherScheduler.scheduleDailyWeather(this)
        }

        // Bật / tắt thời tiết
        binding.switchWeatherEnabled.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(WeatherScheduler.KEY_WEATHER_ENABLED, isChecked).apply()
            if (isChecked) {
                WeatherScheduler.scheduleDailyWeather(this)
                Toast.makeText(this, "Đã bật tự động báo thời tiết hàng ngày", Toast.LENGTH_SHORT).show()
            } else {
                WeatherScheduler.cancelDailyWeather(this)
                Toast.makeText(this, "Đã tắt thông báo thời tiết hàng ngày", Toast.LENGTH_SHORT).show()
            }
            updateNextWeatherSchedule()
        }

        // Thêm khung giờ báo mới (Mở TimePickerDialog)
        binding.btnAddWeatherTime.setOnClickListener {
            val calendar = java.util.Calendar.getInstance()
            val currentH = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            val currentM = calendar.get(java.util.Calendar.MINUTE)

            TimePickerDialog(this, { _, selectedH, selectedM ->
                val formatted = String.format(java.util.Locale.getDefault(), "%02d:%02d", selectedH, selectedM)
                val added = WeatherScheduler.addTimeSlot(this, formatted)
                if (added) {
                    renderWeatherTimeChips()
                    updateNextWeatherSchedule()
                    Toast.makeText(this, "Đã thêm khung giờ báo: $formatted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Khung giờ $formatted đã có trong danh sách!", Toast.LENGTH_SHORT).show()
                }
            }, currentH, currentM, true).show()
        }

        // Lưu địa điểm khi người dùng gõ
        binding.etWeatherLocation.doAfterTextChanged { text ->
            val loc = text?.toString()?.trim() ?: WeatherScheduler.DEFAULT_LOCATION
            prefs.edit().putString(WeatherScheduler.KEY_WEATHER_LOCATION, loc).apply()
        }

        // Nút test gửi thử dự báo thời tiết hôm nay
        binding.btnTestWeather.setOnClickListener {
            val loc = binding.etWeatherLocation.text?.toString()?.trim() ?: WeatherScheduler.DEFAULT_LOCATION
            val inputKey = binding.etApiKey.text?.toString()?.trim() ?: ""

            Toast.makeText(this, "Đang tạo bản tin thời tiết $loc...", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                val prompt = "Tóm tắt dự báo thời tiết hôm nay tại $loc thật ngắn gọn cho màn hình đồng hồ đeo tay: Nhiệt độ thấp nhất - cao nhất, nắng hay mưa, lời khuyên cần mang áo mưa hay chống nắng (dưới 28 từ, bắt đầu bằng icon thời tiết)."
                val result = geminiService.askGemini(inputKey, prompt)

                val summary = result.getOrElse {
                    "Thời tiết $loc hôm nay: 28°C - 34°C, ngày nắng ráo, chiều tối có mưa rào thoáng qua. Nhớ mang theo áo mưa!"
                }

                // Cập nhật mockup đồng hồ trực quan
                binding.tvMockArrow.text = "🌤️"
                binding.tvMockRoad.text = "Thời Tiết $loc"
                binding.tvMockDistance.text = if (summary.length > 36) summary.take(36) + "..." else summary

                // Gửi thông báo sang Redmi Watch 5
                NotificationHelper.sendWeatherNotification(
                    context = this@MainActivity,
                    title = "🌤️ Thời Tiết Hôm Nay ($loc)",
                    summary = summary
                )
                Toast.makeText(this@MainActivity, "Đã gửi bản tin thời tiết lên Redmi Watch!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderWeatherTimeChips() {
        val container = binding.llWeatherTimeSlots
        // Xóa các chip cũ (giữ lại nút btnAddWeatherTime ở cuối)
        val childCount = container.childCount
        if (childCount > 1) {
            container.removeViews(0, childCount - 1)
        }

        val slots = WeatherScheduler.getTimeSlots(this)
        val inflater = layoutInflater

        slots.forEachIndexed { index, slotTime ->
            val chipView = inflater.inflate(R.layout.item_weather_time_chip, container, false)
            val tvChip = chipView.findViewById<android.widget.TextView>(R.id.tvChipTime)
            val btnRemove = chipView.findViewById<android.widget.ImageView>(R.id.btnRemoveChip)

            tvChip.text = "⏰ $slotTime"
            btnRemove.setOnClickListener {
                if (slots.size <= 1) {
                    Toast.makeText(this, "Cần giữ lại ít nhất 1 khung giờ báo!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                WeatherScheduler.removeTimeSlot(this, slotTime)
                renderWeatherTimeChips()
                updateNextWeatherSchedule()
                Toast.makeText(this, "Đã xóa khung giờ $slotTime", Toast.LENGTH_SHORT).show()
            }

            container.addView(chipView, index)
        }
    }

    private fun updateNextWeatherSchedule() {
        binding.tvNextWeatherSchedule.text = WeatherScheduler.getNextScheduledSummary(this)
    }

    private fun setupSuggestionChips() {
        binding.chipWeather.setOnClickListener {
            val prompt = "Thời tiết hôm nay thế nào?"
            binding.etPrompt.setText(prompt)
            saveApiKey()
            askGeminiAndNotifyWatch(prompt)
        }

        binding.chipWater.setOnClickListener {
            val prompt = "Nhắc nhở tôi uống nước và mẹo khỏe"
            binding.etPrompt.setText(prompt)
            saveApiKey()
            askGeminiAndNotifyWatch(prompt)
        }

        binding.chipRoute.setOnClickListener {
            val prompt = "Mẹo làm việc tập trung và hiệu quả hôm nay"
            binding.etPrompt.setText(prompt)
            saveApiKey()
            askGeminiAndNotifyWatch(prompt)
        }
    }

    private fun setupListeners() {
        // Nút kiểm tra cập nhật thủ công
        binding.btnCheckUpdate.setOnClickListener {
            checkAppUpdate(silent = false)
        }

        // Nút mở cài đặt cấp quyền Notification Listener
        binding.btnGrantPermission.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        // Nút mở thẳng trang Thông tin ứng dụng (App Info) để mở khóa "Cài đặt hạn chế" trên Pixel
        binding.btnOpenAppInfo.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
            startActivity(intent)
            Toast.makeText(
                this,
                "Chạm vào dấu 3 chấm (⋮) ở góc trên bên phải > Chọn 'Cho phép cài đặt bị hạn chế'",
                Toast.LENGTH_LONG
            ).show()
        }

        // Nút test bắn thông báo chỉ đường Maps mẫu sang đồng hồ
        binding.btnTestMapsNotify.setOnClickListener {
            // Cập nhật mockup hiển thị trực quan
            binding.tvMockArrow.text = "↰"
            binding.tvMockRoad.text = "Đ. Man Thiện"
            binding.tvMockDistance.text = "11 m • 1 phút • Google Maps"

            NotificationHelper.sendMapsNotification(
                context = this,
                arrowEmoji = "↰",
                title = "Man Thiện",
                distanceInfo = "11 m",
                timeRemaining = "1 phút"
            )
            Toast.makeText(this, "Đã gửi chỉ đường mẫu (↰ 11 m • Man Thiện)!", Toast.LENGTH_SHORT).show()
        }

        // Nút test cuộc gọi Zalo có tên người gọi lên đồng hồ
        binding.btnTestCallNotify.setOnClickListener {
            // Cập nhật mockup hiển thị trực quan
            binding.tvMockArrow.text = "📞"
            binding.tvMockRoad.text = "Nguyễn Văn A"
            binding.tvMockDistance.text = "Cuộc gọi thoại đến • Zalo"

            Toast.makeText(this, "Đang phát chuông rung liên tục 8 giây lên đồng hồ...", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch {
                for (cycle in 0 until 4) {
                    NotificationHelper.sendVoipCallNotification(
                        context = this@MainActivity,
                        appName = "Zalo",
                        callerName = "Nguyễn Văn A",
                        callType = "Cuộc gọi thoại đến... (${cycle * 2}s)",
                        ringCycle = cycle
                    )
                    kotlinx.coroutines.delay(2200)
                }
                NotificationHelper.dismissVoipCallNotification(this@MainActivity)
            }
        }

        // Mở Google AI Studio để lấy API Key miễn phí
        binding.btnGetFreeApiKey.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
            startActivity(intent)
        }

        // Kiểm tra nhanh tính hợp lệ của API Key
        binding.btnTestApiKey.setOnClickListener {
            val key = binding.etApiKey.text?.toString()?.trim() ?: ""
            if (key.isBlank()) {
                binding.tvApiKeyStatus.text = "⚠️ Vui lòng nhập API Key vào ô trên trước khi kiểm tra!"
                binding.tvApiKeyStatus.setTextColor(Color.parseColor("#DC2626"))
                return@setOnClickListener
            }

            binding.tvApiKeyStatus.text = "⏳ Đang kiểm tra API Key với máy chủ Google AI..."
            binding.tvApiKeyStatus.setTextColor(Color.parseColor("#7E57C2"))
            binding.btnTestApiKey.isEnabled = false

            lifecycleScope.launch {
                val res = geminiService.verifyApiKey(key)
                binding.btnTestApiKey.isEnabled = true
                res.onSuccess { msg ->
                    binding.tvApiKeyStatus.text = msg
                    binding.tvApiKeyStatus.setTextColor(Color.parseColor("#059669"))
                    saveApiKey()
                }.onFailure { err ->
                    binding.tvApiKeyStatus.text = "❌ ${err.message}"
                    binding.tvApiKeyStatus.setTextColor(Color.parseColor("#DC2626"))
                }
            }
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
        val inputKey = binding.etApiKey.text?.toString()?.trim() ?: ""
        if (inputKey.isBlank()) {
            binding.tvGeminiStatus.text = "❌ Chưa có API Key. Bấm '🔑 Lấy API Key miễn phí' để tạo key và dán vào ô trên."
            Toast.makeText(this, "Vui lòng nhập API Key để dùng trợ lý Gemini!", Toast.LENGTH_LONG).show()
            return
        }

        binding.tvGeminiStatus.text = "⏳ Đang hỏi Gemini AI..."
        binding.btnAskGemini.isEnabled = false

        lifecycleScope.launch {
            val result = geminiService.askGemini(inputKey, prompt)
            binding.btnAskGemini.isEnabled = true

            result.onSuccess { answer ->
                binding.tvGeminiStatus.text = "✅ Đã gửi phản hồi lên đồng hồ:\n$answer"

                // Cập nhật mockup trên màn hình
                binding.tvMockArrow.text = "✨"
                binding.tvMockRoad.text = "Gemini AI"
                binding.tvMockDistance.text = if (answer.length > 36) answer.take(36) + "..." else answer

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
            binding.tvPermissionStatus.text = "Đã kích hoạt đầy đủ quyền"
            binding.tvPermissionStatus.setTextColor(Color.parseColor("#059669"))
            binding.btnGrantPermission.isEnabled = false
            binding.btnGrantPermission.text = "✓ Quyền hệ thống đã sẵn sàng"
            binding.btnOpenAppInfo.visibility = View.GONE
        } else {
            binding.tvPermissionStatus.text = "Chưa cấp quyền lắng nghe thông báo"
            binding.tvPermissionStatus.setTextColor(Color.parseColor("#DC2626"))
            binding.btnGrantPermission.isEnabled = true
            binding.btnGrantPermission.text = "Mở cài đặt cấp quyền thông báo"
            binding.btnOpenAppInfo.visibility = View.VISIBLE
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val packages = NotificationManagerCompat.getEnabledListenerPackages(this)
        return packages.contains(packageName)
    }

    private fun loadSavedApiKey() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedKey = prefs.getString(KEY_API_KEY, null)
        // Nếu là key cũ từng bị leak thì bỏ qua
        val effectiveKey = if (!savedKey.isNullOrBlank() && savedKey != "AIzaSyCAz4kmmUrYKqRCtzynOTICNPkutUkxSHE") {
            savedKey
        } else {
            ""
        }
        binding.etApiKey.setText(effectiveKey)
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

    private fun checkAppUpdate(silent: Boolean) {
        if (!silent) {
            Toast.makeText(this, "Đang kiểm tra bản cập nhật...", Toast.LENGTH_SHORT).show()
        }
        lifecycleScope.launch {
            val result = UpdateManager.checkUpdate()
            result.onSuccess { releaseInfo ->
                if (releaseInfo != null) {
                    UpdateManager.promptUpdate(this@MainActivity, releaseInfo)
                } else if (!silent) {
                    Toast.makeText(this@MainActivity, "Bạn đang dùng phiên bản mới nhất (${UpdateManager.CURRENT_VERSION})", Toast.LENGTH_SHORT).show()
                }
            }.onFailure { error ->
                if (!silent) {
                    Toast.makeText(this@MainActivity, "Không thể kiểm tra cập nhật: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
