package com.juying.app.source

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.juying.app.engine.NetworkClient
import com.juying.app.engine.QuickJsEngine
import com.juying.app.engine.SourceExports
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

fun isLikelyTranscodingPlaceholderUrl(url: String): Boolean {
    val parsed = url.trim().toHttpUrlOrNull() ?: return false
    val path = parsed.encodedPath.lowercase(Locale.US)
    return listOf(
        "transcod",
        "zhuanma",
        "%e8%bd%ac%e7%a0%81",
        "loading",
        "jiazai",
        "processing",
        "zanwu",
        "wait.mp4",
        "404.mp4",
        "tips.mp4",
        "notice.mp4",
        "error.mp4"
    ).any(path::contains)
}

data class SourceItem(
    val id: String = "",
    val title: String = "",
    val year: String = "",
    val kind: String = "",
    val tags: List<String> = emptyList(),
    val status: String = "",
    val score: String = "",
    val cover: String = "",
    val description: String = "",
    val sourceKey: String = "",
    val sourceTitle: String = "",
    val sourceCount: Int = 1
)

enum class MediaReleaseState {
    FINISHED,
    SERIALIZING,
    UPDATING,
    UPCOMING,
    UNKNOWN
}

data class MediaStatusSummary(
    val state: MediaReleaseState,
    val displayText: String,
    val episodeText: String
)

/**
 * Normalizes source-specific remarks without guessing that every number means
 * "still serializing". Completion has the highest priority, followed by
 * explicit upcoming/serializing/updating markers. A bare episode count remains
 * UNKNOWN until a source supplies an actual release-state marker.
 */
fun resolveMediaStatus(item: SourceItem, episodeCount: Int? = null): MediaStatusSummary {
    val rawStatus = item.status.trim()
    val combined = buildString {
        append(rawStatus)
        append(' ')
        append(item.tags.joinToString(" "))
        append(' ')
        append(item.kind)
    }.replace(Regex("\\s+"), " ").trim()

    val finished = listOf(
        "已完结", "完结", "全集", "全剧终", "完毕", "大结局", "收官"
    ).any { combined.contains(it, ignoreCase = true) } ||
        Regex("\\bcomplete(?:d)?\\b|\\bfinished\\b", RegexOption.IGNORE_CASE).containsMatchIn(combined) ||
        Regex("全\\s*\\d+(?:\\.\\d+)?\\s*[集话期]|\\d+(?:\\.\\d+)?\\s*[集话期]\\s*全").containsMatchIn(combined)

    val upcoming = listOf(
        "未开播", "待播", "即将开播", "预告", "定档"
    ).any { combined.contains(it, ignoreCase = true) }

    val serializing = listOf(
        "连载中", "连载", "连载新番", "周更"
    ).any { combined.contains(it, ignoreCase = true) }

    val updating = listOf(
        "更新中", "更新至", "更新到", "更至", "更新第", "已更新", "每周更新"
    ).any { combined.contains(it, ignoreCase = true) } ||
        Regex("^\\s*\\d+(?:\\.\\d+)?\\s*[|｜].*(?:周|更)")
            .containsMatchIn(rawStatus)

    val finishedCount = Regex("全\\s*(\\d+(?:\\.\\d+)?)\\s*[集话期]")
        .find(combined)?.groupValues?.getOrNull(1)
        ?: Regex("(\\d+(?:\\.\\d+)?)\\s*[集话期]\\s*全")
            .find(combined)?.groupValues?.getOrNull(1)
        ?: Regex("共\\s*(\\d+(?:\\.\\d+)?)\\s*[集话期]")
            .find(combined)?.groupValues?.getOrNull(1)

    val latestCount = Regex(
        "(?:更新至|更新到|更至|更新第|已更新至?)\\s*第?\\s*(\\d+(?:\\.\\d+)?)\\s*[集话期]"
    ).find(combined)?.groupValues?.getOrNull(1)
        ?: Regex("第?\\s*(\\d+(?:\\.\\d+)?)\\s*[集话期]")
            .find(rawStatus)?.groupValues?.getOrNull(1)
        ?: Regex("^\\s*(\\d+(?:\\.\\d+)?)\\s*[|｜]")
            .find(rawStatus)?.groupValues?.getOrNull(1)

    val reliableEpisodeCount = episodeCount?.takeIf { it > 0 }?.toString()
    val movieLike = listOf("剧场版", "电影", "Movie", "OVA", "OAD")
        .any { item.kind.contains(it, ignoreCase = true) || item.tags.any { tag -> tag.contains(it, ignoreCase = true) } }

    val state = when {
        finished -> MediaReleaseState.FINISHED
        upcoming -> MediaReleaseState.UPCOMING
        updating -> MediaReleaseState.UPDATING
        serializing -> MediaReleaseState.SERIALIZING
        movieLike && reliableEpisodeCount == "1" -> MediaReleaseState.FINISHED
        else -> MediaReleaseState.UNKNOWN
    }

    val count = when (state) {
        MediaReleaseState.FINISHED -> finishedCount ?: reliableEpisodeCount ?: latestCount
        MediaReleaseState.SERIALIZING, MediaReleaseState.UPDATING -> latestCount ?: reliableEpisodeCount
        else -> reliableEpisodeCount ?: latestCount
    }
    val episodeText = when {
        state == MediaReleaseState.FINISHED && movieLike && (count == null || count == "1") -> "正片"
        state == MediaReleaseState.FINISHED && count != null -> "全${count}集"
        (state == MediaReleaseState.SERIALIZING || state == MediaReleaseState.UPDATING) && count != null -> "更新至${count}集"
        count != null -> "${count}集"
        else -> ""
    }
    val statusLabel = when (state) {
        MediaReleaseState.FINISHED -> "已完结"
        MediaReleaseState.SERIALIZING -> "连载中"
        MediaReleaseState.UPDATING -> "更新中"
        MediaReleaseState.UPCOMING -> "未开播"
        MediaReleaseState.UNKNOWN -> "状态待确认"
    }
    val fallbackDetail = rawStatus
        .takeIf { it.isNotBlank() && it != statusLabel }
        ?.take(24)
        .orEmpty()
    val detail = episodeText.ifBlank { fallbackDetail }
    return MediaStatusSummary(
        state = state,
        displayText = if (detail.isBlank()) statusLabel else "$statusLabel | $detail",
        episodeText = episodeText
    )
}

