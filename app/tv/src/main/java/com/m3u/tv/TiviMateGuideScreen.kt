package com.m3u.tv

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Programme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val epgTimeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())

/** Formats a Programme's start..end as "18:00 - 19:30", gracefully handles null. */
private fun programmeTiming(programme: Programme?): String {
    if (programme == null) return ""
    return "${epgTimeSdf.format(Date(programme.start))} - ${epgTimeSdf.format(Date(programme.end))}"
}

@androidx.compose.runtime.Immutable
data class TimelineShowSlot(
    val title: String,
    val startTimeString: String,
    val endTimeString: String,
    val durationMinutes: Int,
    val isLive: Boolean,
    val progress: Float = 0f
)

/**
 * Builds continuous, dynamic, non-stop timeline shows for a channel:
 * 1. Slot 0 is ALWAYS the currently airing LIVE show (Zero "Earlier Broadcast").
 * 2. Followed by a continuous 12+ hour schedule of dynamic, channel-unique upcoming shows.
 */
private fun getChannelTimelineShows(
    channelName: String,
    category: String,
    programme: Programme?,
    nowMs: Long = System.currentTimeMillis()
): List<TimelineShowSlot> {
    val list = mutableListOf<TimelineShowSlot>()
    val cleanCategory = category.trim().ifEmpty { "General" }
    val cleanName = channelName.trim().ifEmpty { "Channel" }

    var currentSlotStart: Long
    var currentSlotEnd: Long
    var liveTitle: String
    var liveProgress: Float

    if (programme != null && programme.start > 0 && programme.end > programme.start) {
        currentSlotStart = programme.start
        currentSlotEnd = programme.end
        liveTitle = programme.title.ifBlank { "$cleanName Live" }
        val isLive = nowMs in currentSlotStart..currentSlotEnd
        liveProgress = if (isLive && currentSlotEnd > currentSlotStart) {
            ((nowMs - currentSlotStart).toFloat() / (currentSlotEnd - currentSlotStart).toFloat()).coerceIn(0f, 1f)
        } else 0f
    } else {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        val min = cal.get(java.util.Calendar.MINUTE)
        cal.set(java.util.Calendar.MINUTE, if (min < 30) 0 else 30)
        cal.set(java.util.Calendar.SECOND, 0)
        currentSlotStart = cal.timeInMillis
        currentSlotEnd = currentSlotStart + (30 * 60 * 1000L)
        liveTitle = "$cleanName Live"
        liveProgress = ((nowMs - currentSlotStart).toFloat() / (currentSlotEnd - currentSlotStart).toFloat()).coerceIn(0f, 1f)
    }

    val liveDurationMin = ((currentSlotEnd - currentSlotStart) / 60000L).toInt().coerceAtLeast(15)

    // 1. FIRST ITEM IS ALWAYS CURRENTLY AIRING SHOW (Strictly NO Earlier Broadcast)
    list.add(
        TimelineShowSlot(
            title = liveTitle,
            startTimeString = epgTimeSdf.format(Date(currentSlotStart)),
            endTimeString = epgTimeSdf.format(Date(currentSlotEnd)),
            durationMinutes = liveDurationMin,
            isLive = true,
            progress = liveProgress
        )
    )

    // 2. DYNAMIC & CHANNEL-UNIQUE UPCOMING SHOWS (Zero repeating duplicates across channels)
    val channelSeed = kotlin.math.abs(cleanName.lowercase().hashCode())

    val showPatterns = when {
        cleanCategory.contains("News", ignoreCase = true) -> listOf(
            30 to "$cleanName Live Desk",
            60 to "Special Report with $cleanName",
            30 to "$cleanName Superfast 100",
            60 to "Prime Time Debate on $cleanName",
            45 to "$cleanName Global Headlines",
            30 to "World Vision with $cleanName",
            60 to "Late Night Bulletin - $cleanName",
            30 to "$cleanName Morning Express",
            45 to "Top Stories on $cleanName",
            60 to "$cleanName In-Depth Analysis",
            30 to "National Focus with $cleanName",
            45 to "$cleanName News Hour"
        )
        cleanCategory.contains("Movie", ignoreCase = true) || cleanCategory.contains("Cinema", ignoreCase = true) -> listOf(
            120 to "$cleanName Blockbuster Premiere",
            90 to "Action Unlimited on $cleanName",
            120 to "$cleanName Superhit Cinema",
            105 to "Classic Hits Festival",
            120 to "$cleanName Midnight Thriller",
            90 to "Mega Feature with $cleanName",
            120 to "Late Night Cinema Special",
            90 to "$cleanName Morning Movie",
            120 to "Matinee Showcase - $cleanName"
        )
        cleanCategory.contains("Sport", ignoreCase = true) -> listOf(
            60 to "$cleanName Match Day Live",
            90 to "Championship Highlights",
            60 to "$cleanName Sports Central",
            120 to "Live Tournament Replay",
            60 to "Game Night with $cleanName",
            90 to "Pro Arena Special",
            60 to "$cleanName Morning Kickoff",
            90 to "League Preview on $cleanName"
        )
        cleanCategory.contains("Music", ignoreCase = true) -> listOf(
            45 to "$cleanName Non-Stop Hits",
            30 to "Top 20 Countdown on $cleanName",
            60 to "Bollywood Beats with $cleanName",
            45 to "$cleanName Romantic Melodies",
            60 to "Retro Rewind Special",
            45 to "$cleanName Club Mix Hour",
            60 to "Morning Ragas on $cleanName",
            45 to "$cleanName Superhit Tracks"
        )
        cleanCategory.contains("Religio", ignoreCase = true) || cleanCategory.contains("Devotion", ignoreCase = true) -> listOf(
            45 to "$cleanName Morning Aarti & Satsang",
            60 to "Bhajan Sandhya on $cleanName",
            45 to "Divine Darshan Live",
            60 to "$cleanName Spiritual Pravachan",
            30 to "Shanti Mantra Hour",
            60 to "Night Katha with $cleanName"
        )
        else -> listOf(
            30 to "$cleanName Prime Serial",
            45 to "Family Showcase with $cleanName",
            60 to "$cleanName Gala Premiere",
            30 to "Comedy Star Hour",
            60 to "$cleanName Super Drama",
            45 to "Night Feature with $cleanName",
            60 to "$cleanName Midnight Special",
            45 to "Daytime Prime - $cleanName"
        )
    }

    val offset = channelSeed % showPatterns.size
    var nextStart = currentSlotEnd
    for (i in 0 until showPatterns.size) {
        val (durationMin, title) = showPatterns[(offset + i) % showPatterns.size]
        val nextEnd = nextStart + (durationMin * 60 * 1000L)
        list.add(
            TimelineShowSlot(
                title = title,
                startTimeString = epgTimeSdf.format(Date(nextStart)),
                endTimeString = epgTimeSdf.format(Date(nextEnd)),
                durationMinutes = durationMin,
                isLive = false
            )
        )
        nextStart = nextEnd
    }

    return list
}

@Composable
private fun TiviMateClockDisplay(modifier: Modifier = Modifier) {
    var timeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            timeString = sdf.format(Date())
            delay(1000)
        }
    }
    Text(
        text = timeString,
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = TvFonts.Body,
        modifier = modifier
    )
}

@Composable
private fun TiviMateTimelineHeader() {
    var dateString by remember { mutableStateOf("") }
    var timelineSlots by remember { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(Unit) {
        val dateSdf = SimpleDateFormat("EEE, d MMM, hh:mm a", Locale.getDefault())
        val slotSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            val now = java.util.Calendar.getInstance()
            dateString = dateSdf.format(now.time)

            now.set(java.util.Calendar.MINUTE, if (now.get(java.util.Calendar.MINUTE) < 30) 0 else 30)
            now.set(java.util.Calendar.SECOND, 0)
            timelineSlots = (0..4).map { i ->
                val cal = now.clone() as java.util.Calendar
                cal.add(java.util.Calendar.MINUTE, i * 30)
                slotSdf.format(cal.time)
            }
            delay(10_000)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(TvColors.SurfaceGlass)
            .border(BorderStroke(1.dp, TvColors.BorderSubtle))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            text = dateString.ifEmpty { "Today, Live" },
            color = TvColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = TvFonts.Body,
            modifier = Modifier.width(240.dp)
        )

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.weight(1f)
        ) {
            timelineSlots.forEach { slot ->
                Text(
                    text = slot,
                    color = if (slot == timelineSlots.firstOrNull())
                        TvColors.TimelineMarker
                    else
                        TvColors.TextMuted,
                    fontSize = 11.sp,
                    fontWeight = if (slot == timelineSlots.firstOrNull())
                        FontWeight.Bold
                    else
                        FontWeight.Normal,
                    fontFamily = TvFonts.Body
                )
            }
        }
    }
}

