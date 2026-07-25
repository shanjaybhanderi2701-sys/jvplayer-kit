package com.jv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jv.player.core.JvPlayer
import kotlinx.coroutines.delay

/**
 * The player control surface — the public control-surface entry point. Stacks, in order:
 * SurfaceView render target (§5.3) → audio-only backdrop → gesture layer (§5) → scrim → chrome
 * (top bar, center transport, bottom bar with seekbar + resize) → error/retry overlay. It binds
 * its own [PlayerController] over the [engine]; the caller owns the engine's lifecycle (create +
 * `setSource` + `release`).
 *
 * Chrome follows the design's resting rule (§3.2): it starts visible, auto-hides after
 * [PlayerTokens.CHROME_AUTOHIDE_MS] **while playing**, and **stays visible while paused**
 * (paused = the user is deciding — don't hide their controls). A single tap toggles it (§5).
 *
 * **W4 hardening (APP-589):** an audio-only source shows an [AudioOnlyBackdrop] over the empty
 * surface; a playback error shows a [PlayerErrorOverlay] with one-tap retry; and when
 * [pauseOnBackground] is true (default) the surface **pauses when the host goes to the
 * background** (`ON_STOP`) — correct for a video player, and complementary to the engine's audio
 * focus handling. Hosts that want true background *audio* playback set `pauseOnBackground = false`
 * and drive their own foreground service/notification (out of SDK scope, per plan §2).
 *
 * The surface knows nothing about files, paths, or storage — it drives only the controller
 * façade, keeping it storage-agnostic per design §10.
 */
@Composable
fun PlayerSurface(
    engine: JvPlayer,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pauseOnBackground: Boolean = true,
) {
    val controller = rememberPlayerController(engine)
    PlayerSurfaceContent(
        controller = controller,
        title = title,
        onBack = onBack,
        modifier = modifier,
        pauseOnBackground = pauseOnBackground,
    )
}

@Composable
internal fun PlayerSurfaceContent(
    controller: PlayerController,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pauseOnBackground: Boolean = true,
) {
    var chromeVisible by remember { mutableStateOf(true) }

    // Background/foreground handling (§6 W4): pause when the host is no longer visible. Audio focus
    // (engine) covers interruptions while foregrounded; this covers the app leaving the screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    if (pauseOnBackground) {
        DisposableEffect(lifecycleOwner, controller) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) controller.pause()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    // Continuous pinch-zoom (§5.2 / mechanic 3c). One PinchZoom accumulator for the surface's
    // lifetime — never re-created per gesture. `contentScale`/`pan*` are the Compose-observed
    // transform the SurfaceView container renders; PinchZoom owns the accumulation logic (locked
    // by PinchZoomTest), the state here just mirrors its output for recomposition.
    val pinch = remember { PinchZoom() }
    var contentScale by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(0f) }

    // Auto-hide only while playing (§3.2). Restarts whenever visibility or play-state flips.
    LaunchedEffect(chromeVisible, controller.isPlaying) {
        if (chromeVisible && controller.isPlaying) {
            delay(PlayerTokens.CHROME_AUTOHIDE_MS)
            chromeVisible = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("player_surface"),
    ) {
        PlayerRenderView(
            controller = controller,
            scale = contentScale,
            panX = panX,
            panY = panY,
            modifier = Modifier.fillMaxSize(),
        )

        // Audio-only path (§6 W4): once ready with no video track, cover the empty surface.
        if (controller.phase == PlaybackPhase.Ready && !controller.hasVideo) {
            AudioOnlyBackdrop(title = title, modifier = Modifier.fillMaxSize())
        }

        // Gesture layer over the whole surface (§5). Sits above the render target and below
        // the chrome buttons, so button taps win where they are and everything else is a
        // surface gesture.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .playerGestures(
                    controller = controller,
                    onToggleChrome = { chromeVisible = !chromeVisible },
                    onPinch = { zoomFactor, pan ->
                        contentScale = pinch.onScale(zoomFactor)
                        if (contentScale > 1f) {
                            // Pan only while zoomed in; clamped to the scaled content in the
                            // render view. Reset to centre when the pinch returns to 1×.
                            panX += pan.x
                            panY += pan.y
                        } else {
                            panX = 0f
                            panY = 0f
                        }
                    },
                ),
        )

        if (chromeVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PlayerTokens.scrimControls),
            ) {
                PlayerTopBar(
                    title = title,
                    onBack = onBack,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(),
                )
                PlayerTransport(
                    controller = controller,
                    modifier = Modifier.align(Alignment.Center),
                )
                PlayerBottomBar(
                    controller = controller,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                )
            }
        }

        // Error/retry overlay (§6 W4). Above the chrome so it is reachable even after auto-hide;
        // renders only while the controller holds an error message.
        PlayerErrorOverlay(controller = controller, modifier = Modifier.fillMaxSize())
    }
}