data class HomeSection(
    val title: String,
    val key: String,
    val sourceKey: String = "",
    val sourceTitle: String = "",
    val items: List<SourceItem>
)

/** 源 JS `categories()` 返回的分类 Tab：key 作为 search(catKey,1) 的关键词，title 为显示名。 */
data class SourceCategory(val key: String, val title: String)

data class Episode(
    val id: String,
    val name: String,
    val route: String = "",
    val flagStr: String = "",
    val flag: Map<String, String> = emptyMap()
)

data class QualityOption(
    val name: String,
    val url: String,
    val type: String = "auto"
)

data class PlayResult(
    val url: String = "",
    val type: String = "auto",
    val headers: Map<String, String>? = null,
    val referer: String? = null,
    val qualities: List<QualityOption> = emptyList(),
    val error: String? = null,
    val startSec: Long = 0L
)

data class DetailResult(
    val item: SourceItem,
    val episodes: List<Episode>
)

data class SourceConfig(
    val key: String,
    val title: String,
    val localFile: String,
    val codeUrl: String = "",
    val defaultEnabled: Boolean = true,
    val availability: SourceAvailability = SourceAvailability.AVAILABLE,
    val unavailableReason: String = ""
)

enum class SourceAvailability(val label: String) {
    AVAILABLE("可用"),
    MAINTENANCE("源维护中"),
    ABNORMAL("源异常")
}

class SourceManager(private val context: Context) {

    private val engine = QuickJsEngine(context)
    private val gson = Gson()
    private val adapters = mutableMapOf<String, SourceAdapter>()
    private val prefs = context.getSharedPreferences("juying_sources_config", Context.MODE_PRIVATE)

    // Populated from RemoteSourceFetcher for remote URL mapping
    private val remoteConfigs = RemoteSourceFetcher.remoteSources().associateBy { it.key }

    private val builtInSources: List<SourceConfig> = listOf(
        SourceConfig("AuvFun", "AuvFun动漫", "AuvFun.js"),
        // Upstream currently disables Lanerc because its proxy returns a
        // fixed upgrade-notice playlist instead of the requested title.
        SourceConfig(
            "lanerc", "Lanerc动漫", "lanerc.js",
            defaultEnabled = false,
            availability = SourceAvailability.MAINTENANCE,
            unavailableReason = "上游当前只返回版本升级提示视频"
        ),
        SourceConfig("jinpai", "金牌动漫", "jinpai.js"),
        SourceConfig("cycapp", "次元城", "cycapp.js"),
        SourceConfig("guazi", "瓜子动漫", "guazi.js"),
        SourceConfig("shuangxing", "双星动漫", "shuangxing.js"),
        SourceConfig("xifanacg", "稀饭动漫", "xifanacg.js"),
        SourceConfig(
            "fanshu", "番薯动漫", "fanshu.js",
            defaultEnabled = false,
            availability = SourceAvailability.MAINTENANCE,
            unavailableReason = "上游 App-Guard 校验已更新"
        ),
        // Sanqiu is absent from AuvFun's current remote source list and its
        // only known API host now responds 403.
        SourceConfig(
            "sanqiu", "三秋动漫", "sanqiu.js",
            defaultEnabled = false,
            availability = SourceAvailability.ABNORMAL,
            unavailableReason = "上游接口持续返回 403"
        ),
        SourceConfig("gugu", "咕咕动漫", "gugu.js"),
        SourceConfig(
            "shutiao", "薯条动漫", "shutiao.js",
            defaultEnabled = false,
            availability = SourceAvailability.MAINTENANCE,
            unavailableReason = "上游已暂停该来源"
        ),
        SourceConfig("yzx", "云帧享", "yzx.js"),
        SourceConfig("akianime", "AkiAnime", "akianime.js"),
        SourceConfig("lmm85", "动漫在线", "lmm85.js"),
        SourceConfig("dmbus", "动漫巴士", "dmbus.js")
    )

