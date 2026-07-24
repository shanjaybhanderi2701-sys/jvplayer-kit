package com.jv.player.demo.source

import android.net.Uri
import androidx.media3.common.MimeTypes
import com.jv.player.api.PlaybackMetadata
import com.jv.player.api.PlaybackReader
import com.jv.player.api.PlaybackSource
import java.io.File
import java.io.RandomAccessFile

/**
 * Plain-file reference [PlaybackSource] for the `:demo` app (APP-583 plan §3.6 / §4).
 *
 * This is the *trivial* source that exercises the exact same seam a real host app uses,
 * just backed by a plain file: [openReader] returns a reader whose random access is a
 * `RandomAccessFile.seek(position)`. The SDK sees identical [PlaybackReader] semantics
 * here as it would for any other source — that is the seam working. No storage or
 * transform logic of any kind lives in the SDK.
 */
class FilePlaybackSource(
    private val file: File,
) : PlaybackSource {
    override val id: String = file.absolutePath
    override val metadata: PlaybackMetadata = PlaybackMetadata(title = file.name)

    /** Opaque private-scheme URI — the SDK treats it as an identity token, not a path. */
    override val uri: Uri = Uri.parse("jvsource://${Uri.encode(file.name)}")

    /** Explicit container hint from the file extension; null lets the extractors sniff (§3.5). */
    override val mimeType: String? = mimeFromExtension(file.name)

    override val contentLength: Long = file.length()

    override fun openReader(position: Long, length: Long): PlaybackReader = FileReader(file, position)

    private class FileReader(file: File, position: Long) : PlaybackReader {
        private val randomAccess = RandomAccessFile(file, "r").apply { seek(position) }

        override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int =
            randomAccess.read(buffer, offset, readLength) // returns -1 at EOF, matching the contract

        override fun close() = randomAccess.close()
    }

    private companion object {
        fun mimeFromExtension(name: String): String? = when {
            name.endsWith(".mp4", ignoreCase = true) -> MimeTypes.VIDEO_MP4
            name.endsWith(".m4v", ignoreCase = true) -> MimeTypes.VIDEO_MP4
            name.endsWith(".mkv", ignoreCase = true) -> MimeTypes.VIDEO_MATROSKA
            name.endsWith(".webm", ignoreCase = true) -> MimeTypes.VIDEO_WEBM
            else -> null
        }
    }
}
