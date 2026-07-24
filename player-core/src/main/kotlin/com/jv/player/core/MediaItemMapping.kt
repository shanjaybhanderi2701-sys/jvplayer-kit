package com.jv.player.core

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.jv.player.api.PlaybackSource

/**
 * Maps a [PlaybackSource] to a Media3 [MediaItem] (APP-583 plan §3.5).
 *
 * Content-type detection over an opaque private-scheme URI (`jvsource://…`) works two
 * ways, in order of preference:
 *  1. **Explicit MIME** ([PlaybackSource.mimeType]) set on the item — authoritative, and
 *     avoids ambiguous sniffs on e.g. fragmented mp4. Always prefer this when the app
 *     knows the container.
 *  2. **Extractor sniffing fallback** — when `mimeType` is null, `DefaultExtractorsFactory`
 *     sniffs from the actual bytes our `DataSource` returns (magic-byte sniffers), which
 *     works over an opaque scheme because sniffing reads bytes, not the path.
 *
 * [PlaybackSource.id] becomes the `mediaId` so resume/notification tags stay stable.
 */
internal fun PlaybackSource.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setUri(uri)
        .setMediaId(id)
        .apply { mimeType?.let { setMimeType(it) } }
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(metadata.title)
                .setArtist(metadata.artist)
                .build(),
        )
        .build()
