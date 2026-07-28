package com.juying.app.source

import android.content.Context
import com.google.gson.Gson
import com.juying.app.engine.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class AccountUser(
    val id: String,
    val email: String,
    val nickname: String,
)

data class AccountResult(
    val user: AccountUser? = null,
    val token: String? = null,
    val error: String? = null,
)

class AccountRepository(context: Context) {
    private val client = NetworkClient.create(context).newBuilder().cache(null).build()
    private val gson = Gson()

    suspend fun login(email: String, password: String): AccountResult =
        requestAuth("/api/auth/login", email, password, null)

    suspend fun register(email: String, password: String, nickname: String): AccountResult =
        requestAuth("/api/auth/register", email, password, nickname)

    suspend fun me(token: String): AccountResult = withContext(Dispatchers.IO) {
        request("/api/auth/me", token, "GET", null).let(::parseResult)
    }

    suspend fun logout(token: String) {
        withContext(Dispatchers.IO) {
            request("/api/auth/logout", token, "POST", "{}")
        }
    }

    suspend fun sync(token: String, favorites: List<SourceItem>, history: List<HistoryItem>) {
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
                    "sourceKey" to it.item.sourceKey.substringBefore(',').trim(),
                    "positionMs" to 0,
                    "durationMs" to 0,
                    "completed" to false,
                )
            }
            val payload = gson.toJson(mapOf("favorites" to favoriteJson, "progress" to progressJson))
            request("/api/sync", token, "POST", payload)
        }
    }

    private suspend fun requestAuth(path: String, email: String, password: String, nickname: String?): AccountResult =
        withContext(Dispatchers.IO) {
            val payload = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .apply { if (nickname != null) put("nickname", nickname.trim()) }
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
            if (!response.isSuccessful) throw IllegalStateException(JSONObject(text).optString("error", "请求失败"))
            return text
        }
    }

    private fun parseResult(jsonText: String): AccountResult {
        val json = JSONObject(jsonText)
        val userJson = json.optJSONObject("user") ?: return AccountResult(error = json.optString("error").ifBlank { null })
        return AccountResult(
            user = AccountUser(
                id = userJson.optString("id"),
                email = userJson.optString("email"),
                nickname = userJson.optString("nickname"),
            ),
            token = json.optString("token").ifBlank { null },
        )
    }

    companion object {
        private const val API_BASE = "https://www.lanerc.app"
    }
}
