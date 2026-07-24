@file:Suppress("MagicNumber") // Design tokens (APP-584 §2): the literal dp/ms/% values ARE the spec.

package com.jv.player.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Player-local design tokens (APP-584 spec §2). Namespaced under this object so a consumer
 * app's theme can override the accent without collision. Defaults target the neutral dark
 * player the `:demo` app renders verbatim (§11).
 *
 * These are the Wave 2 subset the usable control surface needs — the full token set (HUD
 * pills, brightness/volume glyphs, error/loading states) fills in as those surfaces land.
 * Every value here is a build target from the spec, not a suggestion.
 */
internal object PlayerTokens {
    // Color — default dark skin (§2 color table).
    val scrimControls = Color(0x8C000000) // #000000 @ ~55%, chrome legibility over any frame.
    val accent = Color(0xFF4C8DFF) // active scrubber fill / selected control (consumer-overridable).
    val onPrimary = Color(0xEBFFFFFF) // #FFFFFF @ 92% — icons, primary time label.
    val onSecondary = Color(0x99FFFFFF) // #FFFFFF @ 60% — duration, secondary labels.
    val trackInactive = Color(0x3DFFFFFF) // #FFFFFF @ 24% — remaining track.
    val trackBuffer = Color(0x66FFFFFF) // #FFFFFF @ 40% — buffered-ahead fill.
    val hudBg = Color(0xB3000000) // #000000 @ 70% — HUD pill background.

    // Dimension / motion (§2 dimension table).
    val touchMin = 48.dp // minimum interactive hit box.
    val seekHitHeight = 48.dp // seekbar invisible hit band; fixes the row height (no reflow).
    val seekTrackIdle = 3.dp
    val seekTrackDragging = 5.dp
    val seekThumbIdle = 6.dp
    val seekThumbDragging = 16.dp

    const val CHROME_FADE_MS = 220 // show/hide controls.
    const val CHROME_AUTOHIDE_MS = 3000L // auto-hide after last interaction, while playing only.
    const val SEEK_STEP_MS = 10_000L // double-tap / skip increment.

    /** Progress poll cadence — drives the time row + seekbar thumb while NOT dragging. */
    const val PROGRESS_POLL_MS = 200L

    /** Pinch scale that flips the resize mode Fit -> Crop (and back below it), §5.1 threshold. */
    const val PINCH_ZOOM_IN_THRESHOLD = 1.15f
    const val PINCH_ZOOM_OUT_THRESHOLD = 0.87f
}
