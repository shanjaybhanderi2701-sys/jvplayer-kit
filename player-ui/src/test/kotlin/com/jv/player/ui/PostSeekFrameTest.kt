package com.jv.player.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fail-then-pass regression lock for critical mechanic **3b** — post-seek frame render (plan §5.3).
 *
 * Locks the predicate the controller and the on-device instrumentation test share: after
 * `seekTo(T)`, has a frame *at/near* `T` reached the surface? `SeekParameters.EXACT` lands on the
 * frame at or just before `T` and frames fall on decode boundaries, so "within one frame of `T`"
 * is correct and exact-microsecond equality is wrong.
 *
 * RED (`lastRenderedUs == targetUs`): a real decoded frame a few ms off `T` is rejected →
 * [rendersTargetFrameWithinOneFrame] fails. GREEN (within-tolerance): passes.
 */
class PostSeekFrameTest {
    private val targetUs = 5_000_000L // seek to 5.000s

    @Test
    fun rendersTargetFrameWithinOneFrame() {
        // EXACT lands on the frame at/just before T — here ~10ms before the target.
        val renderedUs = targetUs - 10_000L
        assertTrue(
            "a frame within one frame of the seek target must satisfy the post-seek render gate",
            PostSeekFrame.postSeekFrameSatisfied(lastRenderedUs = renderedUs, targetUs = targetUs),
        )
    }

    @Test
    fun exactHitSatisfies() {
        assertTrue(PostSeekFrame.postSeekFrameSatisfied(lastRenderedUs = targetUs, targetUs = targetUs))
    }

    @Test
    fun staleOrBlackFrameFails() {
        // No frame rendered since the seek (black), or a lingering pre-seek frame far from T.
        assertFalse(PostSeekFrame.postSeekFrameSatisfied(lastRenderedUs = null, targetUs = targetUs))
        assertFalse(PostSeekFrame.postSeekFrameSatisfied(lastRenderedUs = 1_000_000L, targetUs = targetUs))
    }
}
