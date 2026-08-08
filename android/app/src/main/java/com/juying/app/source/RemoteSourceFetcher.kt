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
import java.util.concurrent.TimeUnit

/**
 * Resolves JS source scripts for the runtime. The current safety policy keeps
 * bundled APK assets authoritative and only uses CDN/cache copies as fallback.
 *
 * Deployment: upload remote_sources/ JS files to any static hosting (Cloudflare R2,
 * Workers static assets, or any CDN). Set SOURCE_BASE_URL to the hosting root.
 */
object RemoteSourceFetcher {
    private const val TAG = "RemoteSourceFetcher"
    private const val ENC1_KEY_SEED = "anime_79bcadc9f3304f99bb8c3896bf826062e14644ff"

    private const val LEGACY_SOURCE_BASE_URL = "https://js.z1i.cn/js/"

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
            RemoteSource("lanerc", "Lanerc", "lanerc.js", "${LEGACY_SOURCE_BASE_URL}lanerc.js", defaultEnabled = false),
            RemoteSource("jinpai", "金牌", "jinpai.js", "${LEGACY_SOURCE_BASE_URL}jinpaiapp.js"),
            RemoteSource("cycapp", "次元城", "cycapp.js", "${LEGACY_SOURCE_BASE_URL}cyc.js"),
            RemoteSource("guazi", "瓜子", "guazi.js", "${LEGACY_SOURCE_BASE_URL}guazi.js"),
            RemoteSource("shuangxing", "双星", "shuangxing.js", "${LEGACY_SOURCE_BASE_URL}shuangxing99.js"),
            RemoteSource("xifanacg", "稀饭动漫", "xifanacg.js", "${LEGACY_SOURCE_BASE_URL}xifanacg.js"),
            RemoteSource("fanshu", "番薯", "fanshu.js", "${LEGACY_SOURCE_BASE_URL}fanshu.js", defaultEnabled = false),
            RemoteSource("yzx", "云帆", "yzx.js", "${LEGACY_SOURCE_BASE_URL}yzx.js"),
            RemoteSource("sanqiu", "三秋", "sanqiu.js", "${LEGACY_SOURCE_BASE_URL}sanqiu.js", defaultEnabled = false),
            RemoteSource("akianime", "AkiAnime", "akianime.js", "${LEGACY_SOURCE_BASE_URL}akianime.js"),
            RemoteSource("lmm85", "动漫在线", "lmm85.js", "${LEGACY_SOURCE_BASE_URL}lmm85.js"),
            RemoteSource("gugu", "咕咕动漫", "gugu.js", "${LEGACY_SOURCE_BASE_URL}gugu.js"),
            RemoteSource("dmbus", "动漫巴士", "dmbus.js", "${LEGACY_SOURCE_BASE_URL}dmbus.js"),
            RemoteSource("shutiao", "薯条", "shutiao.js", "${LEGACY_SOURCE_BASE_URL}shutiao.js", defaultEnabled = false),
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
     * Priority: bundled assets > remote cache > on-demand fetch.
     *
     * This deliberately does not promise remote hot updates: a valid bundled
     * script returns before OSS is consulted. A versioned/signed manifest is
     * required before remote code can safely take precedence.
     */
    fun getScript(context: Context, key: String, remoteUrl: String): String {
        val cacheFile = File(context.filesDir, "source_scripts/${key}.js")

        // 1. Bundled assets are authoritative under the current safety policy.
        val bundled = try {
            context.assets.open("sources/${key}.js").bufferedReader().readText()
                .removePrefix("\uFEFF").trim()
        } catch (_: Exception) { "" }
        if (isValidJsScript(bundled)) return bundled

        // 2. Fall back to a previously cached remote copy if the APK copy is bad.
        if (cacheFile.exists() && cacheFile.length() > 100) {
            val cached = decodeEnc1(cacheFile.readText()).removePrefix("\uFEFF").trim()
            if (isValidJsScript(cached)) {
                return cached
            } else {
                Log.w(TAG, "Deleting corrupted cache file for $key")
                try { cacheFile.delete() } catch (_: Exception) {}
            }
        }

        // 3. A missing cache must not silently select a broken APK copy.
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

        // 4. Last-resort bundled asset (possibly empty; SourceManager isolates it)
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
     * Clear stale cached scripts so the authoritative bundled assets are used.
     * The previous sync target (js.z1i.cn) is retired and the Lanerc 1.0.7
     * backend executes scripts server-side; re-downloading old copies here
     * would silently downgrade every source. Kept as a suspend fun to keep
     * the startup call site unchanged.
     */
    suspend fun syncAll(context: Context): Int {
        val cacheDir = File(context.filesDir, "source_scripts")
        if (!cacheDir.exists()) return 0
        var cleared = 0
        cacheDir.listFiles { _, name -> name.endsWith(".js") || name.endsWith(".js.tmp") }
            ?.forEach { file ->
                try {
                    if (file.delete()) cleared++
                } catch (_: Exception) {}
            }
        Log.i(TAG, "Stale script cache cleared: $cleared files")
        return cleared
    }

    private fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
