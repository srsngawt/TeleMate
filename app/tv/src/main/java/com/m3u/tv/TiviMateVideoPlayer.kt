package com.m3u.tv

import android.view.LayoutInflater
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Production-grade ExoPlayer video surface for Android TV.
 *
 * WHY TextureView (not SurfaceView):
 * SurfaceView's underlying Surface buffer is set by MediaCodec to NATIVE video resolution
 * (e.g. 1280×720 for 720p). This buffer is composited at that size regardless of view layout,
 * causing the video to appear top-left at 66% when switching from 1080p → 720p.
 *
 * TextureView uses SurfaceTexture: its buffer IS the texture, and the GPU always stretches
 * it to fill the TextureView's view bounds. So RESIZE_MODE_FILL makes the TextureView
 * 1920×1080 and the GPU scales ANY video resolution to fill it — no top-left shrink.
 *
 * WHY no Matrix transform:
 * TextureView's GPU already stretches the video buffer to view bounds.
 * Applying an extra Matrix causes double-scaling (wrong). We just reset transform to null
 * on every quality switch so no stale matrix lingers.
 */
@OptIn(UnstableApi::class)
@Composable
fun TiviMateVideoPlayer(
    player: Player?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            val view = LayoutInflater.from(context)
                .inflate(R.layout.tivimate_player_view, null) as PlayerView

            view.layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // FILL: TextureView is always sized to full parent (e.g. 1920×1080)
            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL

            view.player = player

            // SCALE_TO_FIT: lets GPU handle upscaling via TextureView stretch
            (player as? androidx.media3.exoplayer.ExoPlayer)?.videoScalingMode =
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT

            val sizeListener = object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    view.post {
                        // Reset any stale matrix so TextureView's natural GPU stretch takes over
                        (view.videoSurfaceView as? TextureView)?.setTransform(null)
                        // Re-assert fill so AspectRatioFrameLayout doesn't cache old dimensions
                        view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
                        view.requestLayout()
                    }
                }
            }
            player?.addListener(sizeListener)

            view.setTag(R.id.tivimate_exo_player_view, sizeListener)
            view
        },
        update = { view ->
            if (view.player != player) {
                view.player = player
                (player as? androidx.media3.exoplayer.ExoPlayer)?.videoScalingMode =
                    C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            // Always ensure no stale matrix on player/channel change
            (view.videoSurfaceView as? TextureView)?.setTransform(null)
        },
        onRelease = { view ->
            (view.getTag(R.id.tivimate_exo_player_view) as? Player.Listener)?.let {
                view.player?.removeListener(it)
            }
            view.player = null
        },
        modifier = modifier
    )
}
