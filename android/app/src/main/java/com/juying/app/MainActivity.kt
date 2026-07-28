@file:OptIn(ExperimentalMaterial3Api::class)

package com.juying.app

import android.app.Application
import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import java.util.concurrent.atomic.AtomicInteger

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.juying.app.source.*
import com.juying.app.ui.EmbeddedVideoPlayer
import com.juying.app.ui.PipController
import com.juying.app.update.AppUpdateInfo
import com.juying.app.update.AppUpdateManager
import com.juying.app.update.UpdateCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.input.PasswordVisualTransformation
import java.io.File
import java.util.Calendar
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

// 临时关闭账号体系入口：保留原登录/注册实现，后续只需改为 false 即可恢复。
private const val TEMP_ACCOUNT_AUTH_DISABLED = true
// 临时关闭评论发送；评论读取仍保留，便于展示已有或外部评论数据。
private const val TEMP_COMMENT_POSTING_DISABLED = true

data class CustomColors(
    val bg: Color,
    val panel: Color,
    val panel2: Color,
    val text: Color,
    val muted: Color,
    val cyan: Color,
    val purple: Color,
    val orange: Color,
    val rose: Color,
    val green: Color,
    val isDark: Boolean
)

val LocalCustomColors = staticCompositionLocalOf {
    CustomColors(
        bg = Color(0xFF090E17),
        panel = Color(0xFF101926),
        panel2 = Color(0xFF162436),
        text = Color(0xFFF3F7FC),
        muted = Color(0xFF8B9AAF),
        cyan = Color(0xFF43D5E8),
        purple = Color(0xFFA855F7),
        orange = Color(0xFFFFB257),
        rose = Color(0xFFFF7F9E),
        green = Color(0xFF4ADE80),
        isDark = true
    )
}

object AppColors {
    val bg: Color @Composable get() = LocalCustomColors.current.bg
    val panel: Color @Composable get() = LocalCustomColors.current.panel
    val panel2: Color @Composable get() = LocalCustomColors.current.panel2
    val text: Color @Composable get() = LocalCustomColors.current.text
    val muted: Color @Composable get() = LocalCustomColors.current.muted
    val cyan: Color @Composable get() = LocalCustomColors.current.cyan
    val purple: Color @Composable get() = LocalCustomColors.current.purple
    val orange: Color @Composable get() = LocalCustomColors.current.orange
    val rose: Color @Composable get() = LocalCustomColors.current.rose
    val green: Color @Composable get() = LocalCustomColors.current.green
}

fun isValidEmail(email: String): Boolean {
    val trimmed = email.trim()
    return trimmed.contains("@") && trimmed.contains(".") &&
            Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(trimmed)
}

