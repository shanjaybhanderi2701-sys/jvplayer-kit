package com.jv.player.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fail-then-pass regression lock for critical mechanic **3a** — seek-on-release (plan §5.1).
 *
 * Drives DOWN → 20×MOVE → UP against a [SeekScrubber] whose commit callback counts seeks.
 * Asserts: **zero** commits across all moves, and **exactly one** commit — on release.
 *
 * RED (commit-per-move): 20 commits fire during the drag → [seekCommitsExactlyOnceOnRelease]
 * fails its "0 commits during drag" assert. GREEN (commit only in `release()`): passes.
 */
class SeekScrubberTest {
    @Test
    fun seekCommitsExactlyOnceOnRelease() {
        var commits = 0
        var starts = 0
        var ends = 0
        var committedFraction = -1f
        val scrubber = SeekScrubber(
            onCommit = { commits++; committedFraction = it },
            onScrubStart = { starts++ },
            onScrubEnd = { ends++ },
        )

        scrubber.begin(0.10f)
        repeat(20) { i -> scrubber.moveTo(0.10f + i * 0.01f) }

        // The load-bearing invariant: nothing is committed to the player during the drag.
        assertEquals("no seek may be committed during the drag", 0, commits)

        scrubber.release()

        assertEquals("exactly one seek is committed, on release", 1, commits)
        assertEquals("scrub session opens exactly once", 1, starts)
        assertEquals("scrub session closes exactly once", 1, ends)
        assertEquals("committed position equals the final previewed position", 0.29f, committedFraction, 1e-4f)
    }

    @Test
    fun noCommitWithoutRelease() {
        var commits = 0
        val scrubber = SeekScrubber(onCommit = { commits++ })
        scrubber.begin(0.2f)
        repeat(5) { scrubber.moveTo(0.5f) }
        assertEquals("an unreleased drag never commits", 0, commits)
    }

    @Test
    fun movesBeforeBeginAreIgnored() {
        var commits = 0
        val scrubber = SeekScrubber(onCommit = { commits++ })
        scrubber.moveTo(0.5f) // stray pointer before a real gesture
        scrubber.release()
        assertEquals(0, commits)
    }
}
