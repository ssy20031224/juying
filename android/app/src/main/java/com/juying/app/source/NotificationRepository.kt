package com.juying.app.source

import android.content.Context
import com.juying.app.BuildConfig
import com.juying.app.engine.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class CloudNotification(
    val id: String = "",
    val type: String = "favorite_update",
    val title: String = "",
    val body: String = "",
    val mediaKey: String = "",
    val episodeName: String = "",
    val commentId: String = "",
    val mediaSnapshot: String = "{}",
    val read: Boolean = false,
    val ts: Long = 0L
)

/**
 * 消息通知云端仓库：追番更新提醒（客户端检测到新剧集后上报）与评论回复提醒（服务端生成）。
 */
class NotificationRepository(context: Context) {
    private val client = NetworkClient.create(context).newBuilder().cache(null).build()
    private val storage = StorageManager(context)

    private fun authBuilder(): Request.Builder? {
        val token = storage.getAuthToken()
        if (token.isBlank()) return null
        return Request.Builder().header("Authorization", "Bearer $token")
    }

    suspend fun load(): List<CloudNotification>? = withContext(Dispatchers.IO) {
        try {
            val builder = authBuilder() ?: return@withContext emptyList()
            val request = builder
                .url(API_BASE)
                .header("User-Agent", "juying Android")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val array = JSONObject(body).optJSONArray("notifications") ?: return@withContext emptyList()
                buildList {
                    for (index in 0 until array.length()) {
                        val obj = array.optJSONObject(index) ?: continue
                        add(
                            CloudNotification(
                                id = obj.optString("id"),
                                type = obj.optString("type").ifBlank { "favorite_update" },
                                title = obj.optString("title"),
                                body = obj.optString("body"),
                                mediaKey = obj.optString("mediaKey"),
                                episodeName = obj.optString("episodeName"),
                                commentId = obj.optString("commentId"),
                                mediaSnapshot = obj.optString("mediaSnapshot").ifBlank { "{}" },
                                read = obj.optBoolean("read", false),
                                ts = obj.optLong("ts", 0L)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 上报追番更新提醒；服务端按 (类型, 作品, 剧集) 去重。返回 null 表示成功 */
    suspend fun reportFavoriteUpdate(
        title: String,
        body: String,
        mediaKey: String,
        episodeName: String,
        mediaSnapshot: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val builder = authBuilder() ?: return@withContext "登录状态已失效，请重新登录"
            val payload = JSONObject()
                .put("type", "favorite_update")
                .put("title", title)
                .put("body", body)
                .put("mediaKey", mediaKey)
                .put("episodeName", episodeName)
                .put("mediaSnapshot", mediaSnapshot)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = builder
                .url(API_BASE)
                .header("User-Agent", "juying Android")
                .post(payload)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = runCatching {
                        JSONObject(response.body?.string().orEmpty()).optString("error")
                    }.getOrNull().orEmpty()
                    return@withContext error.ifBlank { "通知上报失败（HTTP ${response.code}）" }
                }
                null
            }
        } catch (error: Exception) {
            error.message ?: "网络异常，通知上报失败"
        }
    }

    suspend fun markAllRead(): Boolean = withContext(Dispatchers.IO) {
        try {
            val builder = authBuilder() ?: return@withContext false
            val request = builder
                .url("$API_BASE/read")
                .header("User-Agent", "juying Android")
                .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private val API_BASE = "${BuildConfig.ACCOUNT_API_BASE}/api/notifications"
    }
}
