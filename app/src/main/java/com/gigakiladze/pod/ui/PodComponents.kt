package com.gigakiladze.pod.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.Text
import com.gigakiladze.pod.media.AlbumArt
import com.gigakiladze.pod.media.PlaybackState
import com.gigakiladze.pod.media.Track
import kotlin.math.max
import kotlin.math.min

/** The grey title bar across the top of every screen. */
@Composable
fun PodHeader(title: String, isPlaying: Boolean, batteryLevel: Float) {
    val colors = LocalPodColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(Brush.verticalGradient(listOf(colors.headerTop, colors.headerBottom))),
    ) {
        // Play / pause indicator, top left, exactly like the original.
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
                .size(12.dp)
        ) {
            if (isPlaying) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, size.height / 2f)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(path, colors.text)
            } else {
                val barWidth = size.width * 0.32f
                drawRect(colors.text, Offset(0f, 0f), Size(barWidth, size.height))
                drawRect(colors.text, Offset(size.width - barWidth, 0f), Size(barWidth, size.height))
            }
        }

        Text(
            text = title,
            style = PodType.header.copy(color = colors.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 44.dp),
        )

        BatteryGlyph(
            level = batteryLevel,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 8.dp)
                .size(width = 22.dp, height = 11.dp),
        )
    }
}

@Composable
private fun BatteryGlyph(level: Float, modifier: Modifier) {
    val colors = LocalPodColors.current
    Canvas(modifier) {
        val capWidth = size.width * 0.09f
        val bodyWidth = size.width - capWidth
        drawRect(
            color = colors.text,
            topLeft = Offset.Zero,
            size = Size(bodyWidth, size.height),
            style = Stroke(width = 1.5f),
        )
        drawRect(
            color = colors.text,
            topLeft = Offset(bodyWidth, size.height * 0.3f),
            size = Size(capWidth, size.height * 0.4f),
        )
        val inset = 2.5f
        drawRect(
            color = colors.text,
            topLeft = Offset(inset, inset),
            size = Size(
                width = max(0f, (bodyWidth - inset * 2) * level.coerceIn(0f, 1f)),
                height = max(0f, size.height - inset * 2),
            ),
        )
    }
}

/**
 * A scrolling menu. The cursor is driven by the wheel, not by touch, so the list
 * scrolls only far enough to keep the highlighted row on screen.
 */
