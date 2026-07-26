package com.juying.app.ui

import android.app.Activity
import android.app.ActivityManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.juying.app.AppColors
import com.juying.app.source.Episode
import com.juying.app.source.SourceLogManager
import kotlinx.coroutines.delay
import java.util.Locale

private const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

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

private fun adjustScreenBrightness(activity: Activity?, delta: Float) {
    activity ?: return
    val attributes = activity.window.attributes
    val current = if (attributes.screenBrightness in 0.01f..1f) {
        attributes.screenBrightness
    } else {
        0.5f
    }
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
private fun FullscreenIcon(modifier: Modifier = Modifier, isFull: Boolean) {
    if (isFull) {
        Box(
            modifier = modifier
                .size(20.dp)
                .background(AppColors.cyan, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(8.dp).background(Color.Black))
        }
    } else {
        Box(
            modifier = modifier
                .size(20.dp)
                .border(1.5.dp, AppColors.cyan, RoundedCornerShape(3.dp)),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier.size(7.dp).background(AppColors.cyan))
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
private fun PauseIcon(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(4.dp, 14.dp).background(tint, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.size(4.dp, 14.dp).background(tint, RoundedCornerShape(1.dp)))
    }
}

@Composable
fun EmbeddedVideoPlayer(
    url: String,
    type: String,
    headers: Map<String, String>?,
    referer: String?,
    title: String,
    episodeName: String,
    episodes: List<Episode> = emptyList(),
    currentEpisodeIndex: Int = 0,
    onSelectEpisode: ((Int) -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    onBack: () -> Unit,
    onError: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    var currentSpeed by remember { mutableStateOf(1.0f) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var playError by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf("Auto") }
    var qualityEnhancement by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showDanmakuSettings by remember { mutableStateOf(false) }
    var danmakuOpacity by remember { mutableStateOf(0.85f) }
    var danmakuDraft by remember { mutableStateOf("") }
    val lowRamDevice = remember(context) {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    }
    val maxSpeed = if (lowRamDevice || qualityEnhancement) 2.0f else 3.0f
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    val sentDanmaku = remember { mutableStateListOf<String>() }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableStateOf(0f) }

    var isFullscreen by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var danmakuEnabled by remember { mutableStateOf(true) }

    var showSpeedMenu by remember { mutableStateOf(false) }
    var showRatioMenu by remember { mutableStateOf(false) }
    var showEpisodeDrawer by remember { mutableStateOf(false) }

    // Toggle fullscreen helper
    val toggleFullscreen = {
        val targetFullscreen = !isFullscreen
        isFullscreen = targetFullscreen
        activity?.let { act ->
            val window = act.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (targetFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // System orientation sensor detection ("若系统层面未开启方向锁定!")
    DisposableEffect(context, isLocked) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN || isLocked) return
                val autoRotateOn = try {
                    Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
                } catch (_: Exception) { false }

                if (!autoRotateOn) return

                val isLandscape = (orientation in 60..120) || (orientation in 240..300)
                val isPortrait = (orientation in 0..30) || (orientation in 330..359) || (orientation in 150..210)

                if (isLandscape && !isFullscreen) {
                    isFullscreen = true
                    activity?.let { act ->
                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        WindowCompat.getInsetsController(act.window, act.window.decorView).apply {
                            hide(WindowInsetsCompat.Type.systemBars())
                            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    }
                } else if (isPortrait && isFullscreen) {
                    isFullscreen = false
                    activity?.let { act ->
                        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        WindowCompat.getInsetsController(act.window, act.window.decorView).show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
        }
        if (listener.canDetectOrientation()) {
            listener.enable()
        }
        onDispose {
            listener.disable()
        }
    }

    val playbackKey = remember(url, type, headers, referer) {
        listOf(url, type, referer ?: "", headers?.toSortedMap()?.entries?.joinToString(";") { "${it.key}=${it.value}" } ?: "").joinToString("|")
    }
    val exoPlayer = remember(playbackKey) {
        val cleanUrl = url.trim()
        val requestHeaders = mutableMapOf<String, String>()
        referer?.let {
            if (it.isNotBlank() && !it.equals("never", ignoreCase = true)) {
                requestHeaders["Referer"] = it
            }
        }
        headers?.forEach { (k, v) ->
            if (v.isNotBlank() && !(k.equals("Referer", true) && v.equals("never", true))) {
                requestHeaders[k] = v
            }
        }

        val customUa = requestHeaders.entries.firstOrNull { it.key.equals("user-agent", ignoreCase = true) }?.let {
            requestHeaders.remove(it.key)
            it.value
        }
        val finalUa = customUa ?: BROWSER_UA

        val httpDataSourceFactory = DefaultHttpDataSource.Factory().apply {
            setUserAgent(finalUa)
            setAllowCrossProtocolRedirects(true)
            setConnectTimeoutMs(15000)
            setReadTimeoutMs(15000)
            if (requestHeaders.isNotEmpty()) {
                setDefaultRequestProperties(requestHeaders)
            }
        }

        val uri = Uri.parse(cleanUrl)
        val mediaItem = MediaItem.fromUri(uri)
        val isHls = cleanUrl.lowercase().contains(".m3u8") ||
                    type.lowercase().contains("m3u8") ||
                    type.lowercase().contains("hls") ||
                    type.lowercase().contains("application/x-mpegurl")

        val mediaSource = if (isHls) {
            HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
        }

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
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        duration = duration.coerceAtLeast(this@apply.duration.coerceAtLeast(0L))
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    android.util.Log.e("EmbeddedPlayer", "Playback error: ${error.errorCodeName} - ${error.message}")
                    SourceLogManager.error(
                        "player",
                        "播放失败",
                        "${error.errorCodeName}: ${error.message ?: "unknown"}",
                        "url=${cleanUrl.take(300)} type=$type headers=${requestHeaders.keys.joinToString(",")}"
                    )
                    playError = true
                    onError()
                }
            })
        }
    }

    // Twice per second is smooth enough for the progress bar and avoids
    // forcing four whole-player recompositions per second while decoding.
    LaunchedEffect(exoPlayer, isSeeking) {
        while (true) {
            if (!isSeeking) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                duration = exoPlayer.duration.coerceAtLeast(0L)
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

    LaunchedEffect(selectedQuality) {
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
        when (selectedQuality) {
            "1080p" -> builder.setMaxVideoSize(1920, 1080)
            "720p" -> builder.setMaxVideoSize(1280, 720)
            "480p" -> builder.setMaxVideoSize(854, 480)
            else -> builder.clearVideoSizeConstraints()
        }
        exoPlayer.trackSelectionParameters = builder.build()
    }

    LaunchedEffect(qualityEnhancement) {
        exoPlayer.videoScalingMode = if (qualityEnhancement) {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        } else {
            C.VIDEO_SCALING_MODE_SCALE_TO_FIT
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

    // Enter Picture-in-Picture helper
    val enterPip = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            try {
                activity.enterPictureInPictureMode(PictureInPictureParams.Builder().build())
            } catch (e: Exception) {
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
            .then(if (isFullscreen) Modifier.pointerInput(duration, isLocked) {
                var startX = 0f
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        totalX = 0f
                        totalY = 0f
                    },
                    onDrag = { change, amount ->
                        totalX += amount.x
                        totalY += amount.y
                        if (!isFullscreen || isLocked) return@detectDragGestures
                        if (kotlin.math.abs(totalY) > kotlin.math.abs(totalX)) {
                            val delta = (-amount.y / 900f).coerceIn(-0.08f, 0.08f)
                            val width = size.width.coerceAtLeast(1)
                            if (startX < width * 0.5f) {
                                adjustScreenBrightness(activity, delta)
                            } else {
                                audioManager?.let { manager ->
                                    val max = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                    val current = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    val step = (amount.y / -36f).toInt()
                                    manager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        (current + step).coerceIn(0, max),
                                        0
                                    )
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        if (!isFullscreen || isLocked || kotlin.math.abs(totalX) < kotlin.math.abs(totalY)) return@detectDragGestures
                        val width = size.width.coerceAtLeast(1)
                        val proportion = (totalX / width.toFloat()).coerceIn(-0.35f, 0.35f)
                        val deltaMs = if (kotlin.math.abs(totalX) < width * 0.35f) {
                            if (totalX > 0) 15_000L else -15_000L
                        } else {
                            (duration.coerceAtLeast(60_000L) * proportion).toLong()
                        }
                        exoPlayer.seekTo(
                            (exoPlayer.currentPosition + deltaMs)
                                .coerceIn(0L, exoPlayer.duration.coerceAtLeast(0L))
                        )
                    }
                )
            } else Modifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                    },
                    onLongPress = { offset ->
                        if (isFullscreen && !isLocked) {
                            currentSpeed = if (offset.x < size.width * 0.5f) 0.5f else maxSpeed
                        }
                    },
                    onDoubleTap = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    }
                )
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
                Text("请尝试点击「换源」切换到其他数据源", color = AppColors.muted, fontSize = 13.sp)
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        isClickable = false
                        isFocusable = false
                        setOnTouchListener { _, _ -> false }
                        this.resizeMode = resizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    view.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (danmakuEnabled && sentDanmaku.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 72.dp, end = 18.dp)
                    .widthIn(max = 280.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sentDanmaku.takeLast(6).forEach { message ->
                    Text(
                        message,
                        color = Color.White.copy(alpha = danmakuOpacity),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.28f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // ── Controls Overlay ──
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(Modifier.fillMaxSize()) {
                if (isLocked) {
                    // Lock Screen Only Button
                    IconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = "解锁屏幕", tint = AppColors.cyan)
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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
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

                    // ── RIGHT CENTER LOCK BUTTON ──
                    IconButton(
                        onClick = { isLocked = true },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                            .size(44.dp)
                    ) {
                        Text("🔓", fontSize = 18.sp)
                    }

                    // ── BOTTOM OVERLAY CONTAINER ──
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                                )
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        // ── ROW 1: Time, Slider, Duration, PiP, Fullscreen ──
                        Row(
                            modifier = Modifier.fillMaxWidth(),
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
                                    isSeeking = true
                                    sliderPosition = it
                                },
                                onValueChangeFinished = {
                                    exoPlayer.seekTo(sliderPosition.toLong())
                                    isSeeking = false
                                },
                                valueRange = 0f..(duration.coerceAtLeast(1L).toFloat()),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
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

                            // PiP Small Window Button (全屏左侧的画中画小窗按钮)
                            IconButton(
                                onClick = { enterPip() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                PipIcon(tint = Color.White)
                            }

                            Spacer(Modifier.width(4.dp))

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
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                if (isPlaying) {
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

                                // Line / Setting
                                IconButton(
                                    onClick = { showRatioMenu = true },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "清晰度/线路",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Danmaku Input Placeholder
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 6.dp)
                                    .height(28.dp)
                                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
                                    .clickable { showDanmakuSettings = true }
                                    .padding(horizontal = 12.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    "发送一条友善的弹幕吧~",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    selectedQuality,
                                    color = AppColors.cyan,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { showQualityMenu = true }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )

                                Spacer(Modifier.width(6.dp))

                                // Screen Scale Mode (默认 / 铺满 / 裁剪 / 拉伸)
                                Text(
                                    when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "铺满"
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "裁剪"
                                        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH -> "拉伸"
                                        else -> "默认"
                                    },
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .clickable {
                                            resizeMode = when (resizeMode) {
                                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
                                                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                            }
                                        }
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
            AlertDialog(
                onDismissRequest = { showSpeedMenu = false },
                title = { Text("选择播放倍速", color = AppColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        currentSpeed = speed
                                        showSpeedMenu = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${speed}x", color = if (currentSpeed == speed) AppColors.cyan else AppColors.text, fontSize = 14.sp)
                                if (currentSpeed == speed) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.cyan, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSpeedMenu = false }) {
                        Text("关闭", color = AppColors.cyan)
                    }
                },
                containerColor = AppColors.panel
            )
        }

        if (showQualityMenu) {
            AlertDialog(
                onDismissRequest = { showQualityMenu = false },
                title = { Text("清晰度与画质增强", color = AppColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        listOf("Auto", "1080p", "720p", "480p").forEach { quality ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedQuality = quality
                                        showQualityMenu = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(quality, color = if (selectedQuality == quality) AppColors.cyan else AppColors.text)
                                if (selectedQuality == quality) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.cyan)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("硬件画质增强", color = AppColors.text)
                                Text(
                                    if (lowRamDevice) "低内存设备已限制为最高 2x" else "开启后会增加 GPU/解码负载",
                                    color = AppColors.muted,
                                    fontSize = 11.sp
                                )
                            }
                            Switch(
                                checked = qualityEnhancement,
                                onCheckedChange = { qualityEnhancement = it }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showQualityMenu = false }) {
                        Text("关闭", color = AppColors.cyan)
                    }
                },
                containerColor = AppColors.panel
            )
        }

        if (showDanmakuSettings) {
            AlertDialog(
                onDismissRequest = { showDanmakuSettings = false },
                title = { Text("弹幕设置", color = AppColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("显示弹幕", color = AppColors.text)
                            Switch(checked = danmakuEnabled, onCheckedChange = { danmakuEnabled = it })
                        }
                        Text("透明度 ${(danmakuOpacity * 100).toInt()}%", color = AppColors.muted, fontSize = 12.sp)
                        Slider(
                            value = danmakuOpacity,
                            onValueChange = { danmakuOpacity = it },
                            valueRange = 0.25f..1f
                        )
                        OutlinedTextField(
                            value = danmakuDraft,
                            onValueChange = { danmakuDraft = it },
                            label = { Text("发送弹幕") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (danmakuDraft.isNotBlank()) {
                            sentDanmaku.add(danmakuDraft.trim())
                            danmakuDraft = ""
                        }
                        showDanmakuSettings = false
                    }) {
                        Text("发送并关闭", color = AppColors.cyan)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDanmakuSettings = false }) {
                        Text("取消", color = AppColors.muted)
                    }
                },
                containerColor = AppColors.panel
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
                            AspectRatioFrameLayout.RESIZE_MODE_FIT to "默认适应 (Fit)",
                            AspectRatioFrameLayout.RESIZE_MODE_FILL to "铺满屏幕 (Fill)",
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "裁剪拉伸 (Zoom)"
                        ).forEach { (mode, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        resizeMode = mode
                                        showRatioMenu = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, color = if (resizeMode == mode) AppColors.cyan else AppColors.text, fontSize = 14.sp)
                                if (resizeMode == mode) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.cyan, modifier = Modifier.size(18.dp))
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
