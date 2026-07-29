package com.whiskeymike.wmpoketrap.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.whiskeymike.wmpoketrap.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String = "",
)

sealed class UpdateCheckResult {
    data class Available(val info: UpdateInfo) : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data class Failed(val message: String) : UpdateCheckResult()
}

object AppUpdater {
    /** Public releases repo that hosts APKs. */
    const val OWNER = "mikejschartner"
    const val REPO = "WMPokeTrap"
    private const val API =
        "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val body = httpGet(API)
            val json = JSONObject(body)
            val tag = json.optString("tag_name").removePrefix("v")
            val notes = json.optString("body").orEmpty()
            val assets = json.optJSONArray("assets")
                ?: return@withContext UpdateCheckResult.Failed("No release assets yet")

            var apkUrl: String? = null
            var metaCode: Int? = null
            var metaName: String? = null

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                val url = asset.optString("browser_download_url")
                when {
                    name.equals("latest.json", ignoreCase = true) -> {
                        val meta = JSONObject(httpGet(url))
                        metaCode = meta.optInt("versionCode", 0)
                        metaName = meta.optString("versionName", tag)
                    }
                    name.endsWith(".apk", ignoreCase = true) && apkUrl == null -> {
                        apkUrl = url
                    }
                }
            }

            val remoteCode = metaCode
                ?: tag.substringBefore('-').substringBefore('+').toIntOrNull()
                ?: parseVersionCodeFromNotes(notes)
                ?: 0
            val remoteName = metaName ?: tag.ifBlank { remoteCode.toString() }
            val download = apkUrl
                ?: return@withContext UpdateCheckResult.Failed("No APK on latest release")

            if (remoteCode <= BuildConfig.VERSION_CODE) {
                UpdateCheckResult.UpToDate
            } else {
                UpdateCheckResult.Available(
                    UpdateInfo(
                        versionCode = remoteCode,
                        versionName = remoteName,
                        apkUrl = download,
                        notes = notes.trim(),
                    ),
                )
            }
        } catch (e: Exception) {
            UpdateCheckResult.Failed(e.message ?: "Update check failed")
        }
    }

    suspend fun downloadApk(context: Context, info: UpdateInfo): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val out = File(dir, "WMPokeTrap-update.apk")
            if (out.exists()) out.delete()
            val conn = (URL(info.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 120_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "WMPokeTrap/${BuildConfig.VERSION_NAME}")
                setRequestProperty("Accept", "application/octet-stream")
            }
            conn.inputStream.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            conn.disconnect()
            out
        }

    fun canRequestInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}"),
                ),
            )
        }
    }

    fun installApk(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun parseVersionCodeFromNotes(notes: String): Int? {
        val re = Regex("""versionCode\s*[=:]\s*(\d+)""", RegexOption.IGNORE_CASE)
        return re.find(notes)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun httpGet(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "WMPokeTrap/${BuildConfig.VERSION_NAME}")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            if (conn.responseCode !in 200..299) {
                throw IllegalStateException("HTTP ${conn.responseCode}")
            }
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
