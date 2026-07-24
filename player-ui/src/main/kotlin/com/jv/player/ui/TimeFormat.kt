package com.jv.player.ui

/**
 * Formats a playback position/duration in ms as `H:MM:SS` (or `M:SS` under an hour), the
 * time-row format from the design redlines (§3.1). A negative or unknown value renders the
 * spec's `--:--` placeholder (§7.1 loading), so the time row never shows a bogus `0:00`
 * before the duration is known.
 */
internal fun formatPlaybackTime(ms: Long): String {
    if (ms < 0) return "--:--"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
