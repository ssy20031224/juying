package com.juying.app.source

import android.content.Context
import com.juying.app.BuildConfig
import com.juying.app.engine.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.Request
import org.json.JSONObject

data class AppAnnouncement(
    val id: String,
    val title: String,
    val summary: String,
    val content: String,
    val enabled: Boolean = true,
    val updatedAt: String = "",
)

class AnnouncementRepository(context: Context) {
    private val client = NetworkClient.create(context).newBuilder().cache(null).build()

    suspend fun load(): AppAnnouncement? = withContext(Dispatchers.IO) {
        urls().firstNotNullOfOrNull { url ->
            runCatching {
                val separator = if (url.contains('?')) '&' else '?'
                val request = Request.Builder()
                    .url("$url${separator}_=${System.currentTimeMillis()}")
                    .header("User-Agent", "juying/${BuildConfig.VERSION_NAME} Android")
                    .cacheControl(CacheControl.Builder().noCache().build())
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    parse(response.body?.string().orEmpty())
                }
            }.getOrNull()
        }
    }

    private fun parse(raw: String): AppAnnouncement? {
        val root = JSONObject(raw)
        val value = root.optJSONObject("announcement") ?: root
        if (!value.optBoolean("enabled", true)) return null
        val title = value.optString("title").trim()
        val content = value.optString("content").trim()
        if (title.isBlank() || content.isBlank()) return null
        return AppAnnouncement(
            id = value.optString("id").trim().ifBlank {
                value.optString("updatedAt").trim().ifBlank { title.hashCode().toString() }
            },
            title = title,
            summary = value.optString("summary").trim().ifBlank { content.lineSequence().firstOrNull().orEmpty() },
            content = content,
            enabled = true,
            updatedAt = value.optString("updatedAt").trim(),
        )
    }

    private fun urls(): List<String> = (
        BuildConfig.ANNOUNCEMENT_URLS.split(',', ';', '\n').map(String::trim) +
            listOf("${BuildConfig.ACCOUNT_API_BASE}/api/announcement")
        ).filter { it.startsWith("https://", ignoreCase = true) }.distinct()
}
