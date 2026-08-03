
package com.juying.app.ui

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.os.Build
import android.view.SurfaceView
import android.view.TextureView
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import com.juying.app.MainActivity
import com.juying.app.source.StorageManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.VideoSize
import androidx.media3.common.util.Size as Media3Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.juying.app.AppColors
import com.juying.app.R
import com.juying.app.source.Episode
import com.juying.app.source.QualityOption
import com.juying.app.source.SourceLogManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
private const val PLAYER_DIAG_TAG = "JuyingPlayerDiag"
// TEMP: 弹幕发送暂时关闭；保留输入弹窗实现，后续接入授权外部弹幕 API 时恢复。
private const val TEMP_DANMAKU_POSTING_DISABLED = true

data class DanmakuItem(
    val id: Long = System.nanoTime(),
    val text: String,
    val color: Color = Color.White,
    val lineIndex: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}

private fun getScreenBrightness(activity: Activity?): Float {
    activity ?: return 0.5f
    val b = activity.window.attributes.screenBrightness
    return if (b in 0.05f..1f) b else 0.5f
}

private fun adjustScreenBrightness(activity: Activity?, delta: Float) {
    activity ?: return
    val attributes = activity.window.attributes
    val current = getScreenBrightness(activity)
    attributes.screenBrightness = (current + delta).coerceIn(0.05f, 1f)
    activity.window.attributes = attributes
}

// Custom UI Vector Icons
@Composable
private fun PipIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Box(
        modifier = modifier
            .size(20.dp)
            .border(1.5.dp, tint, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.BottomEnd
    ) {
        Box(
            modifier = Modifier
                .size(9.dp, 7.dp)
                .background(tint, RoundedCornerShape(1.dp))
        )
    }
}

@Composable
private fun TvCastIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Box(
        modifier = modifier
            .size(20.dp)
            .border(1.5.dp, tint, RoundedCornerShape(3.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text("TV", color = tint, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun FullscreenIcon(modifier: Modifier = Modifier, isFull: Boolean, tint: Color = Color.White) {
    Canvas(modifier = modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        val c = 5.dp.toPx()
        val cap = StrokeCap.Round
        
        if (isFull) {
            // Exit fullscreen (arrows in)
            drawLine(tint, Offset(0f, c), Offset(c, c), strokeWidth = stroke, cap = cap)
            drawLine(tint, Offset(c, 0f), Offset(c, c), strokeWidth = stroke, cap = cap)
            drawLine(tint, Offset(w, c), Offset(w - c, c), strokeWidth = stroke, cap = cap)
            drawLine(tint, Offset(w - c, 0f), Offset(w - c, c), strokeWidth = stroke, cap = cap)
            drawLine(tint, Offset(0f, h - c), Offset(c, h - c), strokeWidth = stroke, cap = cap)
            drawLine(tint, Offset(c, h), Offset(c, h - c), strokeWidth = stroke, cap = cap)
            drawLine(tint, Offset(w, h - c), Offset(w - c, h - c), strokeWidth = stroke, cap = cap)
            drawLine(tint, Offset(w - c, h), Offset(w - c, h - c), strokeWidth = stroke, cap = cap)
        } else {
            // Enter fullscreen (arrows out)
            drawLine(tint, Offset(0f, 0f), Offset(c, 0f), strokeWidth = stroke, cap = cap)
            drawLine(tint, Offset(0f, 0f), Offset(0f, c), strokeWidth = stroke, cap = cap)
            drawLine(tint, Offset(w, 0f), Offset(w - c, 0f), strokeWidth = stroke, cap = cap)
            drawLine(tint, Offset(w, 0f), Offset(w, c), strokeWidth = stroke, cap = cap)
            drawLine(tint, Offset(0f, h), Offset(c, h), strokeWidth = stroke, cap = cap)
            drawLine(tint, Offset(0f, h), Offset(0f, h - c), strokeWidth = stroke, cap = cap)
            drawLine(tint, Offset(w, h), Offset(w - c, h), strokeWidth = stroke, cap = cap)
            drawLine(tint, Offset(w, h), Offset(w, h - c), strokeWidth = stroke, cap = cap)
        }
    }
}

@Composable
private fun SkipNextIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Box(modifier = Modifier.size(2.dp, 12.dp).background(tint))
    }
}

@Composable
private fun SkipPreviousIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(2.dp, 12.dp).background(tint))
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp).graphicsLayer { rotationZ = 180f }
        )
    }
}

@Composable
private fun PauseIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(4.dp, 14.dp).background(tint, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.size(4.dp, 14.dp).background(tint, RoundedCornerShape(1.dp)))
    }
}