@Composable
fun TiviMateGuideScreen(
    channels: List<Channel>,
    favoriteChannels: List<Channel>,
    player: Player?,
    activeChannel: Channel?,
    currentProgrammes: Map<String, Programme>,
    initialCategory: String = "All channels",
    isGuideVisible: Boolean = true,
    unifiedChannelsInput: List<UnifiedChannel> = emptyList(),
    groupsInput: List<Pair<String, Int>> = emptyList(),
    onCategoryChanged: (String) -> Unit = {},
    onPlayChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onOpenFullscreen: () -> Unit,
    onPlayRecording: (TvRecording) -> Unit = {}
) {
    val favoriteIds = remember(favoriteChannels) { favoriteChannels.map { it.id }.toSet() }

    val unifiedChannels = if (unifiedChannelsInput.isNotEmpty()) {
        unifiedChannelsInput
    } else {
        remember(channels, favoriteIds) {
            TiviMateChannelParser.groupChannels(channels, favoriteIds)
        }
    }

    val groups = if (groupsInput.isNotEmpty()) {
        groupsInput
    } else {
        remember(unifiedChannels, favoriteChannels) {
            val list = mutableListOf<Pair<String, Int>>()
            list.add("All channels" to unifiedChannels.size)
            list.add("Favorites" to favoriteChannels.size)
            val categoryCounts = unifiedChannels.groupBy { it.category }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
            list.addAll(categoryCounts)
            list
        }
    }

    var selectedGroupIndex by remember(groups, initialCategory) {
        val idx = groups.indexOfFirst { it.first.equals(initialCategory, ignoreCase = true) }
        mutableIntStateOf(if (idx >= 0) idx else 0)
    }
    val currentGroupName = groups.getOrNull(selectedGroupIndex)?.first ?: "All channels"

    LaunchedEffect(currentGroupName) {
        onCategoryChanged(currentGroupName)
    }

    val displayedChannels = remember(unifiedChannels, currentGroupName, favoriteIds) {
        when (currentGroupName) {
            "Favorites" -> unifiedChannels.filter { it.isFavorite }
            "All channels" -> unifiedChannels
            else -> unifiedChannels.filter { it.category.equals(currentGroupName, ignoreCase = true) }
        }
    }

    var focusedChannelIndex by remember { mutableIntStateOf(0) }
    val focusedUnifiedChannel = displayedChannels.getOrNull(focusedChannelIndex)
        ?: displayedChannels.firstOrNull()

    // Store selected language & quality per channel
    val selectedLanguages = remember { mutableStateMapOf<Int, String>() }
    val selectedQualities = remember { mutableStateMapOf<Int, String>() }

    val activeLanguage = focusedUnifiedChannel?.let {
        val raw = selectedLanguages[it.id] ?: it.defaultLanguage
        TiviMateChannelParser.normalizeLanguage(raw)
    } ?: ""

    val activeQuality = focusedUnifiedChannel?.let {
        selectedQualities[it.id] ?: it.defaultQuality
    } ?: "HD"

    val channelListState = rememberLazyListState()
    val categoryListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val onSelectStream: (Channel) -> Unit = { stream ->
        onPlayChannel(stream)
        onOpenFullscreen()
    }

    val onChannelPageUp: () -> Unit = {
        if (focusedChannelIndex + 1 < displayedChannels.size) {
            focusedChannelIndex++
            val nextItem = displayedChannels[focusedChannelIndex]
            val stream = nextItem.getStreamFor(
                selectedLanguages[nextItem.id] ?: nextItem.defaultLanguage,
                selectedQualities[nextItem.id] ?: nextItem.defaultQuality
            )
            if (activeChannel?.id != stream.id) {
                onPlayChannel(stream)
            }
            scope.launch {
                channelListState.animateScrollToItem(maxOf(0, focusedChannelIndex - 1))
            }
        }
    }

    val onChannelPageDown: () -> Unit = {
        if (focusedChannelIndex - 1 >= 0) {
            focusedChannelIndex--
            val prevItem = displayedChannels[focusedChannelIndex]
            val stream = prevItem.getStreamFor(
                selectedLanguages[prevItem.id] ?: prevItem.defaultLanguage,
                selectedQualities[prevItem.id] ?: prevItem.defaultQuality
            )
            if (activeChannel?.id != stream.id) {
                onPlayChannel(stream)
            }
            scope.launch {
                channelListState.animateScrollToItem(maxOf(0, focusedChannelIndex - 1))
            }
        }
    }

    // Focus Requesters for clean TV Navigation between columns
    val railFocusRequester = remember { FocusRequester() }
    val searchRailFocusRequester = remember { FocusRequester() }
    val searchBarFocusRequester = remember { FocusRequester() }
    val categoryFocusRequester = remember { FocusRequester() }
    val categoryFocusRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val channelFocusRequester = remember { FocusRequester() }

    // Dialog & overlay states
    var showSearchOverlay by remember { mutableStateOf(false) }
    var showRecordingsDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Left Navigation Menu expansion state (expands on focus, collapses on navigate right)
    var focusedRailItemIndex by remember { mutableIntStateOf(-1) }
    val isRailFocused = focusedRailItemIndex >= 0
    val railWidth by animateDpAsState(
        targetValue = if (isRailFocused) 210.dp else 68.dp,
        animationSpec = tween(durationMillis = 220),
        label = "railWidth"
    )

    // When TV Guide opens or isGuideVisible becomes true, automatically focus and scroll to the appropriate item
    var isFirstLaunch by remember { mutableStateOf(true) }
    var wasGuideVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isGuideVisible, activeChannel?.id) {
        if (isGuideVisible && (!wasGuideVisible || focusedChannelIndex < 0)) {
            wasGuideVisible = true
            if (isFirstLaunch) {
                isFirstLaunch = false
                selectedGroupIndex = 0
                focusedChannelIndex = 0
                focusedRailItemIndex = -1
                delay(150)
                try {
                    categoryListState.scrollToItem(0)
                    categoryFocusRequesters[0]?.requestFocus() ?: categoryFocusRequester.requestFocus()
                } catch (_: Exception) {}
            } else {
                focusedRailItemIndex = -1
                // Find the playing channel's actual original category (excluding Favorites)
                if (activeChannel != null) {
                    val activeUnified = unifiedChannels.firstOrNull { u ->
                        u.variants.any { it.channel.id == activeChannel.id }
                    } ?: unifiedChannels.firstOrNull { it.id == activeChannel.id }
                    val catName = activeUnified?.category ?: activeChannel.category
                    if (!catName.isNullOrBlank()) {
                        val foundGroupIdx = groups.indexOfFirst {
                            it.first.equals(catName, ignoreCase = true)
                        }
                        if (foundGroupIdx >= 0 && selectedGroupIndex != foundGroupIdx) {
                            selectedGroupIndex = foundGroupIdx
                        }
                    }
                }

                // Compute channel index in current category
                val currentCategoryChannels = when (val gName = groups.getOrNull(selectedGroupIndex)?.first ?: "All channels") {
                    "Favorites" -> unifiedChannels.filter { it.isFavorite }
                    "All channels" -> unifiedChannels
                    else -> unifiedChannels.filter { it.category.equals(gName, ignoreCase = true) }
                }

                val playingIndex = if (activeChannel != null && currentCategoryChannels.isNotEmpty()) {
                    val found = currentCategoryChannels.indexOfFirst { u ->
                        u.variants.any { it.channel.id == activeChannel.id }
                    }
                    if (found >= 0) found else 0
                } else 0

                focusedChannelIndex = playingIndex
                delay(120)
                try {
                    categoryListState.scrollToItem(maxOf(0, selectedGroupIndex))
                    channelListState.scrollToItem(maxOf(0, playingIndex - 1))
                    channelFocusRequester.requestFocus()
                } catch (_: Exception) {}
            }
        } else if (!isGuideVisible) {
            wasGuideVisible = false
        }
    }

    // Auto-Play in TV Guide: When focusedChannelIndex changes and user pauses on that channel, auto-play its video and audio in the mini player
    LaunchedEffect(focusedChannelIndex, isGuideVisible, showSearchOverlay, showRecordingsDialog, showSettingsDialog) {
        if (isGuideVisible && !showSearchOverlay && !showRecordingsDialog && !showSettingsDialog) {
            if (focusedChannelIndex in displayedChannels.indices) {
                delay(550)
                val item = displayedChannels[focusedChannelIndex]
                val stream = item.getStreamFor(
                    selectedLanguages[item.id] ?: item.defaultLanguage,
                    selectedQualities[item.id] ?: item.defaultQuality
                )
                if (activeChannel?.id != stream.id) {
                    onPlayChannel(stream)
                }
            }
        }
    }

    // Immediately pause live player video & audio when Search Window opens; resume when closed
    LaunchedEffect(showSearchOverlay) {
        if (showSearchOverlay) {
            try {
                player?.pause()
            } catch (_: Exception) {}
        } else if (isGuideVisible) {
            try {
                player?.play()
            } catch (_: Exception) {}
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .focusProperties { canFocus = isGuideVisible && !showRecordingsDialog && !showSettingsDialog }
            .background(TvColors.RootBackground)
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                            if (displayedChannels.isNotEmpty()) {
                                val nextIndex = (focusedChannelIndex + 1) % displayedChannels.size
                                focusedChannelIndex = nextIndex
                                val nextItem = displayedChannels[nextIndex]
                                val stream = nextItem.getStreamFor(
                                    selectedLanguages[nextItem.id] ?: nextItem.defaultLanguage,
                                    selectedQualities[nextItem.id] ?: nextItem.defaultQuality
                                )
                                onPlayChannel(stream)
                                scope.launch {
                                    channelListState.animateScrollToItem(maxOf(0, nextIndex - 1))
                                }
                            }
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                            if (displayedChannels.isNotEmpty()) {
                                val prevIndex = if (focusedChannelIndex - 1 < 0) displayedChannels.size - 1 else focusedChannelIndex - 1
                                focusedChannelIndex = prevIndex
                                val prevItem = displayedChannels[prevIndex]
                                val stream = prevItem.getStreamFor(
                                    selectedLanguages[prevItem.id] ?: prevItem.defaultLanguage,
                                    selectedQualities[prevItem.id] ?: prevItem.defaultQuality
                                )
                                onPlayChannel(stream)
                                scope.launch {
                                    channelListState.animateScrollToItem(maxOf(0, prevIndex - 1))
                                }
                            }
                            true
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        // =========================================================================
        // 1. TIVIMATE FAR-LEFT EXPANDABLE NAVIGATION DRAWER
        // =========================================================================
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(railWidth)
                .fillMaxHeight()
                .background(TvColors.SurfaceGlass)
                .border(BorderStroke(1.dp, TvColors.BorderSubtle))
                .padding(vertical = 16.dp, horizontal = 10.dp)
        ) {
            // TOP BRANDING LOGO
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (isRailFocused) {
                    // Photo 1: Wide horizontal "tivimate" logo when menu is expanded
                    Image(
                        painter = painterResource(id = R.drawable.tivimate_logo),
                        contentDescription = "TiviMate",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .padding(horizontal = 8.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // Photo 2: Crisp square "tv" icon when menu is collapsed
                    Image(
                        painter = painterResource(id = R.drawable.tivimate_icon),
                        contentDescription = "TiviMate",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            // VERTICALLY CENTERED NAVIGATION ITEMS (Centered in the middle of screen)
            Spacer(modifier = Modifier.weight(1f))

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Search
                TiviMateNavItem(
                    icon = Icons.Rounded.Search,
                    label = "Search",
                    isSelected = showSearchOverlay,
                    isExpanded = isRailFocused,
                    focusRequester = searchRailFocusRequester,
                    onClick = {
                        showSearchOverlay = true
                        scope.launch {
                            delay(50)
                            try {
                                searchBarFocusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                    },
                    onNavigateRight = {
                        showSearchOverlay = true
                        scope.launch {
                            delay(50)
                            try {
                                searchBarFocusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                    },
                    onFocusChange = {
                        if (it) focusedRailItemIndex = 0
                        else if (focusedRailItemIndex == 0) focusedRailItemIndex = -1
                    }
                )

                // 2. TV Guide
                TiviMateNavItem(
                    icon = Icons.Rounded.Tv,
                    label = "TV Guide",
                    isSelected = !showSearchOverlay && !showRecordingsDialog && !showSettingsDialog && currentGroupName != "Favorites",
                    isExpanded = isRailFocused,
                    focusRequester = railFocusRequester,
                    onClick = {
                        showSearchOverlay = false
                        selectedGroupIndex = 0
                        focusedChannelIndex = 0
                        scope.launch {
                            channelListState.scrollToItem(0)
                            categoryListState.animateScrollToItem(0)
                            categoryFocusRequesters[0]?.requestFocus()
                        }
                    },
                    onNavigateRight = {
                        if (showSearchOverlay) {
                            showSearchOverlay = false
                        }
                        val targetIdx = selectedGroupIndex.coerceAtLeast(0)
                        scope.launch {
                            categoryListState.scrollToItem(targetIdx)
                            delay(20)
                            try {
                                categoryFocusRequesters[targetIdx]?.requestFocus() ?: categoryFocusRequester.requestFocus()
                            } catch (_: Exception) {}
                        }
                    },
                    onFocusChange = {
                        if (it) focusedRailItemIndex = 1
                        else if (focusedRailItemIndex == 1) focusedRailItemIndex = -1
                    }
                )

                // 3. Favorites
                TiviMateNavItem(
                    icon = Icons.Rounded.Star,
                    label = "Favorites",
                    isSelected = currentGroupName == "Favorites",
                    isExpanded = isRailFocused,
                    focusRequester = null,
                    onClick = {
                        showSearchOverlay = false
                        val idx = groups.indexOfFirst { it.first == "Favorites" }
                        if (idx >= 0) {
                            selectedGroupIndex = idx
                            focusedChannelIndex = 0
                            scope.launch {
                                channelListState.scrollToItem(0)
                                categoryListState.animateScrollToItem(idx)
                                categoryFocusRequesters[idx]?.requestFocus()
                            }
                        } else {
                            categoryFocusRequester.requestFocus()
                        }
                    },
                    onNavigateRight = {
                        showSearchOverlay = false
                        val idx = groups.indexOfFirst { it.first == "Favorites" }
                        if (idx >= 0) {
                            selectedGroupIndex = idx
                            focusedChannelIndex = 0
                            scope.launch {
                                channelListState.scrollToItem(0)
                                categoryListState.animateScrollToItem(idx)
                                categoryFocusRequesters[idx]?.requestFocus()
                            }
                        } else {
                            categoryFocusRequester.requestFocus()
                        }
                    },
                    onFocusChange = {
                        if (it) focusedRailItemIndex = 2
                        else if (focusedRailItemIndex == 2) focusedRailItemIndex = -1
                    }
                )

                // 4. Recordings
                TiviMateNavItem(
                    icon = Icons.Rounded.FiberManualRecord,
                    label = "Recordings",
                    isSelected = showRecordingsDialog,
                    isExpanded = isRailFocused,
                    focusRequester = null,
                    onClick = {
                        showSearchOverlay = false
                        showRecordingsDialog = true
                    },
                    onNavigateRight = {
                        showSearchOverlay = false
                        categoryFocusRequester.requestFocus()
                    },
                    onFocusChange = {
                        if (it) focusedRailItemIndex = 3
                        else if (focusedRailItemIndex == 3) focusedRailItemIndex = -1
                    }
                )

                // 5. Settings
                TiviMateNavItem(
                    icon = Icons.Rounded.Settings,
                    label = "Settings",
                    isSelected = showSettingsDialog,
                    isExpanded = isRailFocused,
                    focusRequester = null,
                    onClick = {
                        showSearchOverlay = false
                        showSettingsDialog = true
                    },
                    onNavigateRight = {
                        showSearchOverlay = false
                        categoryFocusRequester.requestFocus()
                    },
                    onFocusChange = {
                        if (it) focusedRailItemIndex = 4
                        else if (focusedRailItemIndex == 4) focusedRailItemIndex = -1
                    }
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        // =========================================================================
        // MAIN BODY: SEARCH SCREEN OR (TOP PREVIEW & DETAILS + BOTTOM TIMELINE & CHANNELS)
        // =========================================================================
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            if (showSearchOverlay) {
                TiviMateSearchScreen(
                    allChannels = unifiedChannels,
                    searchBarFocusRequester = searchBarFocusRequester,
                    onNavigateLeftToRail = {
                        searchRailFocusRequester.requestFocus()
                    },
                    onSelectChannel = { channel ->
                        showSearchOverlay = false
                        val groupIdx = groups.indexOfFirst {
                            it.first.equals(channel.category, ignoreCase = true)
                        }.takeIf { it >= 0 } ?: 0
                        selectedGroupIndex = groupIdx
                        val stream = channel.getStreamFor(
                            selectedLanguages[channel.id] ?: channel.defaultLanguage,
                            selectedQualities[channel.id] ?: channel.defaultQuality
                        )
                        val catChannels = if (groupIdx == 0) unifiedChannels else unifiedChannels.filter { it.category.equals(groups[groupIdx].first, ignoreCase = true) }
                        val chIdx = catChannels.indexOfFirst { it.id == channel.id }.takeIf { it >= 0 } ?: 0
                        focusedChannelIndex = chIdx
                        scope.launch {
                            categoryListState.scrollToItem(groupIdx)
                            channelListState.scrollToItem(maxOf(0, chIdx - 1))
                        }
                        onPlayChannel(stream)
                        onOpenFullscreen()
                    },
                    onClose = {
                        showSearchOverlay = false
                        searchRailFocusRequester.requestFocus()
                    }
                )
            } else {
                // TOP SECTION: LIVE TV PREVIEW (LEFT) + PROGRAM & CHANNEL DETAILS (RIGHT)
                Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.40f)
                    .background(TvColors.Surface)
                    .border(BorderStroke(1.dp, TvColors.BorderSubtle))
                    .padding(14.dp)
            ) {
                // TOP-LEFT: LIVE VIDEO PLAYER BOX (16:9 Widescreen)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                        .border(BorderStroke(1.2.dp, TvColors.BorderGlass), RoundedCornerShape(8.dp))
                        .clickable { onOpenFullscreen() }
                        .onPreviewKeyEvent { event ->
                            if ((event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN || event.nativeKeyEvent.action == KeyEvent.ACTION_UP) &&
                                (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                 event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                            ) {
                                onOpenFullscreen()
                                true
                            } else false
                        }
                        .focusable()
                ) {
                    if (player != null) {
                        TiviMateVideoPlayer(
                            player = player,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(TvColors.BackgroundSoft)
                        ) {}
                    }

                    // Fullscreen Badge overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black.copy(alpha = 0.70f))
                            .border(BorderStroke(1.dp, TvColors.BorderSubtle), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Fullscreen,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Press OK for Fullscreen",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = TvFonts.Body
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(18.dp))

                // TOP-RIGHT: CHANNEL & PROGRAM DETAILS CARD
                if (focusedUnifiedChannel != null) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Row 1: Channel Logo, Title & Time
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                // Logo
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(TvColors.SurfaceRaised)
                                        .border(BorderStroke(1.dp, TvColors.BorderSubtle), RoundedCornerShape(8.dp))
                                ) {
                                    if (!focusedUnifiedChannel.cover.isNullOrEmpty()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(focusedUnifiedChannel.cover)
                                                .size(128, 128)
                                                .crossfade(false)
                                                .build(),
                                            contentDescription = focusedUnifiedChannel.baseName,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Rounded.Tv,
                                            contentDescription = null,
                                            tint = TvColors.TextMuted,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = focusedUnifiedChannel.baseName,
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = TvFonts.Body,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${focusedUnifiedChannel.category} • Live Streaming",
                                        color = TvColors.TextSecondary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = TvFonts.Body
                                    )
                                }
                            }

                            // Live Clock & Favorite
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TiviMateClockDisplay()

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(TvColors.SurfaceRaised)
                                        .border(BorderStroke(1.dp, TvColors.BorderSubtle), CircleShape)
                                        .clickable {
                                            onToggleFavorite(focusedUnifiedChannel.getStreamFor(activeLanguage, activeQuality))
                                        }
                                        .focusable()
                                ) {
                                    Icon(
                                        imageVector = if (focusedUnifiedChannel.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (focusedUnifiedChannel.isFavorite) TvColors.Danger else TvColors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        // Row 2: Show Time & Progress Bar
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val detailProgramme = focusedUnifiedChannel
                                    .getStreamFor(activeLanguage, activeQuality)
                                    .relationId
                                    ?.let { relId ->
                                        currentProgrammes[relId]
                                            ?: relId.substringBefore('@').let { baseId ->
                                                currentProgrammes["$baseId@HD"] ?: currentProgrammes["$baseId@SD"] ?: currentProgrammes[baseId]
                                            }
                                    }
                                val detailTiming = programmeTiming(detailProgramme)
                                val detailTitle = detailProgramme?.title?.takeIf { it.isNotBlank() }
                                Text(
                                    text = when {
                                        detailTitle != null && detailTiming.isNotEmpty() ->
                                            "$detailTiming  •  $detailTitle"
                                        detailTiming.isNotEmpty() -> detailTiming
                                        detailTitle != null -> detailTitle
                                        else -> "Live Stream"
                                    },
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = TvFonts.Body
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(TvColors.Danger)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = TvFonts.Body
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color.White.copy(alpha = 0.2f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.60f)
                                        .fillMaxHeight()
                                        .background(TvColors.Accent)
                                )
                            }
                        }

                        // Row 3: Language & Quality Selectors
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Language Chips (Full Title Case Name, Hidden if No Valid Language)
                            val validLangs = focusedUnifiedChannel.languages
                            if (validLangs.isNotEmpty()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "LANG:",
                                        color = TvColors.TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = TvFonts.Body
                                    )
                                    validLangs.forEach { lang ->
                                        val isSelected = lang.equals(activeLanguage, ignoreCase = true)
                                        var isFocused by remember { mutableStateOf(false) }

                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isSelected) TvColors.Focus else if (isFocused) TvColors.SurfaceHighlight else TvColors.BadgeBackground)
                                                .border(
                                                    BorderStroke(1.dp, if (isSelected) TvColors.FocusGlow else TvColors.BorderSubtle),
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .onFocusChanged { isFocused = it.isFocused }
                                                .clickable {
                                                    selectedLanguages[focusedUnifiedChannel.id] = lang
                                                }
                                                .focusable()
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = lang,
                                                color = if (isSelected) Color.White else TvColors.TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontFamily = TvFonts.Body
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            // Quality Chips (Filtered strictly for active language)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "QUALITY:",
                                    color = TvColors.TextMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = TvFonts.Body
                                )
                                val availQuals = focusedUnifiedChannel.availableQualitiesFor(activeLanguage)
                                availQuals.forEach { qual ->
                                    val isSelected = qual.equals(activeQuality, ignoreCase = true)
                                    var isFocused by remember { mutableStateOf(false) }

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) TvColors.Focus else if (isFocused) TvColors.SurfaceHighlight else TvColors.BadgeBackground)
                                            .border(
                                                BorderStroke(1.dp, if (isSelected) TvColors.FocusGlow else TvColors.BorderSubtle),
                                                RoundedCornerShape(6.dp)
                                            )
                                            .onFocusChanged { isFocused = it.isFocused }
                                            .clickable {
                                                selectedQualities[focusedUnifiedChannel.id] = qual
                                            }
                                            .focusable()
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = qual,
                                                color = if (isSelected) Color.White else TvColors.TextPrimary,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontFamily = TvFonts.Body
                                            )
                                        }
                                }
                            }
                        }
                    }
                }
            }

            // =========================================================================
            // BOTTOM SECTION: CATEGORIES (LEFT) + CHANNELS & EPG SCHEDULE (RIGHT)
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.60f)
            ) {
                // CATEGORIES COLUMN (~190dp width)
                Column(
                    modifier = Modifier
                        .width(190.dp)
                        .fillMaxHeight()
                        .background(TvColors.Surface)
                        .border(BorderStroke(1.dp, TvColors.BorderSubtle))
                ) {
                    // Category Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "CATEGORIES",
                            color = TvColors.TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TvFonts.Body,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = "${groups.size}",
                            color = TvColors.TextMuted,
                            fontSize = 11.sp,
                            fontFamily = TvFonts.Body
                        )
                    }

                    LazyColumn(
                        state = categoryListState,
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = groups,
                            key = { _, (groupName, _) -> groupName },
                            contentType = { _, _ -> "category_row" }
                        ) { index, (groupName, count) ->
                            val isSelected = index == selectedGroupIndex
                            var isFocused by remember { mutableStateOf(false) }
                            val rowRequester = categoryFocusRequesters.getOrPut(index) { FocusRequester() }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isFocused -> TvColors.Focus
                                            isSelected -> TvColors.SurfaceRaised
                                            else -> Color.Transparent
                                        }
                                    )
                                    .border(
                                        BorderStroke(
                                            if (isFocused) 1.5.dp else if (isSelected) 1.dp else 0.dp,
                                            if (isFocused) TvColors.FocusGlow else if (isSelected) TvColors.BorderSubtle else Color.Transparent
                                        ),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .focusRequester(rowRequester)
                                    .then(if (isSelected) Modifier.focusRequester(categoryFocusRequester) else Modifier)
                                    .onFocusChanged {
                                        isFocused = it.isFocused
                                        if (it.isFocused) {
                                            selectedGroupIndex = index
                                            focusedChannelIndex = 0
                                            scope.launch { channelListState.scrollToItem(0) }
                                        }
                                    }
                                    .clickable {
                                        selectedGroupIndex = index
                                        focusedChannelIndex = 0
                                        channelFocusRequester.requestFocus()
                                    }
                                    .onPreviewKeyEvent { event ->
                                        val nativeEvent = event.nativeKeyEvent
                                        val isActionDown = nativeEvent.action == KeyEvent.ACTION_DOWN
                                        when (nativeEvent.keyCode) {
                                            KeyEvent.KEYCODE_DPAD_UP -> {
                                                if (index == 0) true else false
                                            }
                                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                if (index == groups.size - 1) true else false
                                            }
                                            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                if (isActionDown && displayedChannels.isNotEmpty()) {
                                                    channelFocusRequester.requestFocus()
                                                }
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                if (isActionDown) {
                                                    railFocusRequester.requestFocus()
                                                }
                                                true
                                            }
                                            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                                if (isActionDown && displayedChannels.isNotEmpty()) {
                                                    channelFocusRequester.requestFocus()
                                                }
                                                true
                                            }
                                            else -> false
                                        }
                                    }
                                    .focusable()
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = groupName,
                                    color = if (isFocused) Color.White else if (isSelected) TvColors.Accent else TvColors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = TvFonts.Body,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isFocused) Color.Black.copy(alpha = 0.35f) else TvColors.BadgeBackground)
                                        .border(
                                            BorderStroke(1.dp, if (isFocused) Color.White.copy(alpha = 0.2f) else TvColors.BorderSubtle),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = count.toString(),
                                        color = if (isFocused) Color.White else TvColors.TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = TvFonts.Body
                                    )
                                }
                            }
                        }
                    }
                }

                // CHANNELS & EPG SCHEDULE (FILLS REMAINING WIDTH)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(TvColors.BackgroundSoft)
                ) {
                    TiviMateTimelineHeader()

                    // Channels List with Show Time and Details
                    if (displayedChannels.isEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(32.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (currentGroupName == "Favorites") Icons.Rounded.FavoriteBorder else Icons.Rounded.Tv,
                                    contentDescription = null,
                                    tint = TvColors.TextMuted,
                                    modifier = Modifier.size(52.dp)
                                )
                                Text(
                                    text = if (currentGroupName == "Favorites") "No Favorite Channels Yet" else "No channels in this category",
                                    color = TvColors.TextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = TvFonts.Body
                                )
                                Text(
                                    text = if (currentGroupName == "Favorites")
                                        "Press 'Add Favorite' on any channel infobar to pin it here."
                                    else
                                        "Select another category from the list.",
                                    color = TvColors.TextMuted,
                                    fontSize = 13.sp,
                                    fontFamily = TvFonts.Body
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = channelListState,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                        itemsIndexed(
                            items = displayedChannels,
                            key = { _, item -> item.id },
                            contentType = { _, _ -> "channel_row" }
                        ) { index, item ->
                            val streamChannel = item.getStreamFor(
                                selectedLanguages[item.id] ?: item.defaultLanguage,
                                selectedQualities[item.id] ?: item.defaultQuality
                            )
                            val programme = currentProgrammes[streamChannel.relationId]
                                ?: streamChannel.relationId?.substringBefore('@')?.let { baseId ->
                                    currentProgrammes["$baseId@HD"] ?: currentProgrammes["$baseId@SD"] ?: currentProgrammes[baseId]
                                }

                            TiviMateChannelRow(
                                item = item,
                                index = index,
                                isTargetFocused = index == focusedChannelIndex,
                                isPlayingThis = activeChannel?.id == streamChannel.id,
                                selectedLanguage = selectedLanguages[item.id],
                                selectedQuality = selectedQualities[item.id],
                                programme = programme,
                                channelFocusRequester = channelFocusRequester,
                                categoryFocusRequester = categoryFocusRequester,
                                onFocused = { focusedChannelIndex = index },
                                onSelectStream = onSelectStream,
                                onNavigateLeft = {
                                    scope.launch {
                                        categoryListState.scrollToItem(selectedGroupIndex)
                                        delay(35)
                                        try {
                                            categoryFocusRequesters[selectedGroupIndex]?.requestFocus()
                                                ?: categoryFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    }
                                },
                                onChannelPageUp = onChannelPageUp,
                                onChannelPageDown = onChannelPageDown,
                                isFirstItem = index == 0,
                                isLastItem = index == displayedChannels.size - 1
                            )
                        }
                    }
                }
            }
            }
        }
    }
}

    // =========================================================================
    // RECORDINGS & SETTINGS DIALOGS
    // =========================================================================
    if (showRecordingsDialog) {
        TiviMateRecordingsDialog(
            onDismiss = {
                showRecordingsDialog = false
                railFocusRequester.requestFocus()
            },
            onPlayRecording = { rec ->
                showRecordingsDialog = false
                onPlayRecording(rec)
            }
        )
    }

    if (showSettingsDialog) {
        TiviMateSettingsDialog(
            onDismiss = {
                showSettingsDialog = false
                railFocusRequester.requestFocus()
            }
        )
    }
}

