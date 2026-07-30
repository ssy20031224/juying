package com.juying.app.source

enum class RankingKind(val label: String) {
    HOT("热门"),
    POPULARITY("人气"),
    SCORE("评分")
}

enum class AnimeRankingCategory(val label: String) {
    JAPANESE_TV("日漫TV番剧"),
    JAPANESE_MOVIE("日漫剧场版"),
    CHINESE_TV("国漫动画"),
    CHINESE_MOVIE("国漫剧场版")
}

data class SourceRankingEntry(
    val item: SourceItem,
    val sourceSection: String,
    val sourcePosition: Int
)

data class AnimeSeason(
    val year: Int,
    val month: Int
) {
    val label: String
        get() = "${year}年${month}月新番"
}

data class SeasonalRecommendation(
    val season: AnimeSeason,
    val entries: List<SourceRankingEntry>
)

data class ScheduleEntry(
    val item: SourceItem,
    val weekdayIndex: Int,
    val airTime: String?,
    val episodeText: String
)

/**
 * Returns the current and immediately previous Japanese anime cour that have
 * already started in the same Beijing-calendar year. Future quarters and the
 * previous year's October cour are deliberately excluded.
 */
fun beijingSeasonWindow(nowMillis: Long = System.currentTimeMillis()): List<AnimeSeason> {
    val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai"))
    calendar.timeInMillis = nowMillis
    val year = calendar.get(java.util.Calendar.YEAR)
    val currentMonth = calendar.get(java.util.Calendar.MONTH) + 1
    val currentSeasonMonth = listOf(1, 4, 7, 10).last { it <= currentMonth }
    return listOf(currentSeasonMonth, currentSeasonMonth - 3)
        .filter { it >= 1 }
        .map { AnimeSeason(year, it) }
}

internal fun seasonMonthFromLabel(value: String): Int? {
    val normalized = value
        .replace("一月", "1月")
        .replace("四月", "4月")
        .replace("七月", "7月")
        .replace("十月", "10月")
    return Regex("(?<!\\d)(1|4|7|10)\\s*月")
        .find(normalized)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
}

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
 * Splits source-backed ranking/recommendation order into anime catalog
 * categories. The function never manufactures a popularity score: ranked
 * entries keep their upstream order, followed by source section order.
 */
fun buildAnimeCategoryRanking(
    rankedEntries: List<SourceRankingEntry>,
    sections: List<HomeSection>,
    category: AnimeRankingCategory
): List<SourceRankingEntry> {
    val result = ArrayList<SourceRankingEntry>()
    val seen = HashSet<String>()

    fun append(entry: SourceRankingEntry) {
        val key = normalizeDiscoveryTitle(entry.item.title)
        if (
            key.isNotEmpty() &&
            matchesAnimeRankingCategory(entry.item, entry.sourceSection, category) &&
            seen.add(key)
        ) {
            result += entry
        }
    }

    rankedEntries.forEach(::append)
    sections.forEach { section ->
        section.items.forEachIndexed { index, item ->
            append(
                SourceRankingEntry(
                    item = item,
                    sourceSection = section.title,
                    sourcePosition = index + 1
                )
            )
        }
    }
    return result
}

internal fun matchesAnimeRankingCategory(
    item: SourceItem,
    sectionTitle: String,
    category: AnimeRankingCategory
): Boolean {
    val evidence = listOf(
        sectionTitle,
        item.kind,
        item.tags.joinToString(" "),
        item.sourceTitle
    ).joinToString(" ").lowercase()
    val chinese = listOf("国漫", "国产动漫", "国产动画", "国创", "中国动漫", "中国动画")
        .any(evidence::contains)
    val japanese = listOf("日漫", "日本动漫", "日本动画", "番剧", "新番", "anime")
        .any(evidence::contains) ||
        (!chinese && sectionTitle.contains(Regex("月新番|季度")))
    val theatrical = listOf("剧场版", "动画电影", "动漫电影", "movie")
        .any(evidence::contains)

    return when (category) {
        AnimeRankingCategory.JAPANESE_TV -> japanese && !theatrical
        AnimeRankingCategory.JAPANESE_MOVIE -> japanese && theatrical
        AnimeRankingCategory.CHINESE_TV -> chinese && !theatrical
        AnimeRankingCategory.CHINESE_MOVIE -> chinese && theatrical
    }
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
