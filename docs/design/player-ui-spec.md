# jvplayer-kit — Player UI Design Spec

**Issue:** APP-584 · **Parent:** APP-582 (kickoff) · **Sibling:** APP-583 (clean-room architecture / `PlaybackSource` seam)
**Owner (design):** Principal Product Designer · **Implements against:** Media3/ExoPlayer SDK + `:demo` app
**Status:** v1 — ready for engineering + Architect review · **Fidelity:** buildable spec (flows + annotated states + gesture map)

> **How to read this doc.** Layouts are ASCII redlines with measured tokens, not decorative sketches. Every state, gesture, and transition below is normative — where a number appears (dp, ms, %), it is a build target, not a suggestion. Screens map to the Compose files they become (see §12). Every non-obvious decision carries a **▷ Why** annotation tied to a job-to-be-done or a *specific prior-player-kit failure* we are contractually not repeating.

---

## 0. ⚖️ Legal boundary — acknowledged (design)

I acknowledge the boundary in the kickoff brief and APP-584: **NextPlayer and `nextlib` are GPL v3.** This spec was authored **from first principles** (Media3/ExoPlayer interaction docs + our own prior player-kit history on this board). It references only NextPlayer's **observable UX** (seek-commits-on-release, gesture zones, pinch-zoom feel) as a quality bar. **No NextPlayer asset, layout, drawable, measurement, or source was copied, traced, or adapted.** All dimensions, component names, motion curves, and copy here are original. Consumers (CalcVault/JGallery) theme on top; the SDK ships neutral.

---

## 1. Design goals & the bar

| Goal | What "passing" looks like |
|------|---------------------------|
| **Rock-solid from first integration** | No seekbar thrash, no frozen post-seek frame, no pinch jump — the three mechanics that sank the old player-kit are designed out at the interaction level, not patched later. |
| **NextPlayer-quality feel, original design** | Immersive, gesture-first, chrome-that-gets-out-of-the-way. Our own visual language and layout. |
| **Encryption-agnostic** | The UI binds to a `PlayerController` façade over the `PlaybackSource` seam (APP-583). Nothing in the UI knows about vaults, keys, files, or offsets. |
| **Themeable, unopinionated skin** | All color/typography flow from tokens a consumer can override. Default theme is a neutral dark player. |
| **One surface, every state** | Loading, buffering, error, end-of-media, locked, audio-only, portrait, landscape — all specified, no dead ends. |

**Design principles**
1. **The video is the UI.** Chrome is transient overlay; default resting state is *no chrome*.
2. **Gestures are the primary control; the visible controls are the discoverable fallback.** Both must always agree on state.
3. **Never block the main thread for feedback.** Every gesture gives instant *local* visual feedback; the expensive work (seek/decode) is deferred and de-duplicated (see §4).
4. **One intent → one commit.** Dragging previews; releasing commits. This single rule kills the class of bugs the old player fought five times.

---

## 2. Design tokens (player-local, consumer-overridable)

Namespaced `player.*` so a consumer's app theme can override without collision. Defaults target a neutral dark player. These are **semantic** tokens; map to `core-ui` primitives if/when the SDK adopts a shared token module.

### Color (default dark skin)
| Token | Default | Use |
|-------|---------|-----|
| `player.scrim.controls` | `#000000 @ 55%` top+bottom gradient | chrome legibility over any frame |
| `player.scrim.dim` | `#000000 @ 40%` flat | locked / long-press dim |
| `player.surface.sheet` | `#111417 @ 96%` | audio-only backdrop, menus |
| `player.accent` | `#4C8DFF` (overridable) | active scrubber fill, selected speed |
| `player.on.primary` | `#FFFFFF @ 92%` | icons, primary time label |
| `player.on.secondary` | `#FFFFFF @ 60%` | duration, secondary labels |
| `player.track.inactive` | `#FFFFFF @ 24%` | seekbar remaining track |
| `player.track.buffer` | `#FFFFFF @ 40%` | buffered-ahead fill |
| `player.error` | `#FF6B6B` | error glyph/text |
| `player.hud.bg` | `#000000 @ 70%` | brightness/volume/zoom HUD pill |

