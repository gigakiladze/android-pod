package com.gigakiladze.pod.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The iPod's palette: mostly greyscale chrome with a single blue accent for the
 * selection bar. Both themes keep that structure so the device still reads as an
 * iPod whichever way the toggle is set.
 */
@Immutable
data class PodColors(
    val background: Color,
    val surface: Color,
    val headerTop: Color,
    val headerBottom: Color,
    val text: Color,
    val textDim: Color,
    val separator: Color,
    val selectionTop: Color,
    val selectionBottom: Color,
    val selectedText: Color,
    val wheelFace: Color,
    val wheelEdge: Color,
    val wheelLabel: Color,
    val centerButton: Color,
    val bezel: Color,
    val progressTrack: Color,
    val progressFill: Color,
)

private val LightPod = PodColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF2F2F5),
    headerTop = Color(0xFFFBFBFD),
    headerBottom = Color(0xFFD5D5DB),
    text = Color(0xFF0A0A0A),
    textDim = Color(0xFF7A7A80),
    separator = Color(0xFFD8D8DE),
    selectionTop = Color(0xFF6FA9E8),
    selectionBottom = Color(0xFF1F63B8),
    selectedText = Color(0xFFFFFFFF),
    wheelFace = Color(0xFFE4E4E8),
    wheelEdge = Color(0xFFBEBEC6),
    wheelLabel = Color(0xFF6A6A72),
    centerButton = Color(0xFFF4F4F7),
    bezel = Color(0xFFCFCFD6),
    progressTrack = Color(0xFFD5D5DC),
    progressFill = Color(0xFF2B6FC4),
)

private val DarkPod = PodColors(
    background = Color(0xFF000000),
    surface = Color(0xFF131316),
    headerTop = Color(0xFF2A2A2E),
    headerBottom = Color(0xFF131316),
    text = Color(0xFFF2F2F5),
    textDim = Color(0xFF8A8A92),
    separator = Color(0xFF2A2A2E),
    selectionTop = Color(0xFF5B9BE0),
    selectionBottom = Color(0xFF16509C),
    selectedText = Color(0xFFFFFFFF),
    wheelFace = Color(0xFF232327),
    wheelEdge = Color(0xFF3A3A40),
    wheelLabel = Color(0xFFA0A0A8),
    centerButton = Color(0xFF2E2E34),
    bezel = Color(0xFF1A1A1E),
    progressTrack = Color(0xFF34343A),
    progressFill = Color(0xFF5B9BE0),
)

val LocalPodColors: ProvidableCompositionLocal<PodColors> = staticCompositionLocalOf { DarkPod }

object PodType {
    val header = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    val row = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 17.sp)
    val rowDim = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp)
    val nowPlayingTitle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 19.sp)
    val nowPlayingSub = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp)
    val timecode = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 12.sp)
    val wheelLabel = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 13.sp)
}

@Composable
fun PodTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPodColors provides if (darkTheme) DarkPod else LightPod,
        content = content,
    )
}
