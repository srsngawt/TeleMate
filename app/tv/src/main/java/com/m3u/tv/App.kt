package com.m3u.tv

import android.app.Activity
import android.content.Intent
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.focus.focusProperties
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.m3u.data.tv.model.keyCode
import com.m3u.i18n.R.string

@Composable
fun App(
    viewModel: TvHomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val currentChannel by viewModel.currentChannel.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val remoteControlCode by viewModel.remoteControlCode.collectAsStateWithLifecycle()
    val view = LocalView.current
    val localeTag = LocalConfiguration.current.locales[0].toLanguageTag()
    val context = LocalContext.current
    val diagnosticsShareTitle = stringResource(string.feat_setting_extension_diagnostics_share_title)
    val currentDiagnosticsShareTitle by rememberUpdatedState(diagnosticsShareTitle)
    var destination by remember { mutableStateOf(TvDestination.Home) }
    var surface by remember { mutableStateOf(TvSurface.Browse) }
    var activeRecording by remember { mutableStateOf<TvRecording?>(null) }
    var currentCategoryName by remember { mutableStateOf("All channels") }
    var showExitDialog by remember { mutableStateOf(false) }
    val closePlayer = {
        activeRecording = null
        surface = TvSurface.Browse
    }

    LaunchedEffect(state.channels) {
        if (currentChannel == null && state.channels.isNotEmpty()) {
            viewModel.play(state.recent ?: state.channels.first())
        }
    }

    val backTarget = tvAppBackTarget(
        playerVisible = surface == TvSurface.Player,
        providerSubscriptionVisible = state.providerSubscriptionForm != null,
        extensionSettingsVisible = state.extensionSettings != null,
    )
    BackHandler(enabled = true) {
        when {
            showExitDialog -> showExitDialog = false
            backTarget == TvAppBackTarget.PLAYER -> closePlayer()
            backTarget == TvAppBackTarget.PROVIDER_SUBSCRIPTION -> viewModel.closeProviderSubscription()
            backTarget == TvAppBackTarget.EXTENSION_SETTINGS -> viewModel.closeExtensionSettings()
            backTarget == TvAppBackTarget.ACTIVITY -> {
                showExitDialog = true
            }
        }
    }

    LaunchedEffect(view) {
        viewModel.remoteDirections.collect { direction ->
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, direction.keyCode))
            view.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, direction.keyCode))
        }
    }
    LaunchedEffect(viewModel, localeTag) {
        viewModel.updateLocale(localeTag)
    }
    LaunchedEffect(viewModel, context) {
        viewModel.extensionDiagnostics.collect { payload ->
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, payload)
                    },
                    currentDiagnosticsShareTitle,
                )
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TvColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .focusProperties { canFocus = !showExitDialog }
        ) {
            TiviMateGuideScreen(
                channels = state.channels,
                favoriteChannels = state.favorites,
                player = if (surface == TvSurface.Browse && activeRecording == null) player else null,
                activeChannel = currentChannel,
                currentProgrammes = state.currentProgrammes,
                initialCategory = currentCategoryName,
                isGuideVisible = surface == TvSurface.Browse,
                onCategoryChanged = { currentCategoryName = it },
                onPlayChannel = { channel ->
                    activeRecording = null
                    viewModel.play(channel)
                },
                onToggleFavorite = { channel ->
                    viewModel.toggleFavorite(channel)
                },
                onOpenFullscreen = {
                    surface = TvSurface.Player
                },
                onPlayRecording = { rec ->
                    activeRecording = rec
                    val matchingChannel = state.channels.find { it.id == rec.channelId || it.url == rec.streamUrl }
                    if (matchingChannel != null) {
                        viewModel.play(matchingChannel)
                    } else {
                        viewModel.play(
                            com.m3u.data.database.model.Channel(
                                id = rec.channelId,
                                title = rec.channelName,
                                url = rec.streamUrl,
                                playlistUrl = "",
                                category = "Recordings",
                                cover = rec.coverUrl
                            )
                        )
                    }
                    surface = TvSurface.Player
                }
            )

            AnimatedVisibility(
                visible = surface == TvSurface.Player,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                val recording = activeRecording
                if (recording != null) {
                    TiviMateRecordingPlayerScreen(
                        player = player,
                        recording = recording,
                        isPlaying = isPlaying,
                        onPlayPause = { viewModel.pauseOrContinue(!isPlaying) },
                        onClose = closePlayer
                    )
                } else {
                    val categoryChannels = remember(state.channels, currentCategoryName, state.favorites) {
                        val favIds = state.favorites.map { it.id }.toSet()
                        val allUnified = TiviMateChannelParser.groupChannels(state.channels, favIds)
                        val filtered = when (currentCategoryName) {
                            "Favorites" -> allUnified.filter { it.isFavorite }
                            "All channels" -> allUnified
                            else -> allUnified.filter { it.category.equals(currentCategoryName, ignoreCase = true) }
                        }
                        filtered.map { it.variants.first().channel }
                    }

                    TiviMatePlayerScreen(
                        player = player,
                        channel = currentChannel,
                        channelList = state.channels,
                        categoryChannels = categoryChannels,
                        currentCategoryName = currentCategoryName,
                        favoriteChannels = state.favorites,
                        isPlaying = isPlaying,
                        onPlayPause = { viewModel.pauseOrContinue(!isPlaying) },
                        onSwitchChannel = { channel ->
                            viewModel.play(channel)
                        },
                        onToggleFavorite = { channel ->
                            viewModel.toggleFavorite(channel)
                        },
                        onBackToGuide = closePlayer
                    )
                }
            }
        }

        remoteControlCode?.let { code ->
            val displayCode = code.toString().padStart(6, '0')
            Text(
                text = "Pair: $displayCode",
                color = TvColors.TextMuted,
                fontFamily = TvFonts.Body,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 12.dp)
                    .background(TvColors.Surface.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        if (showExitDialog) {
            TiviMateExitDialog(
                onConfirmExit = {
                    (context as? Activity)?.finish()
                },
                onDismiss = {
                    showExitDialog = false
                }
            )
        }
    }
}
