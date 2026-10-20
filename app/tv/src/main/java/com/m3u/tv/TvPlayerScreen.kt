package com.m3u.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.tv.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.m3u.core.foundation.util.basic.title
import com.m3u.data.database.model.Channel
import com.m3u.i18n.R.string
import kotlinx.coroutines.yield

@Composable
fun TvPlayerScreen(
    player: Player?,
    channel: Channel?,
    isPlaying: Boolean,
    playbackState: Int,
    onPlayPause: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit
) {
    var showTrackSettings by remember { mutableStateOf(false) }
    BackHandler(onBack = {
        if (showTrackSettings) {
            showTrackSettings = false
        } else {
            onBack()
        }
    })
    val playPauseFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        yield()
        playPauseFocusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (player != null) {
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier = Modifier.fillMaxSize()
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, bottom = 32.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.Black.copy(alpha = 0.78f))
                .border(
                    BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                    RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .focusGroup()
        ) {
            TvIconActionButton(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) {
                    stringResource(string.tv_action_pause)
                } else {
                    stringResource(string.tv_action_play)
                },
                onClick = onPlayPause,
                focusRequester = playPauseFocusRequester
            )
            TvIconActionButton(
                icon = Icons.Rounded.Settings,
                contentDescription = "Track Settings",
                onClick = { showTrackSettings = !showTrackSettings }
            )
            TvIconActionButton(
                icon = Icons.Rounded.Close,
                contentDescription = stringResource(string.tv_action_close_player),
                onClick = onClose
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .padding(start = 8.dp, end = 16.dp)
                    .widthIn(max = 420.dp)
            ) {
                Text(
                    text = channel?.title?.title().orEmpty(),
                    color = TvColors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = TvFonts.Body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playerStateText(playbackState),
                    color = TvColors.TextSecondary,
                    fontSize = 13.sp,
                    fontFamily = TvFonts.Body,
                    maxLines = 1
                )
            }
        }

        if (showTrackSettings && player != null) {
            val audioTracks = remember(player.currentTracks) {
                val list = mutableListOf<Pair<String, () -> Unit>>()
                player.currentTracks.groups.forEach { group ->
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            val lang = format.language?.uppercase() ?: format.label ?: "Audio ${i + 1}"
                            val isSelected = group.isTrackSelected(i)
                            val label = if (isSelected) "✓ $lang" else lang
                            list.add(label to {
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                    .build()
                                showTrackSettings = false
                            })
                        }
                    }
                }
                list
            }

            val videoQualities = remember(player.currentTracks) {
                val list = mutableListOf<Pair<String, () -> Unit>>()
                list.add("Auto" to {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                        .build()
                    showTrackSettings = false
                })
                player.currentTracks.groups.forEach { group ->
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.length) {
                            val format = group.getTrackFormat(i)
                            if (format.height > 0) {
                                val label = "${format.height}p"
                                val isSelected = group.isTrackSelected(i)
                                val displayLabel = if (isSelected) "✓ $label" else label
                                list.add(displayLabel to {
                                    player.trackSelectionParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                                        .build()
                                    showTrackSettings = false
                                })
                            }
                        }
                    }
                }
                list.distinctBy { it.first.replace("✓ ", "") }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = 32.dp)
                    .width(280.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.90f))
                    .border(
                        BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                        RoundedCornerShape(24.dp)
                    )
                    .padding(16.dp)
                    .focusGroup()
            ) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
                ) {
                    item {
                        Text(
                            text = "Quality & Audio",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = TvColors.TextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    if (videoQualities.size > 1) {
                        item {
                            Text(
                                text = "Quality",
                                fontSize = 13.sp,
                                color = TvColors.TextSecondary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(videoQualities) { (label, onClick) ->
                            FocusFrame(
                                onClick = onClick,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { focused ->
                                Text(
                                    text = label,
                                    color = if (focused) TvColors.OnFocus else TvColors.TextPrimary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                    if (audioTracks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Audio Language",
                                fontSize = 13.sp,
                                color = TvColors.TextSecondary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                            )
                        }
                        items(audioTracks) { (label, onClick) ->
                            FocusFrame(
                                onClick = onClick,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { focused ->
                                Text(
                                    text = label,
                                    color = if (focused) TvColors.OnFocus else TvColors.TextPrimary,
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
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
private fun playerStateText(playbackState: Int): String = when (playbackState) {
    Player.STATE_BUFFERING -> stringResource(string.feat_channel_playback_state_buffering)
    Player.STATE_READY -> stringResource(string.feat_channel_playback_state_ready)
    Player.STATE_ENDED -> stringResource(string.feat_channel_playback_state_ended)
    else -> stringResource(string.feat_channel_playback_state_idle)
}