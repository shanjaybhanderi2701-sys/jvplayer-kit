@file:Suppress("MagicNumber") // Drawn dp/geometry values come straight from the design redlines (§2/§4.5).

package com.jv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import kotlin.math.roundToLong

/**
 * The scrubber — the interaction the design calls out as load-bearing (§4). Wave 2 delivers
 * the usable, correct-by-construction version the Wave 3 hardening + fail-then-pass tests
 * build on.
 *
 * **The one rule (§4.1):** while the finger is down and moving this is a PREVIEW — nothing
 * touches the player. On release, exactly one [PlayerController.seekTo] fires at the final
 * position. During the drag we mutate only local UI state ([dragFraction]); we never call
 * `seekTo` per move, so the thumb tracks the finger with zero lag and the engine sees one
 * seek per gesture, not ~30/second.
 *
 * **Fat-finger hit band (§4.2):** the row is a fixed 48dp-tall band; the drawn track/thumb
 * are small and grow only *visually* on latch, inside that fixed box — so latching never
 * reflows the time row (the ±1px no-shift invariant, §2). The band owns its pointer stream,
 * so a press here never leaks to the surrounding surface gestures (§4.3).
 */
@Composable
internal fun PlayerSeekbar(controller: PlayerController, modifier: Modifier = Modifier) {
    val durationMs = controller.durationMs
    val enabled = durationMs > 0

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(0f) }

    // While dragging, the thumb follows the finger (preview); otherwise it follows playback.
    val playbackFraction = if (enabled) (controller.positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (enabled) (controller.bufferedPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val thumbFraction = if (dragging) dragFraction else playbackFraction

    fun commit(fraction: Float) {
        controller.seekTo((fraction.coerceIn(0f, 1f) * durationMs).roundToLong())
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(PlayerTokens.seekHitHeight) // fixed hit band → no layout shift on latch/release.
            .testTag("player_seekbar")
            .onSizeChanged { widthPx = it.width.toFloat() }
            .then(
                if (!enabled) {
                    Modifier
                } else {
                    Modifier
                        .pointerInput(durationMs) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset: Offset ->
                                    dragging = true
                                    dragFraction = fractionOf(offset.x, widthPx) // jump-to-finger latch (§4.2).
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    val delta = if (widthPx > 0f) dragAmount / widthPx else 0f
                                    dragFraction = (dragFraction + delta).coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                    commit(dragFraction) // the one commit (§4.1).
                                    dragging = false
                                },
                                onDragCancel = {
                                    commit(dragFraction)
                                    dragging = false
                                },
                            )
                        }
                        .pointerInput(durationMs) {
                            detectTapGestures(onTap = { offset -> commit(fractionOf(offset.x, widthPx)) })
                        }
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        SeekbarTrack(
            thumbFraction = thumbFraction,
            bufferedFraction = bufferedFraction,
            dragging = dragging,
            enabled = enabled,
        )
    }
}

@Composable
private fun SeekbarTrack(thumbFraction: Float, bufferedFraction: Float, dragging: Boolean, enabled: Boolean) {
    val trackHeight = if (dragging) PlayerTokens.seekTrackDragging else PlayerTokens.seekTrackIdle
    val thumbSize = if (dragging) PlayerTokens.seekThumbDragging else PlayerTokens.seekThumbIdle

    // Track lane: inactive base, buffered-ahead fill, elapsed accent fill (drawn back-to-front).
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(trackHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(PlayerTokens.trackInactive),
    ) {
        if (enabled) {
            FractionBar(fraction = bufferedFraction, color = PlayerTokens.trackBuffer)
            FractionBar(fraction = thumbFraction, color = PlayerTokens.accent)
        }
    }

    if (enabled) {
        // Thumb sits at the elapsed fraction; grows on drag, but the parent band height is
        // fixed so nothing around it reflows.
        Box(
            modifier = Modifier.fillMaxWidth(thumbFraction),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(PlayerTokens.accent),
            )
        }
    }
}

@Composable
private fun FractionBar(fraction: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction.coerceIn(0f, 1f))
            .fillMaxHeight()
            .clip(RoundedCornerShape(percent = 50))
            .background(color),
    )
}

private fun fractionOf(x: Float, widthPx: Float): Float = if (widthPx > 0f) (x / widthPx).coerceIn(0f, 1f) else 0f
