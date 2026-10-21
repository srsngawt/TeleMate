package com.m3u.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.m3u.data.database.model.Channel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun TiviMatePlayerScreen(
    player: Player?,
    channel: Channel?,
    channelList: List<Channel>,
    categoryChannels: List<Channel> = channelList,
    currentCategoryName: String = "",
    favoriteChannels: List<Channel>,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSwitchChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onBackToGuide: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showInfobar by remember { mutableStateOf(true) }
    var showQuickChannelDrawer by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var zappingToastText by remember { mutableStateOf<String?>(null) }
    var liveDelaySeconds by remember(channel?.id) { mutableLongStateOf(0L) }

    val activeZapList = remember(categoryChannels, channelList) {
        if (categoryChannels.isNotEmpty()) categoryChannels else channelList
    }

    val currentChannelIndex = remember(channel?.id, activeZapList) {
        val idx = activeZapList.indexOfFirst { it.id == channel?.id }
        if (idx >= 0) idx else 0
    }

    val playerFocusRequester = remember { FocusRequester() }
    val actionButtonsFocusRequester = remember { FocusRequester() }
    val sliderFocusRequester = remember { FocusRequester() }
    val drawerFocusRequester = remember { FocusRequester() }
    val drawerCloseFocusRequester = remember { FocusRequester() }
    val drawerListState = androidx.compose.foundation.lazy.rememberLazyListState()
    var currentRecordingId by remember { mutableStateOf<String?>(null) }
    var recordingStartTimeMs by remember { mutableLongStateOf(0L) }

    // Timeshift / Live seeking logic
    val onSeekBy: (Long) -> Unit = { deltaSec ->
        player?.let { p ->
            val newDelay = maxOf(0L, liveDelaySeconds - deltaSec)
            liveDelaySeconds = newDelay
            if (newDelay == 0L) {
                try {
                    p.seekToDefaultPosition()
                } catch (_: Exception) {}
            } else {
                val currentPos = p.currentPosition
                val targetPos = maxOf(0L, currentPos + (deltaSec * 1000L))
                try {
                    p.seekTo(targetPos)
                } catch (_: Exception) {}
            }
        }
    }

    val onGoLive: () -> Unit = {
        liveDelaySeconds = 0L
        try {
            player?.seekToDefaultPosition()
        } catch (_: Exception) {}
    }

    // Live Recording Timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (true) {
                delay(500)
                if (recordingStartTimeMs > 0L) {
                    recordingSeconds = ((System.currentTimeMillis() - recordingStartTimeMs) / 1000L).toInt()
                } else {
                    recordingSeconds++
                }
            }
        }
    }

    // Auto-hide infobar after 8 seconds of inactivity
    LaunchedEffect(showInfobar, showLanguageDialog, showQualityDialog, showQuickChannelDrawer) {
        if (showInfobar && !showLanguageDialog && !showQualityDialog && !showQuickChannelDrawer) {
            delay(8000)
            showInfobar = false
        }
    }

    // Auto-focus controls when infobar or drawer opens, and restore focus to player when closed
    LaunchedEffect(showInfobar, showQuickChannelDrawer, showLanguageDialog, showQualityDialog) {
        when {
            showLanguageDialog || showQualityDialog -> {
                // Dialog handles its own focus
            }
            showQuickChannelDrawer -> {
                showInfobar = false
                val targetIndex = currentChannelIndex.coerceIn(0, maxOf(0, activeZapList.size - 1))
                try {
                    drawerListState.scrollToItem(maxOf(0, targetIndex - 2))
                } catch (_: Exception) {}
                delay(80)
                try {
                    drawerFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
            showInfobar -> {
                delay(80)
                try {
                    actionButtonsFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
            else -> {
                // When ALL overlays (infobar, drawer, dialogs) close:
                // Focus MUST be immediately returned to the root player!
                delay(50)
                try {
                    playerFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        }
    }

    // Auto-hide zapping toast
    LaunchedEffect(zappingToastText) {
        if (zappingToastText != null) {
            delay(2000)
            zappingToastText = null
        }
    }

    val isFavorite = remember(channel, favoriteChannels) {
        channel != null && favoriteChannels.any { it.id == channel.id }
    }

    // Instant Channel Zapping strictly within current section/category
    val zapNext = {
        if (activeZapList.isNotEmpty()) {
            val nextIndex = (currentChannelIndex + 1) % activeZapList.size
            val nextCh = activeZapList[nextIndex]
            onSwitchChannel(nextCh)
            val catPrefix = if (currentCategoryName.isNotBlank() && currentCategoryName != "All channels") "[$currentCategoryName] " else ""
            zappingToastText = "$catPrefix#${nextIndex + 1} ${nextCh.title}"
        }
    }

    val zapPrev = {
        if (activeZapList.isNotEmpty()) {
            val prevIndex = if (currentChannelIndex - 1 < 0) activeZapList.size - 1 else currentChannelIndex - 1
            val prevCh = activeZapList[prevIndex]
            onSwitchChannel(prevCh)
            val catPrefix = if (currentCategoryName.isNotBlank() && currentCategoryName != "All channels") "[$currentCategoryName] " else ""
            zappingToastText = "$catPrefix#${prevIndex + 1} ${prevCh.title}"
        }
    }

    // Resolve UnifiedChannel for current channel
    val currentUnified = remember(channel, channelList, favoriteChannels) {
        if (channel == null) null
        else {
            val favIds = favoriteChannels.map { it.id }.toSet()
            val allUnified = TiviMateChannelParser.groupChannels(channelList, favIds)
            allUnified.find { u -> u.variants.any { it.channel.id == channel.id } }
        }
    }

    val stopAndSaveRecording: () -> Unit = {
        if (isRecording) {
            isRecording = false
            val filePath = StreamRecorder.stop()
            val recId = currentRecordingId ?: java.util.UUID.randomUUID().toString()
            val localFile = java.io.File(context.filesDir, "recordings/rec_${recId}.ts")
            val savedPath = filePath ?: localFile.absolutePath
            val elapsedSec = if (recordingStartTimeMs > 0L) {
                ((System.currentTimeMillis() - recordingStartTimeMs) / 1000L).toInt()
            } else {
                recordingSeconds
            }
            val finalDuration = maxOf(1, elapsedSec)
            val rec = TvRecording(
                id = recId,
                channelId = channel?.id ?: 0,
                channelName = channel?.title ?: "Live Channel",
                showTitle = "${currentUnified?.baseName ?: channel?.title ?: "Live Channel"} Broadcast",
                coverUrl = channel?.cover,
                streamUrl = savedPath,
                durationSeconds = finalDuration,
                timestamp = System.currentTimeMillis()
            )
            RecordingsManager.saveRecording(context, rec)
            currentRecordingId = null
            recordingStartTimeMs = 0L
            zappingToastText = "⏹️ Recording saved (${rec.formattedDuration})"
        }
    }

    BackHandler {
        if (isRecording) {
            stopAndSaveRecording()
            return@BackHandler
        }
        when {
            showLanguageDialog -> showLanguageDialog = false
            showQualityDialog -> showQualityDialog = false
            showQuickChannelDrawer -> showQuickChannelDrawer = false
            showInfobar -> showInfobar = false
            else -> onBackToGuide()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopAndSaveRecording()
        }
    }

    // Active variant info
    val activeVariant = remember(channel, currentUnified) {
        currentUnified?.variants?.find { it.channel.id == channel?.id }
    }
    val activeLanguage = activeVariant?.language ?: currentUnified?.defaultLanguage ?: ""
    val cleanPlayerLang = TiviMateChannelParser.normalizeLanguage(activeLanguage)
    val activeQuality = activeVariant?.quality ?: currentUnified?.defaultQuality ?: "HD"

    // Detect actual active video track resolution from ExoPlayer
    val detectedTrackQuality = remember(player?.currentTracks) {
        var topQual: String? = null
        player?.currentTracks?.groups?.forEach { group ->
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    if (group.isTrackSelected(i)) {
                        val format = group.getTrackFormat(i)
                        topQual = when {
                            format.height >= 2160 -> "4K UHD"
                            format.height >= 1080 -> "1080p FHD"
                            format.height >= 720 -> "720p HD"
                            format.height >= 480 -> "480p SD"
                            else -> null
                        }
                    }
                }
            }
        }
        topQual
    }

    // Dynamic User-Selected Quality Override (persists across dialog opens)
    var userSelectedQuality by remember(channel?.id) { mutableStateOf<String?>(null) }
    val displayedQuality = userSelectedQuality ?: activeVariant?.quality?.takeIf { it.isNotBlank() } ?: detectedTrackQuality ?: activeQuality

    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTimeString = sdf.format(Date())
            delay(1000)
        }
    }

    LaunchedEffect(channel?.id) {
        showInfobar = true
        liveDelaySeconds = 0L
        delay(80)
        try {
            actionButtonsFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        delay(120)
        try {
            actionButtonsFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val isAnyPopupOpen = showLanguageDialog || showQualityDialog

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = !isAnyPopupOpen }
                .focusRequester(playerFocusRequester)
                .clickable {
                    if (!isAnyPopupOpen && !showQuickChannelDrawer) {
                        showInfobar = !showInfobar
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (isAnyPopupOpen) return@onPreviewKeyEvent false
                    val action = event.nativeKeyEvent.action
                    val keyCode = event.nativeKeyEvent.keyCode
                    if (action == KeyEvent.ACTION_DOWN) {
                        if (isRecording && !showQuickChannelDrawer) {
                            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                keyCode == KeyEvent.KEYCODE_ENTER ||
                                keyCode == KeyEvent.KEYCODE_BACK) {
                                stopAndSaveRecording()
                                return@onPreviewKeyEvent true
                            }
                        }
                        if (!showInfobar && !showQuickChannelDrawer) {
                            when (keyCode) {
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    showInfobar = true
                                    coroutineScope.launch {
                                        delay(60)
                                        try {
                                            actionButtonsFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    showInfobar = true
                                    coroutineScope.launch {
                                        delay(60)
                                        try {
                                            actionButtonsFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                    onBackToGuide()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MENU -> {
                                    showInfobar = false
                                    showQuickChannelDrawer = true
                                    true
                                }
                                KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                                    zapNext()
                                    true
                                }
                                KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                                    zapPrev()
                                    true
                                }
                                KeyEvent.KEYCODE_BACK -> {
                                    onBackToGuide()
                                    true
                                }
                                else -> false
                            }
                        } else {
                            if (showQuickChannelDrawer) {
                                when (keyCode) {
                                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_BACK -> {
                                        showQuickChannelDrawer = false
                                        try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
                                        true
                                    }
                                    else -> false
                                }
                            } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                                if (showInfobar) {
                                    showInfobar = false
                                    try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
                                    true
                                } else false
                            } else false
                        }
                    } else false
                }
            .focusable()
        ) {
        // 1. FULLSCREEN VIDEO PLAYER
        if (player != null) {
            key(channel?.id, player) {
                TiviMateVideoPlayer(
                    player = player,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                TvLoadingSpinner(
                    modifier = Modifier.size(40.dp),
                    color = TvColors.Focus,
                    strokeWidth = 3.dp
                )
            }
        }

        // 1B. LIVE RECORDING BANNER OVERLAY
        if (isRecording) {
            val m = recordingSeconds / 60
            val s = recordingSeconds % 60
            val recTime = String.format("%02d:%02d", m, s)
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 32.dp, top = 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.85f))
                    .border(BorderStroke(1.dp, TvColors.Danger), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(TvColors.Danger)
                    )
                    Text(
                        text = "REC $recTime",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvFonts.Body
                    )
                }
            }
        }

        // 2. QUICK CHANNEL ZAPPING TOAST
        AnimatedVisibility(
            visible = zappingToastText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(TvColors.Surface.copy(alpha = 0.95f))
                    .border(BorderStroke(1.dp, TvColors.Focus), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = zappingToastText ?: "",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TvFonts.Body
                )
            }
        }

        // 3. TIVIMATE BOTTOM INFOBAR & ACTIONS (Matches Screenshot 7.26.18 PM)
        AnimatedVisibility(
            visible = showInfobar,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, TvColors.Background.copy(alpha = 0.85f), TvColors.Background)
                        )
                    )
                    .padding(horizontal = 40.dp, vertical = 24.dp)
            ) {
                // Program Card & Channel Info Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        // Channel Logo Box
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(TvColors.SurfaceRaised)
                                .border(BorderStroke(1.dp, TvColors.BorderSubtle), RoundedCornerShape(10.dp))
                        ) {
                            val coverUrl = channel?.cover ?: currentUnified?.cover
                            if (!coverUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = coverUrl,
                                    contentDescription = channel?.title,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.size(46.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Tv,
                                    contentDescription = null,
                                    tint = TvColors.TextMuted,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Program Name & Details
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "${currentChannelIndex + 1}. ${currentUnified?.baseName ?: channel?.title ?: "Live Channel"}",
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = TvFonts.Body,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Quality Pill Badge
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(TvColors.BadgeBackground)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = displayedQuality,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = TvFonts.Body
                                    )
                                }

                                // Language Pill Badge
                                if (cleanPlayerLang.isNotBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(TvColors.Focus)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = cleanPlayerLang,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = TvFonts.Body
                                        )
                                    }
                                }

                                // LIVE / TIME SHIFT badge
                                if (liveDelaySeconds > 0L) {
                                    val m = liveDelaySeconds / 60
                                    val s = liveDelaySeconds % 60
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(TvColors.Warning)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = String.format("-%02d:%02d", m, s),
                                            color = Color.Black,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = TvFonts.Body
                                        )
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(TvColors.Danger)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "LIVE",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = TvFonts.Body
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = channel?.category?.takeIf { it.isNotBlank() } ?: "Live Stream",
                                color = TvColors.TextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = TvFonts.Body
                            )
                        }
                    }

                    // Live Clock
                    Text(
                        text = currentTimeString,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvFonts.Body
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                val closeControlsAndFocusPlayer: () -> Unit = {
                    showInfobar = false
                    try {
                        playerFocusRequester.requestFocus()
                    } catch (_: Exception) {}
                }

                // Interactive Video Seek Slider
                TiviMateSeekSlider(
                    focusRequester = sliderFocusRequester,
                    liveDelaySeconds = liveDelaySeconds,
                    onSeekBy = onSeekBy,
                    onGoLive = onGoLive,
                    onNavigateDown = {
                        try {
                            actionButtonsFocusRequester.requestFocus()
                        } catch (_: Exception) {}
                    },
                    onCloseControls = closeControlsAndFocusPlayer
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Action Row (TiviMate Tile Actions)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TiviMateActionButton(
                        icon = Icons.Rounded.Tv,
                        label = "TV guide",
                        focusRequester = actionButtonsFocusRequester,
                        onNavigateUp = { sliderFocusRequester.requestFocus() },
                        onNavigateDown = closeControlsAndFocusPlayer,
                        onNavigateLeft = { /* Trap focus on left */ },
                        onClick = onBackToGuide
                    )

                    TiviMateActionButton(
                        icon = Icons.Rounded.Language,
                        label = if (cleanPlayerLang.isNotBlank()) "Language ($cleanPlayerLang)" else "Language",
                        onNavigateUp = { sliderFocusRequester.requestFocus() },
                        onNavigateDown = closeControlsAndFocusPlayer,
                        onClick = { showLanguageDialog = true }
                    )

                    TiviMateActionButton(
                        icon = Icons.Rounded.HighQuality,
                        label = "Quality ($displayedQuality)",
                        onNavigateUp = { sliderFocusRequester.requestFocus() },
                        onNavigateDown = closeControlsAndFocusPlayer,
                        onClick = { showQualityDialog = true }
                    )

                    TiviMateActionButton(
                        icon = Icons.Rounded.FiberManualRecord,
                        label = if (isRecording) {
                            val m = recordingSeconds / 60
                            val s = recordingSeconds % 60
                            String.format("REC %02d:%02d", m, s)
                        } else "Record",
                        tint = if (isRecording) TvColors.Danger else Color.White,
                        onNavigateUp = { sliderFocusRequester.requestFocus() },
                        onNavigateDown = closeControlsAndFocusPlayer,
                        onClick = {
                            if (!isRecording) {
                                val recId = java.util.UUID.randomUUID().toString()
                                currentRecordingId = recId
                                isRecording = true
                                recordingSeconds = 0
                                recordingStartTimeMs = System.currentTimeMillis()
                                val currentPlayingUrl = player?.currentMediaItem?.localConfiguration?.uri?.toString()
                                    ?.takeIf { it.isNotBlank() } ?: channel?.url ?: ""
                                val targetVideoHeight = run {
                                    var h = player?.videoSize?.height ?: 0
                                    if (h <= 0) {
                                        player?.currentTracks?.groups?.forEach { group ->
                                            if (group.type == C.TRACK_TYPE_VIDEO) {
                                                for (i in 0 until group.length) {
                                                    if (group.isTrackSelected(i)) {
                                                        val format = group.getTrackFormat(i)
                                                        if (format.height > h) h = format.height
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    h
                                }
                                val playbackOffsetFromLiveMs = run {
                                    val liveOffset = player?.currentLiveOffset ?: C.TIME_UNSET
                                    if (liveOffset != C.TIME_UNSET && liveOffset > 0L) {
                                        liveOffset
                                    } else {
                                        val dur = player?.duration ?: -1L
                                        val pos = player?.currentPosition ?: -1L
                                        if (dur > 0L && pos in 0L..dur) {
                                            dur - pos
                                        } else {
                                            15_000L + (liveDelaySeconds * 1000L)
                                        }
                                    }
                                }
                                if (currentPlayingUrl.isNotBlank()) {
                                    StreamRecorder.start(
                                        context = context,
                                        recId = recId,
                                        streamUrl = currentPlayingUrl,
                                        targetHeight = targetVideoHeight,
                                        playbackOffsetFromLiveMs = playbackOffsetFromLiveMs
                                    )
                                }
                                zappingToastText = "🔴 Recording started: ${channel?.title ?: "Stream"}"
                            } else {
                                stopAndSaveRecording()
                            }
                        }
                    )

                    TiviMateActionButton(
                        icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        label = if (isFavorite) "Favorite" else "Add Favorite",
                        tint = if (isFavorite) TvColors.Danger else Color.White,
                        onNavigateUp = { sliderFocusRequester.requestFocus() },
                        onNavigateDown = closeControlsAndFocusPlayer,
                        onClick = { channel?.let(onToggleFavorite) }
                    )

                    TiviMateActionButton(
                        icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                        label = "Channels",
                        onNavigateUp = { sliderFocusRequester.requestFocus() },
                        onNavigateDown = closeControlsAndFocusPlayer,
                        onNavigateRight = { /* Trap focus on right */ },
                        onClick = {
                            showInfobar = false
                            showQuickChannelDrawer = true
                        }
                    )
                }
            }
        }

        // 4. QUICK CHANNEL DRAWER (Left Sidebar)
        AnimatedVisibility(
            visible = showQuickChannelDrawer,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
                    .background(TvColors.Background.copy(alpha = 0.96f))
                    .border(BorderStroke(1.dp, TvColors.BorderSubtle))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 12.dp)
                ) {
                    Text(
                        text = if (currentCategoryName.isNotBlank() && currentCategoryName != "All channels") {
                            "CHANNELS - ${currentCategoryName.uppercase()}"
                        } else "CHANNELS",
                        color = TvColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvFonts.Body,
                        letterSpacing = 0.8.sp
                    )

                    var isDrawerCloseFocused by remember { mutableStateOf(false) }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (isDrawerCloseFocused) TvColors.Focus else TvColors.SurfaceRaised)
                            .border(
                                BorderStroke(
                                    if (isDrawerCloseFocused) 1.5.dp else 1.dp,
                                    if (isDrawerCloseFocused) TvColors.FocusGlow else TvColors.BorderSubtle
                                ),
                                CircleShape
                            )
                            .focusRequester(drawerCloseFocusRequester)
                            .onFocusChanged { isDrawerCloseFocused = it.isFocused }
                            .clickable {
                                showQuickChannelDrawer = false
                                try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
                            }
                            .onPreviewKeyEvent { event ->
                                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (event.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_CENTER,
                                        KeyEvent.KEYCODE_ENTER -> {
                                            showQuickChannelDrawer = false
                                            try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            try { drawerFocusRequester.requestFocus() } catch (_: Exception) {}
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_RIGHT,
                                        KeyEvent.KEYCODE_BACK -> {
                                            showQuickChannelDrawer = false
                                            try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_UP,
                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            true // Trap focus
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .focusable()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                LazyColumn(
                    state = drawerListState,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(activeZapList) { index, ch ->
                        val isCurrent = ch.id == channel?.id
                        var isFocused by remember { mutableStateOf(false) }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isFocused -> TvColors.Focus
                                        isCurrent -> TvColors.SurfaceRaised
                                        else -> Color.Transparent
                                    }
                                )
                                .border(
                                    BorderStroke(
                                        if (isFocused) 1.5.dp else 0.dp,
                                        if (isFocused) TvColors.FocusGlow else Color.Transparent
                                    ),
                                    RoundedCornerShape(8.dp)
                                )
                                .then(if (index == currentChannelIndex) Modifier.focusRequester(drawerFocusRequester) else Modifier)
                                .onFocusChanged { isFocused = it.isFocused }
                                .clickable {
                                    if (isRecording) {
                                        stopAndSaveRecording()
                                    }
                                    onSwitchChannel(ch)
                                    showQuickChannelDrawer = false
                                    try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
                                }
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                        when (keyEvent.nativeKeyEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                                if (isRecording) {
                                                    stopAndSaveRecording()
                                                }
                                                onSwitchChannel(ch)
                                                showQuickChannelDrawer = false
                                                try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_UP -> {
                                                if (index == 0) {
                                                    true // Trap at top channel so focus remains predictable
                                                } else false
                                            }
                                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                if (index == activeZapList.size - 1) {
                                                    true // Trap focus at bottom
                                                } else false
                                            }
                                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                showQuickChannelDrawer = false
                                                try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                true // Trap focus inside drawer
                                            }
                                            KeyEvent.KEYCODE_BACK -> {
                                                showQuickChannelDrawer = false
                                                try { playerFocusRequester.requestFocus() } catch (_: Exception) {}
                                                true
                                            }
                                            else -> false
                                        }
                                    } else false
                                }
                                .focusable()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = if (isFocused) Color.White else TvColors.TextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(28.dp)
                            )

                            Text(
                                text = ch.title,
                                color = if (isFocused || isCurrent) Color.White else TvColors.TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = TvFonts.Body,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }

        // =========================================================================
        // 5. DYNAMIC LANGUAGE SELECTOR (ZERO "(Internal)" OR "(Active)" JARGON)
        // =========================================================================
        if (showLanguageDialog) {
            val languageOptions = remember(currentUnified, activeLanguage, activeQuality, player?.currentTracks) {
                val list = mutableListOf<Pair<String, () -> Unit>>()

                // A. Channel has multiple stream language variants
                val availableLangs = currentUnified?.languages ?: emptyList()
                if (availableLangs.size > 1) {
                    availableLangs.forEach { lang ->
                        val isCurrent = lang.equals(activeLanguage, ignoreCase = true)
                        val display = if (isCurrent) "✓ $lang" else "  $lang"
                        list.add(display to {
                            currentUnified?.let {
                                val targetQuality = if (it.availableQualitiesFor(lang).contains(activeQuality)) {
                                    activeQuality
                                } else {
                                    it.availableQualitiesFor(lang).firstOrNull() ?: it.defaultQuality
                                }
                                val stream = it.getStreamFor(lang, targetQuality)
                                onSwitchChannel(stream)
                            }
                            showLanguageDialog = false
                        })
                    }
                } else {
                    // Single variant or stream - inspect internal audio tracks
                    val singleLang = availableLangs.firstOrNull()
                    val internalAudioTracks = mutableListOf<Triple<Int, String, Boolean>>()
                    player?.currentTracks?.groups?.forEach { group ->
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val langCode = format.language
                                val cleanLang = if (!langCode.isNullOrEmpty()) {
                                    TiviMateChannelParser.normalizeLanguage(langCode)
                                } else {
                                    format.label?.takeIf { it.isNotBlank() } ?: "Audio ${i + 1}"
                                }
                                if (cleanLang.isNotBlank()) {
                                    val isSelected = group.isTrackSelected(i)
                                    internalAudioTracks.add(Triple(i, cleanLang, isSelected))
                                }
                            }
                        }
                    }

                    if (internalAudioTracks.size > 1) {
                        player?.currentTracks?.groups?.forEach { group ->
                            if (group.type == C.TRACK_TYPE_AUDIO) {
                                for (i in 0 until group.length) {
                                    val format = group.getTrackFormat(i)
                                    val langCode = format.language
                                    val cleanLang = if (!langCode.isNullOrEmpty()) {
                                        TiviMateChannelParser.normalizeLanguage(langCode)
                                    } else {
                                        format.label?.takeIf { it.isNotBlank() } ?: "Audio ${i + 1}"
                                    }
                                    if (cleanLang.isNotBlank() && list.none { it.first.contains(cleanLang) }) {
                                        val isSelected = group.isTrackSelected(i)
                                        val display = if (isSelected) "✓ $cleanLang" else "  $cleanLang"
                                        list.add(display to {
                                            player?.let { p ->
                                                p.trackSelectionParameters = p.trackSelectionParameters
                                                    .buildUpon()
                                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                                    .setAudioOffloadPreferences(
                                                        TrackSelectionParameters.AudioOffloadPreferences.DEFAULT
                                                            .buildUpon()
                                                            .setAudioOffloadMode(TrackSelectionParameters.AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED)
                                                            .build()
                                                    )
                                                    .build()
                                            }
                                            showLanguageDialog = false
                                        })
                                    }
                                }
                            }
                        }
                    } else if (singleLang != null && singleLang.isNotBlank()) {
                        list.add("✓ $singleLang" to { showLanguageDialog = false })
                    } else if (internalAudioTracks.size == 1) {
                        list.add("✓ ${internalAudioTracks.first().second}" to { showLanguageDialog = false })
                    } else {
                        list.add("✓ Original" to { showLanguageDialog = false })
                    }
                }
                list
            }

            TiviMateSelectionPopup(
                title = "Select Audio Language",
                options = languageOptions,
                onDismiss = { showLanguageDialog = false }
            )
        }

        // =========================================================================
        // 6. DYNAMIC QUALITY SELECTOR (SAME LOGIC AS LANGUAGE: SWITCHES STREAM)
        // =========================================================================
        if (showQualityDialog) {
            val qualityOptions = remember(currentUnified, activeLanguage, displayedQuality) {
                val list = mutableListOf<Pair<String, () -> Unit>>()

                // Available stream qualities for this channel and active language
                val availableQuals = currentUnified?.availableQualitiesFor(activeLanguage) ?: emptyList()
                if (availableQuals.size > 1) {
                    availableQuals.forEach { qual ->
                        val isCurrent = qual.equals(displayedQuality, ignoreCase = true)
                        val display = if (isCurrent) "✓ $qual" else "  $qual"
                        list.add(display to {
                            if (!qual.equals(displayedQuality, ignoreCase = true)) {
                                userSelectedQuality = qual
                                currentUnified?.let {
                                    val stream = it.getStreamFor(activeLanguage, qual)
                                    onSwitchChannel(stream)
                                }
                            }
                            showQualityDialog = false
                        })
                    }
                } else {
                    // Single quality available for this channel — only show what actually exists
                    val singleQual = availableQuals.firstOrNull() ?: displayedQuality
                    list.add("✓ $singleQual" to { showQualityDialog = false })
                }
                list
            }

            TiviMateSelectionPopup(
                title = "Select Video Quality",
                options = qualityOptions,
                onDismiss = { showQualityDialog = false }
            )
        }
    }
}

