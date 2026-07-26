@file:OptIn(ExperimentalMaterial3Api::class)

package com.juying.app

import android.app.Application
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juying.app.source.*
import com.juying.app.ui.EmbeddedVideoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger

// ── Color scheme matching web globals.css ──
object AppColors {
    val bg = Color(0xFF090E17)
    val panel = Color(0xFF101926)
    val panel2 = Color(0xFF162436)
    val text = Color(0xFFF3F7FC)
    val muted = Color(0xFF8B9AAF)
    val cyan = Color(0xFF43D5E8)
    val purple = Color(0xFFA855F7)
    val orange = Color(0xFFFFB257)
    val rose = Color(0xFFFF7F9E)
    val green = Color(0xFF4ADE80)
}

@Composable
fun LoadingSpinner(modifier: Modifier = Modifier, color: Color = AppColors.cyan) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "angle"
    )
    Icon(
        imageVector = Icons.Default.Refresh,
        contentDescription = "加载中",
        tint = color,
        modifier = modifier.graphicsLayer { rotationZ = angle }
    )
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val sourceManager = SourceManager(application)
    private val storageManager = StorageManager(application)
    private var isAppInitialized = false
    private var playerReturnView = "home"
    private var pendingEpisodeName: String? = null

    // Cached pool of home section items — reused by fetchLibrary() to avoid re-fetching
    private var cachedHomePool: List<SourceItem> = emptyList()
    // Cancels the previous search when a new one starts
    private var searchJob: Job? = null

    var view by mutableStateOf("home") // home, library, player, profile
    var query by mutableStateOf("")
    var isSearchActive by mutableStateOf(false)
    var items by mutableStateOf<List<SourceItem>>(emptyList())
    var homeSections by mutableStateOf<List<HomeSection>>(emptyList())

    // Player & Detail State
    var activeDetail by mutableStateOf<DetailResult?>(null)
    var currentEpisodeIndex by mutableStateOf(0)
    var currentPlayResult by mutableStateOf<PlayResult?>(null)
    var isPlayLoading by mutableStateOf(false)
    var playError by mutableStateOf<String?>(null)
    var episodeCacheProgress by mutableStateOf<String?>(null)
    var downloadProgress by mutableStateOf<String?>(null)

    // Multi-source alternative details for "换源"
    var alternativeDetails by mutableStateOf<List<Pair<String, DetailResult>>>(emptyList())
    var activeAlternativeIndex by mutableStateOf(0)

    var loading by mutableStateOf(false)
    var notice by mutableStateOf("正在连接已收录来源...")

    // Source config list state for UI
    var sourcesState by mutableStateOf<List<SourceConfig>>(emptyList())

    // History and Favorites
    var historyList by mutableStateOf<List<HistoryItem>>(emptyList())
    var favoritesList by mutableStateOf<List<SourceItem>>(emptyList())
    var commentDraft by mutableStateOf("")
    var comments by mutableStateOf<List<String>>(emptyList())

    fun addComment() {
        val text = commentDraft.trim()
        if (text.isNotEmpty()) {
            comments = comments + text
            commentDraft = ""
        }
    }

    // ── Library filters matching user spec ──
    var activeKind by mutableStateOf("全部")
    var activeGenre by mutableStateOf("全部")
    var activeYear by mutableStateOf("全部")
    var activeStatus by mutableStateOf("全部")
    var activeSort by mutableStateOf("recent")
    var activeSource by mutableStateOf("全部")

    var totalLibrary by mutableStateOf(0)
    var page by mutableStateOf(1)

    val kinds = listOf("全部", "日漫", "国漫", "剧场版", "欧美")
    val genres = listOf(
        "全部", "热血", "奇幻", "战斗", "穿越", "后宫", "恋爱", "校园", "日常",
        "治愈", "搞笑", "悬疑", "科幻", "冒险", "魔法", "机战", "推理", "运动",
        "音乐", "偶像", "职场", "历史", "美食", "萌系", "百合", "耽美", "泡面番"
    )
    val years = listOf("全部") + (2026 downTo 2003).map { it.toString() } + "更早"
    val statuses = listOf("全部", "连载中", "已完结")
    val sorts = listOf("recent" to "最近更新", "hot" to "多源热门", "score" to "高分好评")
    val sourceOptions get() = listOf("全部") + sourcesState.map { it.title.ifEmpty { it.key } }

    fun initApp() {
        if (isAppInitialized) return
        isAppInitialized = true
        reloadStorageData()
        reloadSourcesState()
        viewModelScope.launch {
            withContext(Dispatchers.Main) { notice = "正在加载视频源..." }
            withContext(Dispatchers.IO) { sourceManager.init() }
            loadHomeInternal()
            viewModelScope.launch(Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                    RemoteSourceFetcher.syncAll(getApplication<Application>())
                }
                sourceManager.init()
                loadHomeInternal()
            }
        }
    }

    fun reloadSourcesState() {
        sourcesState = sourceManager.rawSources
    }

    fun toggleSource(key: String) {
        sourceManager.toggleSourceEnabled(key)
        reloadSourcesState()
        loadHome()
        reloadStorageData()
    }
    
    fun importSource(key: String, title: String, code: String): Boolean {
        return sourceManager.importCustomSource(key, title, code)
    }
    
    fun testSource(key: String): Long {
        return sourceManager.testSourceSpeed(key)
    }

    fun reloadStorageData() {
        historyList = storageManager.getHistory()
        favoritesList = storageManager.getFavorites()
    }

    fun loadHome() {
        viewModelScope.launch {
            loadHomeInternal()
        }
    }

    private suspend fun loadHomeInternal() {
        val presetSections = mutableMapOf(
            "🔥 热门推荐" to mutableListOf<SourceItem>(),
            "🇯🇵 日漫精选" to mutableListOf<SourceItem>(),
            "🇨🇳 国漫精粹" to mutableListOf<SourceItem>(),
            "🎬 剧场版/电影" to mutableListOf<SourceItem>(),
            "✨ 最新更新" to mutableListOf<SourceItem>()
        )

        fun buildHomeSectionsList(): List<HomeSection> {
            return presetSections.mapNotNull { (title, list) ->
                val merged = SourceManager.mergeSearchItems(list)
                if (merged.isEmpty()) null
                else HomeSection(title = title, key = title, items = merged)
            }
        }

        val adapters = sourceManager.allAdapters()
        withContext(Dispatchers.Main) {
            loading = true
            notice = "正在并发动态加载多源视频..."
            if (cachedHomePool.isNotEmpty()) {
                cachedHomePool.forEach { item ->
                    val kind = item.kind + " " + item.title
                    when {
                        kind.contains("日漫") || kind.contains("日本") -> presetSections["🇯🇵 日漫精选"]?.add(item)
                        kind.contains("国漫") || kind.contains("国产") -> presetSections["🇨🇳 国漫精粹"]?.add(item)
                        kind.contains("剧场") || kind.contains("电影") -> presetSections["🎬 剧场版/电影"]?.add(item)
                        else -> {
                            presetSections["🔥 热门推荐"]?.add(item)
                            presetSections["✨ 最新更新"]?.add(item)
                        }
                    }
                }
                val initialList = buildHomeSectionsList()
                if (initialList.isNotEmpty()) {
                    homeSections = initialList
                    if (!isSearchActive) items = cachedHomePool
                    loading = false
                }
            }
        }

        // Concurrent streaming: each source populates the preset sections as data arrives
        withContext(Dispatchers.IO) {
            coroutineScope {
                adapters.forEach { adapter ->
                    launch {
                        // 1. Try JS adapter's homeSections()
                        val jsSections = try {
                            withTimeout(10_000L) { adapter.homeSections() }
                        } catch (_: Exception) { emptyList() }

                        // 2. If homeSections() is empty, fallback to empty query or popular query
                        val fetchedItems = if (jsSections.isNotEmpty()) {
                            jsSections.flatMap { it.items }
                        } else {
                            val r1 = try {
                                withTimeout(10_000L) { adapter.search("", 1) }
                            } catch (_: Exception) { emptyList() }
                            if (r1.isNotEmpty()) r1 else {
                                try {
                                    withTimeout(10_000L) { adapter.search("漫", 1) }
                                } catch (_: Exception) { emptyList() }
                            }
                        }

                        if (fetchedItems.isNotEmpty()) {
                            synchronized(presetSections) {
                                fetchedItems.forEach { item ->
                                    val kind = item.kind + " " + item.title + " " + item.tags.joinToString(" ")
                                    when {
                                        kind.contains("日漫") || kind.contains("日本") -> presetSections["🇯🇵 日漫精选"]?.add(item)
                                        kind.contains("国漫") || kind.contains("国产") -> presetSections["🇨🇳 国漫精粹"]?.add(item)
                                        kind.contains("剧场") || kind.contains("电影") -> presetSections["🎬 剧场版/电影"]?.add(item)
                                        else -> {
                                            presetSections["🔥 热门推荐"]?.add(item)
                                            presetSections["✨ 最新更新"]?.add(item)
                                        }
                                    }
                                }
                            }

                            val updatedList = synchronized(presetSections) { buildHomeSectionsList() }
                            withContext(Dispatchers.Main) {
                                homeSections = updatedList
                                cachedHomePool = updatedList.flatMap { it.items }
                                if (!isSearchActive) items = cachedHomePool
                                notice = "已成功加载 ${updatedList.size} 个视频分区"
                                loading = false
                            }
                        }
                    }
                }
            }
        }

        // Guaranteed fallback: if homeSections is empty, search popular terms to guarantee home cards!
        if (homeSections.isEmpty()) {
            withContext(Dispatchers.IO) {
                val fallbackItems = mutableListOf<SourceItem>()
                val terms = listOf("海贼", "凡人", "鬼灭", "斗罗", "咒术")
                for (term in terms) {
                    for (adapter in adapters) {
                        try {
                            val res = adapter.search(term, 1)
                            if (res.isNotEmpty()) {
                                fallbackItems.addAll(res)
                                break
                            }
                        } catch (_: Exception) {}
                    }
                    if (fallbackItems.size >= 12) break
                }
                if (fallbackItems.isNotEmpty()) {
                    val merged = SourceManager.mergeSearchItems(fallbackItems)
                    val section = HomeSection(title = "🔥 热门推荐", key = "hot", items = merged)
                    withContext(Dispatchers.Main) {
                        homeSections = listOf(section)
                        cachedHomePool = merged
                        items = merged
                        loading = false
                        notice = "已为你加载 ${merged.size} 部热门作品"
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            loading = false
            if (homeSections.isEmpty()) {
                notice = "视频源动态加载完成"
            }
        }
    }

    fun search(keyword: String) {
        if (keyword.isBlank()) {
            isSearchActive = false
            items = cachedHomePool
            return
        }
        // Cancel any previous in-flight search
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            withContext(Dispatchers.Main) {
                loading = true
                isSearchActive = true
                notice = "正在搜索「$keyword」..."
            }

            val targetAdapters = if (activeSource != "全部") {
                sourceManager.allAdapters().filter {
                    it.title.equals(activeSource, ignoreCase = true) ||
                    it.config.key.equals(activeSource, ignoreCase = true)
                }
            } else {
                sourceManager.allAdapters()
            }

            // Cache-first: return instantly if we searched this before
            val cacheKey = "search:$keyword:$activeSource"
            val cached = ResultCache.getSearch(cacheKey)
            if (cached != null) {
                withContext(Dispatchers.Main) {
                    items = cached
                    notice = "已找到 ${cached.size} 部作品 (缓存)"
                    loading = false
                }
                return@launch
            }

            // Progressive streaming: each source fires a UI update the moment it returns
            val accumulated = mutableListOf<SourceItem>()
            val accLock = Any()
            val completedCount = AtomicInteger(0)
            val totalCount = targetAdapters.size

            withContext(Dispatchers.IO) {
                coroutineScope {
                    targetAdapters.forEach { adapter ->
                        launch {
                            val results = try {
                                withTimeout(12_000L) { adapter.search(keyword, 1) }
                            } catch (_: Exception) { emptyList() }

                            // Thread-safe accumulation + compute merge/sort on IO thread
                            val snapshot = synchronized(accLock) {
                                accumulated.addAll(results)
                                accumulated.toList()
                            }
                            val n = completedCount.incrementAndGet()
                            val merged = SourceManager.mergeSearchItems(snapshot)
                            val sorted = SourceManager.sortByRelevance(merged, keyword)

                            // Update UI immediately — first results appear when fastest source returns
                            withContext(Dispatchers.Main) {
                                items = sorted
                                val progress = if (n < totalCount) "$n/$totalCount 源" else "全部 $totalCount 源"
                                notice = "已找到 ${sorted.size} 部作品 ($progress 已完成)"
                                if (n == 1) loading = false  // Hide spinner after very first result
                            }
                        }
                    }
                }
            }

            // If the exact query produced only a handful of hits, ask each
            // source for a compact alias as well. This is what surfaces
            // “第一季/第二季/剧场版/番外” and common spacing/typo variants
            // while the relevance scorer keeps the exact title first.
            val primaryCount = synchronized(accLock) { accumulated.size }
            val supplementalQueries = SourceManager.searchVariants(keyword)
                .drop(1)
                .take(2)
            if (primaryCount < 12 && supplementalQueries.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    coroutineScope {
                        supplementalQueries.forEach { supplement ->
                            targetAdapters.forEach { adapter ->
                                launch {
                                    val results = try {
                                        withTimeout(8_000L) { adapter.search(supplement, 1) }
                                    } catch (_: Exception) { emptyList() }
                                    synchronized(accLock) {
                                        accumulated.addAll(results)
                                    }
                                }
                            }
                        }
                    }
                }
                val snapshot = synchronized(accLock) { accumulated.toList() }
                val sorted = SourceManager.sortByRelevance(
                    SourceManager.mergeSearchItems(snapshot),
                    keyword
                )
                withContext(Dispatchers.Main) {
                    items = sorted
                    notice = "已按相关度找到 ${sorted.size} 部作品"
                }
            }

            // Fuzzy fallback: if still too few results, retry with shorter queries
            val currentItems = SourceManager.mergeSearchItems(
                synchronized(accLock) { accumulated.toList() }
            )
            if (currentItems.size < 5 && keyword.length > 2) {
                val shorter = listOf(
                    keyword.take((keyword.length * 0.7).toInt().coerceAtLeast(2)),
                    keyword.take(keyword.length / 2),
                    keyword.take(2)
                ).distinct().filter { it != keyword && it.isNotBlank() }

                for (q in shorter) {
                    val fuzzyResults = mutableListOf<SourceItem>()
                    withContext(Dispatchers.IO) {
                        coroutineScope {
                            targetAdapters.forEach { adapter ->
                                launch {
                                    val r = try {
                                        withTimeout(8_000L) { adapter.search(q, 1) }
                                    } catch (_: Exception) { emptyList() }
                                    synchronized(accLock) {
                                        accumulated.addAll(r)
                                        fuzzyResults.addAll(r)
                                    }
                                }
                            }
                        }
                    }
                    if (fuzzyResults.isNotEmpty()) {
                        val snapshot = synchronized(accLock) { accumulated.toList() }
                        val merged = SourceManager.mergeSearchItems(snapshot)
                        val sorted = SourceManager.sortByRelevance(merged, keyword)
                        withContext(Dispatchers.Main) {
                            items = sorted
                            notice = "已找到 ${sorted.size} 部作品"
                        }
                        if (items.size >= 10) break
                    }
                }
            }

            withContext(Dispatchers.Main) {
                loading = false
                val finalItems = items
                if (finalItems.isEmpty()) {
                    notice = "未找到「$keyword」匹配内容，试试短一点的关键词"
                } else {
                    // Cache the final merged result
                    ResultCache.putSearch(cacheKey, finalItems)
                }
            }
        }
    }

    fun clearSearch() {
        query = ""
        isSearchActive = false
        items = cachedHomePool
        notice = "已清除搜索"
    }

    fun applyFilter(
        kind: String? = null,
        genre: String? = null,
        year: String? = null,
        status: String? = null,
        sort: String? = null,
        source: String? = null
    ) {
        kind?.let { activeKind = it }
        genre?.let { activeGenre = it }
        year?.let { activeYear = it }
        status?.let { activeStatus = it }
        sort?.let { activeSort = it }
        source?.let { activeSource = it }
        page = 1
        fetchLibrary()
    }

    private fun serverCategory(adapter: SourceAdapter, kind: String): String {
        if (kind == "全部") return ""
        return when (kind) {
            "日漫" -> when (adapter.key.lowercase()) {
                "lanerc", "jinpai", "sanqiu" -> "日本"
                "shuangxing" -> "@1"
                else -> "日漫"
            }
            "国漫" -> when (adapter.key.lowercase()) {
                "lanerc", "jinpai", "sanqiu" -> "大陆"
                "shuangxing" -> "@2"
                else -> "国漫"
            }
            "剧场版" -> "剧场版"
            "欧美" -> when (adapter.key.lowercase()) {
                "lanerc", "jinpai", "sanqiu" -> "欧美"
                else -> "欧美"
            }
            else -> kind
        }
    }

    private fun serverFilterMap(adapter: SourceAdapter): Map<String, String> {
        val result = linkedMapOf<String, String>()
        if (activeGenre != "全部") {
            // Different scripts use different names for the same server field.
            result["class"] = activeGenre
            result["genre"] = activeGenre
            result["type"] = activeGenre
            result["vod_class"] = activeGenre
        }
        if (activeYear != "全部" && activeYear != "更早") {
            result["year"] = activeYear
        }
        if (activeStatus != "全部") {
            result["status"] = activeStatus
            result["state"] = activeStatus
            result["remarks"] = activeStatus
        }

        val sort = when (adapter.key.lowercase()) {
            "lanerc" -> if (activeSort == "score") "按评分" else "按时间"
            "yzx" -> when (activeSort) {
                "hot" -> "热度"
                "score" -> "评分"
                else -> "最新"
            }
            "akianime" -> when (activeSort) {
                "hot" -> "hits"
                "score" -> "score"
                else -> "time"
            }
            "sanqiu" -> if (activeSort == "score") "score" else if (activeSort == "hot") "hits" else "time"
            else -> when (activeSort) {
                "hot" -> "hits"
                "score" -> "score"
                else -> "time"
            }
        }
        result["sort"] = sort
        result["by"] = sort
        result["order"] = sort
        result["extend_sort"] = sort
        return result
    }

    var libraryItems by mutableStateOf<List<SourceItem>>(emptyList())
    var libraryPage by mutableStateOf(1)
    var libraryHasMore by mutableStateOf(true)
    var libraryLoadingMore by mutableStateOf(false)
    private var libraryJob: Job? = null
    private var libraryGeneration = 0

    private fun applyLibraryFiltersFast(input: List<SourceItem>): List<SourceItem> {
        var filtered = input
        if (activeKind != kinds.first()) {
            val key = activeKind.lowercase()
            filtered = filtered.filter { item ->
                val metadata = "${item.kind} ${item.tags.joinToString(" ")}".lowercase()
                metadata.isBlank() || metadata.contains(key)
            }
        }
        if (activeGenre != genres.first()) {
            val key = activeGenre.lowercase()
            filtered = filtered.filter { item ->
                val metadata = "${item.kind} ${item.tags.joinToString(" ")}".lowercase()
                metadata.isBlank() || metadata.contains(key)
            }
        }
        if (activeYear != years.first() && activeYear != years.last()) {
            filtered = filtered.filter { item ->
                item.year.isBlank() || item.year.contains(activeYear)
            }
        } else if (activeYear == years.last()) {
            filtered = filtered.filter {
                it.year.toIntOrNull()?.let { year -> year < 2003 } ?: true
            }
        }
        if (activeStatus != statuses.first()) {
            filtered = filtered.filter { item ->
                val status = item.status
                status.isBlank() ||
                    (activeStatus == statuses[1] && (status.contains("\u8fde\u8f7d") || status.contains("\u66f4\u65b0"))) ||
                    (activeStatus == statuses[2] && (status.contains("\u5b8c\u7ed3") || status.contains("\u5168\u96c6")))
            }
        }
        return when (activeSort) {
            "hot" -> filtered.sortedWith(compareByDescending<SourceItem> { it.sourceCount }
                .thenByDescending { it.score.toDoubleOrNull() ?: 0.0 })
            "score" -> filtered.sortedByDescending { it.score.toDoubleOrNull() ?: 0.0 }
            else -> filtered
        }
    }

    fun fetchLibrary(reset: Boolean = true) {
        libraryJob?.cancel()
        val requestId = ++libraryGeneration
        libraryJob = viewModelScope.launch {
            if (reset) {
                libraryPage = 1
                val cached = applyLibraryFiltersFast(cachedHomePool)
                libraryItems = cached
                libraryHasMore = true
                withContext(Dispatchers.Main) {
                    items = cached
                    totalLibrary = cached.size
                    loading = false
                    libraryLoadingMore = false
                    notice = "正在检索多源片库..."
                }
            } else {
                if (libraryLoadingMore || !libraryHasMore) return@launch
                withContext(Dispatchers.Main) {
                    libraryLoadingMore = true
                    notice = "正在预获取第 ${libraryPage} 页视频..."
                }
            }

            val currentPage = libraryPage
            val currentExisting = libraryItems

            val allAvailable = sourceManager.allAdapters()
            val targetAdapters = if (activeSource != "全部") {
                allAvailable.filter { it.title.equals(activeSource, ignoreCase = true) || it.config.key.equals(activeSource, ignoreCase = true) }
            } else {
                allAvailable
            }

            val fetchedNewItems = mutableListOf<SourceItem>()
            val fetchLock = Any()
            val completedSources = AtomicInteger(0)

            withContext(Dispatchers.IO) {
                coroutineScope {
                    targetAdapters.forEach { adapter ->
                        launch {
                            val rawItems = try {
                                withTimeout(12_000L) {
                                    // Always try the source's native filter endpoint
                                    // first, including the unfiltered “recent” view.
                                    var res = adapter.searchFiltered(
                                        serverCategory(adapter, activeKind),
                                        serverFilterMap(adapter),
                                        currentPage
                                    )
                                    if (res.isEmpty()) {
                                        val queryTerm = when {
                                            activeGenre != "全部" -> activeGenre
                                            activeKind != "全部" -> activeKind
                                            else -> "漫"
                                        }
                                        res = adapter.search(queryTerm, currentPage)
                                    }
                                    res
                                }
                            } catch (_: Exception) { emptyList() }

                            val snapshot = synchronized(fetchLock) {
                                fetchedNewItems.addAll(rawItems)
                                fetchedNewItems.toList()
                            }
                            val streamed = applyLibraryFiltersFast(
                                SourceManager.mergeSearchItems(snapshot)
                            )
                            val done = completedSources.incrementAndGet()
                            withContext(Dispatchers.Main) {
                                if (requestId != libraryGeneration) return@withContext
                                val existingKeys = currentExisting
                                    .map { SourceManager.normalizeTitle(it.title) }
                                    .toSet()
                                val newItems = streamed.filter {
                                    SourceManager.normalizeTitle(it.title) !in existingKeys
                                }
                                val merged = if (reset) {
                                    SourceManager.mergeSearchItems(currentExisting + streamed)
                                } else {
                                    currentExisting + newItems
                                }
                                libraryItems = merged
                                items = merged
                                totalLibrary = merged.size
                                loading = false
                                libraryLoadingMore = false
                                notice = "received ${merged.size} items; ${done}/${targetAdapters.size} sources complete"
                            }
                        }
                    }
                }
            }

            val dedupedBatch = SourceManager.mergeSearchItems(fetchedNewItems)

            // Apply filtering logic
            var filtered = dedupedBatch
            if (activeKind != "全部") {
                val kindLower = activeKind.lowercase()
                filtered = filtered.filter { item ->
                    val kindFirst = item.kind.lowercase().split("[\\s,，、/|·]+".toRegex()).firstOrNull() ?: ""
                    val metadata = "${item.kind} ${item.tags.joinToString(" ")}".lowercase()
                    metadata.isBlank() || kindFirst.contains(kindLower) || metadata.contains(kindLower)
                }
            }
            if (activeGenre != "全部") {
                val genreLower = activeGenre.lowercase()
                filtered = filtered.filter { item ->
                    val metadata = "${item.kind} ${item.tags.joinToString(" ")}".lowercase().trim()
                    metadata.isBlank() || metadata.contains(genreLower)
                }
            }
            if (activeStatus != "全部") {
                filtered = filtered.filter { item ->
                    val s = item.status
                    when {
                        s.isBlank() -> true
                        activeStatus == "连载中" -> s.contains("连载") || s.contains("更新") || s.contains("话") || s.contains("集")
                        activeStatus == "已完结" -> s.contains("完结") || s.contains("全") || s.contains("0集")
                        else -> true
                    }
                }
            }
            if (activeYear != "全部" && activeYear != "更早") {
                filtered = filtered.filter { item -> item.year.isBlank() || item.year.contains(activeYear) }
            } else if (activeYear == "更早") {
                filtered = filtered.filter { item ->
                    val y = item.year.toIntOrNull()
                    y == null || y < 2003
                }
            }

            filtered = when (activeSort) {
                "hot" -> filtered.sortedWith(compareByDescending<SourceItem> { it.sourceCount }.thenByDescending { it.score.toDoubleOrNull() ?: 0.0 })
                "score" -> filtered.sortedByDescending { it.score.toDoubleOrNull() ?: 0.0 }
                else -> filtered
            }

            // Incremental append logic (增量添加展示):
            val existingKeys = currentExisting.map { SourceManager.normalizeTitle(it.title) }.toSet()
            val trulyNewItems = filtered.filter { SourceManager.normalizeTitle(it.title) !in existingKeys }

            val finalUpdatedList = if (reset) {
                if (filtered.isNotEmpty()) filtered else dedupedBatch
            } else {
                currentExisting + trulyNewItems
            }

            withContext(Dispatchers.Main) {
                libraryItems = finalUpdatedList
                items = finalUpdatedList
                totalLibrary = finalUpdatedList.size
                loading = false
                libraryLoadingMore = false

                if (!reset && trulyNewItems.isEmpty()) {
                    libraryHasMore = false
                    notice = "已为你展示全网多源片库全部作品 (共 ${finalUpdatedList.size} 部)"
                } else {
                    libraryPage = currentPage + 1
                    notice = "已检索到 ${finalUpdatedList.size} 部作品"
                }
            }
        }
    }

    fun loadNextPage() {
        fetchLibrary(reset = false)
    }

    fun openMovie(item: SourceItem, preferredEpisodeName: String? = null) {
        playerReturnView = when (view) {
            "library" -> "library"
            "profile" -> "profile"
            else -> "home"
        }
        pendingEpisodeName = preferredEpisodeName
        viewModelScope.launch {
            // Older/merged home cards may contain "sourceA,sourceB". The item id
            // belongs to the first source, so resolve a real adapter identity.
            val primarySourceKey = item.sourceKey
                .split(',')
                .asSequence()
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() && sourceManager.getAdapter(it) != null }
                ?: item.sourceKey.trim()
            val playableItem = if (primarySourceKey != item.sourceKey) {
                item.copy(
                    sourceKey = primarySourceKey,
                    sourceTitle = item.sourceTitle.substringBefore('+').ifBlank { item.sourceTitle },
                    sourceCount = 1
                )
            } else item

            withContext(Dispatchers.Main) {
                loading = true
                alternativeDetails = emptyList()
                currentPlayResult = null
                notice = "正在解析「${playableItem.title}」剧集列表..."
            }
            val adapter = sourceManager.getAdapter(primarySourceKey)

            // Detail cache-first (30 min TTL) — cached hit shows episodes in <5ms
            val detailCacheKey = "$primarySourceKey:${playableItem.id}"
            val detailResult = ResultCache.getDetail(detailCacheKey)
                ?: run {
                    val fresh = withContext(Dispatchers.IO) {
                        if (adapter != null) {
                            try { adapter.detail(playableItem.id) } catch (_: Exception) { null }
                        } else null
                    } ?: DetailResult(playableItem, emptyList())
                    ResultCache.putDetail(detailCacheKey, fresh)
                    fresh
                }

            // Show player immediately — don't wait for alt sources
            withContext(Dispatchers.Main) {
                loading = false
                activeDetail = detailResult
                activeAlternativeIndex = 0
                currentEpisodeIndex = detailResult.episodes.indexOfFirst { ep ->
                    pendingEpisodeName?.let { preferred ->
                        ep.name.equals(preferred, ignoreCase = true) ||
                            ep.name.contains(preferred, ignoreCase = true) ||
                            preferred.contains(ep.name, ignoreCase = true)
                    } ?: false
                }.takeIf { it >= 0 } ?: 0
                pendingEpisodeName = null
                view = "player"
                if (detailResult.episodes.isNotEmpty()) {
                    selectEpisode(currentEpisodeIndex)
                } else {
                    notice = "该作品暂无可用选集"
                }
            }

            // Pre-fetch episode 2 play URL in background (ep 1 is being fetched by selectEpisode above)
            val ep1 = detailResult.episodes.getOrNull(1)
            if (ep1 != null && adapter != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    val prefetchKey = "$primarySourceKey:${ep1.flagStr.take(200)}"
                    if (ResultCache.getPlay(prefetchKey) == null) {
                        try {
                            val r = withTimeout(15_000L) { adapter.play(ep1.flagStr) }
                            if (r.url.isNotEmpty()) ResultCache.putPlay(prefetchKey, r)
                        } catch (_: Exception) { }
                    }
                }
            }

            // Fetch alternative sources in background concurrently
            val itemTitle = playableItem.title
            val primaryKey = primarySourceKey
            viewModelScope.launch(Dispatchers.IO) {
                val altList = try {
                    coroutineScope {
                        val others = sourceManager.allAdapters().filter { it.key != primaryKey }
                        others.map { alt ->
                            async {
                                try {
                                    // Cache alt search results to avoid redundant calls
                                    val altSearchKey = "${alt.key}:search:$itemTitle"
                                    val altItems = ResultCache.getSearch(altSearchKey)
                                        ?: alt.search(itemTitle, 1).also {
                                            if (it.isNotEmpty()) ResultCache.putSearch(altSearchKey, it)
                                        }
                                    val matched = altItems.firstOrNull {
                                        SourceManager.normalizeTitle(it.title) == SourceManager.normalizeTitle(itemTitle)
                                    }
                                    if (matched != null) {
                                        val altDetailKey = "${alt.key}:${matched.id}"
                                        val altDetail = ResultCache.getDetail(altDetailKey)
                                            ?: alt.detail(matched.id).also {
                                                ResultCache.putDetail(altDetailKey, it)
                                            }
                                        if (altDetail.episodes.isNotEmpty()) alt.config.key to altDetail else null
                                    } else null
                                } catch (_: Exception) { null }
                            }
                        }.awaitAll().filterNotNull()
                    }
                } catch (_: Exception) { emptyList() }

                withContext(Dispatchers.Main) {
                    alternativeDetails = altList
                    if (altList.isNotEmpty()) {
                        notice = "已找到 ${altList.size + 1} 个可用数据源"
                    }
                }
            }
        }
    }

    fun selectEpisode(index: Int) {
        val detail = currentActiveDetail() ?: return
        val episodes = detail.episodes
        if (index !in episodes.indices) return

        currentEpisodeIndex = index
        val ep = episodes[index]
        val sourceKey = detail.item.sourceKey
        val adapter = sourceManager.getAdapter(sourceKey) ?: return

        viewModelScope.launch {
            val resolveStartedAt = System.nanoTime()
            withContext(Dispatchers.Main) {
                isPlayLoading = true
                playError = null
                notice = "正在解析「${ep.name}」播放地址..."
            }

            // Play URL cache-first (10 min TTL) — cached hit is instant (<1ms)
            val playCacheKey = "$sourceKey:${ep.flagStr.take(200)}"
            val cachedPlay = ResultCache.getPlay(playCacheKey)
            if (cachedPlay != null) {
                val elapsedMs = (System.nanoTime() - resolveStartedAt) / 1_000_000L
                withContext(Dispatchers.Main) {
                    isPlayLoading = false
                    currentPlayResult = cachedPlay
                    storageManager.addHistory(detail.item, ep.name, cachedPlay.url)
                    reloadStorageData()
                    notice = "已解析 ${ep.name}（${elapsedMs}ms）"
                    notice = "正在播放 ${ep.name}"
                }
                // Pre-fetch next episode while current plays
                prefetchAdjacentEpisodes(episodes, index, sourceKey, adapter)
                return@launch
            }

            val playResult = withContext(Dispatchers.IO) {
                try { adapter.play(ep.flagStr) } catch (e: Exception) {
                    android.util.Log.e("MainViewModel", "play failed: ${e.message}")
                    null
                }
            }

            withContext(Dispatchers.Main) {
                isPlayLoading = false
                if (playResult != null && playResult.url.isNotEmpty()) {
                    val elapsedMs = (System.nanoTime() - resolveStartedAt) / 1_000_000L
                    ResultCache.putPlay(playCacheKey, playResult)  // Cache for instant replay
                    currentPlayResult = playResult
                    storageManager.addHistory(detail.item, ep.name, playResult.url)
                    reloadStorageData()
                    notice = "已解析 ${ep.name}（${elapsedMs}ms）"
                    notice = "正在播放 ${ep.name}"
                } else {
                    playError = "播放地址解析失败"
                    notice = "播放地址解析失败 (${detail.item.sourceTitle})，试试「换源播放」"
                    if (alternativeDetails.isNotEmpty()) {
                        switchSource()
                    }
                }
            }

            // Pre-fetch adjacent episodes in background
            prefetchAdjacentEpisodes(episodes, index, sourceKey, adapter)
        }
    }

    /**
     * A CDN can return a URL that expires without carrying a recognizable
     * timestamp in its query string. Do not keep serving that URL from the
     * play cache after Media3 reports an error; the next tap will resolve it
     * again with fresh headers/tokens.
     */
    fun invalidateCurrentPlayCache() {
        val detail = currentActiveDetail() ?: return
        val ep = detail.episodes.getOrNull(currentEpisodeIndex) ?: return
        ResultCache.invalidatePlay("${detail.item.sourceKey}:${ep.flagStr.take(200)}")
        currentPlayResult = null
        playError = "视频加载失败，已清除旧地址，请点击本集重试"
    }

    /**
     * Pre-fetches the next (and optionally previous) episode play URL in the background.
     * Cached results make episode switching near-instant.
     */
    private fun prefetchAdjacentEpisodes(
        episodes: List<Episode>,
        currentIndex: Int,
        sourceKey: String,
        adapter: SourceAdapter
    ) {
        // Pre-fetch next 2 episodes
        for (offset in 1..2) {
            val nextIdx = currentIndex + offset
            if (nextIdx !in episodes.indices) break
            val nextEp = episodes[nextIdx]
            val nextKey = "$sourceKey:${nextEp.flagStr.take(200)}"
            if (ResultCache.getPlay(nextKey) != null) continue  // Already cached
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val r = withTimeout(15_000L) { adapter.play(nextEp.flagStr) }
                    if (r.url.isNotEmpty()) {
                        ResultCache.putPlay(nextKey, r)
                        android.util.Log.d("Prefetch", "预取第${nextEp.name}集完成")
                    }
                } catch (_: Exception) { }
            }
        }
    }

    /** Returns the currently active DetailResult (considering source switching) */
    private fun currentActiveDetail(): DetailResult? {
        return if (activeAlternativeIndex == 0) activeDetail
        else alternativeDetails.getOrNull(activeAlternativeIndex - 1)?.second
    }

    val displayedDetail: DetailResult?
        get() = currentActiveDetail()

    val availableSourceLabels: List<String>
        get() {
            val primary = activeDetail?.item?.sourceTitle
                ?.ifBlank { activeDetail?.item?.sourceKey }
                ?: "当前源"
            return listOf(primary) + alternativeDetails.map { (key, detail) ->
                detail.item.sourceTitle.ifBlank { key }
            }
        }

    /** Select an exact source index for the same title/episode. */
    fun selectSource(index: Int) {
        val total = 1 + alternativeDetails.size
        if (index !in 0 until total) return
        activeAlternativeIndex = index

        val newDetail = currentActiveDetail() ?: return

        // Select the closest episode number
        val currentEpNum = currentEpisodeIndex + 1
        val bestIdx = newDetail.episodes.indexOfFirst {
            it.name.toIntOrNull() == currentEpNum
        }.let { if (it >= 0) it else 0.coerceAtMost(newDetail.episodes.size - 1) }
        currentEpisodeIndex = bestIdx
        playError = null
        selectEpisode(bestIdx)
    }

    /** Legacy “换源” button behavior: cycle, while the chips use selectSource(). */
    fun switchSource() {
        val total = 1 + alternativeDetails.size
        if (total > 1) selectSource((activeAlternativeIndex + 1) % total)
    }

    fun retryPlay() {
        val detail = currentActiveDetail() ?: return
        if (detail.episodes.isNotEmpty()) {
            val ep = detail.episodes.getOrNull(currentEpisodeIndex)
            if (ep != null) ResultCache.invalidatePlay("${detail.item.sourceKey}:${ep.flagStr.take(200)}")
            selectEpisode(currentEpisodeIndex)
        }
    }

    fun goBackFromPlayer() {
        val destination = playerReturnView
        view = destination
        when (destination) {
            "library" -> {
                items = libraryItems
                reloadStorageData()
            }
            "profile" -> reloadStorageData()
        }
    }

    /**
     * Warm the in-memory play cache for every episode. Signed URLs are
     * intentionally rejected by ResultCache and will still be resolved fresh.
     */
    fun cacheCurrentEpisodes() {
        val detail = currentActiveDetail() ?: return
        val adapter = sourceManager.getAdapter(detail.item.sourceKey) ?: return
        if (episodeCacheProgress != null) return
        val episodes = detail.episodes
        viewModelScope.launch(Dispatchers.IO) {
            var cached = 0
            withContext(Dispatchers.Main) {
                episodeCacheProgress = "0/${episodes.size}"
            }
            episodes.forEachIndexed { index, ep ->
                val key = "${detail.item.sourceKey}:${ep.flagStr.take(200)}"
                if (ResultCache.getPlay(key) == null) {
                    try {
                        val result = withTimeout(15_000L) { adapter.play(ep.flagStr) }
                        if (result.url.isNotBlank()) {
                            ResultCache.putPlay(key, result)
                            if (ResultCache.getPlay(key) != null) cached++
                        }
                    } catch (_: Exception) { }
                } else {
                    cached++
                }
                withContext(Dispatchers.Main) {
                    episodeCacheProgress = "${index + 1}/${episodes.size}"
                }
            }
            withContext(Dispatchers.Main) {
                episodeCacheProgress = null
                notice = "已缓存 $cached/${episodes.size} 集可复用播放地址"
            }
        }
    }

    fun downloadCurrentEpisodes() {
        val detail = currentActiveDetail() ?: return
        val adapter = sourceManager.getAdapter(detail.item.sourceKey) ?: return
        if (downloadProgress != null) return
        val episodes = detail.episodes
        viewModelScope.launch(Dispatchers.IO) {
            val downloader = VideoDownloadManager(getApplication())
            var downloaded = 0
            episodes.forEachIndexed { index, episode ->
                withContext(Dispatchers.Main) {
                    downloadProgress = "${index + 1}/${episodes.size}"
                }
                try {
                    val play = withTimeout(20_000L) { adapter.play(episode.flagStr) }
                    if (play.url.isNotBlank()) {
                        val file = downloader.download(
                            play.url,
                            play.headers,
                            play.referer,
                            detail.item.title,
                            episode.name
                        )
                        if (file != null) downloaded++
                    }
                } catch (_: Exception) {
                    SourceLogManager.error(
                        adapter.key,
                        "download",
                        "download failed",
                        "episode=${episode.name}"
                    )
                }
            }
            withContext(Dispatchers.Main) {
                downloadProgress = null
                notice = "已下载 $downloaded/${episodes.size} 集到本地视频目录"
            }
        }
    }

    fun toggleFavorite(item: SourceItem) {
        storageManager.toggleFavorite(item)
        reloadStorageData()
    }

    fun isFavorite(item: SourceItem): Boolean {
        return storageManager.isFavorite(item)
    }

    fun clearHistory() {
        storageManager.clearHistory()
        reloadStorageData()
    }

    override fun onCleared() {
        sourceManager.close()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            LaunchedEffect(Unit) {
                vm.initApp()
            }
            JuyingApp(vm)
        }
    }
}