    val customSources: List<SourceConfig>
        get() {
            val customDir = java.io.File(context.filesDir, "custom_sources")
            if (!customDir.exists()) return emptyList()
            val files = customDir.listFiles { _, name -> name.endsWith(".js") } ?: return emptyList()
            return files.map { file ->
                val key = file.nameWithoutExtension
                val title = prefs.getString("custom_title_$key", key) ?: key
                SourceConfig(key, title, file.name, defaultEnabled = true)
            }
        }

    val rawSources: List<SourceConfig>
        get() = builtInSources + customSources

    fun getSourceConfig(keyOrTitle: String): SourceConfig? = rawSources.firstOrNull {
        it.key.equals(keyOrTitle, ignoreCase = true) ||
            it.title.equals(keyOrTitle, ignoreCase = true)
    }

    fun sourceAvailability(keyOrTitle: String): SourceAvailability =
        getSourceConfig(keyOrTitle)?.availability ?: SourceAvailability.AVAILABLE

    fun isSourceAvailable(keyOrTitle: String): Boolean =
        sourceAvailability(keyOrTitle) == SourceAvailability.AVAILABLE

    fun sourceUnavailableMessage(keyOrTitle: String): String? {
        val source = getSourceConfig(keyOrTitle) ?: return null
        if (source.availability == SourceAvailability.AVAILABLE) return null
        val suffix = source.unavailableReason.takeIf(String::isNotBlank)?.let { "：$it" }.orEmpty()
        return "${source.availability.label}，暂不可用$suffix，请尝试切换其他源"
    }

    fun isSourceEnabled(key: String): Boolean {
        val source = rawSources.find { it.key == key }
        if (source?.availability != null && source.availability != SourceAvailability.AVAILABLE) {
            return false
        }
        val defaultVal = source?.defaultEnabled ?: true
        return prefs.getBoolean("source_enabled_$key", defaultVal)
    }

    private fun loadAdapter(source: SourceConfig): SourceAdapter? {
        val key = source.key
        // Check custom_sources first
        val customFile = java.io.File(context.filesDir, "custom_sources/${source.localFile}")
        if (customFile.exists()) {
            try {
                val customCode = customFile.readText(Charsets.UTF_8).removePrefix("\uFEFF").trim()
                if (customCode.isNotEmpty()) {
                    val exports = engine.loadSourceFromCode(key, source.localFile, customCode)
                    return SourceAdapter(source, exports, gson)
                }
            } catch (e: Exception) {
                Log.e("SourceManager", "Failed to load custom script for $key: ${e.message}")
            }
        }

        if (key != "lanerc") {
            val rc = remoteConfigs[key]
            var code = RemoteSourceFetcher.getScript(context, key, rc?.codeUrl ?: "")
            if (code.isNotEmpty()) {
                try {
                    val exports = engine.loadSourceFromCode(key, source.localFile, code)
                    return SourceAdapter(source, exports, gson)
                } catch (e: Exception) {
                    Log.w("SourceManager", "Failed loading script for $key (${e.message}), clearing cache and trying bundled asset...")
                    val cacheFile = java.io.File(context.filesDir, "source_scripts/${key}.js")
                    if (cacheFile.exists()) try { cacheFile.delete() } catch (_: Exception) {}
                }
            }
        }
        // Fallback to bundled asset directly
        return try {
            val assetPath = "sources/${source.localFile}"
            val assetCode = context.assets.open(assetPath).bufferedReader().readText().removePrefix("\uFEFF").trim()
            if (assetCode.isNotEmpty()) {
                val exports = engine.loadSourceFromCode(key, source.localFile, assetCode)
                SourceAdapter(source, exports, gson)
            } else null
        } catch (e: Exception) {
            Log.e("SourceManager", "Failed to load bundled asset for $key: ${e.message}")
            null
        }
    }

    fun toggleSourceEnabled(key: String): Boolean {
        val current = isSourceEnabled(key)
        val newState = !current
        if (newState && !isSourceAvailable(key)) return false
        // 强制至少保留一个启用的源：禁用最后一个启用源时拒绝
        if (!newState && enabledSources.count() <= 1) {
            return current
        }
        prefs.edit().putBoolean("source_enabled_$key", newState).apply()

        if (newState) {
            val config = rawSources.find { it.key == key }
            if (config != null && !adapters.containsKey(key)) {
                val adapter = loadAdapter(config)
                if (adapter != null) {
                    adapters[key] = adapter
                }
            }
        } else {
            adapters.remove(key)?.close()
        }
        return newState
    }

    val enabledSources get() = rawSources.filter { isSourceEnabled(it.key) }