@Composable
private fun TiviMateProgramSlotCard(
    show: TimelineShowSlot,
    onSelect: () -> Unit,
    onNavigateLeft: (() -> Unit)? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val cardWidth = (show.durationMinutes * 3.5f).toInt().coerceIn(160, 360).dp

    Box(
        modifier = Modifier
            .width(cardWidth)
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> TvColors.Focus
                    show.isLive -> TvColors.SurfaceRaised
                    else -> TvColors.Surface.copy(alpha = 0.7f)
                }
            )
            .border(
                BorderStroke(
                    if (isFocused) 1.5.dp else 1.dp,
                    if (isFocused) TvColors.FocusGlow else if (show.isLive) TvColors.BorderGlass else TvColors.BorderSubtle.copy(alpha = 0.6f)
                ),
                RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onSelect() }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            onSelect()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (onNavigateLeft != null) {
                                onNavigateLeft()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${show.startTimeString} - ${show.endTimeString}",
                    color = if (isFocused) Color.White.copy(alpha = 0.9f) else if (show.isLive) TvColors.Accent else TvColors.TextMuted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = TvFonts.Body
                )

                if (show.isLive) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(TvColors.Danger)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = TvFonts.Body
                        )
                    }
                }
            }

            Text(
                text = show.title,
                color = if (isFocused) Color.White else TvColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = if (show.isLive || isFocused) FontWeight.Bold else FontWeight.Medium,
                fontFamily = TvFonts.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (show.isLive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(show.progress)
                            .fillMaxHeight()
                            .background(if (isFocused) Color.White else TvColors.Accent)
                    )
                }
            }
        }
    }
}

