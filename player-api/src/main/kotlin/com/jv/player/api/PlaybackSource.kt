package com.jv.player.api

import android.net.Uri

/**
 * Storage-agnostic description of one playable item (APP-583 plan §3.2).
 *
 * The SDK's engine is built against **byte streams and identity**, never against
 * files, paths, or storage details. The only thing the engine knows how to do is ask a
 * [PlaybackSource] for bytes at an arbitrary offset. The consuming app is the sole
 * place that knows how to turn a [PlaybackSource] into actual bytes, which is what
 * keeps this contract free of any storage/transform concern and lets a host app serve
 * bytes however it likes without the SDK ever learning how.
 *
 * The critical property is **random-access byte-range reads**: ExoPlayer seeks by
 * closing the current transfer and re-opening a reader at a new absolute byte offset
 * (plan §3.4). A source that can cheaply serve "give me `length` bytes starting at
 * absolute `position`" therefore seeks with no whole-file buffering.
 *
 * This interface lives in `:player-api` and has **no Media3 dependency** by design, so
 * apps compile against the smallest possible, most stable surface (plan §2.2).
 */
interface PlaybackSource {
    /** Stable opaque identity. Used for equality, media-notification tags, resume keys. */
    val id: String

    /** Display metadata the SDK surfaces to UI/notification. */
    val metadata: PlaybackMetadata

    /**
     * Explicit MIME/container hint used when [uri] is opaque (a private scheme the
     * Media3 URI-suffix sniffer cannot read), e.g. `MimeTypes.VIDEO_MP4` /
     * `VIDEO_MATROSKA`. `null` lets the extractors sniff from the returned bytes
     * (plan §3.5). Prefer setting it when the app knows the container.
     */
    val mimeType: String?

    /**
     * Opaque URI carrying enough information for THIS source's reader to resolve bytes.
     * Uses a private scheme, e.g. `jvsource://<id>`. The SDK treats it as opaque and
     * never parses it — resolution is entirely the source's concern.
     */
    val uri: Uri

    /**
     * Total content length in bytes if known, else [LENGTH_UNSET]. A known length
     * enables a real scrubbable timeline and lets extractors do relative seeks.
     */
    val contentLength: Long

    /**
     * Opens a reader positioned at [position], able to deliver up to [length] bytes
     * (or [LENGTH_UNSET] for "until EOF"). MUST support an arbitrary [position] so that
     * seeking works — this is the seek primitive (plan §3.4). Implementations own
     * whatever caching, networking, or byte transformation they need; the SDK only
     * consumes the resulting [PlaybackReader].
     */
    fun openReader(position: Long, length: Long): PlaybackReader

    companion object {
        /**
         * Sentinel for an unknown length, mirroring Media3's `C.LENGTH_UNSET` value
         * (`-1`) without importing Media3 (this module is Media3-free — plan §2.2). The
         * `:player-core` adapter bridges these two constants; they are numerically equal
         * by contract, asserted in `player-core` tests.
         */
        const val LENGTH_UNSET: Long = -1L
    }
}

/**
 * Random-access byte reader. **One instance == one transfer** (plan §3.7). Not required
 * to be thread-safe: Media3 drives a given transfer from a single loading thread, so a
 * fresh reader per [PlaybackSource.openReader] means no shared mutable cursor.
 */
interface PlaybackReader {
    /**
     * Blocking read of up to [readLength] bytes into [buffer] starting at [offset].
     * Returns the number of bytes read, or `-1` at end of input. May legally return
     * fewer bytes than requested (a partial read) without signalling EOF.
     */
    fun read(buffer: ByteArray, offset: Int, readLength: Int): Int

    /**
     * Releases the reader's resources deterministically. May be called after a partial
     * read (e.g. on seek or stop). After [close], [read] must not be called again.
     */
    fun close()
}

/**
 * Optional display metadata surfaced to UI/notifications (plan §3.2). Purely
 * descriptive — it never affects how bytes are read.
 */
data class PlaybackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
    val artworkUri: Uri? = null,
)
