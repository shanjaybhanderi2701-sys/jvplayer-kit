# jvplayer-kit — API Stability & Freeze Policy

**As of `1.0.0` (Wave 4, APP-589).**

All three artifacts (`player-api`, `player-core`, `player-ui`) share one version and release
together. Versioning is **SemVer**.

## Why `:player-api` is special

`:player-api` is the surface an app compiles against to author a `PlaybackSource`. Any breaking
change to it forces **every consumer to a new major version**, so it is **frozen**: no shape change
without a major bump. It is intentionally tiny and has **no Media3 and no Compose dependency**, so it
never churns with those libraries.

### Frozen `:player-api` surface (1.0.0)

```kotlin
package com.jv.player.api

interface PlaybackSource {
    val id: String
    val metadata: PlaybackMetadata
    val mimeType: String?
    val uri: android.net.Uri
    val contentLength: Long
    fun openReader(position: Long, length: Long): PlaybackReader
    companion object { const val LENGTH_UNSET: Long = -1L }
}

interface PlaybackReader {
    fun read(buffer: ByteArray, offset: Int, readLength: Int): Int
    fun close()
}

data class PlaybackMetadata(
    val title: String? = null,
    val artist: String? = null,
    val durationMs: Long? = null,
    val artworkUri: android.net.Uri? = null,
)
```

The clean-room enforcement test (`:architecture-tests`) fails the build if `:player-api` ever gains a
Media3/Compose dependency or an inter-module dependency, so the "tiny and stable" property is
mechanically guarded, not just documented.

## Compatibility policy

| Change | Version bump |
|---|---|
| Remove/rename a `:player-api` member, or change a signature/semantics incompatibly | **major** |
| Add a member to `:player-api` (source-compatible for callers, but a new abstract member breaks *implementers*) | **major** — implementers are the consumers here, so treat additions as breaking |
| Add a `:player-core` / `:player-ui` capability additively (new function, new defaulted param) | **minor** |
| Bug fix, internal change, doc | **patch** |

> Note on additive `:player-api` changes: because consumers **implement** `PlaybackSource`, adding an
> abstract member is a breaking change for them. Prefer default methods or a new sibling interface if
> the surface ever must grow; otherwise it is a major bump.

## Engine / UI surface (`:player-core`, `:player-ui`) — stable, evolves additively

- `:player-core` — `JvPlayer` (`create`, `setSource(source, playWhenReady, startPositionMs)`,
  `play`/`pause`/`seekTo`/`retry`/`release`, `currentPositionMs`/`durationMs`, `addListener`,
  `setOnVideoFrameRendered`, `player`).
- `:player-ui` — `PlayerSurface(engine, title, onBack, modifier, pauseOnBackground)`.

New W4 members (`startPositionMs`, `retry`, `pauseOnBackground`) were added source-compatibly
(defaulted params / new functions), so this is `1.0.0`'s baseline going forward.

## Media3 pin

Media3 is pinned at `1.5.1` (no Media3 BOM exists; mixed versions crash at runtime). Bumping the pin
is a deliberate, on-device-reverified change, released as at least a **minor** bump.