@Composable
private fun SunIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Canvas(modifier = modifier.size(22.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.width * 0.22f
        drawCircle(
            color = tint,
            radius = radius,
            center = center,
            style = Stroke(width = 1.8.dp.toPx())
        )
        val rayInner = radius + 2.5.dp.toPx()
        val rayOuter = rayInner + 3.5.dp.toPx()
        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 45).toDouble())
            val start = Offset(
                center.x + (rayInner * Math.cos(angle)).toFloat(),
                center.y + (rayInner * Math.sin(angle)).toFloat()
            )
            val end = Offset(
                center.x + (rayOuter * Math.cos(angle)).toFloat(),
                center.y + (rayOuter * Math.sin(angle)).toFloat()
            )
            drawLine(
                color = tint,
                start = start,
                end = end,
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun SpeakerVolumeIcon(modifier: Modifier = Modifier, value: Int, tint: Color = Color.White) {
    Canvas(modifier = modifier.size(22.dp)) {
        val stroke = 1.8.dp.toPx()
        val speakerPath = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.38f)
            lineTo(size.width * 0.35f, size.height * 0.38f)
            lineTo(size.width * 0.55f, size.height * 0.2f)
            lineTo(size.width * 0.55f, size.height * 0.8f)
            lineTo(size.width * 0.35f, size.height * 0.62f)
            lineTo(size.width * 0.15f, size.height * 0.62f)
            close()
        }
        drawPath(path = speakerPath, color = tint, style = Stroke(width = stroke, join = StrokeJoin.Round))

        if (value > 0) {
            drawArc(
                color = tint,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(size.width * 0.42f, size.height * 0.28f),
                size = Size(size.width * 0.38f, size.height * 0.44f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = tint,
                startAngle = -50f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(size.width * 0.3f, size.height * 0.18f),
                size = Size(size.width * 0.58f, size.height * 0.64f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        } else {
            drawLine(
                color = tint,
                start = Offset(size.width * 0.65f, size.height * 0.35f),
                end = Offset(size.width * 0.85f, size.height * 0.65f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(size.width * 0.85f, size.height * 0.35f),
                end = Offset(size.width * 0.65f, size.height * 0.65f),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun HudIcon(type: String, value: Int) {
    when (type) {
        "brightness" -> SunIcon(tint = Color.White)
        "volume" -> SpeakerVolumeIcon(value = value, tint = Color.White)
        "seek" -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        }
        "speed" -> Box(
            modifier = Modifier.size(20.dp).border(1.5.dp, Color.White, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("X", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EmbeddedVideoPlayer(
    url: String,
    type: String,
    headers: Map<String, String>?,
    referer: String?,
    qualities: List<QualityOption> = emptyList(),
    title: String,
    episodeName: String,
    episodes: List<Episode> = emptyList(),
    currentEpisodeIndex: Int = 0,
    onSelectEpisode: ((Int) -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    onPrevEpisode: (() -> Unit)? = null,
    onBack: () -> Unit,
    onError: () -> Unit = {},
    onLikelyTranscodingPlaceholder: () -> Unit = {},
    onFullscreenChanged: (Boolean) -> Unit = {},
    onNavigateToLogin: (() -> Unit)? = null,
    onPlaybackProgress: (Long, Long) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val storageManager = remember(context) { StorageManager(context) }

    var longPressSpeed by remember { mutableStateOf(storageManager.getLongPressSpeed()) }
    var customSpeed by remember { mutableStateOf(storageManager.getCustomSpeed()) }
    var currentSpeed by remember { mutableStateOf(1.0f) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playError by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf("超分辨率") }
    var anime4kMode by remember { mutableStateOf(Anime4kMode.OFF) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showDanmakuSettings by remember { mutableStateOf(false) }
    var showCustomSpeedDialog by remember { mutableStateOf(false) }
    var showLongPressSpeedDialog by remember { mutableStateOf(false) }
    var showLoginPromptDialog by remember { mutableStateOf(false) }
    var isSpeedLocked by remember { mutableStateOf(false) }
    var danmakuOpacity by remember { mutableStateOf(0.85f) }
    var danmakuDraft by remember { mutableStateOf("") }
    val lowRamDevice = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }
    val maxSpeed = if (lowRamDevice || anime4kMode != Anime4kMode.OFF) 2.0f else 3.0f
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val sentDanmaku = remember { mutableStateListOf<String>() }

    // The player is prepared paused and only starts after the output surface
    // is ready. Do not show a false pause state before Media3 starts.
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf(0f) }
    var lastDragTime by remember { mutableStateOf(0L) }
    var resumeAfterSliderSeek by remember { mutableStateOf(true) }
    var dragGestureActive by remember { mutableStateOf(false) }
    var shortPlaceholderReported by remember(url) { mutableStateOf(false) }
    val latestPlaceholderCallback by rememberUpdatedState(onLikelyTranscodingPlaceholder)

    var isFullscreen by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var danmakuEnabled by remember { mutableStateOf(true) }
    var blockBottom by remember { mutableStateOf(false) }
    var blockTop by remember { mutableStateOf(false) }
    var blockScroll by remember { mutableStateOf(false) }
    var blockColor by remember { mutableStateOf(false) }
    var danmakuAreaRatio by remember { mutableStateOf(1.0f) }
    var danmakuFontSizeScale by remember { mutableStateOf(1.0f) }

    val adaptiveStream = remember(url, type) {
        val lowerUrl = url.lowercase()
        val lowerType = type.lowercase()
        lowerUrl.contains(".m3u8") ||
            lowerType.contains("m3u8") ||
            lowerType.contains("hls") ||
            lowerType.contains("application/x-mpegurl")
    }
    val qualityChoices = remember(qualities, adaptiveStream) {
        if (qualities.isNotEmpty()) {
            listOf("Auto") + qualities.map { it.name }.filter { it.isNotBlank() }.distinct()
        } else if (adaptiveStream) {
            listOf("Auto", "4K", "1080p", "720p", "480p")
        } else {
            listOf("Auto")
        }
    }

    var showSpeedMenu by remember { mutableStateOf(false) }
    var showRatioMenu by remember { mutableStateOf(false) }
    var showEpisodeDrawer by remember { mutableStateOf(false) }
    var showDanmakuInput by remember { mutableStateOf(false) }

    val activeDanmakus = remember { mutableStateListOf<DanmakuItem>() }
    val danmakuColorPalette = remember {
        listOf(
            Color(0xFFFFFFFF) to "白色",
            Color(0xFFFF5252) to "红色",
            Color(0xFFFFD600) to "黄色",
            Color(0xFF00E676) to "绿色",
            Color(0xFF40C4FF) to "蓝色",
            Color(0xFFE040FB) to "紫色",
            Color(0xFFFF9100) to "橙色"
        )
    }
    var selectedDanmakuColor by remember { mutableStateOf(danmakuColorPalette[0].first) }

    // 长按手势状态：记录长按前的倍速（松手恢复），以及左侧快退回放的协程
    var speedBeforeHold by remember { mutableStateOf(1.0f) }
    var rewindJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var holdGestureActive by remember { mutableStateOf(false) }

    val playerScope = rememberCoroutineScope()
    var gestureHudType by remember { mutableStateOf("") }
    var gestureHudValue by remember { mutableStateOf(0) }
    var gestureHudText by remember { mutableStateOf("") }
    var gestureHudJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var seekPreviewPosition by remember(url) { mutableStateOf(0L) }
    var gestureSeekOriginPosition by remember { mutableStateOf(0L) }
    var scanlineProgress by remember { mutableStateOf(0f) }
    var isScanlineActive by remember { mutableStateOf(false) }
    val latestProgressCallback by rememberUpdatedState(onPlaybackProgress)

    val triggerHud = { type: String, value: Int, text: String, durationMs: Long ->
        gestureHudType = type
        gestureHudValue = value
        gestureHudText = text
        gestureHudJob?.cancel()
        if (durationMs > 0L) {
            gestureHudJob = playerScope.launch {
                delay(durationMs)
                gestureHudText = ""
            }
        }
    }
    val stopHoldGesture = {
        rewindJob?.cancel()
        rewindJob = null
        if (holdGestureActive) {
            holdGestureActive = false
            if (currentSpeed != speedBeforeHold) {
                currentSpeed = speedBeforeHold
            }
            gestureHudText = ""
        }
    }

    // Unified fullscreen window handling: orientation + system bars + display cutout.
    // SHORT_EDGES lets the window extend into the cutout area on punch-hole devices,
    // otherwise the system letterboxes the player and control bars can be pushed off-screen.
    val applyFullscreen: (Boolean) -> Unit = { targetFullscreen ->
        isFullscreen = targetFullscreen
        onFullscreenChanged(targetFullscreen)
        activity?.let { act ->
            val window = act.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (targetFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val lp = window.attributes
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    window.attributes = lp
                }
            } else {
                // Give orientation control back to the user/system. A natural
                // landscape rotation uses the app's split player layout.
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                controller.show(WindowInsetsCompat.Type.systemBars())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val lp = window.attributes
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    window.attributes = lp
                }
            }
        }
    }
    val toggleFullscreen = { applyFullscreen(!isFullscreen) }

    // Safety net: if the player leaves composition while still fullscreen
    // (e.g. external navigation), always restore portrait/system bars/cutout mode.
    DisposableEffect(Unit) {
        onDispose {
            if (isFullscreen) applyFullscreen(false)
        }
    }

    if (isFullscreen) {
        BackHandler {
            toggleFullscreen()
        }
    }

    val playbackKey = remember(url, type, headers, referer) {
        listOf(url, type, referer ?: "", headers?.toSortedMap()?.entries?.joinToString(";") { "${it.key}=${it.value}" } ?: "").joinToString("|")
    }
    val requestHeaders = remember(playbackKey) {
        mutableMapOf<String, String>().apply {
            referer?.let {
                if (it.isNotBlank() && !it.equals("never", ignoreCase = true)) {
                    put("Referer", it)
                }
            }
            headers?.forEach { (k, v) ->
                if (v.isNotBlank() && !(k.equals("Referer", true) && v.equals("never", true))) {
                    put(k, v)
                }
            }
        }
    }
    val httpDataSourceFactory = remember(playbackKey) {
        val customUa = requestHeaders.entries.firstOrNull {
            it.key.equals("user-agent", ignoreCase = true)
        }?.value
        DefaultHttpDataSource.Factory().apply {
            setUserAgent(customUa ?: BROWSER_UA)
            setAllowCrossProtocolRedirects(true)
            setConnectTimeoutMs(15000)
            setReadTimeoutMs(15000)
            if (requestHeaders.isNotEmpty()) {
                setDefaultRequestProperties(requestHeaders)
            }
        }
    }
    val dataSourceFactory = remember(playbackKey) {
        // DefaultHttpDataSource cannot open device files. Wrapping it in
        // DefaultDataSource keeps remote request headers while adding file://
        // and content:// support for completed offline downloads.
        DefaultDataSource.Factory(context, httpDataSourceFactory)
    }
    fun createMediaSource(targetUrl: String, targetType: String): MediaSource {
        val cleanUrl = targetUrl.trim()
        val mediaItem = MediaItem.fromUri(Uri.parse(cleanUrl))
        val targetIsHls = cleanUrl.lowercase().contains(".m3u8") ||
            targetType.lowercase().contains("m3u8") ||
            targetType.lowercase().contains("hls") ||
            targetType.lowercase().contains("application/x-mpegurl")
        return if (targetIsHls) {
            HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
        }
    }

    var boundPlayerView by remember(playbackKey) { mutableStateOf<PlayerView?>(null) }

    val exoPlayer = remember(playbackKey) {

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30_000,  // keep at least 30 seconds buffered
                120_000, // allow up to 2 minutes on unstable CDN links
                2_500,
                5_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)

        ExoPlayer.Builder(context, renderersFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
            // Media3 requires the effects pipeline to be initialized before
            // prepare(). The previous first call happened from LaunchedEffect
            // after prepare(), which could race the initial video surface.
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
            setVideoEffects(emptyList())
            setMediaSource(createMediaSource(url, type))
            prepare()
            // AndroidView below starts playback only after its actual output
            // surface is attached, available and has non-zero dimensions.
            playWhenReady = true
            addListener(object : Player.Listener {
                private var renderedBeforeVideoSize = false

                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                    android.util.Log.i(
                        PLAYER_DIAG_TAG,
                        "isPlaying=$playing state=$playbackState videoSize=${videoSize.width}x${videoSize.height}"
                    )
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    val videoGroups = currentTracks.groups.count { it.type == C.TRACK_TYPE_VIDEO }
                    android.util.Log.i(
                        PLAYER_DIAG_TAG,
                        "state=$playbackState playWhenReady=$playWhenReady videoGroups=$videoGroups"
                    )
                    if (playbackState == Player.STATE_READY) {
                        val mediaDuration = this@apply.duration.coerceAtLeast(0L)
                        duration = duration.coerceAtLeast(mediaDuration)
                    }
                }

                override fun onRenderedFirstFrame() {
                    // This is evidence, not a start trigger: Media3 reports
                    // that a frame reached its configured video output.
                    renderedBeforeVideoSize = videoSize.width <= 0 || videoSize.height <= 0
                    android.util.Log.i(
                        PLAYER_DIAG_TAG,
                        "first-frame-rendered position=${currentPosition}ms size=${videoSize.width}x${videoSize.height}"
                    )
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    android.util.Log.i(
                        PLAYER_DIAG_TAG,
                        "video-size=${videoSize.width}x${videoSize.height} ratio=${videoSize.pixelWidthHeightRatio}"
                    )
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        val player = this@apply
                        boundPlayerView?.post {
                            val view = boundPlayerView ?: return@post
                            view.videoSurfaceView?.requestLayout()
                            view.requestLayout()
                            view.invalidate()

                            // Some decoders report a rendered frame while its
                            // size is still 0x0. That frame never becomes
                            // visible until fullscreen rebinds the surface.
                            // Rebind once when the real dimensions arrive so
                            // Media3 renders a correctly-sized frame now.
                            if (renderedBeforeVideoSize) {
                                android.util.Log.i(
                                    PLAYER_DIAG_TAG,
                                    "redraw-after-zero-size-frame view=${view.width}x${view.height}"
                                )
                                renderedBeforeVideoSize = false
                                // The effects video sink rendered once while
                                // its output resolution was still 0x0. Notify
                                // only the video renderer of the real surface
                                // size, then re-seek the current position to
                                // draw a fresh frame. This keeps PlayerView
                                // attached and preserves the playback clock.
                                val outputWidth = view.videoSurfaceView?.width
                                    ?.takeIf { it > 0 } ?: view.width
                                val outputHeight = view.videoSurfaceView?.height
                                    ?.takeIf { it > 0 } ?: view.height
                                repeat(player.rendererCount) { rendererIndex ->
                                    if (player.getRendererType(rendererIndex) == C.TRACK_TYPE_VIDEO) {
                                        player.createMessage(player.getRenderer(rendererIndex))
                                            .setType(Renderer.MSG_SET_VIDEO_OUTPUT_RESOLUTION)
                                            .setPayload(Media3Size(outputWidth, outputHeight))
                                            .send()
                                    }
                                }
                                player.seekTo(player.currentPosition.coerceAtLeast(0L))
                            }
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    android.util.Log.e("EmbeddedPlayer", "Playback error: ${error.errorCodeName} - ${error.message}")
                    if (
                        anime4kMode != Anime4kMode.OFF &&
                        error.errorCode == PlaybackException.ERROR_CODE_VIDEO_FRAME_PROCESSING_FAILED
                    ) {
                        // A few GPU/driver combinations cannot allocate or recycle the
                        // enlarged processing texture. Enhancement is optional: recover
                        // the same media item and position without invoking source
                        // failover or turning a rendering issue into a playback failure.
                        val resumePosition = currentPosition.coerceAtLeast(0L)
                        val resumePlayback = playWhenReady
                        anime4kMode = Anime4kMode.OFF
                        runCatching {
                            stop()
                            setVideoEffects(emptyList())
                            prepare()
                            if (resumePosition > 0L) seekTo(resumePosition)
                            playWhenReady = resumePlayback
                        }.onSuccess {
                            playError = false
                            Toast.makeText(
                                context,
                                "当前GPU不兼容Anime4K，已关闭增强并恢复播放",
                                Toast.LENGTH_SHORT
                            ).show()
                        }.onFailure {
                            playError = true
                            onError()
                        }
                        return
                    }
                    SourceLogManager.error(
                        "player",
                        "播放失败",
                        "${error.errorCodeName}: ${error.message ?: "unknown"}",
                        "url=${url.trim().take(300)} type=$type headers=${requestHeaders.keys.joinToString(",")}"
                    )
                    playError = true
                    onError()
                }
            })
        }
    }

    // Do not infer surface readiness from a posted callback or from
    // onRenderedFirstFrame. Wait for the actual PlayerView output surface.
    // The diagnostic snapshots let logcat distinguish decoder, surface and
    // zero-size layout failures without exposing the signed playback URL.
    LaunchedEffect(exoPlayer, boundPlayerView) {
        val view = boundPlayerView ?: return@LaunchedEffect
        val sourceHost = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
        repeat(180) { attempt ->
            val output = view.videoSurfaceView
            val outputReady = when (output) {
                is TextureView -> output.isAvailable
                is SurfaceView -> output.holder.surface?.isValid == true
                else -> output != null
            }
            val layoutReady = view.isAttachedToWindow &&
                view.width > 0 &&
                view.height > 0 &&
                (output?.width ?: 0) > 0 &&
                (output?.height ?: 0) > 0

            if (attempt == 0 || attempt % 30 == 0 || (outputReady && layoutReady)) {
                android.util.Log.i(
                    PLAYER_DIAG_TAG,
                    "surface-check attempt=$attempt host=$sourceHost " +
                        "view=${view.width}x${view.height} attached=${view.isAttachedToWindow} " +
                        "output=${output?.javaClass?.simpleName}:${output?.width}x${output?.height} " +
                        "ready=$outputReady layoutReady=$layoutReady"
                )
            }

            if (outputReady && layoutReady) {
                exoPlayer.playWhenReady = true
                return@LaunchedEffect
            }
            delay(16)
        }

        android.util.Log.e(
            PLAYER_DIAG_TAG,
            "surface-timeout view=${view.width}x${view.height}; rebind player once"
        )
        view.player = null
        view.player = exoPlayer
        view.requestLayout()
        delay(100)
        exoPlayer.playWhenReady = true
    }

    // Twice per second is smooth enough for the progress bar and avoids
    // forcing four whole-player recompositions per second while decoding.
    LaunchedEffect(exoPlayer, isSeeking) {
        while (true) {
            if (!isSeeking) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                duration = exoPlayer.duration.coerceAtLeast(0L)
                if (duration > 0L) {
                    latestProgressCallback(currentPosition, duration)
                }
            }
            delay(500)
        }
    }

    // Auto-hide controls after 4s
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }

    LaunchedEffect(url) {
        playError = false
    }

    LaunchedEffect(currentSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(currentSpeed)
    }

    val togglePlayback = {
        if (exoPlayer.playWhenReady || exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0L)
                exoPlayer.prepare()
            }
            exoPlayer.play()
        }
    }

    LaunchedEffect(selectedQuality) {
        val selectedVariant = qualities.firstOrNull { it.name == selectedQuality }
        if (selectedVariant != null) {
            // Concrete variants are switched by replacing the media source;
            // constraints are only useful for adaptive HLS/DASH streams.
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .clearVideoSizeConstraints()
                .build()
            return@LaunchedEffect
        }
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
        when (selectedQuality) {
            "4K" -> builder.setMaxVideoSize(3840, 2160)
            "1080p" -> builder.setMaxVideoSize(1920, 1080)
            "720p" -> builder.setMaxVideoSize(1280, 720)
            "480p" -> builder.setMaxVideoSize(854, 480)
            else -> builder.clearVideoSizeConstraints()
        }
        exoPlayer.trackSelectionParameters = builder.build()
    }

    fun switchQuality(option: QualityOption?) {
        val targetUrl = option?.url ?: url
        val targetType = option?.type ?: type
        if (targetUrl.isBlank() || targetUrl == exoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()) {
            return
        }
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        val shouldPlay = exoPlayer.playWhenReady
        try {
            exoPlayer.setMediaSource(createMediaSource(targetUrl, targetType))
            exoPlayer.prepare()
            if (position > 0L) exoPlayer.seekTo(position)
            exoPlayer.playWhenReady = shouldPlay
        } catch (e: Exception) {
            android.util.Log.w("EmbeddedPlayer", "quality switch failed: ${e.message}")
            Toast.makeText(context, "清晰度切换失败，请稍后重试", Toast.LENGTH_SHORT).show()
        }
    }

    // 扫描线动画进度持久化：remember 一个 Animatable，保证中途取消后能接着当前位置回退
    val scanlineAnim = remember { androidx.compose.animation.core.Animatable(0f) }
    var effectsWereEnabled by remember(exoPlayer) { mutableStateOf(false) }

    LaunchedEffect(anime4kMode) {
        exoPlayer.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT

        // Lanerc sends Anime4K GLSL files to libmpv. This native Media3 player
        // hosts an equivalent local 2x/adaptive-line GLSL pass in Media3's GPU
        // frame processor, preserving the existing player/session state machine.
        try {
            val shouldRebuildPipeline =
                anime4kMode != Anime4kMode.OFF || effectsWereEnabled
            if (shouldRebuildPipeline) {
                // Media3 1.2.x can double-release an output texture when the
                // effect graph is hot-swapped while frames are flowing. Stop
                // first, then configure effects before prepare(), preserving
                // the user's media item, position and play/pause state.
                val resumePosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                val resumePlayback = exoPlayer.playWhenReady
                val hasMediaItem = exoPlayer.currentMediaItem != null
                if (hasMediaItem) exoPlayer.stop()
                exoPlayer.setVideoEffects(
                    if (anime4kMode == Anime4kMode.OFF) {
                        emptyList()
                    } else {
                        listOf(Anime4kGpuEffect(anime4kMode))
                    }
                )
                effectsWereEnabled = anime4kMode != Anime4kMode.OFF
                if (hasMediaItem) {
                    exoPlayer.prepare()
                    if (resumePosition > 0L) exoPlayer.seekTo(resumePosition)
                    exoPlayer.playWhenReady = resumePlayback
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "当前设备不支持Anime4K GPU增强", Toast.LENGTH_SHORT).show()
            anime4kMode = Anime4kMode.OFF
            return@LaunchedEffect
        }

        if (anime4kMode != Anime4kMode.OFF) {
            // 开启：扫描线从左到右扫过表示增强生效
            isScanlineActive = true
            scanlineAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1500, easing = LinearEasing)
            ) {
                scanlineProgress = value
            }
            isScanlineActive = false
            scanlineProgress = 0f
            scanlineAnim.snapTo(0f)
        } else if (isScanlineActive || scanlineAnim.value > 0f) {
            // 中途关闭：扫描线从当前位置向左回退滑出，而不是冻结在屏幕中间
            isScanlineActive = true
            scanlineAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 450, easing = LinearEasing)
            ) {
                scanlineProgress = value
            }
            isScanlineActive = false
            scanlineProgress = 0f
        }
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            } catch (_: Exception) {}
            exoPlayer.release()
        }
    }

    // 上报画中画状态：仅当播放器在屏且可播放时，退到后台才允许进入小窗；
    // 离开播放器（首页/片库等）立即清除，避免任意页面退出都触发画中画。
    SideEffect {
        PipController.playerActive = !playError
        PipController.isPlaying = isPlaying
        PipController.explicitFullscreen = isFullscreen
        PipController.hasNext = onNextEpisode != null
        PipController.hasPrev = onPrevEpisode != null
    }
    DisposableEffect(exoPlayer) {
        PipController.onTogglePlayPause = {
            togglePlayback()
            PipController.isPlaying = exoPlayer.playWhenReady
        }
        PipController.onNextEpisode = onNextEpisode
        PipController.onPrevEpisode = onPrevEpisode
        onDispose {
            PipController.clearPlayerState()
        }
    }
    DisposableEffect(Unit) {
        PipController.onRestorePresentation = { restoreFullscreen ->
            applyFullscreen(restoreFullscreen)
        }
        onDispose {
            // Resolving another episode temporarily removes this Composable
            // from the PiP window. Keep the restoration callback for that
            // short gap, but never retain it after a normal player exit.
            if (!PipController.inPipMode) {
                PipController.onRestorePresentation = null
            }
        }
    }

    // 退到后台/锁屏（ON_STOP）时无条件结束临时长按动作并暂停。
    // isPlaying may already be false because audio focus/surface was lost,
    // while playWhenReady or temporary 3x speed is still active.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val activityInPip = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    activity?.isInPictureInPictureMode == true
                } else false
                if (PlayerInteractionPolicy.shouldPauseOnStop(activityInPip)) {
                    stopHoldGesture()
                    dragGestureActive = false
                    exoPlayer.playbackParameters = PlaybackParameters(currentSpeed)
                    exoPlayer.pause()
                    controlsVisible = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Enter Picture-in-Picture helper
    val enterPip = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity is MainActivity) {
            if (!activity.enterPlayerPictureInPicture()) {
                Toast.makeText(context, "小窗/画中画启动失败", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "当前设备不支持画中画功能", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(duration, isLocked, controlsVisible) {
                var startX = 0f
                var startY = 0f
                var totalX = 0f
                var totalY = 0f
                var volumeAcc = 0f
                var startedOnControls = false
                var resumeAfterGestureSeek = false
                var dragAxis = PlayerInteractionPolicy.DragAxis.UNDECIDED

                detectDragGestures(
                    onDragStart = { offset ->
                        if (holdGestureActive) return@detectDragGestures
                        dragGestureActive = true
                        stopHoldGesture()
                        startX = offset.x
                        startY = offset.y
                        totalX = 0f
                        totalY = 0f
                        volumeAcc = 0f
                        dragAxis = PlayerInteractionPolicy.DragAxis.UNDECIDED
                        gestureSeekOriginPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                        seekPreviewPosition = gestureSeekOriginPosition
                        startedOnControls = controlsVisible && startY > size.height * 0.70f
                        resumeAfterGestureSeek = exoPlayer.playWhenReady
                    },
                    onDrag = { change, amount ->
                        if (holdGestureActive || isLocked || startedOnControls) return@detectDragGestures
                        change.consume()
                        totalX += amount.x
                        totalY += amount.y

                        val width = size.width.coerceAtLeast(1)
                        dragAxis = PlayerInteractionPolicy.lockDragAxis(
                            current = dragAxis,
                            totalX = totalX,
                            totalY = totalY
                        )

                        if (dragAxis == PlayerInteractionPolicy.DragAxis.VERTICAL) {
                            val delta = (-amount.y / 700f).coerceIn(-0.08f, 0.08f)
                            if (startX < width * 0.5f) {
                                adjustScreenBrightness(activity, delta)
                                val currentB = getScreenBrightness(activity)
                                val pct = (currentB * 100).toInt()
                                triggerHud("brightness", pct, "亮度 $pct%", 1200L)
                            } else {
                                audioManager?.let { manager ->
                                    runCatching {
                                        val maxVol = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                        val curVol = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        volumeAcc += -amount.y / 25f
                                        val step = volumeAcc.toInt()
                                        if (step != 0) {
                                            volumeAcc -= step
                                            val target = (curVol + step).coerceIn(0, maxVol)
                                            manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                            val pct = if (maxVol > 0) (target * 100) / maxVol else 0
                                            triggerHud("volume", target, "音量 $pct%", 1200L)
                                        }
                                    }
                                }
                            }
                        } else if (dragAxis == PlayerInteractionPolicy.DragAxis.HORIZONTAL) {
                            val proportion = (totalX / width.toFloat()).coerceIn(-0.5f, 0.5f)
                            val validDuration = if (duration > 0L) duration else exoPlayer.duration.coerceAtLeast(0L)
                            val deltaMs = (validDuration.coerceAtLeast(60_000L) * proportion).toLong()
                            val targetMs = (gestureSeekOriginPosition + deltaMs)
                                .coerceIn(0L, validDuration.coerceAtLeast(0L))
                            seekPreviewPosition = targetMs
                            gestureHudType = "seek"
                            gestureHudValue = if (deltaMs >= 0) 1 else -1
                            val directionSymbol = if (deltaMs >= 0) "+" else "-"
                            val deltaSec = kotlin.math.abs(deltaMs / 1000L)
                            gestureHudText = "${formatTime(targetMs)} / ${formatTime(validDuration)} (${directionSymbol}${deltaSec}s)"
                        }
                    },
                    onDragEnd = {
                        lastDragTime = System.currentTimeMillis()
                        dragGestureActive = false
                        if (
                            isLocked ||
                            startedOnControls ||
                            dragAxis != PlayerInteractionPolicy.DragAxis.HORIZONTAL
                        ) return@detectDragGestures
                        val width = size.width.coerceAtLeast(1)
                        val proportion = (totalX / width.toFloat()).coerceIn(-0.5f, 0.5f)
                        val validDuration = if (duration > 0L) duration else exoPlayer.duration.coerceAtLeast(0L)
                        val deltaMs = (validDuration.coerceAtLeast(60_000L) * proportion).toLong()
                        val targetPosition = (gestureSeekOriginPosition + deltaMs)
                            .coerceIn(0L, validDuration.coerceAtLeast(0L))
                        runCatching {
                            if (exoPlayer.isCurrentMediaItemSeekable || validDuration > 0L) {
                                exoPlayer.seekTo(targetPosition)
                                exoPlayer.playWhenReady = resumeAfterGestureSeek
                            }
                        }
                        gestureHudText = ""
                    },
                    onDragCancel = {
                        lastDragTime = System.currentTimeMillis()
                        dragGestureActive = false
                        if (gestureHudType == "seek") gestureHudText = ""
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (System.currentTimeMillis() - lastDragTime < 300L) return@detectTapGestures
                        controlsVisible = !controlsVisible
                    }
                )
            }
            // 长按手势（独立检测，支持松手回调与上下滑锁定/解锁）：
            .pointerInput(isLocked) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val longPress = awaitLongPressOrCancellation(down.id)
                    if (!PlayerInteractionPolicy.canStartHoldGesture(
                            longPressDetected = longPress != null,
                            controlsLocked = isLocked,
                            dragGestureActive = dragGestureActive
                        )
                    ) {
                        return@awaitEachGesture
                    }

                    val width = size.width.coerceAtLeast(1)
                    val holdLeft = longPress!!.position.x < width * 0.5f
                    speedBeforeHold = currentSpeed
                    holdGestureActive = true

                    try {
                        if (holdLeft) {
                            // 左侧长按：3X << 3倍速快退
                            triggerHud("seek", -1, "3X <<", 0L)
                            rewindJob?.cancel()
                            rewindJob = playerScope.launch {
                                while (true) {
                                    delay(380L)
                                    if (exoPlayer.playbackState != Player.STATE_BUFFERING) {
                                        val target = (exoPlayer.currentPosition - 3_000L).coerceAtLeast(0L)
                                        exoPlayer.seekTo(target)
                                    }
                                    gestureHudText = "3X <<"
                                }
                            }
                        } else {
                            // 右侧长按：触发长按倍速，上滑锁定，下滑恢复1.0倍速
                            var isSpeedLockedThisHold = isSpeedLocked
                            val startY = longPress.position.y
                            currentSpeed = longPressSpeed
                            triggerHud("speed", 1, "${String.format("%.1f", longPressSpeed)}X >> (上滑锁定)", 0L)

                            while (true) {
                                val event = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) break
                                val diffY = startY - change.position.y
                                if (diffY > 50f && !isSpeedLockedThisHold) {
                                    isSpeedLockedThisHold = true
                                    isSpeedLocked = true
                                    triggerHud("speed", 1, "已锁定 ${String.format("%.1f", longPressSpeed)}X", 0L)
                                } else if (diffY < -50f && isSpeedLockedThisHold) {
                                    isSpeedLockedThisHold = false
                                    isSpeedLocked = false
                                    triggerHud("speed", 1, "已恢复1.0倍速", 1200L)
                                }
                            }

                            if (isSpeedLockedThisHold) {
                                isSpeedLocked = true
                            }
                        }

                        // 等待松手（或手势被取消）
                        waitForUpOrCancellation()
                    } finally {
                        lastDragTime = System.currentTimeMillis()
                        stopHoldGesture()
                        if (isSpeedLocked) {
                            currentSpeed = longPressSpeed
                            triggerHud("speed", 1, "已锁定 ${String.format("%.1f", longPressSpeed)}X", 1800L)
                        } else {
                            currentSpeed = speedBeforeHold.coerceAtLeast(1.0f)
                            triggerHud("speed", 0, "已恢复1.0倍速", 1200L)
                        }
                    }
                }
            }
    ) {
        if (playError) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("视频加载失败", color = AppColors.orange, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(8.dp))
                Text("请尝试点击「重新加载」或切换数据源", color = AppColors.muted, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            playError = false
                            if (onSelectEpisode != null && currentEpisodeIndex in episodes.indices) {
                                onSelectEpisode(currentEpisodeIndex)
                            } else {
                                exoPlayer.seekTo(0L)
                                exoPlayer.prepare()
                                exoPlayer.playWhenReady = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.cyan)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                        Spacer(Modifier.width(4.dp))
                        Text("重新加载", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    if (onNextEpisode != null) {
                        OutlinedButton(
                            onClick = { onNextEpisode() },
                            border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.cyan.copy(alpha = 0.6f))
                        ) {
                            Text("下一集", color = AppColors.cyan, fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    // 从 XML 膨胀以启用 texture_view（见 juying_player_view.xml），
                    // 修复部分机型“开始没画面、切全屏才有画面”的 SurfaceView 绑定问题
                    (android.view.LayoutInflater.from(ctx)
                        .inflate(R.layout.juying_player_view, FrameLayout(ctx), false) as PlayerView).apply {
                        boundPlayerView = this
                        player = exoPlayer
                        useController = false
                        setKeepContentOnPlayerReset(true)
                        isClickable = false
                        isFocusable = false
                        setOnTouchListener { _, _ -> false }
                        this.resizeMode = resizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        // Playback is started by the surface-readiness effect,
                        // not by post{} or onRenderedFirstFrame.
                    }
                },
                update = { view ->
                    if (boundPlayerView !== view) {
                        boundPlayerView = view
                    }
                    if (view.player != exoPlayer) {
                        view.player = exoPlayer
                    }
                    if (view.resizeMode != resizeMode) {
                        view.resizeMode = resizeMode
                        view.requestLayout()
                        view.invalidate()
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // ── Hardware Super-Res Scanline Comparison Line Animation (Monochrome) ──
        if (isScanlineActive) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val lineX = maxWidth * scanlineProgress
                Box(
                    modifier = Modifier
                        .offset(x = lineX)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.8f), Color.White, Color.White.copy(alpha = 0.8f), Color.Transparent)
                            )
                        )
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp)
                ) {
                    Text(
                        if (anime4kMode == Anime4kMode.HIGH_QUALITY) {
                            "Anime4K 高质量增强中"
                        } else {
                            "Anime4K 性能增强中"
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // ── Horizontal seek preview OSD Badge ──
        if (dragGestureActive && gestureHudType == "seek" && gestureHudText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.20f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (isFullscreen) 52.dp else 36.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        if (gestureHudValue >= 0) "快进 " else "快退 ",
                        color = AppColors.cyan,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        gestureHudText,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── Gesture HUD OSD Badge Overlay (brightness/volume/hold actions) ──
        if (gestureHudText.isNotBlank() && !(dragGestureActive && gestureHudType == "seek")) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.40f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!gestureHudText.contains("3X")) {
                        HudIcon(gestureHudType, gestureHudValue)
                    }
                    Text(gestureHudText, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        if (danmakuEnabled && activeDanmakus.isNotEmpty()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isFullscreen) 240.dp else 160.dp)
                    .padding(top = if (isFullscreen) 36.dp else 16.dp)
                    .clipToBounds()
            ) {
                val screenWidthPx = with(LocalDensity.current) { constraints.maxWidth.toFloat() }

                activeDanmakus.forEach { danmaku ->
                    key(danmaku.id) {
                        var progress by remember { mutableFloatStateOf(0f) }
                        val lineTopDp = (danmaku.lineIndex * 28).dp

                        LaunchedEffect(danmaku.id) {
                            val durationMs = 6500L
                            val startTime = System.currentTimeMillis()
                            while (progress < 1f) {
                                val elapsed = System.currentTimeMillis() - startTime
                                progress = (elapsed.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                delay(16L)
                            }
                            activeDanmakus.remove(danmaku)
                        }

                        val translationX = with(LocalDensity.current) {
                            (screenWidthPx * (1f - progress) - 280.dp.toPx() * progress).toDp()
                        }

                        Text(
                            text = danmaku.text,
                            color = danmaku.color.copy(alpha = danmakuOpacity),
                            fontSize = (14 * danmakuFontSizeScale).sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .offset(x = translationX, y = lineTopDp)
                                .background(Color.Black.copy(alpha = 0.40f * danmakuOpacity), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        // ── Controls Overlay ──
        // 画中画小窗内不展示顶部栏/进度条等控制层（小窗由系统提供关闭/放大按钮及自定义遥控按钮）
        AnimatedVisibility(
            visible = controlsVisible && !PipController.inPipMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            // 全屏横屏时避让刘海/挖孔区域，保证不同机型上顶部栏与底部控制栏完整可见
            Box(Modifier.fillMaxSize().then(if (isFullscreen) Modifier.displayCutoutPadding() else Modifier)) {
                if (isLocked) {
                    // Lock Screen Only Button (Monochrome white)
                    IconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(44.dp)
                    ) {
                        LockIcon(tint = Color.White)
                    }
                } else {
                    // ── TOP BAR (Back, Title, PiP, Cast, More) ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                                )
                            )
                            .padding(
                                start = if (isFullscreen) 24.dp else 12.dp,
                                end = if (isFullscreen) 24.dp else 12.dp,
                                top = if (isFullscreen) 4.dp else 4.dp,
                                bottom = 4.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (isFullscreen) {
                                toggleFullscreen()
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = Color.White)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (episodeName.isNotBlank()) {
                                Text(
                                    episodeName,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                        }

                        // PiP Button (画中画)
                        IconButton(onClick = { enterPip() }) {
                            PipIcon(tint = Color.White)
                        }

                        // Cast Button (投屏)
                        IconButton(onClick = {
                            Toast.makeText(context, "搜寻附近可投屏 TV 设备中...", Toast.LENGTH_SHORT).show()
                        }) {
                            TvCastIcon(tint = Color.White)
                        }

                        // More Button (更多)
                        IconButton(onClick = { showRatioMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多", tint = Color.White)
                        }
                    }

                    // ── RIGHT CENTER LOCK BUTTON (Monochrome white) ──
                    IconButton(
                        onClick = { isLocked = true },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = if (isFullscreen) 28.dp else 16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(44.dp)
                    ) {
                        UnlockIcon(tint = Color.White)
                    }

                    // ── CENTER TRANSPORT BUTTONS (上一集 / 播放暂停 / 下一集) ──
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(30.dp)
                    ) {
                        IconButton(
                            onClick = { onPrevEpisode?.invoke() },
                            enabled = onPrevEpisode != null,
                            modifier = Modifier
                                .size(46.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        ) {
                            SkipPreviousIcon(
                                tint = if (onPrevEpisode != null) Color.White else Color.White.copy(alpha = 0.35f)
                            )
                        }
                        IconButton(
                            onClick = togglePlayback,
                            modifier = Modifier
                                .size(62.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        ) {
                            if (exoPlayer.playWhenReady || isPlaying) {
                                PauseIcon(tint = Color.White)
                            } else {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "播放",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { onNextEpisode?.invoke() },
                            enabled = onNextEpisode != null,
                            modifier = Modifier
                                .size(46.dp)
                                .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        ) {
                            SkipNextIcon(
                                tint = if (onNextEpisode != null) Color.White else Color.White.copy(alpha = 0.35f)
                            )
                        }
                    }

                    // ── BOTTOM OVERLAY CONTAINER ──
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                                )
                            )
                            .padding(
                                start = if (isFullscreen) 24.dp else 12.dp,
                                end = if (isFullscreen) 24.dp else 12.dp,
                                bottom = if (isFullscreen) 6.dp else 2.dp,
                                top = 0.dp
                            )
                    ) {
                        // ── ROW 1: Time, Slider, Duration, Fullscreen ──
                        Row(
                            modifier = Modifier.fillMaxWidth().height(26.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatTime(if (isSeeking) sliderPosition.toLong() else currentPosition),
                                color = Color.White,
                                fontSize = 12.sp
                            )

                            Slider(
                                value = if (isSeeking) sliderPosition else currentPosition.toFloat(),
                                onValueChange = {
                                    if (!isSeeking) {
                                        resumeAfterSliderSeek = exoPlayer.playWhenReady
                                    }
                                    isSeeking = true
                                    sliderPosition = it
                                },
                                onValueChangeFinished = {
                                    exoPlayer.seekTo(sliderPosition.toLong())
                                    exoPlayer.playWhenReady = resumeAfterSliderSeek
                                    isSeeking = false
                                },
                                valueRange = 0f..(duration.coerceAtLeast(1L).toFloat()),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(24.dp)
                                    .padding(horizontal = 6.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = AppColors.cyan,
                                    activeTrackColor = AppColors.cyan,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                )
                            )

                            Text(
                                formatTime(duration),
                                color = Color.White,
                                fontSize = 12.sp
                            )

                            Spacer(Modifier.width(6.dp))

                            // Fullscreen Button (进度条最右侧的全屏按钮)
                            IconButton(
                                onClick = { toggleFullscreen() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                FullscreenIcon(isFull = isFullscreen)
                            }
                        }

                        // ── ROW 2: Play/Pause, NextEp, Danmaku, Line, Danmaku Input, SuperRes, Speed, Episodes ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Play / Pause
                                IconButton(
                                    onClick = {
                                        togglePlayback()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                if (exoPlayer.playWhenReady || isPlaying) {
                                    PauseIcon(tint = Color.White)
                                } else {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "播放",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                }

                                // Next Episode
                                IconButton(
                                    onClick = { onNextEpisode?.invoke() },
                                    enabled = onNextEpisode != null,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    SkipNextIcon(tint = if (onNextEpisode != null) Color.White else Color.White.copy(alpha = 0.4f))
                                }

                                // Danmaku Toggle [弹]
                                Surface(
                                    onClick = { danmakuEnabled = !danmakuEnabled },
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (danmakuEnabled) AppColors.cyan.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.15f),
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        "弹",
                                        color = if (danmakuEnabled) AppColors.cyan else Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                 // Danmaku Setting
                                IconButton(
                                    onClick = { showDanmakuSettings = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "弹幕设置",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Danmaku Input Entry with Login Check
                            val isLoggedIn = remember(context) { storageManager.getAuthToken().isNotBlank() }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp)
                                    .height(28.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                    .clickable {
                                        if (isLoggedIn) {
                                            showDanmakuInput = true
                                        } else {
                                            showLoginPromptDialog = true
                                        }
                                    }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    if (isLoggedIn) "发送一条友善的弹幕吧~" else "请“登录”后发弹幕",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "超分辨率",
                                    color = AppColors.cyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { showQualityMenu = true }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )

                                Spacer(Modifier.width(6.dp))

                                // Screen Scale Mode (打开画面比例选项弹窗)
                                Text(
                                    when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "铺满"
                                        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> "裁剪"
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "拉伸"
                                        else -> "默认"
                                    },
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clickable { showRatioMenu = true }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )

                                Spacer(Modifier.width(6.dp))

                                // Speed Switch
                                Text(
                                    "${currentSpeed}x",
                                    color = AppColors.cyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { showSpeedMenu = true }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )

                                Spacer(Modifier.width(6.dp))

                                // Episode Drawer Button
                                if (episodes.isNotEmpty()) {
                                    Text(
                                        "选集",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable { showEpisodeDrawer = true }
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Speed Selector Dialog / Modal ──
        if (showSpeedMenu) {
            PlayerRightSideOverlay(
                onDismiss = { showSpeedMenu = false },
                title = "倍速设置",
                subtitle = "播放速度设置"
            ) {
                val presetList = listOf(4.0f, 3.0f, 2.0f, 1.5f, 1.25f, 1.0f, 0.75f, 0.5f)
                val isCustomActive = !presetList.contains(currentSpeed)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(180.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(presetList.size + 1) { idx ->
                        if (idx < presetList.size) {
                            val speed = presetList[idx]
                            val active = currentSpeed == speed && !isCustomActive
                            Surface(
                                onClick = {
                                    currentSpeed = speed
                                    showSpeedMenu = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) Color.White else Color(0xFF262A34)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${speed}X",
                                        color = if (active) Color.Black else Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            // 自定义倍速按钮 - 与其他倍速按钮样式完全统一
                            val active = isCustomActive
                            val label = if (active) "${String.format("%.2f", currentSpeed)}X\n(自定义)" else "自定义\n${String.format("%.2f", customSpeed)}X"
                            Surface(
                                onClick = { showCustomSpeedDialog = true },
                                shape = RoundedCornerShape(10.dp),
                                color = if (active) AppColors.cyan else Color(0xFF262A34)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        label,
                                        color = if (active) Color.Black else Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                Text("其他设置", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Surface(
                    onClick = { showLongPressSpeedDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF262A34),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("长按速度设置", color = Color.White, fontSize = 13.sp)
                        Text("${String.format("%.1f", longPressSpeed)}X >", color = AppColors.cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF262A34),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("长按上滑锁定倍速", color = Color.White, fontSize = 13.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                        }
                        Switch(
                            checked = isSpeedLocked,
                            onCheckedChange = { isSpeedLocked = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppColors.cyan)
                        )
                    }
                }
            }
        }

        // 自定义倍速调节弹窗 (0.25X ~ 4.0X)
        if (showCustomSpeedDialog) {
            var tempVal by remember { mutableStateOf(currentSpeed.coerceIn(0.25f, 4.0f)) }
            AlertDialog(
                onDismissRequest = { showCustomSpeedDialog = false },
                title = { Text("自定义倍速设置", color = AppColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${String.format("%.2f", tempVal)}X",
                            color = AppColors.cyan,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { tempVal = (tempVal - 0.05f).coerceAtLeast(0.25f) }) {
                                Text("-0.05", color = AppColors.text, fontSize = 14.sp)
                            }
                            Slider(
                                value = tempVal,
                                onValueChange = { tempVal = (Math.round(it * 20) / 20.0f).coerceIn(0.25f, 4.0f) },
                                valueRange = 0.25f..4.0f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = AppColors.cyan, activeTrackColor = AppColors.cyan)
                            )
                            TextButton(onClick = { tempVal = (tempVal + 0.05f).coerceAtMost(4.0f) }) {
                                Text("+0.05", color = AppColors.text, fontSize = 14.sp)
                            }
                        }
                        Text("支持 0.25X ~ 4.0X 精细调节", color = AppColors.muted, fontSize = 11.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val finalVal = (Math.round(tempVal * 20) / 20.0f).coerceIn(0.25f, 4.0f)
                        customSpeed = finalVal
                        currentSpeed = finalVal
                        storageManager.setCustomSpeed(finalVal)
                        showCustomSpeedDialog = false
                        showSpeedMenu = false
                    }) {
                        Text("确定", color = AppColors.cyan, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCustomSpeedDialog = false }) {
                        Text("取消", color = AppColors.muted)
                    }
                },
                containerColor = AppColors.panel,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // 长按倍速调节弹窗 (0.25X ~ 4.0X)
        if (showLongPressSpeedDialog) {
            var tempVal by remember { mutableStateOf(longPressSpeed.coerceIn(0.25f, 4.0f)) }
            AlertDialog(
                onDismissRequest = { showLongPressSpeedDialog = false },
                title = { Text("长按倍速设置", color = AppColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "${String.format("%.1f", tempVal)}X",
                            color = AppColors.cyan,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { tempVal = (tempVal - 0.25f).coerceAtLeast(0.25f) }) {
                                Text("-0.25", color = AppColors.text, fontSize = 14.sp)
                            }
                            Slider(
                                value = tempVal,
                                onValueChange = { tempVal = (Math.round(it * 4) / 4.0f).coerceIn(0.25f, 4.0f) },
                                valueRange = 0.25f..4.0f,
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(thumbColor = AppColors.cyan, activeTrackColor = AppColors.cyan)
                            )
                            TextButton(onClick = { tempVal = (tempVal + 0.25f).coerceAtMost(4.0f) }) {
                                Text("+0.25", color = AppColors.text, fontSize = 14.sp)
                            }
                        }
                        Text("设置长按屏幕右侧触发的快进倍速 (0.25X~4.0X)", color = AppColors.muted, fontSize = 11.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val finalVal = (Math.round(tempVal * 4) / 4.0f).coerceIn(0.25f, 4.0f)
                        longPressSpeed = finalVal
                        storageManager.setLongPressSpeed(finalVal)
                        showLongPressSpeedDialog = false
                    }) {
                        Text("确定", color = AppColors.cyan, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLongPressSpeedDialog = false }) {
                        Text("取消", color = AppColors.muted)
                    }
                },
                containerColor = AppColors.panel,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // 未登录提示弹窗
        if (showLoginPromptDialog) {
            AlertDialog(
                onDismissRequest = { showLoginPromptDialog = false },
                title = { Text("登录提示", color = AppColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = { Text("发表弹幕与评论需要登录账号。是否立即前往“登录”？", color = AppColors.text, fontSize = 14.sp) },
                confirmButton = {
                    Button(
                        onClick = {
                            showLoginPromptDialog = false
                            onNavigateToLogin?.invoke()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.cyan)
                    ) {
                        Text("登录", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLoginPromptDialog = false }) {
                        Text("取消", color = AppColors.muted)
                    }
                },
                containerColor = AppColors.panel,
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (showQualityMenu) {
            AlertDialog(
                onDismissRequest = { showQualityMenu = false },
                title = { Text("超分辨率与画质增强", color = AppColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "Anime4K GPU超分辨率 · 实验功能",
                            color = AppColors.text,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Anime4kMode.entries.forEach { mode ->
                                val selected = anime4kMode == mode
                                val enabled = mode != Anime4kMode.HIGH_QUALITY || !lowRamDevice
                                Surface(
                                    onClick = {
                                        if (enabled) anime4kMode = mode
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) AppColors.cyan.copy(alpha = 0.22f) else AppColors.panel2,
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (selected) AppColors.cyan else Color.Transparent
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = { if (enabled) anime4kMode = mode },
                                            enabled = enabled,
                                            colors = RadioButtonDefaults.colors(selectedColor = AppColors.cyan)
                                        )
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                mode.label,
                                                color = if (enabled) AppColors.text else AppColors.muted,
                                                fontSize = 13.sp,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                            )
                                            if (mode != Anime4kMode.OFF) {
                                                Text(
                                                    if (mode == Anime4kMode.PERFORMANCE) {
                                                        "2× GPU放大与轻量线条恢复，最高处理到1440p"
                                                    } else if (lowRamDevice) {
                                                        "低内存设备已停用高质量模式"
                                                    } else {
                                                        "2× GPU放大与强化线条恢复，最高处理到2160p"
                                                    },
                                                    color = AppColors.muted,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Text(
                            "开启后会持续占用GPU并增加耗电、温度；中低端手机可能出现发热、掉帧或卡顿。遇到卡顿请改用性能优先、关闭弹幕或直接关闭。本功能只增强本地渲染，不会把低清片源变成真正4K片源。",
                            color = AppColors.orange,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showQualityMenu = false }) {
                        Text("关闭", color = AppColors.cyan)
                    }
                },
                containerColor = AppColors.panel,
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (showDanmakuSettings) {
            AlertDialog(
                onDismissRequest = { showDanmakuSettings = false },
                title = {
                    Text("弹幕设置", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. 不透明度
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("不透明度", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                                Text("${(danmakuOpacity * 100).toInt()}%", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                            Slider(
                                value = danmakuOpacity,
                                onValueChange = { danmakuOpacity = it },
                                valueRange = 0.1f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = AppColors.rose,
                                    activeTrackColor = AppColors.rose,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                )
                            )
                        }

                        // 2. 屏蔽弹幕类型
                        Column {
                            Text("屏蔽弹幕类型", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Triple("屏蔽底部", blockBottom) { blockBottom = !blockBottom },
                                    Triple("屏蔽顶部", blockTop) { blockTop = !blockTop },
                                    Triple("屏蔽滚动", blockScroll) { blockScroll = !blockScroll },
                                    Triple("屏蔽彩色", blockColor) { blockColor = !blockColor }
                                ).forEach { (label, isBlocked, toggle) ->
                                    Surface(
                                        onClick = toggle,
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isBlocked) AppColors.rose.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                                        border = if (isBlocked) androidx.compose.foundation.BorderStroke(1.dp, AppColors.rose) else null,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(64.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize().padding(4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            val iconChar = when (label) {
                                                "屏蔽底部" -> "⎽"
                                                "屏蔽顶部" -> "⎾"
                                                "屏蔽滚动" -> "≡"
                                                else -> "💧"
                                            }
                                            Text(iconChar, color = if (isBlocked) AppColors.rose else Color.White, fontSize = 16.sp)
                                            Spacer(Modifier.height(4.dp))
                                            Text(label, color = if (isBlocked) AppColors.rose else Color.White.copy(alpha = 0.8f), fontSize = 10.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }

                        // 3. 显示区域
                        Column {
                            Text("显示区域", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "全屏" to 1.0f,
                                    "3/4" to 0.75f,
                                    "半屏" to 0.5f,
                                    "1/4" to 0.25f
                                ).forEach { (label, ratio) ->
                                    val isSelected = danmakuAreaRatio == ratio
                                    Surface(
                                        onClick = { danmakuAreaRatio = ratio },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) AppColors.rose else Color.White.copy(alpha = 0.08f),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(44.dp)
                                    ) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                label,
                                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 4. 字号大小
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("字号大小", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                                Text("${(danmakuFontSizeScale * 100).toInt()}%", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                            Slider(
                                value = danmakuFontSizeScale,
                                onValueChange = { danmakuFontSizeScale = it },
                                valueRange = 0.5f..1.5f,
                                colors = SliderDefaults.colors(
                                    thumbColor = AppColors.rose,
                                    activeTrackColor = AppColors.rose,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                )
                            )
                        }

                        // 5. 恢复默认设置
                        Surface(
                            onClick = {
                                danmakuOpacity = 1.0f
                                blockBottom = false
                                blockTop = false
                                blockScroll = false
                                blockColor = false
                                danmakuAreaRatio = 1.0f
                                danmakuFontSizeScale = 1.0f
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("恢复默认设置", color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDanmakuSettings = false }) {
                        Text("完成", color = AppColors.rose)
                    }
                },
                containerColor = Color(0xFF1E1E24),
                shape = RoundedCornerShape(16.dp)
            )
        }

        // ── Send Danmaku Input Dialog ──
        if (showDanmakuInput) {
            AlertDialog(
                onDismissRequest = { showDanmakuInput = false },
                title = { Text("发送弹幕", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = danmakuDraft,
                            onValueChange = { danmakuDraft = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("发送一条友善的弹幕吧~", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AppColors.cyan,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("弹幕颜色", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            danmakuColorPalette.forEach { (color, _) ->
                                Box(
                                    modifier = Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (selectedDanmakuColor == color) 2.5.dp else 0.dp,
                                            color = if (selectedDanmakuColor == color) AppColors.cyan else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedDanmakuColor = color }
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val text = danmakuDraft.trim()
                            if (text.isNotEmpty()) {
                                sentDanmaku.add(text)
                                activeDanmakus.add(
                                    DanmakuItem(
                                        text = text,
                                        color = selectedDanmakuColor,
                                        lineIndex = (activeDanmakus.size % 5)
                                    )
                                )
                                danmakuDraft = ""
                                showDanmakuInput = false
                                if (!danmakuEnabled) danmakuEnabled = true
                            }
                        },
                        enabled = danmakuDraft.isNotBlank()
                    ) {
                        Text("发送", color = AppColors.cyan)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDanmakuInput = false }) {
                        Text("取消", color = Color.White.copy(alpha = 0.7f))
                    }
                },
                containerColor = Color(0xFF1E1E24),
                shape = RoundedCornerShape(16.dp)
            )
        }

        // ── Ratio Selector Modal ──
        if (showRatioMenu) {
            AlertDialog(
                onDismissRequest = { showRatioMenu = false },
                title = { Text("画面比例", color = AppColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf(
                            AspectRatioFrameLayout.RESIZE_MODE_FIT to "默认 (16:9 原比例居中)",
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "铺满 (无黑边满屏)",
                            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH to "裁剪 (等比例裁剪)",
                            AspectRatioFrameLayout.RESIZE_MODE_FILL to "拉伸 (强行填充整屏)"
                        ).forEach { (mode, label) ->
                            val selected = resizeMode == mode
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        resizeMode = mode
                                        showRatioMenu = false
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = {
                                            resizeMode = mode
                                            showRatioMenu = false
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = AppColors.cyan)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        label,
                                        color = if (selected) AppColors.cyan else AppColors.text,
                                        fontSize = 14.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRatioMenu = false }) {
                        Text("关闭", color = AppColors.cyan)
                    }
                },
                containerColor = AppColors.panel
            )
        }

        // ── Episode Drawer Sheet ──
        if (showEpisodeDrawer && episodes.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { showEpisodeDrawer = false },
                title = { Text("选集 (${episodes.size}集)", color = AppColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Box(modifier = Modifier.heightIn(max = 280.dp)) {
                        LazyVerticalGrid(columns = GridCells.Fixed(4), contentPadding = PaddingValues(4.dp)) {
                            itemsIndexed(episodes) { index, ep ->
                                val isCur = index == currentEpisodeIndex
                                Box(
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .background(if (isCur) AppColors.cyan else AppColors.panel2, RoundedCornerShape(6.dp))
                                        .border(1.dp, if (isCur) AppColors.cyan else Color.Transparent, RoundedCornerShape(6.dp))
                                        .clickable {
                                            onSelectEpisode?.invoke(index)
                                            showEpisodeDrawer = false
                                        }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        ep.name,
                                        color = if (isCur) Color.Black else AppColors.text,
                                        fontSize = 12.sp,
                                        fontWeight = if (isCur) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showEpisodeDrawer = false }) {
                        Text("关闭", color = AppColors.cyan)
                    }
                },
                containerColor = AppColors.panel
            )
        }
    }
}

@Composable
fun LockIcon(tint: Color = Color.White, modifier: Modifier = Modifier.size(22.dp)) {
    Icon(Icons.Default.Lock, contentDescription = "已锁定", tint = tint, modifier = modifier)
}

@Composable
fun UnlockIcon(tint: Color = Color.White, modifier: Modifier = Modifier.size(22.dp)) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.11f

        // Body (lower rounded box)
        drawRoundRect(
            color = tint,
            topLeft = androidx.compose.ui.geometry.Offset(w * 0.15f, h * 0.45f),
            size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.48f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f)
        )
        // Shackle (upper arch, open/lifted up)
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.32f, h * 0.45f)
            lineTo(w * 0.32f, h * 0.28f)
            cubicTo(w * 0.32f, h * 0.10f, w * 0.68f, h * 0.10f, w * 0.68f, h * 0.20f)
        }
        drawPath(
            path = path,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = stroke,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        )
    }
}


@Composable
private fun PlayerRightSideOverlay(
    onDismiss: () -> Unit,
    title: String,
    subtitle: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.CenterEnd
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.48f)
                .clickable { /* consume click */ },
            color = Color(0xFF14171F).copy(alpha = 0.96f),
            tonalElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        if (subtitle.isNotEmpty()) {
                            Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}