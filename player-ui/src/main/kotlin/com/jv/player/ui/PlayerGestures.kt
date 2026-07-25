package com.jv.player.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/** Horizontal gesture zones over the video surface (design §5 thirds). */
private enum class Zone { Left, Center, Right }

/**
 * The surface gesture layer (design §5). Applied to the video area *below* the top bar and
 * *outside* the seekbar's owned band, so the seekbar's exclusive pointer ownership (§4.3) is
 * never contested — this layer only ever sees pointers the seekbar didn't claim.
 *
 * Wave 2 wires every critical mechanic so it is reachable in `:demo` (the issue's ask):
 *  - **single tap** anywhere → toggle chrome (§5 center) — here applied surface-wide.
 *  - **double tap** left / right third → discrete ∓/±10s seek (§5); center third → play/pause.
 *  - **pinch** anywhere → resize/zoom: crossing the §5.1 thresholds flips Fit ⇄ Crop and keeps
 *    the `[⤢]` button and the gesture in agreement.
 *
 * The *continuous* anchored pinch (no snap, no release-spring — §5.1) and double-tap
 * accumulation are the Wave 3 hardening + CEO on-device gate; Wave 2 delivers the reachable,
 * discrete control the hardening refines.
 */
internal fun Modifier.playerGestures(controller: PlayerController, onToggleChrome: () -> Unit): Modifier =
    this
        .pointerInput(controller) {
            detectTapGestures(
                onTap = { onToggleChrome() },
                onDoubleTap = { offset ->
                    when (zoneOf(offset.x, size.width)) {
                        Zone.Left -> controller.seekBy(-PlayerTokens.SEEK_STEP_MS)
                        Zone.Right -> controller.seekBy(PlayerTokens.SEEK_STEP_MS)
                        Zone.Center -> controller.playPause()
                    }
                },
            )
        }
        .pointerInput(controller) {
            var cumulativeZoom = 1f
            detectTransformGestures { _, _, zoom, _ ->
                cumulativeZoom *= zoom
                when {
                    cumulativeZoom >= PlayerTokens.PINCH_ZOOM_IN_THRESHOLD -> {
                        controller.setResizeMode(ResizeMode.Zoom)
                        cumulativeZoom = 1f
                    }

                    cumulativeZoom <= PlayerTokens.PINCH_ZOOM_OUT_THRESHOLD -> {
                        controller.setResizeMode(ResizeMode.Fit)
                        cumulativeZoom = 1f
                    }
                }
            }
        }

// Design §5 thirds: the left/right gesture zones each span one third of the surface width.
private const val LEFT_ZONE_MAX_FRACTION = 1f / 3f
private const val RIGHT_ZONE_MIN_FRACTION = 2f / 3f

private fun zoneOf(x: Float, width: Int): Zone {
    if (width <= 0) return Zone.Center
    return when {
        x < width * LEFT_ZONE_MAX_FRACTION -> Zone.Left
        x > width * RIGHT_ZONE_MIN_FRACTION -> Zone.Right
        else -> Zone.Center
    }
}
