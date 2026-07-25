package com.jv.player.ui

/**
 * Formats a playback position/duration in ms as `H:MM:SS` (or `M:SS` under an hour), the
 * time-row format from the design redlines (§3.1). A negative or unknown value renders the
 * spec's `--:--` placeholder (§7.1 loading), so the time row never shows a bogus `0:00`
 * before the duration is known.
 */
private const val MS_PER_SECOND = 1000
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600

internal fun formatPlaybackTime(ms: Long): String {
    if (ms < 0) return "--:--"
    val totalSeconds = ms / MS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
