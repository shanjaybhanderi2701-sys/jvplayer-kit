package com.jv.player.ui

import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.jv.player.core.JvPlayer
import kotlinx.coroutines.delay

/** Coarse playback phase the control surface reacts to (design §10 `playbackState`). */
internal enum class PlaybackPhase { Idle, Buffering, Ready, Ended }

/**
 * The design-facing controller façade the Compose UI binds to (APP-584 §10, plan §3.3).
 *
 * The UI depends on **only** this interface — never on Media3, the [JvPlayer] engine, or a
 * [com.jv.player.api.PlaybackSource]. That is what keeps the surface encryption-agnostic:
 * the scrubber deals only in `Long` positions, and no path/URI is ever reachable from here
 * (§10 encryption-agnostic guarantee). State reads are Compose-observable (snapshot state),
 * so reading them inside a composable subscribes it to changes.
 *
 * Wave 2 exposes the control hooks the usable surface needs (play/pause, seek, resize,
 * position/duration/buffered, video presence & aspect). The post-seek `renderedFirstFrame`
 * signal and capability flags (§10) land with their consumers in later waves.
 */
@Stable
internal interface PlayerController {
    val isPlaying: Boolean
    val phase: PlaybackPhase
    val positionMs: Long
    val durationMs: Long
    val bufferedPositionMs: Long

    /** True once a video track has a known size; drives Fit/Fill/Crop being meaningful. */
    val hasVideo: Boolean

    /** Pixel-corrected width/height ratio, or `0f` when unknown (drives letterboxing). */
    val videoAspectRatio: Float

    val resizeMode: ResizeMode
    val errorMessage: String?

    fun play()

    fun pause()

    fun playPause()

    /** Single committed seek (design §4 release-commit). Clamped to `[0, duration]`. */
    fun seekTo(positionMs: Long)

    /** Discrete relative skip (the ±10s transport buttons / double-tap, design §3.4). */
    fun seekBy(deltaMs: Long)

    fun setResizeMode(mode: ResizeMode)

    /** Advances the resize control one step (Fit -> Fill -> Crop -> Fit). */
    fun cycleResizeMode()

    /** Attaches the SurfaceView render target so the engine renders into it (§5.3). */
    fun attachVideoSurface(surfaceView: SurfaceView)

    /** Detaches [surfaceView] from the engine (on view teardown), preventing a dangling surface. */
    fun detachVideoSurface(surfaceView: SurfaceView)
}

/**
 * [PlayerController] over the Wave 1 [JvPlayer] engine. Holds Compose snapshot state that a
 * [Player.Listener] and a lightweight progress poll keep in sync with the engine.
 *
 * Lifecycle: [bind] registers the listener and seeds state; [unbind] removes it. This
 * controller does **not** own or release the engine — the host that created [JvPlayer]
 * releases it — so there is no double-release and no surface leak across recomposition.
 */
@Stable
// Façade implementing the wide PlayerController transport interface (play/pause/seek/resize/
// bind/unbind/surface) — the member count is the interface surface, not incidental complexity.
@Suppress("TooManyFunctions")
internal class MediaPlayerController(
    private val engine: JvPlayer,
) : PlayerController {
    private val player: Player get() = engine.player

    override var isPlaying: Boolean by mutableStateOf(false)
        private set
    override var phase: PlaybackPhase by mutableStateOf(PlaybackPhase.Idle)
        private set
    override var positionMs: Long by mutableStateOf(0L)
        private set
    override var durationMs: Long by mutableStateOf(-1L)
        private set
    override var bufferedPositionMs: Long by mutableStateOf(0L)
        private set
    override var hasVideo: Boolean by mutableStateOf(false)
        private set
    override var videoAspectRatio: Float by mutableStateOf(0f)
        private set

    // Backed by a private state field: the interface exposes a read-only `resizeMode` plus an
    // explicit `setResizeMode(mode)`, and a `var` override would generate a synthetic setter
    // with the same JVM signature as that method (a platform declaration clash).
    private var resizeModeState: ResizeMode by mutableStateOf(ResizeMode.Fit)
    override val resizeMode: ResizeMode get() = resizeModeState
    override var errorMessage: String? by mutableStateOf(null)
        private set

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@MediaPlayerController.isPlaying = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            phase = playbackState.toPhase()
            refreshProgress()
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            updateVideoSize(videoSize)
        }

        override fun onPlayerErrorChanged(error: PlaybackException?) {
            errorMessage = error?.errorCodeName
        }
    }

    fun bind() {
        engine.addListener(listener)
        // Seed from current engine state so first composition reflects reality, not defaults.
        isPlaying = player.isPlaying
        phase = player.playbackState.toPhase()
        updateVideoSize(player.videoSize)
        errorMessage = player.playerError?.errorCodeName
        refreshProgress()
    }

    fun unbind() {
        engine.removeListener(listener)
    }

    /** Polled from the surface (position/buffered/duration are not push events in Media3). */
    fun refreshProgress() {
        positionMs = player.currentPosition.coerceAtLeast(0L)
        bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
        val duration = player.duration
        durationMs = if (duration == C.TIME_UNSET) -1L else duration
    }

    override fun play() = player.play()

    override fun pause() = player.pause()

    override fun playPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    override fun seekTo(positionMs: Long) {
        val duration = durationMs
        val target = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        engine.seekTo(target)
        this.positionMs = target
    }

    override fun seekBy(deltaMs: Long) = seekTo(player.currentPosition + deltaMs)

    override fun setResizeMode(mode: ResizeMode) {
        resizeModeState = mode
    }

    override fun cycleResizeMode() {
        resizeModeState = resizeModeState.next()
    }

    override fun attachVideoSurface(surfaceView: SurfaceView) = player.setVideoSurfaceView(surfaceView)

    override fun detachVideoSurface(surfaceView: SurfaceView) = player.clearVideoSurfaceView(surfaceView)

    private fun updateVideoSize(videoSize: VideoSize) {
        val known = videoSize.width > 0 && videoSize.height > 0
        hasVideo = known
        videoAspectRatio = if (known) {
            videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
        } else {
            0f
        }
    }

    private fun Int.toPhase(): PlaybackPhase =
        when (this) {
            Player.STATE_BUFFERING -> PlaybackPhase.Buffering
            Player.STATE_READY -> PlaybackPhase.Ready
            Player.STATE_ENDED -> PlaybackPhase.Ended
            else -> PlaybackPhase.Idle
        }
}

/**
 * Builds and binds a [PlayerController] over [engine] for the enclosing composition.
 *
 * The listener is registered/removed with the composition ([DisposableEffect]) and a
 * progress poll drives the time row + seekbar thumb while the composable is on screen —
 * both scoped to the UI, so nothing keeps running (or leaks a listener) once the player
 * leaves the tree. The [engine] itself is owned/released by the caller.
 */
@Composable
internal fun rememberPlayerController(engine: JvPlayer): PlayerController {
    val controller = remember(engine) { MediaPlayerController(engine) }
    DisposableEffect(controller) {
        controller.bind()
        onDispose { controller.unbind() }
    }
    LaunchedEffect(controller) {
        while (true) {
            controller.refreshProgress()
            delay(PlayerTokens.PROGRESS_POLL_MS)
        }
    }
    return controller
}

/** Convenience for callers that want a plain [State]-free read of "is a real duration known". */
internal val PlayerController.hasKnownDuration: Boolean get() = durationMs > 0
