package com.juying.app.source

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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

data class AccountUser(
    val id: String,
    val email: String,
    val nickname: String,
    val avatarUrl: String = "",
)

data class AccountResult(
    val user: AccountUser? = null,
    val token: String? = null,
    val error: String? = null,
)

data class AccountSyncResult(
    val favorites: List<SourceItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
)

class AccountRepository(context: Context) {
    private val client = NetworkClient.create(context).newBuilder().cache(null).build()
    private val gson = Gson()

    suspend fun login(email: String, password: String): AccountResult =
        requestAuth("/api/auth/login", email, password, null)

    suspend fun register(email: String, password: String, nickname: String, code: String): AccountResult =
        requestAuth("/api/auth/register", email, password, nickname, code)

    suspend fun requestCode(email: String, purpose: String) {
        withContext(Dispatchers.IO) {
            request("/api/auth/request-code", "", "POST", gson.toJson(mapOf("email" to email, "purpose" to purpose)))
        }
    }

    suspend fun me(token: String): AccountResult = withContext(Dispatchers.IO) {
        parseResult(request("/api/auth/me", token, "GET", null))
    }

    suspend fun logout(token: String) {
        withContext(Dispatchers.IO) {
            request("/api/auth/logout", token, "POST", "{}")
        }
    }

    suspend fun changeEmail(token: String, email: String, code: String): AccountResult =
        withContext(Dispatchers.IO) {
            parseResult(
                request(
                    "/api/auth/change-email",
                    token,
                    "POST",
                    gson.toJson(mapOf("email" to email, "code" to code)),
                ),
            )
        }

    suspend fun changeNickname(token: String, nickname: String): AccountResult =
        withContext(Dispatchers.IO) {
            parseResult(
                request(
                    "/api/auth/nickname",
                    token,
                    "POST",
                    gson.toJson(mapOf("nickname" to nickname.trim())),
                ),
            )
        }

    suspend fun resetPassword(email: String, code: String, password: String, confirmPassword: String) {
        withContext(Dispatchers.IO) {
            request(
                "/api/auth/reset-password",
                "",
                "POST",
                gson.toJson(
                    mapOf(
                        "email" to email,
                        "code" to code,
                        "password" to password,
                        "confirmPassword" to confirmPassword,
                    ),
                ),
            )
        }
    }

    suspend fun uploadAvatar(token: String, file: File): AccountResult = withContext(Dispatchers.IO) {
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
            .url("$API_BASE/api/auth/avatar")
            .header("User-Agent", "juying Android")
            .header("Authorization", "Bearer $token")
            .post(multipart)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(parseError(text, response.code))
            parseResult(text)
        }
    }

    suspend fun pull(token: String): AccountSyncResult = withContext(Dispatchers.IO) {
        parseSync(request("/api/sync", token, "GET", null))
    }

    suspend fun sync(token: String, favorites: List<SourceItem>, history: List<HistoryItem>): AccountSyncResult =
        withContext(Dispatchers.IO) {
            val favoriteJson = favorites.map {
                mapOf(
                    "mediaKey" to "${it.sourceKey.substringBefore(',').trim()}:${it.id}",
                    "mediaSnapshot" to gson.toJson(it),
                )
            }
            val progressJson = history.map {
                mapOf(
                    "mediaKey" to "${it.item.sourceKey.substringBefore(',').trim()}:${it.item.id}",
                    "episodeKey" to it.episodeName,
                    "episodeName" to it.episodeName,
                    "mediaSnapshot" to gson.toJson(it.item),
                    "sourceKey" to it.item.sourceKey.substringBefore(',').trim(),
                    "positionMs" to 0,
                    "durationMs" to 0,
                    "completed" to false,
                )
            }
            val payload = gson.toJson(
                mapOf(
                    "favorites" to favoriteJson,
                    "progress" to progressJson,
                    "replaceFavorites" to true,
                    "replaceProgress" to true,
                ),
            )
            parseSync(request("/api/sync", token, "POST", payload))
        }

    private suspend fun requestAuth(
        path: String,
        email: String,
        password: String,
        nickname: String?,
        code: String? = null,
    ): AccountResult = withContext(Dispatchers.IO) {
        val payload = JSONObject()
            .put("email", email.trim())
            .put("password", password)
            .apply {
                if (nickname != null) put("nickname", nickname.trim())
                if (code != null) put("code", code.trim())
            }
            .toString()
        parseResult(request(path, "", "POST", payload))
    }

    private fun request(path: String, token: String, method: String, body: String?): String {
        val builder = Request.Builder().url("$API_BASE$path").header("User-Agent", "juying Android")
        if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
        if (method == "POST") {
            builder.post((body ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType()))
        } else {
            builder.get()
        }
        client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException(parseError(text, response.code))
            return text
        }
    }

    private fun parseResult(jsonText: String): AccountResult {
        val json = runCatching { JSONObject(jsonText) }
            .getOrElse { return AccountResult(error = "服务器返回了非 JSON 响应，请检查 API 地址或部署状态") }
        val userJson = json.optJSONObject("user")
            ?: return AccountResult(error = json.optString("error").ifBlank { "账号请求失败" })
        return AccountResult(
            user = AccountUser(
                id = userJson.optString("id"),
                email = userJson.optString("email"),
                nickname = userJson.optString("nickname"),
                avatarUrl = userJson.optString("avatarUrl"),
            ),
            token = json.optString("token").ifBlank { null },
        )
    }

    private fun parseSync(jsonText: String): AccountSyncResult {
        val json = JSONObject(jsonText)
        val remoteFavorites = gson.fromJson<List<Map<String, String>>>(
            json.optJSONArray("favorites")?.toString() ?: "[]",
            object : TypeToken<List<Map<String, String>>>() {}.type,
        ).mapNotNull { row ->
            runCatching { gson.fromJson(row["mediaSnapshot"] ?: "{}", SourceItem::class.java) }.getOrNull()
        }
        val remoteHistory = gson.fromJson<List<Map<String, Any>>>(
            json.optJSONArray("progress")?.toString() ?: "[]",
            object : TypeToken<List<Map<String, Any>>>() {}.type,
        ).mapNotNull { row ->
            val snapshot = row["mediaSnapshot"]?.toString() ?: return@mapNotNull null
            val item = runCatching { gson.fromJson(snapshot, SourceItem::class.java) }.getOrNull() ?: return@mapNotNull null
            HistoryItem(item, row["episodeName"]?.toString().orEmpty(), "", System.currentTimeMillis())
        }
        return AccountSyncResult(remoteFavorites, remoteHistory)
    }

    private fun parseError(text: String, status: Int): String {
        val jsonError = runCatching { JSONObject(text).optString("error") }.getOrNull().orEmpty()
        return jsonError.ifBlank {
            val trimmed = text.trimStart()
            if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) || trimmed.startsWith("<html", ignoreCase = true)) {
                "服务器返回了网页而不是 API 数据（HTTP $status），请检查域名或部署状态"
            } else {
                "请求失败（HTTP $status）"
            }
        }
    }

    companion object {
        private val API_BASE = BuildConfig.ACCOUNT_API_BASE
    }
}
