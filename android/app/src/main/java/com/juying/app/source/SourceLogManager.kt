package com.juying.app.source

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

enum class LogLevel { ERROR, WARN, SUCCESS, INFO }

data class SourceLogEntry(
    val sourceKey: String,
    val title: String,          // operation category
    val message: String,        // summary
    val level: LogLevel,
    val timestamp: Long = System.currentTimeMillis(),
    val details: String = ""    // truncated response or stack trace
)

object SourceLogManager {
    private const val MAX_ENTRIES = 500
    private val gson = Gson()
    
    @Volatile
    var logs: List<SourceLogEntry> = emptyList()
        private set
    
    private var ctx: Context? = null
    private var persistFile: File? = null

    fun init(context: Context) {
        ctx = context.applicationContext
        persistFile = File(context.filesDir, "source_logs.json")
        loadFromDisk()
        if (persistFile?.exists() != true) persist()
    }

    fun info(sourceKey: String, title: String, message: String) {
        add(SourceLogEntry(sourceKey, title, message, LogLevel.INFO))
    }

    fun warn(sourceKey: String, title: String, message: String) {
        add(SourceLogEntry(sourceKey, title, message, LogLevel.WARN))
    }

    fun error(sourceKey: String, title: String, message: String, details: String = "") {
        add(SourceLogEntry(sourceKey, title, message, LogLevel.ERROR, details = details))
    }

    fun success(sourceKey: String, title: String, message: String, details: String = "") {
        add(SourceLogEntry(sourceKey, title, message, LogLevel.SUCCESS, details = details))
    }

    @Synchronized
    fun getLogs(sourceKey: String? = null, level: LogLevel? = null): List<SourceLogEntry> {
        return logs
            .filter { sourceKey == null || it.sourceKey == sourceKey }
            .filter { level == null || it.level == level }
    }

    @Synchronized
    fun getErrors(): List<SourceLogEntry> {
        return logs.filter { it.level == LogLevel.ERROR }
    }

    @Synchronized
    fun clear() {
        logs = emptyList()
        persist()
    }

    @Synchronized
    fun clearLogs(sourceKey: String? = null) {
        if (sourceKey != null) {
            logs = logs.filter { it.sourceKey != sourceKey }
        } else {
            logs = emptyList()
        }
        persist()
    }

    @Synchronized
    private fun add(entry: SourceLogEntry) {
        val newList = mutableListOf(entry)
        newList.addAll(logs.take(MAX_ENTRIES - 1))
        logs = newList
        // Failures are diagnostic evidence; persist them immediately so a
        // process kill after a CDN/API error does not erase the only clue.
        if (entry.level == LogLevel.ERROR || logs.size % 10 == 0) persist()
    }

    private fun persist() {
        try {
            persistFile?.writeText(gson.toJson(logs.take(200)))
        } catch (_: Exception) {}
    }

    private fun loadFromDisk() {
        try {
            val file = persistFile
            if (file != null && file.exists()) {
                val json = file.readText()
                val loaded: List<SourceLogEntry> = gson.fromJson(json, object : TypeToken<List<SourceLogEntry>>() {}.type) ?: emptyList()
                if (loaded.isNotEmpty()) logs = loaded.take(MAX_ENTRIES)
            }
        } catch (_: Exception) {}
    }
}
