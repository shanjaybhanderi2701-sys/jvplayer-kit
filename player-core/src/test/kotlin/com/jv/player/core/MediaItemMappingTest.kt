package com.jv.player.core

import android.net.Uri
import androidx.media3.common.MimeTypes
import com.jv.player.api.PlaybackMetadata
import com.jv.player.api.PlaybackReader
import com.jv.player.api.PlaybackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [toMediaItem] (APP-583 plan §3.5) covering the W4 edge-codec/container matrix
 * (§7.2: H.264/H.265 in mp4/mkv). The mapping's job is to hand Media3 an authoritative container
 * hint over the opaque `jvsource://` scheme (the URI-suffix sniffer can't read a private scheme),
 * and to keep [PlaybackSource.id] as the `mediaId` so resume/notification tags stay stable.
 *
 * Runs on the JVM via Robolectric because [MediaItem] building needs a real `android.net.Uri`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaItemMappingTest {
    @Test
    fun `mp4 container hint is carried onto the MediaItem`() {
        // H.264/mp4 — the "short" and "medium" clips in the §7.2 matrix.
        val item = source(mimeType = MimeTypes.VIDEO_MP4).toMediaItem()
        assertEquals(MimeTypes.VIDEO_MP4, item.localConfiguration?.mimeType)
    }

    @Test
    fun `matroska container hint is carried onto the MediaItem`() {
        // H.265/mkv — the "short-hevc" and "long" clips in the §7.2 matrix.
        val item = source(mimeType = MimeTypes.VIDEO_MATROSKA).toMediaItem()
        assertEquals(MimeTypes.VIDEO_MATROSKA, item.localConfiguration?.mimeType)
    }

    @Test
    fun `null mime leaves the item hint-free so extractors sniff from bytes`() {
        val item = source(mimeType = null).toMediaItem()
        assertNull(
            "a null container hint must fall through to extractor byte-sniffing (§3.5)",
            item.localConfiguration?.mimeType,
        )
    }

    @Test
    fun `identity and uri survive the mapping`() {
        val item = source(id = "stable-id", mimeType = MimeTypes.VIDEO_MP4).toMediaItem()
        assertEquals("stable-id", item.mediaId)
        assertEquals(Uri.parse("jvsource://stable-id"), item.localConfiguration?.uri)
    }

    private fun source(id: String = "id", mimeType: String?): PlaybackSource =
        object : PlaybackSource {
            override val id: String = id
            override val metadata = PlaybackMetadata(title = "clip")
            override val mimeType: String? = mimeType
            override val uri: Uri = Uri.parse("jvsource://$id")
            override val contentLength: Long = 1_024L

            override fun openReader(position: Long, length: Long): PlaybackReader =
                error("not needed for mapping tests")
        }
}
