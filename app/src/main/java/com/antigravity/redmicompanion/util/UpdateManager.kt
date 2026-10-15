package com.antigravity.redmicompanion.util

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

data class AppReleaseInfo(
    val tagName: String,
    val releaseNotes: String,
    val downloadUrl: String
)

object UpdateManager {

    private const val TAG = "UpdateManager"
    private const val GITHUB_REPO = "mcngocsonvualoidan-lab/redmi-watch-companion"
    private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"

    // Phiên bản hiện tại của app
    const val CURRENT_VERSION = "v1.6.1"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun checkUpdate(): Result<AppReleaseInfo?> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(API_URL)
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Lỗi máy chủ (${response.code})"))
            }

            val json = JSONObject(body)
            val latestTag = json.optString("tag_name", "")
            val releaseNotes = json.optString("body", "Có bản cập nhật mới!")

            // Kiểm tra xem tag mới có khác hoặc cao hơn tag hiện tại không
            if (latestTag.isNotBlank() && isNewerVersion(latestTag, CURRENT_VERSION)) {
                val assets = json.optJSONArray("assets")
                var downloadUrl = ""

                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            downloadUrl = asset.optString("browser_download_url", "")
                            break
                        }
                    }
                }

                if (downloadUrl.isNotBlank()) {
                    return@withContext Result.success(AppReleaseInfo(latestTag, releaseNotes, downloadUrl))
                }
            }

            return@withContext Result.success(null) // Đang ở bản mới nhất
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi kiểm tra cập nhật", e)
            return@withContext Result.failure(e)
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val cleanLatest = latest.removePrefix("v").trim()
        val cleanCurrent = current.removePrefix("v").trim()
        return cleanLatest != cleanCurrent
    }

    fun promptUpdate(activity: Activity, releaseInfo: AppReleaseInfo) {
        AlertDialog.Builder(activity)
            .setTitle("🚀 Đã có bản cập nhật mới (${releaseInfo.tagName})")
            .setMessage("${releaseInfo.releaseNotes}\n\nBạn có muốn tự động tải về và nâng cấp ngay không?")
            .setPositiveButton("Cập nhật ngay") { _, _ ->
                startDownloadAndInstall(activity, releaseInfo.downloadUrl, "RedmiCompanion_${releaseInfo.tagName}.apk")
            }
            .setNegativeButton("Để sau", null)
            .show()
    }

    fun startDownloadAndInstall(activity: Activity, downloadUrl: String, fileName: String) {
        Toast.makeText(activity, "Đang tải bản cập nhật mới...", Toast.LENGTH_SHORT).show()

        val downloadManager = activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(downloadUrl)

        val destinationFile = File(activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
        if (destinationFile.exists()) {
            destinationFile.delete()
        }

        val request = DownloadManager.Request(uri).apply {
            setTitle("Redmi Watch Companion Update")
            setDescription("Đang tải bản cập nhật mới...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationUri(Uri.fromFile(destinationFile))
        }

        val downloadId = downloadManager.enqueue(request)

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    try {
                        activity.unregisterReceiver(this)
                    } catch (e: Exception) {}

                    installApk(activity, destinationFile)
                }
            }
        }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.registerReceiver(onComplete, filter, Context.RECEIVER_EXPORTED)
        } else {
            activity.registerReceiver(onComplete, filter)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(context, "Không tìm thấy file APK vừa tải!", Toast.LENGTH_SHORT).show()
            return
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Không thể mở trình cài đặt: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
