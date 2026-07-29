package com.juying.app.source

enum class RankingKind(val label: String) {
    HOT("热门"),
    POPULARITY("人气"),
    SCORE("评分")
}

data class SourceRankingEntry(
    val item: SourceItem,
    val sourceSection: String,
    val sourcePosition: Int
)

data class ScheduleEntry(
    val item: SourceItem,
    val weekdayIndex: Int,
    val airTime: String?,
    val episodeText: String
)

private val weekdayTokens = mapOf(
    "一" to 0, "1" to 0, "月" to 0,
    "二" to 1, "2" to 1, "火" to 1,
    "三" to 2, "3" to 2, "水" to 2,
    "四" to 3, "4" to 3, "木" to 3,
    "五" to 4, "5" to 4, "金" to 4,
    "六" to 5, "6" to 5, "土" to 5,
    "日" to 6, "天" to 6, "7" to 6
)

private val chineseWeekdayRegex = Regex("(?:周|星期|礼拜)\\s*([一二三四五六日天1-7])")
private val japaneseWeekdayRegex = Regex("([月火水木金土日])曜")
private val timeRegex = Regex("(?<!\\d)([01]?\\d|2[0-3]):([0-5]\\d)(?!\\d)")

fun buildScheduleEntries(items: List<SourceItem>): List<ScheduleEntry> {
    return items.asSequence()
        .mapNotNull { item ->
            val evidence = buildString {
                append(item.status)
                append(' ')
                append(item.tags.joinToString(" "))
            }
            val weekdayToken = chineseWeekdayRegex.find(evidence)?.groupValues?.getOrNull(1)
                ?: japaneseWeekdayRegex.find(evidence)?.groupValues?.getOrNull(1)
                ?: return@mapNotNull null
            val weekdayIndex = weekdayTokens[weekdayToken] ?: return@mapNotNull null
            val status = resolveMediaStatus(item)
            if (status.state == MediaReleaseState.FINISHED) return@mapNotNull null
            ScheduleEntry(
                item = item,
                weekdayIndex = weekdayIndex,
                airTime = timeRegex.find(evidence)?.value,
                episodeText = status.episodeText
            )
        }
        .distinctBy { normalizeDiscoveryTitle(it.item.title) }
        .sortedWith(compareBy<ScheduleEntry> { it.weekdayIndex }.thenBy { it.airTime ?: "99:99" })
        .toList()
}

fun buildRankingEntries(
    sections: List<HomeSection>,
    allItems: List<SourceItem>,
    kind: RankingKind
): List<SourceRankingEntry> {
    if (kind == RankingKind.SCORE) {
        return allItems.asSequence()
            .mapNotNull { item ->
                val score = item.score.trim().toDoubleOrNull() ?: return@mapNotNull null
                if (score <= 0.0) return@mapNotNull null
                Triple(item, score, normalizeDiscoveryTitle(item.title))
            }
            .sortedByDescending { it.second }
            .distinctBy { it.third }
            .mapIndexed { index, value ->
                SourceRankingEntry(value.first, "来源评分", index + 1)
            }
            .toList()
    }

    val keywords = when (kind) {
        RankingKind.HOT -> listOf("热门", "热播", "排行榜", "排行")
        RankingKind.POPULARITY -> listOf("人气", "热度", "收藏", "追番")
        RankingKind.SCORE -> emptyList()
    }
    val seen = HashSet<String>()
    val result = ArrayList<SourceRankingEntry>()
    sections.forEach { section ->
        if (keywords.none { section.title.contains(it, ignoreCase = true) }) return@forEach
        section.items.forEachIndexed { index, item ->
            val key = normalizeDiscoveryTitle(item.title)
            if (key.isNotEmpty() && seen.add(key)) {
                result += SourceRankingEntry(item, section.title, index + 1)
            }
        }
    }
    return result
}

/**
 * Builds deterministic metadata-based recommendations from audited discovery
 * results. No network request occurs here, so opening a player can never wait
 * for the ten-minute discovery cache or its upstream service.
 */
fun buildDiscoveryRecommendations(
    seed: SourceItem,
    candidates: List<SourceItem>,
    limit: Int = 20
): List<SourceItem> {
    val seedTitle = normalizeDiscoveryTitle(seed.title)
    val seedTags = (seed.tags + seed.kind.split(Regex("\\s+")))
        .map(String::trim)
        .filter(String::isNotBlank)
        .toSet()

    return candidates.asSequence()
        .filter { normalizeDiscoveryTitle(it.title) != seedTitle }
        .map { candidate ->
            val candidateTags = (candidate.tags + candidate.kind.split(Regex("\\s+")))
                .map(String::trim)
                .filter(String::isNotBlank)
                .toSet()
            val overlap = seedTags.intersect(candidateTags).size
            val sameYear = seed.year.isNotBlank() && seed.year == candidate.year
            val realScore = candidate.score.toDoubleOrNull()?.takeIf { it > 0.0 } ?: 0.0
            val relevance = overlap * 100 + (if (sameYear) 10 else 0) + realScore
            candidate to relevance
        }
        .filter { seedTags.isEmpty() || it.second > 0.0 }
        .sortedWith(
            compareByDescending<Pair<SourceItem, Double>> { it.second }
                .thenBy { normalizeDiscoveryTitle(it.first.title) }
        )
        .distinctBy { normalizeDiscoveryTitle(it.first.title) }
        .take(limit.coerceAtLeast(0))
        .map { it.first }
        .toList()
}

internal fun normalizeDiscoveryTitle(value: String): String {
    return value.lowercase()
        .replace(Regex("[\\s·・:：!！?？,，.。\\-—_()（）\\[\\]【】]+"), "")
        .trim()
}
