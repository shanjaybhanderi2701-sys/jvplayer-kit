@file:Suppress("MagicNumber") // Chrome spacing/typography values are design tokens from the redlines (§3).

package com.jv.player.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Top bar (design §3.1): back affordance + media title. Lock/overflow (§3.4) are later-wave
 * surfaces; Wave 2 ships the back + title the usable surface needs.
 */
@Composable
internal fun PlayerTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(
            glyph = "‹",
            description = "Back",
            testTag = "topbar_back",
            glyphSizeSp = 30,
            onClick = onBack,
        )
        Text(
            text = title,
            color = PlayerTokens.onPrimary,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 8.dp)
                .fillMaxWidth(),
        )
    }
}

/**
 * Bottom bar (design §3.1): the time row + seekbar + resize-mode control. The time row shows
 * elapsed / duration in `H:MM:SS`, `--:--` until a real duration is known (§7.1). The resize
 * button cycles Fit -> Fill -> Crop (§3.4) and stays in sync with pinch (§5.1).
 */
@Composable
internal fun PlayerBottomBar(controller: PlayerController, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatPlaybackTime(controller.positionMs),
                color = PlayerTokens.onPrimary,
                fontSize = 13.sp,
            )
            PlayerSeekbar(
                controller = controller,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            Text(
                text = formatPlaybackTime(if (controller.hasKnownDuration) controller.durationMs else -1L),
                color = PlayerTokens.onSecondary,
                fontSize = 13.sp,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ResizeButton(controller)
        }
    }
}

/** Cycles the resize mode and shows the current mode's label (design §3.4 `[⤢]`). */
@Composable
private fun ResizeButton(controller: PlayerController) {
    Text(
        text = "⤢ ${controller.resizeMode.label}",
        color = PlayerTokens.onPrimary,
        fontSize = 13.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, PlayerTokens.trackInactive, RoundedCornerShape(6.dp))
            .clickable { controller.cycleResizeMode() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("bottombar_resize"),
    )
}
