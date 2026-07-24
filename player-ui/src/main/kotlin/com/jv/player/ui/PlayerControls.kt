@file:Suppress("MagicNumber") // Chrome dp/sp sizing values are design tokens from the redlines (§3).

package com.jv.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Center transport cluster (design §3.1 center row): replay-10 · play/pause · forward-10.
 * While buffering, the center play glyph is replaced by a spinner (§7.3) — controls stay
 * interactive. The ±10s buttons are discrete, cheap, idempotent seeks that commit
 * immediately (design §3.4), unlike the seekbar's deferred commit.
 */
@Composable
internal fun PlayerTransport(controller: PlayerController, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(
            glyph = "«10",
            description = "Rewind 10 seconds",
            testTag = "transport_replay10",
            onClick = { controller.seekBy(-PlayerTokens.SEEK_STEP_MS) },
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .size(64.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (controller.phase == PlaybackPhase.Buffering) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Buffering" },
                    color = PlayerTokens.onPrimary,
                )
            } else {
                val playing = controller.isPlaying
                GlyphButton(
                    glyph = if (playing) "⏸" else "▶",
                    description = if (playing) "Pause" else "Play",
                    testTag = "transport_playpause",
                    glyphSizeSp = 40,
                    onClick = controller::playPause,
                )
            }
        }
        GlyphButton(
            glyph = "10»",
            description = "Forward 10 seconds",
            testTag = "transport_forward10",
            onClick = { controller.seekBy(PlayerTokens.SEEK_STEP_MS) },
        )
    }
}

/**
 * A glyph tap target sized to the design's minimum 48dp touch box (§2 `player.touch.min`).
 * Text glyphs (not vector icons) keep the surface icon-dependency-free and mirror the
 * ASCII redlines directly. Carries a stable [testTag] the `:demo` androidTest keys on.
 */
@Composable
internal fun GlyphButton(
    glyph: String,
    description: String,
    testTag: String,
    modifier: Modifier = Modifier,
    glyphSizeSp: Int = 22,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(PlayerTokens.touchMin)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            color = PlayerTokens.onPrimary,
            fontSize = glyphSizeSp.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}
