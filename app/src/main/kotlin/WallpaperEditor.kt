package dev.korbi.fjell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * Sets up one of the user's own wallpapers: the picture fills the screen as it will on home,
 * and a line marks where the calm area for the app grid begins (the media line sits just
 * above it). Ghost bubbles below the line show where the icons will land.
 */
@Composable
fun WallpaperEditor(scene: Scene, isNew: Boolean, actions: LauncherActions) {
    BackHandler { actions.closeWallpaperEditor() }
    var gridTop by remember(scene.id) { mutableFloatStateOf(scene.gridTop) }
    val painter = rememberScenePainter(scene)

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        WallpaperImage(scene, Modifier.fillMaxSize())

        // Mirror ContentScale.Crop to map between picture fractions and screen pixels.
        val image = painter.intrinsicSize
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val scale = max(screenW / image.width, screenH / image.height)
        val overflow = (image.height * scale - screenH) / 2
        fun toScreen(fraction: Float) = image.height * scale * fraction - overflow
        fun toFraction(y: Float) = (y + overflow) / (image.height * scale)
        val currentGridTop by rememberUpdatedState(gridTop)

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(scale, overflow) {
                    detectVerticalDragGestures { change, amount ->
                        change.consume()
                        gridTop = toFraction(toScreen(currentGridTop) + amount).coerceIn(MIN_GRID_TOP, MAX_GRID_TOP)
                    }
                },
        ) {
            val lineY = toScreen(gridTop)
            val bandTop = toScreen(gridTop - Scene.MEDIA_BAND)
            // Where the media line will sit.
            drawRect(Color.White.copy(alpha = 0.16f), Offset(0f, bandTop), Size(size.width, lineY - bandTop))
            // Ghost grid: two rows of four bubbles, laid out like the home grid.
            val side = 12.dp.toPx()
            val cell = (size.width - 2 * side) / 4
            val bubble = cell * 0.62f
            for (row in 0 until 2) {
                for (col in 0 until 4) {
                    val x = side + cell * col + (cell - bubble) / 2
                    val y = lineY + 12.dp.toPx() + 16.dp.toPx() + row * (bubble + 18.dp.toPx())
                    drawRoundRect(Color.White.copy(alpha = 0.45f), Offset(x, y), Size(bubble, bubble), CornerRadius(bubble * 0.36f))
                }
            }
            // The line itself with a grip in the middle.
            drawLine(Color(0xFFE8E2D0), Offset(0f, lineY), Offset(size.width, lineY), 2.dp.toPx())
            val grip = 44.dp.toPx()
            drawLine(Color(0xFFE8E2D0), Offset(size.width / 2 - grip / 2, lineY), Offset(size.width / 2 + grip / 2, lineY), 6.dp.toPx(), StrokeCap.Round)
        }

        Text(
            stringResource(R.string.wallpaper_editor_hint),
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = Lato, fontSize = 15.sp, color = Color.White),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 16.dp, start = 24.dp, end = 24.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xB3101422))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            if (!isNew) EditorButton(stringResource(R.string.delete), Color(0xFFE8A39C)) { actions.deleteWallpaper(scene) }
            EditorButton(stringResource(R.string.cancel), Color.White.copy(alpha = 0.8f)) { actions.closeWallpaperEditor() }
            EditorButton(stringResource(R.string.save), Color.White, strong = true) {
                actions.saveWallpaper(scene.copy(gridTop = gridTop, mediaTop = gridTop - Scene.MEDIA_BAND, mediaBottom = gridTop))
            }
        }
    }
}

@Composable
private fun EditorButton(label: String, color: Color, strong: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        style = TextStyle(fontFamily = Lato, fontSize = 16.sp, color = if (strong) Ink else color),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (strong) Color(0xFFF3EFF4) else Color(0xB3101422))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
    )
}

private const val MIN_GRID_TOP = 0.45f
private const val MAX_GRID_TOP = 0.85f
