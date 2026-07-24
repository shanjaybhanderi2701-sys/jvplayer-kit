package com.jv.player.ui

import androidx.media3.ui.AspectRatioFrameLayout

/**
 * Video resize modes the control surface exposes (APP-587 scope; design §3.4 / §8).
 *
 * Each maps 1:1 to a Media3 [AspectRatioFrameLayout] resize constant so the render target
 * and the control state can never disagree. The Wave 2 set is the three the scope names —
 * FIT / FILL / ZOOM — cycled by the `[⤢]` button and reached by pinch (§5.1). "Original"
 * (`RESIZE_MODE_FIXED_*`) is intentionally deferred; these three cover the resize JTBD.
 *
 * @property label short user-facing label shown on the resize control.
 * @property frameLayoutMode the `AspectRatioFrameLayout.RESIZE_MODE_*` value to apply.
 */
internal enum class ResizeMode(
    val label: String,
    val frameLayoutMode: Int,
) {
    /** Letterbox — whole frame visible, original aspect ratio (default). */
    Fit("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),

    /** Stretch to fill the view, ignoring aspect ratio. */
    Fill("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL),

    /** Crop — fill the view preserving aspect ratio, clipping overflow. */
    Zoom("Crop", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    ;

    /** Next mode in the cycle used by the resize button (Fit -> Fill -> Crop -> Fit). */
    fun next(): ResizeMode = entries[(ordinal + 1) % entries.size]
}
