package dev.korbi.helgeland

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * The "now playing" line in the wallpaper's media band: title and artist in hand
 * lettering plus ⏯ and ⏭. Tapping the title opens the player for everything else.
 */
@Composable
internal fun MediaLine(nowPlaying: NowPlaying, color: Color, actions: LauncherActions, modifier: Modifier = Modifier) {
    val noRipple = remember { MutableInteractionSource() }
    val shadow = if (color == Color.White) softShadow else null
    val text = listOfNotNull(nowPlaying.title, nowPlaying.artist).joinToString(" – ")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Wave(nowPlaying.playing, color, Modifier.clickable(noRipple, null, onClick = actions::mediaOpen))
        // A clear gap first, so the title fades out well before it reaches the bars.
        Spacer(Modifier.width(12.dp))
        // A new track slides in from the right while the old one leaves to the left, like flipping on.
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 3 } + fadeOut(tween(200)))
            },
            contentAlignment = Alignment.CenterStart,
            label = "track",
            modifier = Modifier
                .weight(1f)
                .clickable(noRipple, null, onClick = actions::mediaOpen)
                .fadeEdges(),
        ) { title ->
            // Long titles drift by while the music plays, like a car radio; paused, they rest at the start.
            Text(
                title,
                maxLines = 1,
                overflow = if (nowPlaying.playing) TextOverflow.Clip else TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = IndieFlower, fontSize = 18.sp, color = color, shadow = shadow),
                modifier = if (nowPlaying.playing) {
                    Modifier.basicMarquee(
                        iterations = Int.MAX_VALUE,
                        initialDelayMillis = 2_000,
                        repeatDelayMillis = 3_000,
                        spacing = MarqueeSpacing(48.dp),
                        velocity = 26.dp,
                    ).padding(start = FADE) // rests clear of the left fade, scrolls into it
                } else {
                    Modifier.padding(start = FADE)
                },
            )
        }
        Crossfade(nowPlaying.playing, animationSpec = tween(180), label = "playPause") { playing ->
            MediaButton(color, stringResource(if (playing) R.string.cd_pause else R.string.cd_play), actions::mediaPlayPause) {
                if (playing) drawPauseGlyph(it) else drawPlayGlyph(it)
            }
        }
        MediaButton(color, stringResource(R.string.cd_next), actions::mediaNext) { drawNextGlyph(it) }
    }
}

private val FADE = 10.dp

/** Lets the scrolling title fade in and out instead of being cut off at its edges. */
private fun Modifier.fadeEdges() = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = FADE.toPx().coerceAtMost(size.width / 2)
        drawRect(
            Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), startX = 0f, endX = fade),
            size = Size(fade, size.height),
            blendMode = BlendMode.DstIn,
        )
        drawRect(
            Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), startX = size.width - fade, endX = size.width),
            topLeft = Offset(size.width - fade, 0f),
            size = Size(fade, size.height),
            blendMode = BlendMode.DstIn,
        )
    }

/** A few equalizer bars that dance while music plays and settle when it's paused. */
@Composable
private fun Wave(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val energy by animateFloatAsState(if (playing) 1f else 0f, tween(400), label = "waveEnergy")
    val phase = if (playing || energy > 0f) {
        val transition = rememberInfiniteTransition(label = "wave")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1_200, easing = LinearEasing)), label = "wavePhase").value
    } else {
        0f
    }
    Canvas(modifier.size(width = 16.dp, height = 14.dp)) {
        val bars = 4
        val gap = size.width * 0.12f
        val barWidth = (size.width - gap * (bars - 1)) / bars
        for (i in 0 until bars) {
            // Each bar runs at its own speed and offset so the motion never looks like a loop.
            val swing = 0.5f + 0.5f * sin((phase * WAVE_SPEEDS[i] + i * 0.29f) * 2 * PI.toFloat())
            val height = size.height * (0.22f + 0.78f * energy * swing)
            drawRoundRect(
                color,
                topLeft = Offset(i * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
    }
}

/** Whole numbers, so every bar is back at its start when the phase wraps. */
private val WAVE_SPEEDS = intArrayOf(2, 3, 1, 2)

@Composable
private fun MediaButton(color: Color, label: String, onClick: () -> Unit, glyph: DrawScope.(Color) -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clickable(noRipple, null, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
    ) {
        Canvas(Modifier.size(16.dp)) { glyph(color) }
    }
}

private fun DrawScope.drawPlayGlyph(color: Color) {
    val w = size.width
    val h = size.height
    drawPath(
        Path().apply {
            moveTo(w * 0.22f, h * 0.1f)
            lineTo(w * 0.9f, h * 0.5f)
            lineTo(w * 0.22f, h * 0.9f)
            close()
        },
        color,
    )
}

private fun DrawScope.drawPauseGlyph(color: Color) {
    val bar = Size(size.width * 0.24f, size.height * 0.8f)
    val radius = CornerRadius(bar.width / 3)
    drawRoundRect(color, Offset(size.width * 0.16f, size.height * 0.1f), bar, radius)
    drawRoundRect(color, Offset(size.width * 0.6f, size.height * 0.1f), bar, radius)
}

private fun DrawScope.drawNextGlyph(color: Color) {
    val w = size.width
    val h = size.height
    drawPath(
        Path().apply {
            moveTo(w * 0.08f, h * 0.12f)
            lineTo(w * 0.66f, h * 0.5f)
            lineTo(w * 0.08f, h * 0.88f)
            close()
        },
        color,
    )
    drawRoundRect(color, Offset(w * 0.72f, h * 0.12f), Size(w * 0.16f, h * 0.76f), CornerRadius(w * 0.05f))
}
