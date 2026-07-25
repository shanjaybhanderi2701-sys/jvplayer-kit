@file:Suppress("MagicNumber") // Overlay spacing/typography values are design tokens from the redlines (§7).

package com.jv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Full-surface error overlay with a one-tap retry (APP-589 W4 error/retry UX; design §7).
 *
 * Shown whenever [PlayerController.errorMessage] is non-null — a [PlaybackException] surfaced by
 * the engine. It dims the frame, states that playback failed (with the engine's error code name
 * for support/debugging), and offers **Retry**, which calls [PlayerController.retry] to re-prepare
 * the current source from the failed position. The message clears optimistically on tap and
 * re-appears only if the retry also fails, so a transient network/decoder hiccup recovers in one
 * gesture. Sits above the chrome so it is reachable even when controls have auto-hidden.
 */
@Composable
internal fun PlayerErrorOverlay(controller: PlayerController, modifier: Modifier = Modifier) {
    val message = controller.errorMessage ?: return
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xC0000000))
            .testTag("player_error_overlay"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "Can't play this video",
                color = PlayerTokens.onPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = message,
                color = PlayerTokens.onSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Retry",
                color = PlayerTokens.onPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(PlayerTokens.accent)
                    .clickable { controller.retry() }
                    .padding(horizontal = 24.dp, vertical = 10.dp)
                    .testTag("player_error_retry"),
            )
        }
    }
}

/**
 * Backdrop shown for an **audio-only** source (APP-589 W4 audio-only path; design §7).
 *
 * A source with no video track leaves the [SurfaceView] painting nothing, so once the engine is
 * ready and reports [PlayerController.hasVideo]` == false` we cover the black surface with a simple
 * audio affordance + the item [title]. Gating on `phase == Ready` (not merely "no size yet") avoids
 * flashing this behind a video during the brief window before its first frame reports a size. The
 * full transport chrome still works over the top — this is purely the empty-surface stand-in.
 */
@Composable
internal fun AudioOnlyBackdrop(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("audio_only_backdrop"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(text = "♪", color = PlayerTokens.onPrimary, fontSize = 64.sp)
            Text(
                text = title,
                color = PlayerTokens.onSecondary,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
