package com.juying.app.source

import android.content.Context
import android.util.Base64
import android.util.Log
import com.juying.app.engine.NetworkClient
import com.juying.app.BuildConfig
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Downloads JS source scripts from a remote CDN/server and caches them locally.
 * Falls back to bundled APK assets when network is unavailable.
 *
 * Deployment: upload remote_sources/ JS files to any static hosting (Cloudflare R2,
 * Workers static assets, or any CDN). Set SOURCE_BASE_URL to the hosting root.
 */
object RemoteSourceFetcher {
    private const val TAG = "RemoteSourceFetcher"
    private const val ENC1_KEY_SEED = "anime_79bcadc9f3304f99bb8c3896bf826062e14644ff"

    private const val LEGACY_SOURCE_BASE_URL = "https://js.z1i.cn/js/"

    private val downloadedHashes = mutableMapOf<String, String>()

    data class RemoteSource(
        val key: String,
        val title: String = "",
        val localFile: String,
        val codeUrl: String,
        val defaultEnabled: Boolean = true
    )

    fun remoteSources(): List<RemoteSource> {
        return listOf(
            RemoteSource("AuvFun", "AuvFun", "AuvFun.js", "${LEGACY_SOURCE_BASE_URL}AuvFun.js"),
            RemoteSource("lanerc", "Lanerc", "lanerc.js", "${LEGACY_SOURCE_BASE_URL}lanerc_legacy.js"),
            RemoteSource("jinpai", "金牌", "jinpai.js", "${LEGACY_SOURCE_BASE_URL}jinpaiapp.js"),
            RemoteSource("cycapp", "次元城", "cycapp.js", "${LEGACY_SOURCE_BASE_URL}cyc.js"),
            RemoteSource("guazi", "瓜子", "guazi.js", "${LEGACY_SOURCE_BASE_URL}guazi.js"),
            RemoteSource("shuangxing", "双星", "shuangxing.js", "${LEGACY_SOURCE_BASE_URL}shuangxing99.js"),
            RemoteSource("xifanacg", "稀饭动漫", "xifanacg.js", "${LEGACY_SOURCE_BASE_URL}xifanacg.js"),
            RemoteSource("yzx", "云帆", "yzx.js", "${LEGACY_SOURCE_BASE_URL}yzx.js"),
            RemoteSource("sanqiu", "三秋", "sanqiu.js", "${LEGACY_SOURCE_BASE_URL}sanqiu.js"),
            RemoteSource("akianime", "AkiAnime", "akianime.js", "${LEGACY_SOURCE_BASE_URL}akianime.js"),
            RemoteSource("lmm85", "动漫在线", "lmm85.js", "${LEGACY_SOURCE_BASE_URL}lmm85.js"),
            RemoteSource("gugu", "咕咕动漫", "gugu.js", "${LEGACY_SOURCE_BASE_URL}gugu.js"),
            RemoteSource("dmbus", "动漫巴士", "dmbus.js", "${LEGACY_SOURCE_BASE_URL}dmbus.js"),
            RemoteSource("shutiao", "薯条", "shutiao.js", "${LEGACY_SOURCE_BASE_URL}shutiao.js"),
        )
    }

    private fun candidateUrls(source: RemoteSource): List<String> {
        val cloud = BuildConfig.SOURCE_SCRIPT_BASE_URL.trim().trimEnd('/')
        return listOfNotNull(
            cloud.takeIf { it.startsWith("https://", ignoreCase = true) }
                ?.let { "$it/${source.localFile}" },
            source.codeUrl.takeIf(String::isNotBlank)
        ).distinct()
    }

