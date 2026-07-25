package com.jv.player.ui

/**
 * Continuous pinch-zoom accumulator (plan §5.2) — Wave 3 critical mechanic **3c**.
 *
 * **The one rule:** the applied [scale] *accumulates* across the frames of a single pinch
 * gesture. Each [ScaleGestureDetector][android.view.ScaleGestureDetector] callback delivers an
 * *incremental* factor (`detector.scaleFactor`, the ratio of this frame's pointer span to the
 * previous frame's); the correct behaviour multiplies that into the persisted [scale]. The
 * classic bug — the one §5.2 calls out — is assigning the raw per-frame factor to [scale], which
 * snaps the video back toward 1× on every callback so the zoom judders instead of growing.
 *
 * This type is deliberately pure (no Android, no Compose) so the fail-then-pass regression lock
 * ([PinchZoomTest]) can drive a scripted multi-frame gesture on the JVM and assert the applied
 * scale is monotonic and equals the cumulative product — the exact assertions §5.2 specifies.
 * The Compose surface owns one instance for the view's lifetime (never re-created per gesture)
 * and maps [scale] onto the SurfaceView container transform.
 */
internal class PinchZoom(
    private val minScale: Float = MIN_SCALE,
    private val maxScale: Float = MAX_SCALE,
) {
    /** The cumulative applied scale, clamped to `[minScale, maxScale]`. Starts at identity (1×). */
    var scale: Float = MIN_SCALE
        private set

    /**
     * Feeds one gesture-frame factor from the detector and returns the new cumulative [scale].
     * @param scaleFactor the *incremental* per-callback factor (`detector.scaleFactor`).
     */
    fun onScale(scaleFactor: Float): Float {
        // NAIVE (RED): assigns the per-frame factor instead of accumulating it, so the scale
        // collapses back toward the single-frame ratio every callback (the §5.2 juddering bug).
        scale = scaleFactor.coerceIn(minScale, maxScale)
        return scale
    }

    /** Resets to identity. Call when media changes or zoom is dismissed — never per gesture frame. */
    fun reset() {
        scale = MIN_SCALE
    }

    companion object {
        const val MIN_SCALE = 1f
        const val MAX_SCALE = 4f
    }
}