    fun removeCustomSource(key: String) {
        adapters.remove(key)?.close()
        val file = java.io.File(context.filesDir, "custom_sources/$key.js")
        try { file.delete() } catch (_: Exception) {}
        prefs.edit().remove("custom_title_$key").remove("source_enabled_$key").apply()
    }

    fun init() {
        for ((_, oldAdapter) in adapters) {
            try { oldAdapter.close() } catch (_: Exception) {}
        }
        adapters.clear()

        for (source in enabledSources) {
            val adapter = loadAdapter(source)
            if (adapter != null) {
                adapters[source.key] = adapter
            }
        }
    }

    fun getAdapter(key: String) = adapters[key]
    fun allAdapters() = adapters.values.toList()

    fun getSourceUrl(key: String): String {
        val customFile = java.io.File(context.filesDir, "custom_sources/$key.js")
        if (customFile.exists()) return customFile.absolutePath
        return remoteConfigs[key]?.codeUrl ?: ""
    }

    fun isCustomSource(key: String): Boolean =
        java.io.File(context.filesDir, "custom_sources/$key.js").exists()

    fun testSourceSpeed(key: String): Long {
        when (sourceAvailability(key)) {
            SourceAvailability.MAINTENANCE -> return SOURCE_TEST_MAINTENANCE
            SourceAvailability.ABNORMAL -> return SOURCE_TEST_ABNORMAL
            SourceAvailability.AVAILABLE -> Unit
        }
        val adapter = adapters[key] ?: return -1L
        val start = System.currentTimeMillis()
        return try {
            val sections = adapter.homeSections()
            val items = if (sections.isNotEmpty()) sections.flatMap { it.items } else adapter.search("漫", 1)
            if (items.isEmpty()) SOURCE_TEST_FAILED else System.currentTimeMillis() - start
        } catch (_: Exception) {
            SOURCE_TEST_FAILED
        }
    }

    fun importCustomSource(key: String, title: String, jsCode: String): Boolean {
        val cleanKey = key.trim().lowercase().replace(Regex("[^a-z0-9_]"), "")
        if (cleanKey.isEmpty() || jsCode.isBlank()) return false
        return try {
            val customDir = java.io.File(context.filesDir, "custom_sources")
            if (!customDir.exists()) customDir.mkdirs()
            val file = java.io.File(customDir, "${cleanKey}.js")
            file.writeText(jsCode.trim(), Charsets.UTF_8)
            prefs.edit().putBoolean("custom_source_$cleanKey", true).apply()
            prefs.edit().putString("custom_title_$cleanKey", title.ifEmpty { cleanKey }).apply()
            init()
            true
        } catch (e: Exception) {
            Log.e("SourceManager", "Failed to import custom source $key: ${e.message}")
            false
        }
    }

    fun testSource(key: String): String {
        sourceUnavailableMessage(key)?.let { message ->
            val source = getSourceConfig(key)
            return "【${source?.availability?.label ?: "源异常"}】${source?.title ?: key}：$message"
        }
        val adapter = adapters[key] ?: return "源 [$key] 未开启或未成功初始化"
        val start = System.currentTimeMillis()
        return try {
            // 对齐 AuvFun：测试只验证首页数据能否返回（连通性），不做详情/播放链路探测
            val sections = adapter.homeSections()
            val items = if (sections.isNotEmpty()) sections.flatMap { it.items } else adapter.search("漫", 1)
            val duration = System.currentTimeMillis() - start
            if (items.isEmpty()) {
                "【首页失败】源 [$key] 无视频卡片 (${duration}ms) — 服务器不可达或被风控"
            } else {
                "【首页✅】$key: ${items.size}卡片 (${duration}ms), 首卡: ${items.first().title}"
            }
        } catch (e: Exception) {
            "【测试异常】源 [$key] ${e.message} (${System.currentTimeMillis() - start}ms)"
        }
    }

    fun testAllSources(): String {
        val sb = StringBuilder()
        val keys = rawSources.map { it.key }
        for (key in keys) {
            val result = testSource(key)
            sb.append(result).append("\n").append("─────────────────────\n")
        }
        return sb.toString()
    }

    fun close() {
        adapters.values.forEach { it.close() }
        engine.close()
    }

