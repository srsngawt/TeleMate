package com.m3u.tv

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun TiviMateExitDialog(
    onConfirmExit: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val cancelFocusRequester = remember { FocusRequester() }
    val exitFocusRequester = remember { FocusRequester() }

    // Auto-focus the "No / Cancel" button first for safety against accidental clicks
    LaunchedEffect(Unit) {
        delay(100)
        try {
            cancelFocusRequester.requestFocus()
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
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(min = 340.dp, max = 440.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(TvColors.Surface)
                .border(BorderStroke(1.dp, TvColors.BorderSubtle), RoundedCornerShape(14.dp))
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            // Icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TvColors.SurfaceRaised)
                    .border(BorderStroke(1.dp, TvColors.BorderSubtle), RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ExitToApp,
                    contentDescription = null,
                    tint = TvColors.Danger,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = "Exit TeleMate",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TvFonts.Body
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = "Are you sure you want to close the app?",
                color = TvColors.TextSecondary,
                fontSize = 14.sp,
                fontFamily = TvFonts.Body
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons: No (Cancel) and Yes (Exit)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // "No" / Cancel button
                var isCancelFocused by remember { mutableStateOf(false) }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCancelFocused) TvColors.Focus else TvColors.SurfaceRaised)
                        .border(
                            BorderStroke(
                                if (isCancelFocused) 1.5.dp else 1.dp,
                                if (isCancelFocused) TvColors.FocusGlow else TvColors.BorderSubtle
                            ),
                            RoundedCornerShape(8.dp)
                        )
                        .focusRequester(cancelFocusRequester)
                        .onFocusChanged { isCancelFocused = it.isFocused }
                        .clickable { onDismiss() }
                        .onPreviewKeyEvent { event ->
                            if ((event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) &&
                                (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                 event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                            ) {
                                onDismiss()
                                true
                            } else false
                        }
                        .focusable()
                ) {
                    Text(
                        text = "No",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = TvFonts.Body
                    )
                }

                // "Yes" / Exit button
                var isExitFocused by remember { mutableStateOf(false) }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isExitFocused) TvColors.Danger else TvColors.SurfaceRaised)
                        .border(
                            BorderStroke(
                                if (isExitFocused) 1.5.dp else 1.dp,
                                if (isExitFocused) TvColors.Danger else TvColors.BorderSubtle
                            ),
                            RoundedCornerShape(8.dp)
                        )
                        .focusRequester(exitFocusRequester)
                        .onFocusChanged { isExitFocused = it.isFocused }
                        .clickable { onConfirmExit() }
                        .onPreviewKeyEvent { event ->
                            if ((event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) &&
                                (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                 event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER)
                            ) {
                                onConfirmExit()
                                true
                            } else false
                        }
                        .focusable()
                ) {
                    Text(
                        text = "Yes, Exit",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = TvFonts.Body
                    )
                }
            }
        }
    }
}
