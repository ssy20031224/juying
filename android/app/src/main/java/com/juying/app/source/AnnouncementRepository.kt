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
        } ?: defaultAnnouncement()
    }

    // 全部公告源不可用时展示的内置兜底公告，保证首页公告入口始终可用
    private fun defaultAnnouncement(): AppAnnouncement = AppAnnouncement(
        id = "builtin_default",
        title = "欢迎使用聚映",
        summary = "追番、评论与弹幕功能说明",
        content = "【聚映使用说明】\n1. 追番收藏与观看记录支持登录后多设备云端同步。\n2. 观看时右侧长按可临时快进倍速，上滑锁定、下滑恢复；播放中发送的弹幕会在同剧集同时间点回放。\n3. 如遇到播放卡顿，可在设置中切换视频源或降低清晰度。\n4. 请妥善保管账号密码，勿泄露给他人。",
        updatedAt = "2026-08-03 12:00",
    )

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
