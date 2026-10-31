package dev.korbi.fjell

import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.cos
import kotlin.math.sin

private val DangerColor = Color(0xFFB0413A)

/**
 * A small card in the popup palette that floats right above its anchor (or below
 * it when there's no room) and grows out of it. Used for all long-press menus.
 */
@Composable
private fun MenuCard(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    var opensUpward by remember { mutableStateOf(true) }
    val positioner = remember(density) {
        val margin = with(density) { 14.dp.roundToPx() }
        val gap = with(density) { 10.dp.roundToPx() }
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val x = (anchorBounds.center.x - popupContentSize.width / 2)
                    .coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
                val above = anchorBounds.top - popupContentSize.height - gap
                opensUpward = above >= margin
                return IntOffset(x, if (opensUpward) above else anchorBounds.bottom + gap)
            }
        }
    }

    Popup(popupPositionProvider = positioner, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val appear by animateFloatAsState(if (shown) 1f else 0f, tween(180), label = "menu")
        Column(
            Modifier
                .width(236.dp)
                .graphicsLayer {
                    alpha = appear
                    val scale = 0.9f + 0.1f * appear
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, if (opensUpward) 1f else 0f)
                }
                .shadow(16.dp, RoundedCornerShape(26.dp), ambientColor = Ink, spotColor = Ink)
                .clip(RoundedCornerShape(26.dp))
                .background(PopupColor)
                .padding(vertical = 8.dp),
            content = content,
        )
    }
}

/** Long-press menu for an app. */
@Composable
fun AppMenuPopup(
    app: AppEntry,
    inGrid: Boolean,
    actions: LauncherActions,
    onOpenIconPicker: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dismissThen = { action: () -> Unit -> onDismiss(); action() }
    MenuCard(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            AppIcon(app, 36.dp, actions)
            Spacer(Modifier.width(12.dp))
            EditableName(app, onRename = { label -> dismissThen { actions.rename(app, label) } })
        }
        MenuDivider()
        MenuRow(if (inGrid) stringResource(R.string.menu_move_to_stack) else stringResource(R.string.menu_add_to_home), glyph = DrawScope::drawGridGlyph) { dismissThen { actions.toggleGrid(app) } }
        MenuRow(stringResource(R.string.menu_change_icon), glyph = DrawScope::drawIconGlyph) { dismissThen(onOpenIconPicker) }
        if (!inGrid) {
            val hidden = actions.isHidden(app)
            MenuRow(if (hidden) stringResource(R.string.menu_show_in_stack) else stringResource(R.string.menu_hide_from_stack), glyph = DrawScope::drawHideGlyph) {
                dismissThen { actions.setHidden(app, !hidden) }
            }
        }
        MenuRow(stringResource(R.string.menu_app_info), glyph = DrawScope::drawInfoGlyph) { dismissThen { actions.appInfo(app) } }
        MenuRow(stringResource(R.string.menu_uninstall), color = DangerColor, glyph = DrawScope::drawTrashGlyph) { dismissThen { actions.uninstall(app) } }
    }
}

/** Long-press menu for the 3x3 button: what it opens, and taking it off the grid. */
@Composable
fun StapelMenuPopup(
    opensSettings: Boolean,
    iconSize: Dp,
    actions: LauncherActions,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dismissThen = { action: () -> Unit -> onDismiss(); action() }
    MenuCard(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            StapelIcon(minOf(iconSize, 36.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.stapel_button), style = TextStyle(fontFamily = IndieFlower, fontSize = 22.sp, color = Ink))
        }
        MenuDivider()
        MenuRow(stringResource(R.string.menu_opens_stack), glyph = DrawScope::drawListGlyph, checked = !opensSettings) { actions.updateSettings { it.copy(stapelOpensSettings = false) } }
        MenuRow(stringResource(R.string.menu_opens_settings), glyph = DrawScope::drawGearGlyph, checked = opensSettings) { actions.updateSettings { it.copy(stapelOpensSettings = true) } }
        MenuDivider()
        MenuRow(stringResource(R.string.settings), glyph = DrawScope::drawGearGlyph) { dismissThen(onOpenSettings) }
        MenuRow(stringResource(R.string.menu_remove_from_home), color = DangerColor, glyph = DrawScope::drawTrashGlyph) { dismissThen { actions.setStapelShown(false) } }
    }
}

