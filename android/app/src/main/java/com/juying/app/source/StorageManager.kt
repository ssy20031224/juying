package com.juying.app.source

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class HistoryItem(
    val item: SourceItem,
    val episodeName: String,
    val playUrl: String,
    val timestamp: Long = System.currentTimeMillis()
)

class StorageManager(context: Context) {
    private val prefs = context.getSharedPreferences("juying_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private fun itemKey(item: SourceItem): String {
        // Older merged cards may have stored "sourceA,sourceB". Keep the
        // first executable source as the stable identity for history/favorites.
        val source = item.sourceKey.substringBefore(',').trim()
        return "$source\u0000${item.id}"
    }

    fun getHistory(): List<HistoryItem> {
        val json = prefs.getString("watch_history", "[]") ?: "[]"
        return try {
            gson.fromJson(json, object : TypeToken<List<HistoryItem>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun addHistory(item: SourceItem, episodeName: String) {
        val list = getHistory().toMutableList()
        val key = itemKey(item)
        list.removeAll { itemKey(it.item) == key }
        // History only needs the stable work/episode identity. Never persist a
        // temporary or signed playback URL; it may expire and must be resolved
        // again when the user reopens the record.
        list.add(0, HistoryItem(item, episodeName, playUrl = ""))
        if (list.size > 50) list.removeAt(list.size - 1)
        prefs.edit().putString("watch_history", gson.toJson(list)).apply()
    }

    fun clearHistory() {
        prefs.edit().remove("watch_history").apply()
    }

    // 评论昵称：每台设备稳定一个，用于云端评论署名
    fun getCommentNick(): String {
        val existing = prefs.getString("comment_nick", null)
        if (!existing.isNullOrBlank()) return existing
        val nick = "漫友_${(1000..9999).random()}"
        prefs.edit().putString("comment_nick", nick).apply()
        return nick
    }

    fun setCommentNick(nick: String) {
        prefs.edit().putString("comment_nick", nick.trim()).apply()
    }

    fun getFavorites(): List<SourceItem> {
        val json = prefs.getString("favorites", "[]") ?: "[]"
        return try {
            gson.fromJson(json, object : TypeToken<List<SourceItem>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    private fun isMatch(a: SourceItem, b: SourceItem): Boolean {
        if (itemKey(a) == itemKey(b)) return true
        val normA = SourceManager.normalizeTitle(a.title)
        val normB = SourceManager.normalizeTitle(b.title)
        return normA.isNotBlank() && normA == normB
    }

    fun isFavorite(item: SourceItem): Boolean {
        return getFavorites().any { isMatch(it, item) }
    }

    fun toggleFavorite(item: SourceItem): Boolean {
        val list = getFavorites().toMutableList()
        val exists = list.any { isMatch(it, item) }
        if (exists) {
            list.removeAll { isMatch(it, item) }
        } else {
            list.add(0, item)
        }
        prefs.edit().putString("favorites", gson.toJson(list)).apply()
        return !exists
    }

    fun mergeCloudData(remoteFavorites: List<SourceItem>, remoteHistory: List<HistoryItem>) {
        val mergedFavorites = getFavorites().toMutableList()
        remoteFavorites.forEach { remote ->
            if (mergedFavorites.none { isMatch(it, remote) }) mergedFavorites.add(remote)
        }
        val mergedHistory = getHistory().toMutableList()
        remoteHistory.forEach { remote ->
            val index = mergedHistory.indexOfFirst {
                itemKey(it.item) == itemKey(remote.item) && it.episodeName == remote.episodeName
            }
            if (index >= 0) mergedHistory[index] = remote else mergedHistory.add(remote)
        }
        prefs.edit()
            .putString("favorites", gson.toJson(mergedFavorites.take(200)))
            .putString("watch_history", gson.toJson(mergedHistory.take(50)))
            .apply()
    }

    fun getThemeMode(): String = prefs.getString("theme_mode", "dark") ?: "dark"
    fun setThemeMode(mode: String) { prefs.edit().putString("theme_mode", mode).apply() }

    fun getUserEmail(): String = prefs.getString("user_email", "user@juying.com") ?: "user@juying.com"
    fun setUserEmail(email: String) { prefs.edit().putString("user_email", email).apply() }

    fun getUserPassword(): String = prefs.getString("user_password", "Pass1234!") ?: "Pass1234!"
    fun setUserPassword(password: String) { prefs.edit().putString("user_password", password).apply() }

    fun getUserAvatar(): Int = prefs.getInt("user_avatar", 0)
    fun setUserAvatar(avatarIndex: Int) { prefs.edit().putInt("user_avatar", avatarIndex).apply() }

    fun getAuthToken(): String = prefs.getString("auth_token", "") ?: ""
    fun setAuthToken(token: String) { prefs.edit().putString("auth_token", token).apply() }
    fun clearAuthToken() { prefs.edit().remove("auth_token").apply() }

    fun getAccountNickname(): String = prefs.getString("account_nickname", "") ?: ""
    fun setAccountNickname(nickname: String) { prefs.edit().putString("account_nickname", nickname).apply() }
}
