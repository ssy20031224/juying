@file:OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.material.ExperimentalMaterialApi::class
)

package com.juying.app

import android.app.Application
import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.window.Dialog
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
import androidx.compose.material.DismissDirection
import androidx.compose.material.DismissValue
import androidx.compose.material.SwipeToDismiss
import androidx.compose.material.rememberDismissState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.google.gson.Gson
import com.juying.app.source.*
import com.juying.app.ui.EmbeddedVideoPlayer
import com.juying.app.ui.PipController
import com.juying.app.ui.PlayerInteractionPolicy
import com.juying.app.update.AppUpdateInfo
import com.juying.app.update.AppUpdateManager
import com.juying.app.update.UpdateCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import java.io.File
import java.util.Calendar
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

// 维护开关；当前账号与评论能力均已恢复。
private const val TEMP_ACCOUNT_AUTH_DISABLED = false
private const val TEMP_COMMENT_POSTING_DISABLED = false

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
    private val lanercDiscoveryRepository = LanercDiscoveryRepository()
    private val animeMetadataRepository = AnimeMetadataRepository(application)
    private val commentRepository = CommentRepository(application)
    private val accountRepository = AccountRepository(application)
    private val announcementRepository = AnnouncementRepository(application)
    private val notificationRepository = NotificationRepository(application)
    private val gson = Gson()
    private var commentsLoadedFor: String? = null
    private var isAppInitialized = false
    private var playerReturnView = "home"
    private var pendingEpisodeName: String? = null
    // 拍照返回时 Activity 可能被重建（remember 状态丢失），故放入 ViewModel 暂存
    var pendingAvatarPhotoUri: Uri? = null
    private data class PlayerNavigationState(
        val activeDetail: DetailResult?,
        val currentEpisodeIndex: Int,
        val currentPlayResult: PlayResult?,
        val isPlayLoading: Boolean,
        val playError: String?,
        val relatedItems: List<SourceItem>,
        val alternativeDetails: List<Pair<String, DetailResult>>,
        val activeAlternativeIndex: Int,
        val notice: String
    )
    private val playerNavigationStack = ArrayDeque<PlayerNavigationState>()

    // Cached pool of home section items — reused by fetchLibrary() to avoid re-fetching
    private var cachedHomePool: List<SourceItem> = emptyList()
    // A refresh replaces the previous refresh. This prevents late source
    // responses from reordering or overwriting a newer home result.
    private var homeLoadJob: Job? = null
    // Cancels the previous search when a new one starts
    private var searchJob: Job? = null
    // Cancels the previous detail/source discovery when another card opens.
    // Without this guard a late empty response can erase a newer title's
    // episodes and source chips.
    private var detailLoadJob: Job? = null
    private var detailLoadGeneration = 0
    // Exactly one episode URL may be resolving at a time. Cancelling the
    // previous request prevents a late result from replacing a newer episode.
    private var playResolveJob: Job? = null
    private var playResolveGeneration = 0

    var view by mutableStateOf("home") // home, library, player, profile
    var query by mutableStateOf("")
    var isSearchActive by mutableStateOf(false)
    var items by mutableStateOf<List<SourceItem>>(emptyList())
    var homeSections by mutableStateOf<List<HomeSection>>(emptyList())
    var discoverySections by mutableStateOf<List<HomeSection>>(emptyList())
        private set
    var lanercRankings by mutableStateOf<Map<RankingKind, List<SourceRankingEntry>>>(emptyMap())
        private set
    var lanercSchedule by mutableStateOf<List<ScheduleEntry>>(emptyList())
        private set
    var lanercSeasons by mutableStateOf<List<SeasonalRecommendation>>(emptyList())
        private set
    var lanercDiscoveryLoading by mutableStateOf(false)
        private set
    var lanercDiscoveryUpdatedAt by mutableStateOf(0L)
        private set
    var lanercDiscoveryError by mutableStateOf<String?>(null)
        private set
    var lanercDiscoveryMessage by mutableStateOf("")
        private set
    var animeCategoryRankings by mutableStateOf<Map<AnimeRankingCategory, List<SourceRankingEntry>>>(
        emptyMap()
    )
        private set
    var animeCategoryRankingLoading by mutableStateOf<Set<AnimeRankingCategory>>(emptySet())
        private set
    private val animeCategoryRankingJobs = mutableMapOf<AnimeRankingCategory, Job>()
    private val animeCategorySearchCompleted = mutableSetOf<AnimeRankingCategory>()

    // Player & Detail State
    var activeDetail by mutableStateOf<DetailResult?>(null)
    var currentEpisodeIndex by mutableStateOf(0)
    var currentPlayResult by mutableStateOf<PlayResult?>(null)
    var isPlayLoading by mutableStateOf(false)
    var playError by mutableStateOf<String?>(null)
    var relatedItems by mutableStateOf<List<SourceItem>>(emptyList())
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
    var searchHistoryEntries by mutableStateOf(storageManager.getSearchHistoryEntries())
        private set
    val searchHistory: List<String>
        get() = searchHistoryEntries.map(SearchHistoryEntry::query)
    var commentNick by mutableStateOf(storageManager.getCommentNick())
    var commentDraft by mutableStateOf("")
    var comments by mutableStateOf<List<CloudComment>>(emptyList())
    var commentPosting by mutableStateOf(false)
    var commentImageUrl by mutableStateOf("")
    var commentImageUploading by mutableStateOf(false)

    // Cloud account state. Anonymous local storage remains the default.
    var accountUser by mutableStateOf<AccountUser?>(null)
    var accountBusy by mutableStateOf(false)
    var accountDialogVisible by mutableStateOf(false)
    var accountMessage by mutableStateOf("")
    var accountCodeCooldownSeconds by mutableIntStateOf(0)
        private set
    private var accountCodeCooldownJob: Job? = null

    private var lastHistoryProgressWriteAt = 0L
    private var lastHistoryProgressKey = ""
    private var lastHistoryProgressPercent = -1
    private val placeholderRecoveryKeys = mutableSetOf<String>()

    // Theme & Account Settings State
    var themeMode by mutableStateOf(storageManager.getThemeMode())
    var userEmail by mutableStateOf(storageManager.getUserEmail())
    var userAvatarIndex by mutableStateOf(storageManager.getUserAvatar())
    var updateChecking by mutableStateOf(false)
    var updateInfo by mutableStateOf<AppUpdateInfo?>(null)
    var updateDialogVisible by mutableStateOf(false)
    var updateMessage by mutableStateOf("")
    var updateDownloadProgress by mutableStateOf<Int?>(null)
    var announcement by mutableStateOf<AppAnnouncement?>(null)
        private set
    var announcementDialogVisible by mutableStateOf(false)
        private set

    // ── 消息通知（追番更新 / 评论回复）──
    var notifications by mutableStateOf<List<CloudNotification>>(emptyList())
        private set
    val unreadNotificationCount: Int
        get() = notifications.count { !it.read }

    fun openNotificationsScreen() {
        view = "notifications"
        viewModelScope.launch {
            val fresh = notificationRepository.load()
            if (fresh != null) notifications = fresh
        }
    }

    fun closeNotificationsScreen() {
        if (notifications.any { !it.read }) {
            viewModelScope.launch {
                notificationRepository.markAllRead()
                notifications = notifications.map { it.copy(read = true) }
            }
        }
    }

    private fun loadNotifications() {
        viewModelScope.launch {
            val fresh = runCatching { notificationRepository.load() }.getOrNull() ?: return@launch
            notifications = fresh
        }
    }

    // 追番更新检测：收藏且未完结的作品出现「超出已看集数」的新剧集时上报提醒
    private fun checkFavoriteUpdate(item: SourceItem, detail: DetailResult) {
        if (TEMP_ACCOUNT_AUTH_DISABLED || accountUser == null) return
        val state = resolveMediaStatus(item).state
        if (state != MediaReleaseState.SERIALIZING && state != MediaReleaseState.UPDATING) return
        if (!storageManager.isFavorite(item)) return
        val latest = detail.episodes.lastOrNull()?.name?.trim().orEmpty()
        if (latest.isEmpty()) return
        val latestNum = episodeNumber(latest).toIntOrNull() ?: return
        val mediaKey = commentMediaKey(item)
        // 以收藏时的集数为基线：无基线的旧收藏先静默记下当前集数，之后每更新一集提醒一次
        val baseline = storageManager.getFavoriteBaseline(mediaKey) ?: run {
            storageManager.setFavoriteBaseline(mediaKey, latestNum)
            return
        }
        if (latestNum <= baseline) return
        storageManager.setFavoriteBaseline(mediaKey, latestNum)
        if (storageManager.getAuthToken().isBlank()) return
        viewModelScope.launch {
            notificationRepository.reportFavoriteUpdate(
                title = "追番更新提醒",
                body = "你收藏的「${item.title}」更新了（$latest），请您追番哦。",
                mediaKey = mediaKey,
                episodeName = latest,
                mediaSnapshot = gson.toJson(item)
            )
            val fresh = notificationRepository.load()
            if (fresh != null) notifications = fresh
        }
    }

    // 应用启动时后台检测最近收藏的未完结番剧是否有更新
    private fun backgroundFavoriteUpdateCheck() {
        if (TEMP_ACCOUNT_AUTH_DISABLED || accountUser == null) return
        val candidates = favoritesList
            .filter {
                val state = resolveMediaStatus(it).state
                state == MediaReleaseState.SERIALIZING || state == MediaReleaseState.UPDATING
            }
            .take(5)
        if (candidates.isEmpty()) return
        viewModelScope.launch {
            candidates.forEach { item ->
                runCatching {
                    val primarySourceKey = item.sourceKey
                        .split(',')
                        .asSequence()
                        .map { it.trim() }
                        .firstOrNull { it.isNotEmpty() && sourceManager.getAdapter(it) != null }
                        ?: item.sourceKey.trim()
                    val adapter = sourceManager.getAdapter(primarySourceKey)
                    val cached = ResultCache.getDetail("$primarySourceKey:${item.id}")
                    val detail = cached?.let { mergeDetailMetadata(item, it) } ?: run {
                        if (adapter == null) return@runCatching
                        val fresh = withContext(Dispatchers.IO) {
                            fetchUsableDetail(adapter, item)
                        } ?: return@runCatching
                        val merged = mergeDetailMetadata(item, fresh)
                        if (merged.episodes.isNotEmpty()) ResultCache.putDetail("$primarySourceKey:${item.id}", merged)
                        merged
                    }
                    checkFavoriteUpdate(item, detail)
                }
                delay(600L)
            }
        }
    }

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

    fun updateUserAvatar(index: Int) {
        userAvatarIndex = index
        storageManager.setUserAvatar(index)
    }

    private fun loadAnnouncement() {
        viewModelScope.launch {
            val loaded = runCatching { announcementRepository.load() }.getOrNull() ?: return@launch
            announcement = loaded
            announcementDialogVisible = storageManager.announcementDismissedUntil(loaded.id) <=
                System.currentTimeMillis()
        }
    }

    fun openAnnouncement() {
        if (announcement != null) announcementDialogVisible = true
    }

    fun dismissAnnouncement(days: Int = 0) {
        val current = announcement ?: return
        val now = System.currentTimeMillis()
        val until = if (days <= 0) {
            Calendar.getInstance(java.util.TimeZone.getTimeZone("Asia/Shanghai")).apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        } else now + days * 24L * 60L * 60L * 1000L
        storageManager.dismissAnnouncementUntil(current.id, until)
        announcementDialogVisible = false
    }

    fun closeAnnouncement() {
        announcementDialogVisible = false
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
                    storageManager.setCommentNick(result.user.nickname)
                    commentNick = result.user.nickname
                    updateUserEmail(result.user.email)
                    accountUser = result.user
                    accountMessage = "登录成功，正在同步本机数据"
                    // 同步失败不影响登录；下次启动会再次拉取
                    runCatching {
                        val remote = accountRepository.pull(result.token)
                        storageManager.mergeCloudData(remote.favorites, remote.history)
                        reloadStorageData()
                        accountRepository.sync(result.token, favoritesList, historyList, getDownloadedFilesList())
                    }
                    loadNotifications()
                    backgroundFavoriteUpdateCheck()
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
                val token = if (purpose == "change-email") storageManager.getAuthToken() else ""
                accountRepository.requestCode(email, purpose, token)
                accountMessage = "验证码已发送，请检查邮箱"
                accountCodeCooldownJob?.cancel()
                accountCodeCooldownJob = viewModelScope.launch {
                    accountCodeCooldownSeconds = 60
                    while (accountCodeCooldownSeconds > 0) {
                        delay(1_000L)
                        accountCodeCooldownSeconds -= 1
                    }
                }
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
                    storageManager.setCommentNick(result.user.nickname)
                    commentNick = result.user.nickname
                    updateUserEmail(result.user.email)
                    accountUser = result.user
                    accountMessage = "注册成功，已同步本机数据"
                    val remote = accountRepository.pull(result.token)
                    storageManager.mergeCloudData(remote.favorites, remote.history)
                    reloadStorageData()
                    accountRepository.sync(result.token, favoritesList, historyList, getDownloadedFilesList())
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

    fun changeAccountPassword(oldPassword: String, newPassword: String, confirmPassword: String) {
        val token = storageManager.getAuthToken()
        if (token.isBlank() || accountBusy) return
        viewModelScope.launch {
            accountBusy = true
            accountMessage = ""
            try {
                accountRepository.changePassword(token, oldPassword, newPassword, confirmPassword)
                accountMessage = "密码修改成功"
            } catch (error: Exception) {
                accountMessage = error.message ?: "密码修改失败"
            } finally {
                accountBusy = false
            }
        }
    }

    fun submitFeedback(category: String, text: String, onSuccess: () -> Unit = {}) {
        val token = storageManager.getAuthToken()
        if (token.isBlank()) {
            accountMessage = "请先登录后提交反馈"
            return
        }
        if (accountBusy) return
        viewModelScope.launch {
            accountBusy = true
            accountMessage = ""
            try {
                accountRepository.submitFeedback(token, category, text)
                accountMessage = "反馈已提交，感谢你的建议"
                onSuccess()
            } catch (error: Exception) {
                accountMessage = error.message ?: "反馈提交失败"
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

    fun uploadCommentImage(uri: Uri) {
        val token = storageManager.getAuthToken()
        if (token.isBlank()) {
            accountMessage = "请先“登录”后发表评论"
            accountDialogVisible = true
            return
        }
        if (commentImageUploading) return
        viewModelScope.launch {
            commentImageUploading = true
            val file = compressCommentImage(getApplication(), uri)
            if (file == null) {
                accountMessage = "无法读取图片"
                android.widget.Toast.makeText(getApplication(), "无法读取图片，请重试", android.widget.Toast.LENGTH_SHORT).show()
                commentImageUploading = false
                return@launch
            }
            try {
                val result = commentRepository.uploadImage(file)
                if (result.url != null) {
                    commentImageUrl = result.url
                } else {
                    accountMessage = result.error ?: "图片上传失败"
                    android.widget.Toast.makeText(
                        getApplication(),
                        result.error ?: "图片上传失败",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                file.delete()
                commentImageUploading = false
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
                        commentNick = result.user.nickname
                        storageManager.setCommentNick(result.user.nickname)
                        runCatching {
                            val remote = accountRepository.pull(token)
                            storageManager.mergeCloudData(remote.favorites, remote.history)
                            reloadStorageData()
                            accountRepository.sync(token, favoritesList, historyList, getDownloadedFilesList())
                        }
                        loadNotifications()
                        backgroundFavoriteUpdateCheck()
                    } else {
                        storageManager.clearAuthToken()
                    }
                }
                // 仅当接口明确返回无效时才登出；网络抖动不清除登录态
                .onFailure { /* 网络异常时保留登录态，下次启动重试 */ }
        }
    }

    fun allPoolItems(): List<SourceItem> {
        return (cachedHomePool + homeSections.flatMap { it.items } + libraryItems)
            .distinctBy { "${it.sourceKey}:${it.id}:${SourceManager.normalizeTitle(it.title)}" }
    }

    fun remoteDiscoveryPool(): List<SourceItem> =
        (
            lanercSeasons.flatMap { it.entries }.map { it.item } +
                animeCategoryRankings.values.flatten().map { it.item } +
                lanercRankings.values.flatten().map { it.item } +
                lanercSchedule.map { it.item }
            )
            .distinctBy { SourceManager.normalizeTitle(it.title) }

    fun refreshLanercDiscovery(force: Boolean = true) {
        if (lanercDiscoveryLoading) {
            if (force) lanercDiscoveryMessage = "正在刷新，请稍候…"
            return
        }
        viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            val refreshTarget = when (view) {
                "schedule" -> "本周更新"
                "leaderboard" -> "分类排行榜"
                "seasonal_schedule" -> "季度排期"
                "seasonal_ranking" -> "季度新番榜"
                else -> "榜单与排期"
            }
            lanercDiscoveryLoading = true
            if (force) lanercDiscoveryMessage = "正在刷新$refreshTarget…"
            val result = runCatching { lanercDiscoveryRepository.load(force) }
            result.onSuccess { snapshot ->
                lanercRankings = snapshot.rankings
                animeCategoryRankings = AnimeRankingCategory.entries.associateWith { category ->
                    (
                        snapshot.categoryRankings[category].orEmpty() +
                            animeCategoryRankings[category].orEmpty().filterNot {
                                it.item.sourceKey.equals("lanerc", ignoreCase = true)
                            }
                        ).distinctBy { normalizeDiscoveryTitle(it.item.title) }
                }
                lanercSchedule = snapshot.schedule
                lanercSeasons = snapshot.seasons
                // lanerc 只给季度新番提供评分，其余条目（如新开播的当前季新番）
                // 异步用 bgm.tv 补全，不阻塞刷新提示
                viewModelScope.launch {
                    val enriched = enrichSeasonScores(snapshot.seasons)
                    if (enriched != snapshot.seasons) lanercSeasons = enriched
                }
                lanercDiscoveryUpdatedAt = snapshot.fetchedAt
                lanercDiscoveryError = null
                if (force) {
                    lanercDiscoveryMessage = when (view) {
                        "seasonal_schedule", "seasonal_ranking" -> {
                            val seasonLabels = snapshot.seasons.joinToString("、") { it.season.label }
                            "刷新成功 · ${seasonLabels.ifBlank { refreshTarget }}"
                        }
                        else -> "刷新成功 · $refreshTarget"
                    }
                }
            }.onFailure { error ->
                lanercDiscoveryError = error.message ?: "榜单/周表加载失败"
                if (force) lanercDiscoveryMessage = "刷新失败，请稍后重试"
            }
            val remainingSpinnerMs = 500L - (System.currentTimeMillis() - startedAt)
            if (remainingSpinnerMs > 0L) delay(remainingSpinnerMs)
            lanercDiscoveryLoading = false
            if (force) {
                val displayedMessage = lanercDiscoveryMessage
                delay(2_000L)
                if (lanercDiscoveryMessage == displayedMessage) {
                    lanercDiscoveryMessage = ""
                }
            }
        }
    }

    fun loadAnimeCategoryRanking(
        category: AnimeRankingCategory,
        force: Boolean = false
    ) {
        if (!force && category in animeCategorySearchCompleted) return
        if (force) animeCategorySearchCompleted.remove(category)
        animeCategoryRankingJobs.remove(category)?.cancel()
        animeCategoryRankingJobs[category] = viewModelScope.launch {
            animeCategoryRankingLoading = animeCategoryRankingLoading + category
            val discoverySnapshot = if (force) {
                runCatching { lanercDiscoveryRepository.load(true) }.getOrNull()
            } else {
                null
            }
            val baseline = discoverySnapshot?.categoryRankings?.get(category)
                ?: animeCategoryRankings[category].orEmpty()
            val keywords = when (category) {
                AnimeRankingCategory.JAPANESE_TV -> listOf("日本新番", "日漫")
                AnimeRankingCategory.CHINESE_TV -> listOf("国漫", "国产动画")
            }
            val sourceEnriched = withContext(Dispatchers.IO) {
                coroutineScope {
                    sourceManager.allAdapters()
                        .filterNot { it.key.equals("lanerc", ignoreCase = true) }
                        .map { adapter ->
                        async {
                            keywords.flatMap { keyword ->
                                runCatching { withTimeout(8_000L) { adapter.search(keyword, 1) } }
                                    .getOrNull()
                                    .orEmpty()
                                    .take(10)
                                    .mapIndexed { index, item ->
                                        Triple(adapter, keyword, SourceRankingEntry(
                                            item = item,
                                            sourceSection = "搜索:$keyword · ${adapter.title}",
                                            sourcePosition = index + 1
                                        ))
                                    }
                            }
                        }
                    }.awaitAll().flatten()
                }
            }.let { rawEntries ->
                coroutineScope {
                    rawEntries
                        .groupBy { it.first.key }
                        .values
                        .map { adapterEntries ->
                            async(Dispatchers.IO) {
                            // A single QuickJS adapter must not execute detail()
                            // concurrently. Enrich only its leading exact-search
                            // candidates, sequentially; adapters still run in parallel.
                            adapterEntries
                                .distinctBy { normalizeDiscoveryTitle(it.third.item.title) }
                                .take(4)
                                .map enrich@ { (adapter, _, entry) ->
                                    val needsDetail = entry.item.score.toDoubleOrNull()?.let { it <= 0.0 } != false ||
                                        !matchesAnimeRankingCategory(entry.item, entry.sourceSection, category)
                                    if (!needsDetail) return@enrich entry
                                    val detailItem = runCatching {
                                        withTimeout(8_000L) { adapter.detail(entry.item.id).item }
                                    }.getOrNull()
                                    if (detailItem == null || detailItem.title.isBlank()) entry
                                    else entry.copy(item = mergeRankingMetadata(entry.item, detailItem))
                                }
                            }
                        }
                        .awaitAll()
                        .flatten()
                }
            }
            // Video sources often omit score/region/platform. Enrich a bounded
            // set of leading cross-source candidates with exact-title anime
            // metadata; this never participates in playback resolution.
            val metadataByTitle = withContext(Dispatchers.IO) {
                coroutineScope {
                    (sourceEnriched + baseline)
                        .groupBy { normalizeDiscoveryTitle(it.item.title) }
                        .entries
                        .sortedWith(
                            compareByDescending<Map.Entry<String, List<SourceRankingEntry>>> { it.value.size }
                                .thenBy { group -> group.value.minOfOrNull { it.sourcePosition } ?: Int.MAX_VALUE }
                        )
                        .take(18)
                        .map { group ->
                            async {
                                group.key to runCatching {
                                    withTimeout(7_000L) {
                                        animeMetadataRepository.lookup(group.value.first().item.title)
                                    }
                                }.getOrNull()
                            }
                        }
                        .awaitAll()
                        .mapNotNull { (titleKey, metadata) -> metadata?.let { titleKey to it } }
                        .toMap()
                }
            }
            val fetched = sourceEnriched
                .map { entry ->
                    val metadata = metadataByTitle[normalizeDiscoveryTitle(entry.item.title)]
                    if (metadata == null) entry
                    else entry.copy(item = mergeAnimeMetadata(entry.item, metadata))
                }
                .filter { matchesAnimeRankingCategory(it.item, it.sourceSection, category) }
                .distinctBy { normalizeDiscoveryTitle(it.item.title) }
            // lanerc 分类接口不带评分，baseline 排在前面的热门作品单独查 bgm.tv 补全
            val baselineScoresByTitle = withContext(Dispatchers.IO) {
                coroutineScope {
                    baseline.filter { it.item.score.isBlank() }
                        .take(24)
                        .map { entry ->
                            async {
                                entry.item.title to runCatching {
                                    animeMetadataRepository.lookup(entry.item.title)
                                }.getOrNull()
                            }
                        }
                        .awaitAll()
                        .mapNotNull { (title, metadata) ->
                            metadata?.takeIf { it.score.isNotBlank() }?.let {
                                normalizeDiscoveryTitle(title) to it
                            }
                        }
                        .toMap()
                }
            }
            val realScoresByTitle = (baseline + fetched)
                .mapNotNull { entry ->
                    entry.item.score.toDoubleOrNull()?.takeIf { it > 0.0 }?.let {
                        normalizeDiscoveryTitle(entry.item.title) to entry.item.score
                    }
                }
                .toMap()
            val finalEntries = (baseline + fetched)
                .distinctBy { normalizeDiscoveryTitle(it.item.title) }
                .map { entry ->
                    val titleKey = normalizeDiscoveryTitle(entry.item.title)
                    // lanerc filter 接口无评分，用 bgm.tv 元数据补全
                    val withMetadata = (metadataByTitle[titleKey] ?: baselineScoresByTitle[titleKey])?.let { metadata ->
                        if (entry.item.score.isBlank()) {
                            entry.copy(item = mergeAnimeMetadata(entry.item, metadata))
                        } else entry
                    } ?: entry
                    val score = realScoresByTitle[titleKey].orEmpty()
                    if (withMetadata.item.score.isBlank() && score.isNotBlank()) {
                        withMetadata.copy(item = withMetadata.item.copy(score = score))
                    } else withMetadata
                }
            discoverySnapshot?.let { snapshot ->
                lanercRankings = snapshot.rankings
                lanercSchedule = snapshot.schedule
                lanercSeasons = snapshot.seasons
                lanercDiscoveryUpdatedAt = snapshot.fetchedAt
                lanercDiscoveryError = null
            }
            animeCategoryRankings = animeCategoryRankings + (
                category to finalEntries
            )
            animeCategorySearchCompleted += category
            animeCategoryRankingLoading = animeCategoryRankingLoading - category
            animeCategoryRankingJobs.remove(category)
        }
    }

    /** 为无评分的季度新番条目异步补全 bgm.tv 评分（每季限量，避免拖慢刷新） */
    private suspend fun enrichSeasonScores(seasons: List<SeasonalRecommendation>): List<SeasonalRecommendation> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                seasons.map { season ->
                    async {
                        season.copy(
                            entries = season.entries.mapIndexed { index, entry ->
                                if (entry.item.score.isNotBlank() || index >= 25) {
                                    entry
                                } else {
                                    val metadata = runCatching {
                                        animeMetadataRepository.lookup(entry.item.title)
                                    }.getOrNull()
                                    if (metadata != null && metadata.score.isNotBlank()) {
                                        entry.copy(item = entry.item.copy(score = metadata.score))
                                    } else entry
                                }
                            }
                        )
                    }
                }.awaitAll()
            }
        }

    private fun mergeRankingMetadata(summary: SourceItem, detail: SourceItem): SourceItem = summary.copy(
        title = detail.title.ifBlank { summary.title },
        year = detail.year.ifBlank { summary.year },
        kind = detail.kind.ifBlank { summary.kind },
        tags = (summary.tags + detail.tags).distinct(),
        status = detail.status.ifBlank { summary.status },
        score = detail.score.takeIf { it.toDoubleOrNull()?.let { value -> value > 0.0 } == true }
            ?: summary.score,
        cover = detail.cover.ifBlank { summary.cover },
        description = detail.description.ifBlank { summary.description }
    )

    private fun mergeAnimeMetadata(item: SourceItem, metadata: AnimeMetadata): SourceItem = item.copy(
        year = item.year.ifBlank { metadata.year },
        kind = listOf(item.kind, metadata.platform).filter(String::isNotBlank).joinToString(" "),
        tags = (item.tags + metadata.tags).distinct(),
        score = item.score.takeIf { it.toDoubleOrNull()?.let { value -> value > 0.0 } == true }
            ?: metadata.score
    )

    fun commentMediaKey(item: SourceItem): String =
        "${item.sourceKey.substringBefore(',').trim()}:${item.id}"

    // 打开详情/播放页时按作品加载云端评论；接口失败保持空列表，不影响播放链路。
    // force=true 时即使同一部番剧再次进入也会重新拉取（避免上次加载失败/为空时列表一直空白）
    fun loadCommentsForActiveDetail(force: Boolean = false) {
        val detail = displayedDetail ?: activeDetail ?: return
        val key = commentMediaKey(detail.item)
        if (!force && key == commentsLoadedFor) return
        commentsLoadedFor = key
        if (force && comments.isNotEmpty()) {
            // 已有数据时先保留展示，后台刷新成功后替换
        } else {
            comments = emptyList()
        }
        viewModelScope.launch {
            val remote = commentRepository.load(key)
            if (remote != null && commentsLoadedFor == key) {
                comments = remote
            }
        }
    }

    var replyTargetComment by mutableStateOf<CloudComment?>(null)

    fun addComment(parentId: String? = null, replyToNick: String? = null) {
        if (TEMP_COMMENT_POSTING_DISABLED) {
            accountMessage = "评论发送暂时关闭，仅展示已有评论"
            return
        }
        if (accountUser == null || storageManager.getAuthToken().isBlank()) {
            accountMessage = "请先“登录”后发表评论"
            accountDialogVisible = true
            return
        }
        val text = commentDraft.trim()
        if ((text.isEmpty() && commentImageUrl.isEmpty()) || commentPosting) return
        val detail = displayedDetail ?: activeDetail ?: return
        val key = commentMediaKey(detail.item)
        val userNick = accountUser?.nickname?.ifBlank { accountUser?.email?.substringBefore('@') }
        val nick = userNick?.ifBlank { null } ?: commentNick.ifBlank { storageManager.getCommentNick() }
        val avatarUrl = accountUser?.avatarUrl.orEmpty()
        val targetParentId = parentId ?: replyTargetComment?.let { it.parentId ?: it.id }
        val targetReplyNick = replyToNick ?: replyTargetComment?.nick

        viewModelScope.launch {
            commentPosting = true
            val result = commentRepository.post(key, nick, text, avatarUrl, targetParentId, targetReplyNick, commentImageUrl)
            val remote = result.comments
            if (remote != null) {
                commentDraft = ""
                commentImageUrl = ""
                replyTargetComment = null
                commentsLoadedFor = key
                comments = remote
                android.widget.Toast.makeText(getApplication(), "评论发布成功", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                android.widget.Toast.makeText(
                    getApplication(),
                    result.error ?: "评论发布失败，请稍后重试",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            commentPosting = false
        }
    }

    fun deleteComment(comment: CloudComment) {
        val detail = displayedDetail ?: activeDetail ?: return
        val key = commentMediaKey(detail.item)
        viewModelScope.launch {
            val ok = commentRepository.delete(key, comment.id)
            if (ok) {
                val fresh = commentRepository.load(key)
                if (fresh != null) comments = fresh
                android.widget.Toast.makeText(getApplication(), "已删除评论", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                val updated = comments.filterNot { it.id == comment.id }.map { parent ->
                    parent.copy(replies = parent.replies.filterNot { it.id == comment.id })
                }
                comments = updated
                android.widget.Toast.makeText(getApplication(), "已删除评论", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun likeComment(comment: CloudComment) {
        val detail = displayedDetail ?: activeDetail ?: return
        val key = commentMediaKey(detail.item)
        viewModelScope.launch {
            comments = comments.map { parent ->
                if (parent.id == comment.id) {
                    val newLiked = !parent.likedByMe
                    val newCount = if (newLiked) parent.likesCount + 1 else (parent.likesCount - 1).coerceAtLeast(0)
                    parent.copy(likedByMe = newLiked, likesCount = newCount)
                } else {
                    val updatedReplies = parent.replies.map { reply ->
                        if (reply.id == comment.id) {
                            val newLiked = !reply.likedByMe
                            val newCount = if (newLiked) reply.likesCount + 1 else (reply.likesCount - 1).coerceAtLeast(0)
                            reply.copy(likedByMe = newLiked, likesCount = newCount)
                        } else reply
                    }
                    parent.copy(replies = updatedReplies)
                }
            }
            commentRepository.like(key, comment.id)
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
        // Independent metadata fetch. It never gates source initialization,
        // detail parsing, play URL resolution, or player startup.
        refreshLanercDiscovery(force = false)
        loadAnnouncement()
        // 正常构建会恢复登录态并拉取用户云端数据。
        if (!TEMP_ACCOUNT_AUTH_DISABLED) {
            restoreAccount()
        }
        viewModelScope.launch {
            withContext(Dispatchers.Main) { notice = "正在加载视频源..." }
            withContext(Dispatchers.IO) { sourceManager.init() }
            loadHomeInternal()
            viewModelScope.launch(Dispatchers.IO) {
                val changed = kotlinx.coroutines.withTimeoutOrNull(30_000L) {
                    RemoteSourceFetcher.syncAll(getApplication<Application>())
                } ?: 0
                if (changed > 0) {
                    withContext(Dispatchers.Main) { homeLoadJob?.cancel() }
                    sourceManager.init()
                    withContext(Dispatchers.Main) { loadHome() }
                }
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
            runCatching {
                accountRepository.sync(token, favoritesList, historyList, getDownloadedFilesList())
            }
        }
    }

    fun loadHome() {
        homeLoadJob?.cancel()
        homeLoadJob = viewModelScope.launch {
            loadHomeInternal()
        }
    }

    private suspend fun loadHomeInternal() {
        val collectedSourceSections = mutableListOf<HomeSection>()
        val adapters = sourceManager.allAdapters()
        val sourceOrder = adapters.mapIndexed { index, adapter -> adapter.key to index }.toMap()
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
                val deduped = list
                    .distinctBy { SourceManager.normalizeTitle(it.title) }
                    .withIndex()
                    .sortedWith(
                        compareBy<IndexedValue<SourceItem>> {
                            sourceOrder[it.value.sourceKey] ?: Int.MAX_VALUE
                        }.thenBy { it.index }
                    )
                    .map { it.value }
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

        withContext(Dispatchers.Main) {
            loading = true
            notice = "正在并发动态加载多源视频..."
            if (cachedHomePool.isNotEmpty()) {
            cachedHomePool.forEach { item ->
                    val kind = item.kind + " " + item.title
                    val targetKey = when {
                        kind.contains("日漫") || kind.contains("日本") -> "日漫精选"
                        kind.contains("国漫") || kind.contains("国产") -> "国漫精粹"
                        kind.contains("剧场") || kind.contains("电影") -> "剧场版/电影"
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
                        if (jsSections.isNotEmpty()) {
                            synchronized(collectedSourceSections) {
                                collectedSourceSections += jsSections
                            }
                        }

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
                                val sourcedItems = if (jsSections.isNotEmpty()) {
                                    jsSections.flatMap { section ->
                                        section.items.map { item -> section.title to item }
                                    }
                                } else {
                                    fetchedItems.map { "" to it }
                                }
                                sourcedItems.forEach { (sectionTitle, item) ->
                                    val kind = item.kind + " " + item.title + " " + item.tags.joinToString(" ")
                                    val targetKey = when {
                                        listOf("热门", "热播", "排行", "人气").any { sectionTitle.contains(it) } -> "热门推荐"
                                        listOf("最新", "更新", "新番", "上新").any { sectionTitle.contains(it) } -> "最新更新"
                                        listOf("剧场", "电影").any { sectionTitle.contains(it) } -> "剧场版/电影"
                                        listOf("国漫", "国产").any { sectionTitle.contains(it) } -> "国漫精粹"
                                        listOf("日漫", "日本", "TV").any { sectionTitle.contains(it, ignoreCase = true) } -> "日漫精选"
                                        kind.contains("日漫") || kind.contains("日本") -> "日漫精选"
                                        kind.contains("国漫") || kind.contains("国产") -> "国漫精粹"
                                        kind.contains("剧场") || kind.contains("电影") -> "剧场版/电影"
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
                                    discoverySections = synchronized(collectedSourceSections) {
                                        collectedSourceSections
                                            .distinctBy { "${it.sourceKey}:${it.title}:${it.key}" }
                                            .toList()
                                    }
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

        withContext(Dispatchers.Main) {
            discoverySections = synchronized(collectedSourceSections) {
                collectedSourceSections
                    .distinctBy { "${it.sourceKey}:${it.title}:${it.key}" }
                    .toList()
            }
        }

        // If every source omits home sections, show a clearly labelled content
        // fallback. It must not be advertised as a measured popularity list.
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
                    val section = HomeSection(title = "内容推荐", key = "fallback", items = merged)
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
        storageManager.addSearchHistory(q)
        searchHistoryEntries = storageManager.getSearchHistoryEntries()
        view = "search_result"
        search(q)
    }

    fun openSearch() {
        query = ""
        searchHistoryEntries = storageManager.getSearchHistoryEntries()
        view = "search"
    }

    fun removeSearchHistory(query: String) {
        storageManager.removeSearchHistory(query)
        searchHistoryEntries = storageManager.getSearchHistoryEntries()
    }

    fun clearSearchHistory() {
        storageManager.clearSearchHistory()
        searchHistoryEntries = emptyList()
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
    var libraryLoadError by mutableStateOf<String?>(null)
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

        if (activeStatus != "全部") {
            filtered = filtered.filter { item ->
                when (resolveMediaStatus(item).state) {
                    MediaReleaseState.FINISHED -> activeStatus == "已完结"
                    MediaReleaseState.SERIALIZING, MediaReleaseState.UPDATING -> activeStatus == "连载中"
                    else -> false
                }
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
        // Pagination can be requested by several visible grid items in the
        // same frame. Never cancel an active page request merely because a
        // duplicate load-more signal arrived; that used to leave
        // libraryLoadingMore=true forever after the cancelled job exited.
        if (!reset && (libraryLoadingMore || !libraryHasMore || libraryJob?.isActive == true)) {
            return
        }
        if (reset) libraryJob?.cancel()
        val requestId = ++libraryGeneration
        libraryJob = viewModelScope.launch {
            try {
            if (reset) {
                libraryPage = 1
                val cached = applyLibraryFiltersFast(cachedHomePool)
                libraryItems = cached
                libraryHasMore = true
                libraryLoadError = null
                withContext(Dispatchers.Main) {
                    items = cached
                    totalLibrary = cached.size
                    loading = cached.isEmpty()
                    libraryLoadingMore = false
                    notice = "正在检索多源片库..."
                }
            } else {
                if (libraryLoadingMore || !libraryHasMore) return@launch
                withContext(Dispatchers.Main) {
                    libraryLoadingMore = true
                    libraryLoadError = null
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
            val nonEmptySources = AtomicInteger(0)

            withContext(Dispatchers.IO) {
                coroutineScope {
                    targetAdapters.forEach { adapter ->
                        launch {
                            val rawItems = try {
                                withTimeout(2_500L) {
                                    // SourceExports blocks on Future.get().
                                    // runInterruptible makes the 2.5s coroutine
                                    // timeout actually interrupt that wait.
                                    runInterruptible {
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
                                }
                            } catch (_: Exception) { emptyList() }
                            if (rawItems.isNotEmpty()) nonEmptySources.incrementAndGet()

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
                    when (resolveMediaStatus(item).state) {
                        MediaReleaseState.FINISHED -> activeStatus == "已完结"
                        MediaReleaseState.SERIALIZING, MediaReleaseState.UPDATING -> activeStatus == "连载中"
                        else -> false
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
                when {
                    filtered.isNotEmpty() -> filtered
                    dedupedBatch.isNotEmpty() -> dedupedBatch
                    else -> currentExisting
                }
            } else {
                currentExisting + trulyNewItems
            }

            withContext(Dispatchers.Main) {
                libraryItems = finalUpdatedList
                items = finalUpdatedList
                totalLibrary = finalUpdatedList.size
                loading = false
                libraryLoadingMore = false

                if (fetchedNewItems.isEmpty()) {
                    libraryLoadError =
                        "本次 ${targetAdapters.size} 个视频源均未返回内容，请检查网络后重试"
                    libraryHasMore = true
                    notice = libraryLoadError.orEmpty()
                } else if (!reset && trulyNewItems.isEmpty()) {
                    libraryLoadError = null
                    libraryHasMore = false
                    notice = "已为你展示全网多源片库全部作品 (共 ${finalUpdatedList.size} 部)"
                } else {
                    libraryLoadError = null
                    libraryPage = currentPage + 1
                    notice =
                        "已检索到 ${finalUpdatedList.size} 部作品 (${nonEmptySources.get()}/${targetAdapters.size} 源返回内容)"
                }
            }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                withContext(Dispatchers.Main) {
                    if (requestId == libraryGeneration) {
                        libraryLoadError = "片库加载失败：${error.message ?: "未知错误"}"
                        notice = libraryLoadError.orEmpty()
                    }
                }
            } finally {
                withContext(Dispatchers.Main) {
                    if (requestId == libraryGeneration) {
                        loading = false
                        libraryLoadingMore = false
                    }
                }
            }
        }
    }

    fun loadNextPage() {
        fetchLibrary(reset = false)
    }

    private fun mergeDetailMetadata(summary: SourceItem, detail: DetailResult): DetailResult {
        val detailed = detail.item
        return detail.copy(
            item = detailed.copy(
                title = detailed.title.ifBlank { summary.title },
                year = detailed.year.ifBlank { summary.year },
                kind = detailed.kind.ifBlank { summary.kind },
                tags = (detailed.tags + summary.tags).filter { it.isNotBlank() }.distinct(),
                status = detailed.status.ifBlank { summary.status },
                score = detailed.score.ifBlank { summary.score },
                cover = detailed.cover.ifBlank { summary.cover },
                description = detailed.description.ifBlank { summary.description },
                sourceKey = detailed.sourceKey.ifBlank { summary.sourceKey },
                sourceTitle = detailed.sourceTitle.ifBlank { summary.sourceTitle },
                sourceCount = maxOf(detailed.sourceCount, summary.sourceCount)
            )
        )
    }

    private fun findMatchingSourceItem(
        adapter: SourceAdapter,
        title: String,
        initialResults: List<SourceItem>? = null
    ): SourceItem? {
        val aliases = SourceManager.detailSearchVariants(title)
        aliases.forEachIndexed { index, alias ->
            val results = if (index == 0 && initialResults != null) {
                initialResults
            } else {
                runCatching { adapter.search(alias, 1) }.getOrDefault(emptyList())
            }
            results.firstOrNull {
                SourceManager.normalizeTitle(it.title) == SourceManager.normalizeTitle(title)
            }?.let { return it }
            results.firstOrNull {
                SourceManager.titlesLikelyMatch(it.title, title)
            }?.let { return it }
        }
        return null
    }

    /**
     * A merged/home/search card can carry a stale source-local id even though
     * its title is valid. Try the supplied id first, then recover the exact
     * title inside the same source before declaring that the source has no
     * episodes.
     */
    private fun fetchUsableDetail(adapter: SourceAdapter, summary: SourceItem): DetailResult {
        val direct = runCatching {
            mergeDetailMetadata(summary, adapter.detail(summary.id))
        }.getOrNull()
        if (direct != null && direct.episodes.isNotEmpty()) return direct

        val recovered = runCatching {
            findMatchingSourceItem(adapter, summary.title)
                ?.let { match ->
                    mergeDetailMetadata(match, adapter.detail(match.id))
                }
        }.getOrNull()
        return recovered?.takeIf { it.episodes.isNotEmpty() }
            ?: direct
            ?: DetailResult(summary, emptyList())
    }

    fun openMovie(item: SourceItem, preferredEpisodeName: String? = null) {
        detailLoadJob?.cancel()
        val detailGeneration = ++detailLoadGeneration
        playResolveJob?.cancel()
        playResolveJob = null
        playResolveGeneration++
        if (view == "player") {
            if (playerNavigationStack.size >= 20) {
                playerNavigationStack.removeFirst()
            }
            playerNavigationStack.addLast(
                PlayerNavigationState(
                    activeDetail = activeDetail,
                    currentEpisodeIndex = currentEpisodeIndex,
                    currentPlayResult = currentPlayResult,
                    isPlayLoading = isPlayLoading,
                    playError = playError,
                    relatedItems = relatedItems,
                    alternativeDetails = alternativeDetails,
                    activeAlternativeIndex = activeAlternativeIndex,
                    notice = notice
                )
            )
        } else {
            playerNavigationStack.clear()
            playerReturnView = view
        }
        pendingEpisodeName = preferredEpisodeName
        activeDetail = DetailResult(item, emptyList())
        currentPlayResult = null
        isPlayLoading = true
        playError = null
        relatedItems = emptyList()
        view = "player"

        detailLoadJob = viewModelScope.launch {
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
                if (detailGeneration != detailLoadGeneration) return@withContext
                loading = true
                alternativeDetails = emptyList()
                notice = "正在解析「${playableItem.title}」剧集列表..."
            }
            if (detailGeneration != detailLoadGeneration) return@launch
            val adapter = sourceManager.getAdapter(primarySourceKey)

            // Detail cache-first (30 min TTL) — cached hit shows episodes in <5ms
            val detailCacheKey = "$primarySourceKey:${playableItem.id}"
            val cachedDetail = ResultCache.getDetail(detailCacheKey)
            val detailResult = cachedDetail
                ?.let { mergeDetailMetadata(playableItem, it) }
                ?: run {
                    val fresh = withContext(Dispatchers.IO) {
                        if (adapter != null) {
                            fetchUsableDetail(adapter, playableItem)
                        } else null
                    } ?: DetailResult(playableItem, emptyList())
                    val merged = mergeDetailMetadata(playableItem, fresh)
                    if (merged.episodes.isNotEmpty()) ResultCache.putDetail(detailCacheKey, merged)
                    merged
                }

            // Fallback for expired/broken source URL from history or favorites
            var finalDetailResult = detailResult
            if (finalDetailResult.episodes.isEmpty()) {
                val fallback = withContext(Dispatchers.IO) {
                    coroutineScope {
                        sourceManager.allAdapters()
                            .filter { it.key != primarySourceKey }
                            .map { alt ->
                                async {
                                    withTimeoutOrNull(12_000L) {
                                        runCatching {
                                            val match = findMatchingSourceItem(alt, playableItem.title)
                                            match?.let { fetchUsableDetail(alt, it) }
                                                ?.takeIf { it.episodes.isNotEmpty() }
                                        }.getOrNull()
                                    }
                                }
                            }
                            .awaitAll()
                            .firstOrNull { it != null }
                    }
                }
                if (fallback != null) {
                    finalDetailResult = fallback
                }
            }
            if (detailGeneration != detailLoadGeneration) return@launch

            // 收藏且未完结的番剧加载出剧集列表后，检测是否有未看过的新集并上报追番更新提醒
            checkFavoriteUpdate(playableItem, finalDetailResult)

            // Keep the fast cache-first render, then revalidate metadata and
            // episode count in the background so a recently completed/updated
            // series does not remain stale for the full detail-cache TTL.
            if (cachedDetail != null && adapter != null) {
                launch(Dispatchers.IO) {
                    val refreshed = try {
                        fetchUsableDetail(adapter, playableItem)
                    } catch (_: Exception) {
                        null
                    }
                    if (refreshed != null && refreshed.episodes.isNotEmpty()) {
                        ResultCache.putDetail(detailCacheKey, refreshed)
                        withContext(Dispatchers.Main) {
                            if (detailGeneration != detailLoadGeneration) return@withContext
                            val current = activeDetail
                            if (
                                activeAlternativeIndex == 0 &&
                                current?.item?.id == playableItem.id &&
                                current.item.sourceKey == primarySourceKey
                            ) {
                                val selectedName = current.episodes.getOrNull(currentEpisodeIndex)?.name
                                activeDetail = refreshed
                                currentEpisodeIndex = refreshed.episodes.indexOfFirst {
                                    selectedName != null && it.name.equals(selectedName, ignoreCase = true)
                                }.takeIf { it >= 0 } ?: currentEpisodeIndex.coerceIn(refreshed.episodes.indices)
                            }
                        }
                    }
                }
            }

            // Recommendations are computed only from already-loaded metadata.
            // No discovery network request is allowed on the player path.
            val discoveryRecommendations = buildDiscoveryRecommendations(
                finalDetailResult.item,
                remoteDiscoveryPool()
            )

            // Show player immediately — don't wait for alt sources
            withContext(Dispatchers.Main) {
                if (detailGeneration != detailLoadGeneration) return@withContext
                loading = false
                activeDetail = finalDetailResult
                activeAlternativeIndex = 0
                relatedItems = discoveryRecommendations
                currentEpisodeIndex = finalDetailResult.episodes.indexOfFirst { ep ->
                    pendingEpisodeName?.let { preferred ->
                        ep.name.equals(preferred, ignoreCase = true) ||
                            ep.name.contains(preferred, ignoreCase = true) ||
                            preferred.contains(ep.name, ignoreCase = true)
                    } ?: false
                }.takeIf { it >= 0 } ?: 0
                pendingEpisodeName = null
                if (finalDetailResult.episodes.isNotEmpty()) {
                    selectEpisode(currentEpisodeIndex)
                } else {
                    isPlayLoading = false
                    playError = "当前来源没有返回可播放剧集"
                    notice = "该作品暂无可用选集，请尝试换源或稍后重试"
                }
            }
            if (detailGeneration != detailLoadGeneration) return@launch

            // Source-owned recommendations remain the most specific signal.
            // If unavailable, keep the deterministic tag/year/score matches
            // already derived from the remote rank/week metadata.
            if (adapter != null) {
                launch(Dispatchers.IO) {
                    // Let the first play resolve use the source's single
                    // QuickJS executor before the optional recommendation
                    // request joins its queue.
                    delay(1_200L)
                    val recommendations = try {
                        adapter.related(playableItem.id)
                            .filter {
                                SourceManager.normalizeTitle(it.title) !=
                                    SourceManager.normalizeTitle(playableItem.title)
                            }
                            .distinctBy { SourceManager.normalizeTitle(it.title) }
                            .take(20)
                    } catch (_: Exception) {
                        emptyList()
                    }
                    withContext(Dispatchers.Main) {
                        if (detailGeneration != detailLoadGeneration) return@withContext
                        val current = activeDetail
                        if (current?.item?.id == playableItem.id && recommendations.isNotEmpty()) {
                            relatedItems = recommendations
                        }
                    }
                }
            }

            // Pre-fetch episode 2 play URL in background (ep 1 is being fetched by selectEpisode above)
            val ep1 = detailResult.episodes.getOrNull(1)
            if (ep1 != null && adapter != null) {
                launch(Dispatchers.IO) {
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
            launch(Dispatchers.IO) {
                val altList = try {
                    coroutineScope {
                        val activeResolvedKey = finalDetailResult.item.sourceKey
                        val others = sourceManager.allAdapters().filter {
                            it.key != primaryKey && it.key != activeResolvedKey
                        }
                        others.map { alt ->
                            async {
                                try {
                                    // Cache alt search results to avoid redundant calls
                                    val altSearchKey = "${alt.key}:search:$itemTitle"
                                    val altItems = ResultCache.getSearch(altSearchKey)
                                        ?: alt.search(itemTitle, 1).also {
                                            if (it.isNotEmpty()) ResultCache.putSearch(altSearchKey, it)
                                        }
                                    val matched = findMatchingSourceItem(alt, itemTitle, altItems)
                                    if (matched != null) {
                                        val altDetailKey = "${alt.key}:${matched.id}"
                                        val altDetail = ResultCache.getDetail(altDetailKey)
                                            ?.let { mergeDetailMetadata(matched, it) }
                                            ?: fetchUsableDetail(alt, matched)
                                                .also {
                                                    if (it.episodes.isNotEmpty()) {
                                                        ResultCache.putDetail(altDetailKey, it)
                                                    }
                                                }
                                        alt.config.key to altDetail
                                    } else null
                                } catch (_: Exception) { null }
                            }
                        }.awaitAll().filterNotNull().let { discovered ->
                            val failedPrimary = if (activeResolvedKey != primaryKey) {
                                primaryKey to detailResult
                            } else {
                                null
                            }
                            listOfNotNull(failedPrimary) + discovered
                        }
                    }
                } catch (_: Exception) { emptyList() }

                withContext(Dispatchers.Main) {
                    if (detailGeneration != detailLoadGeneration) return@withContext
                    alternativeDetails = altList
                    if (altList.isNotEmpty()) {
                        notice = "已找到 ${altList.size + 1} 个可用数据源"
                    }
                }
            }
        }
    }

    fun selectEpisode(index: Int, forceFresh: Boolean = false) {
        val detail = currentActiveDetail()
        if (detail == null) {
            finishPlayResolutionWithError("作品详情已失效，请返回后重新打开")
            return
        }
        val episodes = detail.episodes
        if (index !in episodes.indices) {
            finishPlayResolutionWithError(
                if (episodes.isEmpty()) "当前来源没有返回可播放剧集，请尝试换源"
                else "所选剧集已失效，请重新选择"
            )
            return
        }

        currentEpisodeIndex = index
        val ep = episodes[index]
        val sourceKey = detail.item.sourceKey
        val adapter = sourceManager.getAdapter(sourceKey)
        if (adapter == null) {
            finishPlayResolutionWithError("数据源「${detail.item.sourceTitle.ifBlank { sourceKey }}」当前不可用")
            return
        }

        playResolveJob?.cancel()
        val generation = ++playResolveGeneration
        currentPlayResult = null
        playResolveJob = viewModelScope.launch {
            val resolveStartedAt = System.nanoTime()
            withContext(Dispatchers.Main) {
                isPlayLoading = true
                playError = null
                notice = "正在解析「${ep.name}」播放地址..."
            }

            // Play URL cache-first (10 min TTL) — cached hit is instant (<1ms)
            val playCacheKey = "$sourceKey:${ep.flagStr.take(200)}"
            val cachedPlay = if (!forceFresh) ResultCache.getPlay(playCacheKey) else null
            if (cachedPlay != null && isLikelyTranscodingPlaceholderUrl(cachedPlay.url)) {
                ResultCache.invalidatePlay(playCacheKey)
            }
            if (cachedPlay != null && !isLikelyTranscodingPlaceholderUrl(cachedPlay.url)) {
                val elapsedMs = (System.nanoTime() - resolveStartedAt) / 1_000_000L
                withContext(Dispatchers.Main) {
                    if (generation != playResolveGeneration) return@withContext
                    isPlayLoading = false
                    currentPlayResult = cachedPlay
                    recordLocalPlayback(detail.item, ep.name)
                    notice = "已解析 ${ep.name}（${elapsedMs}ms）"
                    notice = "正在播放 ${ep.name}"
                }
                // Pre-fetch next episode while current plays
                prefetchAdjacentEpisodes(episodes, index, sourceKey, adapter)
                return@launch
            }

            val playResult = try {
                withContext(Dispatchers.IO) {
                    withTimeout(20_000L) { adapter.play(ep.flagStr) }
                }
            } catch (_: TimeoutCancellationException) {
                null
            } catch (_: CancellationException) {
                return@launch
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "play failed: ${e.message}")
                null
            }

            withContext(Dispatchers.Main) {
                if (generation != playResolveGeneration) return@withContext
                isPlayLoading = false
                if (
                    playResult != null &&
                    playResult.url.isNotEmpty() &&
                    !isLikelyTranscodingPlaceholderUrl(playResult.url)
                ) {
                    val elapsedMs = (System.nanoTime() - resolveStartedAt) / 1_000_000L
                    ResultCache.putPlay(playCacheKey, playResult)  // Cache for instant replay
                    currentPlayResult = playResult
                    recordLocalPlayback(detail.item, ep.name)
                    notice = "已解析 ${ep.name}（${elapsedMs}ms）"
                    notice = "正在播放 ${ep.name}"
                } else {
                    val transcoding = playResult?.url?.let(::isLikelyTranscodingPlaceholderUrl) == true
                    playError = if (transcoding) "当前播放源仍在转码，正在尝试其他来源"
                        else "播放地址解析失败或解析超时"
                    notice = if (transcoding) "已拦截转码占位视频，正在换源"
                        else "播放地址解析失败 (${detail.item.sourceTitle})，试试「换源播放」"
                    if (alternativeDetails.isNotEmpty()) {
                        switchSource()
                    }
                }
            }

            // Only warm adjacent episodes after this exact request succeeded.
            // Failed/stale resolution must not queue more work on the same
            // source executor while fallback source switching is in progress.
            if (
                generation == playResolveGeneration &&
                playResult != null &&
                playResult.url.isNotBlank()
            ) {
                prefetchAdjacentEpisodes(episodes, index, sourceKey, adapter)
            }
        }
    }

    private fun recordLocalPlayback(item: SourceItem, episodeName: String) {
        storageManager.addHistory(item, episodeName)
        reloadStorageData()
        if (!TEMP_ACCOUNT_AUTH_DISABLED && accountUser != null) {
            syncAccountData()
        }
    }

    fun updatePlaybackProgress(
        item: SourceItem,
        episodeName: String,
        positionMs: Long,
        durationMs: Long
    ) {
        if (positionMs < 0L || durationMs <= 0L) return
        val now = System.currentTimeMillis()
        val percent = ((positionMs.coerceAtMost(durationMs) * 100L) / durationMs)
            .toInt()
            .coerceIn(0, 100)
        val key = "${item.sourceKey.substringBefore(',')}:${item.id}:$episodeName"
        if (
            key == lastHistoryProgressKey &&
            percent == lastHistoryProgressPercent &&
            now - lastHistoryProgressWriteAt < 15_000L
        ) {
            return
        }
        if (key == lastHistoryProgressKey && now - lastHistoryProgressWriteAt < 5_000L) return
        lastHistoryProgressKey = key
        lastHistoryProgressPercent = percent
        lastHistoryProgressWriteAt = now
        storageManager.addHistory(item, episodeName, positionMs, durationMs)
        historyList = storageManager.getHistory()
        if (!TEMP_ACCOUNT_AUTH_DISABLED && accountUser != null) {
            syncAccountData()
        }
    }

    private fun finishPlayResolutionWithError(message: String) {
        playResolveJob?.cancel()
        playResolveJob = null
        playResolveGeneration++
        currentPlayResult = null
        isPlayLoading = false
        playError = message
        notice = message
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

    fun recoverFromLikelyTranscodingPlaceholder() {
        val detail = currentActiveDetail() ?: return
        val episode = detail.episodes.getOrNull(currentEpisodeIndex) ?: return
        val recoveryKey = "${detail.item.sourceKey}:${episode.flagStr.take(200)}"
        if (!placeholderRecoveryKeys.add(recoveryKey)) return

        ResultCache.invalidatePlay(recoveryKey)
        currentPlayResult = null
        playError = "检测到播放源返回转码占位视频，正在自动恢复"
        notice = "当前源仍在转码，正在切换可用来源"
        viewModelScope.launch {
            delay(2_500L)
            if (alternativeDetails.isNotEmpty()) {
                switchSource()
            } else {
                selectEpisode(currentEpisodeIndex, forceFresh = true)
            }
        }
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

        val newDetail = currentActiveDetail()
        if (newDetail == null || newDetail.episodes.isEmpty()) {
            finishPlayResolutionWithError("该数据源没有返回可播放剧集，请选择其他来源")
            return
        }

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
        detailLoadJob?.cancel()
        detailLoadJob = null
        detailLoadGeneration++
        playResolveJob?.cancel()
        playResolveJob = null
        playResolveGeneration++
        val previousPlayer = playerNavigationStack.removeLastOrNull()
        if (previousPlayer != null) {
            activeDetail = previousPlayer.activeDetail
            currentEpisodeIndex = previousPlayer.currentEpisodeIndex
            currentPlayResult = previousPlayer.currentPlayResult
            isPlayLoading = previousPlayer.isPlayLoading
            playError = previousPlayer.playError
            relatedItems = previousPlayer.relatedItems
            alternativeDetails = previousPlayer.alternativeDetails
            activeAlternativeIndex = previousPlayer.activeAlternativeIndex
            notice = previousPlayer.notice
            pendingEpisodeName = null
            view = "player"
            return
        }
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
                            if (accountUser != null) syncAccountData()
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
            if (accountUser != null) syncAccountData()
        } else {
            notice = "删除缓存失败"
        }
    }

    fun playOfflineVideo(item: com.juying.app.source.DownloadedItemInfo) {
        val localPath = Uri.fromFile(item.videoFile).toString()
        currentPlayResult = PlayResult(
            url = localPath,
            type = if (item.videoFile.extension.equals("m3u8", ignoreCase = true)) "hls" else "mp4",
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
        recordLocalPlayback(dummyDetail.item, item.episodeName)
        playerReturnView = view
        view = "player"
        notice = "正在播放离线缓存：${item.title} ${item.episodeName}"
    }

    fun toggleFavorite(item: SourceItem, latestEpisodeNum: Int? = null) {
        val nowFavorite = storageManager.toggleFavorite(item)
        favoritesList = storageManager.getFavorites()
        notice = if (nowFavorite) "已追番" else "已取消追番"
        val mediaKey = commentMediaKey(item)
        if (nowFavorite) {
            // 记录收藏时的集数作为提醒基线，之后每更新一集才提醒
            if (latestEpisodeNum != null) storageManager.setFavoriteBaseline(mediaKey, latestEpisodeNum)
        } else {
            storageManager.removeFavoriteBaseline(mediaKey)
        }
        if (!TEMP_ACCOUNT_AUTH_DISABLED && accountUser != null) {
            syncAccountData()
        }
    }

    fun isFavorite(item: SourceItem): Boolean {
        val targetSource = item.sourceKey.substringBefore(',').trim()
        val targetTitle = SourceManager.normalizeTitle(item.title)
        return favoritesList.any { favorite ->
            val sameIdentity =
                favorite.sourceKey.substringBefore(',').trim() == targetSource &&
                    favorite.id == item.id &&
                    item.id.isNotBlank()
            val sameTitle =
                targetTitle.isNotBlank() &&
                    SourceManager.normalizeTitle(favorite.title) == targetTitle
            sameIdentity || sameTitle
        }
    }

    fun removeHistory(item: SourceItem) {
        storageManager.removeHistory(item)
        historyList = storageManager.getHistory()
        notice = "已删除该条观看记录"
    }

    fun clearHistory() {
        storageManager.clearHistory()
        reloadStorageData()
    }

    override fun onCleared() {
        animeCategoryRankingJobs.values.forEach { it.cancel() }
        detailLoadJob?.cancel()
        playResolveJob?.cancel()
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
        PipController.resetActivityState()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // 小窗模式下播放器隐藏顶部栏/进度条等控制层（由 EmbeddedVideoPlayer 读取）
        PipController.inPipMode = isInPictureInPictureMode
        if (!isInPictureInPictureMode) {
            PipController.finishPipSession()
        }
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

    /**
     * PiP is deliberately manual-only. Pressing Home or switching apps must
     * follow the normal background policy (pause), not silently create a new
     * PiP session because the user used the PiP button earlier.
     */
    fun enterPlayerPictureInPicture(): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O ||
            !PipController.playerActive
        ) {
            return false
        }
        PipController.beginManualPipSession()
        return try {
            val entered = enterPictureInPictureMode(buildPipParams())
            if (!entered) PipController.cancelManualPipSession()
            entered
        } catch (_: Exception) {
            PipController.cancelManualPipSession()
            false
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
            vm.view.startsWith("settings_") -> vm.view = "settings"
            vm.view.startsWith("profile_") || vm.view == "settings" -> vm.view = "profile"
            vm.view == "search" || vm.view == "search_result" -> vm.view = "home"
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
                    if (
                        vm.view != "player" &&
                        !vm.view.startsWith("profile_") &&
                        !vm.view.startsWith("seasonal_") &&
                        vm.view != "search" &&
                        vm.view != "search_result" &&
                        !vm.view.startsWith("settings")
                    ) {
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
                                icon = { Icon(painterResource(R.drawable.ic_leaderboard), null) },
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
                        "search" -> SearchLandingScreen(vm)
                        "search_result" -> SearchResultScreen(vm)
                        "library" -> LibraryView(vm)
                        "schedule" -> WeeklyScheduleScreen(vm)
                        "leaderboard" -> LeaderboardScreen(vm)
                        "seasonal_ranking" -> SeasonalRankingScreen(vm)
                        "seasonal_schedule" -> SeasonalScheduleScreen(vm)
                        "profile" -> ProfileView(vm)
                        "profile_history" -> HistoryScreen(vm)
                        "profile_favorites" -> FavoritesScreen(vm)
                        "profile_downloads" -> OfflineCacheScreen(vm)
                        "settings" -> SettingsScreen(vm)
            "notifications" -> NotificationsScreen(vm)
                        "settings_password" -> ChangePasswordScreen(vm)
                        "settings_email" -> ChangeEmailScreen(vm)
                        "settings_feedback" -> FeedbackScreen(vm)
                        "settings_disclaimer" -> DisclaimerScreen(vm)
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
                                    "更新说明",
                                    color = AppColors.muted,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 280.dp),
                                    color = AppColors.panel2,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        info.notes.ifBlank { "包含性能优化与已知问题修复" },
                                        color = AppColors.muted,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState())
                                            .padding(12.dp)
                                    )
                                }
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

            if (vm.accountDialogVisible) {
                AccountDialog(vm) { vm.accountDialogVisible = false }
            }

            if (vm.announcementDialogVisible && !vm.updateDialogVisible) {
                vm.announcement?.let { notice ->
                    AlertDialog(
                        onDismissRequest = vm::closeAnnouncement,
                        icon = {
                            Surface(shape = RoundedCornerShape(12.dp), color = AppColors.orange) {
                                Text(
                                    "公告",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                )
                            }
                        },
                        title = { Text(notice.title, color = AppColors.text, fontWeight = FontWeight.Bold) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (notice.updatedAt.isNotBlank()) {
                                    Text("更新时间：${notice.updatedAt}", color = AppColors.muted, fontSize = 11.sp)
                                }
                                Surface(
                                    color = AppColors.panel2,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                                ) {
                                    Text(
                                        notice.content,
                                        color = AppColors.text,
                                        lineHeight = 22.sp,
                                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(14.dp)
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = vm::closeAnnouncement,
                                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.orange),
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("知道了") }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    TextButton(onClick = { vm.dismissAnnouncement(0) }) { Text("今日内不再弹出") }
                                    TextButton(onClick = { vm.dismissAnnouncement(7) }) { Text("近期不再弹出") }
                                }
                            }
                        },
                        containerColor = AppColors.panel
                    )
                }
            }
        }
    }
}

// 文本超宽时向左滚动展示全名（跑马灯）；文本不超宽时静态左对齐
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@Composable
private fun MarqueeText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
    speedDpPerSec: Float = 48f,
    gapDp: Float = 48f
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val style = TextStyle(color = color, fontSize = fontSize, fontWeight = fontWeight)
    val measuredWidthPx = remember(text, style) {
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = style,
            constraints = androidx.compose.ui.unit.Constraints(maxWidth = androidx.compose.ui.unit.Constraints.Infinity)
        ).size.width.toFloat()
    }
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val needsMarquee = measuredWidthPx > containerWidthPx && containerWidthPx > 0f
    val travelPx = measuredWidthPx + with(density) { gapDp.dp.toPx() }
    val durationMs = if (needsMarquee) {
        (((travelPx + containerWidthPx) / with(density) { speedDpPerSec.dp.toPx() }) * 1000f).toInt().coerceAtLeast(2000)
    } else 1
    val infinite = rememberInfiniteTransition(label = "marqueeTransition")
    val offsetX by infinite.animateFloat(
        initialValue = if (needsMarquee) containerWidthPx else 0f,
        targetValue = if (needsMarquee) -travelPx else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "marqueeOffset"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { containerWidthPx = it.width.toFloat() }
    ) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.offset(x = with(density) { offsetX.dp })
        )
    }
}

@Composable
fun HomeView(vm: MainViewModel) {
    var selectedCategory by remember { mutableStateOf("精选") }
    val homeSeasons = vm.lanercSeasons.map { it.season }
        .ifEmpty { beijingSeasonWindow() }
    val homeSeasonSummary = homeSeasons.firstOrNull()?.year
        ?.let { year ->
            "$year · ${homeSeasons.joinToString(" / ") { "${it.month}月" }}"
        }
        .orEmpty()

    val featuredItems = remember(vm.homeSections.firstOrNull()?.items?.firstOrNull()?.id, selectedCategory) {
        val filteredSections = if (selectedCategory == "精选") {
            vm.homeSections
        } else {
            vm.homeSections.filter { section ->
                val sectionKind = when {
                    section.title.contains("国漫") || section.key.contains("guo") -> "国漫"
                    section.title.contains("日漫") || section.title.contains("日本") || section.title.contains("番") -> "日漫"
                    section.title.contains("剧场") || section.title.contains("电影") -> "剧场版"
                    section.title.contains("欧美") -> "欧美"
                    else -> "全部"
                }
                sectionKind == selectedCategory
            }
        }
        filteredSections.flatMap { it.items }.distinctBy { SourceManager.normalizeTitle(it.title) }.take(5)
    }

    // 分类页签（日漫/国漫/剧场版）专用内容：严格按分类聚合全部首页作品，
    // 轮播图与热门推荐 3x3 网格每 10 秒轮换展示，不固定同一批内容。
    var categoryRotation by remember(selectedCategory) { mutableStateOf(0L) }
    LaunchedEffect(selectedCategory) {
        if (selectedCategory != "精选") {
            while (true) {
                delay(10_000L)
                categoryRotation++
            }
        }
    }
    val categoryAllItems = remember(vm.homeSections, selectedCategory) {
        if (selectedCategory == "精选") emptyList() else categoryHomeItems(vm, selectedCategory)
    }
    val categoryCarouselItems = remember(categoryAllItems, categoryRotation) {
        rotateWindow(categoryAllItems, (categoryRotation * 5L).toInt(), 5)
    }
    val categoryHotItems = remember(categoryAllItems, categoryRotation) {
        rotateWindow(categoryAllItems, (categoryRotation * 3L).toInt(), 9)
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
                AccountAvatar(vm.accountUser?.avatarUrl, Modifier.fillMaxSize())
            }

            Spacer(Modifier.width(8.dp))

            // Keep the whole bar as one click target. A read-only text field
            // used here previously consumed taps before the Surface.
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clickable { vm.openSearch() },
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
                    Text(
                        text = vm.query.ifBlank { "今天你想看些什么？" },
                        modifier = Modifier.weight(1f),
                        color = if (vm.query.isBlank()) AppColors.muted else AppColors.text,
                        fontSize = if (vm.query.isBlank()) 12.sp else 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val arrowTint = if (vm.query.isNotBlank()) AppColors.cyan else AppColors.muted.copy(alpha = 0.5f)
                    Text("➔", color = arrowTint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.width(8.dp))

            // Notification Bell with Unread Badge
            Box(modifier = Modifier.size(36.dp)) {
                IconButton(
                    onClick = { vm.openNotificationsScreen() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_notification),
                        contentDescription = "消息通知",
                        tint = AppColors.text,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (vm.unreadNotificationCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                            .size(16.dp)
                            .background(AppColors.rose, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (vm.unreadNotificationCount > 99) "99+" else vm.unreadNotificationCount.toString(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // History Clock Icon on Right (截图1 风格)
            IconButton(
                onClick = { vm.view = "profile_history" },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(R.drawable.ic_history),
                    contentDescription = "观看历史",
                    tint = AppColors.text,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── CATEGORY TABS (精选 | 日漫 | 剧场版) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            listOf("精选", "日漫", "国漫", "剧场版").forEach { category ->
                val isSelected = selectedCategory == category
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        selectedCategory = category
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
                    Column(modifier = Modifier.weight(1f)) {
                        MarqueeText(
                            text = activeDetail.item.title,
                            color = AppColors.text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            currentEpName,
                            color = AppColors.muted,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(6.dp))
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
                    // ── 精选页签：轮播 + 快捷入口 + 公告 + 全部分区；分类页签只显示当前分类内容 ──
                    if (selectedCategory == "精选") {
                    // ── CAROUSEL BANNER CARD (截图2 风格) ──
                    if (featuredItems.isNotEmpty()) {
                        item(key = "home_carousel_banner") {
                            HomeCarouselBanner(
                                items = featuredItems,
                                onSelect = { vm.openMovie(it) }
                            )
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
                                    .height(58.dp)
                                    .clickable { vm.view = "seasonal_ranking" },
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
                                    Column {
                                        Text("季度新番榜", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(homeSeasonSummary, color = AppColors.muted, fontSize = 10.sp)
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .clickable { vm.view = "seasonal_schedule" },
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
                                    Column {
                                        Text("季度排期表", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(homeSeasonSummary, color = AppColors.muted, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    // ── ANNOUNCEMENT CARD (季度排期表下方，热门推荐上方，匹配截图样式) ──
                    item(key = "home_announcement_card") {
                        val announcementText = vm.announcement?.let { it.summary.ifBlank { it.title } }
                            ?: "手机环境异常及注册账号注意事项"
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clickable { vm.openAnnouncement() },
                            shape = RoundedCornerShape(22.dp),
                            color = AppColors.panel,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                AppColors.orange.copy(alpha = 0.22f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFFF5500)
                                ) {
                                    Text(
                                        "公告",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    announcementText,
                                    color = AppColors.text,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.KeyboardArrowRight,
                                    contentDescription = "查看公告",
                                    tint = AppColors.muted.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
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
                    } else {
                        // ── 分类页签内容（日漫/国漫/剧场版）──
                        if (categoryAllItems.isEmpty()) {
                            item(key = "category_empty_${selectedCategory}") {
                                Box(
                                    Modifier.fillMaxWidth().padding(vertical = 60.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("暂未加载到${selectedCategory}推荐内容", color = AppColors.muted, fontSize = 13.sp)
                                }
                            }
                        } else {
                            if (categoryCarouselItems.isNotEmpty()) {
                                item(key = "category_carousel_${selectedCategory}") {
                                    HomeCarouselBanner(
                                        items = categoryCarouselItems,
                                        label = "${selectedCategory}内容",
                                        onSelect = { vm.openMovie(it) }
                                    )
                                }
                            }
                            item(key = "category_hot_header_${selectedCategory}") {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("热门推荐", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                    TextButton(onClick = {
                                        vm.applyFilter(kind = selectedCategory)
                                        vm.view = "library"
                                    }) {
                                        Text("查看更多", color = AppColors.cyan, fontSize = 13.sp)
                                    }
                                }
                            }
                            item(key = "category_hot_grid_${selectedCategory}") {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    categoryHotItems.chunked(3).forEach { rowItems ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            rowItems.forEach { item ->
                                                MovieCard(item, Modifier.weight(1f).padding(4.dp)) { vm.openMovie(item) }
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

/**
 * 严格区分日漫/国漫/剧场版：标题、分类与标签同时作为证据；
 * 日漫与国漫互斥，带剧场版/电影证据的作品只归入剧场版分类，避免分类互相混入。
 */
private fun strictCategoryMatch(item: SourceItem, category: String): Boolean {
    val evidence = "${item.title} ${item.kind} ${item.tags.joinToString(" ")}".lowercase()
    val chinese = listOf("国漫", "国产动画", "国产动漫", "国创", "中国动漫", "中国动画", "大陆动漫", "大陆", "华语")
        .any(evidence::contains)
    val japanese = listOf("日漫", "日本动画", "日本动漫", "日本番剧", "anime", "日剧")
        .any(evidence::contains)
    val theatrical = listOf("剧场版", "动画电影", "动漫电影", "电影")
        .any(evidence::contains)
    return when (category) {
        "日漫" -> japanese && !chinese && !theatrical
        "国漫" -> chinese && !japanese && !theatrical
        else -> theatrical
    }
}

private fun categoryHomeItems(vm: MainViewModel, category: String): List<SourceItem> {
    return vm.homeSections
        .flatMap { it.items }
        .distinctBy { SourceManager.normalizeTitle(it.title) }
        .filter { strictCategoryMatch(it, category) }
}

/** 把作品池按偏移量循环平移后取前 size 个，保证分类轮播/热门推荐定期轮换内容。 */
private fun rotateWindow(items: List<SourceItem>, offset: Int, size: Int): List<SourceItem> {
    if (items.size <= size) return items
    val start = ((offset % items.size) + items.size) % items.size
    return (items.drop(start) + items.take(start)).take(size)
}

@Composable
fun LibraryView(vm: MainViewModel) {
    var filterExpanded by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        Text(
            "多源片库",
            Modifier.padding(16.dp, 12.dp, 16.dp, 2.dp),
            color = AppColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (vm.loading && vm.items.isEmpty()) "加载中..." else "共 ${vm.totalLibrary} 部作品${if (vm.libraryHasMore) " (下滑加载更多)" else ""}",
                color = AppColors.muted, fontSize = 13.sp
            )
            TextButton(onClick = { filterExpanded = !filterExpanded }) {
                Text(
                    if (filterExpanded) "收起筛选 ▲" else "展开筛选 ▼",
                    color = AppColors.cyan,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(Modifier.height(2.dp))

        if (filterExpanded) {
            FilterRow("分类", vm.kinds, vm.activeKind) { vm.applyFilter(kind = it) }
            FilterRow("题材", vm.genres, vm.activeGenre) { vm.applyFilter(genre = it) }
            FilterRow("年份", vm.years, vm.activeYear) { vm.applyFilter(year = it) }
            FilterRow("排序", vm.sorts, vm.activeSort) { vm.applyFilter(sort = it) }
            FilterRow("来源", vm.sourceOptions, vm.activeSource) { vm.applyFilter(source = it) }
            Spacer(Modifier.height(4.dp))
        }

        if (vm.loading && vm.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingSpinner(modifier = Modifier.size(36.dp), color = AppColors.cyan)
            }
        } else if (vm.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        vm.libraryLoadError ?: "暂无满足筛选条件的作品",
                        color = if (vm.libraryLoadError != null) AppColors.rose else AppColors.muted
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { vm.fetchLibrary() }) {
                        Text("重新加载", color = AppColors.cyan)
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                gridItemsIndexed(
                    items = vm.items,
                    key = { index, item -> "${item.sourceKey}:${item.id}:$index" }
                ) { index, item ->
                    MovieCard(item, Modifier.padding(4.dp)) { vm.openMovie(item) }
                    // Exactly one card triggers pre-fetch. The previous >=
                    // condition launched up to nine concurrent loadNextPage()
                    // calls and could cancel the request that owned the spinner.
                    val prefetchIndex = (vm.items.size - 9).coerceAtLeast(0)
                    if (
                        index == prefetchIndex &&
                        vm.libraryHasMore &&
                        vm.libraryLoadError == null &&
                        !vm.libraryLoadingMore &&
                        !vm.loading
                    ) {
                        LaunchedEffect(vm.items.size, vm.libraryPage) { vm.loadNextPage() }
                    }
                }

                if (vm.libraryLoadingMore) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "footer_loading_more") {
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
                } else if (vm.libraryLoadError != null) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "footer_load_error") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                vm.libraryLoadError.orEmpty(),
                                color = AppColors.rose,
                                fontSize = 13.sp
                            )
                            TextButton(onClick = { vm.loadNextPage() }) {
                                Text("点击重试", color = AppColors.cyan)
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
    var playerFullscreen by remember { mutableStateOf(false) }

    val currentEpisode = detail.episodes.getOrNull(vm.currentEpisodeIndex)
    val isFav = vm.isFavorite(detail.item)
    val totalSources = 1 + vm.alternativeDetails.size
    val mediaStatus = resolveMediaStatus(detail.item, detail.episodes.size)
    val genreSummary = resolveAnimeGenres(detail.item)
        .joinToString(" ")
        .ifBlank { detail.item.kind.ifBlank { "动漫" } }
    val episodeSummary = mediaStatus.episodeText.ifBlank {
        if (detail.episodes.isNotEmpty()) "${detail.episodes.size}集" else "集数未知"
    }

    val chunkSize = 30
    val episodeChunks = remember(detail.episodes) { detail.episodes.chunked(chunkSize) }

    if (showAllEpisodesModal) {
        AllEpisodesModal(vm) { showAllEpisodesModal = false }
    }

    // 进入详情/播放页时按作品加载云端评论（每次进入都强制刷新，保证最新评论可见）
    LaunchedEffect(detail.item.sourceKey, detail.item.id) {
        vm.loadCommentsForActiveDetail(force = true)
    }

    // 横屏（全屏播放）时播放器必须精确占满可见区域：
    // 若按宽度推导 16:9 高度（宽×9/16），在不同尺寸/比例的机型上会超出屏幕高度，
    // 导致顶部栏和进度条下方控制行被裁切（见用户反馈的横屏截屏）。
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val showLandscapeSidePanel = PlayerInteractionPolicy.showLandscapeSidePanel(
        isLandscape = isLandscape,
        explicitFullscreen = playerFullscreen,
        inPictureInPicture = PipController.inPipMode
    )

    Row(Modifier.fillMaxSize()) {
        Column(
            modifier = if (showLandscapeSidePanel) {
                Modifier.weight(2f).fillMaxHeight()
            } else {
                Modifier.fillMaxSize()
            }
        ) {
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
                    danmakuMediaKey = vm.commentMediaKey(detail.item),
                    danmakuEpisodeKey = currentEpisode?.name.orEmpty(),
                    episodes = detail.episodes,
                    currentEpisodeIndex = vm.currentEpisodeIndex,
                    onSelectEpisode = { vm.selectEpisode(it) },
                    onBack = { vm.goBackFromPlayer() },
                    onNextEpisode = if (vm.currentEpisodeIndex < detail.episodes.size - 1) {
                        { vm.selectEpisode(vm.currentEpisodeIndex + 1) }
                    } else null,
                    onPrevEpisode = if (vm.currentEpisodeIndex > 0) {
                        { vm.selectEpisode(vm.currentEpisodeIndex - 1) }
                    } else null,
                    onError = { vm.invalidateCurrentPlayCache() },
                    onLikelyTranscodingPlaceholder = {
                        vm.recoverFromLikelyTranscodingPlaceholder()
                    },
                    onFullscreenChanged = { playerFullscreen = it },
                    onNavigateToLogin = { vm.accountDialogVisible = true },
                    onPlaybackProgress = { positionMs, durationMs ->
                        vm.updatePlaybackProgress(
                            item = detail.item,
                            episodeName = currentEpisode?.name.orEmpty(),
                            positionMs = positionMs,
                            durationMs = durationMs
                        )
                    }
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
                            Text("尚未获取播放地址", color = AppColors.muted, fontSize = 13.sp)
                            Spacer(Modifier.height(6.dp))
                            TextButton(onClick = { vm.retryPlay() }) {
                                Text("重新解析", color = AppColors.cyan)
                            }
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
                        "评论 (${vm.comments.sumOf { 1 + it.replies.size }})",
                        fontWeight = if (activeTab == "comments") FontWeight.Bold else FontWeight.Normal,
                        fontSize = 15.sp
                    )
                }
            )
        }

        // ── Tab Content Area ──
        if (activeTab == "details" && expandedDescription) {
            MediaInfoOverlay(
                detail = detail,
                status = mediaStatus,
                genres = genreSummary,
                onDismiss = { expandedDescription = false }
            )
        } else if (activeTab == "details") {
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
                            onClick = { expandedDescription = true }
                        ) {
                            Text("简介 >", color = AppColors.cyan, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }

                // Meta tags + source count
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "$genreSummary  |  ${detail.item.year.ifBlank { "年份未知" }}  |  $episodeSummary",
                            color = AppColors.muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
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

                // Action Buttons Row (换源 | 缓存番剧 | 追番 | 分享)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        ActionButton(
                            androidx.compose.ui.res.painterResource(R.drawable.ic_switch_source), "换源",
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
                        ) { vm.toggleFavorite(detail.item, latestEpisodeNum(detail)) }
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

                if (vm.relatedItems.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(18.dp))
                        Text(
                            "相关推荐",
                            color = AppColors.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            "由当前数据源提供",
                            color = AppColors.muted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 20.dp)
                        ) {
                            lazyItemsIndexed(
                                items = vm.relatedItems,
                                key = { index, item -> "related:${item.sourceKey}:${item.id}:$index" }
                            ) { _, item ->
                                MovieCard(
                                    item = item,
                                    modifier = Modifier.width(104.dp)
                                ) {
                                    vm.openMovie(item)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Standalone Comments Tab Screen
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                if (vm.accountUser == null) {
                    Surface(
                        color = AppColors.cyan.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("请先“登录”后发表评论", color = AppColors.text, fontSize = 13.sp)
                            Button(
                                onClick = { vm.accountDialogVisible = true },
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.cyan),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("登录", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        val replyTarget = vm.replyTargetComment
                        if (replyTarget != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = AppColors.cyan.copy(alpha = 0.12f),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "回复 @${replyTarget.nick}",
                                        color = AppColors.cyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "取消",
                                        color = AppColors.muted,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { vm.replyTargetComment = null }
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        var showEmojiPanel by remember { mutableStateOf(false) }
                        val commentImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                            if (uri != null) vm.uploadCommentImage(uri)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(AppColors.panel2)
                                    .padding(start = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.weight(1f)) {
                                    if (vm.commentDraft.isEmpty()) {
                                        Text(
                                            if (replyTarget != null) "回复 @${replyTarget.nick}" else "友善评论，分享你的观后感",
                                            color = AppColors.muted,
                                            fontSize = 14.sp,
                                            maxLines = 1
                                        )
                                    }
                                    BasicTextField(
                                        value = vm.commentDraft,
                                        onValueChange = { vm.commentDraft = it.take(200) },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                        singleLine = true,
                                        textStyle = TextStyle(color = AppColors.text, fontSize = 15.sp),
                                        cursorBrush = SolidColor(AppColors.cyan)
                                    )
                                }
                                Text(
                                    "😀",
                                    fontSize = 18.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showEmojiPanel = !showEmojiPanel }
                                        .padding(horizontal = 6.dp, vertical = 6.dp)
                                )
                                when {
                                    vm.commentImageUploading -> {
                                        Text("上传中…", color = AppColors.muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp))
                                    }
                                    vm.commentImageUrl.isNotEmpty() -> {
                                        Box(Modifier.padding(horizontal = 4.dp)) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(vm.commentImageUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = "评论图片",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .align(Alignment.TopEnd)
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.7f))
                                                    .clickable { vm.commentImageUrl = "" }
                                                    .padding(2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("×", color = Color.White, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                    else -> {
                                        Text(
                                            "🖼️",
                                            fontSize = 18.sp,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { commentImagePicker.launch("image/*") }
                                                .padding(horizontal = 6.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = vm::addComment,
                                enabled = (vm.commentDraft.trim().isNotEmpty() || vm.commentImageUrl.isNotEmpty()) && !vm.commentPosting,
                                colors = ButtonDefaults.buttonColors(containerColor = AppColors.cyan)
                            ) { Text(if (vm.commentPosting) "发布中" else "发布") }
                        }
                        if (showEmojiPanel) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(COMMENT_EMOJIS.size) { index ->
                                    val emoji = COMMENT_EMOJIS[index]
                                    Text(
                                        emoji,
                                        fontSize = 22.sp,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                vm.commentDraft = (vm.commentDraft + emoji).take(200)
                                                showEmojiPanel = false
                                            }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                if (vm.comments.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("暂无可展示评论", color = AppColors.muted, fontSize = 14.sp)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                        items(vm.comments.size) { index ->
                            val comment = vm.comments[index]
                            CommentCard(
                                comment = comment,
                                currentUserId = vm.accountUser?.id.orEmpty(),
                                onLike = { target ->
                                    if (vm.accountUser == null) {
                                        vm.accountDialogVisible = true
                                    } else {
                                        vm.likeComment(target)
                                    }
                                },
                                onReply = { target ->
                                    if (vm.accountUser == null) {
                                        vm.accountDialogVisible = true
                                    } else {
                                        vm.replyTargetComment = target
                                    }
                                },
                                onDelete = { vm.deleteComment(it) }
                            )
                        }
                    }
                }
            }
        }
        }
        }
        if (showLandscapeSidePanel) {
            LandscapePlayerSidePanel(
                vm = vm,
                detail = detail,
                mediaStatus = mediaStatus,
                genreSummary = genreSummary,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun CommentActionRow(
    likesCount: Int,
    likedByMe: Boolean,
    isOwn: Boolean,
    onLike: () -> Unit,
    onReply: (() -> Unit)?,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            if (likesCount > 0) "👍 $likesCount" else "👍",
            color = if (likedByMe) AppColors.rose else AppColors.muted,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onLike() }
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
        if (onReply != null) {
            Text(
                "回复",
                color = AppColors.muted,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onReply() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        if (isOwn) {
            Text(
                "删除",
                color = AppColors.rose.copy(alpha = 0.8f),
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onDelete() }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

/** 点击评论图片后全屏查看，点击任意处关闭 */
@Composable
private fun CommentImageViewer(url: String?, onDismiss: () -> Unit) {
    if (url.isNullOrBlank()) return
    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = "评论图片大图",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            )
        }
    }
}

@Composable
private fun CommentReplyRow(
    reply: CloudComment,
    currentUserId: String,
    onLike: () -> Unit,
    onDelete: () -> Unit
) {
    var previewUrl by remember { mutableStateOf<String?>(null) }
    Row(verticalAlignment = Alignment.Top) {
        AccountAvatar(avatarUrl = reply.avatarUrl, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(reply.nick, color = AppColors.cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                if (!reply.replyToNick.isNullOrBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "回复 @${reply.replyToNick}",
                        color = AppColors.muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(reply.text, color = AppColors.text, fontSize = 13.sp)
            if (!reply.imageUrl.isBlank()) {
                Spacer(Modifier.height(4.dp))
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(reply.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "评论图片",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { previewUrl = reply.imageUrl }
                )
            }
            Spacer(Modifier.height(4.dp))
            CommentActionRow(
                likesCount = reply.likesCount,
                likedByMe = reply.likedByMe,
                isOwn = reply.userId.isNotBlank() && reply.userId == currentUserId,
                onLike = onLike,
                onReply = null,
                onDelete = onDelete
            )
        }
    }
    CommentImageViewer(previewUrl, onDismiss = { previewUrl = null })
}

@Composable
private fun CommentCard(
    comment: CloudComment,
    currentUserId: String,
    onLike: (CloudComment) -> Unit,
    onReply: (CloudComment) -> Unit,
    onDelete: (CloudComment) -> Unit
) {
    var previewUrl by remember { mutableStateOf<String?>(null) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.panel2)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                AccountAvatar(avatarUrl = comment.avatarUrl, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(comment.nick, color = AppColors.cyan, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            formatRelativeTime(comment.ts),
                            color = AppColors.muted,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(comment.text, color = AppColors.text, fontSize = 14.sp)
                    if (!comment.imageUrl.isBlank()) {
                        Spacer(Modifier.height(4.dp))
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(comment.imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "评论图片",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { previewUrl = comment.imageUrl }
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    CommentActionRow(
                        likesCount = comment.likesCount,
                        likedByMe = comment.likedByMe,
                        isOwn = comment.userId.isNotBlank() && comment.userId == currentUserId,
                        onLike = { onLike(comment) },
                        onReply = { onReply(comment) },
                        onDelete = { onDelete(comment) }
                    )
                }
            }

            if (comment.replies.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 46.dp)
                        .background(AppColors.bg.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    comment.replies.forEach { reply ->
                        CommentReplyRow(
                            reply = reply,
                            currentUserId = currentUserId,
                            onLike = { onLike(reply) },
                            onDelete = { onDelete(reply) }
                        )
                    }
                }
            }
        }
    }
    CommentImageViewer(previewUrl, onDismiss = { previewUrl = null })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LandscapePlayerSidePanel(
    vm: MainViewModel,
    detail: DetailResult,
    mediaStatus: MediaStatusSummary,
    genreSummary: String,
    modifier: Modifier = Modifier
) {
    // 默认展示"动漫"详情（名称/番剧/类型/年份/简介），对应竖屏播放器下方的内容
    var selectedTab by remember(detail.item.sourceKey, detail.item.id) {
        mutableStateOf("info")
    }
    val isFavorite = vm.isFavorite(detail.item)

    Column(
        modifier = modifier
            .background(AppColors.bg)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            detail.item.title,
            color = AppColors.text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "$genreSummary · ${detail.item.year.ifBlank { "年份未知" }} · " +
                mediaStatus.episodeText.ifBlank { "共${detail.episodes.size}集" },
            color = AppColors.muted,
            fontSize = 11.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp, bottom = 6.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(
                selected = selectedTab == "episodes",
                onClick = { selectedTab = "episodes" },
                label = { Text("选集 ${detail.episodes.size}", fontSize = 11.sp) }
            )
            FilterChip(
                selected = selectedTab == "info",
                onClick = { selectedTab = "info" },
                label = { Text("简介/来源", fontSize = 11.sp) }
            )
        }

        if (selectedTab == "episodes") {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)
            ) {
                gridItemsIndexed(detail.episodes) { index, episode ->
                    val selected = index == vm.currentEpisodeIndex
                    Surface(
                        onClick = { vm.selectEpisode(index) },
                        modifier = Modifier.padding(3.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = if (selected) {
                            AppColors.cyan.copy(alpha = 0.22f)
                        } else {
                            AppColors.panel2
                        },
                        border = if (selected) {
                            androidx.compose.foundation.BorderStroke(1.dp, AppColors.cyan)
                        } else null
                    ) {
                        Text(
                            episode.name,
                            color = if (selected) AppColors.cyan else AppColors.text,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(onClick = { vm.toggleFavorite(detail.item, latestEpisodeNum(detail)) }) {
                            Icon(
                                if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isFavorite) AppColors.rose else AppColors.text,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(if (isFavorite) "已收藏" else "收藏", fontSize = 11.sp)
                        }
                        TextButton(
                            onClick = { vm.switchSource() },
                            enabled = vm.availableSourceLabels.size > 1
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_switch_source),
                                contentDescription = null,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("换源", fontSize = 11.sp)
                        }
                    }
                }
                if (vm.availableSourceLabels.size > 1) {
                    item {
                        Text("播放来源", color = AppColors.muted, fontSize = 11.sp)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            vm.availableSourceLabels.forEachIndexed { index, label ->
                                FilterChip(
                                    selected = index == vm.activeAlternativeIndex,
                                    onClick = { vm.selectSource(index) },
                                    label = {
                                        Text(
                                            label,
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                item {
                    MediaInfoLine("状态", mediaStatus.displayText.substringBefore(" | "))
                    if (detail.item.score.isNotBlank()) {
                        MediaInfoLine("评分", detail.item.score)
                    }
                    MediaInfoLine("类型", genreSummary)
                    Spacer(Modifier.height(8.dp))
                    Text("简介", color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        detail.item.description.ifBlank { "暂无简介" },
                        color = AppColors.muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaInfoOverlay(
    detail: DetailResult,
    status: MediaStatusSummary,
    genres: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AppColors.bg,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 28.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        detail.item.title,
                        color = AppColors.text,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(
                        onClick = onDismiss,
                        modifier = Modifier.size(38.dp),
                        shape = CircleShape,
                        color = AppColors.cyan
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "收起简介",
                                tint = Color.White
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            if (detail.item.score.isNotBlank()) {
                item { MediaInfoLine("评分", detail.item.score) }
            }
            item { MediaInfoLine("年份", detail.item.year.ifBlank { "未知" }) }
            item { MediaInfoLine("状态", status.displayText.substringBefore(" | ")) }
            item {
                MediaInfoLine(
                    "集数",
                    status.episodeText.ifBlank {
                        detail.episodes.size.takeIf { it > 0 }?.let { "${it}集" } ?: "未知"
                    }
                )
            }
            item { MediaInfoLine("类型", genres.ifBlank { detail.item.kind.ifBlank { "动漫" } }) }
            item {
                Spacer(Modifier.height(20.dp))
                Text(
                    "简介",
                    color = AppColors.text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    detail.item.description.ifBlank { "暂无简介" },
                    color = AppColors.muted,
                    fontSize = 15.sp,
                    lineHeight = 23.sp
                )
            }
        }
    }
}

@Composable
private fun MediaInfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            "$label：",
            color = AppColors.muted,
            fontSize = 15.sp,
            modifier = Modifier.width(56.dp)
        )
        Text(
            value,
            color = AppColors.muted,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
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
fun ActionButton(
    icon: androidx.compose.ui.graphics.painter.Painter,
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
    var dialogVisible by remember { mutableStateOf(false) }
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.panel),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (TEMP_ACCOUNT_AUTH_DISABLED) "当前为本地使用模式" else if (vm.accountUser == null) "登录后同步记录、追番与缓存索引" else "云端账号",
                color = AppColors.text,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (TEMP_ACCOUNT_AUTH_DISABLED) "登录、注册、邮箱和云端同步暂时关闭；观看记录、收藏和离线缓存均可直接使用。"
                else if (vm.accountUser == null) "未登录时观看、追番和离线缓存仍可使用；云端不保存视频。" else "${vm.accountUser?.nickname} · ${vm.accountUser?.email}",
                color = AppColors.muted,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            if (TEMP_ACCOUNT_AUTH_DISABLED) {
                Text("账号功能暂时停用", color = AppColors.muted, fontSize = 12.sp)
            } else if (vm.accountUser == null) {
                Button(
                    onClick = { dialogVisible = true },
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
    if (dialogVisible) {
        AccountDialog(vm) { dialogVisible = false }
    }
}

// 登录、注册、验证码与密码找回共用的账号弹窗。
@Composable
fun AccountDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    var registering by remember { mutableStateOf(false) }
    var forgot by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }
    var nickname by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val passwordMismatch = (registering || forgot) &&
        confirmPassword.isNotEmpty() && password != confirmPassword

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
                            enabled = !vm.accountBusy && isValidEmail(email) &&
                                vm.accountCodeCooldownSeconds == 0
                        ) {
                            Text(
                                if (vm.accountCodeCooldownSeconds > 0) {
                                    "${vm.accountCodeCooldownSeconds} 秒后重试"
                                } else {
                                    "获取验证码"
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (forgot) "新密码" else "密码") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(
                                    if (passwordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
                                ),
                                contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                                modifier = Modifier.size(20.dp),
                                tint = AppColors.muted
                            )
                        }
                    },
                    supportingText = {
                        if (registering || forgot) {
                            val isStrong = isPasswordStrong(password)
                            if (password.isNotEmpty() && !isStrong) {
                                Text("密码需至少8位，且包含大写字母、小写字母、数字、特殊字符中的至少3种", color = AppColors.orange, fontSize = 11.sp)
                            } else if (password.isEmpty()) {
                                Text("密码需至少8位，包含大/小写字母、数字、特殊字符中至少3种", color = AppColors.muted, fontSize = 11.sp)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (registering || forgot) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("确认密码") },
                        visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                                Icon(
                                    painter = androidx.compose.ui.res.painterResource(
                                        if (confirmPasswordVisible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
                                    ),
                                    contentDescription = if (confirmPasswordVisible) "隐藏密码" else "显示密码",
                                    modifier = Modifier.size(20.dp),
                                    tint = AppColors.muted
                                )
                            }
                        },
                        isError = passwordMismatch,
                        supportingText = {
                            if (passwordMismatch) Text("两次输入的密码不一致")
                        },
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
                        (if (forgot || registering) code.length == 6 && isPasswordStrong(password) &&
                            password == confirmPassword && (!registering || nickname.trim().isNotEmpty())
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
                        email = ""
                        password = ""
                        confirmPassword = ""
                        nickname = ""
                        code = ""
                        vm.accountMessage = ""
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) { Text(if (registering) "已有账号？返回登录" else "没有账号？注册") }
                if (!registering && !forgot) {
                    TextButton(
                        onClick = {
                            forgot = true
                            email = ""
                            password = ""
                            confirmPassword = ""
                            code = ""
                            vm.accountMessage = ""
                        },
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

private class AvatarCropActions(
    val pickFromGallery: () -> Unit,
    val takePhoto: () -> Unit
)

/** 相册/拍照 → UCrop 1:1 裁剪 → 回调裁剪后的 Uri（app cache 内临时文件） */
@Composable
private fun rememberAvatarCropActions(vm: MainViewModel, onCropped: (Uri) -> Unit): AvatarCropActions {
    val context = LocalContext.current

    val cropLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            UCrop.getOutput(result.data!!)?.let(onCropped)
        }
    }
    fun startCrop(source: Uri) {
        val dest = File(context.cacheDir, "avatar_crop_${System.currentTimeMillis()}.jpg")
        cropLauncher.launch(
            UCrop.of(source, Uri.fromFile(dest))
                .withAspectRatio(1f, 1f)
                .withMaxResultSize(1024, 1024)
                .getIntent(context)
        )
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) startCrop(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val photoUri = vm.pendingAvatarPhotoUri
        vm.pendingAvatarPhotoUri = null
        if (success && photoUri != null) startCrop(photoUri)
    }
    return AvatarCropActions(
        pickFromGallery = { galleryLauncher.launch("image/*") },
        takePhoto = {
            val photoFile = File(context.cacheDir, "avatar_photo_${System.currentTimeMillis()}.jpg")
            val photoUri = FileProvider.getUriForFile(context, "${context.packageName}.update.fileprovider", photoFile)
            vm.pendingAvatarPhotoUri = photoUri
            cameraLauncher.launch(photoUri)
        }
    )
}

@Composable
fun ProfileView(vm: MainViewModel) {
    var accountDialogVisible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top right settings gear icon button
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = AppColors.panel,
                    modifier = Modifier.size(42.dp).clickable { vm.view = "settings" }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "设置",
                            tint = AppColors.text,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // User info row (Avatar + Name + "普通用户" tag + ID + arrow)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (vm.accountUser == null) accountDialogVisible = true else vm.view = "settings"
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                var showAvatarDialog by remember { mutableStateOf(false) }
                val avatarActions = rememberAvatarCropActions(vm) { uri -> vm.uploadAccountAvatar(uri) }
                Surface(
                    shape = CircleShape,
                    color = AppColors.cyan.copy(alpha = 0.2f),
                    modifier = Modifier
                        .size(68.dp)
                        .clickable(enabled = vm.accountUser != null && !vm.accountBusy) {
                            showAvatarDialog = true
                        }
                ) {
                    AccountAvatar(vm.accountUser?.avatarUrl, Modifier.fillMaxSize())
                }

                if (showAvatarDialog) {
                    Dialog(
                        onDismissRequest = { showAvatarDialog = false },
                        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = AppColors.panel,
                            modifier = Modifier.width(260.dp)
                        ) {
                            Column(modifier = Modifier.padding(vertical = 10.dp)) {
                                Text(
                                    "修改头像",
                                    color = AppColors.text,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                )
                                TextButton(
                                    onClick = {
                                        showAvatarDialog = false
                                        avatarActions.pickFromGallery()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_gallery),
                                        contentDescription = null,
                                        tint = AppColors.cyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("从相册选择", color = AppColors.cyan, fontSize = 15.sp)
                                }
                                TextButton(
                                    onClick = {
                                        showAvatarDialog = false
                                        avatarActions.takePhoto()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_camera),
                                        contentDescription = null,
                                        tint = AppColors.text,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("拍照上传", color = AppColors.text, fontSize = 15.sp)
                                }
                                TextButton(
                                    onClick = { showAvatarDialog = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("取消", color = AppColors.muted, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            vm.accountUser?.nickname?.ifBlank { vm.accountUser?.email?.substringBefore('@').orEmpty() }
                                ?: "登录 / 注册",
                            color = AppColors.text,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = AppColors.panel2,
                        ) {
                            Text(
                                "普通用户",
                                color = AppColors.muted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (vm.accountUser != null) {
                            val digitsOnly = vm.accountUser?.id?.filter { it.isDigit() }.orEmpty()
                            val numericId = if (digitsOnly.length >= 6) digitsOnly.take(6) else (kotlin.math.abs((vm.accountUser?.id ?: "").hashCode()) % 899999 + 100000).toString()
                            "ID: $numericId"
                        } else "登录后同步追番与记录",
                        color = AppColors.muted,
                        fontSize = 13.sp,
                    )
                }
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = "进入设置", tint = AppColors.muted)
            }
            if (vm.accountMessage.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(vm.accountMessage, color = AppColors.muted, fontSize = 12.sp)
            }
        }

        // 4 Action Buttons Row Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AppColors.panel),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { vm.view = "profile_favorites" }
                    ) {
                        Icon(Icons.Default.FavoriteBorder, contentDescription = "我的追番", tint = AppColors.text, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("我的追番", color = AppColors.text, fontSize = 13.sp)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { vm.view = "profile_downloads" }
                    ) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "下载记录", tint = AppColors.text, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("下载记录", color = AppColors.text, fontSize = 13.sp)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { vm.openNotificationsScreen() }
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "消息通知", tint = AppColors.text, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("消息通知", color = AppColors.text, fontSize = 13.sp)
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { vm.view = "settings_feedback" }
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "意见反馈", tint = AppColors.text, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("意见反馈", color = AppColors.text, fontSize = 13.sp)
                    }
                }
            }
        }

        // 观看历史 Section
        item {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("观看历史", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { vm.view = "profile_history" }
                    ) {
                        Text("更多", color = AppColors.muted, fontSize = 14.sp)
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "更多历史", tint = AppColors.muted, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (vm.historyList.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(vm.historyList.take(8)) { history ->
                            val item = history.item
                            Column(
                                modifier = Modifier
                                    .width(155.dp)
                                    .clickable { vm.openMovie(item) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(155.dp)
                                        .height(90.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AppColors.panel2)
                                ) {
                                    AsyncImage(
                                        model = coverRequest(LocalContext.current, item.cover),
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .align(Alignment.Center)
                                            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "播放", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    item.title,
                                    color = AppColors.text,
                                    fontSize = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("暂无观看记录", color = AppColors.muted, fontSize = 14.sp)
                    }
                }
            }
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
    }
    if (accountDialogVisible) {
        AccountDialog(vm) { accountDialogVisible = false }
    }
}

@Composable
private fun NotificationCard(notification: CloudNotification, vm: MainViewModel) {
    val unread = !notification.read
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable {
                if (notification.type == "favorite_update") {
                    val item = runCatching {
                        Gson().fromJson(notification.mediaSnapshot, SourceItem::class.java)
                    }.getOrNull()
                    // 快照缺失/损坏时，回退按作品标识在收藏中匹配打开
                    val fallback = if (item != null && item.id.isNotBlank()) {
                        null
                    } else {
                        vm.favoritesList.firstOrNull { vm.commentMediaKey(it) == notification.mediaKey }
                    }
                    val target = item?.takeIf { it.id.isNotBlank() } ?: fallback
                    if (target != null) {
                        vm.openMovie(target)
                        vm.closeNotificationsScreen()
                    } else {
                        android.widget.Toast.makeText(
                            vm.getApplication(),
                            "作品信息缺失，无法直接打开",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    val favorite = vm.favoritesList.firstOrNull { vm.commentMediaKey(it) == notification.mediaKey }
                    if (favorite != null) {
                        vm.openMovie(favorite)
                        vm.closeNotificationsScreen()
                    } else {
                        android.widget.Toast.makeText(
                            vm.getApplication(),
                            "该作品不在收藏中，无法直接打开",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
        colors = CardDefaults.cardColors(
            containerColor = if (unread) AppColors.cyan.copy(alpha = 0.12f) else AppColors.panel2.copy(alpha = 0.7f)
        ),
        border = if (unread) androidx.compose.foundation.BorderStroke(1.dp, AppColors.cyan.copy(alpha = 0.4f)) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(if (notification.type == "comment_reply") "💬" else "📢", fontSize = 18.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        notification.title,
                        color = AppColors.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (unread) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(AppColors.rose, CircleShape)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            formatRelativeTime(notification.ts),
                            color = AppColors.muted,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(notification.body, color = AppColors.text, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
fun NotificationsScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = {
                    vm.closeNotificationsScreen()
                    vm.view = "profile"
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回我的", tint = AppColors.text, modifier = Modifier.size(20.dp))
            }
            Icon(Icons.Default.Notifications, contentDescription = null, tint = AppColors.cyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text("消息通知", color = AppColors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("实时接收追番更新与互动提醒", color = AppColors.muted, fontSize = 11.sp)
            }
            if (vm.unreadNotificationCount > 0) {
                TextButton(onClick = { vm.closeNotificationsScreen() }) {
                    Text("全部已读", color = AppColors.cyan, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (vm.notifications.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔔", fontSize = 34.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("暂无消息", color = AppColors.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("收藏的番剧更新或有人回复评论时会提醒你", color = AppColors.muted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.notifications.size) { index ->
                    NotificationCard(vm.notifications[index], vm)
                }
            }
        }
    }
}

@Composable
fun AccountAvatar(avatarUrl: String?, modifier: Modifier = Modifier) {
    if (!avatarUrl.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(avatarUrl)
                .crossfade(true)
                .build(),
            contentDescription = "账号头像",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape),
        )
    } else {
        Box(modifier.background(AppColors.panel2), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Person, contentDescription = "默认头像", tint = AppColors.cyan, modifier = Modifier.size(30.dp))
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
    val periodOrder = listOf("近一周", "近一月", "近半年", "更早")
    val groupedHistory = remember(vm.historyList) {
        val now = System.currentTimeMillis()
        periodOrder.mapNotNull { period ->
            vm.historyList
                .filter { historyPeriodLabel(it.timestamp, now) == period }
                .takeIf { it.isNotEmpty() }
                ?.let { period to it }
        }
    }

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
                groupedHistory.forEach { (period, histories) ->
                    item(key = "history_group_$period") {
                        Text(
                            period,
                            color = AppColors.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 5.dp)
                        )
                    }
                    items(
                        items = histories,
                        key = {
                            "${it.item.sourceKey}:${it.item.id}:${it.timestamp}"
                        }
                    ) { history ->
                        // 左滑仅弹出确认，点击"删除"才真正删除，避免误删
                        var pendingDelete by remember { mutableStateOf(false) }
                        val dismissState = rememberDismissState(
                            confirmStateChange = { value ->
                                if (value == DismissValue.DismissedToStart) {
                                    pendingDelete = true
                                }
                                false
                            }
                        )
                        val progress = historyProgressPercent(history.positionMs, history.durationMs)
                        SwipeToDismiss(
                            state = dismissState,
                            directions = setOf(DismissDirection.EndToStart),
                            background = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AppColors.rose.copy(alpha = 0.18f))
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("删除", color = AppColors.rose, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = AppColors.rose
                                        )
                                    }
                                }
                            },
                            dismissContent = {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { vm.openMovie(history.item, history.episodeName) },
                                colors = CardDefaults.cardColors(containerColor = AppColors.panel2)
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(148.dp)
                                                .aspectRatio(16f / 9f)
                                                .clip(RoundedCornerShape(8.dp))
                                        ) {
                                            AsyncImage(
                                                model = coverRequest(LocalContext.current, history.item.cover),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .background(Color(0xFFD1D5DB).copy(alpha = 0.5f))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxHeight()
                                                        .fillMaxWidth(progress.coerceIn(0, 100) / 100f)
                                                        .background(Color(0xFF38BDF8))
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                history.item.title,
                                                color = AppColors.text,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            val isFinished = progress >= 95 || (history.durationMs > 0 && history.positionMs >= history.durationMs - 5000)
                                            val progressLabel = if (isFinished) "${history.episodeName} · 已看完" else "${history.episodeName} · 观看至 $progress%"
                                            Text(
                                                progressLabel,
                                                color = AppColors.cyan,
                                                fontSize = 12.sp
                                            )
                                            Spacer(Modifier.height(5.dp))
                                            val context = LocalContext.current
                                            val config = context.resources.configuration
                                            val isTablet = (config.screenLayout and android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK) >= android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE
                                            val rawModel = android.os.Build.MODEL.orEmpty()
                                            val rawManuf = android.os.Build.MANUFACTURER.orEmpty()
                                            // 云端记录显示其来源设备；旧数据无设备信息时回退为当前设备
                                            val displayDevice = history.deviceName.ifBlank {
                                                formatFriendlyDeviceName(rawManuf, rawModel, isTablet)
                                            }
                                            Text(
                                                "$displayDevice  ${formatHistoryTimestamp(history.timestamp)}",
                                                color = AppColors.muted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                            }
                        )
                        if (pendingDelete) {
                            AlertDialog(
                                onDismissRequest = { pendingDelete = false },
                                title = { Text("删除观看记录") },
                                text = { Text("确定删除「${history.item.title}」的观看记录吗？") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        pendingDelete = false
                                        vm.removeHistory(history.item)
                                    }) {
                                        Text("删除", color = AppColors.rose)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { pendingDelete = false }) { Text("取消") }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(vm: MainViewModel) {
    val historyByTitle = remember(vm.historyList) {
        vm.historyList.associateBy { SourceManager.normalizeTitle(it.item.title) }
    }
    val favoriteGroups = remember(vm.favoritesList, historyByTitle) {
        val now = System.currentTimeMillis()
        val order = listOf("近一周追番", "近一月追番", "近半年追番", "更早追番", "尚未观看")
        val grouped = vm.favoritesList.groupBy { favorite ->
            val watched = historyByTitle[SourceManager.normalizeTitle(favorite.title)]
                ?: return@groupBy "尚未观看"
            "${historyPeriodLabel(watched.timestamp, now)}追番"
        }
        order.mapNotNull { label -> grouped[label]?.takeIf(List<SourceItem>::isNotEmpty)?.let { label to it } }
    }

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
                favoriteGroups.forEach { (group, favorites) ->
                    item(key = "favorite_group_$group") {
                        Text(
                            group,
                            color = AppColors.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp, bottom = 5.dp)
                        )
                    }
                    items(
                        items = favorites,
                        key = { "${it.sourceKey}:${it.id}:${SourceManager.normalizeTitle(it.title)}" }
                    ) { favorite ->
                        val watched = historyByTitle[SourceManager.normalizeTitle(favorite.title)]
                        val progress = watched?.let {
                            historyProgressPercent(it.positionMs, it.durationMs)
                        } ?: 0
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    vm.openMovie(favorite, watched?.episodeName)
                                },
                            colors = CardDefaults.cardColors(containerColor = AppColors.panel)
                        ) {
                            Column {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = coverRequest(LocalContext.current, favorite.cover),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .width(148.dp)
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            favorite.title,
                                            color = AppColors.text,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        val favoriteStatus = resolveMediaStatus(favorite)
                                        Text(
                                            if (favoriteStatus.state == MediaReleaseState.UNKNOWN) {
                                                "已追番"
                                            } else {
                                                favoriteStatus.displayText
                                            },
                                            color = AppColors.muted,
                                            fontSize = 12.sp
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            watched?.let { "◉  看到 ${it.episodeName} · $progress%" }
                                                ?: "◉  尚未开始观看",
                                            color = if (watched == null) AppColors.muted else AppColors.cyan,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (watched != null) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .background(AppColors.cyan.copy(alpha = 0.16f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(progress.coerceIn(0, 100) / 100f)
                                                .background(Color(0xFF7DD3FC))
                                        )
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(end = 10.dp, bottom = 6.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { vm.toggleFavorite(favorite, null) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Text("取消收藏", color = AppColors.rose, fontSize = 12.sp)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchLandingScreen(vm: MainViewModel) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var hotPage by remember { mutableStateOf(0) }
    var guessPage by remember { mutableStateOf(0) }
    val hotPool = remember(vm.lanercRankings, vm.homeSections, vm.lanercSeasons) {
        (
            vm.lanercRankings[RankingKind.HOT].orEmpty().map { it.item } +
                vm.lanercRankings[RankingKind.POPULARITY].orEmpty().map { it.item } +
                vm.lanercSeasons.flatMap { it.entries }.map { it.item } +
                vm.homeSections.flatMap { it.items }
            )
            .filter { it.title.isNotBlank() }
            .distinctBy { SourceManager.normalizeTitle(it.title) }
    }
    val hotPageSize = 9
    val hotPageCount = ((hotPool.size + hotPageSize - 1) / hotPageSize).coerceAtLeast(1)
    val hotSuggestions = remember(hotPool, hotPage) {
        if (hotPool.isEmpty()) emptyList()
        else {
            val start = (hotPage % hotPageCount) * hotPageSize
            hotPool.drop(start).take(hotPageSize).ifEmpty { hotPool.take(hotPageSize) }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
        hotPage = (hotPage + 1) % hotPageCount
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                keyboardController?.hide()
                vm.view = "home"
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回首页", tint = AppColors.text)
            }
            Surface(
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(24.dp),
                color = AppColors.panel,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    AppColors.cyan.copy(alpha = 0.28f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = AppColors.text,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = vm.query,
                        onValueChange = { vm.query = it.take(80) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = TextStyle(color = AppColors.text, fontSize = 15.sp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (vm.query.isNotBlank()) {
                                    keyboardController?.hide()
                                    vm.executeSearch(vm.query)
                                }
                            }
                        ),
                        cursorBrush = SolidColor(AppColors.cyan),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (vm.query.isBlank()) {
                                    Text("搜索动漫、番剧或视频名称", color = AppColors.muted, fontSize = 13.sp)
                                }
                                innerTextField()
                            }
                        }
                    )
                    if (vm.query.isNotBlank()) {
                        IconButton(
                            onClick = { vm.query = "" },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "清空", tint = AppColors.muted)
                        }
                    }
                }
            }
            TextButton(
                onClick = {
                    if (vm.query.isNotBlank()) {
                        keyboardController?.hide()
                        vm.executeSearch(vm.query)
                    }
                }
            ) {
                Text("搜索", color = if (vm.query.isBlank()) AppColors.muted else AppColors.cyan)
            }
        }

        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("历史搜索", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            if (vm.searchHistoryEntries.isNotEmpty()) {
                IconButton(onClick = { vm.clearSearchHistory() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "清空历史搜索", tint = AppColors.muted)
                }
            }
        }
        if (vm.searchHistoryEntries.isEmpty()) {
            Text("暂无搜索记录", color = AppColors.muted, fontSize = 12.sp)
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                vm.searchHistoryEntries.take(12).forEach { history ->
                    InputChip(
                        selected = false,
                        onClick = {
                            keyboardController?.hide()
                            vm.executeSearch(history.query)
                        },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(history.query, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (history.isFrequent) {
                                    Spacer(Modifier.width(5.dp))
                                    Surface(
                                        color = AppColors.rose.copy(alpha = 0.14f),
                                        shape = RoundedCornerShape(5.dp)
                                    ) {
                                        Text(
                                            "常搜",
                                            color = AppColors.rose,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        },
                        trailingIcon = {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "删除${history.query}",
                                tint = AppColors.muted,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { vm.removeSearchHistory(history.query) }
                            )
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("热门搜索", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            TextButton(onClick = { hotPage = (hotPage + 1) % hotPageCount }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = AppColors.cyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("换一批", color = AppColors.cyan, fontSize = 12.sp)
            }
        }
        if (hotSuggestions.isEmpty()) {
            Text("热门内容正在同步", color = AppColors.muted, fontSize = 12.sp)
        } else {
            hotSuggestions.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { item ->
                        Text(
                            item.title,
                            color = AppColors.text,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    keyboardController?.hide()
                                    vm.executeSearch(item.title)
                                }
                                .padding(vertical = 8.dp)
                        )
                    }
                    repeat(3 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // 猜你想看 (Guess You Want To Watch) section
        val guessRecommendations = remember(
            vm.searchHistoryEntries,
            vm.historyList,
            hotPool,
            vm.homeSections,
            vm.lanercRankings,
            vm.libraryItems,
            guessPage
        ) {
            val candidatePool = (
                hotPool +
                vm.homeSections.flatMap { it.items } +
                vm.lanercRankings.values.flatten().map { it.item } +
                vm.libraryItems
            ).filter { it.title.isNotBlank() }
             .distinctBy { SourceManager.normalizeTitle(it.title) }

            buildSearchRecommendations(
                searchHistory = vm.searchHistoryEntries,
                watchHistory = vm.historyList,
                candidates = candidatePool,
                limit = 6,
                rotationSeed = guessPage + (System.currentTimeMillis() / 86_400_000L).toInt()
            )
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("猜你想看", color = AppColors.text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            TextButton(onClick = { guessPage++ }) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = AppColors.cyan, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    when {
                        vm.searchHistoryEntries.isNotEmpty() -> "根据最近搜索 · 换一批"
                        vm.historyList.isNotEmpty() -> "根据观看偏好 · 换一批"
                        else -> "智能热门 · 换一批"
                    },
                    color = AppColors.muted,
                    fontSize = 10.sp
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        if (guessRecommendations.isNotEmpty()) {
            guessRecommendations.chunked(3).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { item ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    keyboardController?.hide()
                                    vm.openMovie(item)
                                },
                            colors = CardDefaults.cardColors(containerColor = AppColors.panel2),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(3f / 4f)
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                ) {
                                    AsyncImage(
                                        model = coverRequest(LocalContext.current, item.cover),
                                        contentDescription = item.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    if (item.score.isNotBlank()) {
                                        Surface(
                                            color = Color.Black.copy(alpha = 0.65f),
                                            shape = RoundedCornerShape(bottomEnd = 6.dp),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                "★ ${item.score}",
                                                color = AppColors.orange,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text(
                                        item.title,
                                        color = AppColors.text,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        item.kind.takeIf { !it.isNullOrBlank() } ?: item.sourceTitle,
                                        color = AppColors.muted,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    repeat(3 - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun SearchResultScreen(vm: MainViewModel) {
    androidx.activity.compose.BackHandler {
        vm.query = ""
        vm.view = "search"
        vm.isSearchActive = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                vm.query = ""
                vm.view = "search"
                vm.isSearchActive = false
            }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回搜索", tint = AppColors.text)
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
private fun LegacySettingsScreen(vm: MainViewModel) {
    var emailInput by remember { mutableStateOf(vm.userEmail) }
    var emailCodeInput by remember { mutableStateOf("") }
    var emailSuccess by remember { mutableStateOf(false) }
    var nicknameInput by remember(vm.accountUser?.nickname) {
        mutableStateOf(vm.accountUser?.nickname.orEmpty())
    }
    val avatarActions = rememberAvatarCropActions(vm) { uri -> vm.uploadAccountAvatar(uri) }

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

        // Account editing implementations are retained for a future account
        // re-enable, but hidden together with the currently disabled account UI.
        if (!TEMP_ACCOUNT_AUTH_DISABLED) {
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
                        onClick = { avatarActions.pickFromGallery() },
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

        if (!TEMP_ACCOUNT_AUTH_DISABLED) {
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
        }

        if (!TEMP_ACCOUNT_AUTH_DISABLED) {
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
}

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var themeDialogVisible by remember { mutableStateOf(false) }
    val themeLabel = when (vm.themeMode) {
        "light" -> "浅色模式"
        "dark" -> "深色模式"
        else -> "跟随系统"
    }

    Column(Modifier.fillMaxSize()) {
        SettingsHeader("设置") { vm.view = "profile" }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SettingsSectionTitle("外观设置") }
            item {
                SettingsCard {
                    SettingsRow(Icons.Default.Star, "色彩主题", themeLabel) {
                        themeDialogVisible = true
                    }
                }
            }
            item { SettingsSectionTitle("账户设置") }
            item {
                SettingsCard {
                    SettingsRow(Icons.Default.Lock, "修改密码") { vm.view = "settings_password" }
                    SettingsDivider()
                    SettingsRow(Icons.Default.Notifications, "修改邮箱", vm.accountUser?.email.orEmpty()) {
                        vm.view = "settings_email"
                    }
                }
            }
            item { SettingsSectionTitle("通用设置") }
            item {
                SettingsCard {
                    SettingsRow(
                        Icons.Default.Settings,
                        if (vm.updateChecking) "正在检查更新" else "检查更新",
                        BuildConfig.VERSION_NAME,
                    ) { vm.checkForAppUpdate(manual = true) }
                    SettingsDivider()
                    SettingsRow(Icons.Default.Edit, "建议/意见反馈") { vm.view = "settings_feedback" }
                    SettingsDivider()
                    SettingsRow(Icons.Default.Info, "免责声明") { vm.view = "settings_disclaimer" }
                    SettingsDivider()
                    SettingsRow(Icons.Default.Share, "分享应用") {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "聚映 · 多源动漫播放器")
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "我正在使用聚映观看动漫，推荐给你：\n${BuildConfig.ACCOUNT_API_BASE}/api/android/latest",
                            )
                        }
                        context.startActivity(Intent.createChooser(share, "分享聚映"))
                    }
                }
            }
            if (vm.updateMessage.isNotBlank()) {
                item { Text(vm.updateMessage, color = AppColors.muted, fontSize = 12.sp) }
            }
            if (vm.accountMessage.isNotBlank()) {
                item { Text(vm.accountMessage, color = AppColors.muted, fontSize = 12.sp) }
            }
            item {
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = { vm.logoutAccount(); vm.view = "profile" },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF25F68)),
                ) { Text("退出登录", color = Color.White, fontSize = 16.sp) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (themeDialogVisible) {
        AlertDialog(
            onDismissRequest = { themeDialogVisible = false },
            title = { Text("选择色彩主题", color = AppColors.text) },
            text = {
                Column {
                    listOf("system" to "跟随系统", "light" to "浅色模式", "dark" to "深色模式").forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.updateThemeMode(option.first); themeDialogVisible = false }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = vm.themeMode == option.first, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(option.second, color = AppColors.text)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { themeDialogVisible = false }) { Text("取消") } },
            containerColor = AppColors.panel,
        )
    }
}

@Composable
private fun SettingsHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = AppColors.muted)
        }
        Text(title, color = AppColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        title,
        color = AppColors.text,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.panel),
    ) { Column(content = content) }
}

@Composable
private fun SettingsDivider() {
    Divider(
        color = AppColors.muted.copy(alpha = 0.12f),
        modifier = Modifier.padding(horizontal = 18.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    trailing: String = "",
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(18.dp))
        Text(title, color = AppColors.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (trailing.isNotBlank()) {
            Text(
                trailing,
                color = AppColors.muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 150.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = AppColors.muted)
    }
}

@Composable
fun ChangePasswordScreen(vm: MainViewModel) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var oldVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    val valid = oldPassword.isNotBlank() && isPasswordStrong(newPassword) && newPassword == confirmPassword

    Column(Modifier.fillMaxSize()) {
        SettingsHeader("修改密码") { vm.view = "settings" }
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp)) {
            SettingsCard {
                PasswordSettingField("原密码", "请输入原密码", oldPassword, oldVisible, { oldPassword = it }, { oldVisible = !oldVisible })
                SettingsDivider()
                PasswordSettingField("新密码", "请输入新密码", newPassword, newVisible, { newPassword = it }, { newVisible = !newVisible })
                SettingsDivider()
                PasswordSettingField("确认密码", "请再次输入新密码", confirmPassword, confirmVisible, { confirmPassword = it }, { confirmVisible = !confirmVisible })
            }
            Spacer(Modifier.height(12.dp))
            Text("密码至少 8 位，并包含大写字母、小写字母、数字、特殊字符中的至少 3 类。", color = AppColors.muted, fontSize = 12.sp)
            if (vm.accountMessage.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(vm.accountMessage, color = AppColors.muted, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { vm.changeAccountPassword(oldPassword, newPassword, confirmPassword) },
                enabled = valid && !vm.accountBusy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF25F68)),
            ) { Text(if (vm.accountBusy) "修改中" else "确认修改", color = Color.White) }
        }
    }
}

@Composable
private fun PasswordSettingField(
    label: String,
    placeholder: String,
    value: String,
    visible: Boolean,
    onValueChange: (String) -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AppColors.muted, fontSize = 14.sp, modifier = Modifier.width(76.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = AppColors.text, fontSize = 15.sp),
            cursorBrush = SolidColor(AppColors.cyan),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.weight(1f).padding(vertical = 15.dp),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = AppColors.muted.copy(alpha = 0.45f), fontSize = 14.sp)
                    field()
                }
            },
        )
        IconButton(onClick = onToggle) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(
                    if (visible) R.drawable.ic_visibility else R.drawable.ic_visibility_off
                ),
                contentDescription = if (visible) "隐藏" else "显示",
                modifier = Modifier.size(20.dp),
                tint = AppColors.muted
            )
        }
    }
}

@Composable
fun ChangeEmailScreen(vm: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    val emailValid = isValidEmail(email) && email.trim() != vm.accountUser?.email

    Column(Modifier.fillMaxSize()) {
        SettingsHeader("修改邮箱") { vm.view = "settings" }
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 18.dp)) {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("邮箱", color = AppColors.muted, fontSize = 14.sp, modifier = Modifier.width(60.dp))
                    BasicTextField(
                        value = email,
                        onValueChange = { email = it.trim() },
                        singleLine = true,
                        textStyle = TextStyle(color = AppColors.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(AppColors.cyan),
                        modifier = Modifier.weight(1f).padding(vertical = 22.dp),
                        decorationBox = { field ->
                            Box {
                                if (email.isEmpty()) Text("请输入新邮箱", color = AppColors.muted.copy(alpha = 0.45f))
                                field()
                            }
                        },
                    )
                    TextButton(
                        onClick = { vm.requestAccountCode(email, "change-email") },
                        enabled = emailValid && !vm.accountBusy,
                    ) { Text("发送验证码") }
                }
                SettingsDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("验证码", color = AppColors.muted, fontSize = 14.sp, modifier = Modifier.width(60.dp))
                    BasicTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        singleLine = true,
                        textStyle = TextStyle(color = AppColors.text, fontSize = 15.sp),
                        cursorBrush = SolidColor(AppColors.cyan),
                        modifier = Modifier.weight(1f).padding(vertical = 22.dp),
                        decorationBox = { field ->
                            Box {
                                if (code.isEmpty()) Text("请输入验证码", color = AppColors.muted.copy(alpha = 0.45f))
                                field()
                            }
                        },
                    )
                }
            }
            if (vm.accountMessage.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(vm.accountMessage, color = AppColors.muted, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { vm.changeAccountEmail(email, code) },
                enabled = emailValid && code.length == 6 && !vm.accountBusy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF25F68)),
            ) { Text(if (vm.accountBusy) "修改中" else "确认修改", color = Color.White) }
        }
    }
}

@Composable
fun FeedbackScreen(vm: MainViewModel) {
    var category by remember { mutableStateOf("suggestion") }
    var text by remember { mutableStateOf("") }
    val categories = listOf("suggestion" to "功能建议", "bug" to "问题反馈", "content" to "内容问题", "account" to "账号问题", "other" to "其他")
    Column(Modifier.fillMaxSize()) {
        SettingsHeader("建议反馈") { vm.view = "settings" }
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Text("反馈类型", color = AppColors.text, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories) { item ->
                    FilterChip(
                        selected = category == item.first,
                        onClick = { category = item.first },
                        label = { Text(item.second) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(2000) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 190.dp),
                placeholder = { Text("请描述你的建议、复现步骤或期望效果（至少 5 个字）") },
                supportingText = { Text("${text.length}/2000") },
            )
            if (vm.accountUser == null) {
                Spacer(Modifier.height(10.dp))
                Text("登录后才能提交反馈，便于后续与你确认处理结果。", color = AppColors.rose, fontSize = 12.sp)
            }
            if (vm.accountMessage.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(vm.accountMessage, color = AppColors.muted, fontSize = 13.sp)
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { vm.submitFeedback(category, text) { text = "" } },
                enabled = vm.accountUser != null && text.trim().length >= 5 && !vm.accountBusy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.cyan),
            ) { Text(if (vm.accountBusy) "提交中" else "提交反馈") }
        }
    }
}

@Composable
fun DisclaimerScreen(vm: MainViewModel) {
    Column(Modifier.fillMaxSize()) {
        SettingsHeader("免责声明") { vm.view = "settings" }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("使用说明与责任边界", color = AppColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("聚映仅提供多来源元数据检索、来源切换和临时播放入口，不在服务器保存、上传或分发任何视频文件。", color = AppColors.text, lineHeight = 23.sp)
            Text("应用展示的封面、标题、简介、剧集与播放地址由对应来源提供，其版权与可用性归权利人及来源方所有。使用者应遵守所在地法律法规、来源网站条款及版权要求。", color = AppColors.text, lineHeight = 23.sp)
            Text("如内容涉及侵权、失效、错误分类或其他问题，请通过“建议反馈”提交作品名称、来源与问题说明，我们会核查并处理应用内索引或入口。", color = AppColors.text, lineHeight = 23.sp)
            Text("网络、来源维护、地区限制、设备兼容性等因素可能导致检索或播放失败；聚映不对第三方来源的持续可用性作保证。", color = AppColors.text, lineHeight = 23.sp)
            Text("继续使用即表示你已阅读并理解上述说明。", color = AppColors.muted, lineHeight = 22.sp)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun WeeklyScheduleScreen(vm: MainViewModel) {
    val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    val beijingTimeZone = remember { java.util.TimeZone.getTimeZone("Asia/Shanghai") }
    val calDay = Calendar.getInstance(beijingTimeZone).get(Calendar.DAY_OF_WEEK)
    val defaultIndex = if (calDay == Calendar.SUNDAY) 6 else (calDay - 2).coerceIn(0, 6)
    var selectedDayIndex by remember { mutableStateOf(defaultIndex) }
    val pool = vm.allPoolItems()
    val localScheduleEntries = remember(pool) { buildScheduleEntries(pool) }
    val scheduleEntries = vm.lanercSchedule.ifEmpty { localScheduleEntries }
    val usingRemoteSchedule = vm.lanercSchedule.isNotEmpty()
    val dayItems = remember(selectedDayIndex, scheduleEntries) {
        scheduleEntries.filter { it.weekdayIndex == selectedDayIndex }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.DateRange,
                contentDescription = null,
                tint = AppColors.cyan,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("本周番剧更新表", color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (vm.lanercDiscoveryMessage.isNotBlank()) {
                        vm.lanercDiscoveryMessage
                    } else if (usingRemoteSchedule) {
                        "本周来源更新 · ${discoveryUpdatedLabel(vm.lanercDiscoveryUpdatedAt)}"
                    } else {
                        "远程周表不可用，展示来源明确提供的本周排期"
                    },
                    color = AppColors.muted,
                    fontSize = 11.sp
                )
            }
            IconButton(
                onClick = { vm.refreshLanercDiscovery(force = true) },
                enabled = !vm.lanercDiscoveryLoading
            ) {
                if (vm.lanercDiscoveryLoading) {
                    LoadingSpinner(Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新本周更新", tint = AppColors.cyan)
                }
            }
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
                            text = buildString {
                                append(if (idx == defaultIndex) "$dayName·今天" else dayName)
                                append('\n')
                                append(weekDateLabel(idx, defaultIndex, beijingTimeZone))
                            },
                            fontWeight = if (selectedDayIndex == idx) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 11.sp
                        )
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (dayItems.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("本周当天暂无更新番剧", color = AppColors.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        vm.lanercDiscoveryError ?: "周表只展示来源本周明确给出的更新时间",
                        color = AppColors.muted,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            ScheduleEntryGrid(dayItems, vm)
        }
    }
}

@Composable
fun SeasonalScheduleScreen(vm: MainViewModel) {
    val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    var selectedSeasonIndex by remember { mutableStateOf(0) }
    val seasons = vm.lanercSeasons
    LaunchedEffect(seasons.size) {
        selectedSeasonIndex = selectedSeasonIndex.coerceIn(0, (seasons.lastIndex).coerceAtLeast(0))
    }
    val selectedSeason = seasons.getOrNull(selectedSeasonIndex)
    val scheduleByTitle = remember(vm.lanercSchedule) {
        vm.lanercSchedule.associateBy { normalizeDiscoveryTitle(it.item.title) }
    }
    val seasonEntries = selectedSeason?.entries.orEmpty()
    val seasonCards = remember(seasonEntries, scheduleByTitle) {
        seasonEntries.map { entry ->
            entry to scheduleByTitle[normalizeDiscoveryTitle(entry.item.title)]
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.view = "home" }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回首页", tint = AppColors.text, modifier = Modifier.size(20.dp))
            }
            Icon(Icons.Default.DateRange, contentDescription = null, tint = AppColors.cyan, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text("季度新番排期", color = AppColors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (vm.lanercDiscoveryMessage.isNotBlank()) {
                        vm.lanercDiscoveryMessage
                    } else {
                        "${selectedSeason?.season?.label ?: "季度数据待同步"} · " +
                            "${seasonEntries.size}部 · ${discoveryUpdatedLabel(vm.lanercDiscoveryUpdatedAt)}"
                    },
                    color = AppColors.muted,
                    fontSize = 11.sp
                )
            }
            IconButton(
                onClick = {
                    vm.refreshLanercDiscovery(force = true)
                },
                enabled = !vm.lanercDiscoveryLoading
            ) {
                if (vm.lanercDiscoveryLoading) {
                    LoadingSpinner(Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新季度排期", tint = AppColors.cyan)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (seasons.isNotEmpty()) {
            ScrollableTabRow(
                selectedTabIndex = selectedSeasonIndex,
                containerColor = AppColors.panel,
                contentColor = AppColors.orange,
                edgePadding = 0.dp
            ) {
                seasons.forEachIndexed { index, season ->
                    Tab(
                        selected = selectedSeasonIndex == index,
                        onClick = { selectedSeasonIndex = index },
                        text = {
                            Text(
                                season.season.label,
                                fontSize = 12.sp,
                                fontWeight = if (selectedSeasonIndex == index) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                }
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (seasonCards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无季度新番排期", color = AppColors.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        vm.lanercDiscoveryError
                            ?: "季度页只展示当前年份已开始季度的作品",
                        color = AppColors.muted,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                gridItems(
                    items = seasonCards,
                    key = { (entry, _) ->
                        "${entry.item.sourceKey}:${entry.item.id}:${entry.sourcePosition}"
                    }
                ) { (entry, schedule) ->
                    Column {
                        MovieCard(entry.item, Modifier.fillMaxWidth()) {
                            vm.openMovie(entry.item)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            if (schedule == null) {
                                entry.sourceSection
                            } else {
                                listOfNotNull(
                                    days.getOrNull(schedule.weekdayIndex),
                                    schedule.airTime,
                                    schedule.episodeText.takeIf(String::isNotBlank)
                                ).joinToString(" · ")
                            },
                            color = AppColors.cyan,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleEntryGrid(entries: List<ScheduleEntry>, vm: MainViewModel) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        gridItems(
            items = entries,
            key = { "${it.item.sourceKey}:${it.item.id}:${it.weekdayIndex}" }
        ) { entry ->
            Column {
                MovieCard(entry.item, Modifier.fillMaxWidth()) {
                    vm.openMovie(entry.item)
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    listOfNotNull(
                        entry.airTime?.let { "$it 更新" },
                        entry.episodeText.takeIf { it.isNotBlank() }
                    ).joinToString(" · ").ifBlank { "更新时间未知" },
                    color = AppColors.cyan,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun weekDateLabel(
    weekdayIndex: Int,
    currentWeekdayIndex: Int,
    timeZone: java.util.TimeZone
): String {
    val calendar = Calendar.getInstance(timeZone)
    calendar.add(Calendar.DAY_OF_YEAR, weekdayIndex - currentWeekdayIndex)
    return java.text.SimpleDateFormat("MM-dd", java.util.Locale.CHINA).apply {
        this.timeZone = timeZone
    }.format(calendar.time)
}

private fun discoveryUpdatedLabel(timestamp: Long): String {
    if (timestamp <= 0L) return "等待同步"
    return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.CHINA).apply {
        timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
    }.format(java.util.Date(timestamp)) + " 同步"
}

fun formatRelativeTime(timestamp: Long): String {
    if (timestamp <= 0L) return "刚刚"
    
    val now = java.util.Calendar.getInstance()
    val time = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val nowYear = now.get(java.util.Calendar.YEAR)
    val timeYear = time.get(java.util.Calendar.YEAR)
    
    val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
    val mdFormat = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.CHINA)
    val ymdFormat = java.text.SimpleDateFormat("yyyy年MM/dd HH:mm", java.util.Locale.CHINA)

    val nowStart = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val timeStart = java.util.Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    
    val daysBetween = ((nowStart.timeInMillis - timeStart.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
    
    return when (daysBetween) {
        0 -> android.text.format.DateUtils.getRelativeTimeSpanString(timestamp).toString()
        1 -> "昨天 ${timeFormat.format(time.time)}"
        2 -> "前天 ${timeFormat.format(time.time)}"
        else -> {
            if (nowYear == timeYear) {
                mdFormat.format(time.time)
            } else {
                ymdFormat.format(time.time)
            }
        }
    }
}

@Composable
fun LeaderboardScreen(vm: MainViewModel) {
    val categories = AnimeRankingCategory.entries
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    val selectedCategory = categories[selectedCategoryIndex]
    LaunchedEffect(selectedCategory) {
        vm.loadAnimeCategoryRanking(selectedCategory)
    }
    // 排行榜只显示一次完整请求完成后的分类快照。首页各源逐步加载时不再重排榜单。
    val rankingEntries = vm.animeCategoryRankings[selectedCategory].orEmpty()

    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = AppColors.orange,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text("动漫分类排行榜", color = AppColors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (vm.lanercDiscoveryMessage.isNotBlank()) {
                        vm.lanercDiscoveryMessage
                    } else {
                        "${selectedCategory.label} · 保留来源热门/推荐顺序 · " +
                            discoveryUpdatedLabel(vm.lanercDiscoveryUpdatedAt)
                    },
                    color = AppColors.muted,
                    fontSize = 11.sp
                )
            }
            IconButton(
                onClick = {
                    vm.loadAnimeCategoryRanking(selectedCategory, force = true)
                },
                enabled = selectedCategory !in vm.animeCategoryRankingLoading
            ) {
                if (
                    selectedCategory in vm.animeCategoryRankingLoading
                ) {
                    LoadingSpinner(Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新分类排行榜", tint = AppColors.cyan)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        ScrollableTabRow(
            selectedTabIndex = selectedCategoryIndex,
            containerColor = AppColors.panel,
            contentColor = AppColors.cyan,
            edgePadding = 0.dp
        ) {
            categories.forEachIndexed { index, category ->
                Tab(
                    selected = selectedCategoryIndex == index,
                    onClick = { selectedCategoryIndex = index },
                    text = {
                        Text(
                            category.label,
                            fontSize = 12.sp,
                            fontWeight = if (selectedCategoryIndex == index) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (
            rankingEntries.isEmpty() &&
            selectedCategory in vm.animeCategoryRankingLoading
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LoadingSpinner(Modifier.size(30.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "正在从启用的视频源获取${selectedCategory.label}…",
                        color = AppColors.muted,
                        fontSize = 13.sp
                    )
                }
            }
        } else if (rankingEntries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${selectedCategory.label}暂无榜单内容", color = AppColors.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "只展示来源能明确区分地区和TV/剧场版的动漫",
                        color = AppColors.muted,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            RankingEntriesList(rankingEntries, vm)
        }
    }
}

@Composable
fun SeasonalRankingScreen(vm: MainViewModel) {
    var selectedSeasonIndex by remember { mutableStateOf(0) }
    val seasons = vm.lanercSeasons
    LaunchedEffect(seasons.size) {
        selectedSeasonIndex = selectedSeasonIndex.coerceIn(0, (seasons.lastIndex).coerceAtLeast(0))
    }
    val selectedSeason = seasons.getOrNull(selectedSeasonIndex)

    val remoteRankingEntries = selectedSeason?.entries.orEmpty()
    val rankingEntries = remoteRankingEntries
    val usingRemoteRanking = remoteRankingEntries.isNotEmpty()

    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.view = "home" }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回首页", tint = AppColors.text, modifier = Modifier.size(20.dp))
            }
            Icon(Icons.Default.Star, contentDescription = null, tint = AppColors.orange, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Column(Modifier.weight(1f)) {
                Text("季度新番榜", color = AppColors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (vm.lanercDiscoveryMessage.isNotBlank()) {
                        vm.lanercDiscoveryMessage
                    } else if (usingRemoteRanking) {
                        "${selectedSeason?.season?.label} · 当前年份 · " +
                            discoveryUpdatedLabel(vm.lanercDiscoveryUpdatedAt)
                    } else {
                        "季度新番数据暂不可用"
                    },
                    color = AppColors.muted,
                    fontSize = 11.sp
                )
            }
            IconButton(
                onClick = {
                    vm.refreshLanercDiscovery(force = true)
                },
                enabled = !vm.lanercDiscoveryLoading
            ) {
                if (vm.lanercDiscoveryLoading) {
                    LoadingSpinner(Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新排行榜", tint = AppColors.cyan)
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        if (seasons.isNotEmpty()) {
            TabRow(
                selectedTabIndex = selectedSeasonIndex,
                containerColor = AppColors.panel,
                contentColor = AppColors.cyan
            ) {
                seasons.forEachIndexed { index, season ->
                    Tab(
                        selected = selectedSeasonIndex == index,
                        onClick = { selectedSeasonIndex = index },
                        text = {
                            Text(
                                season.season.label,
                                fontWeight = if (selectedSeasonIndex == index) {
                                    FontWeight.Bold
                                } else {
                                    FontWeight.Normal
                                },
                                fontSize = 13.sp
                            )
                        }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        if (rankingEntries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂无可验证的榜单数据", color = AppColors.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        vm.lanercDiscoveryError ?: "当前来源没有提供该榜单或评分",
                        color = AppColors.muted,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                if (rankingEntries.size >= 3) {
                    item(key = "ranking_podium") {
                        RankingPodium(rankingEntries.take(3)) { vm.openMovie(it) }
                        Text(
                            "完整榜单",
                            color = AppColors.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                        )
                    }
                }
                val listEntries = if (rankingEntries.size >= 3) rankingEntries.drop(3) else rankingEntries
                items(listEntries.size) { listIndex ->
                    val entry = listEntries[listIndex]
                    val item = entry.item
                    val rankIdx = if (rankingEntries.size >= 3) listIndex + 3 else listIndex
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
                                val validScore = item.score.toDoubleOrNull()?.takeIf { it > 0.0 }
                                if (validScore != null) {
                                    Text(
                                        "★ ${item.score}",
                                        color = AppColors.orange,
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    listOf(item.year, item.kind).filter { it.isNotBlank() }.joinToString(" · ")
                                        .ifBlank { "作品信息待补充" },
                                    color = AppColors.muted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${entry.sourceSection} · 来源榜第${entry.sourcePosition}位",
                                    color = AppColors.cyan,
                                    fontSize = 10.sp,
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
private fun RankingEntriesList(
    rankingEntries: List<SourceRankingEntry>,
    vm: MainViewModel
) {
    LazyColumn(Modifier.fillMaxSize()) {
        if (rankingEntries.size >= 3) {
            item(key = "ranking_podium") {
                RankingPodium(rankingEntries.take(3)) { vm.openMovie(it) }
                Text(
                    "完整榜单",
                    color = AppColors.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
                )
            }
        }
        val listEntries = if (rankingEntries.size >= 3) rankingEntries.drop(3) else rankingEntries
        items(listEntries.size) { listIndex ->
            val entry = listEntries[listIndex]
            val item = entry.item
            val rankIdx = if (rankingEntries.size >= 3) listIndex + 3 else listIndex
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

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { vm.openMovie(item) },
                colors = CardDefaults.cardColors(
                    containerColor = if (rankNum <= 3) AppColors.panel2 else AppColors.panel
                )
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
                            Text(
                                badgeIcon,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = badgeColor
                            )
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
                        Text(
                            item.title,
                            color = AppColors.text,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(3.dp))
                        val validScore = item.score.toDoubleOrNull()?.takeIf { it > 0.0 }
                        if (validScore != null) {
                            Text(
                                "★ ${item.score}",
                                color = AppColors.orange,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            listOf(item.year, item.kind)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                                .ifBlank { "作品信息待补充" },
                            color = AppColors.muted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${entry.sourceSection} · 来源第${entry.sourcePosition}位",
                            color = AppColors.cyan,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RankingPodium(
    entries: List<SourceRankingEntry>,
    onOpen: (SourceItem) -> Unit
) {
    val slotOrder = listOf(1, 0, 2)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.panel)
            .padding(horizontal = 4.dp, vertical = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        slotOrder.forEach { entryIndex ->
            val entry = entries[entryIndex]
            val rank = entryIndex + 1
            val badgeColor = when (rank) {
                1 -> Color(0xFFFFD54F)
                2 -> Color(0xFFB0BEC5)
                else -> Color(0xFFCD7F32)
            }
            val cardWidth = if (rank == 1) 92.dp else 76.dp
            val cardHeight = if (rank == 1) 128.dp else 106.dp
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .offset(y = if (rank == 1) (-8).dp else 0.dp)
                    .clickable { onOpen(entry.item) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(shape = CircleShape, color = badgeColor.copy(alpha = 0.2f)) {
                    Text(
                        "#$rank",
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                AsyncImage(
                    model = coverRequest(LocalContext.current, entry.item.cover),
                    contentDescription = entry.item.title,
                    modifier = Modifier.size(cardWidth, cardHeight).clip(RoundedCornerShape(9.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    entry.item.title,
                    color = AppColors.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp)
                )
                val validScore = entry.item.score.toDoubleOrNull()?.takeIf { it > 0.0 }
                if (validScore != null) {
                    Text(
                        "★ ${entry.item.score}",
                        color = AppColors.orange,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
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

// 提取剧集名中的集数用于比较（"第3集"/"03话"→"3"）；无数字时原样返回
private fun episodeNumber(name: String): String {
    return Regex("\\d+").find(name)?.value ?: name.trim()
}

/** 详情列表的最后一集集数（收藏时作为追番提醒基线） */
private fun latestEpisodeNum(detail: DetailResult): Int? =
    detail.episodes.lastOrNull()?.name?.trim()?.let { episodeNumber(it).toIntOrNull() }

private val COMMENT_EMOJIS = listOf(
    "😀", "😁", "😂", "🤣", "😅", "😊", "😍", "🥰", "😘", "😜",
    "🤪", "😎", "🤩", "🥳", "😭", "😤", "😡", "🥺", "😱", "🤔",
    "🤗", "🤫", "😴", "👍", "👎", "👏", "🙏", "💪", "🔥", "❤️",
    "💖", "💔", "⭐", "✨", "🎉", "🎊", "🍀", "🌸", "🍺", "🍜",
)

/** 评论图片压缩：采样到最长边 1280px、JPEG 80%，输出到 cacheDir */
private fun compressCommentImage(context: android.content.Context, uri: Uri): File? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // inJustDecodeBounds 模式下 decode 返回 null 是正常的（只读尺寸），不能当失败处理
        val canRead = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
            true
        } ?: false
        if (!canRead) return null
        var sample = 1
        while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        } ?: return null
        val out = File(context.cacheDir, "comment_image_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { fos -> bitmap.compress(Bitmap.CompressFormat.JPEG, 80, fos) }
        out
    } catch (_: Exception) {
        null
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
fun HomeCarouselBanner(
    items: List<SourceItem>,
    onSelect: (SourceItem) -> Unit,
    label: String = "精选内容"
) {
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
                                    label,
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
    "运动", "音乐", "偶像", "职场", "历史", "美食", "萌系", "百合", "泡面番",
    "美少女", "少女", "少年", "家庭", "恐怖", "神魔", "动作", "喜剧", "爱情",
    "战争", "犯罪", "灾难", "儿童", "教育"
)

private fun resolveAnimeGenres(item: SourceItem): List<String> {
    val orderedMatches = buildList {
        val rawValues = item.tags + item.kind
            .split(Regex("[,，、/|·\\s]+"))
            .filter { it.isNotBlank() }
        rawValues.forEach { raw ->
            val exact = standardAnimeGenres.firstOrNull { raw == it }
            if (exact != null) {
                if (exact !in this) add(exact)
            } else {
                standardAnimeGenres.forEach { genre ->
                    if (raw.contains(genre) && genre !in this) add(genre)
                }
            }
        }
        if (isEmpty()) {
            val fallbackScope = "${item.kind} ${item.description} ${item.title}"
            val candidates = standardAnimeGenres.filter { fallbackScope.contains(it) }
            candidates
                .filter { genre -> candidates.none { other -> other != genre && other.contains(genre) } }
                .forEach { genre ->
                    if (genre !in this) add(genre)
                }
            }
    }
    return orderedMatches.distinct()
}

private fun resolveCardGenre(item: SourceItem): String {
    val matches = resolveAnimeGenres(item)
    if (matches.isNotEmpty()) {
        val shown = matches.take(4).joinToString(" ")
        return if (matches.size > 4) "$shown ..." else shown
    }
    val cleanKind = item.kind.replace(Regex("(全部|首页|推荐|热门|最新|分类)"), "").trim()
    return when {
        cleanKind.isNotBlank() -> cleanKind
        item.year.isNotBlank() -> "${item.year} 动漫"
        else -> "动漫"
    }
}

private fun resolveCardStatus(item: SourceItem): String {
    val summary = resolveMediaStatus(item)
    if (summary.state != MediaReleaseState.UNKNOWN) return summary.displayText
    return summary.episodeText
        .ifBlank { item.status.trim().take(24) }
        .ifBlank { item.year.takeIf(String::isNotBlank)?.let { "${it}年作品" }.orEmpty() }
        .ifBlank { "已收录" }
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

fun formatFriendlyDeviceName(manufacturer: String, model: String, isTablet: Boolean): String {
    val m = model.uppercase().trim()
    val manuf = manufacturer.lowercase().trim()
    return when {
        m.contains("24122") || m.contains("24128") || m.contains("K80") || (m.contains("24") && manuf.contains("xiaomi")) -> "红米 K80"
        m.contains("K70") || m.contains("23113") -> "红米 K70"
        m.contains("K60") || m.contains("23013") -> "红米 K60"
        m.contains("24031") || m.contains("23127") || (m.contains("14") && manuf.contains("xiaomi")) -> "小米 14"
        m.contains("15") && manuf.contains("xiaomi") -> "小米 15"
        manuf.contains("xiaomi") || manuf.contains("redmi") -> if (m.contains("REDMI") || m.contains("24")) "红米 K80" else "小米 手机"

        m.contains("V2309") || m.contains("V2415") || m.contains("X300") -> "vivo X300"
        m.contains("X200") || m.contains("V2405") -> "vivo X200"
        m.contains("X100") || m.contains("V2307") -> "vivo X100"
        manuf.contains("vivo") || manuf.contains("iqoo") -> if (m.contains("IQOO")) "iQOO 13" else "vivo X300"

        m.contains("CPH2609") || m.contains("PKB110") || m.contains("X8") -> "OPPO Find X8"
        m.contains("FIND") || m.contains("X7") -> "OPPO Find X7"
        m.contains("RENO") || m.contains("PJG110") -> "OPPO Reno12"
        manuf.contains("oppo") || manuf.contains("realme") || manuf.contains("oneplus") -> if (m.contains("ONEPLUS")) "一加 13" else "OPPO Find X8"

        manuf.contains("huawei") -> "华为 Mate 60"
        manuf.contains("honor") -> "荣耀 Magic 6"
        manuf.contains("samsung") -> "三星 Galaxy S24"

        else -> {
            val name = if (model.lowercase().startsWith(manufacturer.lowercase())) model else "$manufacturer $model".trim()
            name.ifBlank { if (isTablet) "安卓平板" else "安卓手机" }
        }
    }
}
