package com.jv.player.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput

/** Horizontal gesture zones over the video surface (design §5 thirds). */
private enum class Zone { Left, Center, Right }

/**
 * The surface gesture layer (design §5). Applied to the video area *below* the top bar and
 * *outside* the seekbar's owned band, so the seekbar's exclusive pointer ownership (§4.3) is
 * never contested — this layer only ever sees pointers the seekbar didn't claim.
 *
 *  - **single tap** anywhere → toggle chrome (§5 center) — here applied surface-wide.
 *  - **double tap** left / right third → discrete ∓/±10s seek (§5); center third → play/pause.
 *  - **pinch** anywhere → *continuous* zoom (Wave 3 mechanic 3c, §5.2): each gesture frame's
 *    incremental factor + pan offset is forwarded to [onPinch]; the caller accumulates it in a
 *    persistent [PinchZoom] and applies the transform to the SurfaceView container. The discrete
 *    Fit/Fill/Crop presets stay on the `[⤢]` button so preset and pinch never fight (§5.2).
 *
 * @param onPinch called per gesture frame with the incremental scale factor and pan delta.
 */
internal fun Modifier.playerGestures(
    controller: PlayerController,
    onToggleChrome: () -> Unit,
    onPinch: (zoomFactor: Float, pan: Offset) -> Unit,
): Modifier =
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
            // One persistent detector for the view's lifetime (§5.2): every frame's incremental
            // factor is forwarded and accumulated by the caller — never reset per callback.
            detectTransformGestures { _, pan, zoom, _ -> onPinch(zoom, pan) }
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
