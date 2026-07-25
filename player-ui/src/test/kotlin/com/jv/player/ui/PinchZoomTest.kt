package com.jv.player.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fail-then-pass regression lock for critical mechanic **3c** — continuous pinch-zoom (plan §5.2).
 *
 * Drives a scripted multi-frame pinch (the incremental `detector.scaleFactor` per callback) and
 * asserts the applied scale is **monotonically non-decreasing** across the gesture and equals the
 * **cumulative product** at the end — never dropping back toward 1× mid-gesture.
 *
 * RED (reset-per-callback `scale = scaleFactor`): the applied scale follows each frame's factor,
 * so a decelerating pinch decreases frame-to-frame → [accumulatesAcrossGestureFrames] fails on the
 * monotonicity assert, and the end value is the last factor (~1.05), not the ~2.06 product.
 * GREEN (`scale *= scaleFactor`): both hold.
 */
class PinchZoomTest {
    @Test
    fun accumulatesAcrossGestureFrames() {
        val pinch = PinchZoom()

        // A single pinch-open gesture: strictly-decreasing per-frame factors (a decelerating
        // spread) that are each > 1. The naive reset-per-frame impl would make the applied scale
        // DECREASE frame to frame; correct accumulation makes it strictly INCREASE.
        val factors = floatArrayOf(1.6f, 1.15f, 1.12f, 1.05f, 1.02f)
        val expected = factors.fold(1f) { acc, f -> acc * f } // ≈ 2.06

        val applied = factors.map { pinch.onScale(it) }

        // Monotonic: every frame adds zoom; the scale never snaps back toward 1× mid-gesture.
        applied.zipWithNext().forEachIndexed { i, (prev, next) ->
            assertTrue(
                "scale must not drop back mid-gesture: frame ${i + 1} = $next < frame $i = $prev",
                next > prev,
            )
        }
        // Cumulative product lands at the end (±epsilon).
        assertEquals(expected, pinch.scale, 1e-3f)
    }

    @Test
    fun clampsToMaxScale() {
        val pinch = PinchZoom()
        repeat(20) { pinch.onScale(2f) } // would blow past 4× uncapped
        assertEquals(PinchZoom.MAX_SCALE, pinch.scale, 1e-4f)
    }

    @Test
    fun clampsToMinScaleOnPinchClosed() {
        val pinch = PinchZoom()
        repeat(20) { pinch.onScale(0.5f) } // pinch-closed below identity
        assertEquals(PinchZoom.MIN_SCALE, pinch.scale, 1e-4f)
    }

    @Test
    fun resetReturnsToIdentity() {
        val pinch = PinchZoom()
        pinch.onScale(2f)
        pinch.reset()
        assertEquals(PinchZoom.MIN_SCALE, pinch.scale, 1e-4f)
    }
}
