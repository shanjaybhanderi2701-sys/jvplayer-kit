package com.jv.player.ui

/**
 * Seekbar scrub state machine (plan §5.1) — Wave 3 critical mechanic **3a**.
 *
 * **The one rule:** while the finger is down the drag is a *preview* — the player is never
 * touched. Exactly one commit ([onCommit]) fires, on release, at the final previewed position.
 * A naive scrubber that issues a seek per `ACTION_MOVE` machine-guns the engine (~30 seeks/s),
 * which is the audio/video thrash §5.1 rules out.
 *
 * A gesture is bracketed by [onScrubStart]/[onScrubEnd] (a "scrub session"). Those let the host
 * mark the engine as scrubbing for the duration — on Media3 ≥ 1.6 this is where
 * `setScrubbingModeEnabled` would attach; at the pinned 1.5.1 there is no such API, so the
 * session is a no-op seam today, but the single-commit-on-release invariant (the load-bearing
 * part) holds regardless of Media3 version.
 *
 * Pure (no Android/Compose) so [SeekScrubberTest] can drive DOWN → 20×MOVE → UP on the JVM and
 * assert the commit count directly — the fail-then-pass lock §5.1 specifies. The Compose
 * [PlayerSeekbar] owns one instance and forwards its drag callbacks here.
 *
 * @param onCommit invoked exactly once per gesture, on [release], with the final fraction `[0,1]`.
 * @param onScrubStart invoked once when a gesture begins (open the scrub session).
 * @param onScrubEnd invoked once when a gesture ends, after [onCommit] (close the scrub session).
 */
internal class SeekScrubber(
    private val onCommit: (fraction: Float) -> Unit,
    private val onScrubStart: () -> Unit = {},
    private val onScrubEnd: () -> Unit = {},
) {
    /** The previewed fraction `[0,1]` tracked during the drag; drives the thumb, never the player. */
    var previewFraction: Float = 0f
        private set

    /** True between [begin] and [release]/[cancel]. */
    var isScrubbing: Boolean = false
        private set

    /** ACTION_DOWN: latch to the finger and open the scrub session. */
    fun begin(fraction: Float) {
        isScrubbing = true
        previewFraction = fraction.coerceIn(0f, 1f)
        onScrubStart()
    }

    /** ACTION_MOVE: update the preview only. Must never commit. */
    fun moveTo(fraction: Float) {
        if (!isScrubbing) return
        // Preview only — update local state, never touch the player. The single commit fires in
        // release(); issuing a seek here is the ~30 seeks/second thrash §5.1 forbids.
        previewFraction = fraction.coerceIn(0f, 1f)
    }

    /** ACTION_UP: commit exactly once at the final preview, then close the session. */
    fun release() {
        if (!isScrubbing) return
        isScrubbing = false
        onCommit(previewFraction)
        onScrubEnd()
    }

    /** ACTION_CANCEL: like [release], commit at the last preview (§5.1 treats cancel as a commit). */
    fun cancel() = release()
}
