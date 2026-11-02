package com.jntoolz.data

import android.content.Context
import android.util.Log
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI

data class ReleaseInfo(
    val tagName: String,
    val title: String,
    val changelog: String,
    val apkUrl: String?,
    val htmlUrl: String,
    val hasUpdate: Boolean
)

object UpdateChecker {

    const val REPO_OWNER = "JudeCJz"
    const val REPO_NAME = "JNToolz"
    const val GITHUB_REPO_URL = "https://github.com/$REPO_OWNER/$REPO_NAME"

    val CURRENT_VERSION_CHANGELOG = """
        • Unscroll: Smart anti-doomscroll tool with customizable target apps & humorous Hard Mode unlock
        • Hardware Telemetry: Accurate live detection of phone model (Nothing Phone (4a) Pro), OS build, battery design capacity, health %, and physical device age with charge cycles
        • Battery Graph Export: Comprehensive clipboard export with prepended device hardware specifications
        • Status Bar Icon: Clean monochrome JNToolz logo in status bar, full battery icon in notification shade
        • Hub Screen: Unified red/dark segmented ON/OFF toggles across all tools
        • Counter Editor: Height stabilization when toggling gradient & card styles with smooth animations
    """.trimIndent()

    fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.1"
        } catch (e: Exception) {
            "1.0.1"
        }
    }

    suspend fun checkForUpdates(context: Context): ReleaseInfo = withContext(Dispatchers.IO) {
        val currentVersion = getCurrentVersionName(context)
        try {
            val url = URI("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest").toURL()
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "JNToolz-Android-App")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 7000
                readTimeout = 7000
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)

                val tagName = json.optString("tag_name", "").trim()
                val name = json.optString("name", tagName).ifEmpty { tagName }
                val body = json.optString("body", "No changelog provided.")
                val htmlUrl = json.optString("html_url", "$GITHUB_REPO_URL/releases")

                var apkDownloadUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val assetName = asset.optString("name", "")
                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = if (asset.has("browser_download_url")) asset.getString("browser_download_url") else null
                            break
                        }
                    }
                }

                val isNewer = isVersionNewer(remote = tagName, current = currentVersion)

                ReleaseInfo(
                    tagName = tagName,
                    title = name,
                    changelog = body,
                    apkUrl = apkDownloadUrl ?: htmlUrl,
                    htmlUrl = htmlUrl,
                    hasUpdate = isNewer
                )
            } else {
                ReleaseInfo(
                    tagName = "v$currentVersion",
                    title = "Up to date",
                    changelog = CURRENT_VERSION_CHANGELOG,
                    apkUrl = null,
                    htmlUrl = "$GITHUB_REPO_URL/releases",
                    hasUpdate = false
                )
            }
        } catch (e: Exception) {
            ReleaseInfo(
                tagName = "v$currentVersion",
                title = "Up to date",
                changelog = CURRENT_VERSION_CHANGELOG,
                apkUrl = null,
                htmlUrl = "$GITHUB_REPO_URL/releases",
                hasUpdate = false
            )
        }
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        onProgress: (Float) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            var currentUrl = downloadUrl
            var conn: HttpURLConnection
            var redirects = 0
            while (true) {
                val url = URI(currentUrl).toURL()
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15000
                conn.readTimeout = 25000
                conn.setRequestProperty("User-Agent", "JNToolz-Android-App")

                val status = conn.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM || status == 307 || status == 308) {
                    val location = conn.getHeaderField("Location")
                    if (location != null && redirects < 5) {
                        currentUrl = location
                        redirects++
                        continue
                    }
                }
                break
            }

            val totalBytes = conn.contentLengthLong
            val cacheDir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
            val apkFile = File(cacheDir, "JNToolz-latest.apk").apply { if (exists()) delete() }

            conn.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (totalBytes > 0) {
                            val progress = (totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    }
                }
            }

            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("UpdateChecker", "update step failed: $e")
            Result.failure(e)
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w("UpdateChecker", "update step failed: $e")
            Toast.makeText(context, "Installation error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun isVersionNewer(remote: String, current: String): Boolean {
        fun normalize(v: String): List<Int> {
            val clean = v.trim().removePrefix("v").removePrefix("V")
            return clean.split(".", "-").mapNotNull { part ->
                part.filter { it.isDigit() }.toIntOrNull()
            }
        }

        val remoteParts = normalize(remote)
        val currentParts = normalize(current)
        val maxLen = maxOf(remoteParts.size, currentParts.size)

        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }
}
