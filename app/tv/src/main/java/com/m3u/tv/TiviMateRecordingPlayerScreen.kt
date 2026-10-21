package com.m3u.tv

import android.net.Uri
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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File

/**
 * Dedicated VOD Player for Recorded Shows on Android TV.
 *
 * Implements standard YouTube/OTT player controls:
 * - Play / Pause
 * - Fast-forward +10s / Rewind -10s
 * - Live playback scrubber/progress bar (Current Time / Total Duration)
 * - Show title, channel name, recording date
 * - Completely isolated dedicated ExoPlayer (ZERO Live TV interference)
 */
@Composable
fun TiviMateRecordingPlayerScreen(
    recording: TvRecording,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val localFile = remember(recording.streamUrl, recording.id) {
        val f1 = File(recording.streamUrl)
        if (f1.exists() && f1.length() > 0L) f1
        else {
            val f2 = File(context.filesDir, "recordings/rec_${recording.id}.ts")
            if (f2.exists() && f2.length() > 0L) f2 else null
        }
    }

    var playbackError by remember {
        mutableStateOf<String?>(
            if (localFile == null) "Recorded video file is not available on device storage." else null
        )
    }
    val errorExitFocusRequester = remember { FocusRequester() }

    val recordingPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }
    var isPlaying by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(true) }
    var playerDurationMs by remember { mutableLongStateOf(0L) }

    DisposableEffect(recordingPlayer, localFile) {
        if (localFile != null) {
            val mediaItem = MediaItem.fromUri(Uri.fromFile(localFile))
            recordingPlayer.setMediaItem(mediaItem)
            recordingPlayer.prepare()
            recordingPlayer.play()
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val d = recordingPlayer.duration
                    if (d > 0L) playerDurationMs = d
                } else if (state == Player.STATE_ENDED) {
                    isPlaying = false
                    showControls = true
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage ?: "Failed to play recorded file."
            }
        }
        recordingPlayer.addListener(listener)

        onDispose {
            recordingPlayer.removeListener(listener)
            recordingPlayer.stop()
            recordingPlayer.release()
        }
    }

    val handleExit = {
        try { recordingPlayer.stop() } catch (_: Exception) {}
        onClose()
    }

    LaunchedEffect(playbackError) {
        if (playbackError != null) {
            delay(100)
            try {
                errorExitFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    if (playbackError != null) {
        BackHandler { handleExit() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                        event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                        handleExit()
                        true
                    } else false
                },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .widthIn(min = 360.dp, max = 500.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(TvColors.Surface)
                    .border(BorderStroke(1.dp, TvColors.BorderSubtle), RoundedCornerShape(16.dp))
                    .padding(32.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(TvColors.Danger.copy(alpha = 0.2f))
                        .border(BorderStroke(1.dp, TvColors.Danger), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = TvColors.Danger,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Recording Not Available",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TvFonts.Body
                )

                Text(
                    text = playbackError ?: "The recorded file for this broadcast was not found on device storage.",
                    color = TvColors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontFamily = TvFonts.Body
                )

                Spacer(modifier = Modifier.height(6.dp))

                RecordingControlButton(
                    icon = Icons.Rounded.Close,
                    label = "Exit to Recordings",
                    onClick = handleExit,
                    focusRequester = errorExitFocusRequester,
                    isPrimary = true
                )
            }
        }
        return
    }

    var currentPosMs by remember { mutableLongStateOf(0L) }
    var seekToastText by remember { mutableStateOf<String?>(null) }

    val totalDurationMs = remember(recording.durationSeconds, playerDurationMs) {
        val recMs = recording.durationSeconds * 1000L
        if (recMs > 0L) {
            recMs
        } else if (playerDurationMs > 0L) {
            playerDurationMs
        } else {
            1000L
        }
    }

    // Continuously read player position and enforce recorded duration limit
    LaunchedEffect(recordingPlayer, totalDurationMs) {
        while (true) {
            val rawPos = recordingPlayer.currentPosition.coerceAtLeast(0L)
            if (rawPos >= totalDurationMs) {
                if (recordingPlayer.isPlaying) {
                    recordingPlayer.pause()
                }
                currentPosMs = totalDurationMs
                isPlaying = false
                showControls = true
            } else {
                currentPosMs = rawPos
            }
            delay(150)
        }
    }

    // Auto-hide controls after 5 seconds of inactivity, and manage remote focus
    val playPauseFocusRequester = remember { FocusRequester() }
    val rewindFocusRequester = remember { FocusRequester() }
    val forwardFocusRequester = remember { FocusRequester() }
    val exitFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }
    val topCloseFocusRequester = remember { FocusRequester() }
    var controlsInteractionTimestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val hasEnded = currentPosMs >= totalDurationMs
    LaunchedEffect(hasEnded) {
        if (hasEnded) {
            showControls = true
            delay(80)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(showControls, controlsInteractionTimestamp, isPlaying, hasEnded) {
        if (showControls) {
            delay(80)
            try {
                playPauseFocusRequester.requestFocus()
            } catch (_: Exception) {}
            // ONLY auto-hide if actively playing and video has NOT ended!
            if (isPlaying && !hasEnded) {
                delay(5000)
                if (isPlaying && currentPosMs < totalDurationMs) {
                    showControls = false
                    try {
                        rootFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                }
            }
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

    BackHandler {
        handleExit()
    }

    LaunchedEffect(Unit) {
        delay(150)
        try {
            rootFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    fun seekBy(deltaMs: Long) {
        val target = (currentPosMs + deltaMs).coerceIn(0L, totalDurationMs)
        recordingPlayer.seekTo(target)
        currentPosMs = target
        if (target < totalDurationMs && !recordingPlayer.isPlaying && isPlaying) {
            recordingPlayer.play()
        }
        showControls = true
        controlsInteractionTimestamp = System.currentTimeMillis()
        val curSec = target / 1000
        val curFormatted = String.format("%02d:%02d", curSec / 60, curSec % 60)
        seekToastText = if (deltaMs > 0) "⏩ +10s ($curFormatted)" else "⏪ -10s ($curFormatted)"
    }

    fun togglePlayPause() {
        if (currentPosMs >= totalDurationMs) {
            recordingPlayer.seekTo(0L)
            currentPosMs = 0L
            recordingPlayer.play()
            isPlaying = true
        } else if (recordingPlayer.isPlaying) {
            recordingPlayer.pause()
            isPlaying = false
        } else {
            recordingPlayer.play()
            isPlaying = true
        }
        showControls = true
        controlsInteractionTimestamp = System.currentTimeMillis()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    val keyCode = event.nativeKeyEvent.keyCode
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        handleExit()
                        return@onPreviewKeyEvent true
                    }
                    if (!showControls) {
                        when (keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_DPAD_UP,
                            KeyEvent.KEYCODE_DPAD_DOWN,
                            KeyEvent.KEYCODE_DPAD_LEFT,
                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                showControls = true
                                controlsInteractionTimestamp = System.currentTimeMillis()
                                try { playPauseFocusRequester.requestFocus() } catch (_: Exception) {}
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                togglePlayPause()
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                seekBy(10000L)
                                true
                            }
                            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                                seekBy(-10000L)
                                true
                            }
                            else -> false
                        }
                    } else false // When showControls == true, DO NOT intercept! Let child buttons handle navigation and actions!
                } else false
            }
            .clickable { showControls = !showControls }
            .focusable()
    ) {
        // 1. VIDEO SURFACE
        TiviMateVideoPlayer(
            player = recordingPlayer,
            modifier = Modifier.fillMaxSize()
        )

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
                        onClick = handleExit,
                        focusRequester = topCloseFocusRequester,
                        onNavigateDown = { playPauseFocusRequester.requestFocus() },
                        onNavigateUp = { /* trap */ },
                        onNavigateLeft = { /* trap */ },
                        onNavigateRight = { /* trap */ },
                        onBack = handleExit
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
                    val totalSec = totalDurationMs / 1000
                    val formattedTotal = String.format("%02d:%02d", totalSec / 60, totalSec % 60)
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
                            text = formattedTotal,
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
                                focusRequester = rewindFocusRequester,
                                onClick = {
                                    controlsInteractionTimestamp = System.currentTimeMillis()
                                    seekBy(-10000L)
                                },
                                onNavigateLeft = { /* trap */ },
                                onNavigateRight = { playPauseFocusRequester.requestFocus() },
                                onNavigateUp = { topCloseFocusRequester.requestFocus() },
                                onNavigateDown = {
                                    if (isPlaying && currentPosMs < totalDurationMs) {
                                        showControls = false
                                        rootFocusRequester.requestFocus()
                                    }
                                },
                                onBack = handleExit
                            )

                            RecordingControlButton(
                                icon = if (isPlaying && currentPosMs < totalDurationMs) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                label = if (isPlaying && currentPosMs < totalDurationMs) "Pause" else "Play",
                                onClick = {
                                    controlsInteractionTimestamp = System.currentTimeMillis()
                                    togglePlayPause()
                                },
                                focusRequester = playPauseFocusRequester,
                                isPrimary = true,
                                onNavigateLeft = { rewindFocusRequester.requestFocus() },
                                onNavigateRight = { forwardFocusRequester.requestFocus() },
                                onNavigateUp = { topCloseFocusRequester.requestFocus() },
                                onNavigateDown = {
                                    if (isPlaying && currentPosMs < totalDurationMs) {
                                        showControls = false
                                        rootFocusRequester.requestFocus()
                                    }
                                },
                                onBack = handleExit
                            )

                            RecordingControlButton(
                                icon = Icons.Rounded.Forward10,
                                label = "+10s",
                                focusRequester = forwardFocusRequester,
                                onClick = {
                                    controlsInteractionTimestamp = System.currentTimeMillis()
                                    seekBy(10000L)
                                },
                                onNavigateLeft = { playPauseFocusRequester.requestFocus() },
                                onNavigateRight = { exitFocusRequester.requestFocus() },
                                onNavigateUp = { topCloseFocusRequester.requestFocus() },
                                onNavigateDown = {
                                    if (isPlaying && currentPosMs < totalDurationMs) {
                                        showControls = false
                                        rootFocusRequester.requestFocus()
                                    }
                                },
                                onBack = handleExit
                            )

                            RecordingControlButton(
                                icon = Icons.Rounded.Close,
                                label = "Exit",
                                focusRequester = exitFocusRequester,
                                onClick = handleExit,
                                onNavigateLeft = { forwardFocusRequester.requestFocus() },
                                onNavigateRight = { /* trap */ },
                                onNavigateUp = { topCloseFocusRequester.requestFocus() },
                                onNavigateDown = {
                                    if (isPlaying && currentPosMs < totalDurationMs) {
                                        showControls = false
                                        rootFocusRequester.requestFocus()
                                    }
                                },
                                onBack = handleExit
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
    onNavigateRight: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null
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
                        KeyEvent.KEYCODE_BACK -> {
                            if (onBack != null) {
                                onBack()
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
