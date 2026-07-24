package com.jv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import com.jv.player.core.JvPlayer
import kotlinx.coroutines.delay

/**
 * The player control surface — the public Wave 2 entry point (APP-587). Stacks, in order:
 * SurfaceView render target (§5.3) → gesture layer (§5) → scrim → chrome (top bar, center
 * transport, bottom bar with seekbar + resize). It binds its own [PlayerController] over the
 * Wave 1 [engine]; the caller owns the engine's lifecycle (create + `setSource` + `release`).
 *
 * Chrome follows the design's resting rule (§3.2): it starts visible, auto-hides after
 * [PlayerTokens.CHROME_AUTOHIDE_MS] **while playing**, and **stays visible while paused**
 * (paused = the user is deciding — don't hide their controls). A single tap toggles it (§5).
 *
 * The surface knows nothing about files, paths, or storage — it drives only the controller
 * façade, keeping it encryption-agnostic per design §10.
 */
@Composable
fun PlayerSurface(engine: JvPlayer, title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val controller = rememberPlayerController(engine)
    PlayerSurfaceContent(
        controller = controller,
        title = title,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun PlayerSurfaceContent(
    controller: PlayerController,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var chromeVisible by remember { mutableStateOf(true) }

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
        PlayerRenderView(controller = controller, modifier = Modifier.fillMaxSize())

        // Gesture layer over the whole surface (§5). Sits above the render target and below
        // the chrome buttons, so button taps win where they are and everything else is a
        // surface gesture.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .playerGestures(controller = controller, onToggleChrome = { chromeVisible = !chromeVisible }),
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
    }
}