/** Long-press menu for the search bar: its width, and taking it off the grid. */
@Composable
fun SearchMenuPopup(span: Int, mic: Boolean, actions: LauncherActions, onDismiss: () -> Unit) {
    val dismissThen = { action: () -> Unit -> onDismiss(); action() }
    MenuCard(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Box(Modifier.size(36.dp).background(Color(0xFFF3EFF4), RoundedCornerShape(36)), contentAlignment = Alignment.Center) {
                SearchGlyph(Ink)
            }
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.search_bar), style = TextStyle(fontFamily = IndieFlower, fontSize = 22.sp, color = Ink))
        }
        MenuDivider()
        MenuRow(stringResource(R.string.menu_two_slots), glyph = DrawScope::drawNarrowGlyph, checked = span == 2) { dismissThen { actions.setSearchWidth(2) } }
        MenuRow(stringResource(R.string.menu_four_slots), glyph = DrawScope::drawWideGlyph, checked = span == 4) { dismissThen { actions.setSearchWidth(4) } }
        MenuDivider()
        MenuRow(stringResource(R.string.microphone), glyph = DrawScope::drawMicGlyph, checked = mic) { actions.updateSettings { it.copy(searchMic = !mic) } }
        MenuDivider()
        MenuRow(stringResource(R.string.menu_remove_from_home), color = DangerColor, glyph = DrawScope::drawTrashGlyph) { dismissThen { actions.setSearchShown(false) } }
    }
}

@Composable
private fun MenuDivider() {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().height(1.dp).background(Ink.copy(alpha = 0.1f)))
}

