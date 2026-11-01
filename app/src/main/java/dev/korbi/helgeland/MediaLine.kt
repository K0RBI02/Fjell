package dev.korbi.helgeland

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
        // A new track slides in from the right while the old one leaves to the left, like flipping on.
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (slideInHorizontally(tween(320)) { it / 3 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 3 } + fadeOut(tween(200)))
            },
            contentAlignment = Alignment.CenterStart,
            label = "track",
            modifier = Modifier.weight(1f).clickable(noRipple, null, onClick = actions::mediaOpen),
        ) { title ->
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontFamily = IndieFlower, fontSize = 18.sp, color = color, shadow = shadow),
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