@Composable
private fun TiviMateChannelRow(
    item: UnifiedChannel,
    index: Int,
    isTargetFocused: Boolean,
    isPlayingThis: Boolean,
    selectedLanguage: String?,
    selectedQuality: String?,
    programme: Programme?,
    channelFocusRequester: FocusRequester,
    categoryFocusRequester: FocusRequester,
    onFocused: () -> Unit,
    onSelectStream: (Channel) -> Unit,
    onNavigateLeft: () -> Unit,
    onChannelPageUp: () -> Unit,
    onChannelPageDown: () -> Unit,
    isFirstItem: Boolean,
    isLastItem: Boolean
) {
    var isFocused by remember { mutableStateOf(false) }
    val channelCardRequester = remember { FocusRequester() }
    val streamChannel = remember(item, selectedLanguage, selectedQuality) {
        item.getStreamFor(
            selectedLanguage ?: item.defaultLanguage,
            selectedQuality ?: item.defaultQuality
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // 1. PINNED LEFT CHANNEL CARD (Fixed ~240dp width)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .width(240.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    when {
                        isFocused -> TvColors.Focus
                        isPlayingThis -> TvColors.SurfaceHighlight
                        else -> TvColors.Surface
                    }
                )
                .border(
                    BorderStroke(
                        if (isFocused) 1.5.dp else 1.dp,
                        if (isFocused) TvColors.FocusGlow else if (isPlayingThis) TvColors.BorderActive else TvColors.BorderSubtle
                    ),
                    RoundedCornerShape(8.dp)
                )
                .focusRequester(channelCardRequester)
                .then(if (isTargetFocused) Modifier.focusRequester(channelFocusRequester) else Modifier)
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) {
                        onFocused()
                    }
                }
                .clickable {
                    onSelectStream(streamChannel)
                }
                .onPreviewKeyEvent { event ->
                    val nativeEvent = event.nativeKeyEvent
                    val isActionDown = nativeEvent.action == KeyEvent.ACTION_DOWN
                    when (nativeEvent.keyCode) {
                        KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                            if (isActionDown) onChannelPageUp()
                            true
                        }
                        KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                            if (isActionDown) onChannelPageDown()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            if (isFirstItem) true else false
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (isLastItem) true else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (isActionDown) onNavigateLeft()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (isActionDown) onSelectStream(streamChannel)
                            true
                        }
                        else -> false
                    }
                }
                .focusable()
                .padding(horizontal = 8.dp)
        ) {
            // Channel Number
            Text(
                text = "${index + 1}",
                color = if (isFocused) Color.White.copy(alpha = 0.85f) else TvColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = TvFonts.Body,
                modifier = Modifier.width(26.dp)
            )

            // Logo
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(TvColors.Background)
            ) {
                if (!item.cover.isNullOrEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.cover)
                            .size(96, 96)
                            .crossfade(false)
                            .build(),
                        contentDescription = item.baseName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Tv,
                        contentDescription = null,
                        tint = TvColors.TextMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Channel Name
            Text(
                text = item.baseName,
                color = if (isFocused) Color.White else TvColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = TvFonts.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Badges: Language & Quality & Favorite
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Full Title Case Language (Hidden if blank)
                val currentLang = remember(selectedLanguage, item.defaultLanguage) {
                    TiviMateChannelParser.normalizeLanguage(
                        selectedLanguage ?: item.defaultLanguage
                    )
                }
                if (currentLang.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isFocused) Color.Black.copy(alpha = 0.35f) else TvColors.BadgeBackground)
                            .border(BorderStroke(1.dp, if (isFocused) Color.White.copy(alpha = 0.2f) else TvColors.BorderSubtle), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = currentLang,
                            color = if (isFocused) Color.White else TvColors.Accent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TvFonts.Body
                        )
                    }
                }

                if (item.isFavorite) {
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = "Favorite",
                        tint = if (isFocused) Color.White else TvColors.Danger,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // 2. HORIZONTALLY SCROLLABLE PROGRAM TIMELINE (Zero nested LazyRow overhead)
        val timelineShows = remember(item.baseName, item.category, programme) {
            getChannelTimelineShows(
                channelName = item.baseName,
                category = item.category,
                programme = programme
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            timelineShows.forEachIndexed { slotIdx, show ->
                TiviMateProgramSlotCard(
                    show = show,
                    onSelect = {
                        onSelectStream(streamChannel)
                    },
                    onNavigateLeft = if (slotIdx == 0) {
                        { channelCardRequester.requestFocus() }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun TiviMateNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    isExpanded: Boolean,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    onNavigateRight: () -> Unit,
    onFocusChange: (Boolean) -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (isExpanded) Arrangement.Start else Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    isFocused -> TvColors.Focus
                    isSelected -> TvColors.SurfaceRaised
                    else -> Color.Transparent
                }
            )
            .border(
                BorderStroke(
                    if (isFocused) 1.5.dp else if (isSelected) 1.dp else 0.dp,
                    if (isFocused) TvColors.FocusGlow else if (isSelected) TvColors.BorderSubtle else Color.Transparent
                ),
                RoundedCornerShape(8.dp)
            )
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                isFocused = it.isFocused
                onFocusChange(it.isFocused)
            }
            .clickable { onClick() }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            onNavigateRight()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            true // Consume left arrow so focus does not wander off screen
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .focusable()
            .padding(horizontal = if (isExpanded) 12.dp else 0.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isFocused) Color.White else if (isSelected) TvColors.Accent else TvColors.TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        if (isExpanded) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                color = if (isFocused) Color.White else if (isSelected) Color.White else TvColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = if (isFocused || isSelected) FontWeight.SemiBold else FontWeight.Medium,
                fontFamily = TvFonts.Body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TiviMateSearchScreen(
    allChannels: List<UnifiedChannel>,
    searchBarFocusRequester: FocusRequester,
    onNavigateLeftToRail: () -> Unit,
    onSelectChannel: (UnifiedChannel) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchQuery by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }

    val micFocusRequester = remember { FocusRequester() }
    val closeButtonFocusRequester = remember { FocusRequester() }
    val textFieldFocusRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }

    var isMicFocused by remember { mutableStateOf(false) }
    var isSearchBoxFocused by remember { mutableStateOf(false) }
    var isCloseFocused by remember { mutableStateOf(false) }

    var isListening by remember { mutableStateOf(false) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Pulsing animation for the Mic button while listening
    val infiniteTransition = rememberInfiniteTransition(label = "micPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val stopVoiceSearch: () -> Unit = {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        isListening = false
    }

    val recognitionListener = remember {
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListening = true
            }

            override fun onBeginningOfSpeech() {
                isListening = true
            }

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                isListening = false
            }

            override fun onError(error: Int) {
                isListening = false
                try {
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}
                speechRecognizer = null

                val msg = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check mic."
                    SpeechRecognizer.ERROR_CLIENT -> "" // User stopped or client cancel
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
                    SpeechRecognizer.ERROR_NETWORK -> "Network error for voice search."
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Speak into mic."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Voice recognizer is busy, please retry."
                    SpeechRecognizer.ERROR_SERVER -> "Voice server error."
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Speak closer to mic."
                    else -> "Speech recognition error ($error)"
                }
                if (msg.isNotEmpty()) {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull { it.isNotBlank() }?.trim()
                if (!spokenText.isNullOrBlank()) {
                    searchQuery = spokenText
                    isEditing = false
                }
                isListening = false
                try {
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}
                speechRecognizer = null
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull { it.isNotBlank() }?.trim()
                if (!spokenText.isNullOrBlank()) {
                    searchQuery = spokenText
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (_: Exception) {}
        }
    }

    val startVoiceSearch: () -> Unit = {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Toast.makeText(context, "Voice recognition not available on this device", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    speechRecognizer?.stopListening()
                    speechRecognizer?.cancel()
                    speechRecognizer?.destroy()
                } catch (_: Exception) {}
                speechRecognizer = null

                val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext ?: context).also {
                    speechRecognizer = it
                }
                recognizer.setRecognitionListener(recognitionListener)
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                }
                searchQuery = ""
                isListening = true
                recognizer.startListening(intent)
            }
        } catch (e: Exception) {
            isListening = false
            Toast.makeText(context, "Voice search error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher for voice permission dialog
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startVoiceSearch()
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required for voice search",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val onMicClick: () -> Unit = {
        if (isListening) {
            stopVoiceSearch()
        } else {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                startVoiceSearch()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Filter channels based on search query
    val filteredChannels = remember(searchQuery, allChannels) {
        if (searchQuery.trim().isEmpty()) {
            emptyList()
        } else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            allChannels.filter {
                it.baseName.lowercase(Locale.ROOT).contains(q) ||
                it.category.lowercase(Locale.ROOT).contains(q)
            }
        }
    }

    // Initial focus directly on search box
    LaunchedEffect(Unit) {
        delay(100)
        try {
            searchBarFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // When isEditing toggles true, focus text field and show keyboard
    LaunchedEffect(isEditing) {
        if (isEditing) {
            delay(50)
            try {
                textFieldFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    // 1. When listening to voice, Back cancels listening
    BackHandler(enabled = isListening) {
        stopVoiceSearch()
    }

    // 2. When soft keyboard is open, Back closes the keyboard and returns focus to search box
    BackHandler(enabled = isEditing && !isListening) {
        keyboardController?.hide()
        isEditing = false
        try {
            searchBarFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    // 3. When in search screen, Back closes search, resets query, and returns focus to Left Rail
    BackHandler(enabled = !isEditing && !isListening) {
        searchQuery = ""
        onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        // TOP SEARCH CONTROLS ROW: [ Mic Button ] [ Search Text Box ] [ Clear / Close Button ]
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. Mic Button (LEFT SIDE)
            val micBg = if (isListening) {
                TvColors.Danger.copy(alpha = pulseAlpha)
            } else if (isMicFocused) {
                TvColors.Focus
            } else {
                TvColors.SurfaceRaised
            }

            val micBorder = if (isListening) {
                BorderStroke(2.dp, Color.White)
            } else if (isMicFocused) {
                BorderStroke(1.5.dp, TvColors.FocusGlow)
            } else {
                BorderStroke(1.dp, TvColors.BorderSubtle)
            }

            val micTint = if (isListening || isMicFocused) Color.White else TvColors.Accent

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(micBg)
                    .border(micBorder, RoundedCornerShape(10.dp))
                    .focusRequester(micFocusRequester)
                    .onFocusChanged { isMicFocused = it.isFocused }
                    .clickable { onMicClick() }
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER -> {
                                    onMicClick()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    onNavigateLeftToRail()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    searchBarFocusRequester.requestFocus()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (filteredChannels.isNotEmpty()) {
                                        firstResultFocusRequester.requestFocus()
                                        true
                                    } else false
                                }
                                else -> false
                            }
                        } else false
                    }
                    .focusable()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Mic,
                    contentDescription = "Voice Search",
                    tint = micTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 2. Search Text Box (CENTER)
            val searchBoxBorder = if (isListening) {
                BorderStroke(1.5.dp, TvColors.Danger.copy(alpha = pulseAlpha))
            } else if (isSearchBoxFocused || isEditing) {
                BorderStroke(1.5.dp, TvColors.FocusGlow)
            } else {
                BorderStroke(1.dp, TvColors.BorderSubtle)
            }

            Box(
                contentAlignment = Alignment.CenterStart,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TvColors.BackgroundSoft)
                    .border(searchBoxBorder, RoundedCornerShape(10.dp))
                    .focusRequester(searchBarFocusRequester)
                    .onFocusChanged { isSearchBoxFocused = it.isFocused }
                    .clickable {
                        if (isListening) stopVoiceSearch()
                        isEditing = true
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            if (isEditing) {
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_BACK -> {
                                        keyboardController?.hide()
                                        isEditing = false
                                        searchBarFocusRequester.requestFocus()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (filteredChannels.isNotEmpty()) {
                                            keyboardController?.hide()
                                            isEditing = false
                                            firstResultFocusRequester.requestFocus()
                                            true
                                        } else false
                                    }
                                    else -> false
                                }
                            } else {
                                when (event.nativeKeyEvent.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                                        micFocusRequester.requestFocus()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        closeButtonFocusRequester.requestFocus()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_ENTER -> {
                                        if (isListening) stopVoiceSearch()
                                        isEditing = true
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                                        if (filteredChannels.isNotEmpty()) {
                                            firstResultFocusRequester.requestFocus()
                                            true
                                        } else false
                                    }
                                    else -> false
                                }
                            }
                        } else false
                    }
                    .focusable()
                    .padding(horizontal = 14.dp)
            ) {
                if (isEditing) {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            fontFamily = TvFonts.Body
                        ),
                        cursorBrush = SolidColor(TvColors.Focus),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                isEditing = false
                                searchBarFocusRequester.requestFocus()
                            },
                            onDone = {
                                keyboardController?.hide()
                                isEditing = false
                                searchBarFocusRequester.requestFocus()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(textFieldFocusRequester),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(
                                        text = "Type channel or category name...",
                                        color = TvColors.TextMuted,
                                        fontSize = 15.sp,
                                        fontFamily = TvFonts.Body
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                } else if (isListening) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (searchQuery.isEmpty()) "Speak now..." else searchQuery,
                            color = if (searchQuery.isEmpty()) TvColors.Danger else Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = TvFonts.Body
                        )
                    }
                } else {
                    Text(
                        text = if (searchQuery.isEmpty()) "Type channel or category name..." else searchQuery,
                        color = if (searchQuery.isEmpty()) TvColors.TextMuted else Color.White,
                        fontSize = 15.sp,
                        fontFamily = TvFonts.Body
                    )
                }
            }

            // 3. Clear / Close Button (RIGHT SIDE)
            val closeBg = if (isCloseFocused) TvColors.Danger else TvColors.SurfaceRaised
            val closeBorder = if (isCloseFocused) {
                BorderStroke(1.5.dp, Color.White)
            } else {
                BorderStroke(1.dp, TvColors.BorderSubtle)
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(closeBg)
                    .border(closeBorder, RoundedCornerShape(10.dp))
                    .focusRequester(closeButtonFocusRequester)
                    .onFocusChanged { isCloseFocused = it.isFocused }
                    .clickable {
                        if (searchQuery.isNotEmpty()) {
                            searchQuery = ""
                            searchBarFocusRequester.requestFocus()
                        } else {
                            onClose()
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER -> {
                                    if (searchQuery.isNotEmpty()) {
                                        searchQuery = ""
                                        searchBarFocusRequester.requestFocus()
                                    } else {
                                        onClose()
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                    searchBarFocusRequester.requestFocus()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                    if (filteredChannels.isNotEmpty()) {
                                        firstResultFocusRequester.requestFocus()
                                        true
                                    } else false
                                }
                                else -> false
                            }
                        } else false
                    }
                    .focusable()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Clear or Close",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Results count or Empty State Hint
        if (searchQuery.trim().isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(TvColors.SurfaceRaised)
                            .border(BorderStroke(1.dp, TvColors.BorderSubtle), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = if (isListening) TvColors.Danger else TvColors.Focus,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = if (isListening) "Listening... Speak channel name now" else "Type channel name or press Mic to speak",
                        color = if (isListening) TvColors.Danger else TvColors.TextMuted,
                        fontSize = 14.sp,
                        fontWeight = if (isListening) FontWeight.SemiBold else FontWeight.Normal,
                        fontFamily = TvFonts.Body
                    )
                }
            }
        } else if (filteredChannels.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    text = "No channels found for \"$searchQuery\"",
                    color = TvColors.TextSecondary,
                    fontSize = 14.sp,
                    fontFamily = TvFonts.Body
                )
            }
        } else {
            Text(
                text = "${filteredChannels.size} channels found",
                color = TvColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = TvFonts.Body,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            // Results list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(filteredChannels) { index, channel ->
                    var isItemFocused by remember { mutableStateOf(false) }

                    val itemModifier = if (index == 0) {
                        Modifier.focusRequester(firstResultFocusRequester)
                    } else Modifier

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = itemModifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isItemFocused) TvColors.Focus else TvColors.SurfaceRaised
                            )
                            .border(
                                BorderStroke(
                                    if (isItemFocused) 1.5.dp else 1.dp,
                                    if (isItemFocused) TvColors.FocusGlow else TvColors.BorderSubtle
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .onFocusChanged { isItemFocused = it.isFocused }
                            .clickable { onSelectChannel(channel) }
                            .onPreviewKeyEvent { event ->
                                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (event.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            if (index == 0) {
                                                searchBarFocusRequester.requestFocus()
                                                true
                                            } else false
                                        }
                                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                                            onNavigateLeftToRail()
                                            true
                                        }
                                        KeyEvent.KEYCODE_DPAD_CENTER,
                                        KeyEvent.KEYCODE_ENTER -> {
                                            onSelectChannel(channel)
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .focusable()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // Logo
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(TvColors.Background)
                            ) {
                                if (!channel.cover.isNullOrEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(channel.cover)
                                            .size(96, 96)
                                            .crossfade(false)
                                            .build(),
                                        contentDescription = channel.baseName,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(26.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Tv,
                                        contentDescription = null,
                                        tint = TvColors.TextMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Column {
                                Text(
                                    text = channel.baseName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = TvFonts.Body,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = channel.category,
                                    color = if (isItemFocused) Color.White.copy(alpha = 0.8f) else TvColors.TextSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = TvFonts.Body
                                )
                            }
                        }

                        // Badges
                        val lang = channel.defaultLanguage
                        if (lang.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isItemFocused) Color.Black.copy(alpha = 0.3f) else TvColors.BadgeBackground)
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = lang,
                                    color = if (isItemFocused) Color.White else TvColors.Accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = TvFonts.Body
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
private fun TiviMateRecordingsDialog(
    onDismiss: () -> Unit,
    onPlayRecording: (TvRecording) -> Unit
) {
    BackHandler { onDismiss() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var recordings by remember { mutableStateOf(RecordingsManager.getRecordings(context)) }
    var focusTargetIndex by remember { mutableStateOf<Int?>(null) }

    val closeFocusRequester = remember { FocusRequester() }
    val targetItemFocusRequester = remember { FocusRequester() }
    val bottomBtnFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        try {
            if (recordings.isNotEmpty()) {
                targetItemFocusRequester.requestFocus()
            } else {
                bottomBtnFocusRequester.requestFocus()
            }
        } catch (_: Exception) {}
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                    onDismiss()
                    true
                } else false
            }
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 400.dp, max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(TvColors.SurfaceRaised)
                .border(BorderStroke(1.dp, TvColors.BorderGlass), RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FiberManualRecord,
                        contentDescription = null,
                        tint = TvColors.Danger,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (recordings.isEmpty()) "Recordings Library" else "Recordings (${recordings.size})",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvFonts.Body
                    )
                }

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
                                        if (recordings.isNotEmpty()) {
                                            targetItemFocusRequester.requestFocus()
                                        } else {
                                            bottomBtnFocusRequester.requestFocus()
                                        }
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP,
                                    KeyEvent.KEYCODE_DPAD_LEFT,
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        true // Trap focus strictly inside dialog
                                    }
                                    KeyEvent.KEYCODE_BACK -> {
                                        onDismiss()
                                        true
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

            if (recordings.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(TvColors.SurfaceRaised)
                            .border(BorderStroke(1.dp, TvColors.BorderSubtle), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Videocam,
                            contentDescription = null,
                            tint = TvColors.TextSecondary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "No Recorded Shows",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvFonts.Body
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "To record live broadcasts, open the player infobar and press Record. Your saved recordings will be accessible here.",
                        color = TvColors.TextSecondary,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontFamily = TvFonts.Body
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .padding(vertical = 8.dp)
                ) {
                    itemsIndexed(recordings, key = { _, rec -> rec.id }) { index, rec ->
                        var isPlayFocused by remember { mutableStateOf(false) }
                        var isDeleteFocused by remember { mutableStateOf(false) }
                        val isFirst = index == 0
                        val isLast = index == recordings.size - 1
                        val isTarget = index == (focusTargetIndex ?: 0)

                        val playItemModifier = if (isTarget) {
                            Modifier.focusRequester(targetItemFocusRequester)
                        } else Modifier

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 1. Play Recording Button / Card
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = playItemModifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isPlayFocused) TvColors.Focus else TvColors.SurfaceRaised)
                                    .border(
                                        BorderStroke(1.2.dp, if (isPlayFocused) TvColors.FocusGlow else TvColors.BorderSubtle),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .onFocusChanged { isPlayFocused = it.isFocused }
                                    .clickable { onPlayRecording(rec) }
                                    .onPreviewKeyEvent { event ->
                                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                            when (event.nativeKeyEvent.keyCode) {
                                                KeyEvent.KEYCODE_DPAD_CENTER,
                                                KeyEvent.KEYCODE_ENTER -> {
                                                    onPlayRecording(rec)
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_LEFT -> {
                                                    true // Trap focus on left
                                                }
                                                KeyEvent.KEYCODE_DPAD_UP -> {
                                                    if (isFirst) {
                                                        closeFocusRequester.requestFocus()
                                                        true
                                                    } else false
                                                }
                                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    if (isLast) {
                                                        bottomBtnFocusRequester.requestFocus()
                                                        true
                                                    } else false
                                                }
                                                KeyEvent.KEYCODE_BACK -> {
                                                    onDismiss()
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                                    .focusable()
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (!rec.coverUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = rec.coverUrl,
                                            contentDescription = rec.channelName,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.Black.copy(alpha = 0.4f))
                                        )
                                    } else {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(TvColors.SurfaceHighlight)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Tv,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = rec.channelName,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = TvFonts.Body,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = rec.showTitle,
                                            color = if (isPlayFocused) Color.White.copy(alpha = 0.9f) else TvColors.TextSecondary,
                                            fontSize = 12.sp,
                                            fontFamily = TvFonts.Body,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val recFile = remember(rec) {
                                            val f1 = java.io.File(rec.streamUrl)
                                            if (f1.exists() && f1.length() > 0L) f1
                                            else {
                                                val f2 = java.io.File(context.filesDir, "recordings/rec_${rec.id}.ts")
                                                if (f2.exists() && f2.length() > 0L) f2 else null
                                            }
                                        }
                                        val sizeStr = remember(recFile) {
                                            if (recFile != null) {
                                                val mb = recFile.length() / (1024.0 * 1024.0)
                                                String.format("%.1f MB", mb)
                                            } else "No File"
                                        }
                                        Text(
                                            text = "🔴 ${rec.formattedDuration}  •  ${rec.formattedDate}  •  $sizeStr",
                                            color = if (isPlayFocused) Color.White.copy(alpha = 0.75f) else TvColors.TextMuted,
                                            fontSize = 11.sp,
                                            fontFamily = TvFonts.Body
                                        )
                                    }
                                }

                                // Play Indicator Icon
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(if (isPlayFocused) Color.White.copy(alpha = 0.25f) else TvColors.SurfaceHighlight)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Play",
                                        tint = if (isPlayFocused) Color.White else TvColors.Focus,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            val deleteItem: () -> Unit = {
                                RecordingsManager.deleteRecording(context, rec.id)
                                val updated = RecordingsManager.getRecordings(context)
                                if (updated.isEmpty()) {
                                    recordings = updated
                                    focusTargetIndex = null
                                    coroutineScope.launch {
                                        delay(80)
                                        try {
                                            bottomBtnFocusRequester.requestFocus()
                                        } catch (_: Exception) {
                                            try { closeFocusRequester.requestFocus() } catch (_: Exception) {}
                                        }
                                    }
                                } else {
                                    val nextIdx = index.coerceIn(0, updated.size - 1)
                                    focusTargetIndex = nextIdx
                                    recordings = updated
                                    coroutineScope.launch {
                                        delay(80)
                                        try {
                                            targetItemFocusRequester.requestFocus()
                                        } catch (_: Exception) {}
                                    }
                                }
                            }

                            // 2. Delete Recording Button
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDeleteFocused) TvColors.Danger else TvColors.SurfaceRaised)
                                    .border(
                                        BorderStroke(
                                            if (isDeleteFocused) 1.5.dp else 1.dp,
                                            if (isDeleteFocused) Color.White else TvColors.BorderSubtle
                                        ),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .onFocusChanged { isDeleteFocused = it.isFocused }
                                    .clickable { deleteItem() }
                                    .onPreviewKeyEvent { event ->
                                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                            when (event.nativeKeyEvent.keyCode) {
                                                KeyEvent.KEYCODE_DPAD_CENTER,
                                                KeyEvent.KEYCODE_ENTER -> {
                                                    deleteItem()
                                                    true
                                                }
                                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                                    true // Trap focus on right
                                                }
                                                KeyEvent.KEYCODE_DPAD_UP -> {
                                                    if (isFirst) {
                                                        closeFocusRequester.requestFocus()
                                                        true
                                                    } else false
                                                }
                                                KeyEvent.KEYCODE_DPAD_DOWN -> {
                                                    if (isLast) {
                                                        bottomBtnFocusRequester.requestFocus()
                                                        true
                                                    } else false
                                                }
                                                KeyEvent.KEYCODE_BACK -> {
                                                    onDismiss()
                                                    true
                                                }
                                                else -> false
                                            }
                                        } else false
                                    }
                                    .focusable()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete Recording",
                                    tint = if (isDeleteFocused) Color.White else TvColors.Danger,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            var isBtnFocused by remember { mutableStateOf(false) }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBtnFocused) TvColors.Focus else TvColors.SurfaceRaised)
                    .border(
                        BorderStroke(1.dp, if (isBtnFocused) TvColors.FocusGlow else TvColors.BorderSubtle),
                        RoundedCornerShape(8.dp)
                    )
                    .focusRequester(bottomBtnFocusRequester)
                    .onFocusChanged { isBtnFocused = it.isFocused }
                    .clickable { onDismiss() }
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER -> {
                                    onDismiss()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    if (recordings.isNotEmpty()) {
                                        targetItemFocusRequester.requestFocus()
                                    } else {
                                        closeFocusRequester.requestFocus()
                                    }
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    true // Trap focus at bottom of dialog
                                }
                                KeyEvent.KEYCODE_BACK -> {
                                    onDismiss()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                    .focusable()
                    .padding(vertical = 11.dp)
            ) {
                Text(
                    text = "Close",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TvFonts.Body
                )
            }
        }
    }
}

