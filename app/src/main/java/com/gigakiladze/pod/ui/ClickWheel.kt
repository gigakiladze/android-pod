package com.gigakiladze.pod.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min

/** Everything the wheel can tell the rest of the app. */
sealed interface WheelEvent {
    data object ScrollUp : WheelEvent
    data object ScrollDown : WheelEvent
    data object Select : WheelEvent
    data object Menu : WheelEvent
    data object Next : WheelEvent
    data object Previous : WheelEvent
    data object PlayPause : WheelEvent
}

/** Radius of the centre select button as a fraction of the wheel radius. */
private const val CENTER_RATIO = 0.38f

/** Degrees of rotation per scroll tick — roughly 20 ticks per full revolution. */
private const val STEP_DEGREES = 18f

/** Below this much total rotation, a gesture counts as a tap rather than a scroll. */
private const val TAP_SLOP_DEGREES = 7f

/**
 * The touch click wheel. Dragging around the ring scrolls; tapping one of the four
 * cardinal zones fires MENU / prev / play-pause / next; tapping the middle selects.
 */
@Composable
fun ClickWheel(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    onEvent: (WheelEvent) -> Unit,
) {
    val colors = LocalPodColors.current
    val view = LocalView.current
    val textMeasurer = rememberTextMeasurer()
    val currentOnEvent by rememberUpdatedState(onEvent)

    var centerPressed by remember { mutableStateOf(false) }
    var pressedZone by remember { mutableStateOf<WheelEvent?>(null) }

    Canvas(
        modifier = modifier.pointerInput(Unit) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = min(size.width, size.height) / 2f
            val innerRadius = outerRadius * CENTER_RATIO

            awaitEachGesture {
                val down = awaitFirstDown()
                val startVector = down.position - center
                val startDistance = startVector.getDistance()

                // Middle of the wheel: a plain press-and-release select button.
                if (startDistance <= innerRadius) {
                    centerPressed = true
                    val up = waitForUpOrCancellation()
                    centerPressed = false
                    if (up != null) {
                        tick(view)
                        currentOnEvent(WheelEvent.Select)
                    }
                    return@awaitEachGesture
                }

                if (startDistance > outerRadius) return@awaitEachGesture

                val zone = zoneFor(angleOf(startVector))
                pressedZone = zone

                var lastAngle = angleOf(startVector)
                var accumulated = 0f
                var travelled = 0f

                drag(down.id) { change ->
                    val vector = change.position - center
                    // Ignore drift into the dead middle, where the angle goes wild.
                    if (vector.getDistance() < innerRadius * 0.6f) return@drag

                    val angle = angleOf(vector)
                    var delta = angle - lastAngle
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    lastAngle = angle

                    accumulated += delta
                    travelled += abs(delta)
                    if (travelled >= TAP_SLOP_DEGREES) pressedZone = null

                    // Clockwise moves down the list, as on the hardware.
                    while (accumulated >= STEP_DEGREES) {
                        accumulated -= STEP_DEGREES
                        tick(view)
                        currentOnEvent(WheelEvent.ScrollDown)
                    }
                    while (accumulated <= -STEP_DEGREES) {
                        accumulated += STEP_DEGREES
                        tick(view)
                        currentOnEvent(WheelEvent.ScrollUp)
                    }
                    change.consume()
                }

                pressedZone = null
                if (travelled < TAP_SLOP_DEGREES) {
                    tick(view)
                    currentOnEvent(zone)
                }
            }
        }
    ) {
        val outerRadius = min(size.width, size.height) / 2f
        val innerRadius = outerRadius * CENTER_RATIO

        drawWheelFace(colors, outerRadius, innerRadius, pressedZone)
        drawCenterButton(colors, innerRadius, centerPressed)
        drawWheelLabels(colors, textMeasurer, outerRadius, innerRadius, isPlaying)
    }
}

private fun DrawScope.drawWheelFace(
    colors: PodColors,
    outerRadius: Float,
    innerRadius: Float,
    pressedZone: WheelEvent?,
) {
    // A soft top-to-bottom gradient reads as the moulded plastic of the original.
    drawCircle(
        brush = Brush.verticalGradient(
            colors = listOf(colors.wheelFace, colors.wheelEdge),
            startY = center.y - outerRadius,
            endY = center.y + outerRadius,
        ),
        radius = outerRadius,
        center = center,
    )
    drawCircle(
        color = colors.wheelEdge,
        radius = outerRadius,
        center = center,
        style = Stroke(width = 1.5f),
    )

    // Highlight the quadrant being tapped.
    if (pressedZone != null) {
        val sweepStart = when (pressedZone) {
            WheelEvent.Menu -> -135f
            WheelEvent.Next -> -45f
            WheelEvent.PlayPause -> 45f
            else -> 135f
        }
        drawArc(
            color = colors.wheelLabel.copy(alpha = 0.14f),
            startAngle = sweepStart,
            sweepAngle = 90f,
            useCenter = true,
            topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
            size = Size(outerRadius * 2, outerRadius * 2),
        )
    }

    // Inner shadow ring around the hole in the middle.
    drawCircle(
        color = colors.wheelEdge,
        radius = innerRadius + 2f,
        center = center,
        style = Stroke(width = 3f),
    )
}