fun isPasswordStrong(password: String): Boolean {
    if (password.length < 8) return false
    var types = 0
    if (password.any { it.isUpperCase() }) types++
    if (password.any { it.isLowerCase() }) types++
    if (password.any { it.isDigit() }) types++
    if (password.any { !it.isLetterOrDigit() }) types++
    return types >= 3
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
    private val appUpdateManager = AppUpdateManager(application)
    private val commentRepository = CommentRepository(application)
    private val accountRepository = AccountRepository(application)
    private var commentsLoadedFor: String? = null
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
    var commentNick by mutableStateOf(storageManager.getCommentNick())
    var commentDraft by mutableStateOf("")
    var comments by mutableStateOf<List<CloudComment>>(emptyList())

    // Cloud account state. Anonymous local storage remains the default.
    var accountUser by mutableStateOf<AccountUser?>(null)
    var accountBusy by mutableStateOf(false)
    var accountMessage by mutableStateOf("")

    // Theme & Account Settings State
    var themeMode by mutableStateOf(storageManager.getThemeMode())
    var userEmail by mutableStateOf(storageManager.getUserEmail())
    var userPassword by mutableStateOf(storageManager.getUserPassword())
    var userAvatarIndex by mutableStateOf(storageManager.getUserAvatar())
    var updateChecking by mutableStateOf(false)
    var updateInfo by mutableStateOf<AppUpdateInfo?>(null)
    var updateDialogVisible by mutableStateOf(false)
    var updateMessage by mutableStateOf("")
    var updateDownloadProgress by mutableStateOf<Int?>(null)

    fun checkForAppUpdate(manual: Boolean = true) {
        if (updateChecking || updateDownloadProgress != null) return
        viewModelScope.launch {
            updateChecking = true
            if (manual) updateMessage = "正在检查更新…"
            when (val result = appUpdateManager.check(manual)) {
                is UpdateCheckResult.Available -> {
                    updateInfo = result.info
                    updateDialogVisible = true
                    updateMessage = "发现新版本 ${result.info.versionName}"
                }
                UpdateCheckResult.Latest -> {
                    if (manual) updateMessage = "当前已是最新版本"
                }
                is UpdateCheckResult.Failed -> {
                    if (manual) updateMessage = "检查失败：${result.message}"
                }
            }
            updateChecking = false
        }
    }

    fun downloadAndInstallUpdate(activity: Activity) {
        val info = updateInfo ?: return
        if (updateDownloadProgress != null) return
        viewModelScope.launch {
            updateDownloadProgress = 0
            updateMessage = "正在下载 ${info.versionName}"
            val result = appUpdateManager.download(info) { progress ->
                viewModelScope.launch { updateDownloadProgress = progress }
            }
            result.onSuccess { apk ->
                updateDownloadProgress = 100
                updateMessage = "下载完成，正在打开安装界面"
                updateDialogVisible = false
                appUpdateManager.installOrRequestPermission(activity, apk)
            }.onFailure { error ->
                updateDownloadProgress = null
                updateMessage = "下载失败：${error.message ?: "未知错误"}"
            }
        }
    }

    fun dismissUpdate() {
        updateDialogVisible = false
        updateMessage = "已跳过本次更新，可继续使用当前版本"
    }

    fun updateThemeMode(mode: String) {
        themeMode = mode
        storageManager.setThemeMode(mode)
    }

    fun updateUserEmail(email: String) {
        userEmail = email
        storageManager.setUserEmail(email)
    }

    fun updateUserPassword(password: String) {
        userPassword = password
        storageManager.setUserPassword(password)
    }

    fun updateUserAvatar(index: Int) {
        userAvatarIndex = index
        storageManager.setUserAvatar(index)
    }

    fun updateCommentNick(nick: String) {
        val normalized = nick.trim().take(24)
        commentNick = normalized
        if (normalized.isNotEmpty()) {
            storageManager.setCommentNick(normalized)
        }
    }

    fun loginAccount(email: String, password: String) {
        if (accountBusy) return
        viewModelScope.launch {
            accountBusy = true
            accountMessage = ""
            try {
                val result = accountRepository.login(email, password)
                if (result.user == null || result.token.isNullOrBlank()) {
                    accountMessage = result.error ?: "登录失败"
                } else {
                    storageManager.setAuthToken(result.token)
                    storageManager.setAccountNickname(result.user.nickname)
                    updateUserEmail(result.user.email)
                    accountUser = result.user
                    accountMessage = "登录成功，正在同步本机数据"
                    val remote = accountRepository.pull(result.token)
                    storageManager.mergeCloudData(remote.favorites, remote.history)
                    reloadStorageData()
                    accountRepository.sync(result.token, favoritesList, historyList)
                }
            } catch (error: Exception) {
                accountMessage = error.message ?: "登录失败"
            } finally {
                accountBusy = false
            }
        }
    }

    fun requestAccountCode(email: String, purpose: String) {
        if (accountBusy) return
        viewModelScope.launch {
            accountBusy = true
            accountMessage = ""
            try {
                accountRepository.requestCode(email, purpose)
                accountMessage = "验证码已发送，请检查邮箱"
            } catch (error: Exception) {
                accountMessage = error.message ?: "验证码发送失败"
            } finally {
                accountBusy = false
            }
        }
    }

    fun registerAccount(email: String, password: String, confirmPassword: String, nickname: String, code: String) {
        if (accountBusy) return
        if (password != confirmPassword) {
            accountMessage = "两次输入的密码不一致"
            return
        }
        viewModelScope.launch {
            accountBusy = true
            accountMessage = ""
            try {
                val result = accountRepository.register(email, password, nickname, code)
                if (result.user == null || result.token.isNullOrBlank()) {
                    accountMessage = result.error ?: "注册失败"
                } else {
                    storageManager.setAuthToken(result.token)
                    storageManager.setAccountNickname(result.user.nickname)
                    updateUserEmail(result.user.email)
                    accountUser = result.user
                    accountMessage = "注册成功，已同步本机数据"
                    val remote = accountRepository.pull(result.token)
                    storageManager.mergeCloudData(remote.favorites, remote.history)
                    reloadStorageData()
                    accountRepository.sync(result.token, favoritesList, historyList)
                }
            } catch (error: Exception) {
                accountMessage = error.message ?: "注册失败"
            } finally {
                accountBusy = false
            }
        }
    }

    fun resetAccountPassword(email: String, code: String, password: String, confirmPassword: String) {
        if (accountBusy) return
        viewModelScope.launch {
            accountBusy = true
            accountMessage = ""
            try {
                accountRepository.resetPassword(email, code, password, confirmPassword)
                accountMessage = "密码已重置，请使用新密码登录"
            } catch (error: Exception) {
                accountMessage = error.message ?: "密码重置失败"
            } finally {
                accountBusy = false
            }
        }
    }

    fun changeAccountEmail(email: String, code: String) {
        val token = storageManager.getAuthToken()
        if (token.isBlank() || accountBusy) return
        viewModelScope.launch {
            accountBusy = true
            accountMessage = ""
            try {
                val result = accountRepository.changeEmail(token, email, code)
                if (result.user != null) {
                    accountUser = result.user
                    updateUserEmail(result.user.email)
                    accountMessage = "邮箱修改成功"
                } else {
                    accountMessage = result.error ?: "邮箱修改失败"
                }
            } catch (error: Exception) {
                accountMessage = error.message ?: "邮箱修改失败"
            } finally {
                accountBusy = false
            }
        }
    }

    fun changeAccountNickname(nickname: String) {
        val token = storageManager.getAuthToken()
        val normalized = nickname.trim().take(24)
        if (token.isBlank() || normalized.isBlank() || accountBusy) return
        viewModelScope.launch {
            accountBusy = true
            accountMessage = ""
            try {
                val result = accountRepository.changeNickname(token, normalized)
                if (result.user != null) {
                    accountUser = result.user
                    commentNick = result.user.nickname
                    storageManager.setAccountNickname(result.user.nickname)
                    storageManager.setCommentNick(result.user.nickname)
                    accountMessage = "昵称修改成功"
                } else {
                    accountMessage = result.error ?: "昵称修改失败"
                }
            } catch (error: Exception) {
                accountMessage = error.message ?: "昵称修改失败"
            } finally {
                accountBusy = false
            }
        }
    }

    fun logoutAccount() {
        val token = storageManager.getAuthToken()
        viewModelScope.launch {
            if (token.isNotBlank()) runCatching { accountRepository.logout(token) }
            storageManager.clearAuthToken()
            accountUser = null
            accountMessage = "已退出云端账号，本机数据仍保留"
        }
    }

    fun uploadAccountAvatar(uri: Uri) {
        val token = storageManager.getAuthToken()
        if (token.isBlank() || accountBusy) return
        viewModelScope.launch {
            accountBusy = true
            accountMessage = ""
            val resolver = getApplication<Application>().contentResolver
            val extension = when (resolver.getType(uri)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> "jpg"
            }
            val file = File(getApplication<Application>().cacheDir, "avatar_upload_${System.currentTimeMillis()}.$extension")
            try {
                resolver.openInputStream(uri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: throw IllegalStateException("无法读取图片")
                val result = accountRepository.uploadAvatar(token, file)
                if (result.user != null) {
                    accountUser = result.user
                    accountMessage = "头像上传成功"
                } else {
                    accountMessage = result.error ?: "头像上传失败"
                }
            } catch (error: Exception) {
                accountMessage = error.message ?: "头像上传失败"
            } finally {
                file.delete()
                accountBusy = false
            }
        }
    }

    private fun restoreAccount() {
        val token = storageManager.getAuthToken()
        if (token.isBlank()) return
        viewModelScope.launch {
            runCatching { accountRepository.me(token) }
                .onSuccess { result ->
                    if (result.user != null) {
                        accountUser = result.user
                        updateUserEmail(result.user.email)
                    } else {
                        storageManager.clearAuthToken()
                    }
                }
                .onFailure { storageManager.clearAuthToken() }
        }
    }

    fun allPoolItems(): List<SourceItem> {
        return (cachedHomePool + homeSections.flatMap { it.items }).distinctBy { it.id }
    }

    private fun commentMediaKey(item: SourceItem): String =
        "${item.sourceKey.substringBefore(',').trim()}:${item.id}"

    // 打开详情/播放页时按作品加载云端评论；接口失败保持空列表，不影响播放链路
    fun loadCommentsForActiveDetail() {
        val detail = displayedDetail ?: activeDetail ?: return
        val key = commentMediaKey(detail.item)
        if (key == commentsLoadedFor) return
        commentsLoadedFor = key
        comments = emptyList()
        viewModelScope.launch {
            val remote = commentRepository.load(key)
            if (remote != null && commentsLoadedFor == key) {
                comments = remote
            }
        }
    }

    fun addComment() {
        // TEMP: 评论发送暂时关闭；不要删除下面原有发送逻辑，恢复开关即可继续使用。
        if (TEMP_COMMENT_POSTING_DISABLED) {
            accountMessage = "评论发送暂时关闭，仅展示已有评论"
            return
        }
        val text = commentDraft.trim()
        if (text.isEmpty()) return
        commentDraft = ""
        val detail = displayedDetail ?: activeDetail ?: return
        val key = commentMediaKey(detail.item)
        val nick = commentNick.ifBlank { storageManager.getCommentNick() }
        // 乐观上屏，再以云端返回的权威列表覆盖
        comments = comments + CloudComment(nick, text)
        viewModelScope.launch {
            val remote = commentRepository.post(key, nick, text)
            if (remote != null) {
                commentsLoadedFor = key
                comments = remote
            } else {
                android.widget.Toast.makeText(
                    getApplication(),
                    "云端评论暂不可用，评论仅保留在本机当前会话",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
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
    var showDownloadEpisodeModal by mutableStateOf(false)
    val activeDownloadKeys = mutableStateListOf<String>()
    val activeDownloadProgress = mutableStateMapOf<String, String>()
    val activeDownloadInfo = mutableStateMapOf<String, Pair<String, String>>()

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
        // TEMP: 登录/注册暂时关闭，保持本地模式，避免启动时访问账号服务。
        if (!TEMP_ACCOUNT_AUTH_DISABLED) restoreAccount()
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

    private fun syncAccountData() {
        val token = storageManager.getAuthToken()
        if (token.isBlank() || accountUser == null) return
        viewModelScope.launch {
            runCatching { accountRepository.sync(token, favoritesList, historyList) }
        }
    }

    fun loadHome() {
        viewModelScope.launch {
            loadHomeInternal()
        }
    }

    private suspend fun loadHomeInternal() {
        val presetSections = linkedMapOf(
            "热门推荐" to mutableListOf<SourceItem>(),
            "最新更新" to mutableListOf<SourceItem>(),
            "日漫精选" to mutableListOf<SourceItem>(),
            "国漫精粹" to mutableListOf<SourceItem>(),
            "剧场版/电影" to mutableListOf<SourceItem>()
        )

        fun buildHomeSectionsList(): List<HomeSection> {
            val hotTitles = presetSections["热门推荐"]
                ?.map { SourceManager.normalizeTitle(it.title) }
                ?.toSet() ?: emptySet()

            return presetSections.mapNotNull { (title, list) ->
                val deduped = list.distinctBy { SourceManager.normalizeTitle(it.title) }
                val finalItems = if (title == "最新更新" && hotTitles.isNotEmpty()) {
                    val distinctFromHot = deduped.filter { SourceManager.normalizeTitle(it.title) !in hotTitles }
                    if (distinctFromHot.isNotEmpty()) distinctFromHot else deduped
                } else {
                    deduped
                }
                if (finalItems.isEmpty()) null
                else {
                    HomeSection(title = title, key = title, items = finalItems.take(16))
                }
            }
        }

        val adapters = sourceManager.allAdapters()
        withContext(Dispatchers.Main) {
            loading = true
            notice = "正在并发动态加载多源视频..."
            if (cachedHomePool.isNotEmpty()) {
                cachedHomePool.forEachIndexed { index, item ->
                    val kind = item.kind + " " + item.title
                    val targetKey = when {
                        kind.contains("日漫") || kind.contains("日本") -> "日漫精选"
                        kind.contains("国漫") || kind.contains("国产") -> "国漫精粹"
                        kind.contains("剧场") || kind.contains("电影") -> "剧场版/电影"
                        index % 2 == 0 -> "热门推荐"
                        else -> "最新更新"
                    }
                    val targetList = presetSections[targetKey]
                    if (targetList != null) {
                        val norm = SourceManager.normalizeTitle(item.title)
                        if (targetList.none { SourceManager.normalizeTitle(it.title) == norm }) {
                            targetList.add(item)
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

        // Concurrent streaming: silently append new items to the right side of presetSections
        var lastUIUpdateMs = 0L
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
                                fetchedItems.forEachIndexed { idx, item ->
                                    val kind = item.kind + " " + item.title + " " + item.tags.joinToString(" ")
                                    val targetKey = when {
                                        kind.contains("日漫") || kind.contains("日本") -> "日漫精选"
                                        kind.contains("国漫") || kind.contains("国产") -> "国漫精粹"
                                        kind.contains("剧场") || kind.contains("电影") -> "剧场版/电影"
                                        idx % 2 == 0 -> "热门推荐"
                                        else -> "最新更新"
                                    }
                                    val targetList = presetSections[targetKey]
                                    if (targetList != null) {
                                        val norm = SourceManager.normalizeTitle(item.title)
                                        if (targetList.none { SourceManager.normalizeTitle(it.title) == norm }) {
                                            targetList.add(item)
                                        }
                                    }
                                }
                            }

                            val now = System.currentTimeMillis()
                            if (now - lastUIUpdateMs > 1500L || homeSections.isEmpty()) {
                                lastUIUpdateMs = now
                                val updatedList = synchronized(presetSections) { buildHomeSectionsList() }
                                withContext(Dispatchers.Main) {
                                    homeSections = updatedList
                                    cachedHomePool = updatedList.flatMap { it.items }
                                    if (!isSearchActive) items = cachedHomePool
                                    notice = "已为你动态加载 ${updatedList.sumOf { it.items.size }} 部精彩作品"
                                    loading = false
                                }
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
                    notice = "未找到「$keyword」匹配内容，试试其他关键词"
                } else {
                    ResultCache.putSearch(cacheKey, finalItems)
                }
            }
        }
    }

    fun executeSearch(keyword: String) {
        val q = keyword.trim()
        if (q.isBlank()) return
        query = q
        view = "search_result"
        search(q)
    }

    fun clearSearch() {
        query = ""
        isSearchActive = false
        items = emptyList()
        notice = ""
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

        val pool = (cachedHomePool + libraryItems).distinctBy { SourceManager.normalizeTitle(it.title) }
        val instant = applyLibraryFiltersFast(pool)
        libraryItems = instant
        items = instant
        totalLibrary = instant.size
        loading = false
        notice = "已为你即时筛选出 ${instant.size} 部作品"

        fetchLibrary(reset = true)
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
        var filtered = input.distinctBy { SourceManager.normalizeTitle(it.title) }

        if (activeKind != kinds.first()) {
            val key = activeKind.lowercase()
            filtered = filtered.filter { item ->
                val kindStr = "${item.kind} ${item.tags.joinToString(" ")}".lowercase()
                when (activeKind) {
                    "日漫" -> kindStr.contains("日漫") || kindStr.contains("日本") || kindStr.contains("日产")
                    "国漫" -> kindStr.contains("国漫") || kindStr.contains("国产") || kindStr.contains("大陆") || kindStr.contains("华语")
                    "剧场版" -> kindStr.contains("剧场") || kindStr.contains("电影") || item.title.contains("剧场版") || item.title.contains("电影")
                    "欧美" -> kindStr.contains("欧美") || kindStr.contains("美国") || kindStr.contains("迪士尼")
                    else -> kindStr.contains(key)
                }
            }
        }

        if (activeGenre != genres.first()) {
            val key = activeGenre.lowercase()
            filtered = filtered.filter { item ->
                val metadata = "${item.kind} ${item.tags.joinToString(" ")} ${item.description}".lowercase()
                metadata.contains(key)
            }
        }

        if (activeYear != years.first()) {
            if (activeYear == years.last()) {
                filtered = filtered.filter { item ->
                    (item.year.toIntOrNull() ?: 2024) < 2003
                }
            } else {
                filtered = filtered.filter { item ->
                    item.year.contains(activeYear)
                }
            }
        }

        if (activeSource != "全部") {
            val srcLower = activeSource.lowercase()
            filtered = filtered.filter { item ->
                val itemSourceStr = "${item.sourceTitle} ${item.sourceKey}".lowercase()
                itemSourceStr.contains(srcLower)
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
                                withTimeout(2_500L) {
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
                                val existingKeys = libraryItems
                                    .map { SourceManager.normalizeTitle(it.title) }
                                    .toSet()
                                val appendOnly = streamed.filter {
                                    SourceManager.normalizeTitle(it.title) !in existingKeys
                                }
                                if (appendOnly.isNotEmpty()) {
                                    val finalList = libraryItems + appendOnly
                                    libraryItems = finalList
                                    items = finalList
                                    totalLibrary = finalList.size
                                }
                                loading = false
                                notice = "已检索呈现 ${items.size} 部符合要求作品 (${done}/${targetAdapters.size} 源就绪)"
                            }
                        }
                    }

                    // First-wave 180ms yield: Publish fast-responding network sources to UI within 100-300ms window
                    launch {
                        delay(180L)
                        val snapshot = synchronized(fetchLock) { fetchedNewItems.toList() }
                        if (snapshot.isNotEmpty()) {
                            val streamed = applyLibraryFiltersFast(SourceManager.mergeSearchItems(snapshot))
                            withContext(Dispatchers.Main) {
                                if (requestId != libraryGeneration) return@withContext
                                val existingKeys = libraryItems.map { SourceManager.normalizeTitle(it.title) }.toSet()
                                val appendOnly = streamed.filter { SourceManager.normalizeTitle(it.title) !in existingKeys }
                                if (appendOnly.isNotEmpty()) {
                                    val finalList = libraryItems + appendOnly
                                    libraryItems = finalList
                                    items = finalList
                                    totalLibrary = finalList.size
                                    loading = false
                                }
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
        playerReturnView = view
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
                    if (fresh.episodes.isNotEmpty()) ResultCache.putDetail(detailCacheKey, fresh)
                    fresh
                }

            // Fallback for expired/broken source URL from history or favorites
            var finalDetailResult = detailResult
            if (finalDetailResult.episodes.isEmpty()) {
                val fallback = withContext(Dispatchers.IO) {
                    val targetTitle = SourceManager.normalizeTitle(playableItem.title)
                    sourceManager.allAdapters().firstNotNullOfOrNull { alt ->
                        if (alt.key == primarySourceKey) null
                        else {
                            try {
                                val altItems = alt.search(playableItem.title, 1)
                                val match = altItems.firstOrNull { SourceManager.normalizeTitle(it.title) == targetTitle }
                                if (match != null) {
                                    val d = alt.detail(match.id)
                                    if (d.episodes.isNotEmpty()) d else null
                                } else null
                            } catch (_: Exception) { null }
                        }
                    }
                }
                if (fallback != null) {
                    finalDetailResult = fallback
                }
            }

            // Show player immediately — don't wait for alt sources
            withContext(Dispatchers.Main) {
                loading = false
                activeDetail = finalDetailResult
                activeAlternativeIndex = 0
                currentEpisodeIndex = finalDetailResult.episodes.indexOfFirst { ep ->
                    pendingEpisodeName?.let { preferred ->
                        ep.name.equals(preferred, ignoreCase = true) ||
                            ep.name.contains(preferred, ignoreCase = true) ||
                            preferred.contains(ep.name, ignoreCase = true)
                    } ?: false
                }.takeIf { it >= 0 } ?: 0
                pendingEpisodeName = null
                view = "player"
                if (finalDetailResult.episodes.isNotEmpty()) {
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
                    if (accountUser != null) {
                        storageManager.addHistory(detail.item, ep.name, cachedPlay.url)
                        reloadStorageData()
                        syncAccountData()
                    }
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
                    if (accountUser != null) {
                        storageManager.addHistory(detail.item, ep.name, playResult.url)
                        reloadStorageData()
                        syncAccountData()
                    }
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
        currentPlayResult = null
        isPlayLoading = true
        playError = null
        activeAlternativeIndex = index

        val newDetail = currentActiveDetail() ?: return

        // Select the closest episode number
        val currentEpNum = currentEpisodeIndex + 1
        val bestIdx = newDetail.episodes.indexOfFirst {
            it.name.toIntOrNull() == currentEpNum
        }.let { if (it >= 0) it else 0.coerceAtMost((newDetail.episodes.size - 1).coerceAtLeast(0)) }
        currentEpisodeIndex = bestIdx
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
        when {
            destination == "library" -> {
                items = libraryItems
                reloadStorageData()
            }
            destination.startsWith("profile") -> reloadStorageData()
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

    fun startDownloadEpisode(detail: DetailResult, episode: Episode) {
        val downloadKey = "${detail.item.title}_${episode.name}"
        if (activeDownloadKeys.contains(downloadKey)) {
            notice = "「${episode.name}」正在下载中，请勿重复点击"
            return
        }
        val adapter = sourceManager.getAdapter(detail.item.sourceKey) ?: return
        activeDownloadKeys.add(downloadKey)
        activeDownloadInfo[downloadKey] = detail.item.title to episode.name
        activeDownloadProgress[downloadKey] = "0%"
        notice = "已开始后台下载：${detail.item.title} ${episode.name}"

        viewModelScope.launch(Dispatchers.IO) {
            val downloader = VideoDownloadManager(getApplication())
            try {
                downloader.downloadCover(detail.item.cover, detail.item.title)
                val play = withTimeout(20_000L) { adapter.play(episode.flagStr) }
                if (play.url.isNotBlank()) {
                    val fileInfo = downloader.download(
                        play.url,
                        play.headers,
                        play.referer,
                        detail.item.title,
                        episode.name,
                        onProgress = { done, total ->
                            val text = if (total > 0L) {
                                val pct = (done * 100 / total).toInt().coerceIn(0, 100)
                                "$pct%"
                            } else {
                                val mb = done / (1024.0 * 1024.0)
                                String.format("%.1f MB", mb)
                            }
                            viewModelScope.launch(Dispatchers.Main) {
                                activeDownloadProgress[downloadKey] = text
                            }
                        }
                    )
                    if (fileInfo != null && fileInfo.playableOffline) {
                        downloader.saveMetadata(detail.item.title, episode.name, detail.item.cover, fileInfo.file)
                        withContext(Dispatchers.Main) {
                            notice = "【下载完成】${detail.item.title} ${episode.name}"
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            notice = "【下载失败】${episode.name} 无法离线播放"
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        notice = "【下载失败】无法解析 ${episode.name} 播放资源"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    notice = "【下载异常】${episode.name}：${e.message}"
                }
            } finally {
                withContext(Dispatchers.Main) {
                    activeDownloadKeys.remove(downloadKey)
                    activeDownloadProgress.remove(downloadKey)
                    activeDownloadInfo.remove(downloadKey)
                }
            }
        }
    }

    fun getDownloadedFilesList(): List<com.juying.app.source.DownloadedItemInfo> {
        val downloader = VideoDownloadManager(getApplication())
        return downloader.getDownloadedItems()
    }

    fun deleteDownloadedFile(item: com.juying.app.source.DownloadedItemInfo) {
        val downloader = VideoDownloadManager(getApplication())
        val ok = downloader.deleteDownload(item)
        if (ok) {
            notice = "已成功删除缓存：${item.title} ${item.episodeName}"
            reloadStorageData()
        } else {
            notice = "删除缓存失败"
        }
    }

    fun playOfflineVideo(item: com.juying.app.source.DownloadedItemInfo) {
        val localPath = item.videoFile.absolutePath
        currentPlayResult = PlayResult(
            url = localPath,
            headers = emptyMap(),
            referer = ""
        )
        val dummyDetail = DetailResult(
            item = SourceItem(
                id = "local_${item.title}",
                title = item.title,
                cover = item.coverPath.orEmpty(),
                year = "离线缓存",
                kind = "本地文件",
                sourceKey = "local",
                sourceTitle = "本地视频"
            ),
            episodes = listOf(Episode(id = "1", name = item.episodeName, flagStr = localPath))
        )
        activeDetail = dummyDetail
        currentEpisodeIndex = 0
        playerReturnView = view
        view = "player"
        notice = "正在播放离线缓存：${item.title} ${item.episodeName}"
    }

    fun toggleFavorite(item: SourceItem) {
        if (accountUser == null) {
            accountMessage = "请先登录后使用收藏"
            return
        }
        storageManager.toggleFavorite(item)
        reloadStorageData()
        syncAccountData()
    }

    fun isFavorite(item: SourceItem): Boolean {
        return accountUser != null && storageManager.isFavorite(item)
    }

    fun clearHistory() {
        storageManager.clearHistory()
        reloadStorageData()
    }

    override fun onCleared() {
        sourceManager.close()
    }
}

private const val ACTION_PIP_CONTROL = "com.juying.app.action.PIP_CONTROL"
private const val EXTRA_PIP_CONTROL = "extra_pip_control"
private const val PIP_CONTROL_PLAY_PAUSE = 1
private const val PIP_CONTROL_PREV = 2
private const val PIP_CONTROL_NEXT = 3

class MainActivity : ComponentActivity() {
    private lateinit var updateManager: AppUpdateManager

    // 画中画小窗遥控按钮（播放/暂停、上一集/下一集）广播接收器
    private val pipActionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            when (intent.getIntExtra(EXTRA_PIP_CONTROL, -1)) {
                PIP_CONTROL_PLAY_PAUSE -> PipController.onTogglePlayPause?.invoke()
                PIP_CONTROL_PREV -> PipController.onPrevEpisode?.invoke()
                PIP_CONTROL_NEXT -> PipController.onNextEpisode?.invoke()
            }
            // 操作后刷新小窗按钮（暂停/播放图标、上/下集可用性）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && isInPictureInPictureMode) {
                try { setPictureInPictureParams(buildPipParams()) } catch (_: Exception) {}
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        updateManager = AppUpdateManager(this)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val filter = android.content.IntentFilter(ACTION_PIP_CONTROL)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(pipActionReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                registerReceiver(pipActionReceiver, filter)
            }
        }
        setContent {
            val vm: MainViewModel = viewModel()
            var showStartupSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                vm.initApp()
                vm.checkForAppUpdate(manual = false)
            }
            if (showStartupSplash) {
                JuyingStartupSplash { showStartupSplash = false }
            } else {
                JuyingApp(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::updateManager.isInitialized) {
            updateManager.resumePendingInstall(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try { unregisterReceiver(pipActionReceiver) } catch (_: Exception) {}
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // 小窗模式下播放器隐藏顶部栏/进度条等控制层（由 EmbeddedVideoPlayer 读取）
        PipController.inPipMode = isInPictureInPictureMode
    }

    private fun buildPipParams(): android.app.PictureInPictureParams {
        fun pipAction(control: Int, iconRes: Int, title: String): android.app.RemoteAction {
            val intent = android.content.Intent(ACTION_PIP_CONTROL)
                .setPackage(packageName)
                .putExtra(EXTRA_PIP_CONTROL, control)
            val pending = android.app.PendingIntent.getBroadcast(
                this, control, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            return android.app.RemoteAction(
                android.graphics.drawable.Icon.createWithResource(this, iconRes), title, title, pending
            )
        }
        val actions = mutableListOf<android.app.RemoteAction>()
        if (PipController.hasPrev) actions += pipAction(PIP_CONTROL_PREV, android.R.drawable.ic_media_previous, "上一集")
        actions += pipAction(
            PIP_CONTROL_PLAY_PAUSE,
            if (PipController.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (PipController.isPlaying) "暂停" else "播放"
        )
        if (PipController.hasNext) actions += pipAction(PIP_CONTROL_NEXT, android.R.drawable.ic_media_next, "下一集")
        return android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(16, 9))
            .setActions(actions)
            .build()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 只有播放器在屏且正在播放时才进入画中画；首页等其他页面退出不再触发小窗
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
            && PipController.playerActive && PipController.isPlaying
        ) {
            try {
                enterPictureInPictureMode(buildPipParams())
            } catch (_: Exception) {}
        }
    }
}

@Composable
private fun JuyingStartupSplash(onFinished: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "juying-splash-alpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.88f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "juying-splash-scale"
    )
    val glowTransition = rememberInfiniteTransition(label = "juying-splash-glow")
    val glow by glowTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "juying-splash-glow-alpha"
    )

    LaunchedEffect(Unit) {
        entered = true
        delay(1850)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050817))
    ) {
        Image(
            painter = painterResource(id = R.drawable.juying_splash_art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x22050817),
                            Color(0x44050817),
                            Color(0xF2050817)
                        )
                    )
                )
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 92.dp)
                .graphicsLayer {
                    this.alpha = alpha
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            Image(
                painter = painterResource(id = R.drawable.juying_icon_art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(26.dp))
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "juying",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "发现你的下一部心动番剧",
                color = Color(0xFFD7E8FF),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(Color(0x5563E6FF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(glow)
                        .clip(CircleShape)
                        .background(Color(0xFF63E6FF))
                )
            }
        }
    }
}

@Composable
fun JuyingApp(vm: MainViewModel) {
    val activity = LocalContext.current as? Activity

    BackHandler {
        when {
            vm.view == "player" -> vm.goBackFromPlayer()
            vm.view.startsWith("profile_") || vm.view == "settings" -> vm.view = "profile"
            vm.view == "search_result" -> vm.view = "home"
            vm.view != "home" -> vm.view = "home"
            else -> activity?.finish()
        }
    }

    val systemDark = isSystemInDarkTheme()
    val isDark = when (vm.themeMode) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }

    val customColors = if (isDark) {
        CustomColors(
            bg = Color(0xFF090E17),
            panel = Color(0xFF101926),
            panel2 = Color(0xFF162436),
            text = Color(0xFFF3F7FC),
            muted = Color(0xFF8B9AAF),
            cyan = Color(0xFF43D5E8),
            purple = Color(0xFFA855F7),
            orange = Color(0xFFFFB257),
            rose = Color(0xFFFF7F9E),
            green = Color(0xFF4ADE80),
            isDark = true
        )
    } else {
        CustomColors(
            bg = Color(0xFFF1F5F9),
            panel = Color(0xFFFFFFFF),
            panel2 = Color(0xFFF8FAFC),
            text = Color(0xFF0F172A),
            muted = Color(0xFF64748B),
            cyan = Color(0xFF0284C7),
            purple = Color(0xFF9333EA),
            orange = Color(0xFFEA580C),
            rose = Color(0xFFE11D48),
            green = Color(0xFF16A34A),
            isDark = false
        )
    }

    CompositionLocalProvider(LocalCustomColors provides customColors) {
        MaterialTheme(
            colorScheme = if (isDark) {
                darkColorScheme(
                    background = customColors.bg,
                    surface = customColors.panel,
                    primary = customColors.cyan,
                )
            } else {
                lightColorScheme(
                    background = customColors.bg,
                    surface = customColors.panel,
                    primary = customColors.cyan,
                )
            }
        ) {
            Scaffold(
                bottomBar = {
                    if (vm.view != "player" && !vm.view.startsWith("profile_") && vm.view != "settings") {
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
                                selected = vm.view == "schedule",
                                onClick = { vm.view = "schedule" },
                                icon = { Icon(Icons.Default.DateRange, null) },
                                label = { Text("周表") }
                            )
                            NavigationBarItem(
                                selected = vm.view == "leaderboard",
                                onClick = { vm.view = "leaderboard" },
                                icon = { Icon(Icons.Default.Star, null) },
                                label = { Text("排行榜") }
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
                        "search_result" -> SearchResultScreen(vm)
                        "library" -> LibraryView(vm)
                        "schedule" -> WeeklyScheduleScreen(vm)
                        "leaderboard" -> LeaderboardScreen(vm)
                        "profile" -> ProfileView(vm)
                        "profile_history" -> HistoryScreen(vm)
                        "profile_favorites" -> FavoritesScreen(vm)
                        "profile_downloads" -> OfflineCacheScreen(vm)
                        "settings" -> SettingsScreen(vm)
                        "player" -> PlayerViewScreen(vm)
                    }
                }
            }

            if (vm.updateDialogVisible) {
                vm.updateInfo?.let { info ->
                    AlertDialog(
                        onDismissRequest = {
                            if (vm.updateDownloadProgress == null) vm.dismissUpdate()
                        },
                        icon = {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = AppColors.cyan)
                        },
                        title = {
                            Text(
                                info.title.ifBlank { "发现新版本 ${info.versionName}" },
                                color = AppColors.text
                            )
                        },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    "当前版本 ${BuildConfig.VERSION_NAME} → ${info.versionName}",
                                    color = AppColors.cyan,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    info.notes.ifBlank { "修复问题并提升使用体验" },
                                    color = AppColors.muted,
                                    maxLines = 8,
                                    overflow = TextOverflow.Ellipsis
                                )
                                vm.updateDownloadProgress?.let { progress ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(AppColors.panel2)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth((progress.coerceIn(0, 100)) / 100f)
                                                .fillMaxHeight()
                                                .background(AppColors.cyan)
                                        )
                                    }
                                    Text("正在下载 $progress%", color = AppColors.muted, fontSize = 12.sp)
                                }
                                Text(
                                    "更新不是强制的，暂不更新仍可继续使用当前版本。",
                                    color = AppColors.muted,
                                    fontSize = 12.sp
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    activity?.let { vm.downloadAndInstallUpdate(it) }
                                },
                                enabled = activity != null && vm.updateDownloadProgress == null,
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.cyan)
                            ) {
                                Text("下载并安装")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { vm.dismissUpdate() },
                                enabled = vm.updateDownloadProgress == null
                            ) {
                                Text("以后再说", color = AppColors.muted)
                            }
                        },
                        containerColor = AppColors.panel
                    )
                }
            }
        }
    }
}

@Composable
fun HomeView(vm: MainViewModel) {
    val avatars = listOf("👤", "🦊", "🐲", "🐱")
    val avatarEmoji = avatars.getOrElse(vm.userAvatarIndex) { "👤" }
    var selectedCategory by remember { mutableStateOf("精选") }

    val featuredItems = remember(vm.homeSections.firstOrNull()?.items?.firstOrNull()?.id) {
        vm.homeSections.flatMap { it.items }.distinctBy { SourceManager.normalizeTitle(it.title) }.take(5)
    }

    Column(Modifier.fillMaxSize()) {
        // ── TOP BAR (Avatar | Compact Search Bar | History Icon 截图1) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // User Avatar on Left
            Surface(
                shape = CircleShape,
                color = AppColors.cyan.copy(alpha = 0.2f),
                modifier = Modifier
                    .size(36.dp)
                    .clickable { vm.view = "profile" }
            ) {
                AvatarImage(vm.userAvatarIndex, modifier = Modifier.fillMaxSize())
            }

            Spacer(Modifier.width(8.dp))

            // Compact Search Bar in Middle
            // Compact Search Bar in Middle (BasicTextField for zero text clipping)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (vm.query.isNotBlank()) AppColors.cyan else Color.White.copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = AppColors.muted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = vm.query,
                        onValueChange = {
                            vm.query = it
                            if (it.isBlank()) vm.clearSearch()
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = TextStyle(color = AppColors.text, fontSize = 14.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.executeSearch(vm.query) }),
                        cursorBrush = SolidColor(AppColors.cyan),
                        decorationBox = @Composable { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (vm.query.isEmpty()) {
                                    Text("今天你想看些什么？", color = AppColors.muted, fontSize = 12.sp)
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (vm.query.isNotEmpty()) {
                        IconButton(onClick = { vm.clearSearch() }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "清除", tint = AppColors.muted, modifier = Modifier.size(15.dp))
                        }
                        Spacer(Modifier.width(2.dp))
                    }
                    val arrowTint = if (vm.query.isNotBlank()) AppColors.cyan else AppColors.muted.copy(alpha = 0.5f)
                    IconButton(
                        onClick = {
                            if (vm.query.isNotBlank()) vm.executeSearch(vm.query)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Text("➔", color = arrowTint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // History Clock Icon on Right (截图1 风格)
            IconButton(
                onClick = { vm.view = "profile_history" },
                modifier = Modifier.size(36.dp)
            ) {
                Text("🕒", fontSize = 20.sp)
            }
        }

        // ── CATEGORY TABS (精选 | 日漫 | 剧场版) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            listOf("精选", "日漫", "剧场版").forEach { category ->
                val isSelected = selectedCategory == category
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        selectedCategory = category
                        if (category != "精选") {
                            vm.applyFilter(kind = category)
                            vm.view = "library"
                        }
                    }
                ) {
                    Text(
                        category,
                        color = if (isSelected) AppColors.cyan else AppColors.muted,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp
                    )
                    if (isSelected) {
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .width(18.dp)
                                .height(3.dp)
                                .background(AppColors.cyan, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }

        // Active Playback Banner (Jump back to player)
        val activeDetail = vm.activeDetail
        var showPlaybackBanner by remember { mutableStateOf(true) }
        if (activeDetail != null && showPlaybackBanner) {
            val currentEpName = activeDetail.episodes.getOrNull(vm.currentEpisodeIndex)?.name ?: "第1集"
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp)
                    .clickable { vm.view = "player" },
                shape = RoundedCornerShape(10.dp),
                color = AppColors.cyan.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.cyan.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AppColors.cyan, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "正在播放: ${activeDetail.item.title} ($currentEpName)",
                        color = AppColors.text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = AppColors.cyan,
                        modifier = Modifier.clickable { vm.view = "player" }
                    ) {
                        Text(
                            "继续播放",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { showPlaybackBanner = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = AppColors.muted, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

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
                    // ── CAROUSEL BANNER CARD (截图2 风格) ──
                    if (featuredItems.isNotEmpty()) {
                        item(key = "home_carousel_banner") {
                            HomeCarouselBanner(featuredItems) { vm.openMovie(it) }
                        }
                    }

                    // ── QUICK NAV CARDS (热度排行榜 | 番剧排期表) ──
                    item(key = "home_quick_nav") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clickable { vm.view = "leaderboard" },
                                shape = RoundedCornerShape(12.dp),
                                color = AppColors.panel
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = AppColors.orange, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("热度排行榜", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clickable { vm.view = "schedule" },
                                shape = RoundedCornerShape(12.dp),
                                color = AppColors.panel
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.DateRange, contentDescription = null, tint = AppColors.cyan, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("番剧排期表", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    // ── SECTIONS ──
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
                                        items = section.items.take(16),
                                        key = { index, item -> "${section.key}:${item.sourceKey}:${item.id}:$index" }
                                    ) { _, item ->
                                        MovieCard(item, Modifier.width(135.dp).padding(4.dp)) { vm.openMovie(item) }
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
                gridItemsIndexed(
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

@Composable
fun PlayerViewScreen(vm: MainViewModel) {
    val detail = vm.displayedDetail ?: vm.activeDetail ?: return
    var expandedDescription by remember { mutableStateOf(false) }
    var selectedChunkIndex by remember { mutableStateOf(0) }
    var activeTab by remember { mutableStateOf("details") }
    var showAllEpisodesModal by remember { mutableStateOf(false) }

    val currentEpisode = detail.episodes.getOrNull(vm.currentEpisodeIndex)
    val isFav = vm.isFavorite(detail.item)
    val totalSources = vm.alternativeDetails.size.coerceAtLeast(1)

    val chunkSize = 30
    val episodeChunks = remember(detail.episodes) { detail.episodes.chunked(chunkSize) }

    if (showAllEpisodesModal) {
        AllEpisodesModal(vm) { showAllEpisodesModal = false }
    }

    // 进入详情/播放页时按作品加载云端评论
    LaunchedEffect(detail.item.sourceKey, detail.item.id) {
        vm.loadCommentsForActiveDetail()
    }

    // 横屏（全屏播放）时播放器必须精确占满可见区域：
    // 若按宽度推导 16:9 高度（宽×9/16），在不同尺寸/比例的机型上会超出屏幕高度，
    // 导致顶部栏和进度条下方控制行被裁切（见用户反馈的横屏截屏）。
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Column(Modifier.fillMaxSize()) {
        // ── Video Player Area ──
        Box(
            modifier = (if (isLandscape) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                .background(Color.Black)
        ) {
            val playResult = vm.currentPlayResult
            if (playResult != null && playResult.url.isNotBlank()) {
                EmbeddedVideoPlayer(
                    url = playResult.url,
                    type = playResult.type,
                    headers = playResult.headers,
                    referer = playResult.referer,
                    qualities = playResult.qualities,
                    title = detail.item.title,
                    episodeName = currentEpisode?.name.orEmpty(),
                    onBack = { vm.goBackFromPlayer() },
                    onNextEpisode = if (vm.currentEpisodeIndex < detail.episodes.size - 1) {
                        { vm.selectEpisode(vm.currentEpisodeIndex + 1) }
                    } else null,
                    onPrevEpisode = if (vm.currentEpisodeIndex > 0) {
                        { vm.selectEpisode(vm.currentEpisodeIndex - 1) }
                    } else null
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (vm.isPlayLoading) {
                            LoadingSpinner(modifier = Modifier.size(36.dp), color = AppColors.cyan)
                            Spacer(Modifier.height(8.dp))
                            Text(vm.notice, color = AppColors.muted, fontSize = 13.sp)
                        } else if (vm.playError != null) {
                            Text(vm.playError.orEmpty(), color = AppColors.rose, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { vm.retryPlay() }) {
                                Text("重试播放", color = AppColors.cyan)
                            }
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AppColors.muted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(6.dp))
                            Text("等待解析播放地址", color = AppColors.muted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // 横屏时播放器已占满全屏，下方 Tab 与详情内容不再渲染，避免超出可见区域
        if (!isLandscape) {
        // ── Navigation Tabs below Player (动漫 | 评论) ──
        TabRow(
            selectedTabIndex = if (activeTab == "details") 0 else 1,
            containerColor = AppColors.panel,
            contentColor = AppColors.cyan,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = activeTab == "details",
                onClick = { activeTab = "details" },
                text = {
                    Text(
                        "动漫",
                        fontWeight = if (activeTab == "details") FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp
                    )
                }
            )
            Tab(
                selected = activeTab == "comments",
                onClick = { activeTab = "comments" },
                text = {
                    Text(
                        "评论 (${vm.comments.size})",
                        fontWeight = if (activeTab == "comments") FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp
                    )
                }
            )
        }

        // ── Tab Content Area ──
        if (activeTab == "details") {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                // Title and Brief
                item {
                    Spacer(Modifier.height(12.dp))
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
                            Icons.Default.KeyboardArrowDown,
                            if (vm.activeDownloadKeys.isNotEmpty()) "下载中(${vm.activeDownloadKeys.size})" else "下载番剧",
                            enabled = true
                        ) { vm.showDownloadEpisodeModal = true }
                        ActionButton(
                            if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            if (isFav) "已追番" else "追番",
                            tint = if (isFav) AppColors.rose else AppColors.text
                        ) { vm.toggleFavorite(detail.item) }
                        ActionButton(Icons.Default.Share, "分享", enabled = false) {}
                    }

                    if (vm.showDownloadEpisodeModal) {
                        var selectedEpSet by remember { mutableStateOf(setOf<Episode>()) }

                        AlertDialog(
                            onDismissRequest = { vm.showDownloadEpisodeModal = false },
                            title = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("选择要下载的剧集", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    TextButton(onClick = {
                                        selectedEpSet = if (selectedEpSet.size == detail.episodes.size) emptySet() else detail.episodes.toSet()
                                    }) {
                                        Text(if (selectedEpSet.size == detail.episodes.size) "取消全选" else "全选", color = AppColors.cyan, fontSize = 12.sp)
                                    }
                                }
                            },
                            text = {
                                Column(Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                                    Text(
                                        "当前源：${detail.item.sourceTitle} · 已勾选 ${selectedEpSet.size}/${detail.episodes.size} 集",
                                        color = AppColors.cyan,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(3),
                                        modifier = Modifier.fillMaxWidth().weight(1f)
                                    ) {
                                        gridItemsIndexed(detail.episodes) { index, ep ->
                                            val key = "${detail.item.title}_${ep.name}"
                                            val isDownloading = vm.activeDownloadKeys.contains(key)
                                            val isSelected = selectedEpSet.contains(ep)
                                            Surface(
                                                onClick = {
                                                    if (!isDownloading) {
                                                        selectedEpSet = if (isSelected) selectedEpSet - ep else selectedEpSet + ep
                                                    }
                                                },
                                                modifier = Modifier.padding(4.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                color = when {
                                                    isDownloading -> AppColors.cyan.copy(alpha = 0.25f)
                                                    isSelected -> AppColors.rose.copy(alpha = 0.3f)
                                                    else -> AppColors.panel2
                                                },
                                                border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, AppColors.rose) else null
                                            ) {
                                                Box(
                                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        when {
                                                            isDownloading -> "${ep.name}\n(下载中)"
                                                            isSelected -> "✓ ${ep.name}"
                                                            else -> ep.name
                                                        },
                                                        color = when {
                                                            isDownloading -> AppColors.cyan
                                                            isSelected -> AppColors.rose
                                                            else -> AppColors.text
                                                        },
                                                        fontSize = 12.sp,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                                        maxLines = 2
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val toDownload = selectedEpSet.toList()
                                        vm.showDownloadEpisodeModal = false
                                        toDownload.forEach { ep ->
                                            vm.startDownloadEpisode(detail, ep)
                                        }
                                    },
                                    enabled = selectedEpSet.isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.rose)
                                ) {
                                    Text(
                                        if (selectedEpSet.isEmpty()) "请选择剧集" else "确认下载 (${selectedEpSet.size}集)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { vm.showDownloadEpisodeModal = false }) {
                                    Text("取消", color = AppColors.muted)
                                }
                            },
                            containerColor = AppColors.panel
                        )
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
                        Text(
                            "共 ${detail.episodes.size} 集 >",
                            color = AppColors.cyan,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showAllEpisodesModal = true }
                        )
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
                        gridItemsIndexed(displayedEpisodes) { indexInChunk, ep ->
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
        } else {
            // Standalone Comments Tab Screen
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                // TEMP: 评论发送入口暂时隐藏，只保留评论读取和展示。
                Text(
                    "评论发送暂时关闭，仅展示已有评论",
                    color = AppColors.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                if (vm.comments.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("暂无可展示评论", color = AppColors.muted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(vm.comments.size) { index ->
                            val comment = vm.comments[index]
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = AppColors.panel2)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = AppColors.cyan.copy(alpha = 0.2f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("👤", fontSize = 18.sp)
                                        }
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(comment.nick, color = AppColors.cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(
                                                if (comment.ts > 0L) {
                                                    android.text.format.DateUtils.getRelativeTimeSpanString(comment.ts).toString()
                                                } else "刚刚",
                                                color = AppColors.muted,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(comment.text, color = AppColors.text, fontSize = 14.sp)
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
}

@Composable
fun AllEpisodesModal(vm: MainViewModel, onDismiss: () -> Unit) {
    val detail = vm.activeDetail ?: return
    var isAscending by remember { mutableStateOf(true) }
    var selectedChunkIndex by remember { mutableStateOf(0) }
    val chunkSize = 30

    val orderedEpisodes: List<Pair<Int, Episode>> = remember(detail.episodes, isAscending) {
        val mapped = detail.episodes.mapIndexed { idx, ep -> idx to ep }
        if (isAscending) mapped else mapped.reversed()
    }

    val chunks: List<List<Pair<Int, Episode>>> = remember(orderedEpisodes) { orderedEpisodes.chunked(chunkSize) }
    val currentChunk: List<Pair<Int, Episode>> = chunks.getOrNull(selectedChunkIndex) ?: orderedEpisodes

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭", color = AppColors.cyan)
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("全部选集 (${detail.episodes.size}集)", color = AppColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { isAscending = !isAscending }) {
                    Text(if (isAscending) "排序: 正序 ⬆" else "排序: 倒序 ⬇", color = AppColors.cyan, fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                if (chunks.size > 1) {
                    LazyRow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        items(chunks.size) { idx ->
                            val chunkList = chunks[idx]
                            val firstName = chunkList.firstOrNull()?.second?.name ?: ""
                            val lastName = chunkList.lastOrNull()?.second?.name ?: ""
                            FilterChip(
                                selected = idx == selectedChunkIndex,
                                onClick = { selectedChunkIndex = idx },
                                label = { Text("$firstName - $lastName", fontSize = 11.sp) },
                                modifier = Modifier.padding(end = 4.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.cyan.copy(alpha = 0.25f),
                                    selectedLabelColor = AppColors.cyan
                                )
                            )
                        }
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(currentChunk.size) { idx ->
                        val (globalIdx, ep) = currentChunk[idx]
                        val isPlaying = globalIdx == vm.currentEpisodeIndex
                        Surface(
                            onClick = {
                                vm.selectEpisode(globalIdx)
                                onDismiss()
                            },
                            modifier = Modifier.padding(3.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isPlaying) AppColors.cyan.copy(alpha = 0.2f) else AppColors.panel2,
                            border = if (isPlaying) androidx.compose.foundation.BorderStroke(1.5.dp, AppColors.cyan) else null
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    ep.name,
                                    color = if (isPlaying) AppColors.cyan else AppColors.text,
                                    fontSize = 12.sp,
                                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = AppColors.panel,
        shape = RoundedCornerShape(16.dp)
    )
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
fun AccountEntryCard(vm: MainViewModel) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.panel),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (TEMP_ACCOUNT_AUTH_DISABLED) "当前为本地使用模式" else if (vm.accountUser == null) "登录后同步观看记录与收藏" else "云端账号",
                color = AppColors.text,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (TEMP_ACCOUNT_AUTH_DISABLED) "登录、注册、邮箱和云端同步暂时关闭；观看记录、收藏和离线缓存均可直接使用。"
                else if (vm.accountUser == null) "未登录时观看和离线缓存仍可使用，登录后开启账号数据同步。" else "${vm.accountUser?.nickname} · ${vm.accountUser?.email}",
                color = AppColors.muted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            if (TEMP_ACCOUNT_AUTH_DISABLED) {
                Text("账号功能暂时停用", color = AppColors.muted, fontSize = 12.sp)
            } else if (vm.accountUser == null) {
                Button(
                    onClick = { /* account dialog is enabled when TEMP_ACCOUNT_AUTH_DISABLED is false */ },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.cyan),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("登录 / 注册") }
            } else {
                OutlinedButton(onClick = { vm.logoutAccount() }) { Text("退出云端账号") }
            }
            if (vm.accountMessage.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(vm.accountMessage, color = AppColors.muted, fontSize = 12.sp)
            }
        }
    }
    /* TEMP: 保留原 AccountDialog 调用位置，恢复账号开关后可重新接入。
    if (dialogVisible) {
        AccountDialog(vm) { dialogVisible = false }
    }
    */
}

// TEMP: 账号登录/注册弹窗暂时不可达，保留实现以便后续恢复。
@Composable
fun AccountDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var registering by remember { mutableStateOf(false) }
    var forgot by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf(vm.userEmail) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf(vm.commentNick) }
    var code by remember { mutableStateOf("") }

    LaunchedEffect(vm.accountUser) {
        if (vm.accountUser != null) onDismiss()
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppColors.panel,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when {
                            forgot -> "重置密码"
                            registering -> "注册账号"
                            else -> "登录账号"
                        },
                        color = AppColors.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = AppColors.muted)
                    }
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("邮箱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (registering) {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = { nickname = it.take(24) },
                        label = { Text("昵称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (forgot || registering) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.take(6) },
                            label = { Text("邮箱验证码") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                vm.requestAccountCode(email, if (forgot) "reset-password" else "register")
                            },
                            enabled = !vm.accountBusy && isValidEmail(email)
                        ) { Text("获取验证码") }
                    }
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (forgot) "新密码" else "密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (registering || forgot) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("确认密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Button(
                    onClick = {
                        when {
                            forgot -> vm.resetAccountPassword(email, code, password, confirmPassword)
                            registering -> vm.registerAccount(email, password, confirmPassword, nickname, code)
                            else -> vm.loginAccount(email, password)
                        }
                    },
                    enabled = !vm.accountBusy && isValidEmail(email) &&
                        (if (forgot || registering) code.length == 6 && isPasswordStrong(password) && password == confirmPassword
                        else password.isNotBlank()),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.cyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (forgot) "重置密码" else if (registering) "注册并同步" else "登录并同步")
                }
                TextButton(
                    onClick = {
                        forgot = false
                        registering = !registering
                        code = ""
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text(if (registering) "已有账号？返回登录" else "没有账号？注册") }
                if (!registering && !forgot) {
                    TextButton(
                        onClick = { forgot = true; code = "" },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) { Text("忘记密码？") }
                }
                if (vm.accountMessage.isNotBlank()) {
                    Text(vm.accountMessage, color = AppColors.muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun ProfileView(vm: MainViewModel) {
    val avatars = listOf("👤", "🦊", "🐲", "🐱")
    val avatarEmoji = avatars.getOrElse(vm.userAvatarIndex) { "👤" }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        // User Profile Header Card with Settings Button
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AppColors.panel),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AppColors.cyan.copy(alpha = 0.2f),
                        modifier = Modifier.size(56.dp)
                    ) {
                        if (!vm.accountUser?.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = vm.accountUser?.avatarUrl,
                                contentDescription = "账号头像",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AvatarImage(vm.userAvatarIndex, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("个人中心", color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(vm.userEmail, color = AppColors.muted, fontSize = 13.sp)
                    }
                    IconButton(onClick = { vm.view = "settings" }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置", tint = AppColors.cyan)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        item { AccountEntryCard(vm) }

        // Secondary Entrance Buttons (二级界面入口)
        item {
            Text("管理与服务", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))

            ProfileEntryCard(
                title = "观看历史",
                subtitle = "已播放 ${vm.historyList.size} 条本机记录",
                icon = Icons.Default.Refresh,
                onClick = {
                    vm.view = "profile_history"
                }
            )
            Spacer(Modifier.height(8.dp))

            ProfileEntryCard(
                title = "我的追番 / 收藏",
                subtitle = "已收藏 ${vm.favoritesList.size} 部本机作品",
                icon = Icons.Default.Favorite,
                onClick = {
                    vm.view = "profile_favorites"
                }
            )
            Spacer(Modifier.height(8.dp))

            ProfileEntryCard(
                title = "离线缓存",
                subtitle = "已缓存 ${vm.getDownloadedFilesList().size} 个本地视频文件",
                icon = Icons.Default.PlayArrow,
                onClick = { vm.view = "profile_downloads" }
            )
            Spacer(Modifier.height(16.dp))
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
    }
}

@Composable
fun ProfileEntryCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.panel),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = AppColors.cyan.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = title, tint = AppColors.cyan, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = AppColors.muted, fontSize = 12.sp)
            }
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = AppColors.muted)
        }
    }
}

@Composable
fun HistoryScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.view = "profile" }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = AppColors.text)
            }
            Text("观看历史", color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (vm.historyList.isNotEmpty()) {
                TextButton(onClick = { vm.clearHistory() }) {
                    Text("清空记录", color = AppColors.rose, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (vm.historyList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无观看历史记录", color = AppColors.muted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
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
        }
    }
}

@Composable
fun FavoritesScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.view = "profile" }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = AppColors.text)
            }
            Text("我的追番 / 收藏", color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))

        if (vm.favoritesList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂未添加任何收藏", color = AppColors.muted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
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
}

@Composable
fun LoginRequiredScreen(vm: MainViewModel, title: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = AppColors.cyan, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = AppColors.text, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.view = "profile" }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.cyan)) {
            Text("去登录")
        }
    }
}

@Composable
fun OfflineCacheScreen(vm: MainViewModel) {
    var refreshKey by remember { mutableStateOf(0) }
    val downloads = remember(refreshKey, vm.notice, vm.activeDownloadKeys.size) { vm.getDownloadedFilesList() }
    val activeKeys = vm.activeDownloadKeys.toList()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.view = "profile" }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = AppColors.text)
            }
            Text(
                "离线缓存 (${downloads.size}已完成" + if (activeKeys.isNotEmpty()) " · ${activeKeys.size}下载中)" else ")",
                color = AppColors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(12.dp))

        if (downloads.isEmpty() && activeKeys.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = AppColors.muted, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("暂无离线缓存视频", color = AppColors.muted, fontSize = 14.sp)
                    Text("在详情页点击「下载番剧」选完后点击「确认下载」", color = AppColors.muted.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (activeKeys.isNotEmpty()) {
                    item {
                        Text("正在缓存中 (${activeKeys.size})", color = AppColors.cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    items(activeKeys.size) { idx ->
                        val key = activeKeys[idx]
                        val (title, epName) = vm.activeDownloadInfo[key] ?: (key to "下载中")
                        val progressText = vm.activeDownloadProgress[key] ?: "下载中..."
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = AppColors.cyan.copy(alpha = 0.12f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.cyan.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LoadingSpinner(modifier = Modifier.size(22.dp), color = AppColors.cyan)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(title, color = AppColors.text, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(2.dp))
                                    Text(epName, color = AppColors.cyan, fontSize = 12.sp)
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = AppColors.cyan,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text(
                                        "缓存中 $progressText",
                                        color = AppColors.bg,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(10.dp))
                    }
                }

                if (downloads.isNotEmpty()) {
                    if (activeKeys.isNotEmpty()) {
                        item {
                            Text("已完成离线缓存 (${downloads.size})", color = AppColors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                    items(downloads.size) { index ->
                        val downloadItem = downloads[index]
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { vm.playOfflineVideo(downloadItem) },
                            colors = CardDefaults.cardColors(containerColor = AppColors.panel)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val context = LocalContext.current
                                val coverPath: String = downloadItem.coverPath.orEmpty()
                                AsyncImage(
                                    model = coverRequest(context, coverPath),
                                    contentDescription = null,
                                    modifier = Modifier.size(50.dp, 70.dp).clip(RoundedCornerShape(6.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        downloadItem.title,
                                        color = AppColors.text,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        downloadItem.episodeName,
                                        color = AppColors.cyan,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "文件大小：${downloadItem.fileSizeFormatted} · 【点击即可播放】",
                                        color = AppColors.muted,
                                        fontSize = 11.sp
                                    )
                                }
                                IconButton(onClick = {
                                    vm.deleteDownloadedFile(downloadItem)
                                    refreshKey++
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除缓存", tint = AppColors.rose)
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
fun SearchResultScreen(vm: MainViewModel) {
    androidx.activity.compose.BackHandler {
        vm.view = "home"
        vm.isSearchActive = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                vm.view = "home"
                vm.isSearchActive = false
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回首页", tint = AppColors.text)
            }
            Spacer(Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("搜索结果", color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                if (vm.query.isNotBlank()) {
                    Text("「${vm.query}」", color = AppColors.cyan, fontSize = 12.sp)
                }
            }
            if (vm.loading) {
                LoadingSpinner(modifier = Modifier.size(20.dp), color = AppColors.cyan)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (vm.notice.isNotBlank()) {
            Text(vm.notice, color = AppColors.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
            Spacer(Modifier.height(8.dp))
        }

        if (vm.loading && vm.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingSpinner(modifier = Modifier.size(36.dp), color = AppColors.cyan)
                    Spacer(Modifier.height(12.dp))
                    Text("正在全网检索「${vm.query}」...", color = AppColors.muted, fontSize = 14.sp)
                }
            }
        } else if (vm.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("未找到「${vm.query}」相关动漫作品", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("请检查拼写或尝试简称（如：芙利莲）", color = AppColors.muted, fontSize = 13.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(vm.items.size) { idx ->
                    val item = vm.items[idx]
                    MovieCard(item, Modifier.padding(4.dp)) { vm.openMovie(item) }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    var emailInput by remember { mutableStateOf(vm.userEmail) }
    var emailCodeInput by remember { mutableStateOf("") }
    var emailSuccess by remember { mutableStateOf(false) }
    var nicknameInput by remember(vm.accountUser?.nickname) {
        mutableStateOf(vm.accountUser?.nickname.orEmpty())
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.uploadAccountAvatar(uri)
    }

    var oldPassInput by remember { mutableStateOf("") }
    var newPassInput by remember { mutableStateOf("") }
    var confirmPassInput by remember { mutableStateOf("") }
    var passSuccess by remember { mutableStateOf(false) }

    val avatars = listOf("👤", "🦊", "🐲", "🐱")

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.view = "profile" }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = AppColors.text)
                }
                Text("系统设置", color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AppColors.panel),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "软件更新",
                                color = AppColors.text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                "当前版本 ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）",
                                color = AppColors.muted,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = { vm.checkForAppUpdate(manual = true) },
                            enabled = !vm.updateChecking && vm.updateDownloadProgress == null,
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.cyan)
                        ) {
                            if (vm.updateChecking) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.9f))
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (vm.updateChecking) "检查中" else "检查更新")
                        }
                    }
                    if (vm.updateMessage.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            vm.updateMessage,
                            color = if (vm.updateMessage.startsWith("检查失败") ||
                                vm.updateMessage.startsWith("下载失败")
                            ) AppColors.rose else AppColors.muted,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Appearance Settings Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AppColors.panel),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("外观设置", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("选择界面色彩风格与配色方案", color = AppColors.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val modes = listOf("system" to "跟随系统", "light" to "浅色模式", "dark" to "深色模式")
                        modes.forEach { (modeKey, label) ->
                            val selected = vm.themeMode == modeKey
                            FilterChip(
                                selected = selected,
                                onClick = { vm.updateThemeMode(modeKey) },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.cyan.copy(alpha = 0.25f),
                                    selectedLabelColor = AppColors.cyan
                                )
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Account Nickname Settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AppColors.panel),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("账号昵称", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("昵称会跟随账号同步，并显示在你发表的评论中", color = AppColors.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = nicknameInput,
                        onValueChange = { nicknameInput = it.take(24) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("昵称") },
                        singleLine = true,
                        enabled = vm.accountUser != null && !vm.accountBusy
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { vm.changeAccountNickname(nicknameInput) },
                        enabled = vm.accountUser != null &&
                            nicknameInput.trim().isNotEmpty() &&
                            nicknameInput.trim() != vm.accountUser?.nickname &&
                            !vm.accountBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.cyan),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("保存昵称")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Account Avatar Settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AppColors.panel),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("本地头像设置", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("更换在本地保存展示的个性头像", color = AppColors.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        (0..3).forEach { idx ->
                            val isSelected = vm.userAvatarIndex == idx
                            Surface(
                                onClick = { vm.updateUserAvatar(idx) },
                                shape = CircleShape,
                                color = if (isSelected) AppColors.cyan.copy(alpha = 0.3f) else AppColors.panel2,
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.5.dp, AppColors.cyan) else null,
                                modifier = Modifier.size(58.dp)
                            ) {
                                AvatarImage(idx, modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { avatarPicker.launch("image/*") },
                        enabled = vm.accountUser != null && !vm.accountBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.cyan),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (vm.accountUser == null) "登录后上传头像" else "从相册上传头像（JPG/PNG）")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Change Email Settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AppColors.panel),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("账户邮箱设置", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("输入新邮箱地址，须符合标准邮箱格式", color = AppColors.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))

                    val validEmail = isValidEmail(emailInput)
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it; emailSuccess = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("邮箱地址") },
                        singleLine = true,
                        isError = emailInput.isNotEmpty() && !validEmail,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.cyan,
                            unfocusedBorderColor = AppColors.muted.copy(alpha = 0.3f)
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = emailCodeInput,
                            onValueChange = { emailCodeInput = it.take(6); emailSuccess = false },
                            modifier = Modifier.weight(1f),
                            label = { Text("邮箱验证码") },
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { vm.requestAccountCode(emailInput, "change-email") },
                            enabled = vm.accountUser != null && validEmail && !vm.accountBusy
                        ) { Text("获取验证码") }
                    }

                    if (emailInput.isNotEmpty() && !validEmail) {
                        Spacer(Modifier.height(4.dp))
                        Text("格式不合规（须包含 @ 与有效域名后缀）", color = AppColors.rose, fontSize = 12.sp)
                    }

                    if (emailSuccess) {
                        Spacer(Modifier.height(4.dp))
                        Text("邮箱保存成功！", color = AppColors.green, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (validEmail && emailCodeInput.length == 6) {
                                vm.changeAccountEmail(emailInput, emailCodeInput)
                                emailSuccess = false
                            }
                        },
                        enabled = vm.accountUser != null && validEmail && emailCodeInput.length == 6 && emailInput != vm.userEmail,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.cyan),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("保存邮箱修改")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Change Password Settings
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AppColors.panel),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("账户密码修改", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("修改密码前需要验证当前账号邮箱。密码至少8位，并包含四类字符中的至少3种。", color = AppColors.muted, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = oldPassInput,
                            onValueChange = { oldPassInput = it.take(6); passSuccess = false },
                            modifier = Modifier.weight(1f),
                            label = { Text("邮箱验证码") },
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { vm.requestAccountCode(vm.accountUser?.email.orEmpty(), "reset-password") },
                            enabled = vm.accountUser != null && !vm.accountBusy
                        ) { Text("获取验证码") }
                    }
                    Spacer(Modifier.height(8.dp))

                    val isStrong = isPasswordStrong(newPassInput)
                    OutlinedTextField(
                        value = newPassInput,
                        onValueChange = { newPassInput = it; passSuccess = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("新密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = newPassInput.isNotEmpty() && !isStrong,
                        singleLine = true
                    )
                    if (newPassInput.isNotEmpty() && !isStrong) {
                        Spacer(Modifier.height(4.dp))
                        Text("⚠️ 密码强度不达标：须至少8位并包含大/小写/数字/特殊字符中至少3种组合", color = AppColors.rose, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(8.dp))
                    val passMatches = confirmPassInput == newPassInput
                    OutlinedTextField(
                        value = confirmPassInput,
                        onValueChange = { confirmPassInput = it; passSuccess = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("确认新密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        isError = confirmPassInput.isNotEmpty() && !passMatches,
                        singleLine = true
                    )
                    if (confirmPassInput.isNotEmpty() && !passMatches) {
                        Spacer(Modifier.height(4.dp))
                        Text("⚠️ 两次输入的密码不一致", color = AppColors.rose, fontSize = 12.sp)
                    }

                    if (passSuccess) {
                        Spacer(Modifier.height(4.dp))
                        Text("✅ 密码已成功更新！", color = AppColors.green, fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (isStrong && passMatches) {
                                vm.resetAccountPassword(
                                    vm.accountUser?.email.orEmpty(),
                                    oldPassInput,
                                    newPassInput,
                                    confirmPassInput
                                )
                                passSuccess = false
                            }
                        },
                        enabled = vm.accountUser != null && oldPassInput.length == 6 && isStrong && passMatches && newPassInput.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.cyan),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("确认修改密码")
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyScheduleScreen(vm: MainViewModel) {
    val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val calDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
    val defaultIndex = if (calDay == Calendar.SUNDAY) 6 else (calDay - 2).coerceIn(0, 6)
    var selectedDayIndex by remember { mutableStateOf(defaultIndex) }

    val pool = vm.allPoolItems()
    val dayItems = remember(selectedDayIndex, pool) {
        if (pool.isEmpty()) emptyList()
        else {
            val chunkSize = maxOf(1, pool.size / 7)
            val chunked = pool.chunked(chunkSize)
            chunked.getOrElse(selectedDayIndex) { pool.take(6) }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.DateRange, contentDescription = null, tint = AppColors.cyan, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("番剧连载周表", color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))

        ScrollableTabRow(
            selectedTabIndex = selectedDayIndex,
            containerColor = AppColors.panel,
            contentColor = AppColors.cyan,
            edgePadding = 0.dp
        ) {
            days.forEachIndexed { idx, dayName ->
                Tab(
                    selected = selectedDayIndex == idx,
                    onClick = { selectedDayIndex = idx },
                    text = {
                        Text(
                            text = if (idx == defaultIndex) "$dayName(今天)" else dayName,
                            fontWeight = if (selectedDayIndex == idx) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (dayItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无当天连载更新计划", color = AppColors.muted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(dayItems.size) { index ->
                    val item = dayItems[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable { vm.openMovie(item) },
                        colors = CardDefaults.cardColors(containerColor = AppColors.panel)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = coverRequest(LocalContext.current, item.cover),
                                contentDescription = null,
                                modifier = Modifier.size(60.dp, 84.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text("更新至第 ${index + 12} 集 · 周${days[selectedDayIndex]} 23:30", color = AppColors.cyan, fontSize = 12.sp)
                                Spacer(Modifier.height(2.dp))
                                Text("分类: ${item.kind.ifEmpty { "日漫/热血" }} | 来源: ${item.sourceTitle.ifEmpty { item.sourceKey }}", color = AppColors.muted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderboardScreen(vm: MainViewModel) {
    val tabs = listOf("TV番剧", "剧场番剧", "国漫")
    var selectedTabIdx by remember { mutableStateOf(0) }

    val pool = vm.allPoolItems()
    val filteredItems = remember(selectedTabIdx, pool) {
        val targetKind = when (selectedTabIdx) {
            0 -> "TV"
            1 -> "剧场"
            else -> "国漫"
        }
        val matches = pool.filter { it.kind.contains(targetKind) || (selectedTabIdx == 0 && !it.kind.contains("国漫") && !it.kind.contains("剧场")) }
        if (matches.isNotEmpty()) matches else pool
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Star, contentDescription = null, tint = AppColors.orange, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("热门作品排行榜", color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))

        TabRow(
            selectedTabIndex = selectedTabIdx,
            containerColor = AppColors.panel,
            contentColor = AppColors.cyan
        ) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTabIdx == idx,
                    onClick = { selectedTabIdx = idx },
                    text = { Text(title, fontWeight = if (selectedTabIdx == idx) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (filteredItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无排行榜数据", color = AppColors.muted, fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(filteredItems.size) { rankIdx ->
                    val item = filteredItems[rankIdx]
                    val rankNum = rankIdx + 1
                    val badgeColor = when (rankNum) {
                        1 -> Color(0xFFFFD700)
                        2 -> Color(0xFFC0C0C0)
                        3 -> Color(0xFFCD7F32)
                        else -> AppColors.muted
                    }
                    val badgeIcon = when (rankNum) {
                        1 -> "🥇"
                        2 -> "🥈"
                        3 -> "🥉"
                        else -> "$rankNum"
                    }
                    val heatVal = (99 - rankIdx).coerceAtLeast(80) / 10.0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { vm.openMovie(item) },
                        colors = CardDefaults.cardColors(containerColor = if (rankNum <= 3) AppColors.panel2 else AppColors.panel)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = badgeColor.copy(alpha = 0.2f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(badgeIcon, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = badgeColor)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            AsyncImage(
                                model = coverRequest(LocalContext.current, item.cover),
                                contentDescription = null,
                                modifier = Modifier.size(54.dp, 76.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(3.dp))
                                Text("🔥 热度: ${heatVal}分 · 播放 18.${(9 - rankIdx).coerceAtLeast(1)}万", color = AppColors.orange, fontSize = 12.sp)
                                Spacer(Modifier.height(2.dp))
                                Text("${item.year} · ${item.kind.ifEmpty { "热血" }} | 来源: ${item.sourceTitle.ifEmpty { item.sourceKey }}", color = AppColors.muted, fontSize = 11.sp)
                            }
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
fun AvatarImage(avatarIndex: Int, modifier: Modifier = Modifier) {
    val assetPath = "file:///android_asset/avatars/anime_avatar_${(avatarIndex % 4) + 1}.jpg"
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(assetPath)
            .crossfade(true)
            .build(),
        contentDescription = "二次元头像",
        modifier = modifier.clip(CircleShape),
        contentScale = ContentScale.Crop
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeCarouselBanner(items: List<SourceItem>, onSelect: (SourceItem) -> Unit) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })

    LaunchedEffect(items.size) {
        while (items.size > 1) {
            delay(4000)
            val nextPage = (pagerState.currentPage + 1) % items.size
            pagerState.animateScrollToPage(nextPage)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(14.dp))
        ) { page ->
            val item = items[page]
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onSelect(item) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.panel)
            ) {
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = coverRequest(LocalContext.current, item.cover),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 0.28f },
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                    colors = listOf(AppColors.bg, Color.Transparent),
                                    startX = 0f,
                                    endX = 550f
                                )
                            )
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                )
                            )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = AppColors.cyan.copy(alpha = 0.25f)
                            ) {
                                Text(
                                    "🔥 热门推荐",
                                    color = AppColors.cyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                item.title,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${item.status.ifEmpty { "热播中" }} · ${item.kind.ifEmpty { "动漫" }}",
                                color = AppColors.muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.4f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .width(105.dp)
                                .fillMaxHeight()
                        ) {
                            AsyncImage(
                                model = coverRequest(LocalContext.current, item.cover),
                                contentDescription = item.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter
                            )
                        }
                    }
                }
            }
        }

        if (items.size > 1) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(items.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(4.dp)
                            .width(if (isSelected) 16.dp else 6.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) AppColors.cyan else Color.White.copy(alpha = 0.3f))
                    )
                }
            }
        }
    }
}

private val standardAnimeGenres = listOf(
    "热血", "奇幻", "战斗", "穿越", "后宫", "恋爱", "校园", "日常",
    "治愈", "搞笑", "悬疑", "科幻", "冒险", "魔法", "机战", "推理",
    "运动", "音乐", "偶像", "职场", "历史", "美食", "萌系", "百合", "泡面番"
)

private fun resolveCardGenre(item: SourceItem): String {
    val scope = "${item.tags.joinToString(" ")} ${item.kind} ${item.description} ${item.title}"
    val matches = standardAnimeGenres.filter { scope.contains(it) }
    if (matches.isNotEmpty()) {
        return matches.distinct().take(2).joinToString(" · ")
    }
    val cleanKind = item.kind.replace(Regex("(全部|首页|推荐|热门|最新|分类)"), "").trim()
    return when {
        cleanKind.isNotBlank() -> cleanKind
        item.year.isNotBlank() -> "${item.year} 动漫"
        else -> "热血 · 奇幻"
    }
}

private fun resolveCardStatus(item: SourceItem): String {
    val rawStatus = item.status.trim()
    val combined = "$rawStatus ${item.tags.joinToString(" ")}".trim()

    val isFinished = combined.contains("完结") || combined.contains("全集")
    val epMatch = Regex("(更新至)?(第?\\d+[集话])|([全共]?\\d+[集话])|(\\d+[集话])").find(combined)?.value ?: ""

    return if (isFinished) {
        val detail = when {
            epMatch.isNotBlank() -> if (epMatch.startsWith("共") || epMatch.startsWith("全")) epMatch else "共$epMatch"
            rawStatus.isNotBlank() && !rawStatus.contains("完结") -> rawStatus
            else -> "全集"
        }
        "已完结 | $detail"
    } else {
        val detail = when {
            epMatch.isNotBlank() -> if (epMatch.startsWith("更新")) epMatch else "更新至$epMatch"
            rawStatus.isNotBlank() && rawStatus != "连载中" -> rawStatus
            else -> "更新中"
        }
        "连载中 | $detail"
    }
}

@Composable
fun MovieCard(item: SourceItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val statusText = resolveCardStatus(item)
    val genreText = resolveCardGenre(item)

    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = coverRequest(LocalContext.current, item.cover),
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                ) {
                    Text(
                        statusText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 6.dp, bottom = 4.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                item.title,
                color = AppColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                genreText,
                color = AppColors.muted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp)
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
