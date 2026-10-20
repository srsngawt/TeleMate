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
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
    favoriteChannels: List<Channel>,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSwitchChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onBackToGuide: () -> Unit
) {
    val context = LocalContext.current
    var showInfobar by remember { mutableStateOf(true) }
    var showQuickChannelDrawer by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableStateOf(0) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var zappingToastText by remember { mutableStateOf<String?>(null) }

    // Live Recording Timer
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingSeconds = 0
            while (true) {
                delay(1000)
                recordingSeconds++
            }
        }
    }

    // Auto-hide infobar after 5 seconds of inactivity
    LaunchedEffect(showInfobar, showLanguageDialog, showQualityDialog, showQuickChannelDrawer) {
        if (showInfobar && !showLanguageDialog && !showQualityDialog && !showQuickChannelDrawer) {
            delay(5000)
            showInfobar = false
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

    val currentChannelIndex = remember(channel, channelList) {
        channelList.indexOfFirst { it.id == channel?.id }.coerceAtLeast(0)
    }

    // Instant Channel Zapping
    val zapNext = {
        if (channelList.isNotEmpty()) {
            val nextIndex = (currentChannelIndex + 1) % channelList.size
            val nextCh = channelList[nextIndex]
            onSwitchChannel(nextCh)
            zappingToastText = "#${nextIndex + 1} ${nextCh.title}"
        }
    }

    val zapPrev = {
        if (channelList.isNotEmpty()) {
            val prevIndex = if (currentChannelIndex - 1 < 0) channelList.size - 1 else currentChannelIndex - 1
            val prevCh = channelList[prevIndex]
            onSwitchChannel(prevCh)
            zappingToastText = "#${prevIndex + 1} ${prevCh.title}"
        }
    }

    BackHandler {
        when {
            showLanguageDialog -> showLanguageDialog = false
            showQualityDialog -> showQualityDialog = false
            showQuickChannelDrawer -> showQuickChannelDrawer = false
            showInfobar -> showInfobar = false
            else -> onBackToGuide()
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
    val displayedQuality = userSelectedQuality ?: detectedTrackQuality ?: activeQuality

    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            currentTimeString = sdf.format(Date())
            delay(1000)
        }
    }

    val playerFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        try {
            playerFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(playerFocusRequester)
            .clickable {
                if (!showLanguageDialog && !showQualityDialog && !showQuickChannelDrawer) {
                    showInfobar = !showInfobar
                }
            }
            .onPreviewKeyEvent { event ->
                val action = event.nativeKeyEvent.action
                if (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (action == KeyEvent.ACTION_UP) {
                                if (!showLanguageDialog && !showQualityDialog && !showQuickChannelDrawer) {
                                    showInfobar = !showInfobar
                                    true
                                } else false
                            } else true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (action == KeyEvent.ACTION_DOWN) {
                                if (!showInfobar && !showLanguageDialog && !showQualityDialog && !showQuickChannelDrawer) {
                                    showInfobar = true
                                    true
                                } else false
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (action == KeyEvent.ACTION_DOWN) {
                                if (!showInfobar && !showLanguageDialog && !showQualityDialog && !showQuickChannelDrawer) {
                                    zapNext()
                                    true
                                } else false
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (action == KeyEvent.ACTION_DOWN) {
                                if (!showQuickChannelDrawer && !showLanguageDialog && !showQualityDialog) {
                                    showQuickChannelDrawer = true
                                    showInfobar = false
                                    true
                                } else false
                            } else false
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (action == KeyEvent.ACTION_UP) {
                                when {
                                    showLanguageDialog -> { showLanguageDialog = false; true }
                                    showQualityDialog -> { showQualityDialog = false; true }
                                    showQuickChannelDrawer -> { showQuickChannelDrawer = false; true }
                                    showInfobar -> { showInfobar = false; true }
                                    else -> { onBackToGuide(); true }
                                }
                            } else true
                        }
                        else -> false
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

                                // LIVE badge
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

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.62f)
                            .fillMaxHeight()
                            .background(TvColors.Focus)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Row (TiviMate Tile Actions)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TiviMateActionButton(
                        icon = Icons.Rounded.Tv,
                        label = "TV guide",
                        onClick = onBackToGuide
                    )

                    TiviMateActionButton(
                        icon = Icons.Rounded.Language,
                        label = if (cleanPlayerLang.isNotBlank()) "Language ($cleanPlayerLang)" else "Language",
                        onClick = { showLanguageDialog = true }
                    )

                    TiviMateActionButton(
                        icon = Icons.Rounded.HighQuality,
                        label = "Quality ($displayedQuality)",
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
                        onClick = {
                            if (!isRecording) {
                                isRecording = true
                                recordingSeconds = 0
                                zappingToastText = "🔴 Recording started: ${channel?.title ?: "Stream"}"
                            } else {
                                isRecording = false
                                val rec = TvRecording(
                                    id = java.util.UUID.randomUUID().toString(),
                                    channelId = channel?.id ?: 0,
                                    channelName = channel?.title ?: "Live Channel",
                                    showTitle = "${currentUnified?.baseName ?: channel?.title ?: "Live Channel"} Broadcast",
                                    coverUrl = channel?.cover,
                                    streamUrl = channel?.url ?: "",
                                    durationSeconds = maxOf(1, recordingSeconds),
                                    timestamp = System.currentTimeMillis()
                                )
                                RecordingsManager.saveRecording(context, rec)
                                zappingToastText = "⏹️ Recording saved (${rec.formattedDuration})"
                            }
                        }
                    )

                    TiviMateActionButton(
                        icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        label = if (isFavorite) "Favorite" else "Add Favorite",
                        tint = if (isFavorite) TvColors.Danger else Color.White,
                        onClick = { channel?.let(onToggleFavorite) }
                    )

                    TiviMateActionButton(
                        icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                        label = "Channels",
                        onClick = { showQuickChannelDrawer = true }
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
                        text = "CHANNELS",
                        color = TvColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvFonts.Body,
                        letterSpacing = 0.8.sp
                    )

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(TvColors.SurfaceRaised)
                            .clickable { showQuickChannelDrawer = false }
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
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(channelList) { index, ch ->
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
                                .onFocusChanged { isFocused = it.isFocused }
                                .clickable {
                                    onSwitchChannel(ch)
                                    showQuickChannelDrawer = false
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
            .onFocusChanged { isFocused = it.isFocused }
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
private fun TiviMateSelectionPopup(
    title: String,
    options: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit
) {
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

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(TvColors.SurfaceRaised)
                        .clickable { onDismiss() }
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
                itemsIndexed(options) { _, (optionLabel, onSelect) ->
                    var isFocused by remember { mutableStateOf(false) }
                    val isChecked = optionLabel.startsWith("✓")

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
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
