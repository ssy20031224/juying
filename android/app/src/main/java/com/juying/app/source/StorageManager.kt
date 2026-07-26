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

    fun getHistory(): List<HistoryItem> {
        val json = prefs.getString("watch_history", "[]") ?: "[]"
        return try {
            gson.fromJson(json, object : TypeToken<List<HistoryItem>>() {}.type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun addHistory(item: SourceItem, episodeName: String, playUrl: String) {
        val list = getHistory().toMutableList()
        list.removeAll { it.item.id == item.id && it.item.sourceKey == item.sourceKey }
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
        return getFavorites().any { it.id == item.id && it.sourceKey == item.sourceKey }
    }

    fun toggleFavorite(item: SourceItem): Boolean {
        val list = getFavorites().toMutableList()
        val exists = list.any { it.id == item.id && it.sourceKey == item.sourceKey }
        if (exists) {
            list.removeAll { it.id == item.id && it.sourceKey == item.sourceKey }
        } else {
            list.add(0, item)
        }
        prefs.edit().putString("favorites", gson.toJson(list)).apply()
        return !exists
    }
}