@Composable
private fun TiviMateSettingsDialog(
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }
    val closeFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }
    val bottomBtnFocusRequester = remember { FocusRequester() }

    val settingsItems = remember {
        listOf(
            "Playlists & EPG" to "Manage subscriptions & XMLTV URLs",
            "Playback Engine" to "ExoPlayer Media3 (Hardware Accelerated)",
            "Audio & Video Quality" to "Auto adaptive HLS with manual overrides",
            "About TeleMate" to "Version 1.0 (Android TV Pro Edition)"
        )
    }

    LaunchedEffect(Unit) {
        delay(100)
        try {
            firstItemFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                    onDismiss()
                    true
                } else false
            }
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 360.dp, max = 460.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(TvColors.SurfaceRaised)
                .border(BorderStroke(1.dp, TvColors.BorderGlass), RoundedCornerShape(16.dp))
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = TvColors.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Settings",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TvFonts.Body
                    )
                }

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
                                        firstItemFocusRequester.requestFocus()
                                        true
                                    }
                                    KeyEvent.KEYCODE_DPAD_UP,
                                    KeyEvent.KEYCODE_DPAD_LEFT,
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                        true // Trap focus
                                    }
                                    KeyEvent.KEYCODE_BACK -> {
                                        onDismiss()
                                        true
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

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                settingsItems.forEachIndexed { index, (title, subtitle) ->
                    var isItemFocused by remember { mutableStateOf(false) }
                    val isFirst = index == 0
                    val isLast = index == settingsItems.size - 1

                    val itemModifier = if (isFirst) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else Modifier

                    Column(
                        modifier = itemModifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isItemFocused) TvColors.Focus else TvColors.SurfaceRaised)
                            .border(
                                BorderStroke(
                                    1.dp,
                                    if (isItemFocused) TvColors.FocusGlow else TvColors.BorderSubtle
                                ),
                                RoundedCornerShape(8.dp)
                            )
                            .onFocusChanged { isItemFocused = it.isFocused }
                            .clickable { }
                            .onPreviewKeyEvent { event ->
                                if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                    when (event.nativeKeyEvent.keyCode) {
                                        KeyEvent.KEYCODE_DPAD_UP -> {
                                            if (isFirst) {
                                                closeFocusRequester.requestFocus()
                                                true
                                            } else false
                                        }
                                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                                            if (isLast) {
                                                bottomBtnFocusRequester.requestFocus()
                                                true
                                            } else false
                                        }
                                        KeyEvent.KEYCODE_DPAD_LEFT,
                                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                            true // Trap focus horizontally
                                        }
                                        KeyEvent.KEYCODE_DPAD_CENTER,
                                        KeyEvent.KEYCODE_ENTER -> {
                                            true
                                        }
                                        KeyEvent.KEYCODE_BACK -> {
                                            onDismiss()
                                            true
                                        }
                                        else -> false
                                    }
                                } else false
                            }
                            .focusable()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = TvFonts.Body
                        )
                        Text(
                            text = subtitle,
                            color = if (isItemFocused) Color.White.copy(alpha = 0.9f) else TvColors.TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = TvFonts.Body
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            var isBtnFocused by remember { mutableStateOf(false) }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isBtnFocused) TvColors.Focus else TvColors.SurfaceRaised)
                    .border(
                        BorderStroke(1.dp, if (isBtnFocused) TvColors.FocusGlow else TvColors.BorderSubtle),
                        RoundedCornerShape(8.dp)
                    )
                    .focusRequester(bottomBtnFocusRequester)
                    .onFocusChanged { isBtnFocused = it.isFocused }
                    .clickable { onDismiss() }
                    .onPreviewKeyEvent { event ->
                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (event.nativeKeyEvent.keyCode) {
                                KeyEvent.KEYCODE_DPAD_CENTER,
                                KeyEvent.KEYCODE_ENTER -> {
                                    onDismiss()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_UP -> {
                                    firstItemFocusRequester.requestFocus()
                                    true
                                }
                                KeyEvent.KEYCODE_DPAD_DOWN,
                                KeyEvent.KEYCODE_DPAD_LEFT,
                                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    true // Trap focus at bottom
                                }
                                KeyEvent.KEYCODE_BACK -> {
                                    onDismiss()
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                    .focusable()
                    .padding(vertical = 11.dp)
            ) {
                Text(
                    text = "Close",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TvFonts.Body
                )
            }
        }
    }
}
