package com.juying.app.source

private const val DAY_MS = 24L * 60L * 60L * 1000L

fun historyPeriodLabel(
    timestamp: Long,
    nowMillis: Long = System.currentTimeMillis()
): String {
    val age = (nowMillis - timestamp).coerceAtLeast(0L)
    return when {
        age < 1 * DAY_MS -> "今天"
        age < 2 * DAY_MS -> "昨天"
        age < 3 * DAY_MS -> "前天"
        age < 7 * DAY_MS -> "近一周"
        else -> "更早"
    }
}

fun historyProgressPercent(positionMs: Long, durationMs: Long): Int {
    if (durationMs <= 0L) return 0
    return ((positionMs.coerceIn(0L, durationMs) * 100L) / durationMs)
        .toInt()
        .coerceIn(0, 100)
}

fun formatHistoryTimestamp(timestamp: Long): String {
    if (timestamp <= 0L) return "时间未知"
    return java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.CHINA).apply {
        timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
    }.format(java.util.Date(timestamp))
}