    companion object {
        const val SOURCE_TEST_FAILED = -1L
        const val SOURCE_TEST_MAINTENANCE = -2L
        const val SOURCE_TEST_ABNORMAL = -3L

        fun normalizeTitle(title: String): String {
            return title
                .replace(Regex("<[^>]+>"), "")
                .replace(Regex("[\\s\\p{Punct}【】《》「」『』（）\\[\\]（）]"), "")
                .lowercase()
                .trim()
                .replace(
                    Regex(
                        "(?:动态漫画|动态漫|漫画版|动画版)(?:第?[0-9一二三四五六七八九十]+季)?$"
                    ),
                    ""
                )
                .replace(Regex("(?:全集|完整版|普通话版|国语版)$"), "")
                .trim()
        }

        /**
         * Source sites commonly append presentation-only suffixes that are
         * not part of the work's searchable name. Keep the original first,
         * then try a conservative suffix-free alias.
         */
        fun detailSearchVariants(title: String): List<String> {
            val raw = title
                .replace(Regex("<[^>]+>"), "")
                .trim()
            if (raw.isEmpty()) return emptyList()
            val suffixFree = raw
                .replace(
                    Regex(
                        "[\\s·:：\\-—_（(【\\[]*(?:动态漫画|动态漫|漫画版|动画版)" +
                            "(?:\\s*第?[0-9一二三四五六七八九十]+季)?[）)】\\]]*\\s*$",
                        RegexOption.IGNORE_CASE
                    ),
                    ""
                )
                .trim()
            return linkedSetOf(raw, suffixFree)
                .filter { it.isNotBlank() }
        }

        fun titlesLikelyMatch(left: String, right: String): Boolean {
            val a = normalizeTitle(left)
            val b = normalizeTitle(right)
            if (a.isBlank() || b.isBlank()) return false
            if (a == b) return true

            val shorter = if (a.length <= b.length) a else b
            val longer = if (a.length > b.length) a else b
            if (shorter.length < 6 || !longer.startsWith(shorter)) return false

            // Accept a trailing season/presentation qualifier, but avoid
            // matching unrelated works which merely share a short prefix.
            val tail = longer.removePrefix(shorter)
            return tail.matches(
                Regex(
                    "(?:第?[0-9一二三四五六七八九十]+季|season[0-9]+|" +
                        "动态漫画|动态漫|漫画版|动画版|全集|完整版)+",
                    RegexOption.IGNORE_CASE
                )
            )
        }

        fun mergeSearchItems(items: List<SourceItem>): List<SourceItem> {
            val groups = LinkedHashMap<String, MutableList<SourceItem>>()
            for (item in items) {
                val key = normalizeTitle(item.title).ifBlank {
                    "${item.sourceKey}\u0000${item.id}"
                }
                groups.getOrPut(key) { mutableListOf() }.add(item)
            }
            return groups.values.map { rawVariants ->
                // The same source can expose one title in several home sections.
                // Count each (source, id) only once and never concatenate sourceKey:
                // sourceKey is an executable adapter identity, not display metadata.
                val variants = rawVariants.distinctBy { "${it.sourceKey}\u0000${it.id}" }
                val main = variants.first()
                if (variants.size == 1) main.copy(sourceCount = 1)
                else main.copy(
                    sourceTitle = variants.map { it.sourceTitle }.filter { it.isNotBlank() }.distinct().joinToString("+"),
                    sourceCount = variants.map { it.sourceKey }.filter { it.isNotBlank() }.distinct().size.coerceAtLeast(1),
                    year = variants.firstOrNull { it.year.isNotEmpty() }?.year ?: main.year,
                    kind = variants.firstOrNull { it.kind.isNotEmpty() }?.kind ?: main.kind,
                    cover = variants.firstOrNull { it.cover.isNotEmpty() }?.cover ?: main.cover,
                    description = variants.firstOrNull { it.description.isNotEmpty() }?.description ?: main.description,
                    status = variants.firstOrNull { it.status.isNotEmpty() }?.status ?: main.status,
                    score = variants.firstOrNull { it.score.isNotEmpty() }?.score ?: main.score,
                    tags = variants.flatMap { it.tags }.distinct()
                )
            }
        }

        fun sortByRelevance(items: List<SourceItem>, query: String): List<SourceItem> {
            val q = normalizeTitle(query)
            if (q.isEmpty()) return items
            val scored = items.mapNotNull { item ->
                val score = relevanceScore(item, q)
                if (score > 0) item to score else null
            }
            return scored.withIndex()
                .sortedWith(
                    compareByDescending<IndexedValue<Pair<SourceItem, Int>>> { it.value.second }
                        .thenBy { it.index }
                )
                .map { it.value.first }
        }

        /**
         * Search several low-cost aliases only when a source returns too few
         * results. This helps retrieve seasons, specials and typo variants
         * without replacing the user's original query.
         */
        fun searchVariants(query: String): List<String> {
            val raw = query.trim()
            if (raw.isEmpty()) return emptyList()
            val compact = raw.replace(Regex("[\\s\\p{Punct}【】《》「」『』（）\\[\\]]"), "")
            return linkedSetOf<String>().apply {
                add(raw)
                if (compact != raw) add(compact)
                val withoutDe = compact.replace("的", "")
                if (withoutDe.length >= 3 && withoutDe != compact) add(withoutDe)
                if (compact.length >= 5) add(compact.takeLast(3))
            }.toList()
        }

        private fun relevanceScore(item: SourceItem, query: String): Int {
            val title = normalizeTitle(item.title)
            if (title.isEmpty()) return 0
            val searchable = buildString {
                append(title)
                append(normalizeTitle(item.kind))
                item.tags.forEach { append(normalizeTitle(it)) }
                append(normalizeTitle(item.description).take(80))
            }
            val distinctQuery = query.toSet().size.coerceAtLeast(1)
            val overlap = query.toSet().count { it in title } * 100 / distinctQuery
            val edit = levenshtein(query, title)
            val editScore = (100 - edit * 100 / maxOf(query.length, title.length, 1)).coerceAtLeast(0)
            return when {
                title == query -> 10_000
                title.startsWith(query) -> 8_500 + overlap
                title.contains(query) -> 7_500 + overlap
                searchable.contains(query) -> 6_500 + overlap
                overlap >= 50 -> 4_000 + overlap + editScore
                overlap >= 30 -> 2_000 + overlap + editScore / 2
                else -> 0
            }
        }

        private fun levenshtein(left: String, right: String): Int {
            if (left == right) return 0
            if (left.isEmpty()) return right.length
            if (right.isEmpty()) return left.length
            var previous = IntArray(right.length + 1) { it }
            for (i in left.indices) {
                val current = IntArray(right.length + 1)
                current[0] = i + 1
                for (j in right.indices) {
                    val cost = if (left[i] == right[j]) 0 else 1
                    current[j + 1] = minOf(
                        current[j] + 1,
                        previous[j + 1] + 1,
                        previous[j] + cost
                    )
                }
                previous = current
            }
            return previous[right.length]
        }
    }
}

