package com.jv.player.core

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.video.VideoFrameMetadataListener
import com.jv.player.api.PlaybackSource

/**
 * Engine facade for `:player-core` (APP-583 plan §3.3): a thin, UI-free controller that
 * plays any [PlaybackSource] through the [PlaybackSourceDataSource][com.jv.player.core.source.PlaybackSourceDataSource]
 * seam. This is the engine-only consumer surface (headless audio, custom UI); `:player-ui`
 * layers controls/gestures on top (plan §2.2).
 *
 * Each [setSource] binds a fresh `DataSource.Factory` to that one source (plan §3.3) and
 * hands it to `DefaultMediaSourceFactory`, so a single [JvPlayer] can play successive
 * sources. Built with [SeekParameters.EXACT] so a committed seek lands on the exact
 * frame rather than snapping to the nearest keyframe (plan §5.1/§5.3).
 *
 * **W4 hardening (APP-589):**
 *  - **Audio focus** — the engine requests/abandons audio focus itself and ducks/pauses on
 *    interruptions ([create]); it also pauses when audio "becomes noisy" (headphones
 *    unplugged). This is Media3's built-in handling — no custom `AudioManager` code.
 *  - **Resume position** — [setSource] takes a `startPositionMs`; hosts persist
 *    [currentPositionMs] keyed by [PlaybackSource.id] and pass it back to resume where the
 *    user left off. Works for audio-only and video sources alike.
 *  - **Error / retry** — playback errors arrive via [Player.Listener.onPlayerError]; [retry]
 *    re-prepares from the failed position so hosts can offer a one-tap retry.
 *
 * Threading: create/use/[release] on the application main thread, per ExoPlayer's
 * contract. [player] is exposed so a render surface (`PlayerView`/`SurfaceView`) can be
 * attached by UI/host code.
 */
class JvPlayer private constructor(
    private val exoPlayer: ExoPlayer,
) {
    /** The underlying Media3 [Player], for attaching a render surface or reading state. */
    val player: Player get() = exoPlayer

    /** Current playback position in ms. */
    val currentPositionMs: Long get() = exoPlayer.currentPosition

    /** Duration in ms, or [androidx.media3.common.C.TIME_UNSET] if unknown. */
    val durationMs: Long get() = exoPlayer.duration

    /**
     * Binds [source] through the seam and prepares playback. Replaces any current item.
     *
     * @param playWhenReady start as soon as buffering allows (default true).
     * @param startPositionMs initial playback position in ms — the **resume-position** entry
     *   point (plan §6 W4). Pass a value a host saved from [currentPositionMs] to resume where
     *   the user left off; `0L` (default) starts from the beginning. Applied as ExoPlayer's
     *   initial seek before [prepare], so the very first frame decoded is at the resume point
     *   (no visible jump). Clamped to `>= 0`; a value past the end lands at the end.
     */
    fun setSource(source: PlaybackSource, playWhenReady: Boolean = true, startPositionMs: Long = 0L) {
        val dataSourceFactory = com.jv.player.core.source.PlaybackSourceDataSourceFactory(source)
        val mediaSource = DefaultMediaSourceFactory(dataSourceFactory)
            .createMediaSource(source.toMediaItem())
        exoPlayer.setMediaSource(mediaSource, startPositionMs.coerceAtLeast(0L))
        exoPlayer.playWhenReady = playWhenReady
        exoPlayer.prepare()
    }

    fun play() = exoPlayer.play()

    fun pause() = exoPlayer.pause()

    /** Seeks to [positionMs]. Flows through the seam as an `open()` at a new byte offset (plan §3.4). */
    fun seekTo(positionMs: Long) = exoPlayer.seekTo(positionMs)

    /**
     * Re-prepares the current source after a playback error (plan §6 W4 error/retry UX). ExoPlayer
     * retains its position/media across an error, so this resumes from where playback failed —
     * the one-tap "Retry" a host offers when [Player.Listener.onPlayerError] fires. A no-op if there
     * is nothing to prepare.
     */
    fun retry() = exoPlayer.prepare()

    /**
     * Registers a Media3 [Player.Listener]. This is the error/lifecycle surface:
     * [Player.Listener.onPlayerError] (a [PlaybackException]) and
     * [Player.Listener.onPlaybackStateChanged] are delivered here.
     */
    fun addListener(listener: Player.Listener) = exoPlayer.addListener(listener)

    fun removeListener(listener: Player.Listener) = exoPlayer.removeListener(listener)

    private var frameMetadataListener: VideoFrameMetadataListener? = null

    /**
     * Sets (or clears, with `null`) a per-frame render callback. It fires each time the engine
     * pushes a frame to the surface, delivering that frame's `presentationTimeUs` — the signal
     * that proves the correct frame painted after a seek even while paused (plan §5.3). Pairs with
     * [Player.Listener.onRenderedFirstFrame].
     *
     * The Media3 `VideoFrameMetadataListener` type is wrapped here so callers (`:player-ui`) need
     * no `media3-exoplayer` dependency — they deal only in a `Long` presentation time, consistent
     * with the storage-agnostic, engine-free UI seam (plan §3.3/§10).
     */
    fun setOnVideoFrameRendered(onFrameRendered: ((presentationTimeUs: Long) -> Unit)?) {
        frameMetadataListener?.let(exoPlayer::clearVideoFrameMetadataListener)
        frameMetadataListener = onFrameRendered?.let { callback ->
            VideoFrameMetadataListener { presentationTimeUs, _, _, _ -> callback(presentationTimeUs) }
        }
        frameMetadataListener?.let(exoPlayer::setVideoFrameMetadataListener)
    }

    /** Releases the player and all engine resources deterministically. Idempotent per ExoPlayer. */
    fun release() = exoPlayer.release()

    companion object {
        /**
         * Builds an engine bound to [context], seeking with frame-exact parameters and with
         * audio focus fully handled by the engine (plan §6 W4):
         *  - [AudioAttributes] declare media/movie usage and, with `handleAudioFocus = true`,
         *    let ExoPlayer request focus on play and duck/pause on transient loss or a permanent
         *    grab by another app — restoring volume/playback when focus returns.
         *  - `setHandleAudioBecomingNoisy(true)` pauses when the audio output becomes noisy
         *    (e.g. headphones unplugged), matching platform media conventions.
         *
         * Both are Media3 built-ins — no custom `AudioManager` wiring, so nothing GPL-derived
         * enters the decode/focus path (plan §0).
         */
        fun create(context: Context): JvPlayer {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            val handleAudioFocus = true // engine requests/abandons focus; ducks/pauses on loss.
            val exoPlayer = ExoPlayer.Builder(context)
                .setSeekParameters(SeekParameters.EXACT)
                .setAudioAttributes(audioAttributes, handleAudioFocus)
                .setHandleAudioBecomingNoisy(true)
                .build()
            return JvPlayer(exoPlayer)
        }
    }
}
