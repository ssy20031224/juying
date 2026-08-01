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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
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
    val source: String = "",
    val manifestRevision: Long = 0L,
)

internal fun parseUpdateManifest(raw: String, source: String): AppUpdateInfo {
    val root = JSONObject(raw)
    val update = root.optJSONObject("update")
    val scopes = listOfNotNull(update, root)

    fun firstString(vararg names: String): String =
        scopes.firstNotNullOfOrNull { json ->
            names.firstNotNullOfOrNull { name ->
                json.opt(name)?.let { value ->
                    when (value) {
                        is String -> value.trim().takeIf(String::isNotBlank)
                        is JSONArray -> buildList {
                            for (index in 0 until value.length()) {
                                value.optString(index).trim()
                                    .takeIf(String::isNotBlank)
                                    ?.let(::add)
                            }
                        }.joinToString("\n").takeIf(String::isNotBlank)
                        else -> null
                    }
                }
            }
        }.orEmpty()

    val urls = mutableListOf<String>()
    scopes.forEach { json ->
        json.optJSONArray("apkUrls")?.let { array ->
            for (index in 0 until array.length()) {
                array.optString(index).takeIf { it.startsWith("https://") }?.let(urls::add)
            }
        }
        firstStringFrom(json, "apkUrl", "downloadUrl", "download_url")
            .takeIf { it.startsWith("https://") }
            ?.let(urls::add)
    }

    val versionCode = scopes.firstNotNullOfOrNull { json ->
        json.optInt("versionCode", 0).takeIf { it > 0 }
            ?: json.optInt("version_code", 0).takeIf { it > 0 }
    } ?: throw IllegalArgumentException("missing versionCode")
    val versionName = firstString("versionName", "version_name", "version")
        .ifBlank { versionCode.toString() }

    return AppUpdateInfo(
        versionCode = versionCode,
        versionName = versionName,
        title = firstString("title", "releaseTitle", "release_title", "updateTitle", "name")
            .ifBlank { "发现新版本 $versionName" },
        notes = firstString(
            "notes",
            "releaseNotes",
            "release_notes",
            "updateContent",
            "update_content",
            "changelog",
            "changeLog",
            "content",
            "description"
        ).ifBlank { "修复问题并提升使用体验" },
        apkUrls = urls.distinct(),
        sha256 = firstString("sha256", "sha_256"),
        source = source,
        manifestRevision = scopes.firstNotNullOfOrNull { json ->
            json.optLong("manifestRevision", 0L).takeIf { it > 0L }
                ?: json.optLong("revision", 0L).takeIf { it > 0L }
        } ?: 0L
    )
}

internal fun selectBestUpdateCandidate(candidates: List<AppUpdateInfo>): AppUpdateInfo? =
    candidates.withIndex()
        .filter { it.value.apkUrls.isNotEmpty() }
        .sortedWith(
            compareByDescending<IndexedValue<AppUpdateInfo>> { it.value.versionCode }
                .thenByDescending { it.value.manifestRevision }
                // Equal versions keep the earlier configured URL, allowing a
                // publisher-controlled manifest to override mirrors' notes.
                .thenBy { it.index }
        )
        .firstOrNull()
        ?.value

