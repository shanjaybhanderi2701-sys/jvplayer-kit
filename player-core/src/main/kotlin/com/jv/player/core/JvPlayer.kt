@file:OptIn(UnstableApi::class)

package com.jv.player.core

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.jv.player.api.PlaybackSource

/**
 * Engine facade for `:player-core` (APP-583 plan §3.3): a thin, UI-free controller that
 * plays any [PlaybackSource] through the [PlaybackSourceDataSource][com.jv.player.core.source.PlaybackSourceDataSource]
 * seam. Wave 1 scope — playback lifecycle + error surface, no controls/gestures (those
 * are `:player-ui`, Wave 2).
 *
 * Each [setSource] binds a fresh `DataSource.Factory` to that one source (plan §3.3) and
 * hands it to `DefaultMediaSourceFactory`, so a single [JvPlayer] can play successive
 * sources. Built with [SeekParameters.EXACT] so a committed seek lands on the exact
 * frame rather than snapping to the nearest keyframe (plan §5.1/§5.3).
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
     * @param playWhenReady start as soon as buffering allows (default true).
     */
    fun setSource(source: PlaybackSource, playWhenReady: Boolean = true) {
        val dataSourceFactory = com.jv.player.core.source.PlaybackSourceDataSourceFactory(source)
        val mediaSource = DefaultMediaSourceFactory(dataSourceFactory)
            .createMediaSource(source.toMediaItem())
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.playWhenReady = playWhenReady
        exoPlayer.prepare()
    }

    fun play() = exoPlayer.play()

    fun pause() = exoPlayer.pause()

    /** Seeks to [positionMs]. Flows through the seam as an `open()` at a new byte offset (plan §3.4). */
    fun seekTo(positionMs: Long) = exoPlayer.seekTo(positionMs)

    /**
     * Registers a Media3 [Player.Listener]. This is the error/lifecycle surface:
     * [Player.Listener.onPlayerError] (a [PlaybackException]) and
     * [Player.Listener.onPlaybackStateChanged] are delivered here.
     */
    fun addListener(listener: Player.Listener) = exoPlayer.addListener(listener)

    fun removeListener(listener: Player.Listener) = exoPlayer.removeListener(listener)

    /** Releases the player and all engine resources deterministically. Idempotent per ExoPlayer. */
    fun release() = exoPlayer.release()

    companion object {
        /** Builds a Wave-1 engine bound to [context], seeking with frame-exact parameters. */
        fun create(context: Context): JvPlayer {
            val exoPlayer = ExoPlayer.Builder(context)
                .setSeekParameters(SeekParameters.EXACT)
                .build()
            return JvPlayer(exoPlayer)
        }
    }
}