@Composable
private fun TiviMateActionButton(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    focusRequester: FocusRequester? = null,
    onNavigateUp: (() -> Unit)? = null,
    onNavigateDown: (() -> Unit)? = null,
    onNavigateLeft: (() -> Unit)? = null,
    onNavigateRight: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isFocused) TvColors.Focus else TvColors.SurfaceRaised)
            .border(
                BorderStroke(1.2.dp, if (isFocused) TvColors.FocusGlow else TvColors.BorderSubtle),
                RoundedCornerShape(8.dp)
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
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
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .clickable { onClick() }
            .focusable()
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isFocused) Color.White else tint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            color = if (isFocused) Color.White else TvColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = TvFonts.Body
        )
    }
}

@Composable
private fun TiviMateSeekSlider(
    focusRequester: FocusRequester,
    liveDelaySeconds: Long,
    onSeekBy: (Long) -> Unit,
    onGoLive: () -> Unit,
    onNavigateDown: () -> Unit,
    onCloseControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val isDelayed = liveDelaySeconds > 0L

    val maxBufferSec = 300f
    val fraction = if (!isDelayed) 1f else ((maxBufferSec - liveDelaySeconds.coerceAtMost(300L)) / maxBufferSec).coerceIn(0.05f, 1f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            true // Trap focus at top of controls
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            onSeekBy(-10L)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onSeekBy(10L)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            onNavigateDown()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (isDelayed) {
                                onGoLive()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            onCloseControls()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isDelayed) {
                    val m = liveDelaySeconds / 60
                    val s = liveDelaySeconds % 60
                    Text(
                        text = String.format("-%02d:%02d", m, s),
                        color = TvColors.Warning,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvFonts.Body
                    )
                    Text(
                        text = "TIME SHIFT",
                        color = TvColors.TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = TvFonts.Body
                    )
                } else {
                    Text(
                        text = "00:00",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvFonts.Body
                    )
                    Text(
                        text = "LIVE",
                        color = TvColors.Danger,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = TvFonts.Body
                    )
                }
            }

            if (isFocused) {
                Text(
                    text = if (isDelayed) "◀ -10s | +10s ▶ | Press OK for LIVE" else "◀ Press Left to Seek Back (-10s)",
                    color = TvColors.FocusGlow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = TvFonts.Body
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isFocused) 8.dp else 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.2f))
                .border(
                    BorderStroke(
                        if (isFocused) 1.5.dp else 0.dp,
                        if (isFocused) TvColors.FocusGlow else Color.Transparent
                    ),
                    RoundedCornerShape(4.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(if (isDelayed) TvColors.Warning else TvColors.Focus)
            )
        }
    }
}