### Dimension / motion
| Token | Value | Note |
|-------|-------|------|
| `player.touch.min` | **48dp** | every interactive target's minimum hit box |
| `player.seek.hitHeight` | **48dp** | seekbar *invisible* hit band (▷ §4) |
| `player.seek.trackHeight` | 3dp idle → 5dp dragging | *drawn* track only; **must not shift row layout** |
| `player.seek.thumb` | 6dp idle → 16dp dragging (visual) | drawn thumb; hit target is decoupled |
| `player.seek.grabTolerance` | 24dp along track | press this far off-thumb still latches |
| `player.chrome.fade` | 220ms ease-out | show/hide controls |
| `player.chrome.autohide` | 3000ms after last interaction | while playing only |
| `player.hud.fade` | 120ms in / 500ms out | gesture HUDs |
| `player.doubleTap.window` | 280ms | double-tap seek detection |
| `player.seek.step` | 10s | double-tap increment (accumulates) |

**▷ Why decoupled `trackHeight`/`thumb` growth with zero row shift:** the old player-kit had a hard-won *±1px no-layout-shift invariant* (APP-418/APP-429). The track and thumb may grow *visually* on drag, but the seekbar **row height is fixed by the 48dp hit band**, so growth happens inside a fixed box and never reflows the time row.

---

## 3. The player surface — anatomy & control states

The player is one `Box` with a stack: **video surface → gesture layer → scrim → chrome**. Chrome has two states: **hidden** (resting) and **visible**. A single tap toggles between them.

### 3.1 Landscape, chrome VISIBLE (video)
```
┌───────────────────────────────────────────────────────────────┐
│ [<]  Title of the media file.mp4                    [🔒] [⋮]    │  ← top bar (in top scrim)
│                                                                 │
│                                                                 │
│                                                                 │
│                            ▶  (48dp)                            │  ← center transport cluster
│              [ ⟲10 ]      [ ▶/⏸ ]      [ 10⟳ ]                  │     (replay10 · play/pause · fwd10)
│                                                                 │
│                                                                 │
│                                                                 │
│  0:12:34 ●━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━ 1:47:02   │  ← time row + seekbar (bottom scrim)
│  [1.0×]                                        [CC]  [ ⤢ fit ]  │  ← speed · subtitles · resize/zoom mode
└───────────────────────────────────────────────────────────────┘
   ^left third: brightness zone   ^center: tap/double-tap   ^right third: volume zone
```