    /**
     * Get the JS script content for a source.
     * Priority: remote cache > bundled assets
     */
    fun getScript(context: Context, key: String, remoteUrl: String): String {
        val cacheFile = File(context.filesDir, "source_scripts/${key}.js")

        // 1. Return cached remote version if valid JavaScript
        if (cacheFile.exists() && cacheFile.length() > 100) {
            val cached = decodeEnc1(cacheFile.readText()).removePrefix("\uFEFF").trim()
            if (isValidJsScript(cached)) {
                return cached
            } else {
                Log.w(TAG, "Deleting corrupted cache file for $key")
                try { cacheFile.delete() } catch (_: Exception) {}
            }
        }

        // A valid bundled script is the fast offline path. Only attempt a
        // synchronous remote repair when both cache and APK copies are bad;
        // this keeps cold start bounded on networks that are unavailable.
        val bundled = try {
            context.assets.open("sources/${key}.js").bufferedReader().readText()
                .removePrefix("\uFEFF").trim()
        } catch (_: Exception) { "" }
        if (isValidJsScript(bundled)) return bundled

        // 2. A missing cache must not silently select a broken APK copy.
        // SourceManager initializes on Dispatchers.IO, so this bounded fetch is
        // safe here and removes the startup race with asynchronous syncAll().
        if (remoteUrl.isNotBlank()) {
            try {
                val client = NetworkClient.create(context).newBuilder()
                    .connectTimeout(3, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
                val source = remoteSources().firstOrNull { it.key == key }
                val urls = source?.let(::candidateUrls) ?: listOf(remoteUrl)
                for (url in urls) {
                    val response = client.newCall(Request.Builder().url(url).get().build()).execute()
                    val body = response.use { decodeEnc1(it.body?.string().orEmpty()) }
                    if (response.isSuccessful && isValidJsScript(body)) {
                        cacheFile.parentFile?.mkdirs()
                        val tmp = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
                        tmp.writeText(body)
                        if (!tmp.renameTo(cacheFile)) {
                            tmp.delete()
                            cacheFile.writeText(body)
                        }
                        Log.i(TAG, "Fetched $key on demand (${body.length} bytes)")
                        return body.removePrefix("\uFEFF").trim()
                    }
                }
                Log.w(TAG, "On-demand fetch for $key rejected by all configured hosts")
            } catch (e: Exception) {
                Log.w(TAG, "On-demand fetch for $key failed: ${e.message}")
            }
        }

        // 3. Last-resort bundled asset (possibly empty; SourceManager isolates it)
        return try {
            val assetFile = "sources/${key}.js"
            context.assets.open(assetFile).bufferedReader().readText().removePrefix("\uFEFF").trim()
        } catch (e: Exception) {
            Log.e(TAG, "No local script for $key: ${e.message}")
            ""
        }
    }

    private fun isValidJsScript(code: String): Boolean {
        val trimmed = code.trim()
        if (trimmed.length < 100) return false
        val lower = trimmed.take(500).lowercase()
        if (lower.startsWith("<!doctype") || lower.startsWith("<html") || lower.contains("<head>") || lower.contains("</html>")) {
            return false
        }
        val head = trimmed.take(500)
        return head.contains("var ") || head.contains("function ") || head.contains("const ") ||
               head.contains("let ") || head.startsWith("/*") || head.startsWith("//") ||
               head.contains("exports") || head.contains("globalThis") || head.contains("typeof")
    }

    private fun decodeEnc1(raw: String): String {
        val text = raw.removePrefix("\uFEFF").trim()
        if (!text.startsWith("ENC1:")) return text
        return try {
            val payload = Base64.decode(text.removePrefix("ENC1:"), Base64.DEFAULT)
            if (payload.size <= 16) return ""
            val key = MessageDigest.getInstance("SHA-256")
                .digest(ENC1_KEY_SEED.toByteArray(Charsets.UTF_8))
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                IvParameterSpec(payload.copyOfRange(0, 16))
            )
            String(cipher.doFinal(payload.copyOfRange(16, payload.size)), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "ENC1 source decrypt failed: ${e.message}")
            ""
        }
    }

    /**
     * Download all remote scripts and cache them for offline use.
     * Call this on app startup (background thread).
     */
    suspend fun syncAll(context: Context): Int {
        val client = NetworkClient.create(context)
        val sources = remoteSources()
        var downloaded = 0

        for (source in sources) {
            try {
                val result = withContext(Dispatchers.IO) {
                    downloadScript(client, context, source)
                }
                if (result) downloaded++
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync ${source.key}: ${e.message}")
            }
        }
        Log.i(TAG, "Sync complete: $downloaded/${sources.size} scripts updated")
        return downloaded
    }

    private fun downloadScript(client: okhttp3.OkHttpClient, context: Context, source: RemoteSource): Boolean {
        val cacheDir = File(context.filesDir, "source_scripts")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val cacheFile = File(cacheDir, "${source.key}.js")

        for (remoteUrl in candidateUrls(source)) {
            val request = Request.Builder().url(remoteUrl).head().build()
            try {
                client.newCall(request).execute().use { headResp ->
                    val remoteHash = headResp.header("ETag")
                        ?: headResp.header("x-oss-hash-crc64ecma")
                        ?: headResp.header("Content-MD5")
                    if (remoteHash != null) {
                        val cachedHash = downloadedHashes[source.key]
                        if (cachedHash == remoteHash && cacheFile.exists() && cacheFile.length() > 100) {
                            return false
                        }
                    }
                }
            } catch (_: Exception) {
                // HEAD failed; proceed to GET.
            }

            val getRequest = Request.Builder().url(remoteUrl).get().build()
            try {
                client.newCall(getRequest).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = decodeEnc1(response.body?.string() ?: return@use)
                    if (!isValidJsScript(body)) return@use
                    val hash = sha256(body)
                    val cachedBody = runCatching { if (cacheFile.exists()) cacheFile.readText() else "" }
                        .getOrDefault("")
                    if (cachedBody.isNotEmpty() && sha256(cachedBody) == hash) {
                        downloadedHashes[source.key] = hash
                        return false
                    }
                    downloadedHashes[source.key] = hash
                    val tmp = File(cacheDir, "${source.key}.js.tmp")
                    tmp.writeText(body)
                    if (!tmp.renameTo(cacheFile)) {
                        tmp.delete()
                        cacheFile.writeText(body)
                    }
                    Log.d(TAG, "Downloaded ${source.key}: ${body.length} bytes, hash=$hash")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Download failed for ${source.key} from $remoteUrl: ${e.message}")
            }
        }
        return false
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