private fun firstStringFrom(json: JSONObject, vararg names: String): String =
    names.firstNotNullOfOrNull { name ->
        json.optString(name).trim().takeIf(String::isNotBlank)
    }.orEmpty()

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

        // 国内云分发（公开 Bucket，只存放 APK 与 update.json；密钥仅存在于发布端 .env，
        // 公开域名本身会随 update.json 的 apkUrls 下发给所有客户端，不属于敏感信息）。
        private const val ALIYUN_OSS_PUBLIC_BASE = "https://ssyjuying.oss-cn-shanghai.aliyuncs.com"
        private const val TENCENT_COS_PUBLIC_BASE = "" // 腾讯云 COS 配置完成后填入其公开域名
        private const val UPDATE_MANIFEST_KEY = "api/android/update.json"
        private const val APK_OBJECT_PREFIX = "android"

        private fun cloudManifestUrl(publicBase: String): String? =
            publicBase.trim().trimEnd('/')
                .takeIf { it.startsWith("https://", ignoreCase = true) }
                ?.let { "$it/$UPDATE_MANIFEST_KEY" }

        // 国内优先：阿里云 → 腾讯云 → 自建站点；GitHub Releases 只作最后回退，
        // 因为国内访问与大文件下载都慢。
        private val MANIFEST_URLS by lazy {
            (
                BuildConfig.UPDATE_MANIFEST_URLS
                    .split(',', ';', '\n')
                    .map(String::trim)
                    .filter(String::isNotBlank) +
                    listOf(
                        "https://www.lanerc.app/api/android/update.json",
                        "https://lanerc.app/api/android/update.json",
                        "https://raw.githubusercontent.com/ssy20031224/juying/main/public/api/android/update.json",
                        "https://cdn.jsdelivr.net/gh/ssy20031224/juying@main/public/api/android/update.json"
                    ) +
                    listOfNotNull(
                        cloudManifestUrl(ALIYUN_OSS_PUBLIC_BASE),
                        cloudManifestUrl(TENCENT_COS_PUBLIC_BASE)
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
        val candidates = mutableListOf<AppUpdateInfo>()
        val manifestResponses = coroutineScope {
            MANIFEST_URLS.map { url ->
                async { url to fetchText(url, cacheBust = manual) }
            }.awaitAll()
        }
        manifestResponses.forEach { (url, body) ->
            if (body != null) {
                val info = runCatching { parseUpdateManifest(body, url) }.getOrNull()
                if (info != null) candidates += info else errors += "$url: invalid manifest"
            } else {
                errors += "$url: unavailable"
            }
        }

        val githubBody = fetchText(
            GITHUB_RELEASE_API,
            acceptJson = true,
            cacheBust = manual
        )
        if (githubBody != null) {
            val info = runCatching { parseGithubRelease(githubBody) }.getOrNull()
            if (info != null) candidates += info else errors += "GitHub Releases: no APK asset"
        } else {
            errors += "GitHub Releases: unavailable"
        }

        selectBestUpdateCandidate(candidates)?.let { return@withContext compare(it) }
        UpdateCheckResult.Failed(errors.joinToString("；").take(320))
        }
    } catch (error: Exception) {
        UpdateCheckResult.Failed(error.message ?: "update check failed")
    }

    // 下载地址国内优先：同一版本的阿里云/腾讯云地址排在最前，GitHub 仅作最后回退。
    // 即使更新信息来自 GitHub Releases（其只含 GitHub 地址），也先尝试云分发地址，
    // 对象不存在时按序回退，不影响可用性。
    private fun prioritizeApkUrls(info: AppUpdateInfo): List<String> {
        val versionName = info.versionName.trim().removePrefix("v")
        val cloudUrls = mutableListOf<String>()
        if (versionName.isNotBlank()) {
            val apkName = "juying-$versionName.apk"
            val cloudObjects = listOf(
                // 阿里云默认 OSS 域名拒绝直接分发 .apk；远端对象使用 .bin，
                // 下载后仍保存为本地 .apk，并在安装前完成 SHA-256 校验。
                ALIYUN_OSS_PUBLIC_BASE to "juying-$versionName.bin",
                TENCENT_COS_PUBLIC_BASE to apkName
            )
            cloudObjects.forEach { (base, objectName) ->
                base.trim().trimEnd('/')
                    .takeIf { it.startsWith("https://", ignoreCase = true) }
                    ?.let { cloudUrls += "$it/$APK_OBJECT_PREFIX/$objectName" }
            }
        }
        val mainland = info.apkUrls.filter {
            !it.contains("github.com", ignoreCase = true) &&
                !it.contains("githubusercontent.com", ignoreCase = true)
        }
        val github = info.apkUrls.filter { it !in mainland }
        return (cloudUrls + mainland + github).distinct()
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
        val output = File(outputDir, "juying-${safe(info.versionName)}.apk")
        val temporary = File(outputDir, "${output.name}.part")

        var lastError = "没有可用的下载地址"
        prioritizeApkUrls(info).filter { it.startsWith("https://", ignoreCase = true) }.forEach { url ->
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "juying/${BuildConfig.VERSION_NAME} Android")
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

    private fun fetchText(
        url: String,
        acceptJson: Boolean = false,
        cacheBust: Boolean = false
    ): String? {
        if (!url.startsWith("https://", ignoreCase = true)) return null
        val requestUrl = if (cacheBust) {
            url.toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("_juying_check", System.currentTimeMillis().toString())
                ?.build()
                ?.toString()
                ?: url
        } else {
            url
        }
        val request = Request.Builder()
            .url(requestUrl)
            .header("User-Agent", "juying/${BuildConfig.VERSION_NAME} Android")
            .apply {
                if (cacheBust) {
                    cacheControl(CacheControl.Builder().noCache().build())
                    header("Pragma", "no-cache")
                }
            }
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
