package com.juying.app.source

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

private const val LANERC_DISCOVERY_BASE = "http://lol.jngaoke.cn/"
private const val LANERC_DISCOVERY_KEY = "8f81c2519e3b661834219e7142000093"
private const val LANERC_DISCOVERY_TTL_MS = 10 * 60 * 1000L
private const val LANERC_SOURCE_KEY = "lanerc"
private const val LANERC_SOURCE_TITLE = "Lanerc动漫"
private const val DOUBAN_REFERER_SUFFIX =
    "@Referer=https://movie.douban.com/@User-Agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

data class LanercDiscoverySnapshot(
    val rankings: Map<RankingKind, List<SourceRankingEntry>>,
    val categoryRankings: Map<AnimeRankingCategory, List<SourceRankingEntry>> = emptyMap(),
    val schedule: List<ScheduleEntry>,
    val seasons: List<SeasonalRecommendation> = emptyList(),
    val fetchedAt: Long
) {
    val recommendationPool: List<SourceItem>
        get() = (
            seasons.flatMap { it.entries }.map { it.item } +
                categoryRankings.values.flatten().map { it.item } +
                rankings.values.flatten().map { it.item } +
                schedule.map { it.item }
            )
            .distinctBy { normalizeDiscoveryTitle(it.title) }
}

/**
 * Native, read-only port of the audited Lanerc rank/week script contracts.
 *
 * This repository owns its network client and ten-minute metadata cache. It is
 * deliberately independent from SourceAdapter.detail()/play(), ResultCache,
 * QuickJS, and the Media3 player so discovery failures can never fail playback.
 */
class LanercDiscoveryRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private val loadMutex = Mutex()

    @Volatile
    private var cached: LanercDiscoverySnapshot? = null

    suspend fun load(force: Boolean = false): LanercDiscoverySnapshot = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            val now = System.currentTimeMillis()
            cached
                ?.takeIf { !force && now - it.fetchedAt < LANERC_DISCOVERY_TTL_MS }
                ?.let { return@withLock it }

            val previous = cached
            val rankingResult = runCatching { fetchRankings() }
            // rank 接口是全站唯一带评分的接口，用它补全无评分的分类/周表条目
            val scoreByTitle = rankingResult.getOrNull()?.second.orEmpty()
            val categoryResult = runCatching { fetchCategoryRankings(scoreByTitle) }
            val scheduleResult = runCatching { fetchSchedule() }
            val rankings = rankingResult.getOrNull()
                ?.first
                ?.takeIf { it.values.any(List<SourceRankingEntry>::isNotEmpty) }
                ?: previous?.rankings
                ?: emptyMap()
            val schedule = scheduleResult.getOrNull()
                ?.takeIf(List<ScheduleEntry>::isNotEmpty)
                ?: previous?.schedule
                ?: emptyList()
            val categoryRankings = categoryResult.getOrNull()
                ?.takeIf { it.values.any(List<SourceRankingEntry>::isNotEmpty) }
                ?: previous?.categoryRankings
                ?: emptyMap()
            val seasons = runCatching {
                fetchSeasonalRecommendations(rankings, schedule, now, scoreByTitle)
            }.getOrNull()
                ?.takeIf(List<SeasonalRecommendation>::isNotEmpty)
                ?: previous?.seasons
                ?: emptyList()

            if (rankings.isEmpty() && categoryRankings.isEmpty() && schedule.isEmpty() && seasons.isEmpty()) {
                throw rankingResult.exceptionOrNull()
                    ?: scheduleResult.exceptionOrNull()
                    ?: IllegalStateException("远程榜单和周表均未返回数据")
            }

            LanercDiscoverySnapshot(
                rankings = rankings,
                categoryRankings = categoryRankings,
                schedule = schedule,
                seasons = seasons,
                fetchedAt = now
            ).also { cached = it }
        }
    }

    private fun fetchRankings(): Pair<Map<RankingKind, List<SourceRankingEntry>>, Map<String, String>> {
        val payload = fetchDecodedJson("app/rank")
        val groups = payload.getAsJsonArray("rank_list")
            ?.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .orEmpty()
        if (groups.isEmpty()) return emptyMap<RankingKind, List<SourceRankingEntry>>() to emptyMap()

        fun groupEntries(groupIndex: Int): List<SourceRankingEntry> {
            val group = groups.getOrNull(groupIndex.coerceAtMost(groups.lastIndex)) ?: return emptyList()
            val sectionName = group.string("name").ifBlank { "季度推荐" }
            return group.getAsJsonArray("vods")
                ?.mapIndexedNotNull { index, element ->
                    element.takeIf(JsonElement::isJsonObject)?.asJsonObject?.let { vod ->
                        SourceRankingEntry(
                            item = rankItem(vod),
                            sourceSection = sectionName,
                            sourcePosition = index + 1
                        )
                    }
                }
                .orEmpty()
                .filter { it.item.title.isNotBlank() }
        }

        val allScored = groups.asSequence()
            .flatMap { group ->
                val sectionName = group.string("name").ifBlank { "季度推荐" }
                group.getAsJsonArray("vods")
                    ?.asSequence()
                    ?.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
                    ?.map { sectionName to it }
                    ?: emptySequence()
            }
            .mapNotNull { (sectionName, vod) ->
                val item = rankItem(vod)
                val score = item.score.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return@mapNotNull null
                Triple(sectionName, item, score)
            }
            .sortedByDescending { it.third }
            .distinctBy { normalizeDiscoveryTitle(it.second.title) }
            .mapIndexed { index, (sectionName, item, _) ->
                SourceRankingEntry(
                    item = item,
                    sourceSection = "$sectionName · 真实评分",
                    sourcePosition = index + 1
                )
            }
            .toList()

        val scoreByTitle = buildMap {
            groups.asSequence()
                .flatMap { group ->
                    group.getAsJsonArray("vods")
                        ?.asSequence()
                        ?.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
                        ?: emptySequence()
                }
                .mapNotNull { vod ->
                    val item = rankItem(vod)
                    val score = item.score.takeIf { it.toDoubleOrNull()?.let { value -> value > 0.0 } == true }
                        ?: return@mapNotNull null
                    normalizeDiscoveryTitle(item.title) to score
                }
                .forEach { (key, score) -> put(key, score) }
        }

        return mapOf(
            RankingKind.HOT to groupEntries(0),
            RankingKind.POPULARITY to groupEntries(1),
            RankingKind.SCORE to allScored
        ) to scoreByTitle
    }

    /**
     * Lanerc exposes authoritative catalog classes independently from its
     * misleadingly named seasonal "heat" page: 20=Japanese anime,
     * 22=Japanese theatrical anime, 24=Chinese anime. Preserve endpoint order
     * and attach explicit region/format evidence before the shared classifier.
     */
    private fun fetchCategoryRankings(scoreByTitle: Map<String, String>): Map<AnimeRankingCategory, List<SourceRankingEntry>> {
        val japaneseTv = fetchCatalogClass(
            classId = 20,
            section = "Lanerc 日漫",
            fallbackKind = "日漫 TV动画",
            scoreByTitle = scoreByTitle
        ).filter { matchesAnimeRankingCategory(it.item, it.sourceSection, AnimeRankingCategory.JAPANESE_TV) }
        val japaneseMovies = fetchCatalogClass(
            classId = 22,
            section = "Lanerc 剧场版",
            fallbackKind = "日漫 剧场版 动画电影",
            scoreByTitle = scoreByTitle
        ).filter { matchesAnimeRankingCategory(it.item, it.sourceSection, AnimeRankingCategory.JAPANESE_MOVIE) }
        val chinese = fetchCatalogClass(
            classId = 24,
            section = "Lanerc 国漫",
            fallbackKind = "国漫 国产动画",
            scoreByTitle = scoreByTitle
        )
        return mapOf(
            AnimeRankingCategory.JAPANESE_TV to japaneseTv,
            AnimeRankingCategory.JAPANESE_MOVIE to japaneseMovies,
            AnimeRankingCategory.CHINESE_TV to chinese.filter {
                matchesAnimeRankingCategory(it.item, it.sourceSection, AnimeRankingCategory.CHINESE_TV)
            },
            AnimeRankingCategory.CHINESE_MOVIE to chinese.filter {
                matchesAnimeRankingCategory(it.item, it.sourceSection, AnimeRankingCategory.CHINESE_MOVIE)
            }
        )
    }

    private fun fetchCatalogClass(
        classId: Int,
        section: String,
        fallbackKind: String,
        maxPages: Int = 6,
        scoreByTitle: Map<String, String> = emptyMap()
    ): List<SourceRankingEntry> {
        val result = mutableListOf<SourceRankingEntry>()
        val seen = HashSet<String>()
        for (page in 1..maxPages) {
            val payload = runCatching {
                fetchDecodedJson(
                    "app/vod/filter?page=$page&class_id=$classId&vod_class=&year=&sort_by="
                )
            }.getOrNull() ?: break
            val pageItems = payload.getAsJsonArray("filter_vods")
                ?.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
                .orEmpty()
            if (pageItems.isEmpty()) break
            var added = 0
            pageItems.forEach { vod ->
                val item = rankItem(
                    vod = vod,
                    fallbackStatus = currentCatalogStatus(vod),
                    fallbackKind = fallbackKind
                ).let { base ->
                    // filter 接口不返回评分，用 rank 接口的同标题评分补全
                    val key = normalizeDiscoveryTitle(base.title)
                    if (base.score.isBlank() && key.isNotEmpty()) {
                        base.copy(score = scoreByTitle[key].orEmpty())
                    } else base
                }
                val key = normalizeDiscoveryTitle(item.title)
                if (key.isNotEmpty() && seen.add(key)) {
                    result += SourceRankingEntry(
                        item = item,
                        sourceSection = section,
                        sourcePosition = result.size + 1
                    )
                    added++
                }
            }
            if (added == 0) break
        }
        return result
    }

    private fun fetchSeasonalRecommendations(
        rankings: Map<RankingKind, List<SourceRankingEntry>>,
        schedule: List<ScheduleEntry>,
        nowMillis: Long,
        scoreByTitle: Map<String, String> = emptyMap()
    ): List<SeasonalRecommendation> {
        val targetSeasons = beijingSeasonWindow(nowMillis)
        val currentYear = targetSeasons.firstOrNull()?.year ?: return emptyList()
        val explicitEntries = rankings.values
            .flatten()
            .asSequence()
            .filterNot { it.sourceSection.contains("真实评分") }
            .filter { it.item.year.toIntOrNull() == currentYear }
            .distinctBy { normalizeDiscoveryTitle(it.item.title) }
            .groupBy { seasonMonthFromLabel(it.sourceSection) }

        val catalog = fetchCurrentYearCatalog(currentYear)
        val catalogByTitle = catalog.associateBy { normalizeDiscoveryTitle(it.title) }
        val explicitTitles = explicitEntries.values
            .flatten()
            .mapTo(HashSet()) { normalizeDiscoveryTitle(it.item.title) }
        val hotOrder = runCatching { fetchHomeHotOrder() }
            .getOrDefault(emptyList())
            .mapIndexed { index, title -> normalizeDiscoveryTitle(title) to index }
            .toMap()

        return targetSeasons.mapNotNull { season ->
            val explicit = explicitEntries[season.month].orEmpty()
            val entries = if (explicit.isNotEmpty()) {
                explicit.mapIndexed { index, entry ->
                    entry.copy(
                        item = entry.item.copy(
                            status = entry.item.status.ifBlank { season.label },
                            score = entry.item.score.ifBlank { scoreByTitle[normalizeDiscoveryTitle(entry.item.title)].orEmpty() }
                        ),
                        sourceSection = season.label,
                        sourcePosition = index + 1
                    )
                }
            } else if (season == targetSeasons.first()) {
                // The audited rank endpoint can lag one cour behind (for
                // example it still exposes April on July 30). Derive only the
                // current cour from titles that are both in the current-year
                // catalog and the live weekly schedule, then exclude every
                // title explicitly assigned to an earlier cour.
                schedule.asSequence()
                    .mapNotNull seasonalItem@ { scheduled ->
                        val titleKey = normalizeDiscoveryTitle(scheduled.item.title)
                        val catalogItem = catalogByTitle[titleKey] ?: return@seasonalItem null
                        if (titleKey in explicitTitles) return@seasonalItem null
                        val merged = catalogItem.copy(
                            year = currentYear.toString(),
                            kind = scheduled.item.kind.ifBlank { catalogItem.kind },
                            tags = (scheduled.item.tags + catalogItem.tags).distinct(),
                            status = scheduled.item.status.ifBlank { catalogItem.status },
                            score = scheduled.item.score.ifBlank { catalogItem.score }
                                .ifBlank { scoreByTitle[titleKey].orEmpty() },
                            cover = scheduled.item.cover.ifBlank { catalogItem.cover },
                            description = scheduled.item.description.ifBlank { catalogItem.description }
                        )
                        merged to scheduled
                    }
                    .sortedWith(
                        compareBy<Pair<SourceItem, ScheduleEntry>> {
                            hotOrder[normalizeDiscoveryTitle(it.first.title)] ?: Int.MAX_VALUE
                        }
                            .thenByDescending { it.first.score.toDoubleOrNull() ?: 0.0 }
                            .thenBy { it.second.weekdayIndex }
                            .thenBy { it.second.airTime ?: "99:99" }
                    )
                    .distinctBy { normalizeDiscoveryTitle(it.first.title) }
                    .mapIndexed { index, (item, _) ->
                        SourceRankingEntry(
                            item = item,
                            sourceSection = season.label,
                            sourcePosition = index + 1
                        )
                    }
                    .toList()
            } else {
                emptyList()
            }
            entries.takeIf(List<SourceRankingEntry>::isNotEmpty)
                ?.let { SeasonalRecommendation(season, it) }
        }
    }

    private fun fetchCurrentYearCatalog(year: Int): List<SourceItem> {
        val result = mutableListOf<SourceItem>()
        val seen = HashSet<String>()
        for (page in 1..8) {
            val payload = runCatching {
                fetchDecodedJson(
                    "app/vod/filter?page=$page&class_id=20&vod_class=&year=$year&sort_by="
                )
            }.getOrNull() ?: break
            val pageItems = payload.getAsJsonArray("filter_vods")
                ?.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
                .orEmpty()
            if (pageItems.isEmpty()) break
            var newItems = 0
            pageItems.forEach { vod ->
                val item = rankItem(
                    vod = vod,
                    fallbackYear = year.toString(),
                    fallbackStatus = currentCatalogStatus(vod)
                )
                val key = normalizeDiscoveryTitle(item.title)
                if (key.isNotEmpty() && seen.add(key)) {
                    result += item
                    newItems++
                }
            }
            if (newItems == 0) break
        }
        return result
    }

    private fun fetchHomeHotOrder(): List<String> {
        val payload = fetchDecodedJson("app/home")
        return payload.getAsJsonArray("hot_list")
            ?.mapNotNull { element ->
                element.takeIf(JsonElement::isJsonObject)
                    ?.asJsonObject
                    ?.string("vod_name")
                    ?.takeIf(String::isNotBlank)
            }
            .orEmpty()
    }

    private fun currentCatalogStatus(vod: JsonObject): String {
        val remarks = vod.string("vod_remarks")
        val total = vod.int("vod_total").coerceAtLeast(0)
        return when {
            remarks.contains("完结") || remarks.contains("全集") -> remarks
            listOf("未开播", "待播", "预告", "定档").any {
                remarks.contains(it, ignoreCase = true)
            } -> "未开播 $remarks".trim()
            remarks.isNotBlank() -> remarks
            total > 0 -> "更新中 更新至${total}集"
            else -> ""
        }
    }

    private fun rankItem(
        vod: JsonObject,
        fallbackYear: String = "",
        fallbackStatus: String = "",
        fallbackKind: String = ""
    ): SourceItem {
        val tags = splitTags(vod.string("vod_class"))
        val score = vod.numberString("vod_score")
            .takeUnless { it == "0" || it == "0.0" }
            .orEmpty()
        return SourceItem(
            id = vod.idString("id"),
            title = vod.string("vod_name"),
            year = vod.string("vod_year").ifBlank { fallbackYear },
            kind = listOf(fallbackKind, tags.joinToString(" "))
                .filter(String::isNotBlank)
                .joinToString(" "),
            tags = tags,
            status = vod.string("vod_remarks").ifBlank { fallbackStatus },
            score = score,
            cover = cover(vod.string("vod_pic")),
            description = vod.string("vod_blurb").ifBlank { vod.string("vod_sub") },
            sourceKey = LANERC_SOURCE_KEY,
            sourceTitle = LANERC_SOURCE_TITLE
        )
    }

    private fun fetchSchedule(): List<ScheduleEntry> {
        val payload = fetchDecodedJson("app/week")
        val week = payload.getAsJsonObject("week_list") ?: return emptyList()
        val keys = listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")
        return keys.flatMapIndexed { weekdayIndex, key ->
            week.getAsJsonArray(key)
                ?.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
                ?.mapNotNull { vod -> scheduleEntry(vod, weekdayIndex) }
                .orEmpty()
        }
            .distinctBy { "${it.weekdayIndex}:${normalizeDiscoveryTitle(it.item.title)}" }
            .sortedWith(compareBy<ScheduleEntry> { it.weekdayIndex }.thenBy { it.airTime ?: "99:99" })
    }

    private fun scheduleEntry(vod: JsonObject, weekdayIndex: Int): ScheduleEntry? {
        val title = vod.string("vod_name")
        if (title.isBlank()) return null
        val remarks = vod.string("vod_remarks")
        val tags = splitTags(vod.string("vod_class"))
        val episode = vod.int("vod_total").coerceAtLeast(0)
        val upcoming = listOf("开播", "未开播", "待播", "预告", "定档")
            .any { remarks.contains(it, ignoreCase = true) }
        val status = when {
            upcoming -> "未开播 $remarks".trim()
            remarks.contains("完结") || remarks.contains("全集") -> remarks
            episode > 0 -> "更新中 更新至${episode}集 $remarks".trim()
            else -> remarks
        }
        val score = vod.numberString("vod_score")
            .takeUnless { it == "0" || it == "0.0" }
            .orEmpty()
        val item = SourceItem(
            id = vod.idString("id"),
            title = title,
            kind = tags.joinToString(" "),
            tags = tags,
            status = status,
            score = score,
            cover = cover(vod.string("vod_pic")),
            description = vod.string("vod_sub"),
            sourceKey = LANERC_SOURCE_KEY,
            sourceTitle = LANERC_SOURCE_TITLE
        )
        return ScheduleEntry(
            item = item,
            weekdayIndex = weekdayIndex,
            airTime = Regex("(?<!\\d)([01]?\\d|2[0-3]):[0-5]\\d(?!\\d)")
                .find(remarks)
                ?.value,
            episodeText = if (upcoming || episode <= 0) "" else "更新至${episode}集"
        )
    }

    private fun fetchDecodedJson(path: String): JsonObject {
        val request = Request.Builder()
            .url(LANERC_DISCOVERY_BASE + path.trimStart('/'))
            .header("Accept", "application/json")
            .header("User-Agent", "juying-android discovery")
            .cacheControl(CacheControl.Builder().noCache().build())
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("${response.code} ${response.request.url.host}")
            }
            val raw = response.body?.string().orEmpty()
            LanercDiscoveryCodec.decodeEnvelope(raw, LANERC_DISCOVERY_KEY)
        }
    }

    private fun cover(raw: String): String {
        if (raw.isBlank() || !raw.contains("doubanio.com") || raw.contains("@Referer=")) return raw
        return raw + DOUBAN_REFERER_SUFFIX
    }

    private fun splitTags(raw: String): List<String> =
        raw.split(Regex("[,，/]"))
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
}