/** The app's name; tap it to type a new one. Enter or ✓ saves, an empty name restores the original. */
@Composable
private fun RowScope.EditableName(app: AppEntry, onRename: (String) -> Unit) {
    val nameStyle = TextStyle(fontFamily = IndieFlower, fontSize = 22.sp, color = Ink)
    var editing by remember { mutableStateOf(false) }
    if (!editing) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).clickable { editing = true },
        ) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = nameStyle, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            Canvas(Modifier.size(13.dp)) { drawPencilGlyph(Ink.copy(alpha = 0.4f)) }
        }
        return
    }

    var text by remember { mutableStateOf(TextFieldValue(app.label, TextRange(0, app.label.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        singleLine = true,
        textStyle = nameStyle,
        cursorBrush = SolidColor(Ink),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onRename(text.text) }),
        modifier = Modifier
            .weight(1f)
            .focusRequester(focus)
            .background(Ink.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
    Text(
        "✓",
        style = TextStyle(fontFamily = Lato, fontSize = 20.sp, color = Ink),
        modifier = Modifier.clickable { onRename(text.text) }.padding(start = 10.dp, end = 2.dp),
    )
}

@Composable
private fun MenuRow(label: String, color: Color = Ink, glyph: DrawScope.(Color) -> Unit, checked: Boolean = false, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Canvas(Modifier.size(18.dp)) { glyph(color.copy(alpha = 0.8f)) }
        Spacer(Modifier.width(16.dp))
        Text(label, style = TextStyle(fontFamily = Lato, fontSize = 16.sp, color = color), modifier = Modifier.weight(1f))
        if (checked) Text("✓", style = TextStyle(fontFamily = Lato, fontSize = 16.sp, color = color))
    }
}

// Tiny line glyphs, drawn rather than pulled from an icon library to match the hand-made look.

private fun DrawScope.drawPencilGlyph(color: Color) {
    val stroke = size.minDimension * 0.14f
    drawLine(color, Offset(size.width * 0.15f, size.height * 0.85f), Offset(size.width * 0.85f, size.height * 0.15f), stroke, StrokeCap.Round)
    drawLine(color, Offset(size.width * 0.1f, size.height * 0.9f), Offset(size.width * 0.3f, size.height * 0.9f), stroke, StrokeCap.Round)
}

fun DrawScope.drawMicGlyph(color: Color) {
    val stroke = size.minDimension * 0.1f
    val w = size.width
    val h = size.height
    drawRoundRect(color, Offset(w * 0.36f, h * 0.06f), Size(w * 0.28f, h * 0.5f), CornerRadius(w * 0.14f))
    drawArc(color, 0f, 180f, false, Offset(w * 0.2f, h * 0.2f), Size(w * 0.6f, h * 0.52f), style = Stroke(stroke, cap = StrokeCap.Round))
    drawLine(color, Offset(w * 0.5f, h * 0.72f), Offset(w * 0.5f, h * 0.92f), stroke, StrokeCap.Round)
}

private fun DrawScope.drawHideGlyph(color: Color) {
    val stroke = size.minDimension * 0.1f
    val w = size.width
    val h = size.height
    // An eye, struck through.
    drawArc(color, 200f, 140f, false, Offset(w * 0.05f, h * 0.22f), Size(w * 0.9f, h * 0.7f), style = Stroke(stroke, cap = StrokeCap.Round))
    drawArc(color, 20f, 140f, false, Offset(w * 0.05f, h * 0.08f), Size(w * 0.9f, h * 0.7f), style = Stroke(stroke, cap = StrokeCap.Round))
    drawCircle(color, w * 0.13f, center)
    drawLine(color, Offset(w * 0.12f, h * 0.88f), Offset(w * 0.88f, h * 0.12f), stroke, StrokeCap.Round)
}

private fun DrawScope.drawNarrowGlyph(color: Color) {
    val stroke = size.minDimension * 0.1f
    drawRoundRect(color, Offset(size.width * 0.22f, size.height * 0.32f), Size(size.width * 0.56f, size.height * 0.36f), CornerRadius(size.height * 0.18f), style = Stroke(stroke))
}

private fun DrawScope.drawWideGlyph(color: Color) {
    val stroke = size.minDimension * 0.1f
    drawRoundRect(color, Offset(stroke / 2, size.height * 0.32f), Size(size.width - stroke, size.height * 0.36f), CornerRadius(size.height * 0.18f), style = Stroke(stroke))
}

internal fun DrawScope.drawListGlyph(color: Color) {
    val stroke = size.minDimension * 0.1f
    for (row in 0..2) {
        val y = size.height * (0.2f + row * 0.3f)
        drawCircle(color, stroke * 0.7f, Offset(size.width * 0.12f, y))
        drawLine(color, Offset(size.width * 0.32f, y), Offset(size.width * 0.92f, y), stroke, StrokeCap.Round)
    }
}

internal fun DrawScope.drawGearGlyph(color: Color) {
    val stroke = size.minDimension * 0.1f
    val outer = size.minDimension * 0.46f
    val inner = size.minDimension * 0.3f
    for (i in 0 until 8) {
        val angle = Math.PI / 4 * i
        val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
        drawLine(color, center + dir * inner, center + dir * outer, stroke * 1.4f, StrokeCap.Round)
    }
    drawCircle(color, inner, center, style = Stroke(stroke))
    drawCircle(color, size.minDimension * 0.1f, center, style = Stroke(stroke))
}

internal fun DrawScope.drawGridGlyph(color: Color) {
    val step = size.minDimension * 0.36f
    val radius = size.minDimension * 0.08f
    for (row in -1..1) for (col in -1..1) drawCircle(color, radius, center + Offset(col * step, row * step))
}

internal fun DrawScope.drawIconGlyph(color: Color) {
    val stroke = size.minDimension * 0.1f
    val inset = stroke / 2
    drawRoundRect(color, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), CornerRadius(size.minDimension * 0.34f), style = Stroke(stroke))
    drawCircle(color, size.minDimension * 0.13f, center)
}

private fun DrawScope.drawInfoGlyph(color: Color) {
    val stroke = size.minDimension * 0.1f
    drawCircle(color, size.minDimension / 2 - stroke / 2, center, style = Stroke(stroke))
    drawLine(color, Offset(center.x, size.height * 0.44f), Offset(center.x, size.height * 0.72f), stroke, StrokeCap.Round)
    drawCircle(color, stroke * 0.65f, Offset(center.x, size.height * 0.29f))
}

private fun DrawScope.drawTrashGlyph(color: Color) {
    val stroke = size.minDimension * 0.1f
    val w = size.width
    val h = size.height
    drawLine(color, Offset(w * 0.12f, h * 0.22f), Offset(w * 0.88f, h * 0.22f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.38f, h * 0.08f), Offset(w * 0.62f, h * 0.08f), stroke, StrokeCap.Round)
    drawRoundRect(color, Offset(w * 0.22f, h * 0.3f), Size(w * 0.56f, h * 0.62f), CornerRadius(stroke), style = Stroke(stroke))
}