@Composable
fun JuyingApp(vm: MainViewModel) {
    MaterialTheme(colorScheme = darkColorScheme(
        background = AppColors.bg,
        surface = AppColors.panel,
        primary = AppColors.cyan,
    )) {
        Scaffold(
            bottomBar = {
                if (vm.view != "player") {
                    NavigationBar(containerColor = AppColors.panel) {
                        NavigationBarItem(
                            selected = vm.view == "home",
                            onClick = { vm.view = "home" },
                            icon = { Icon(Icons.Default.Home, null) },
                            label = { Text("首页") }
                        )
                        NavigationBarItem(
                            selected = vm.view == "library",
                            onClick = { vm.view = "library"; vm.fetchLibrary() },
                            icon = { Icon(Icons.Default.List, null) },
                            label = { Text("片库") }
                        )
                        NavigationBarItem(
                            selected = vm.view == "profile",
                            onClick = { vm.view = "profile"; vm.reloadStorageData(); vm.reloadSourcesState() },
                            icon = { Icon(Icons.Default.Person, null) },
                            label = { Text("我的") }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(AppColors.bg)) {
                when (vm.view) {
                    "home" -> HomeView(vm)
                    "library" -> LibraryView(vm)
                    "player" -> PlayerViewScreen(vm)
                    "profile" -> ProfileView(vm)
                }
            }
        }
    }
}

@Composable
fun HomeView(vm: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        // Search bar
        OutlinedTextField(
            value = vm.query,
            onValueChange = {
                vm.query = it
                if (it.isBlank()) vm.clearSearch()
            },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            placeholder = { Text("搜索全网视频 (如: 凡人修仙传 / 海贼王)...", color = AppColors.muted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = AppColors.muted) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vm.query.isNotEmpty()) {
                        IconButton({ vm.clearSearch() }) {
                            Icon(Icons.Default.Close, "清除", tint = AppColors.muted)
                        }
                    }
                    IconButton({ vm.search(vm.query) }) {
                        Icon(Icons.Default.ArrowForward, "搜索", tint = AppColors.cyan)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(vm.query) }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.cyan,
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                focusedTextColor = AppColors.text,
                unfocusedTextColor = AppColors.text,
            ),
            shape = RoundedCornerShape(13.dp)
        )

        // Notice & Loading indicator
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (vm.loading) {
                LoadingSpinner(modifier = Modifier.size(18.dp), color = AppColors.cyan)
                Spacer(Modifier.width(8.dp))
            }
            if (vm.notice.isNotEmpty()) {
                Text(vm.notice, color = AppColors.muted, fontSize = 13.sp)
            }
        }

        // Render search results grid when active, otherwise render home sections
        if (vm.isSearchActive || (vm.query.isNotBlank() && vm.items.isNotEmpty())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("搜索结果 (${vm.items.size})", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                TextButton(onClick = { vm.clearSearch() }) {
                    Text("返回首页推荐", color = AppColors.cyan, fontSize = 12.sp)
                }
            }

            if (vm.items.isEmpty() && !vm.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("未搜到相关作品，请更换关键字重试", color = AppColors.muted)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(8.dp)
                ) {
                    items(vm.items) { item ->
                        MovieCard(item, Modifier.padding(4.dp)) { vm.openMovie(item) }
                    }
                }
            }
        } else {
            if (vm.loading && vm.homeSections.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingSpinner(modifier = Modifier.size(36.dp), color = AppColors.cyan)
                        Spacer(Modifier.height(10.dp))
                        Text("正在并发加载视频源...", color = AppColors.muted, fontSize = 13.sp)
                    }
                }
            } else if (vm.homeSections.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("暂未加载到推荐卡片", color = AppColors.muted)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { vm.loadHome() }) {
                            Text("重新加载首页", color = AppColors.cyan)
                        }
                    }
                }
            } else {
                LazyColumn {
                    vm.homeSections.forEach { section ->
                        if (section.items.isNotEmpty()) {
                            item(key = "section_header_${section.key}") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(section.title, color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    TextButton({
                                        val sectionKind = when {
                                            section.title.contains("国漫") || section.key.contains("guo") -> "国漫"
                                            section.title.contains("日漫") || section.title.contains("日本") || section.title.contains("番") -> "日漫"
                                            section.title.contains("剧场") || section.title.contains("电影") -> "剧场版"
                                            section.title.contains("欧美") -> "欧美"
                                            else -> "全部"
                                        }
                                        vm.applyFilter(kind = sectionKind)
                                        vm.view = "library"
                                    }) {
                                        Text("更多", color = AppColors.cyan, fontSize = 13.sp)
                                    }
                                }
                            }
                            item(key = "section_row_${section.key}") {
                                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp)) {
                                    lazyItemsIndexed(
                                        items = section.items.take(12),
                                        key = { index, item -> "${section.key}:${item.sourceKey}:${item.id}:$index" }
                                    ) { _, item ->
                                        MovieCard(item, Modifier.width(140.dp).padding(4.dp)) { vm.openMovie(item) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryView(vm: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "多源片库",
            Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp),
            color = AppColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold
        )
        Text(
            if (vm.loading && vm.items.isEmpty()) "加载中..." else "共 ${vm.totalLibrary} 部作品${if (vm.libraryHasMore) " (下滑加载更多)" else ""}",
            Modifier.padding(horizontal = 16.dp), color = AppColors.muted, fontSize = 13.sp
        )
        Spacer(Modifier.height(4.dp))

        FilterRow("分类", vm.kinds, vm.activeKind) { vm.applyFilter(kind = it) }
        FilterRow("题材", vm.genres, vm.activeGenre) { vm.applyFilter(genre = it) }
        FilterRow("年份", vm.years, vm.activeYear) { vm.applyFilter(year = it) }
        FilterRow("状态", vm.statuses, vm.activeStatus) { vm.applyFilter(status = it) }
        FilterRow("排序", vm.sorts, vm.activeSort) { vm.applyFilter(sort = it) }
        FilterRow("来源", vm.sourceOptions, vm.activeSource) { vm.applyFilter(source = it) }

        Spacer(Modifier.height(4.dp))

        if (vm.loading && vm.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingSpinner(modifier = Modifier.size(36.dp), color = AppColors.cyan)
            }
        } else if (vm.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无满足筛选条件的作品", color = AppColors.muted)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.fetchLibrary() }) {
                        Text("重新加载", color = AppColors.cyan)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(8.dp)
            ) {
                itemsIndexed(
                    items = vm.items,
                    key = { index, item -> "${item.sourceKey}:${item.id}:$index" }
                ) { index, item ->
                    MovieCard(item, Modifier.padding(4.dp)) { vm.openMovie(item) }
                    // Trigger pre-fetch 9 items (3 rows) before hitting the bottom
                    if (index >= vm.items.size - 9 && vm.libraryHasMore && !vm.libraryLoadingMore && !vm.loading) {
                        LaunchedEffect(index) { vm.loadNextPage() }
                    }
                }

                if (vm.libraryLoadingMore) {
                    item(span = { GridItemSpan(3) }, key = "footer_loading_more") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LoadingSpinner(modifier = Modifier.size(20.dp), color = AppColors.cyan)
                                Spacer(Modifier.width(8.dp))
                                Text("转圈加载中... 正在预获取更多视频", color = AppColors.cyan, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Anime Player Screen matching Screenshot 2 spec ──
@Composable
fun PlayerViewScreen(vm: MainViewModel) {
    val detail = vm.displayedDetail ?: return
    val currentEp = detail.episodes.getOrNull(vm.currentEpisodeIndex)
    val isFav = vm.isFavorite(detail.item)
    val chunkSize = 30
    val episodeChunks = remember(detail.episodes) { detail.episodes.chunked(chunkSize) }
    var selectedChunkIndex by remember(detail.episodes) { mutableStateOf(0) }
    var expandedDescription by remember { mutableStateOf(false) }
    var showComments by remember(detail.item.id) { mutableStateOf(false) }
    val totalSources = 1 + vm.alternativeDetails.size

    Column(Modifier.fillMaxSize().background(AppColors.bg)) {
        // ── Top 16:9 Video Player Container ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black)
        ) {
            val playRes = vm.currentPlayResult
            if (playRes != null && playRes.url.isNotEmpty()) {
                EmbeddedVideoPlayer(
                    url = playRes.url,
                    type = playRes.type,
                    headers = playRes.headers,
                    referer = playRes.referer,
                    title = detail.item.title,
                    episodeName = currentEp?.name ?: "",
                    episodes = detail.episodes,
                    currentEpisodeIndex = vm.currentEpisodeIndex,
                    onSelectEpisode = { index -> vm.selectEpisode(index) },
                    onNextEpisode = if (vm.currentEpisodeIndex < detail.episodes.size - 1) {
                        { vm.selectEpisode(vm.currentEpisodeIndex + 1) }
                    } else null,
                    onBack = { vm.goBackFromPlayer() },
                    onError = { vm.invalidateCurrentPlayCache() }
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (vm.isPlayLoading) {
                            LoadingSpinner(modifier = Modifier.size(36.dp), color = AppColors.cyan)
                            Spacer(Modifier.height(10.dp))
                            Text("正在解析视频流...", color = AppColors.text, fontSize = 13.sp)
                        } else if (vm.playError != null) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = AppColors.orange, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(vm.playError ?: "播放失败", color = AppColors.muted, fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                TextButton(onClick = { vm.retryPlay() }) {
                                    Text("重试", color = AppColors.cyan)
                                }
                                if (vm.alternativeDetails.isNotEmpty()) {
                                    TextButton(onClick = { vm.switchSource() }) {
                                        Text("换源播放", color = AppColors.cyan)
                                    }
                                }
                            }
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AppColors.muted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("等待解析播放地址", color = AppColors.muted, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Top Left Floating Back Button
            IconButton(
                onClick = { vm.goBackFromPlayer() },
                modifier = Modifier
                    .padding(8.dp)
                    .align(Alignment.TopStart)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
            }
        }

        // ── Bottom Content Area ──
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            // Tab row: 动漫 | 评论
            item {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("动漫", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.width(16.dp))
                    Text("评论 99+", color = AppColors.muted, fontSize = 15.sp)
                }
                Spacer(Modifier.height(12.dp))
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "评论区",
                        color = AppColors.cyan,
                        fontSize = 14.sp,
                        modifier = Modifier.clickable { showComments = !showComments }
                    )
                    Text("${vm.comments.size} 条本地评论", color = AppColors.muted, fontSize = 12.sp)
                }
                if (showComments) {
                    OutlinedTextField(
                        value = vm.commentDraft,
                        onValueChange = { vm.commentDraft = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        placeholder = { Text("说点什么…") },
                        trailingIcon = {
                            TextButton(onClick = { vm.addComment() }) {
                                Text("发布", color = AppColors.cyan)
                            }
                        },
                        maxLines = 4
                    )
                    vm.comments.takeLast(5).forEach { comment ->
                        Text(
                            comment,
                            color = AppColors.text,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // Title and Brief
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        detail.item.title,
                        color = AppColors.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = { expandedDescription = !expandedDescription }
                    ) {
                        Text(if (expandedDescription) "简介 \u2303" else "简介 >", color = AppColors.cyan, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // Meta tags + source count
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${detail.item.kind.ifEmpty { "动漫" }}  |  ${detail.item.year.ifEmpty { "2026" }}  |  ${detail.item.sourceTitle}",
                        color = AppColors.muted,
                        fontSize = 12.sp
                    )
                    if (totalSources > 1) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AppColors.cyan.copy(alpha = 0.2f)
                        ) {
                            Text(
                                " ${totalSources}源可用 ",
                                color = AppColors.cyan,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Synopsis description
            if (detail.item.description.isNotEmpty()) {
                item {
                    Text(
                        detail.item.description,
                        color = AppColors.muted.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        maxLines = if (expandedDescription) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }

            // Action Buttons Row (换源 | 缓存番剧 | 追番 | 分享)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    ActionButton(
                        Icons.Default.Refresh, "换源",
                        enabled = totalSources > 1,
                        tint = if (totalSources > 1) AppColors.cyan else AppColors.muted
                    ) { vm.switchSource() }
                    ActionButton(
                        Icons.Default.Star,
                        vm.downloadProgress?.let { "下载 $it" }
                            ?: vm.episodeCacheProgress?.let { "预解析 $it" }
                            ?: "下载番剧",
                        enabled = vm.downloadProgress == null && vm.episodeCacheProgress == null
                    ) { vm.downloadCurrentEpisodes() }
                    ActionButton(
                        if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        if (isFav) "已追番" else "追番",
                        tint = if (isFav) AppColors.rose else AppColors.text
                    ) { vm.toggleFavorite(detail.item) }
                    ActionButton(Icons.Default.Share, "分享", enabled = false) {}
                }
                if (totalSources > 1) {
                    Spacer(Modifier.height(8.dp))
                    Text("选择播放源", color = AppColors.muted, fontSize = 12.sp)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        lazyItemsIndexed(vm.availableSourceLabels) { index, label ->
                            FilterChip(
                                selected = index == vm.activeAlternativeIndex,
                                onClick = { vm.selectSource(index) },
                                label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.cyan.copy(alpha = 0.25f),
                                    selectedLabelColor = AppColors.cyan
                                )
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Episodes Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("选集", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("共 ${detail.episodes.size} 集 >", color = AppColors.muted, fontSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            // Chunk selector if > 30 episodes
            if (episodeChunks.size > 1) {
                item {
                    LazyRow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        items(episodeChunks.size) { idx ->
                            val start = idx * chunkSize + 1
                            val end = minOf((idx + 1) * chunkSize, detail.episodes.size)
                            FilterChip(
                                selected = idx == selectedChunkIndex,
                                onClick = { selectedChunkIndex = idx },
                                label = { Text("$start-$end", fontSize = 11.sp) },
                                modifier = Modifier.padding(end = 4.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.cyan.copy(alpha = 0.25f),
                                    selectedLabelColor = AppColors.cyan
                                )
                            )
                        }
                    }
                }
            }

            // Episode Grid (4-columns pill style)
            val displayedEpisodes = episodeChunks.getOrNull(selectedChunkIndex) ?: detail.episodes
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.heightIn(max = 280.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(displayedEpisodes) { indexInChunk, ep ->
                        val globalIndex = selectedChunkIndex * chunkSize + indexInChunk
                        val isPlaying = globalIndex == vm.currentEpisodeIndex
                        Surface(
                            onClick = { vm.selectEpisode(globalIndex) },
                            modifier = Modifier.padding(4.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = if (isPlaying) AppColors.cyan.copy(alpha = 0.2f) else AppColors.panel2,
                            border = if (isPlaying) androidx.compose.foundation.BorderStroke(1.5.dp, AppColors.cyan) else null
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    ep.name,
                                    color = if (isPlaying) AppColors.cyan else AppColors.text,
                                    fontSize = 13.sp,
                                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = AppColors.text,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .padding(4.dp)
            .then(if (!enabled) Modifier.graphicsLayer { alpha = 0.4f } else Modifier)
    ) {
        Surface(
            shape = CircleShape,
            color = AppColors.panel2,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = if (enabled) AppColors.muted else AppColors.muted.copy(alpha = 0.4f), fontSize = 11.sp)
    }
}

@Composable
fun ProfileView(vm: MainViewModel) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("个人中心", color = AppColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
        }

        // Source Management Card
        item {
            Card(colors = CardDefaults.cardColors(containerColor = AppColors.panel), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("数据源开闭与管理", color = AppColors.text, fontWeight = FontWeight.Bold)
                        Text(
                            "${vm.sourcesState.count { vm.sourceManager.isSourceEnabled(it.key) }}/${vm.sourcesState.size} 已开启",
                            color = AppColors.cyan, fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    vm.sourcesState.chunked(2).forEach { rowSources ->
                        Row(Modifier.fillMaxWidth()) {
                            rowSources.forEach { config ->
                                val enabled = vm.sourceManager.isSourceEnabled(config.key)
                                FilterChip(
                                    selected = enabled,
                                    onClick = { vm.toggleSource(config.key) },
                                    label = { Text("${config.title} (${if (enabled) "开" else "关"})", fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f).padding(4.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AppColors.cyan.copy(alpha = 0.2f),
                                        selectedLabelColor = AppColors.cyan,
                                    )
                                )
                            }
                            if (rowSources.size == 1) {
                                Spacer(Modifier.weight(1f).padding(4.dp))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Custom Source Importer
        item {
            CustomSourceImportCard(vm)
        }

        // Source Debug Log Viewer
        item {
            SourceDebugLogCard(vm)
        }

        // Watch History
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("观看历史 (${vm.historyList.size})", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (vm.historyList.isNotEmpty()) {
                    TextButton(onClick = { vm.clearHistory() }) {
                        Text("清空记录", color = AppColors.rose, fontSize = 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (vm.historyList.isEmpty()) {
            item {
                Text("暂无观看历史记录", color = AppColors.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
        } else {
            items(vm.historyList) { history ->
                Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { vm.openMovie(history.item, history.episodeName) },
                    colors = CardDefaults.cardColors(containerColor = AppColors.panel2)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = coverRequest(LocalContext.current, history.item.cover),
                            contentDescription = null,
                            modifier = Modifier.size(50.dp, 70.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(history.item.title, color = AppColors.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                            Text("看到：${history.episodeName}", color = AppColors.cyan, fontSize = 13.sp)
                            Text("数据源：${history.item.sourceTitle.ifEmpty { history.item.sourceKey }}", color = AppColors.muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Favorites / Bookmarks
        item {
            Spacer(Modifier.height(16.dp))
            Text("我的追番 / 收藏 (${vm.favoritesList.size})", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(6.dp))
        }

        if (vm.favoritesList.isEmpty()) {
            item {
                Text("暂未添加任何收藏", color = AppColors.muted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
        } else {
            items(vm.favoritesList) { favorite ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { vm.openMovie(favorite) },
                    colors = CardDefaults.cardColors(containerColor = AppColors.panel)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = coverRequest(LocalContext.current, favorite.cover),
                            contentDescription = null,
                            modifier = Modifier.size(50.dp, 70.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(favorite.title, color = AppColors.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(2.dp))
                            Text("${favorite.year} · ${favorite.kind}", color = AppColors.muted, fontSize = 12.sp)
                        }
                        IconButton(onClick = { vm.toggleFavorite(favorite) }) {
                            Icon(Icons.Default.Favorite, contentDescription = "取消收藏", tint = AppColors.rose)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterRow(label: String, options: List<Any>, active: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label：", color = AppColors.muted, fontSize = 13.sp, modifier = Modifier.width(46.dp))
        LazyRow {
            items(options) { opt ->
                val text = if (opt is Pair<*, *>) opt.second.toString() else opt.toString()
                val valKey = if (opt is Pair<*, *>) opt.first.toString() else text
                val selected = text == active || valKey == active
                FilterChip(
                    selected = selected,
                    onClick = { onSelect(valKey) },
                    label = { Text(text, fontSize = 12.sp) },
                    modifier = Modifier.padding(end = 6.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AppColors.cyan.copy(alpha = 0.25f),
                        selectedLabelColor = AppColors.cyan,
                    )
                )
            }
        }
    }
}

private fun coverRequest(context: android.content.Context, raw: String): ImageRequest {
    var url = raw.trim()
    val headers = linkedMapOf<String, String>()
    // lanerc.js may append transport metadata as @Referer=...@User-Agent=...
    // Keep the URL clean for Coil while preserving the anti-hotlink headers.
    val marker = url.indexOf("@Referer=", ignoreCase = true)
    if (marker >= 0) {
        val meta = url.substring(marker)
        url = url.substring(0, marker)
        Regex("@(Referer|User-Agent)=([^@]+)", RegexOption.IGNORE_CASE).findAll(meta).forEach {
            headers[it.groupValues[1]] = it.groupValues[2]
        }
    }
    if (url.contains("douban", ignoreCase = true)) {
        headers.putIfAbsent("Referer", "https://movie.douban.com/")
        headers.putIfAbsent("User-Agent", BROWSER_COVER_UA)
    }
    val builder = ImageRequest.Builder(context).data(url)
    headers.forEach { (k, v) -> builder.addHeader(k, v) }
    return builder.listener(onError = { _, result ->
        SourceLogManager.error(
            "image",
            "封面加载失败",
            "${result.throwable.javaClass.simpleName}: ${result.throwable.message ?: "unknown"}",
            "url=${url.take(300)} headers=${headers.keys.joinToString(",")}"
        )
    }).crossfade(true).build()
}

private const val BROWSER_COVER_UA =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/131.0 Mobile Safari/537.36"

@Composable
fun MovieCard(item: SourceItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = AppColors.panel),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            AsyncImage(
                model = coverRequest(LocalContext.current, item.cover),
                contentDescription = item.title,
                modifier = Modifier.fillMaxWidth().aspectRatio(0.7f).clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                contentScale = ContentScale.Crop
            )
            Text(
                item.title, Modifier.padding(8.dp, 6.dp, 8.dp, 2.dp),
                color = AppColors.text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${item.sourceTitle} · ${item.kind.ifEmpty { "视频" }}",
                Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                color = AppColors.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CustomSourceImportCard(vm: MainViewModel) {
    var keyText by remember { mutableStateOf("") }
    var titleText by remember { mutableStateOf("") }
    var codeText by remember { mutableStateOf("") }
    var resultMsg by remember { mutableStateOf<String?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.panel),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("导入与测试自定义 JS 源", color = AppColors.text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = keyText,
                onValueChange = { keyText = it },
                label = { Text("源 ID 标识 (英文，如 my_source)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppColors.cyan, unfocusedBorderColor = AppColors.muted)
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it },
                label = { Text("源显示名称 (如 自定义动漫源)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppColors.cyan, unfocusedBorderColor = AppColors.muted)
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = codeText,
                onValueChange = { codeText = it },
                label = { Text("JS 代码 / 脚本内容 / 源文件文本") },
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AppColors.cyan, unfocusedBorderColor = AppColors.muted)
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val success = vm.sourceManager.importCustomSource(keyText, titleText, codeText)
                    if (success) {
                        vm.reloadSourcesState()
                        resultMsg = "自定义源 [$keyText] 导入成功并已开启！"
                        keyText = ""; titleText = ""; codeText = ""
                    } else {
                        resultMsg = "导入失败，请检查 Key 与 JS 代码"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.cyan),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("确认导入并载入源", color = AppColors.bg, fontWeight = FontWeight.Bold)
            }
            resultMsg?.let { msg ->
                Spacer(Modifier.height(6.dp))
                Text(msg, color = if (msg.contains("成功")) AppColors.green else AppColors.rose, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SourceDebugLogCard(vm: MainViewModel) {
    val logs = com.juying.app.source.SourceLogManager.logs
    var testResult by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.panel2),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("数据源诊断与报错日志中心 (${logs.size})", color = AppColors.text, fontWeight = FontWeight.Bold)
                TextButton(onClick = { com.juying.app.source.SourceLogManager.clear() }) {
                    Text("清空", color = AppColors.rose, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        isTesting = true
                        vm.viewModelScope.launch(Dispatchers.IO) {
                            val activeKeys = vm.sourcesState.filter { vm.sourceManager.isSourceEnabled(it.key) }.map { it.key }
                            val resList = activeKeys.map { k -> vm.sourceManager.testSource(k) }
                            withContext(Dispatchers.Main) {
                                testResult = resList.joinToString("\n---\n")
                                isTesting = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.purple),
                    enabled = !isTesting
                ) {
                    Text(if (isTesting) "测试中..." else "一键多源连通性诊断测试", fontSize = 12.sp)
                }
            }
            testResult?.let { res ->
                Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = AppColors.bg)) {
                    Text(res, color = AppColors.cyan, fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(8.dp)).background(AppColors.bg).padding(8.dp)) {
                if (logs.isEmpty()) {
                    Text("暂无诊断日志，搜索/加载数据源后将实时输出", color = AppColors.muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(logs) { entry ->
                            val color = when (entry.level) {
                                com.juying.app.source.LogLevel.ERROR -> AppColors.rose
                                com.juying.app.source.LogLevel.WARN -> AppColors.orange
                                com.juying.app.source.LogLevel.SUCCESS -> AppColors.green
                                com.juying.app.source.LogLevel.INFO -> AppColors.cyan
                            }
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(
                                    "[${entry.timestamp}] [${entry.sourceKey}] ${entry.title}: ${entry.message}",
                                    color = color,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (entry.details.isNotEmpty()) {
                                    Text(entry.details, color = AppColors.muted, fontSize = 10.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
