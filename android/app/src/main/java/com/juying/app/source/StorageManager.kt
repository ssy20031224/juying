package com.juying.app.source

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken

data class HistoryItem(
    val item: SourceItem,
    val episodeName: String,
    val playUrl: String,
    val timestamp: Long = System.currentTimeMillis(),
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val deviceName: String = ""
)

data class SearchHistoryEntry(
    val query: String,
    val count: Int = 1,
    val lastSearchedAt: Long = System.currentTimeMillis()
) {
    val isFrequent: Boolean
        get() = count >= 3
}

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

    fun addHistory(
        item: SourceItem,
        episodeName: String,
        positionMs: Long = 0L,
        durationMs: Long = 0L
    ) {
        val list = getHistory().toMutableList()
        val key = itemKey(item)
        val previous = list.firstOrNull { itemKey(it.item) == key }
        list.removeAll { itemKey(it.item) == key }
        // History only needs the stable work/episode identity. Never persist a
        // temporary or signed playback URL; it may expire and must be resolved
        // again when the user reopens the record.
        val keepPreviousProgress = previous?.episodeName == episodeName && positionMs <= 0L
        list.add(
            0,
            HistoryItem(
                item = item,
                episodeName = episodeName,
                playUrl = "",
                positionMs = if (keepPreviousProgress) previous?.positionMs ?: 0L else positionMs.coerceAtLeast(0L),
                durationMs = if (keepPreviousProgress) previous?.durationMs ?: 0L else durationMs.coerceAtLeast(0L)
            )
        )
        if (list.size > 50) list.removeAt(list.size - 1)
        prefs.edit().putString("watch_history", gson.toJson(list)).apply()
    }

    fun removeHistory(item: SourceItem) {
        val key = itemKey(item)
        val updated = getHistory().filterNot { itemKey(it.item) == key }
        prefs.edit().putString("watch_history", gson.toJson(updated)).apply()
    }

    fun clearHistory() {
        prefs.edit().remove("watch_history").apply()
    }

    fun getSearchHistoryEntries(): List<SearchHistoryEntry> {
        val json = prefs.getString("search_history", "[]") ?: "[]"
        return try {
            val array = JsonParser.parseString(json).asJsonArray
            array.mapNotNull { element ->
                when {
                    element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                        element.asString.trim().takeIf(String::isNotBlank)?.let {
                            SearchHistoryEntry(query = it)
                        }
                    }
                    element.isJsonObject -> runCatching {
                        gson.fromJson(element, SearchHistoryEntry::class.java)
                    }.getOrNull()?.takeIf { it.query.isNotBlank() }
                    else -> null
                }
            }
                .sortedByDescending(SearchHistoryEntry::lastSearchedAt)
                .take(20)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getSearchHistory(): List<String> = getSearchHistoryEntries().map(SearchHistoryEntry::query)

    fun addSearchHistory(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        val existing = getSearchHistoryEntries().firstOrNull {
            it.query.equals(normalized, ignoreCase = true)
        }
        val updated = getSearchHistoryEntries()
            .filterNot { it.query.equals(normalized, ignoreCase = true) }
            .toMutableList()
            .apply {
                add(
                    0,
                    SearchHistoryEntry(
                        query = existing?.query ?: normalized,
                        count = (existing?.count ?: 0) + 1,
                        lastSearchedAt = System.currentTimeMillis()
                    )
                )
            }
            .take(20)
        prefs.edit().putString("search_history", gson.toJson(updated)).apply()
    }

    fun removeSearchHistory(query: String) {
        val updated = getSearchHistoryEntries().filterNot { it.query.equals(query, ignoreCase = true) }
        prefs.edit().putString("search_history", gson.toJson(updated)).apply()
    }

    fun clearSearchHistory() {
        prefs.edit().remove("search_history").apply()
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

    // 收藏时记录番剧集数作为提醒基线：之后每更新一集才提醒一次
    private fun getFavoriteBaselines(): MutableMap<String, Int> {
        val json = prefs.getString("fav_episode_baselines", "{}") ?: "{}"
        return try {
            gson.fromJson(json, object : TypeToken<MutableMap<String, Int>>() {}.type) ?: mutableMapOf()
        } catch (_: Exception) { mutableMapOf() }
    }

    private fun saveFavoriteBaselines(map: MutableMap<String, Int>) {
        prefs.edit().putString("fav_episode_baselines", gson.toJson(map)).apply()
    }

    fun getFavoriteBaseline(mediaKey: String): Int? = getFavoriteBaselines()[mediaKey]

    fun setFavoriteBaseline(mediaKey: String, episodeNum: Int) {
        val map = getFavoriteBaselines()
        map[mediaKey] = episodeNum
        saveFavoriteBaselines(map)
    }

    fun removeFavoriteBaseline(mediaKey: String) {
        val map = getFavoriteBaselines()
        if (map.remove(mediaKey) != null) saveFavoriteBaselines(map)
    }

    fun mergeCloudData(remoteFavorites: List<SourceItem>, remoteHistory: List<HistoryItem>) {
        val mergedFavorites = getFavorites().toMutableList()
        // 云端收藏已按收藏时间倒序返回，新同步的插入头部，保持"最新在前"
        remoteFavorites.forEach { remote ->
            if (mergedFavorites.none { isMatch(it, remote) }) mergedFavorites.add(0, remote)
        }
        val mergedHistory = getHistory().toMutableList()
        remoteHistory.forEach { remote ->
            val index = mergedHistory.indexOfFirst {
                itemKey(it.item) == itemKey(remote.item) && it.episodeName == remote.episodeName
            }
            if (index >= 0) {
                // 两端都有记录时保留观看时间较新的一条，避免旧记录覆盖新进度
                if (remote.timestamp >= mergedHistory[index].timestamp) mergedHistory[index] = remote
            } else {
                mergedHistory.add(remote)
            }
        }
        // 历史记录按观看时间倒序，"最新看的在最上方"
        mergedHistory.sortByDescending { it.timestamp }
        prefs.edit()
            .putString("favorites", gson.toJson(mergedFavorites.take(200)))
            .putString("watch_history", gson.toJson(mergedHistory.take(50)))
            .apply()
    }

    fun getThemeMode(): String = prefs.getString("theme_mode", "dark") ?: "dark"
    fun setThemeMode(mode: String) { prefs.edit().putString("theme_mode", mode).apply() }

    fun getUserEmail(): String = prefs.getString("user_email", "") ?: ""
    fun setUserEmail(email: String) { prefs.edit().putString("user_email", email).apply() }

    fun getUserAvatar(): Int = prefs.getInt("user_avatar", 0)
    fun setUserAvatar(avatarIndex: Int) { prefs.edit().putInt("user_avatar", avatarIndex).apply() }

    fun getAuthToken(): String = prefs.getString("auth_token", "") ?: ""
    fun setAuthToken(token: String) { prefs.edit().putString("auth_token", token).apply() }
    fun clearAuthToken() { prefs.edit().remove("auth_token").apply() }

    fun getAccountNickname(): String = prefs.getString("account_nickname", "") ?: ""
    fun setAccountNickname(nickname: String) { prefs.edit().putString("account_nickname", nickname).apply() }

    fun announcementDismissedUntil(id: String): Long =
        prefs.getLong("announcement_dismissed_until_$id", 0L)

    fun dismissAnnouncementUntil(id: String, until: Long) {
        prefs.edit().putLong("announcement_dismissed_until_$id", until).apply()
    }

    fun getLongPressSpeed(): Float = prefs.getFloat("long_press_speed", 3.0f)
    fun setLongPressSpeed(speed: Float) { prefs.edit().putFloat("long_press_speed", speed.coerceIn(0.25f, 4.0f)).apply() }

    fun getCustomSpeed(): Float = prefs.getFloat("custom_speed", 1.0f)
    fun setCustomSpeed(speed: Float) { prefs.edit().putFloat("custom_speed", speed.coerceIn(0.25f, 4.0f)).apply() }
}
