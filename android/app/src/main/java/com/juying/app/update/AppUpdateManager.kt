package com.juying.app.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.juying.app.BuildConfig
import com.juying.app.engine.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val title: String,
    val notes: String,
    val apkUrls: List<String>,
    val sha256: String = "",
    val source: String = ""
)

sealed interface UpdateCheckResult {
    data class Available(val info: AppUpdateInfo) : UpdateCheckResult
    data object Latest : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

class AppUpdateManager(private val context: Context) {
    companion object {
        private const val PREFS = "app_updates"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_PENDING_APK = "pending_apk"
        private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

        // Mainland-friendly self-hosted manifests first. GitHub Releases is
        // only a fallback because access and large-file downloads can be slow.
        private val MANIFEST_URLS by lazy {
            (
                BuildConfig.UPDATE_MANIFEST_URLS
                    .split(',', ';', '\n')
                    .map(String::trim)
                    .filter(String::isNotBlank) +
                    listOf(
                        "https://www.lanerc.app/api/android/update.json",
                        "https://lanerc.app/api/android/update.json"
                    )
                )
                .filter { it.startsWith("https://", ignoreCase = true) }
                .distinct()
        }
        private const val GITHUB_RELEASE_API =
            "https://api.github.com/repos/ssy20031224/juying/releases/latest"
    }

    private val client by lazy {
        NetworkClient.create(context).newBuilder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }
    private val preferences by lazy {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    suspend fun check(manual: Boolean): UpdateCheckResult = try {
        withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!manual && now - preferences.getLong(KEY_LAST_CHECK, 0L) < CHECK_INTERVAL_MS) {
            return@withContext UpdateCheckResult.Latest
        }
        preferences.edit().putLong(KEY_LAST_CHECK, now).apply()

        val errors = mutableListOf<String>()
        MANIFEST_URLS.forEach { url ->
            val body = fetchText(url)
            if (body != null) {
                val info = runCatching { parseManifest(body, url) }.getOrNull()
                if (info != null) return@withContext compare(info)
                errors += "$url: invalid manifest"
            } else {
                errors += "$url: unavailable"
            }
        }

        val githubBody = fetchText(GITHUB_RELEASE_API, acceptJson = true)
        if (githubBody != null) {
            val info = runCatching { parseGithubRelease(githubBody) }.getOrNull()
            if (info != null) return@withContext compare(info)
            errors += "GitHub Releases: no APK asset"
        } else {
            errors += "GitHub Releases: unavailable"
        }

        UpdateCheckResult.Failed(errors.joinToString("；").take(320))
        }
    } catch (error: Exception) {
        UpdateCheckResult.Failed(error.message ?: "update check failed")
    }

    suspend fun download(
        info: AppUpdateInfo,
        onProgress: (Int) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val outputDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir,
            "updates"
        )
        outputDir.mkdirs()
        val output = File(outputDir, "lanerc-${safe(info.versionName)}.apk")
        val temporary = File(outputDir, "${output.name}.part")

