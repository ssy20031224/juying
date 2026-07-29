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
    val schedule: List<ScheduleEntry>,
    val fetchedAt: Long
) {
    val recommendationPool: List<SourceItem>
        get() = (rankings.values.flatten().map { it.item } + schedule.map { it.item })
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
            val scheduleResult = runCatching { fetchSchedule() }
            val rankings = rankingResult.getOrNull()
                ?.takeIf { it.values.any(List<SourceRankingEntry>::isNotEmpty) }
                ?: previous?.rankings
                ?: emptyMap()
            val schedule = scheduleResult.getOrNull()
                ?.takeIf(List<ScheduleEntry>::isNotEmpty)
                ?: previous?.schedule
                ?: emptyList()

            if (rankings.isEmpty() && schedule.isEmpty()) {
                throw rankingResult.exceptionOrNull()
                    ?: scheduleResult.exceptionOrNull()
                    ?: IllegalStateException("远程榜单和周表均未返回数据")
            }

            LanercDiscoverySnapshot(
                rankings = rankings,
                schedule = schedule,
                fetchedAt = now
            ).also { cached = it }
        }
    }

    private fun fetchRankings(): Map<RankingKind, List<SourceRankingEntry>> {
        val payload = fetchDecodedJson("app/rank")
        val groups = payload.getAsJsonArray("rank_list")
            ?.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject }
            .orEmpty()
        if (groups.isEmpty()) return emptyMap()

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

        return mapOf(
            RankingKind.HOT to groupEntries(0),
            RankingKind.POPULARITY to groupEntries(1),
            RankingKind.SCORE to allScored
        )
    }

    private fun rankItem(vod: JsonObject): SourceItem {
        val tags = splitTags(vod.string("vod_class"))
        val score = vod.numberString("vod_score")
            .takeUnless { it == "0" || it == "0.0" }
            .orEmpty()
        return SourceItem(
            id = vod.idString("id"),
            title = vod.string("vod_name"),
            year = vod.string("vod_year"),
            kind = tags.joinToString(" "),
            tags = tags,
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
