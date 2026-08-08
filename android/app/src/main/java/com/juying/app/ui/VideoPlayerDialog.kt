package com.juying.app.ui

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.juying.app.AppColors
import com.juying.app.source.PlayResult

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VideoPlayerDialog(
    title: String,
    episodeName: String,
    playResult: PlayResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentSpeed by remember { mutableStateOf(1.0f) }
    var resizeMode by remember { mutableStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    val exoPlayer = remember(playResult.url) {
        val cleanUrl = playResult.url.trim()
        val requestHeaders = mutableMapOf<String, String>()
        playResult.referer?.let { if (it.isNotBlank()) requestHeaders["Referer"] = it }
        playResult.headers?.forEach { (k, v) ->
            if (v.isNotBlank()) requestHeaders[k] = v
        }

        val customUa = requestHeaders.entries.firstOrNull { it.key.equals("user-agent", ignoreCase = true) }?.let {
            requestHeaders.remove(it.key)
            it.value
        }
        val finalUa = customUa ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        val httpDataSourceFactory = OkHttpDataSource.Factory(
            okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        ).apply {
            setUserAgent(finalUa)
            if (requestHeaders.isNotEmpty()) {
                setDefaultRequestProperties(requestHeaders)
            }
        }

        val uri = Uri.parse(cleanUrl)
        val mediaItem = MediaItem.fromUri(uri)
        val isHls = cleanUrl.lowercase().contains(".m3u8") ||
                    playResult.type.lowercase().contains("m3u8") ||
                    playResult.type.lowercase().contains("hls") ||
                    playResult.type.lowercase().contains("application/x-mpegurl")

        val mediaSource = if (isHls) {
            HlsMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
        } else {
            ProgressiveMediaSource.Factory(httpDataSourceFactory).createMediaSource(mediaItem)
        }

        ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(currentSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(currentSpeed)
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                        this.resizeMode = resizeMode
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view ->
                    if (view.player != exoPlayer) {
                        view.player = exoPlayer
                    }
                    view.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = AppColors.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = episodeName,
                        color = AppColors.cyan,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }

                // Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Aspect ratio mode
                    TextButton(onClick = {
                        resizeMode = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    }) {
                        Text(
                            text = when (resizeMode) {
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "铺满"
                                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "裁剪"
                                else -> "适应"
                            },
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }

                    // Speed button
                    TextButton(onClick = { showSpeedMenu = !showSpeedMenu }) {
                        Text("${currentSpeed}x", color = AppColors.cyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = Color.White
                        )
                    }
                }
            }

            // Speed Selector Overlay
            if (showSpeedMenu) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showSpeedMenu = false },
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Card(
                        modifier = Modifier.padding(24.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.panel)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("播放倍速", color = AppColors.text, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                TextButton(
                                    onClick = {
                                        currentSpeed = speed
                                        showSpeedMenu = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "${speed}x",
                                        color = if (speed == currentSpeed) AppColors.cyan else AppColors.text,
                                        fontWeight = if (speed == currentSpeed) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
