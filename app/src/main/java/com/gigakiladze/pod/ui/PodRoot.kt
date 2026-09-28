package com.gigakiladze.pod.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gigakiladze.pod.PodSettings
import com.gigakiladze.pod.media.LibraryState
import com.gigakiladze.pod.media.MusicLibrary
import com.gigakiladze.pod.media.PlaybackState
import com.gigakiladze.pod.media.PlayerConnection
import com.gigakiladze.pod.media.Track
import com.gigakiladze.pod.media.VolumeController

/** Seconds of audio skipped per wheel tick while Now Playing is on screen. */
private const val SCRUB_STEP_MS = 5_000L

/** Brightness change per wheel tick. */
private const val BRIGHTNESS_STEP = 0.04f

@Composable
fun PodRoot(
    library: MusicLibrary,
    player: PlayerConnection,
    settings: PodSettings,
    volume: VolumeController,
    nav: PodNavState,
    dimmed: Boolean = false,
    onInteraction: () -> Unit = {},
) {
    val colors = LocalPodColors.current
    val libraryState by library.state.collectAsState()
    val playback by player.state.collectAsState()
    val darkTheme by settings.darkTheme.collectAsState()
    val brightness by settings.brightness.collectAsState()
    val batteryLevel = rememberBatteryLevel()
    val volumeLevel by volume.level.collectAsState()
    val volumeChanges by volume.changes.collectAsState()

    // Flash the volume bar for a moment after each key press, then hide it again.
    var showVolume by remember { mutableStateOf(false) }
    LaunchedEffect(volumeChanges) {
        if (volumeChanges > 0) {
            showVolume = true
            kotlinx.coroutines.delay(1_500)
            showVolume = false
        }
    }

    val destination = nav.current

    // Keyed on what the rows actually depend on, so the twice-a-second position
    // ticks do not rebuild a list with one entry per track.
    val rows = remember(destination, libraryState, darkTheme, playback.track != null) {
        buildRows(
            destination = destination,
            libraryState = libraryState,
            library = library,
            hasNowPlaying = playback.track != null,
            darkTheme = darkTheme,
            settings = settings,
            nav = nav,
            onPlay = { tracks, index ->
                player.play(tracks, index)
                nav.push(PodDestination.NowPlaying)
            },
        )
    }

    val cursor = nav.cursor(destination).coerceIn(0, maxOf(0, rows.size - 1))

    if (dimmed) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            awaitPointerEvent(PointerEventPass.Initial)
                            onInteraction()
                        }
                    }
                }
        ) {
            IdleScreen(track = playback.track, isPlaying = playback.isPlaying)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bezel)
            .pointerInput(Unit) {
                // Initial pass so this observes without consuming the wheel's gestures.
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        onInteraction()
                    }
                }
            },
    ) {
        Spacer(Modifier.height(18.dp))

        // The display: a 4:3 panel, the same proportion as the iPod classic screen.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.background)
                .border(1.dp, colors.separator, RoundedCornerShape(4.dp)),
        ) {
            Column(Modifier.fillMaxSize()) {
                PodHeader(
                    title = destination.title,
                    isPlaying = playback.isPlaying,
                    batteryLevel = batteryLevel,
                )
                Box(Modifier.fillMaxSize().clipToBounds()) {
                    ScreenContent(
                        destination = destination,
                        rows = rows,
                        cursor = cursor,
                        libraryState = libraryState,
                        playback = playback,
                        brightness = brightness,
                        player = player,
                    )
                    if (showVolume) {
                        VolumeHud(
                            level = volumeLevel,
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            ClickWheel(
                modifier = Modifier
                    .fillMaxWidth(0.82f)
                    .aspectRatio(1f),
                isPlaying = playback.isPlaying,
                onEvent = { event ->
                    onInteraction()
                    handleWheelEvent(
                        event = event,
                        destination = destination,
                        rows = rows,
                        cursor = cursor,
                        nav = nav,
                        player = player,
                        settings = settings,
                    )
                },
            )
        }
    }
}

@Composable
private fun ScreenContent(
    destination: PodDestination,
    rows: List<PodRow>,
    cursor: Int,
    libraryState: LibraryState,
    playback: PlaybackState,
    brightness: Float,
    player: PlayerConnection,
) {
    when (destination) {
        PodDestination.NowPlaying -> {
            // Subscribed to only here, so the 2 Hz position updates cannot
            // recompose the menus when Now Playing is not the visible screen.
            val positionMs by player.positionMs.collectAsState()
            NowPlayingScreen(playback, positionMs)
        }

        PodDestination.Brightness -> BrightnessScreen(brightness)

        PodDestination.MainMenu, PodDestination.MusicMenu -> PodSplitMenu(
            rows = rows,
            selectedIndex = cursor,
            previewTrack = rows.getOrNull(cursor)?.previewTrack ?: playback.track,
            previewCaption = playback.track?.title,
        )

        else -> when {
            // Track lists depend on the scan, so surface its state instead of an empty list.
            libraryState is LibraryState.Scanning && destination.needsLibrary ->
                StatusScreen("Scanning…", "${libraryState.found} files found")

            libraryState is LibraryState.Failed && destination.needsLibrary ->
                StatusScreen("Scan failed", libraryState.message)

            rows.isEmpty() && destination.needsLibrary ->
                StatusScreen("No Music", "Copy MP3 files to the device, then Music › Rescan Library")

            else -> PodMenuList(rows = rows, selectedIndex = cursor)
        }
    }
}

private val PodDestination.needsLibrary: Boolean
    get() = this is PodDestination.AllSongs ||
        this is PodDestination.Artists ||
        this is PodDestination.Albums ||
        this is PodDestination.ArtistDetail ||
        this is PodDestination.AlbumDetail

private fun handleWheelEvent(
    event: WheelEvent,
    destination: PodDestination,
    rows: List<PodRow>,
    cursor: Int,
    nav: PodNavState,
    player: PlayerConnection,
    settings: PodSettings,
) {
    when (event) {
        WheelEvent.Menu -> nav.pop()
        WheelEvent.PlayPause -> player.togglePlayPause()
        WheelEvent.Next -> player.next()
        WheelEvent.Previous -> player.previous()

        WheelEvent.Select -> when (destination) {
            // The centre button on Now Playing acts as play/pause.
            PodDestination.NowPlaying -> player.togglePlayPause()
            PodDestination.Brightness -> nav.pop()
            else -> rows.getOrNull(cursor)?.onSelect?.invoke()
        }

        WheelEvent.ScrollUp, WheelEvent.ScrollDown -> {
            val direction = if (event == WheelEvent.ScrollDown) 1 else -1
            when (destination) {
                PodDestination.NowPlaying ->
                    player.seekTo(player.currentPositionMs() + direction * SCRUB_STEP_MS)

                PodDestination.Brightness ->
                    settings.nudgeBrightness(direction * BRIGHTNESS_STEP)

                else -> nav.moveCursor(destination, direction, rows.size)
            }
        }
    }
}

private fun buildRows(
    destination: PodDestination,
    libraryState: LibraryState,
    library: MusicLibrary,
    hasNowPlaying: Boolean,
    darkTheme: Boolean,
    settings: PodSettings,
    nav: PodNavState,
    onPlay: (List<Track>, Int) -> Unit,
): List<PodRow> = when (destination) {

    PodDestination.MainMenu -> buildList {
        add(PodRow("Music", chevron = true) { nav.push(PodDestination.MusicMenu) })
        if (hasNowPlaying) {
            add(PodRow("Now Playing", chevron = true) { nav.push(PodDestination.NowPlaying) })
        }
        add(PodRow("Brightness", chevron = true) { nav.push(PodDestination.Brightness) })
        add(PodRow("Settings", chevron = true) { nav.push(PodDestination.Settings) })
    }

    PodDestination.MusicMenu -> listOf(
        PodRow("All Songs", chevron = true) { nav.push(PodDestination.AllSongs) },
        PodRow("Artists", chevron = true) { nav.push(PodDestination.Artists) },
        PodRow("Albums", chevron = true) { nav.push(PodDestination.Albums) },
        PodRow(
            label = "Rescan Library",
            trailing = (libraryState as? LibraryState.Ready)?.tracks?.size?.toString(),
        ) { library.rescan() },
    )

    PodDestination.AllSongs -> library.tracks.toRows(onPlay)

    PodDestination.Artists -> library.artists.map { group ->
        PodRow(
            label = group.name,
            trailing = group.tracks.size.toString(),
            chevron = true,
            previewTrack = group.tracks.firstOrNull(),
        ) { nav.push(PodDestination.ArtistDetail(group.name)) }
    }

    is PodDestination.ArtistDetail ->
        library.tracksIn(library.artists, destination.artist).toRows(onPlay)

    PodDestination.Albums -> library.albums.map { group ->
        PodRow(
            label = group.name,
            secondary = group.tracks.firstOrNull()?.artist,
            trailing = group.tracks.size.toString(),
            chevron = true,
            previewTrack = group.tracks.firstOrNull(),
        ) { nav.push(PodDestination.AlbumDetail(group.name)) }
    }

    is PodDestination.AlbumDetail ->
        library.tracksIn(library.albums, destination.album).toRows(onPlay)

    PodDestination.Settings -> listOf(
        PodRow("Theme", trailing = if (darkTheme) "Dark" else "Light") { settings.toggleTheme() },
        PodRow("Brightness", chevron = true) { nav.push(PodDestination.Brightness) },
        PodRow("Rescan Library") { library.rescan() },
    )

    PodDestination.NowPlaying, PodDestination.Brightness -> emptyList()
}

private fun List<Track>.toRows(onPlay: (List<Track>, Int) -> Unit): List<PodRow> =
    mapIndexed { index, track ->
        PodRow(
            label = track.title,
            secondary = track.artist,
            trailing = track.durationLabel,
            previewTrack = track,
        ) { onPlay(this, index) }
    }

/** Live battery percentage for the header glyph. */
@Composable
private fun rememberBatteryLevel(): Float {
    val context = LocalContext.current
    var level by remember { mutableStateOf(1f) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ignored: Context?, intent: Intent) {
                val current = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (current >= 0 && scale > 0) level = current.toFloat() / scale
            }
        }
        // ACTION_BATTERY_CHANGED is sticky, so this also delivers the current value.
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return level
}
