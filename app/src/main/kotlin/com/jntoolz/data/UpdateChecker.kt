package com.jntoolz.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
        • Pure AMOLED Black Widget (#000000) inspired by Nothing OS
        • 5-Tier Gradual Card Fade completely blending into background
        • Collapsible Google Account dropdowns with calendar toggles
        • Native offline background sync (works 100% with debugging OFF)
        • Seamless GitHub release auto-updater and changelog view
    """.trimIndent()

    fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
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