class SourceAdapter(
    val config: SourceConfig,
    private val exports: SourceExports,
    private val gson: Gson
) {
    val key get() = config.key
    val title get() = config.title

    fun close() {
        try { exports.close() } catch (_: Exception) {}
    }

    fun search(query: String, page: Int): List<SourceItem> {
        val raw = exports.search(query, page)
        return parseItems(raw)
    }

    fun searchFiltered(category: String, filters: Map<String, String>, page: Int): List<SourceItem> {
        val raw = exports.searchFiltered(category, gson.toJson(filters), page)
        return parseItems(raw)
    }

    fun homeSections(): List<HomeSection> {
        val raw = exports.homeSections()
        android.util.Log.d("SourceAdapter", "[$key] homeSections raw length=${raw.length}")
        return try {
            val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
            val list: List<Map<String, Any>>? = gson.fromJson(raw, listType)
            if (list == null) {
                android.util.Log.w("SourceAdapter", "[$key] homeSections: failed to parse as section list")
                return emptyList()
            }
            android.util.Log.d("SourceAdapter", "[$key] homeSections: ${list.size} sections parsed")
            val result = list.mapNotNull { section ->
                val itemsRaw = section["items"] ?: section["list"] ?: section["videoList"] ?: section["data"]
                val itemsList = parseItems(gson.toJson(itemsRaw))
                if (itemsList.isEmpty()) {
                    android.util.Log.w("SourceAdapter", "[$key] homeSections: section '${section["title"]}' has 0 parsed items (raw items present=${itemsRaw != null})")
                    null
                } else {
                    android.util.Log.d("SourceAdapter", "[$key] homeSections: section '${section["title"]}' parsed ${itemsList.size} items")
                    HomeSection(
                        title = section["title"]?.toString() ?: section["name"]?.toString() ?: "",
                        key = section["key"]?.toString() ?: "",
                        sourceKey = key,
                        sourceTitle = title,
                        items = itemsList
                    )
                }
            }
            android.util.Log.i("SourceAdapter", "[$key] homeSections: TOTAL ${result.size} sections with ${result.sumOf { it.items.size }} items")
            result.filter { it.items.isNotEmpty() }
        } catch (e: Exception) {
            android.util.Log.e("SourceAdapter", "[$key] homeSections parse error: ${e.message}", e)
            emptyList()
        }
    }

    fun categories(): List<SourceCategory> {
        val raw = exports.categories()
        if (raw.isBlank() || raw == "[]") return emptyList()
        return try {
            val listType = object : TypeToken<List<Map<String, Any>>>() {}.type
            val list: List<Map<String, Any>>? = gson.fromJson(raw, listType)
            list?.mapNotNull { m ->
                val cKey = m["key"]?.toString() ?: ""
                val cTitle = m["title"]?.toString()?.trim() ?: ""
                if (cTitle.isBlank()) null else SourceCategory(cKey, cTitle)
            } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun detail(id: String): DetailResult {
        val defaultItem = SourceItem(id = id, title = "", sourceKey = key, sourceTitle = title)
        return try {
            val raw = exports.detail(id)
            val mapType = object : TypeToken<Map<String, Any>>() {}.type
            val json: Map<String, Any>? = gson.fromJson(raw, mapType)
            if (json == null) return DetailResult(defaultItem, emptyList())

            val item = SourceItem(
                id = json["id"]?.toString() ?: json["vod_id"]?.toString() ?: id,
                title = (json["name"]?.toString() ?: json["title"]?.toString() ?: json["vod_name"]?.toString() ?: "").replace("<[^>]+>".toRegex(), "").trim(),
                year = json["year"]?.toString() ?: json["vod_year"]?.toString() ?: "",
                kind = json["type"]?.toString() ?: json["kind"]?.toString() ?: json["vod_class"]?.toString() ?: "",
                tags = listOf("tags", "tag", "genre", "class", "vod_class", "type")
                    .flatMap { field ->
                        val value = json[field] ?: return@flatMap emptyList()
                        when (value) {
                            is List<*> -> value.mapNotNull { it?.toString() }
                            else -> listOf(value.toString())
                        }
                    }
                    .flatMap { it.split(Regex("[,，、/|·\\s]+")) }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct(),
                status = json["status"]?.toString()
                    ?: json["remarks"]?.toString()
                    ?: json["vod_remarks"]?.toString()
                    ?: "",
                score = json["score"]?.toString() ?: json["vod_score"]?.toString() ?: "",
                cover = json["pic"]?.toString() ?: json["cover"]?.toString() ?: json["thumb"]?.toString() ?: json["vod_pic"]?.toString() ?: "",
                description = json["desc"]?.toString() ?: json["description"]?.toString() ?: json["vod_blurb"]?.toString() ?: "",
                sourceKey = key,
                sourceTitle = title,
                sourceCount = 1
            )
            val eps = (json["episodes"] as? List<*>)?.mapNotNull { ep ->
                val e = ep as? Map<*, *> ?: return@mapNotNull null
                val rawUrlObj = e["url"] ?: e["flag"] ?: e["play"] ?: ""
                val fStr = if (rawUrlObj is String) rawUrlObj else gson.toJson(rawUrlObj)
                val mapFlag = (rawUrlObj as? Map<*, *>)?.mapKeys { it.key.toString() }?.mapValues { it.value.toString() } ?: emptyMap()
                Episode(
                    id = e["id"]?.toString() ?: "",
                    name = e["name"]?.toString() ?: "",
                    route = e["route"]?.toString() ?: "",
                    flagStr = fStr,
                    flag = mapFlag
                )
            }?.distinctBy { it.name.trim() } ?: emptyList()
            DetailResult(item, eps)
        } catch (_: Exception) {
            DetailResult(defaultItem, emptyList())
        }
    }

    fun related(id: String): List<SourceItem> {
        return parseItems(exports.related(id))
    }

    fun play(flagStr: String): PlayResult {
        return try {
            val flagJson = if (flagStr.startsWith("{") || flagStr.startsWith("[")) {
                flagStr
            } else {
                gson.toJson(flagStr)
            }
            val raw = exports.play(flagJson).trim()
            if (raw.isBlank() || raw == "{}" || raw == "null") {
                SourceLogManager.error(key, "播放解析", "源返回空播放地址", "flag=${flagStr.take(240)}")
                return PlayResult(url = "", type = "auto")
            }
            if (raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("//")) {
                logResolvedMedia(raw)
                return PlayResult(url = resolvePlayUrl(raw, null), type = "auto")
            }
            val element = com.google.gson.JsonParser.parseString(raw)
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                val referer = obj.get("referer")?.asString
                val type = obj.get("type")?.asString ?: "auto"
                val url = resolvePlayUrl(obj.get("url")?.asString ?: "", referer)
                val error = obj.get("error")?.asString ?: obj.get("_server_msg")?.asString
                val startSec = obj.get("startSec")?.asLong
                    ?: (obj.get("startMs")?.asLong ?: 0L) / 1000
                    ?: (obj.get("start")?.asDouble ?: 0.0).toLong()
                val headerMap = obj.get("headers")?.let { h ->
                    if (h.isJsonObject) {
                        h.asJsonObject.entrySet().associate { it.key to it.value.asString }
                    } else if (h.isJsonPrimitive) {
                        try {
                            val parsed = com.google.gson.JsonParser.parseString(h.asString).asJsonObject
                            parsed.entrySet().associate { it.key to it.value.asString }
                        } catch (_: Exception) { null }
                    } else null
                }
                // Several complete remote scripts (notably guazi) return
                // userAgent/referer/origin/cookie as top-level fields instead
                // of a headers object. Preserve them for ExoPlayer.
                val mergedHeaders = (headerMap ?: emptyMap()).toMutableMap()
                val ua = obj.get("userAgent")?.asString
                    ?: obj.get("ua")?.asString
                if (!ua.isNullOrBlank() && mergedHeaders.keys.none { it.equals("User-Agent", true) }) {
                    mergedHeaders["User-Agent"] = ua
                }
                val origin = obj.get("origin")?.asString
                if (!origin.isNullOrBlank() && mergedHeaders.keys.none { it.equals("Origin", true) }) {
                    mergedHeaders["Origin"] = origin
                }
                val cookie = obj.get("cookie")?.asString
                if (!cookie.isNullOrBlank() && mergedHeaders.keys.none { it.equals("Cookie", true) }) {
                    mergedHeaders["Cookie"] = cookie
                }
                // Source scripts may return several concrete streams in
                // `resolutions`. Preserve them so the player can really
                // switch URL instead of only changing an ExoPlayer constraint
                // on the already-selected stream.
                val qualities = obj.get("resolutions")
                    ?.takeIf { it.isJsonArray }
                    ?.asJsonArray
                    ?.mapNotNull { item ->
                        if (!item.isJsonObject) return@mapNotNull null
                        val q = item.asJsonObject
                        val qUrl = resolvePlayUrl(q.get("url")?.asString?.trim().orEmpty(), referer)
                        if (qUrl.isBlank()) return@mapNotNull null
                        QualityOption(
                            name = q.get("name")?.asString?.trim().orEmpty().ifBlank { "清晰度" },
                            url = qUrl,
                            type = q.get("type")?.asString?.trim().orEmpty().ifBlank { "auto" }
                        )
                    }
                    ?.distinctBy { it.name to it.url }
                    .orEmpty()
                logResolvedMedia(url)
                PlayResult(
                    url = url,
                    type = type,
                    headers = mergedHeaders.takeIf { it.isNotEmpty() },
                    referer = referer,
                    qualities = qualities,
                    error = error,
                    startSec = startSec
                )
            } else {
                PlayResult(url = raw, type = "auto")
            }
        } catch (e: Exception) {
            SourceLogManager.error(key, "播放解析", "播放结果解析异常: ${e.message}", "flag=${flagStr.take(240)}")
            PlayResult(url = "", type = "auto")
        }
    }

    private fun resolvePlayUrl(url: String, referer: String?): String {
        if (url.isBlank()) return url
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("/") -> referer?.toHttpUrlOrNull()
                ?.let { "${it.scheme}://${it.host}${url}" } ?: url
            else -> url
        }
    }

    private fun logResolvedMedia(url: String) {
        if (url.isBlank()) return
        val parsed = url.toHttpUrlOrNull()
        val safeLocation = parsed?.let { "${it.scheme}://${it.host}${it.encodedPath}" }
            ?: "unparseable-url"
        Log.i("SourceAdapter", "[$key] play resolved $safeLocation")
    }

    private fun parseItems(raw: String): List<SourceItem> {
        if (raw.isBlank() || raw == "null") return emptyList()
        return try {
            val element = com.google.gson.JsonParser.parseString(raw)
            val jsonArray = when {
                element.isJsonArray -> element.asJsonArray
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    when {
                        obj.has("list") && obj.get("list").isJsonArray -> obj.getAsJsonArray("list")
                        obj.has("data") && obj.get("data").isJsonArray -> obj.getAsJsonArray("data")
                        obj.has("items") && obj.get("items").isJsonArray -> obj.getAsJsonArray("items")
                        obj.has("videoList") && obj.get("videoList").isJsonArray -> obj.getAsJsonArray("videoList")
                        else -> null
                    }
                }
                else -> null
            } ?: return emptyList()

            jsonArray.mapNotNull { itemElem ->
                if (!itemElem.isJsonObject) return@mapNotNull null
                val obj = itemElem.asJsonObject
                val id = obj.get("id")?.asString ?: obj.get("vod_id")?.asString ?: obj.get("url")?.asString ?: return@mapNotNull null
                val rawTitle = obj.get("title")?.asString ?: obj.get("name")?.asString ?: obj.get("vod_name")?.asString ?: ""
                val cleanTitle = rawTitle.replace("<[^>]+>".toRegex(), "").trim()
                if (cleanTitle.isEmpty()) return@mapNotNull null

                val cover = obj.get("cover")?.asString ?: obj.get("pic")?.asString ?: obj.get("thumb")?.asString ?: obj.get("vod_pic")?.asString ?: ""
                val year = obj.get("year")?.asString ?: obj.get("vod_year")?.asString ?: ""
                val kind = obj.get("kind")?.asString ?: obj.get("type")?.asString ?: obj.get("vod_class")?.asString ?: ""
                val tags = listOf("tags", "tag", "genre", "class", "vod_class", "type")
                    .flatMap { field ->
                        val value = obj.get(field) ?: return@flatMap emptyList()
                        if (value.isJsonArray) {
                            value.asJsonArray.mapNotNull { it.takeIf { v -> v.isJsonPrimitive }?.asString }
                        } else {
                            listOf(value.takeIf { it.isJsonPrimitive }?.asString.orEmpty())
                        }
                    }
                    .flatMap { it.split(Regex("[,，、/|·\\s]+")) }
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                val status = obj.get("status")?.asString ?: obj.get("remarks")?.asString ?: obj.get("vod_remarks")?.asString ?: ""
                val desc = obj.get("desc")?.asString ?: obj.get("description")?.asString ?: obj.get("vod_blurb")?.asString ?: ""
                val score = obj.get("score")?.asString ?: obj.get("vod_score")?.asString ?: ""

                SourceItem(
                    id = id,
                    title = cleanTitle,
                    year = year,
                    kind = kind,
                    tags = tags,
                    status = status,
                    score = score,
                    cover = cover,
                    description = desc,
                    sourceKey = key,
                    sourceTitle = title,
                    sourceCount = 1
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("SourceAdapter", "parseItems failed for $key: ${e.message}")
            emptyList()
        }
    }
}
