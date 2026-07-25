package com.jv.player.core

import android.net.Uri
import com.jv.player.api.PlaybackMetadata
import com.jv.player.api.PlaybackReader
import com.jv.player.api.PlaybackSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unit tests for the W4 resume-position entry point (APP-589 / plan §6 W4).
 *
 * [JvPlayer.setSource] applies `startPositionMs` as ExoPlayer's initial seek before prepare, so the
 * engine's masking position reflects the resume point immediately — the property a host relies on
 * to hand back a persisted [JvPlayer.currentPositionMs] and continue where the user left off. These
 * assert the *masking* position (synchronous, no decode), which is exactly what the resume contract
 * guarantees regardless of when loading actually completes.
 *
 * Runs on the JVM via Robolectric because [JvPlayer.create] builds a real `ExoPlayer`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JvPlayerResumeTest {
    private lateinit var player: JvPlayer

    @Before
    fun setUp() {
        player = JvPlayer.create(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        player.release()
    }

    @Test
    fun `setSource resumes at the requested start position`() {
        player.setSource(FakeSource(), playWhenReady = false, startPositionMs = 30_000L)
        assertEquals(30_000L, player.currentPositionMs)
    }

    @Test
    fun `setSource without a start position begins at zero`() {
        player.setSource(FakeSource(), playWhenReady = false)
        assertEquals(0L, player.currentPositionMs)
    }

    @Test
    fun `a negative start position is clamped to zero`() {
        player.setSource(FakeSource(), playWhenReady = false, startPositionMs = -5_000L)
        assertEquals(0L, player.currentPositionMs)
    }

    /** Minimal source — masking-position assertions never trigger a real byte read. */
    private class FakeSource : PlaybackSource {
        override val id = "fake"
        override val metadata = PlaybackMetadata(title = "fake")
        override val mimeType: String? = null
        override val uri: Uri = Uri.parse("jvsource://fake")
        override val contentLength = PlaybackSource.LENGTH_UNSET

        override fun openReader(position: Long, length: Long): PlaybackReader =
            object : PlaybackReader {
                override fun read(buffer: ByteArray, offset: Int, readLength: Int) = -1

                override fun close() = Unit
            }
    }
}
