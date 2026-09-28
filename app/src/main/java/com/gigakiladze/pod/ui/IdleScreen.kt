package com.gigakiladze.pod.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.Text
import com.gigakiladze.pod.media.Track

/**
 * What the display shows once the device has been left alone.
 *
 * The Pixel 3 has an OLED panel, where a black pixel is an unlit pixel. Because the
 * screen is never allowed to sleep, keeping almost every pixel black is the single
 * biggest power saving available short of turning it off — far more than dimming
 * the full interface, which still lights every pixel in the menus.
 *
 * The text drifts slowly for the same reason any always-on display does it: a
 * static bright element on OLED will eventually burn in.
 */
@Composable
fun IdleScreen(track: Track?, isPlaying: Boolean) {
    // ~4 minute cycle, a few dp of travel — imperceptible to watch, enough to stop
    // any one pixel carrying the same bright content indefinitely.
    val drift = rememberInfiniteTransition(label = "idle-drift")
    val phase by drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 240_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "idle-phase",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (track == null) return@Box

        Column(
            modifier = Modifier
                .offset(
                    x = ((phase - 0.5f) * 24f).dp,
                    y = ((phase - 0.5f) * 80f).dp,
                )
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = track.title,
                style = PodType.nowPlayingSub.copy(
                    color = IdleTextColor,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = PodType.rowDim.copy(
                    color = IdleSubTextColor,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isPlaying) {
                Text(
                    text = "Paused",
                    style = PodType.rowDim.copy(color = IdleSubTextColor),
                )
            }
        }
    }
}

/** Deliberately dim: readable in a dark room, barely any light output. */
private val IdleTextColor = Color(0xFF5A5A5E)
private val IdleSubTextColor = Color(0xFF3A3A3E)
