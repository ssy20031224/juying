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
        val raw: List<HistoryItem> = try {
            gson.fromJson(json, object : TypeToken<List<HistoryItem>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
        val retentionDays = getHistoryRetentionDays()
        if (retentionDays <= 0) return raw
        val cutoff = System.currentTimeMillis() - retentionDays * 86400000L
        val filtered = raw.filter { it.timestamp >= cutoff }
        if (filtered.size != raw.size) {
            prefs.edit().putString("watch_history", gson.toJson(filtered)).apply()
        }
        return filtered
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

    fun removeHistoryBatch(items: List<SourceItem>) {
        if (items.isEmpty()) return
        val keys = items.map(::itemKey).toSet()
        val updated = getHistory().filterNot { itemKey(it.item) in keys }
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

    fun removeFavorites(items: List<SourceItem>) {
        if (items.isEmpty()) return
        val list = getFavorites().filterNot { favorite -> items.any { isMatch(it, favorite) } }
        prefs.edit().putString("favorites", gson.toJson(list)).apply()
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

    fun getThemePalette(): String = prefs.getString("theme_palette", "bubblegum") ?: "bubblegum"
    fun setThemePalette(id: String) { prefs.edit().putString("theme_palette", id).apply() }

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

    fun getAutoSwitchSource(): Boolean = prefs.getBoolean("auto_switch_source", true)
    fun setAutoSwitchSource(v: Boolean) { prefs.edit().putBoolean("auto_switch_source", v).apply() }

    fun getAutoPlayNext(): Boolean = prefs.getBoolean("auto_play_next", false)
    fun setAutoPlayNext(v: Boolean) { prefs.edit().putBoolean("auto_play_next", v).apply() }

    fun getDefaultSpeed(): Float = prefs.getFloat("default_speed", 1.0f)
    fun setDefaultSpeed(v: Float) { prefs.edit().putFloat("default_speed", v.coerceIn(0.25f, 4.0f)).apply() }

    fun getDownloadDir(): String = prefs.getString("download_dir", "") ?: ""
    fun setDownloadDir(dir: String) { prefs.edit().putString("download_dir", dir).apply() }

    fun getMaxConcurrentDownloads(): Int = prefs.getInt("max_concurrent_downloads", 3)
    fun setMaxConcurrentDownloads(n: Int) { prefs.edit().putInt("max_concurrent_downloads", n.coerceIn(1, 8)).apply() }

    fun getSegmentThreads(): Int = prefs.getInt("segment_threads", 4)
    fun setSegmentThreads(n: Int) { prefs.edit().putInt("segment_threads", n.coerceIn(1, 16)).apply() }

    fun getWifiOnly(): Boolean = prefs.getBoolean("wifi_only", false)
    fun setWifiOnly(v: Boolean) { prefs.edit().putBoolean("wifi_only", v).apply() }

    fun getDoubleTapSeekEnabled(): Boolean = prefs.getBoolean("double_tap_seek_enabled", false)
    fun setDoubleTapSeekEnabled(v: Boolean) { prefs.edit().putBoolean("double_tap_seek_enabled", v).apply() }

    fun getSkipIntroSec(): Int = prefs.getInt("skip_intro_sec", 0)
    fun setSkipIntroSec(s: Int) { prefs.edit().putInt("skip_intro_sec", s.coerceIn(0, 300)).apply() }

    fun getSkipOutroSec(): Int = prefs.getInt("skip_outro_sec", 0)
    fun setSkipOutroSec(s: Int) { prefs.edit().putInt("skip_outro_sec", s.coerceIn(0, 300)).apply() }

    fun getAutoFullscreenOnLoad(): Boolean = prefs.getBoolean("auto_fullscreen_on_load", false)
    fun setAutoFullscreenOnLoad(v: Boolean) { prefs.edit().putBoolean("auto_fullscreen_on_load", v).apply() }

    fun getAutoRotateLandscape(): Boolean = prefs.getBoolean("auto_rotate_landscape", true)
    fun setAutoRotateLandscape(v: Boolean) { prefs.edit().putBoolean("auto_rotate_landscape", v).apply() }

    fun getSearchThreads(): Int = prefs.getInt("search_threads", 5)
    fun setSearchThreads(n: Int) { prefs.edit().putInt("search_threads", n.coerceIn(1, 8)).apply() }

    fun getShowContinueWatching(): Boolean = prefs.getBoolean("show_continue_watching", true)
    fun setShowContinueWatching(v: Boolean) { prefs.edit().putBoolean("show_continue_watching", v).apply() }

    fun getHistoryRetentionDays(): Int = prefs.getInt("history_retention_days", 0)
    fun setHistoryRetentionDays(d: Int) { prefs.edit().putInt("history_retention_days", d.coerceIn(0, 365)).apply() }

    fun getAutoSwitchRoute(): Boolean = prefs.getBoolean("auto_switch_route", true)
    fun setAutoSwitchRoute(v: Boolean) { prefs.edit().putBoolean("auto_switch_route", v).apply() }

    fun getAutoSwitchKernel(): Boolean = prefs.getBoolean("auto_switch_kernel", false)
    fun setAutoSwitchKernel(v: Boolean) { prefs.edit().putBoolean("auto_switch_kernel", v).apply() }

    fun getAutoPip(): Boolean = prefs.getBoolean("auto_pip", false)
    fun setAutoPip(v: Boolean) { prefs.edit().putBoolean("auto_pip", v).apply() }

    fun getBackgroundPlayback(): Boolean = prefs.getBoolean("background_playback", false)
    fun setBackgroundPlayback(v: Boolean) { prefs.edit().putBoolean("background_playback", v).apply() }

    fun getPlayerResizeMode(): Int = prefs.getInt("player_resize_mode", 0)
    fun setPlayerResizeMode(mode: Int) { prefs.edit().putInt("player_resize_mode", mode).apply() }

    fun getDefaultEngine(): String = prefs.getString("default_engine", "ExoPlayer") ?: "ExoPlayer"
    fun setDefaultEngine(engine: String) { prefs.edit().putString("default_engine", engine).apply() }

    fun resetSettings() {
        val keys = listOf(
            "theme_mode", "theme_palette",
            "auto_switch_source", "auto_switch_route", "auto_switch_kernel", "auto_pip",
            "background_playback", "player_resize_mode", "default_engine",
            "auto_play_next", "default_speed", "long_press_speed", "custom_speed",
            "download_dir", "max_concurrent_downloads", "segment_threads", "wifi_only",
            "double_tap_seek_enabled", "skip_intro_sec", "skip_outro_sec",
            "auto_fullscreen_on_load", "auto_rotate_landscape",
            "search_threads", "show_continue_watching", "history_retention_days"
        )
        prefs.edit().apply { keys.forEach { remove(it) } }.apply()
    }
}
