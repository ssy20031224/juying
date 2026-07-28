package com.juying.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 画中画(PiP)状态桥：在播放器 Composable 与 MainActivity 之间共享
 * “是否允许进入画中画”以及小窗遥控按钮（播放/暂停、上/下集）回调。
 *
 * 只有播放器在屏且正在播放时，退到后台才允许进入小窗；
 * 离开播放器（首页/片库等）后不再触发画中画。
 */
object PipController {
    /** 播放器正在前台展示且可播放（onUserLeaveHint 是否允许进入画中画的条件之一） */
    var playerActive by mutableStateOf(false)
    /** 当前是否正在播放（决定暂停/播放图标，以及是否允许进入小窗） */
    var isPlaying by mutableStateOf(false)
    /** 当前是否处于画中画模式（小窗内隐藏播放器顶部栏/进度条等控制层） */
    var inPipMode by mutableStateOf(false)
    var hasNext by mutableStateOf(false)
    var hasPrev by mutableStateOf(false)

    var onTogglePlayPause: (() -> Unit)? = null
    var onNextEpisode: (() -> Unit)? = null
    var onPrevEpisode: (() -> Unit)? = null

    fun clear() {
        playerActive = false
        isPlaying = false
        hasNext = false
        hasPrev = false
        onTogglePlayPause = null
        onNextEpisode = null
        onPrevEpisode = null
    }
}
