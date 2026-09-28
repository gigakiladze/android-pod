package com.gigakiladze.pod.media

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface LibraryState {
    data object Idle : LibraryState
    data class Scanning(val found: Int) : LibraryState
    data class Ready(val tracks: List<Track>) : LibraryState
    data class Failed(val message: String) : LibraryState
}

/**
 * The scanned track list, plus the artist/album groupings the browse menus need.
 *
 * The groupings are computed once per scan rather than on each access: the UI
 * recomposes several times a second while a track plays, and regrouping a large
 * library on every frame would show up as wheel lag.
 */
class MusicLibrary(private val scope: CoroutineScope) {

    private val _state = MutableStateFlow<LibraryState>(LibraryState.Idle)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private var scanJob: Job? = null

    var tracks: List<Track> = emptyList()
        private set

    var artists: List<TrackGroup> = emptyList()
        private set

    var albums: List<TrackGroup> = emptyList()
        private set

    /** Re-runs the disk walk. A scan already in flight is cancelled and replaced. */
    fun rescan() {
        scanJob?.cancel()
        scanJob = scope.launch {
            _state.value = LibraryState.Scanning(0)
            try {
                val found = MusicScanner.scan { count ->
                    _state.value = LibraryState.Scanning(count)
                }
                tracks = found
                artists = group(found, Track::artist)
                albums = group(found, Track::album)
                _state.value = LibraryState.Ready(found)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = LibraryState.Failed(e.message ?: "Scan failed")
            }
        }
    }

    /** Tracks for one artist or album, taken from the precomputed grouping. */
    fun tracksIn(groups: List<TrackGroup>, name: String): List<Track> =
        groups.firstOrNull { it.name == name }?.tracks.orEmpty()

    private fun group(source: List<Track>, selector: (Track) -> String): List<TrackGroup> =
        source.groupBy(selector)
            .map { (name, groupTracks) -> TrackGroup(name, groupTracks) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, TrackGroup::name))
}