@Composable
private fun TiviMateSelectionPopup(
    title: String,
    options: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val initialFocusRequester = remember { FocusRequester() }
    val closeFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        try {
            initialFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 340.dp, max = 460.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TvColors.Surface)
                .border(BorderStroke(1.dp, TvColors.BorderSubtle), RoundedCornerShape(12.dp))
                .clickable(enabled = false) {}
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TvFonts.Body
                )

                var isCloseFocused by remember { mutableStateOf(false) }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (isCloseFocused) TvColors.Focus else TvColors.SurfaceRaised)
                        .border(
                            BorderStroke(
                                if (isCloseFocused) 1.5.dp else 1.dp,
                                if (isCloseFocused) TvColors.FocusGlow else TvColors.BorderSubtle
                            ),
                            CircleShape
                        )
                        .focusRequester(closeFocusRequester)
                        .onFocusChanged { isCloseFocused = it.isFocused }
                        .clickable { onDismiss() }
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER -> {
                                        onDismiss()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        initialFocusRequester.requestFocus()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_LEFT,
                                    KeyEvent.KEYCODE_DPAD_RIGHT,
                                    KeyEvent.KEYCODE_DPAD_UP -> {
                                        true // Trap focus strictly inside the popup
                                    }
                                    else -> false
                                }
                            } else false
                        }
                        .focusable()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(options) { index, (optionLabel, onSelect) ->
                    var isFocused by remember { mutableStateOf(false) }
                    val isChecked = optionLabel.startsWith("✓")
                    val isFirst = index == 0
                    val isLast = index == options.size - 1

                    val itemModifier = if (isChecked || (options.none { it.first.startsWith("✓") } && isFirst)) {
                        Modifier.focusRequester(initialFocusRequester)
                    } else Modifier

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = itemModifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isFocused -> TvColors.Focus
                                    isChecked -> TvColors.SurfaceRaised
                                    else -> TvColors.BackgroundSoft
                                }
                            )
                            .border(
                                BorderStroke(
                                    if (isFocused) 1.5.dp else 1.dp,
                                    if (isFocused) TvColors.FocusGlow else TvColors.BorderSubtle
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .onFocusChanged { isFocused = it.isFocused }
                            .clickable { onSelect() }
                            .onPreviewKeyEvent { event ->
                                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (event.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_CENTER,
                                        KeyEvent.KEYCODE_ENTER -> {
                                            onSelect()
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            if (isFirst) {
                                                closeFocusRequester.requestFocus()
                                                true
                                            } else false
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            if (isLast) {
                                                true // Trap focus at bottom
                                            } else false
                                        }
                                        KeyEvent.KEYCODE_DPAD_LEFT,
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            true // Trap focus horizontally
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .focusable()
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = optionLabel.trim(),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = if (isChecked || isFocused) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = TvFonts.Body
                        )

                        if (isChecked) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Active",
                                tint = if (isFocused) Color.White else TvColors.Accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TvLoadingSpinner(
    modifier: Modifier = Modifier,
    color: Color = TvColors.Focus,
    strokeWidth: Dp = 3.dp
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )
    Canvas(modifier = modifier) {
        drawArc(
            color = color,
            startAngle = angle,
            sweepAngle = 270f,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx())
        )
    }
}
