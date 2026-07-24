@file:OptIn(UnstableApi::class)

package com.jv.player.core.source

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import com.jv.player.api.PlaybackReader
import com.jv.player.api.PlaybackSource
import java.io.EOFException

/**
 * Media3 [DataSource] adapter over a [PlaybackSource] (APP-583 plan §3.3).
 *
 * This is the whole seam on the engine side: it never learns how bytes are produced,
 * it only calls [PlaybackSource.openReader]. **One instance per transfer** — Media3
 * creates a fresh [DataSource] for every `open()`/`close()` cycle, so there is no shared
 * mutable cursor and the class need not be thread-safe (plan §3.7). Extending
 * [BaseDataSource] wires `TransferListener` bookkeeping (bandwidth meter/diagnostics)
 * for free.
 *
 * Seeking (plan §3.4): when ExoPlayer seeks it closes the current transfer and calls
 * [open] again with a [DataSpec] whose `position` is the new absolute byte offset — so
 * a seek is simply "open a reader at offset N". No whole-file buffering ever happens
 * here; random access is delegated wholesale to the source's reader.
 */
internal class PlaybackSourceDataSource(
    private val source: PlaybackSource,
) : BaseDataSource(/* isNetwork = */ false) {

    private var reader: PlaybackReader? = null
    private var uri: Uri? = null

    /** Bytes still owed for this transfer, or [C.LENGTH_UNSET] (as a Long) when unbounded. */
    private var bytesRemaining: Long = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        uri = dataSpec.uri
        // dataSpec.position = absolute seek offset; dataSpec.length = requested span.
        reader = source.openReader(dataSpec.position, dataSpec.length)
        bytesRemaining = when {
            dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
            source.contentLength != C.LENGTH_UNSET.toLong() -> source.contentLength - dataSpec.position
            else -> C.LENGTH_UNSET.toLong()
        }
        transferStarted(dataSpec)
        return bytesRemaining
    }

    @Suppress("ReturnCount") // read() is a small state machine; early returns keep it flat.
    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength == 0) return 0
        val remaining = bytesRemaining
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val activeReader = reader ?: return C.RESULT_END_OF_INPUT

        val unbounded = remaining == C.LENGTH_UNSET.toLong()
        val toRead = if (unbounded) readLength else minOf(readLength.toLong(), remaining).toInt()

        val bytesRead = activeReader.read(buffer, offset, toRead)
        if (bytesRead == END_OF_READER) {
            // EOF from the reader. If length was known and we're short, that's a real error.
            if (unbounded) return C.RESULT_END_OF_INPUT
            throw EOFException("PlaybackReader hit EOF with $remaining bytes still expected")
        }

        if (!unbounded) bytesRemaining = remaining - bytesRead
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try {
            reader?.close()
        } finally {
            reader = null
            if (uri != null) {
                uri = null
                transferEnded()
            }
        }
    }

    private companion object {
        /** [PlaybackReader.read] returns this at end of input; equals [C.RESULT_END_OF_INPUT]. */
        const val END_OF_READER = -1
    }
}

/**
 * Builds a fresh [PlaybackSourceDataSource] per transfer for a single [source]
 * (plan §3.3). Bind one factory to one [PlaybackSource] and hand it to
 * `DefaultMediaSourceFactory`.
 */
internal class PlaybackSourceDataSourceFactory(
    private val source: PlaybackSource,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = PlaybackSourceDataSource(source)
}
