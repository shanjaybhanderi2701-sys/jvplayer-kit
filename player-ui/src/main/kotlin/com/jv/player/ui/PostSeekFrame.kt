package com.jv.player.ui

/**
 * Post-seek frame-render accounting (plan §5.3) — Wave 3 critical mechanic **3b**.
 *
 * After a committed `seekTo(T)` — even while paused — the surface must paint the frame *at* the
 * sought position, never a black flash or the pre-seek frame. `SeekParameters.EXACT` decodes
 * forward from the preceding keyframe to the exact target, and a [SurfaceView] avoids the
 * TextureView surface-swap flash. Proof that the correct frame actually reached the surface
 * comes from `ExoPlayer.setVideoFrameMetadataListener` — its `presentationTimeUs` reports the
 * timestamp of each frame pushed to the surface.
 *
 * [postSeekFrameSatisfied] is the load-bearing predicate the controller and the on-device
 * regression test share: given the last rendered frame's presentation time, has a frame *at or
 * near* the seek target been pushed? `EXACT` lands on the frame at/just before `T`, and frames
 * fall on decode boundaries, so the correct check is "within one frame of `T`", not exact
 * equality — the naive `==` never matches a real render and is the fail-then-pass RED case.
 */
internal object PostSeekFrame {
    /** One frame at 30fps, in microseconds — the default render tolerance around a seek target. */
    const val ONE_FRAME_30FPS_US = 33_333L

    /**
     * @param lastRenderedUs presentation time (µs) of the most recent frame pushed to the surface,
     *   or `null` if nothing has rendered since the seek.
     * @param targetUs the seek target in µs.
     * @param toleranceUs how far from [targetUs] still counts as "the sought frame" (default one frame).
     * @return true once a frame within [toleranceUs] of [targetUs] has been rendered.
     */
    fun postSeekFrameSatisfied(
        lastRenderedUs: Long?,
        targetUs: Long,
        toleranceUs: Long = ONE_FRAME_30FPS_US,
    ): Boolean {
        if (lastRenderedUs == null) return false
        // A real decoded frame lands on a decode boundary at/just before T, not exactly on it —
        // so "within one frame of the target" is the correct proof the sought frame was painted.
        return kotlin.math.abs(lastRenderedUs - targetUs) <= toleranceUs
    }
}
