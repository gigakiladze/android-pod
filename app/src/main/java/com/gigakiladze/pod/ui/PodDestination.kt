package com.gigakiladze.pod.ui

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.gigakiladze.pod.media.Track

/** A screen in the iPod's menu hierarchy. */
sealed interface PodDestination {
    val title: String

    /** Identity used to remember where the cursor was when you back out and return. */
    val key: String get() = this::class.simpleName + ":" + title

    data object MainMenu : PodDestination {
        override val title = "iPod"
    }

    data object MusicMenu : PodDestination {
        override val title = "Music"
    }

    data object AllSongs : PodDestination {
        override val title = "Songs"
    }

    data object Artists : PodDestination {
        override val title = "Artists"
    }

    data class ArtistDetail(val artist: String) : PodDestination {
        override val title = artist
    }

    data object Albums : PodDestination {
        override val title = "Albums"
    }

    data class AlbumDetail(val album: String) : PodDestination {
        override val title = album
    }

    data object NowPlaying : PodDestination {
        override val title = "Now Playing"
    }

    data object Settings : PodDestination {
        override val title = "Settings"
    }

    data object Brightness : PodDestination {
        override val title = "Brightness"
    }
}

/** One selectable line in a menu. */
data class PodRow(
    val label: String,
    val secondary: String? = null,
    val trailing: String? = null,
    val chevron: Boolean = false,
    /** Cover art to show in the preview pane while this row is highlighted. */
    val previewTrack: Track? = null,
    val onSelect: () -> Unit = {},
)

/**
 * The back stack, plus a per-screen cursor position. Keeping the cursor means
 * backing out of an album and re-entering puts you back on the same row.
 */
class PodNavState {

    private val stack = mutableStateListOf<PodDestination>(PodDestination.MainMenu)
    private val cursors = mutableStateMapOf<String, Int>()

    val current: PodDestination get() = stack.last()

    val canGoBack: Boolean get() = stack.size > 1

    fun push(destination: PodDestination) {
        stack.add(destination)
    }

    /** Replaces the top of the stack — used so Now Playing does not stack up on itself. */
    fun replaceTop(destination: PodDestination) {
        stack[stack.lastIndex] = destination
    }

    fun pop() {
        if (canGoBack) stack.removeAt(stack.lastIndex)
    }

    fun cursor(destination: PodDestination): Int = cursors[destination.key] ?: 0

    fun setCursor(destination: PodDestination, index: Int) {
        cursors[destination.key] = index
    }

    fun moveCursor(destination: PodDestination, delta: Int, itemCount: Int) {
        if (itemCount <= 0) return
        val next = (cursor(destination) + delta).coerceIn(0, itemCount - 1)
        cursors[destination.key] = next
    }
}
