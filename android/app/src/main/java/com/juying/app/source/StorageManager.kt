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

    fun addHistory(item: SourceItem, episodeName: String, playUrl: String) {
        val list = getHistory().toMutableList()
        val key = itemKey(item)
        list.removeAll { itemKey(it.item) == key }
        list.add(0, HistoryItem(item, episodeName, playUrl))
        if (list.size > 50) list.removeAt(list.size - 1)
        prefs.edit().putString("watch_history", gson.toJson(list)).apply()
    }

    fun clearHistory() {
        prefs.edit().remove("watch_history").apply()
    }

    fun getFavorites(): List<SourceItem> {
        val json = prefs.getString("favorites", "[]") ?: "[]"
        return try {
            gson.fromJson(json, object : TypeToken<List<SourceItem>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun isFavorite(item: SourceItem): Boolean {
        val key = itemKey(item)
        return getFavorites().any { itemKey(it) == key }
    }

    fun toggleFavorite(item: SourceItem): Boolean {
        val list = getFavorites().toMutableList()
        val key = itemKey(item)
        val exists = list.any { itemKey(it) == key }
        if (exists) {
            list.removeAll { itemKey(it) == key }
        } else {
            list.add(0, item)
        }
        prefs.edit().putString("favorites", gson.toJson(list)).apply()
        return !exists
    }

    fun getThemeMode(): String = prefs.getString("theme_mode", "dark") ?: "dark"
    fun setThemeMode(mode: String) { prefs.edit().putString("theme_mode", mode).apply() }

    fun getUserEmail(): String = prefs.getString("user_email", "user@juying.com") ?: "user@juying.com"
    fun setUserEmail(email: String) { prefs.edit().putString("user_email", email).apply() }

    fun getUserPassword(): String = prefs.getString("user_password", "Pass1234!") ?: "Pass1234!"
    fun setUserPassword(password: String) { prefs.edit().putString("user_password", password).apply() }

    fun getUserAvatar(): Int = prefs.getInt("user_avatar", 0)
    fun setUserAvatar(avatarIndex: Int) { prefs.edit().putInt("user_avatar", avatarIndex).apply() }
}

