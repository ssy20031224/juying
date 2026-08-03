package com.juying.app.source

import android.content.Context
import android.net.Uri
import com.juying.app.BuildConfig
import com.juying.app.engine.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class CloudDanmaku(
    val id: String = "",
    val text: String = "",
    val color: String = "#FFFFFFFF",
    val positionMs: Long = 0L,
    val ts: Long = 0L
)

/**
 * 弹幕云端仓库：按「作品 + 剧集 + 时间点」读写，后续观看同一集时回放历史弹幕。
 */
class DanmakuRepository(context: Context) {
    private val client = NetworkClient.create(context).newBuilder().cache(null).build()
    private val storage = StorageManager(context)

    suspend fun load(mediaKey: String, episodeKey: String): List<CloudDanmaku>? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$API_BASE?media=${Uri.encode(mediaKey)}&episode=${Uri.encode(episodeKey)}"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "juying Android")
                    .header("Cache-Control", "no-cache")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val body = response.body?.string() ?: return@withContext null
                    val array = JSONObject(body).optJSONArray("danmakus") ?: return@withContext emptyList()
                    buildList {
                        for (index in 0 until array.length()) {
                            val obj = array.optJSONObject(index) ?: continue
                            val text = obj.optString("text").trim()
                            if (text.isEmpty()) continue
                            add(
                                CloudDanmaku(
                                    id = obj.optString("id"),
                                    text = text,
                                    color = obj.optString("color").ifBlank { "#FFFFFFFF" },
                                    positionMs = obj.optLong("positionMs", 0L),
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

    /** 返回 null 表示成功，否则为错误提示文案 */
    suspend fun post(
        mediaKey: String,
        episodeKey: String,
        positionMs: Long,
        text: String,
        color: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject()
                .put("media", mediaKey)
                .put("episode", episodeKey)
                .put("positionMs", positionMs)
                .put("text", text)
                .put("color", color)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val builder = Request.Builder()
                .url(API_BASE)
                .header("User-Agent", "juying Android")
                .post(payload)
            storage.getAuthToken().takeIf { it.isNotBlank() }?.let {
                builder.header("Authorization", "Bearer $it")
            }
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    val error = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
                    return@withContext when (error) {
                        "authentication required" -> "登录状态已失效，请重新登录"
                        else -> error.ifBlank { "弹幕发送失败（HTTP ${response.code}）" }
                    }
                }
                null
            }
        } catch (error: Exception) {
            error.message ?: "网络异常，弹幕发送失败"
        }
    }

    companion object {
        private val API_BASE = "${BuildConfig.ACCOUNT_API_BASE}/api/danmakus"
    }
}
