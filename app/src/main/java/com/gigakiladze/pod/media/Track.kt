package com.gigakiladze.pod.media

import android.net.Uri
import java.io.File

/** One MP3 found on disk, with whatever ID3 metadata we could read out of it. */
data class Track(
    val id: Long,
    val file: File,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
) {
    val uri: Uri get() = Uri.fromFile(file)

    /** "3:24" — the iPod never showed hours, and neither do we unless a track needs it. */
    val durationLabel: String get() = formatDuration(durationMs)

    companion object {
        fun formatDuration(ms: Long): String {
            if (ms <= 0L) return "--:--"
            val totalSeconds = ms / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60
            return if (hours > 0) {
                String.format("%d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%d:%02d", minutes, seconds)
            }
        }
    }
}

/** A named grouping of tracks — an artist or an album, as shown in the browse menus. */
data class TrackGroup(
    val name: String,
    val tracks: List<Track>,
)