private fun DrawScope.drawCenterButton(colors: PodColors, innerRadius: Float, pressed: Boolean) {
    drawCircle(
        color = if (pressed) colors.wheelEdge else colors.centerButton,
        radius = innerRadius,
        center = center,
    )
    drawCircle(
        color = colors.wheelEdge,
        radius = innerRadius,
        center = center,
        style = Stroke(width = 1.5f),
    )
}

private fun DrawScope.drawWheelLabels(
    colors: PodColors,
    textMeasurer: TextMeasurer,
    outerRadius: Float,
    innerRadius: Float,
    isPlaying: Boolean,
) {
    val ringMid = (outerRadius + innerRadius) / 2f
    val glyph = outerRadius * 0.075f

    val menu = textMeasurer.measure("MENU", PodType.wheelLabel.copy(color = colors.wheelLabel))
    drawText(
        textLayoutResult = menu,
        topLeft = Offset(
            x = center.x - menu.size.width / 2f,
            y = center.y - ringMid - menu.size.height / 2f,
        ),
    )

    drawSkipGlyph(colors.wheelLabel, Offset(center.x - ringMid, center.y), glyph, pointsRight = false)
    drawSkipGlyph(colors.wheelLabel, Offset(center.x + ringMid, center.y), glyph, pointsRight = true)
    drawPlayPauseGlyph(colors.wheelLabel, Offset(center.x, center.y + ringMid), glyph, isPlaying)
}

/** The classic double triangle used for the skip buttons, centred on [at]. */
private fun DrawScope.drawSkipGlyph(color: Color, at: Offset, size: Float, pointsRight: Boolean) {
    val direction = if (pointsRight) 1f else -1f
    val spacing = size * 1.05f
    // Two triangles of width `size` spaced by `spacing` span (size + spacing) in total.
    val firstBase = -(size + spacing) / 2f

    for (index in 0..1) {
        val baseX = at.x + direction * (firstBase + index * spacing)
        val path = Path().apply {
            moveTo(baseX + direction * size, at.y)
            lineTo(baseX, at.y - size)
            lineTo(baseX, at.y + size)
            close()
        }
        drawPath(path, color)
    }
}

/** A right triangle followed by two bars — the same combined glyph the iPod used. */
private fun DrawScope.drawPlayPauseGlyph(color: Color, at: Offset, size: Float, isPlaying: Boolean) {
    val triangleWidth = size
    val barWidth = size * 0.36f
    val barGap = size * 0.22f
    val groupGap = size * 0.30f
    val total = triangleWidth + groupGap + barWidth + barGap + barWidth

    var x = at.x - total / 2f

    // The half that is *not* the current state is dimmed, so the glyph reads as a
    // state indicator as well as a button.
    val triangle = Path().apply {
        moveTo(x, at.y - size)
        lineTo(x + triangleWidth, at.y)
        lineTo(x, at.y + size)
        close()
    }
    drawPath(triangle, color.copy(alpha = if (isPlaying) 0.4f else 1f))

    x += triangleWidth + groupGap
    val barAlpha = if (isPlaying) 1f else 0.4f
    repeat(2) { index ->
        drawRect(
            color = color.copy(alpha = barAlpha),
            topLeft = Offset(x + index * (barWidth + barGap), at.y - size),
            size = Size(barWidth, size * 2),
        )
    }
}

/** Degrees clockwise from 3 o'clock, in screen coordinates where y grows downward. */
private fun angleOf(vector: Offset): Float =
    Math.toDegrees(atan2(vector.y.toDouble(), vector.x.toDouble())).toFloat()

private fun zoneFor(angleDegrees: Float): WheelEvent = when {
    angleDegrees >= -135f && angleDegrees < -45f -> WheelEvent.Menu
    angleDegrees >= -45f && angleDegrees < 45f -> WheelEvent.Next
    angleDegrees >= 45f && angleDegrees < 135f -> WheelEvent.PlayPause
    else -> WheelEvent.Previous
}

private fun tick(view: android.view.View) {
    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
}