        var lastError = "没有可用的下载地址"
        info.apkUrls.filter { it.startsWith("https://", ignoreCase = true) }.forEach { url ->
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Lanerc/${BuildConfig.VERSION_NAME} Android")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = "HTTP ${response.code}: ${response.request.url.host}"
                        return@use
                    }
                    val body = response.body ?: run {
                        lastError = "安装包响应为空"
                        return@use
                    }
                    val total = body.contentLength().coerceAtLeast(0L)
                    body.byteStream().use { input ->
                        temporary.outputStream().use { target ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var copied = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (read == 0) continue
                                target.write(buffer, 0, read)
                                copied += read
                                if (total > 0L) {
                                    onProgress(((copied * 100L) / total).toInt().coerceIn(0, 100))
                                }
                            }
                        }
                    }
                    if (temporary.length() < 1024L * 1024L) {
                        lastError = "下载内容不像完整 APK"
                        temporary.delete()
                        return@use
                    }
                    if (info.sha256.isNotBlank() &&
                        !sha256(temporary).equals(info.sha256, ignoreCase = true)
                    ) {
                        lastError = "安装包 SHA-256 校验失败"
                        temporary.delete()
                        return@use
                    }
                    if (output.exists()) output.delete()
                    if (!temporary.renameTo(output)) {
                        temporary.copyTo(output, overwrite = true)
                        temporary.delete()
                    }
                    onProgress(100)
                    return@withContext Result.success(output)
                }
            } catch (error: Exception) {
                lastError = error.message ?: "下载失败"
                temporary.delete()
            }
        }
        Result.failure(IllegalStateException(lastError))
    }

    fun installOrRequestPermission(activity: Activity, apk: File) {
        if (!apk.exists()) return
        preferences.edit().putString(KEY_PENDING_APK, apk.absolutePath).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            activity.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
            )
            return
        }
        launchInstaller(activity, apk)
    }

    fun resumePendingInstall(activity: Activity) {
        val path = preferences.getString(KEY_PENDING_APK, null) ?: return
        val apk = File(path)
        if (!apk.exists()) {
            preferences.edit().remove(KEY_PENDING_APK).apply()
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            activity.packageManager.canRequestPackageInstalls()
        ) {
            launchInstaller(activity, apk)
        }
    }

    private fun launchInstaller(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${BuildConfig.APPLICATION_ID}.update.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        preferences.edit().remove(KEY_PENDING_APK).apply()
        activity.startActivity(intent)
    }

    private fun compare(info: AppUpdateInfo): UpdateCheckResult =
        if (isNewer(info) && info.apkUrls.isNotEmpty()) {
            UpdateCheckResult.Available(info)
        } else {
            UpdateCheckResult.Latest
        }

    private fun isNewer(info: AppUpdateInfo): Boolean {
        if (info.source == "GitHub Releases") {
            val remote = info.versionName.removePrefix("v")
                .split('.')
                .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            val local = BuildConfig.VERSION_NAME.removePrefix("v")
                .split('.')
                .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
            for (index in 0 until maxOf(remote.size, local.size)) {
                val r = remote.getOrElse(index) { 0 }
                val l = local.getOrElse(index) { 0 }
                if (r != l) return r > l
            }
            return false
        }
        return info.versionCode > BuildConfig.VERSION_CODE
    }

    private fun fetchText(url: String, acceptJson: Boolean = false): String? {
        if (!url.startsWith("https://", ignoreCase = true)) return null
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Lanerc/${BuildConfig.VERSION_NAME} Android")
            .apply { if (acceptJson) header("Accept", "application/vnd.github+json") }
            .get()
            .build()
        return try {
            client.newCall(request).execute().use {
                if (it.isSuccessful) it.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseManifest(raw: String, source: String): AppUpdateInfo {
        val json = JSONObject(raw)
        val urls = mutableListOf<String>()
        json.optJSONArray("apkUrls")?.let { array ->
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.startsWith("https://") }?.let(urls::add)
            }
        }
        json.optString("apkUrl").takeIf { it.startsWith("https://") }?.let(urls::add)
        return AppUpdateInfo(
            versionCode = json.getInt("versionCode"),
            versionName = json.optString("versionName", json.getInt("versionCode").toString()),
            title = json.optString("title", "发现新版本"),
            notes = json.optString("notes", "修复问题并提升使用体验"),
            apkUrls = urls.distinct(),
            sha256 = json.optString("sha256"),
            source = source
        )
    }

    private fun parseGithubRelease(raw: String): AppUpdateInfo? {
        val json = JSONObject(raw)
        val assets = json.optJSONArray("assets") ?: return null
        val urls = mutableListOf<String>()
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (name.endsWith(".apk", ignoreCase = true)) {
                asset.optString("browser_download_url")
                    .takeIf { it.startsWith("https://") }
                    ?.let(urls::add)
            }
        }
        if (urls.isEmpty()) return null
        val tag = json.optString("tag_name").removePrefix("v")
        return AppUpdateInfo(
            versionCode = versionCodeFromTag(tag),
            versionName = tag.ifBlank { json.optString("name", "new") },
            title = json.optString("name", "发现新版本"),
            notes = json.optString("body", "GitHub Release 更新"),
            apkUrls = urls,
            source = "GitHub Releases"
        )
    }

    private fun versionCodeFromTag(tag: String): Int {
        val parts = tag.split('.').mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        if (parts.isEmpty()) return 0
        return parts.getOrElse(0) { 0 } * 10_000 +
            parts.getOrElse(1) { 0 } * 100 +
            parts.getOrElse(2) { 0 }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
    }

    private fun safe(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40).ifBlank { "update" }
}
