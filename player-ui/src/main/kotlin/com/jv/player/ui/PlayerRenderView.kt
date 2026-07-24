package com.jv.player.ui

import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout

/**
 * The video render target (APP-587 scope; design §5.3).
 *
 * **SurfaceView, deliberately** — not TextureView. A TextureView flashes a black frame when
 * its surface is swapped (which happens on resize-mode changes and post-seek re-render), the
 * exact stale/black-frame class of bug the design rules out (§5.3, §6). A [SurfaceView] owns
 * a dedicated hardware surface, so a resize or seek repaints without the swap flash.
 *
 * The [SurfaceView] is hosted inside a Media3 [AspectRatioFrameLayout], which does the
 * letterbox math for us: we feed it the video aspect ratio and the [ResizeMode], and it
 * sizes/positions the surface for Fit / Fill / Crop identically to how ExoPlayer's own
 * `PlayerView` would (§8). We drive it directly instead of using `PlayerView` so the chrome
 * is our own Compose surface, not Media3's stock controls.
 *
 * The engine is bound to the surface via [PlayerController.attachVideoSurface] on create and
 * released via [PlayerController.detachVideoSurface] on dispose, so no surface outlives the view.
 */
@Composable
internal fun PlayerRenderView(controller: PlayerController, modifier: Modifier = Modifier) {
    val resizeMode = controller.resizeMode
    val aspectRatio = controller.videoAspectRatio

    // Holds the attached SurfaceView so the dispose effect can detach exactly it.
    val boundSurface = remember { SurfaceHolderRef() }

    DisposableEffect(controller) {
        onDispose { boundSurface.view?.let(controller::detachVideoSurface) }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val surfaceView = SurfaceView(context)
            boundSurface.view = surfaceView
            controller.attachVideoSurface(surfaceView)
            AspectRatioFrameLayout(context).apply {
                layoutParams = matchParent()
                addView(surfaceView, matchParent())
            }
        },
        update = { frame ->
            frame.resizeMode = resizeMode.frameLayoutMode
            // A known ratio letterboxes correctly; 0f tells AspectRatioFrameLayout to fill
            // its bounds (used before the first frame reports a size).
            frame.setAspectRatio(aspectRatio)
        },
    )
}

/** Mutable single-slot holder for the SurfaceView bound to the engine (see [PlayerRenderView]). */
private class SurfaceHolderRef {
    var view: SurfaceView? = null
}

private fun matchParent(): ViewGroup.LayoutParams =
    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
