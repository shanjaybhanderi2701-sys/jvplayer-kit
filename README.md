# jvplayer-kit

A reusable, storage-agnostic Android video/audio player SDK built on **Media3 / ExoPlayer**.
Consumed by CalcVault and JGallery; replaces the old low-confidence `player-kit`.

> **Status:** Wave 4 (APP-589) — hardened & consumer-ready. `1.0.0` API freeze. Audio-only path,
> audio focus, background/foreground pause, orientation, error/retry, and the resume-position API
> are in. **Integration:** see [`docs/integration.md`](docs/integration.md) (with a copy-paste
> sample) and [`docs/api-stability.md`](docs/api-stability.md) (frozen `:player-api` contract).

## ⚖️ Legal boundary (clean-room — binding)

NextPlayer **and** `nextlib` are **GPL v3**. This project studies NextPlayer's *observable UX only*
(e.g. "seek commits on release") and never copies, adapts, transcribes, or links any NextPlayer /
`nextlib` source, structure, or FFmpeg build. **No `nextlib` dependency.** Decoding path is
**Media3/ExoPlayer + platform `MediaCodec` only**. **Encryption never enters the SDK** — all cipher
logic lives in consuming apps behind the `PlaybackSource` seam. GPL contamination would force
CalcVault/JGallery open-source. Every module owner acknowledges this in writing before code lands.

## Modules (plan APP-583 §2)

| Module | Purpose | Depends on |
|---|---|---|
| `:player-api` | Pure contract (`PlaybackSource`, …). **No Media3, no Compose.** | Android SDK only |
| `:player-core` | ExoPlayer engine + `DataSource` adapter. **No UI.** | `:player-api`, Media3 |
| `:player-ui` | Player surface, controls, gestures. | `:player-core`, Media3 UI, Compose |
| `:demo` | Internal sample app + on-device/instrumentation harness. | `:player-ui` |
| `:architecture-tests` | JVM unit test enforcing the invariants below. | JUnit |

### Enforced invariants (`:architecture-tests`, plan §2.3 / §0)
- `:player-core` never depends on `:player-ui`.
- `:player-api` has no Media3/Compose dependency and no inter-module deps.
- No SDK module source references `encrypt` / `vault` / `cipher` or crypto-key types.

## Toolchain
- **Media3 pinned to `1.5.1`** everywhere via `gradle/libs.versions.toml` (no Media3 BOM exists —
  mixed versions crash at runtime). Bump deliberately, re-verify on-device each bump.
- `minSdk 24`, `compileSdk 35`, `targetSdk 35`; Kotlin 2.0.21; AGP 8.7.3; JDK 17.
- Namespaces: `com.jv.player.api` / `.core` / `.ui`.

## Build & verify
```bash
./gradlew check          # ktlint + detekt + all unit tests (incl. architecture test) + Android lint
./gradlew assembleDebug  # build debug APK/AARs
./gradlew :demo:installDebug          # install the empty demo shell on a connected device
./gradlew :demo:connectedDebugAndroidTest   # emulator/device instrumentation smoke
```

### Consume the SDK
```kotlin
dependencies {
    implementation("com.jv.player:player-ui:1.0.0")   // full Compose surface (re-exports core + api)
    // implementation("com.jv.player:player-core:1.0.0") // engine only
    // implementation("com.jv.player:player-api:1.0.0")  // author a PlaybackSource only
}
```
Full walkthrough + sample snippet: [`docs/integration.md`](docs/integration.md). Supported media
matrix (H.264/H.265 · mp4/mkv): integration guide §6.

### Publish (internal Maven repo)
```bash
./gradlew publishAllPublicationsToInternalRepository   # → build/internal-maven-repo (credentials stubbed)
```
All three artifacts publish at the shared `jvplayer.version` (currently `1.0.0`) with sources JARs.

CI (`.github/workflows/ci.yml`) runs build + unit tests + `:demo` emulator smoke + lint on every push/PR.