### 3.2 Landscape, chrome HIDDEN (resting / default while playing)
```
┌───────────────────────────────────────────────────────────────┐
│                                                                 │
│                                                                 │
│                       (pure video frame)                        │
│                     no chrome, no scrim                          │
│                                                                 │
└───────────────────────────────────────────────────────────────┘
```
**▷ Why hidden-by-default:** immersion is the JTBD for full-screen video. Chrome appears on tap, auto-hides after `player.chrome.autohide` while playing, and **stays visible while paused** (paused = the user is deciding; don't hide their controls).

### 3.3 Portrait (video, chrome visible)
Same control grammar, reflowed. Video letterboxes into the top; controls occupy a taller bottom band. Center transport cluster stays vertically centered on the *video*, not the screen.
```
┌─────────────────────────┐
│ [<] Title.mp4    [🔒][⋮] │
│ ┌─────────────────────┐ │
│ │                     │ │
│ │   video (16:9)      │ │
│ │      ⟲10  ▶  10⟳    │ │
│ │                     │ │
│ └─────────────────────┘ │
│                         │
│ 0:12 ●━━━━━━━━━━━ 1:47   │
│ [1.0×]        [CC] [⤢]   │
└─────────────────────────┘
```

### 3.4 Control inventory (every control, its hook, its states)
| Control | States | Bound to (`PlayerController`) |
|---------|--------|-------------------------------|
| Play / Pause (center) | play ▶ / pause ⏸ / (replaced by spinner while buffering) | `playWhenReady` toggle |
| Replay-10 / Forward-10 | enabled / disabled at bounds | `seekTo(pos ∓ 10s)` (commit immediately — discrete, not drag) |
| Seekbar | idle / dragging / buffering-ahead | preview local; commit on release (§4) |
| Time elapsed / duration | live / `--:--` while duration unknown | `currentPosition` / `duration` |
| Back `[<]` | always | consumer nav callback |
| Lock `[🔒]` | unlocked / locked (§7.5) | UI-only state |
| Overflow `[⋮]` | opens menu: speed, subtitle track, audio track, resize mode, loop | controller getters/setters |
| Speed `[1.0×]` | 0.5–2.0× | `setPlaybackSpeed` |
| Subtitles `[CC]` | off / track selected / none-available (hidden) | text-track selection |
| Resize `[⤢]` | Fit / Fill / Crop / Original (cycles; also set by pinch §5.1) | `resizeMode` |

**▷ Why replay/forward commit immediately but the seekbar defers:** a ±10s tap is a *discrete, cheap, idempotent* intent — one seek, done. The seekbar is a *continuous* gesture that would otherwise fire ~30 seeks/second. Different intent → different commit rule. This distinction is the heart of §4.

---

## 4. Seekbar / scrubber — the critical interaction (normative)

> This is the interaction that was **missed five times** in the old player-kit (APP-429). The design makes the intended behavior unambiguous so engineering builds it exactly once.

### 4.1 The one rule
**While the finger is down and moving → this is a PREVIEW. Nothing touches the player.**
**When the finger lifts → this is the COMMIT. Exactly one seek fires, off the main thread.**

### 4.2 State-by-state contract

**IDLE (not being touched)**
- Thumb sits at `currentPosition`; track fill = elapsed; a lighter fill shows buffered-ahead.
- Track 3dp, thumb 6dp.

**PRESS / LATCH (finger down inside the 48dp hit band)**
- A press anywhere within the **48dp-tall hit band** and within **24dp along the track of the thumb** latches the drag. A press farther along the track **jumps the thumb to the finger** and latches (jump-to-finger is allowed).
- On latch: thumb grows 6→16dp, track 3→5dp, a **preview time bubble** appears above the thumb. Row does **not** shift (§2 ▷).
- **▷ Why the fat-finger band:** APP-434 — visual thumb stays small (looks right) but the hit area is a large invisible 48dp×(track+24dp) region so an imprecise grab still latches. Decouple hit area from drawn size.

**DRAG (finger moving)**
- Update **only**: thumb X, track fill, and the preview bubble's timestamp. This is **pure UI state**.
- **Do NOT** call `seekTo`. **Do NOT** decrypt/read the source. **Do NOT** touch the player. Because nothing expensive runs, the thumb tracks the finger with **zero lag**.
- *(Optional, capability-gated — see §4.4)* if the source can cheaply provide keyframe thumbnails, show a preview thumbnail above the bubble. Default OFF; never blocks the drag.

**RELEASE (finger up / drag end / cancel)**
- Fire **exactly one** `seekTo(finalPosition)`, dispatched **off the main thread**.
- Preserve play/pause state across the seek (paused stays paused; playing stays playing).
- Thumb/track animate back to idle sizes; preview bubble fades (`player.hud.fade` out).
- Immediately enter the **post-seek frame protocol** (§6).

### 4.3 Gesture ownership (no parent steal)
The seekbar **owns its pointer stream exclusively**. A pointer that goes down inside the hit band belongs to the seekbar for its entire lifetime and **never** propagates to the surrounding surface gestures (volume/brightness swipe, chrome tap, pinch). Conversely, surface gestures never move the thumb.
**▷ Why:** APP-448/APP-418 — structural exclusive ownership is what stopped the "parent swipe steals the drag" thrash. Design mandates a single gesture arbiter with the seekbar at highest priority within its bounds.

### 4.4 What the seekbar must NEVER do (design guardrails → become tests)
- ❌ No `seekTo` during `pointer-move`. Only on `pointer-up`/`drag-end`.
- ❌ No per-frame decrypt/source read during drag.
- ❌ No layout shift on latch/release (±1px invariant).
- ❌ More than one seek per drag.
These four map 1:1 to the fail-then-pass instrumented tests APP-583 owns: *press→move×N→release asserts zero seeks during move, exactly one on release at the final position.*

### 4.5 Seekbar redline
```
        preview bubble (drag only)
            ┌────────┐
            │ 1:12:40│
            └───▲────┘
 0:12:34  ●━━━━━━━●······················  1:47:02
 elapsed  |fill  ^thumb    ^buffered  ^remaining   duration
          └─ accent ─┘     (track.buffer)  (track.inactive)

 hit band (invisible): 48dp tall, +24dp grab tolerance each side of thumb
```

---

## 5. Gesture map

The surface (excluding the seekbar's owned band and the top bar) is divided into gesture zones. One arbiter resolves them; the seekbar always wins inside its band.

```
┌───────────────────────────────────────────────────────────┐
│                        TOP BAR (buttons only)              │
├───────────────┬───────────────────────┬───────────────────┤
│               │                        │                   │
│  BRIGHTNESS   │      CENTER            │      VOLUME        │
│  vertical     │   • single tap: chrome │   vertical swipe  │
│  swipe        │   • double tap L: −10s │   = volume        │
│  = screen     │   • double tap R: +10s │                   │
│  brightness   │   • (center dbl: play) │                   │
│               │                        │                   │
│  left third   │      center third      │    right third    │
├───────────────┴───────────────────────┴───────────────────┤
│              SEEKBAR BAND (owns its pointers)              │
└───────────────────────────────────────────────────────────┘
        pinch-to-zoom is global (two fingers, anywhere on surface)
```

| Gesture | Zone | Action | Feedback |
|---------|------|--------|----------|
| Single tap | center third | toggle chrome | 220ms fade |
| Double tap | left third | −10s (accumulates within `doubleTap.window`) | left ripple + `«10s` HUD; counter grows on repeat (`«20s`, `«30s`) |
| Double tap | right third | +10s (accumulates) | right ripple + `10s»` HUD |
| Double tap | center | play/pause toggle | center ▶/⏸ flash |
| Vertical swipe | left third | brightness | vertical bar HUD + ☀ glyph (§5.2) |
| Vertical swipe | right third | volume | vertical bar HUD + 🔊 glyph; at 0 → muted glyph |
| Pinch (2 fingers) | anywhere | continuous zoom / resize mode (§5.1) | zoom-% HUD pill |
| Long-press | anywhere | *reserved* (e.g. temporary 2× speed) — v1: no-op, documented | — |

**▷ Why thirds and double-tap accumulation:** matches the observable, learned behavior users expect from mainstream players; accumulation (tap-tap-tap = −30s) prevents seek spam and lets §4's commit rule apply — the accumulated delta commits once when the tap window closes, as a single `seekTo`.

### 5.1 Continuous pinch-zoom
- Two-finger pinch scales the video surface **continuously and smoothly** between **Fit (1.0×)** and **Crop/Fill**. The scale tracks the pinch span in real time — **no snapping, no bounce, no reset-to-1 on release**, no mid-gesture jump.
- On release, the current scale **holds** (it does not spring back). Pinching back below the Fit threshold returns to letterboxed Fit.
- A small **zoom-% HUD pill** (`118%`) shows during the gesture and fades 500ms after release.
- Crossing meaningful thresholds updates the resize-mode label (`Fit → Fill → Crop`) so the `[⤢]` button and the pinch state always agree.
- **▷ Why "no jump, no residual release jump":** JGallery fought exactly this on its grid (APP-521 swim/jitter + residual release jump; APP-537 simplified to no scale/bounce). The player's pinch must be **continuous and anchored to the focal point**, accumulate scale across successive pinches (no reset between gestures), and never apply a correction on finger-up. This is one of the three CEO on-device gates.

### 5.2 Brightness / volume HUD
```
   ┌──┐
   │☀ │   brightness (left swipe) — app-window brightness, not system
   │▓▓│
   │▓▓│   vertical fill 0–100%, snaps to nearest 1%
   │░░│
   │░░│
   └──┘
```
- Brightness = **window attribute only** (never writes system brightness). Volume = stream volume; at 0 shows a muted glyph.
- HUD is centered in its zone, `player.hud.bg` pill, fades in 120ms / out 500ms after the swipe ends.
- Swipe is **relative** (delta from touch-down), not absolute — starting a swipe doesn't jump the value.

---

## 6. Post-seek behavior — correct frame, immediately

> The bug that surfaced *from* a working seek (APP-450): after seeking, audio resumed at the new position but the **video frame stayed frozen** on the pre-seek frame, across the 1min→3hr matrix, playing and paused. The design must guarantee the target frame appears.

**Design contract for the moment after §4's release commit:**
1. **Show a determinate "seeking" affordance, briefly.** A slim indeterminate progress line at the thumb + the transport spinner replace the play glyph **only if** the decode takes longer than **150ms** (below that, show nothing — instant seeks must feel instant). No full-screen black.
2. **The frame must update to the target position** — never leave the stale pre-seek frame. If the exact target has no decodable keyframe nearby, the player seeks to the **nearest preceding sync/keyframe** so a real frame renders immediately, then rolls forward. (Engineering realization: `SeekParameters.PREVIOUS_SYNC` + video-renderer flush — APP-450 — is the mechanism; the *design requirement* is simply "a correct, non-stale frame is visible within one refresh of the seek.")
3. **Paused seeks still render the destination frame.** Seeking while paused must repaint the surface to the new position (not just move audio). This is explicit because it's the exact failure mode we hit.
4. When the frame is up, remove the seeking affordance (`player.hud.fade` out). If it was playing, playback continues from there; if paused, it holds on the new frame.

**States a frame can be in after seek:** `settled` (frame shown, ≤150ms, no chrome) · `resolving` (>150ms, spinner + thumb progress) · `error` (decode failed → §7.2 inline, position preserved).

---

## 7. States (annotated)

### 7.1 Loading (initial open, before first frame)
```
┌───────────────────────────────┐
│ [<] Title.mp4                  │
│                                │
│            ◜◝                  │   centered spinner
│            ◟◞                  │   subtle, on dim surface
│         Loading…               │   (only if >400ms)
│                                │
│  --:-- ●━━━━━━━━━━━━━━━ --:--   │   seekbar disabled, times = --:--
└───────────────────────────────┘
```
- Duration unknown → `--:--` both ends, seekbar non-interactive (thumb hidden).
- Spinner appears only after **400ms** (avoid flash on fast opens).

### 7.2 Error (source/decode failure)
```
┌───────────────────────────────┐
│ [<] Title.mp4                  │
│                                │
│            ⚠                   │   player.error glyph
│    Can't play this video       │   plain, non-technical
│   [ Retry ]     [ Details ]    │   Retry re-prepares; Details = expandable
│                                │
└───────────────────────────────┘
```
- **Never a dead end.** Always offers **Retry** (re-prepare current position) and **Back**. `Details` reveals a short reason (e.g. "Unsupported format") — never a raw stack trace or a file path (encryption-agnostic: the UI has no path to leak).
- Error copy is user-side and calm. **▷ Why:** consumers are a vault and a gallery; a scary/technical error erodes trust.

### 7.3 Buffering (mid-playback stall)
- Center transport play glyph → spinner; controls stay interactive; last frame stays on screen (no black). Seekbar keeps its position; buffered-ahead fill communicates progress. Auto-resumes.

### 7.4 End of media
```
┌───────────────────────────────┐
│ [<] Title.mp4                  │
│                                │
│            ⟲                   │
│         Replay                 │   tap replays from 0:00
│   (chrome stays visible)       │
│                                │
│  1:47:02 ●━━━━━━━━━━━━━━━● 1:47 │   thumb at end
└───────────────────────────────┘
```
- On completion: chrome shows and **stays** (don't auto-hide at the end), center becomes **Replay**. If the consumer enabled loop, it restarts instead. No dead end.

### 7.5 Locked / immersive
```
┌───────────────────────────────┐
│                                │
│                                │
│              🔓                │   single small unlock affordance,
│         (tap to unlock)        │   auto-hides after 2s
│                                │
│         video plays on          │
└───────────────────────────────┘
```
- Lock hides **all** chrome and **disables all gestures** (no accidental seek/volume/brightness/pinch while in a pocket or handed to someone). Only a single unlock affordance responds; it auto-hides and reappears on tap. **▷ Why:** "hand my phone to someone / put it down mid-video" JTBD — accidental input is the enemy.

### 7.6 Audio-only mode (no video surface)
When the source has no video track (or is audio), the player renders an **audio layout** instead of a black surface.
```
┌───────────────────────────────┐
│ [<]                      [⋮]   │
│                                │
│        ┌──────────┐            │
│        │          │            │   large artwork / placeholder glyph
│        │  ♪ art   │            │   (metadata art if present, else token glyph)
│        └──────────┘            │
│        Track title             │
│        Artist / subtitle       │   from metadata; graceful if absent
│                                │
│   ⟲10     ▶/⏸ (64dp)    10⟳    │   bigger transport (no video to compete)
│                                │
│  0:34 ●━━━━━━━━━━━━━━━━━ 3:52   │   same seekbar contract (§4) verbatim
│  [1.0×]                        │
└───────────────────────────────┘
```
- **No gesture zones** (no brightness/volume-swipe/pinch/double-tap-seek over artwork — there's no video to zoom or immerse; volume uses hardware keys / system). Chrome is **always visible** (nothing to get out of the way of).
- Seekbar behaves **identically** to video (§4) — same release-commit contract; audio just doesn't have the §6 frame concern.
- **▷ Why reuse the exact seekbar:** one scrubber implementation, one set of tests, both modes. Consumers get audio playback (CalcVault already ships an in-vault audio player per APP-526) with zero new scrubber risk.

---

## 8. Orientation & layout behavior
- **Portrait ↔ landscape:** the player recomposes; **playback state, position, scale, and lock survive rotation** (no reload, no reseek). Rotation must not trigger §6.
- Landscape defaults to immersive (system bars hidden, edge-to-edge). Portrait keeps status bar unless the consumer opts into full immersive.
- Respect display cutouts/insets: chrome padding uses safe-area insets so buttons never sit under a notch or nav bar.
- **Fit / Fill / Crop / Original** apply in both orientations; pinch (§5.1) and the `[⤢]` button are the two paths to the same `resizeMode` state.

---

## 9. Motion & feedback summary
| Event | Motion |
|-------|--------|
| Chrome show/hide | 220ms ease-out fade + slight scrim gradient |
| Seek latch | thumb 6→16dp / track 3→5dp, 120ms, spring-free |
| Seek release | reverse, 120ms; then §6 |
| Double-tap seek | zone ripple from tap point, `«10s` HUD 500ms |
| Volume/brightness | vertical HUD, in 120ms / out 500ms |
| Pinch | zoom-% pill live, out 500ms; **no release spring** |
| Buffering | glyph→spinner crossfade 150ms |

All motion is **interruptible** — a new gesture cancels an in-flight animation cleanly (no queueing).

---

## 10. Alignment with `PlaybackSource` / architecture (APP-583)
The UI depends only on a **`PlayerController` façade** (proposed here; final shape owned by APP-583 + Android Architect). The façade wraps Media3 and consumes `PlaybackSource`; the UI never sees Media3 or the source directly.

**Control hooks the UI needs the façade to expose (design → architecture request):**
- State: `isPlaying`, `playbackState {idle,buffering,ready,ended}`, `currentPosition`, `duration`, `bufferedPosition`, `hasVideo` (drives §7.6), `videoSize`, `error`.
- Commands: `play()`, `pause()`, `seekTo(pos)` *(single, off-main-thread — §4)*, `setPlaybackSpeed`, `setResizeMode`, text/audio track selection.
- Post-seek: façade guarantees "correct non-stale frame after seek" (§6) — the UI only shows/hides the resolving affordance based on a `seeking`/`renderedFirstFrame` signal.
- **Capability flags** (so the UI degrades gracefully): `supportsPreviewThumbnails` (gates §4.4), `supportsAudioTrackSelection`, etc. Absent capability → the control is hidden, not disabled-and-confusing.

**Encryption-agnostic guarantees the design relies on:** no path/URI ever surfaces in UI (error copy included); the scrubber deals only in `Long` positions; preview thumbnails (if any) come through the same `PlaybackSource`, never a direct file read.

**Open coordination questions for Head of R&D / Android Architect (APP-583):**
1. Will the façade expose a `renderedFirstFrame`/`seeking` signal so the UI can implement the §6 150ms rule cleanly? (Design needs a first-frame-after-seek callback.)
2. Is `supportsPreviewThumbnails` in scope for v1, or defer scrubbing thumbnails to a later wave? (Design defaults it OFF — §4.4.)
3. Confirm brightness stays a window attribute (design assumes yes; no system-settings write, no permission).

---

## 11. Consumer theming contract
- Consumers override `player.accent`, `player.surface.*`, and typography via the token map; **layout/measurements are fixed by this spec** (so the "solid" feel is uniform across CalcVault & JGallery).
- Consumers may **hide** optional controls (subtitles, speed, resize) via a config flag but **cannot** alter the seekbar interaction contract (§4) — that's load-bearing.
- Default (unthemed) skin is the neutral dark player rendered above; the `:demo` app uses defaults verbatim.

---

## 12. Screen → code-file map (for engineering)
> Names are proposals for the SDK UI layer; final package owned with APP-583.

| Surface / component | Proposed Compose file |
|---------------------|-----------------------|
| Root player composable + state host | `PlayerSurface.kt` |
| Gesture arbiter (zones, priority, seekbar exclusivity) | `PlayerGestures.kt` |
| Seekbar (preview/commit, hit band, no-shift) | `PlayerSeekbar.kt` |
| Center transport (play/pause/±10, spinner) | `PlayerTransport.kt` |
| Top bar (title/back/lock/overflow) | `PlayerTopBar.kt` |
| Bottom bar (time row, speed, CC, resize) | `PlayerBottomBar.kt` |
| Brightness/volume/zoom HUDs | `PlayerHud.kt` |
| Audio-only layout | `AudioPlayerLayout.kt` |
| State overlays (loading/error/end/locked) | `PlayerStateOverlays.kt` |
| Controller façade interface (design-facing) | `PlayerController.kt` *(APP-583 owns impl)* |
| Tokens | `PlayerTokens.kt` |

---

## 13. Definition of Done (design) & handoff
- [x] Flows: open → play → seek → post-seek → end/replay; error → retry; lock → unlock; portrait↔landscape; video↔audio — **no dead ends** (every terminal state has an action).
- [x] Every state specified: loading, buffering, error, end, locked, audio-only, portrait, landscape.
- [x] **Seekbar contract unambiguous** (§4) — preview-on-drag / one-commit-on-release, fat-finger hit band, exclusive ownership, no-shift — mapped to the fail-then-pass tests APP-583 owns.
- [x] **Post-seek frame** guaranteed non-stale (§6), paused case explicit.
- [x] **Continuous pinch** — no jump/reset/release-spring (§5.1).
- [x] Gesture map with zones + feedback (§5).
- [x] Tokens defined, consumer-overridable, encryption-agnostic (§2, §10, §11).
- [x] Screen → code-file map (§12).
- [x] Research annotations tie each risky decision to a prior on-board failure.

**Handoff:** engineering builds against this in the `:demo` app; the three CEO on-device gates (seek-on-release, continuous pinch, post-seek frame) are exactly the three interactions §4/§5.1/§6 make unambiguous. Design will redline the first `:demo` build and adjust tokens against real device feel.

*v1 — Principal Product Designer, APP-584.*
