package com.m3u.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

/**
 * Dedicated VOD Player for Recorded Shows on Android TV.
 *
 * Implements standard YouTube/OTT player controls:
 * - Play / Pause
 * - Fast-forward +10s / Rewind -10s
 * - Live playback scrubber/progress bar (Current Time / Total Duration)
 * - Show title, channel name, recording date
 * - ZERO Live TV clutter (No Record button, No TV Guide button, No channel switcher)
 */
@Composable
fun TiviMateRecordingPlayerScreen(
    player: Player?,
    recording: TvRecording,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onClose: () -> Unit
) {
    var showControls by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var seekToastText by remember { mutableStateOf<String?>(null) }

    val totalDurationMs = remember(recording) {
        (recording.durationSeconds * 1000L).coerceAtLeast(1000L)
    }

    // Continuously read player position
    LaunchedEffect(player) {
        while (true) {
            currentPosMs = player?.currentPosition ?: 0L
            delay(250)
        }
    }

    // Auto-hide controls after 5 seconds of inactivity, and manage remote focus
    val playPauseFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }
    val topCloseFocusRequester = remember { FocusRequester() }
    var controlsInteractionTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(showControls, controlsInteractionTimestamp) {
        if (showControls) {
            delay(100)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
            delay(5000)
            showControls = false
            try {
                rootFocusRequester.requestFocus()
            } catch (_: Exception) {}
        } else {
            delay(50)
            try {
                rootFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    // Auto-hide seek toast
    LaunchedEffect(seekToastText) {
        if (seekToastText != null) {
            delay(1500)
            seekToastText = null
        }
    }

    BackHandler(enabled = showControls) {
        showControls = false
        try {
            rootFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    BackHandler(enabled = !showControls) {
        onClose()
    }

    LaunchedEffect(Unit) {
        delay(150)
        try {
            rootFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    fun seekBy(deltaMs: Long) {
        player?.let { p ->
            val target = (p.currentPosition + deltaMs).coerceIn(0L, totalDurationMs)
            p.seekTo(target)
            currentPosMs = target
            showControls = true
            val curSec = target / 1000
            val curFormatted = String.format("%02d:%02d", curSec / 60, curSec % 60)
            seekToastText = if (deltaMs > 0) "⏩ +10s ($curFormatted)" else "⏪ -10s ($curFormatted)"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            onPlayPause()
                            showControls = true
                            controlsInteractionTimestamp = System.currentTimeMillis()
                            try { playPauseFocusRequester.requestFocus() } catch (_: Exception) {}
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            seekBy(-10000L)
                            controlsInteractionTimestamp = System.currentTimeMillis()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            seekBy(10000L)
                            controlsInteractionTimestamp = System.currentTimeMillis()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                            showControls = true
                            controlsInteractionTimestamp = System.currentTimeMillis()
                            try { playPauseFocusRequester.requestFocus() } catch (_: Exception) {}
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            onPlayPause()
                            showControls = true
                            controlsInteractionTimestamp = System.currentTimeMillis()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            seekBy(10000L)
                            controlsInteractionTimestamp = System.currentTimeMillis()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            seekBy(-10000L)
                            controlsInteractionTimestamp = System.currentTimeMillis()
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            onClose()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable { showControls = !showControls }
            .focusable()
    ) {
        // 1. VIDEO SURFACE
        if (player != null) {
            TiviMateVideoPlayer(
                player = player,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                TvLoadingSpinner(
                    modifier = Modifier.size(40.dp),
                    color = TvColors.Focus,
                    strokeWidth = 3.dp
                )
            }
        }

        // 2. SEEK TOAST
        AnimatedVisibility(
            visible = seekToastText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(BorderStroke(1.dp, TvColors.FocusGlow), RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 14.dp)
            ) {
                Text(
                    text = seekToastText ?: "",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TvFonts.Body
                )
            }
        }

        // 3. TOP & BOTTOM PLAYER CONTROLS (AUTOHIDES)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // TOP BAR (Show Title, Channel Name, Date)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.85f),
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        if (!recording.coverUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = recording.coverUrl,
                                contentDescription = recording.channelName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TvColors.SurfaceHighlight)
                            )
                        } else {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TvColors.SurfaceHighlight)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tv,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = recording.channelName,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = TvFonts.Body
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(TvColors.Danger)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "RECORDED",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = TvFonts.Body
                                    )
                                }
                            }

                            Text(
                                text = "${recording.showTitle} • ${recording.formattedDate}",
                                color = TvColors.TextSecondary,
                                fontSize = 13.sp,
                                fontFamily = TvFonts.Body
                            )
                        }
                    }

                    // Top-right Close Button
                    RecordingControlButton(
                        icon = Icons.Rounded.Close,
                        label = "Close",
                        onClick = onClose,
                        focusRequester = topCloseFocusRequester,
                        onNavigateDown = { playPauseFocusRequester.requestFocus() },
                        onNavigateUp = { /* trap */ },
                        onNavigateLeft = { /* trap */ },
                        onNavigateRight = { /* trap */ }
                    )
                }

                // BOTTOM BAR (Scrubber, Timers, Play/Pause, Rewind, Fast Forward)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Black.copy(alpha = 0.92f)
                                )
                            )
                        )
                        .padding(horizontal = 32.dp, vertical = 24.dp)
                ) {
                    val curSec = currentPosMs / 1000
                    val formattedCur = String.format("%02d:%02d", curSec / 60, curSec % 60)
                    val progressRatio = (currentPosMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)

                    // 1. Progress Bar / Timeline Scrubber
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressRatio)
                                .height(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(TvColors.FocusGlow)
                        )
                    }

                    // 2. Time Indicators (Current / Total)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 16.dp)
                    ) {
                        Text(
                            text = formattedCur,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TvFonts.Body
                        )
                        Text(
                            text = recording.formattedDuration,
                            color = TvColors.TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = TvFonts.Body
                        )
                    }

                    // 3. Playback Controls Row (Rewind, Play/Pause, Fast Forward, Close)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            RecordingControlButton(
                                icon = Icons.Rounded.Replay10,
                                label = "-10s",
                                onClick = {
                                    controlsInteractionTimestamp = System.currentTimeMillis()
                                    seekBy(-10000L)
                                },
                                onNavigateLeft = { /* trap */ },
                                onNavigateUp = { topCloseFocusRequester.requestFocus() },
                                onNavigateDown = {
                                    showControls = false
                                    rootFocusRequester.requestFocus()
                                }
                            )

                            RecordingControlButton(
                                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                label = if (isPlaying) "Pause" else "Play",
                                onClick = {
                                    controlsInteractionTimestamp = System.currentTimeMillis()
                                    onPlayPause()
                                },
                                focusRequester = playPauseFocusRequester,
                                isPrimary = true,
                                onNavigateUp = { topCloseFocusRequester.requestFocus() },
                                onNavigateDown = {
                                    showControls = false
                                    rootFocusRequester.requestFocus()
                                }
                            )

                            RecordingControlButton(
                                icon = Icons.Rounded.Forward10,
                                label = "+10s",
                                onClick = {
                                    controlsInteractionTimestamp = System.currentTimeMillis()
                                    seekBy(10000L)
                                },
                                onNavigateUp = { topCloseFocusRequester.requestFocus() },
                                onNavigateDown = {
                                    showControls = false
                                    rootFocusRequester.requestFocus()
                                }
                            )

                            RecordingControlButton(
                                icon = Icons.Rounded.Close,
                                label = "Exit",
                                onClick = onClose,
                                onNavigateRight = { /* trap */ },
                                onNavigateUp = { topCloseFocusRequester.requestFocus() },
                                onNavigateDown = {
                                    showControls = false
                                    rootFocusRequester.requestFocus()
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
private fun RecordingControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    isPrimary: Boolean = false,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateRight: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> TvColors.Focus
                    isPrimary -> TvColors.SurfaceHighlight
                    else -> TvColors.SurfaceRaised
                }
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (isFocused) TvColors.FocusGlow else TvColors.BorderSubtle
                ),
                RoundedCornerShape(8.dp)
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER -> {
                            onClick()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (onNavigateUp != null) {
                                onNavigateUp()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (onNavigateDown != null) {
                                onNavigateDown()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (onNavigateLeft != null) {
                                onNavigateLeft()
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (onNavigateRight != null) {
                                onNavigateRight()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isFocused || isPrimary) Color.White else TvColors.TextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = TvFonts.Body
        )
    }
}