@Composable
fun PodMenuList(
    rows: List<PodRow>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPodColors.current
    val listState = rememberLazyListState()

    KeepSelectionVisible(listState, selectedIndex, rows.size)

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        itemsIndexed(rows) { index, row ->
            val selected = index == selectedIndex
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (row.secondary != null) 44.dp else 34.dp)
                    .background(
                        if (selected) {
                            Brush.verticalGradient(listOf(colors.selectionTop, colors.selectionBottom))
                        } else {
                            Brush.verticalGradient(listOf(colors.background, colors.background))
                        }
                    )
                    .padding(horizontal = 10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.label,
                        style = PodType.row.copy(
                            color = if (selected) colors.selectedText else colors.text
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (row.secondary != null) {
                        Text(
                            text = row.secondary,
                            style = PodType.rowDim.copy(
                                color = if (selected) {
                                    colors.selectedText.copy(alpha = 0.8f)
                                } else {
                                    colors.textDim
                                }
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (row.trailing != null) {
                    Text(
                        text = row.trailing,
                        style = PodType.rowDim.copy(
                            color = if (selected) colors.selectedText else colors.textDim
                        ),
                        maxLines = 1,
                    )
                }
                if (row.chevron) {
                    Spacer(Modifier.width(6.dp))
                    Chevron(selected)
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.separator)
            )
        }
    }
}

@Composable
private fun Chevron(selected: Boolean) {
    val colors = LocalPodColors.current
    Canvas(Modifier.size(width = 7.dp, height = 12.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(0f, size.height)
        }
        drawPath(
            path = path,
            color = if (selected) colors.selectedText else colors.textDim,
            style = Stroke(width = 2f),
        )
    }
}

/** Scrolls only when the cursor would otherwise leave the viewport. */
@Composable
private fun KeepSelectionVisible(listState: LazyListState, selectedIndex: Int, itemCount: Int) {
    LaunchedEffect(selectedIndex, itemCount) {
        if (itemCount == 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) {
            listState.scrollToItem(selectedIndex.coerceIn(0, itemCount - 1))
            return@LaunchedEffect
        }
        val first = visible.first().index
        val last = visible.last().index
        when {
            selectedIndex <= first -> listState.animateScrollToItem(max(0, selectedIndex - 1))
            selectedIndex >= last -> {
                val windowSize = max(1, visible.size - 1)
                listState.animateScrollToItem(max(0, min(itemCount - 1, selectedIndex) - windowSize + 1))
            }
        }
    }
}

/** Decodes cover art off the main thread and recomposes when it arrives. */
@Composable
fun rememberAlbumArt(track: Track?): ImageBitmap? {
    val key = track?.file?.absolutePath
    var art by remember(key) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(key) {
        art = track?.let { AlbumArt.of(it) }
    }
    return art
}

/**
 * Cover art, falling back to a drawn music-note placeholder.
 *
 * The caller supplies the sizing: Now Playing derives the square from the available
 * height, the menu preview pane from the available width.
 */
@Composable
fun AlbumArtwork(track: Track?, modifier: Modifier = Modifier) {
    val colors = LocalPodColors.current
    val art = rememberAlbumArt(track)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(colors.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("♪", style = PodType.nowPlayingTitle.copy(color = colors.textDim))
        }
    }
}

/** The split view used on the top-level menus: list on the left, art preview on the right. */
@Composable
fun PodSplitMenu(
    rows: List<PodRow>,
    selectedIndex: Int,
    previewTrack: Track?,
    previewCaption: String?,
) {
    val colors = LocalPodColors.current
    Row(Modifier.fillMaxSize()) {
        PodMenuList(
            rows = rows,
            selectedIndex = selectedIndex,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(colors.separator)
        )
        Column(
            modifier = Modifier
                .weight(0.85f)
                .fillMaxHeight()
                .background(colors.surface)
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            AlbumArtwork(previewTrack, Modifier.fillMaxWidth(0.9f).aspectRatio(1f))
            if (previewCaption != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = previewCaption,
                    style = PodType.rowDim.copy(color = colors.textDim),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The Now Playing screen: art, track details, and a scrub bar. */
@Composable
fun NowPlayingScreen(state: PlaybackState, positionMs: Long) {
    val colors = LocalPodColors.current
    val track = state.track

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (track == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing Playing", style = PodType.row.copy(color = colors.textDim))
            }
            return@Column
        }

        Text(
            text = "${state.queueIndex + 1} of ${state.queueSize}",
            style = PodType.rowDim.copy(color = colors.textDim),
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            AlbumArtwork(track, Modifier.fillMaxHeight().aspectRatio(1f))
        }
        Spacer(Modifier.height(10.dp))

        Text(
            text = track.title,
            style = PodType.nowPlayingTitle.copy(color = colors.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.artist,
            style = PodType.nowPlayingSub.copy(color = colors.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (track.album.isNotEmpty()) {
            Text(
                text = track.album,
                style = PodType.nowPlayingSub.copy(color = colors.textDim),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(10.dp))
        val progress = if (state.durationMs > 0L) {
            (positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        ProgressBar(progress)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = Track.formatDuration(positionMs),
                style = PodType.timecode.copy(color = colors.textDim),
            )
            Text(
                text = "-" + Track.formatDuration((state.durationMs - positionMs).coerceAtLeast(0L)),
                style = PodType.timecode.copy(color = colors.textDim),
            )
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    val colors = LocalPodColors.current
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
    ) {
        val radius = size.height / 2f
        drawRoundRect(
            color = colors.progressTrack,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
        )
        if (progress > 0f) {
            drawRoundRect(
                color = colors.progressFill,
                size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
            )
        }
    }
}

/** Full-screen brightness adjuster; the wheel drives the bar. */
@Composable
fun BrightnessScreen(value: Float) {
    val colors = LocalPodColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Brightness", style = PodType.nowPlayingTitle.copy(color = colors.text))
        Spacer(Modifier.height(20.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(18.dp)
        ) {
            val radius = size.height / 2f
            drawRoundRect(
                color = colors.progressTrack,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
            )
            drawRoundRect(
                color = colors.progressFill,
                size = Size(size.width * value.coerceIn(0f, 1f), size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "${(value * 100).toInt()}%",
            style = PodType.row.copy(color = colors.textDim),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Turn the wheel to adjust",
            style = PodType.rowDim.copy(color = colors.textDim),
        )
    }
}

/** Shown while the disk walk is running, and when it finds nothing. */
@Composable
fun StatusScreen(headline: String, detail: String?) {
    val colors = LocalPodColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(headline, style = PodType.row.copy(color = colors.text))
        if (detail != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = detail,
                style = PodType.rowDim.copy(color = colors.textDim),
            )
        }
    }
}

/**
 * The iPod's volume bar, flashed over whatever screen is showing when the hardware
 * keys are pressed. Replaces Android's own volume panel, which would otherwise
 * slide in over the top and give the game away.
 */
@Composable
fun VolumeHud(level: Float, modifier: Modifier = Modifier) {
    val colors = LocalPodColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Volume",
            style = PodType.rowDim.copy(color = colors.textDim),
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SpeakerGlyph(filled = false, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(8.dp))
            Canvas(
                Modifier
                    .weight(1f)
                    .height(12.dp)
            ) {
                val radius = size.height / 2f
                drawRoundRect(
                    color = colors.progressTrack,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                )
                if (level > 0f) {
                    drawRoundRect(
                        color = colors.progressFill,
                        size = Size(size.width * level.coerceIn(0f, 1f), size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            SpeakerGlyph(filled = true, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SpeakerGlyph(filled: Boolean, modifier: Modifier) {
    val colors = LocalPodColors.current
    Canvas(modifier) {
        val body = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width * 0.05f, size.height * 0.35f)
            lineTo(size.width * 0.32f, size.height * 0.35f)
            lineTo(size.width * 0.60f, size.height * 0.10f)
            lineTo(size.width * 0.60f, size.height * 0.90f)
            lineTo(size.width * 0.32f, size.height * 0.65f)
            lineTo(size.width * 0.05f, size.height * 0.65f)
            close()
        }
        drawPath(body, colors.textDim)
        if (filled) {
            // A couple of arcs standing in for sound waves on the "loud" end.
            for (index in 1..2) {
                val inset = size.width * (0.62f + index * 0.12f)
                drawArc(
                    color = colors.textDim,
                    startAngle = -55f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(inset - size.width * 0.30f, size.height * 0.5f - inset * 0.55f),
                    size = Size(size.width * 0.60f, inset * 1.10f),
                    style = Stroke(width = size.width * 0.07f),
                )
            }
        }
    }
}
