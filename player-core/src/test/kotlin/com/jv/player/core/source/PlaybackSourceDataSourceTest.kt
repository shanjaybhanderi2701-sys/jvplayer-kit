@file:OptIn(UnstableApi::class)

package com.jv.player.core.source

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.jv.player.api.PlaybackMetadata
import com.jv.player.api.PlaybackReader
import com.jv.player.api.PlaybackSource
import java.io.ByteArrayOutputStream
import java.io.EOFException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for the [PlaybackSourceDataSource] seam adapter (APP-583 plan §3.3/§3.4).
 * Cover the make-or-break contract: byte-range accounting, random-access seek via
 * re-`open()` at a new offset with **no whole-file load**, and EOF behavior.
 *
 * Runs on the JVM via Robolectric because `DataSpec` needs a real `android.net.Uri`.
 */
@Suppress("MagicNumber", "TooManyFunctions", "LargeClass")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackSourceDataSourceTest {

    private val uri: Uri = Uri.parse("jvsource://fake")

    // --- byte-range reads ----------------------------------------------------------

    @Test
    fun `open returns content length and read streams exact bytes from start`() {
        val content = bytes(16)
        val source = FakeSource(content)
        val ds = PlaybackSourceDataSource(source)

        val declared = ds.open(spec(position = 0, length = C.LENGTH_UNSET.toLong()))
        assertEquals(16L, declared)
        assertArrayEquals(content, readAll(ds))
        assertEquals(listOf(0L), source.openPositions)
    }

    @Test
    fun `bounded length caps the transfer to the requested span`() {
        val content = bytes(100)
        val source = FakeSource(content)
        val ds = PlaybackSourceDataSource(source)

        val declared = ds.open(spec(position = 10, length = 16))
        assertEquals(16L, declared)

        val read = readAll(ds)
        assertArrayEquals(content.copyOfRange(10, 26), read)
        // Span exhausted → further reads report EOF, not more bytes.
        assertEquals(C.RESULT_END_OF_INPUT, ds.read(ByteArray(4), 0, 4))
    }

    @Test
    fun `zero-length read returns zero without touching the reader`() {
        val source = FakeSource(bytes(8))
        val ds = PlaybackSourceDataSource(source)
        ds.open(spec(position = 0, length = C.LENGTH_UNSET.toLong()))

        assertEquals(0, ds.read(ByteArray(4), 0, 0))
    }

    // --- random-access seek --------------------------------------------------------

    @Test
    fun `seek re-opens the reader at a new offset with no whole-file load`() {
        val content = bytes(32)
        val source = FakeSource(content)

        // First transfer: read a few bytes from the start, then close (as ExoPlayer does on seek).
        val first = PlaybackSourceDataSource(source)
        first.open(spec(position = 0, length = C.LENGTH_UNSET.toLong()))
        val head = ByteArray(8)
        assertEquals(8, first.read(head, 0, 8))
        first.close()

        // Seek: a brand-new transfer opened directly at byte offset 20.
        val second = PlaybackSourceDataSource(source)
        val declared = second.open(spec(position = 20, length = C.LENGTH_UNSET.toLong()))
        assertEquals(12L, declared) // contentLength(32) - position(20)
        assertArrayEquals(content.copyOfRange(20, 32), readAll(second))

        assertEquals(listOf(0L, 20L), source.openPositions)
        // Proof of no whole-file buffering: only the 8 head bytes + 12 tail bytes were
        // ever served. The 12 bytes between offset 8 and 20 were never read.
        assertEquals(20, source.totalBytesServed)
    }

    // --- EOF behavior --------------------------------------------------------------

    @Test
    fun `known length but short reader throws EOFException`() {
        val content = bytes(50)
        val source = FakeSource(content)
        val ds = PlaybackSourceDataSource(source)
        // Ask for 100 bytes though only 50 exist → the reader hits EOF while bytes are still owed.
        ds.open(spec(position = 0, length = 100))

        assertThrows(EOFException::class.java) { readAll(ds) }
    }

    @Test
    fun `unbounded transfer ends cleanly at reader EOF`() {
        val content = bytes(24)
        // Unknown content length AND unset span → unbounded transfer.
        val source = FakeSource(content, contentLength = C.LENGTH_UNSET.toLong())
        val ds = PlaybackSourceDataSource(source)

        val declared = ds.open(spec(position = 0, length = C.LENGTH_UNSET.toLong()))
        assertEquals(C.LENGTH_UNSET.toLong(), declared)
        assertArrayEquals(content, readAll(ds))
        assertEquals(C.RESULT_END_OF_INPUT, ds.read(ByteArray(4), 0, 4))
    }

    // --- lifecycle -----------------------------------------------------------------

    @Test
    fun `getUri reflects the opened spec and close releases the reader`() {
        val source = FakeSource(bytes(16))
        val ds = PlaybackSourceDataSource(source)
        ds.open(spec(position = 0, length = C.LENGTH_UNSET.toLong()))

        assertEquals(uri, ds.uri)
        ds.close()
        assertTrue("reader must be closed", source.lastReaderClosed)
        // Reads after close are inert.
        assertEquals(C.RESULT_END_OF_INPUT, ds.read(ByteArray(4), 0, 4))
        ds.close() // idempotent, must not throw
    }

    @Test
    fun `factory creates a fresh data source per transfer`() {
        val factory = PlaybackSourceDataSourceFactory(FakeSource(bytes(4)))
        assertNotSame(factory.createDataSource(), factory.createDataSource())
    }

    @Test
    fun `transfer listener receives byte counts and lifecycle callbacks`() {
        val content = bytes(40)
        val source = FakeSource(content)
        val ds = PlaybackSourceDataSource(source)
        val listener = CountingTransferListener()
        ds.addTransferListener(listener)

        ds.open(spec(position = 0, length = C.LENGTH_UNSET.toLong()))
        readAll(ds)
        ds.close()

        assertEquals(1, listener.starts)
        assertEquals(40, listener.bytesTransferred)
        assertEquals(1, listener.ends)
    }

    @Test
    fun `api LENGTH_UNSET equals Media3 C_LENGTH_UNSET`() {
        // The `:player-api` sentinel is Media3-free but must bridge to C.LENGTH_UNSET (plan §3.2).
        assertEquals(C.LENGTH_UNSET.toLong(), PlaybackSource.LENGTH_UNSET)
    }

    // --- helpers -------------------------------------------------------------------

    private fun spec(position: Long, length: Long): DataSpec =
        DataSpec.Builder()
            .setUri(uri)
            .setPosition(position)
            .setLength(length)
            .build()

    private fun readAll(ds: DataSource): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(7) // odd chunk size to exercise partial-fill accounting
        while (true) {
            val n = ds.read(buf, 0, buf.size)
            if (n == C.RESULT_END_OF_INPUT) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun bytes(size: Int): ByteArray = ByteArray(size) { (it % 256).toByte() }

    /**
     * In-memory [PlaybackSource]. `openReader(position, …)` serves bytes from that
     * absolute offset (like `RandomAccessFile.seek`), records every open position, and
     * tallies bytes actually served so tests can prove no whole-file buffering happened.
     */
    private class FakeSource(
        private val content: ByteArray,
        override val contentLength: Long = content.size.toLong(),
        override val mimeType: String? = null,
    ) : PlaybackSource {
        override val id: String = "fake"
        override val metadata: PlaybackMetadata = PlaybackMetadata()
        override val uri: Uri = Uri.parse("jvsource://fake")

        val openPositions = mutableListOf<Long>()
        var totalBytesServed: Int = 0
            private set
        var lastReaderClosed: Boolean = false
            private set

        override fun openReader(position: Long, length: Long): PlaybackReader {
            openPositions += position
            lastReaderClosed = false
            return FakeReader(position.toInt())
        }

        private inner class FakeReader(startOffset: Int) : PlaybackReader {
            private var pos = startOffset
            private var closed = false

            override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
                check(!closed) { "read after close" }
                if (pos >= content.size) return -1
                val n = minOf(readLength, content.size - pos)
                System.arraycopy(content, pos, buffer, offset, n)
                pos += n
                totalBytesServed += n
                return n
            }

            override fun close() {
                closed = true
                lastReaderClosed = true
            }
        }
    }

    private class CountingTransferListener : TransferListener {
        var starts = 0
        var ends = 0
        var bytesTransferred = 0

        override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

        override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            starts++
        }

        override fun onBytesTransferred(
            source: DataSource,
            dataSpec: DataSpec,
            isNetwork: Boolean,
            bytesTransferred: Int,
        ) {
            this.bytesTransferred += bytesTransferred
        }

        override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
            ends++
        }
    }
}
