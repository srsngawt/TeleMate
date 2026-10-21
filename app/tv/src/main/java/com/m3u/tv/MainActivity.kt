package com.m3u.tv

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import com.m3u.data.service.PlayerManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var playerManager: PlayerManager

    private var wasPlayingBeforeStop = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = TvColors.Focus,
                    onPrimary = TvColors.OnFocus,
                    secondary = TvColors.Accent,
                    background = TvColors.Background,
                    onBackground = TvColors.TextPrimary,
                    surface = TvColors.Surface,
                    onSurface = TvColors.TextPrimary,
                    surfaceVariant = TvColors.SurfaceRaised,
                    onSurfaceVariant = TvColors.TextSecondary
                )
            ) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    App()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (wasPlayingBeforeStop) {
            wasPlayingBeforeStop = false
            try {
                playerManager.pauseOrContinue(true)
            } catch (_: Exception) {}
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            wasPlayingBeforeStop = playerManager.isPlaying.value
            playerManager.pauseOrContinue(false)
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            playerManager.release()
        } catch (_: Exception) {}
        try {
            StreamRecorder.stop()
        } catch (_: Exception) {}
    }

    override fun finish() {
        try {
            playerManager.release()
        } catch (_: Exception) {}
        try {
            StreamRecorder.stop()
        } catch (_: Exception) {}
        super.finish()
    }
}
