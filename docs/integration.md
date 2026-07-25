# jvplayer-kit — Integration Guide

**Audience:** an Android app team embedding the player (CalcVault, JGallery, or any host).
**SDK version:** `1.0.0` (first frozen `:player-api`; see [API stability](#api-stability--freeze)).
**Min/compile/target:** `minSdk 24`, `compileSdk 35`, `targetSdk 35`, Kotlin 2.0.x, JDK 17.

> ⚖️ **Clean-room boundary.** The decode path is **Media3/ExoPlayer + platform `MediaCodec` only**.
> No `nextlib`, no bundled FFmpeg, no GPL-derived source. Byte-transforming sources (e.g. an
> app-side decrypting source) live **entirely in your app** behind the `PlaybackSource` seam — the
> SDK never sees a key or a cipher. Keeping transforms out of the SDK is what keeps your app free of
> GPL contamination.

---

## 1. What you depend on

Three artifacts share one version and release together (never mix versions):

| Artifact | Take it when you… | Pulls in |
|---|---|---|
| `com.jv.player:player-api` | author a `PlaybackSource` and nothing else | Android SDK only — **no Media3, no Compose** |
| `com.jv.player:player-core` | want the engine (`JvPlayer`) with your own UI | `:player-api` + Media3 exoplayer/datasource/common |
| `com.jv.player:player-ui` | want the full Compose control surface (`PlayerSurface`) | `:player-core` + Media3 UI + Compose |

`:player-ui` re-exports `:player-core` and `:player-api` as `api`, so depending on `:player-ui`
alone is enough for the full-UX path.

### Gradle

```kotlin
// settings.gradle.kts — point at the internal Maven repo the SDK publishes to
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://<your-internal-maven-host>/repository/android") }
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation("com.jv.player:player-ui:1.0.0")        // full Compose surface
    // — or, engine only —
    // implementation("com.jv.player:player-core:1.0.0")
    // — or, just to author a source in a lib module —
    // implementation("com.jv.player:player-api:1.0.0")
}
```

---

## 2. Author a `PlaybackSource` (the seam)

The engine is built against **byte streams and identity**, never files/paths/keys. You implement
one interface that answers *"what to play"* (`id`, `metadata`, `mimeType`, `uri`, `contentLength`)
and *"how to read arbitrary byte ranges of it"* (`openReader`). The make-or-break property is
**random-access reads**: ExoPlayer seeks by re-opening a reader at a new absolute byte offset.

```kotlin
import android.net.Uri
import com.jv.player.api.PlaybackMetadata
import com.jv.player.api.PlaybackReader
import com.jv.player.api.PlaybackSource
import java.io.File
import java.io.RandomAccessFile

/** Plain-file source — the trivial reference. A host with a custom byte layout swaps the reader. */
class FileSource(private val file: File) : PlaybackSource {
    override val id: String = file.absolutePath
    override val metadata = PlaybackMetadata(title = file.name)
    override val mimeType: String? = null            // null → extractors sniff from bytes
    override val uri: Uri = Uri.parse("jvsource://${Uri.encode(file.name)}")
    override val contentLength: Long = file.length()  // enables a scrubbable timeline

    override fun openReader(position: Long, length: Long): PlaybackReader =
        object : PlaybackReader {
            private val raf = RandomAccessFile(file, "r").apply { seek(position) }
            override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int =
                raf.read(buffer, offset, readLength)   // -1 at EOF, matching the contract
            override fun close() = raf.close()
        }
}
```

Rules that keep the seam working:
- **`openReader(position, length)` must honour an arbitrary `position`** — that *is* seeking.
- Return `-1` from `read` only at true end of input; a short read (fewer bytes than asked) is fine.
- One reader == one transfer; readers need not be thread-safe (the engine drives each from one
  loading thread). `close()` must release deterministically; it may be called mid-transfer on a seek.
- Set `mimeType` when you know the container (faster, avoids ambiguous sniffs). `null` lets Media3's
  extractors sniff from the bytes you return — which works even over the opaque `jvsource://` scheme.

---

## 3. Full-UX integration (Compose) — the sample snippet

This is the whole integration for a screen that plays one `PlaybackSource` with the default control
surface (play/pause, seek-on-release scrubber, ±10s, resize, pinch-zoom), resume, error/retry,
audio focus, and background pause. It is the snippet verified against a throwaway consumer app.

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.jv.player.api.PlaybackSource
import com.jv.player.core.JvPlayer
import com.jv.player.ui.PlayerSurface

/**
 * @param source     what to play (your PlaybackSource implementation)
 * @param resumeFrom persisted position for this source.id, or 0 to start from the beginning
 * @param onLeave    called with the latest position so the host can persist it (resume next time)
 * @param onBack     back affordance (top bar + system back)
 */
@Composable
fun VideoScreen(
    source: PlaybackSource,
    resumeFrom: Long,
    onLeave: (positionMs: Long) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    // The host owns the engine lifecycle: create → setSource → release.
    val engine = remember(source.id) { JvPlayer.create(context) }

    DisposableEffect(engine) {
        engine.setSource(source, startPositionMs = resumeFrom)   // resume-position API
        onDispose {
            onLeave(engine.currentPositionMs)                    // persist for next time
            engine.release()
        }
    }

    PlayerSurface(
        engine = engine,
        title = source.metadata.title ?: "",
        onBack = onBack,
        modifier = Modifier,
        // pauseOnBackground defaults to true (correct for video). Set false only if you drive
        // your own foreground service for background *audio*.
    )
}
```

That's it — the surface renders on a `SurfaceView`, binds its own controller over the engine, shows
an audio-only backdrop for sources with no video track, and shows a one-tap **Retry** overlay on a
playback error.

---

## 4. Engine-only integration (custom UI / headless audio)

Skip `:player-ui` and drive `JvPlayer` directly:

```kotlin
val engine = JvPlayer.create(context)
engine.addListener(object : Player.Listener {
    override fun onPlayerError(error: PlaybackException) { /* show your retry affordance */ }
})
engine.setSource(source, playWhenReady = true, startPositionMs = 0L)
// engine.player is the Media3 Player — attach a SurfaceView with engine.player.setVideoSurfaceView(...)
engine.play(); engine.pause(); engine.seekTo(90_000L)
engine.retry()                       // re-prepare after an error, from the failed position
val resumeKey = engine.currentPositionMs   // persist keyed by source.id
engine.release()
```

`JvPlayer.create` builds the engine with frame-exact seeks (`SeekParameters.EXACT`) and full
**audio focus** handling (requests/abandons focus, ducks/pauses on interruption, pauses on
headphones-unplugged) — no extra wiring on your side.

---

## 5. Cross-cutting behaviour

- **Resume position.** Persist `JvPlayer.currentPositionMs` keyed by `PlaybackSource.id`; pass it
  back as `setSource(..., startPositionMs = …)`. Applied before prepare, so the first decoded frame
  is at the resume point — no visible jump. Works for audio-only and video alike.
- **Error / retry.** Errors arrive via `Player.Listener.onPlayerError` (engine) or
  `PlayerController` (`PlayerSurface` renders a Retry overlay automatically). `JvPlayer.retry()`
  re-prepares from the failed position.
- **Audio-only.** A source with no video track just works; `PlayerSurface` shows an audio backdrop
  over the empty surface.
- **Background / foreground.** `PlayerSurface(pauseOnBackground = true)` (default) pauses on
  `ON_STOP`. Audio focus covers interruptions while foregrounded. True background *audio* playback
  (foreground service + `MediaSession` notification) is a **host responsibility** — deliberately out
  of SDK scope so the SDK stays a pure, dependency-light library.
- **Orientation.** The surface is orientation-agnostic (Compose, fills its bounds). To avoid
  tearing down the engine (and losing position / flashing black) on rotation, add
  `android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|layoutDirection"`
  to the hosting `Activity`. Alternatively, rely on the resume-position API to restore across
  recreation.

---

## 6. Supported formats (media matrix §7.2)

Decoding is Media3 + platform `MediaCodec`, so support tracks the device's hardware/software codecs.
Verified targets:

| Codec | Containers | Notes |
|---|---|---|
| H.264 (AVC) | mp4, mkv | universal |
| H.265 (HEVC) | mp4, mkv | hardware decode varies by SoC — verify on device |

`DefaultMediaSourceFactory` + `DefaultExtractorsFactory` also handle webm and other Media3-supported
containers; set `PlaybackSource.mimeType` for opaque URIs, or leave it `null` to sniff from bytes.

---

## API stability & freeze

`:player-api` is the **frozen consumer contract** as of `1.0.0`. Its types — `PlaybackSource`,
`PlaybackReader`, `PlaybackMetadata`, and the `PlaybackSource.LENGTH_UNSET` sentinel — will not
change shape without a **major** version bump, because a change forces every consumer to bump major.
The engine (`:player-core`) and UI (`:player-ui`) evolve additively under SemVer minor/patch. See
[api-stability.md](api-stability.md) for the exact frozen surface and the compatibility policy.
