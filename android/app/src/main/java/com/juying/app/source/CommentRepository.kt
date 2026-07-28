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

data class CloudComment(
    val nick: String,
    val text: String,
    val ts: Long = 0L
)

/**
 * 评论云端仓库：读写都经过平台 API（/api/comments），
 * 平台服务端持有阿里云 OSS 密钥并落盘 comments/<mediaKey>.json，密钥不进入客户端。
 * 网络或接口失败时返回 null，由调用方回退本地处理（来源失败隔离，不影响播放）。
 */
class CommentRepository(context: Context) {
    // 评论需要实时性，单独关闭磁盘缓存
    private val client = NetworkClient.create(context).newBuilder().cache(null).build()
    private val storage = StorageManager(context)

    suspend fun load(mediaKey: String): List<CloudComment>? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$API_BASE?media=$mediaKey")
                .header("User-Agent", "juying Android")
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                parseComments(JSONObject(body))
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun post(mediaKey: String, nick: String, text: String): List<CloudComment>? = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject()
                .put("media", mediaKey)
                .put("nick", nick)
                .put("text", text)
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
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                parseComments(JSONObject(body))
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun parseComments(json: JSONObject): List<CloudComment> {
        val array = json.optJSONArray("comments") ?: return emptyList()
        val list = mutableListOf<CloudComment>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val text = obj.optString("text").trim()
            if (text.isEmpty()) continue
            list += CloudComment(
                nick = obj.optString("nick").ifBlank { "漫友" },
                text = text,
                ts = obj.optLong("ts", 0L)
            )
        }
        return list
    }

    companion object {
        private val API_BASE = "${BuildConfig.ACCOUNT_API_BASE}/api/comments"
    }
}
