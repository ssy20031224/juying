package com.juying.app.source

import android.content.Context
import com.juying.app.BuildConfig
import com.juying.app.engine.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File

data class CloudComment(
    val id: String = "",
    val userId: String = "",
    val nick: String = "",
    val text: String = "",
    val ts: Long = 0L,
    val avatarUrl: String = "",
    val imageUrl: String = "",
    val parentId: String? = null,
    val replyToNick: String? = null,
    val likesCount: Int = 0,
    val likedByMe: Boolean = false,
    val replies: List<CloudComment> = emptyList()
)

data class CommentPostResult(
    val comments: List<CloudComment>? = null,
    val error: String? = null,
)

data class CommentImageResult(
    val url: String? = null,
    val error: String? = null,
)

/**
 * 评论云端仓库：读写都经过平台 API（/api/comments），
 * 支持楼中楼回复 (parentId, replyToNick)、评论删除 (delete)、点赞 (like) 以及 OSS 头像完整解析。
 */
class CommentRepository(context: Context) {
    private val client = NetworkClient.create(context).newBuilder().cache(null).build()
    private val storage = StorageManager(context)

    suspend fun load(mediaKey: String): List<CloudComment>? = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder()
                .url("$API_BASE?media=$mediaKey")
                .header("User-Agent", "juying Android")
                .header("Cache-Control", "no-cache")
                .get()
            storage.getAuthToken().takeIf { it.isNotBlank() }?.let {
                builder.header("Authorization", "Bearer $it")
            }
            val request = builder.build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                parseComments(JSONObject(body))
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun post(
        mediaKey: String,
        nick: String,
        text: String,
        avatarUrl: String = "",
        parentId: String? = null,
        replyToNick: String? = null,
        imageUrl: String = ""
    ): CommentPostResult = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject()
                .put("media", mediaKey)
                .put("nick", nick)
                .put("text", text)
                .apply {
                    if (avatarUrl.isNotBlank()) {
                        put("avatarUrl", avatarUrl)
                        put("avatar", avatarUrl)
                        put("avatar_url", avatarUrl)
                    }
                    if (imageUrl.isNotBlank()) {
                        put("imageUrl", imageUrl)
                        put("image_url", imageUrl)
                    }
                    if (!parentId.isNullOrBlank()) {
                        put("parentId", parentId)
                        put("parent_id", parentId)
                    }
                    if (!replyToNick.isNullOrBlank()) {
                        put("replyToNick", replyToNick)
                        put("reply_to_nick", replyToNick)
                    }
                }
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
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val error = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
                    return@withContext CommentPostResult(
                        error = when (error) {
                            "authentication required" -> "登录状态已失效，请重新登录"
                            "comment posting is temporarily disabled" -> "评论发布正在维护中"
                            else -> error.ifBlank { "评论发布失败（HTTP ${response.code}）" }
                        },
                    )
                }
                if (body.isBlank()) return@withContext CommentPostResult(error = "评论服务返回了空响应")
                CommentPostResult(comments = parseComments(JSONObject(body)))
            }
        } catch (error: Exception) {
            CommentPostResult(error = error.message ?: "网络异常，评论发布失败")
        }
    }

    /** 上传评论图片到 OSS，返回公开可访问的 URL */
    suspend fun uploadImage(file: File): CommentImageResult = withContext(Dispatchers.IO) {
        try {
            val token = storage.getAuthToken()
            if (token.isBlank()) return@withContext CommentImageResult(error = "请先“登录”后上传图片")
            val mime = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "webp" -> "image/webp"
                "gif" -> "image/gif"
                else -> "image/jpeg"
            }
            val multipart = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody(mime.toMediaType()))
                .build()
            val request = Request.Builder()
                .url("$API_BASE/images")
                .header("User-Agent", "juying Android")
                .header("Authorization", "Bearer $token")
                .post(multipart)
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val error = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
                    return@withContext CommentImageResult(error = error.ifBlank { "图片上传失败（HTTP ${response.code}）" })
                }
                val url = runCatching { JSONObject(body).optString("url") }.getOrNull().orEmpty()
                if (url.isBlank()) return@withContext CommentImageResult(error = "图片上传服务返回了空响应")
                CommentImageResult(url = url)
            }
        } catch (error: Exception) {
            CommentImageResult(error = error.message ?: "网络异常，图片上传失败")
        }
    }

    suspend fun delete(mediaKey: String, commentId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = storage.getAuthToken()
            if (token.isBlank()) return@withContext false
            val payload = JSONObject()
                .put("media", mediaKey)
                .put("id", commentId)
                .put("commentId", commentId)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$API_BASE/delete")
                .header("User-Agent", "juying Android")
                .header("Authorization", "Bearer $token")
                .post(payload)
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun like(mediaKey: String, commentId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val token = storage.getAuthToken()
            val payload = JSONObject()
                .put("media", mediaKey)
                .put("id", commentId)
                .put("commentId", commentId)
                .toString()
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            val builder = Request.Builder()
                .url("$API_BASE/like")
                .header("User-Agent", "juying Android")
                .post(payload)
            if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
            client.newCall(builder.build()).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseComments(json: JSONObject): List<CloudComment> {
        val array = json.optJSONArray("comments") ?: return emptyList()
        val allComments = mutableListOf<CloudComment>()

        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            val text = obj.optString("text").trim()
            if (text.isEmpty()) continue

            val userObj = obj.optJSONObject("user")
            val avatarUrl = obj.optString("avatarUrl")
                .ifBlank { obj.optString("avatar") }
                .ifBlank { obj.optString("avatar_url") }
                .ifBlank { obj.optString("user_avatar") }
                .ifBlank { userObj?.optString("avatarUrl").orEmpty() }
                .ifBlank { userObj?.optString("avatar_url").orEmpty() }
                .ifBlank { userObj?.optString("avatar").orEmpty() }

            // org.json 对 JSON null 返回字符串 "null"，需要显式过滤
            val imageUrl = obj.optString("imageUrl")
                .ifBlank { obj.optString("image_url") }
                .takeIf { it.isNotBlank() && it != "null" }
                .orEmpty()

            val id = obj.optString("id").ifBlank { obj.optString("_id") }.ifBlank { "${obj.optString("nick")}_${obj.optLong("ts")}" }
            val userId = obj.optString("userId").ifBlank { obj.optString("user_id") }
            // Android org.json 的 optString 对 JSON null 会返回字符串 "null"，必须显式过滤
            val parentId = obj.optString("parentId").takeIf { it.isNotBlank() && it != "null" }
                ?: obj.optString("parent_id").takeIf { it.isNotBlank() && it != "null" }
            val replyToNick = obj.optString("replyToNick").takeIf { it.isNotBlank() && it != "null" }
                ?: obj.optString("reply_to_nick").takeIf { it.isNotBlank() && it != "null" }
            val likes = obj.optInt("likesCount", obj.optInt("likes_count", obj.optInt("likes", 0)))
            val liked = obj.optBoolean("likedByMe", obj.optBoolean("liked_by_me", false))

            allComments += CloudComment(
                id = id,
                userId = userId,
                nick = obj.optString("nick").ifBlank { userObj?.optString("nickname").orEmpty() }.ifBlank { "漫友" },
                text = text,
                ts = obj.optLong("ts", 0L),
                avatarUrl = avatarUrl,
                imageUrl = imageUrl,
                parentId = parentId,
                replyToNick = replyToNick,
                likesCount = likes,
                likedByMe = liked
            )
        }

        // Organize flat list into top-level comments and nested replies (楼中楼)
        val parents = allComments.filter { it.parentId.isNullOrBlank() }
        val repliesMap = allComments.filter { !it.parentId.isNullOrBlank() }.groupBy { it.parentId }

        return parents.map { parent ->
            parent.copy(replies = repliesMap[parent.id] ?: emptyList())
        }
    }

    companion object {
        private val API_BASE = "${BuildConfig.ACCOUNT_API_BASE}/api/comments"
    }
}