internal object LanercDiscoveryCodec {
    fun restoreAlphabet(ciphertext: String): String =
        ciphertext
            .replace('1', '!')
            .replace('5', '@')
            .replace('9', '#')
            .replace('/', '*')
            .replace('-', '&')
            .replace('!', '9')
            .replace('@', '1')
            .replace('#', '5')
            .replace('*', '+')
            .replace('&', '/')

    fun decodeEnvelope(raw: String, key: String): JsonObject {
        val envelope = JsonParser.parseString(raw).asJsonObject
        if (envelope.int("code") != 201 || !envelope.has("data")) return envelope
        val restored = restoreAlphabet(envelope.string("data"))
        val padded = restored + "=".repeat((4 - restored.length % 4) % 4)
        val cipherBytes = Base64.getDecoder().decode(padded)
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "AES")
        )
        return JsonParser.parseString(
            String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8)
        ).asJsonObject
    }
}

private fun JsonObject.string(name: String): String =
    get(name)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

private fun JsonObject.int(name: String): Int =
    get(name)?.takeUnless { it.isJsonNull }?.asInt ?: 0

private fun JsonObject.idString(name: String): String =
    get(name)?.takeUnless { it.isJsonNull }?.let {
        if (it.isJsonPrimitive && it.asJsonPrimitive.isNumber) {
            it.asLong.toString()
        } else {
            it.asString
        }
    }.orEmpty()

private fun JsonObject.numberString(name: String): String =
    get(name)?.takeUnless { it.isJsonNull }?.let { element ->
        runCatching {
            val value = element.asDouble
            if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
        }.getOrElse { element.asString }
    }.orEmpty()
