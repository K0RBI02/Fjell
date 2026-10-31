package dev.korbi.helgeland

import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

internal enum class Press { Tap, Cancel, Long }

/** What sits in a home grid slot. */
internal sealed interface GridItem {
    val key: String

    data class App(val entry: AppEntry) : GridItem {
        override val key get() = entry.key
    }

    /** The button that opens the stack, for getting there without swiping. */
    data object Stapel : GridItem {
        override val key = GridSlots.STAPEL_KEY
    }

    /** Our own search bar, [span] slots wide. */
    data class Search(val span: Int) : GridItem {
        override val key = GridSlots.SEARCH_KEY
    }

    /** A slot the search bar reaches into from the left. */
    data object Covered : GridItem {
        override val key = GridSlots.COVERED
    }
}

/**
 * Fixed 4x3 slots. Tap launches; long-press and drag moves an app to another
 * slot (swapping with whatever is there); long-press without moving opens the menu.
 */
@Composable
internal fun AppGrid(
    slots: List<GridItem?>,
    actions: LauncherActions,
    stapelOpensSettings: Boolean,
    searchMic: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val columns = MainActivity.GRID_COLUMNS
    val rows = MainActivity.GRID_SIZE / columns
    var dragFrom by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var menuSlot by remember { mutableStateOf<Int?>(null) }
    val haptics = LocalHapticFeedback.current
    // The gesture handlers below outlive recompositions, so they must read these fresh.
    val opensSettings by rememberUpdatedState(stapelOpensSettings)
    val searchHasMic by rememberUpdatedState(searchMic)
    val openDrawer by rememberUpdatedState(onOpenDrawer)
    val openSettings by rememberUpdatedState(onOpenSettings)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cellW = constraints.maxWidth / columns.toFloat()
        val cellH = constraints.maxHeight / rows.toFloat()
        val cellSize = DpSize(maxWidth / columns, maxHeight / rows)
        // Fewer rows look lonely at the normal size, so they grow a little (not while dragging,
        // when the full grid opens up again).
        val occupied = slots.map { it != null }
        val growth = when (GridSlots.usedRowSpan(occupied, columns)) {
            2 -> 1.15f
            1 -> 1.28f
            else -> 1f
        }
        val iconScale by animateFloatAsState(if (dragFrom != null) 1f else growth, tween(220), label = "iconScale")
        val maxIconSize = minOf(48.dp * 1.28f, cellSize.height * 0.92f, cellSize.width * 0.8f)
        val iconSize = minOf(48.dp * iconScale, maxIconSize)
        val slotShape = RoundedCornerShape(36)

        // Fully empty rows at the top or bottom don't count: the rest is centred vertically,
        // in whole or half rows. While dragging, all rows open up so empty ones take drops.
        val restShift = GridSlots.centeringShift(occupied, columns)
        val shift by animateFloatAsState(if (dragFrom != null) 0f else restShift, tween(220), label = "gridShift")
        // Where the grid sat when the current drag began, to keep the dragged icon under the finger.
        var dragStartShift by remember { mutableFloatStateOf(0f) }

        fun slotUnder(from: Int, offset: Offset): Int {
            val col = (from % columns + (offset.x / cellW).roundToInt()).coerceIn(0, columns - 1)
            val row = (from / columns + ((offset.y + dragStartShift * cellH) / cellH).roundToInt()).coerceIn(0, rows - 1)
            return row * columns + col
        }
        val hover = dragFrom?.let { slotUnder(it, dragOffset) }
        // A light tick each time the drag moves onto another slot.
        var lastHover by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(hover) {
            if (hover != null && lastHover != null && hover != lastHover) {
                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
            lastHover = hover
        }

        fun Modifier.atSlot(i: Int) = offset { IntOffset(((i % columns) * cellW).roundToInt(), ((i / columns + shift) * cellH).roundToInt()) }

        if (dragFrom != null) {
            slots.indices.forEach { i ->
                Box(Modifier.atSlot(i).size(cellSize), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(iconSize).border(1.5.dp, Color.White.copy(alpha = if (i == hover) 0.95f else 0.35f), slotShape))
                }
            }
        }

        slots.forEachIndexed { i, item ->
            if (item == null || item == GridItem.Covered) return@forEachIndexed
            val isDragged = dragFrom == i
            val span = (item as? GridItem.Search)?.span ?: 1
            Box(
                Modifier
                    .atSlot(i)
                    .size(cellSize.width * span, cellSize.height)
                    .zIndex(if (isDragged) 1f else 0f),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .graphicsLayer {
                            if (isDragged) {
                                translationX = dragOffset.x
                                translationY = dragOffset.y + (dragStartShift - shift) * cellH
                                scaleX = 1.12f
                                scaleY = 1.12f
                            }
                        }
                        .pointerInput(i, item.key) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                // Keeps the home screen's own long-press (settings) from firing too.
                                down.consume()
                                var press = Press.Long
                                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    press = if (waitForUpOrCancellation() != null) Press.Tap else Press.Cancel
                                }
                                when (press) {
                                    Press.Tap -> when (item) {
                                        is GridItem.App -> actions.launch(item.entry)
                                        GridItem.Stapel -> if (opensSettings) openSettings() else openDrawer()
                                        // The mic owns a square at the bar's right end.
                                        is GridItem.Search ->
                                            if (searchHasMic && down.position.x > size.width - size.height) actions.openVoiceSearch() else actions.openWebSearch()
                                        GridItem.Covered -> Unit
                                    }
                                    Press.Cancel -> Unit
                                    Press.Long -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dragStartShift = shift
                                        dragFrom = i
                                        dragOffset = Offset.Zero
                                        var moved = false
                                        while (true) {
                                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            dragOffset += change.positionChange()
                                            change.consume()
                                            if (dragOffset.getDistance() > viewConfiguration.touchSlop) moved = true
                                        }
                                        if (moved) {
                                            val target = slotUnder(i, dragOffset)
                                            if (target != i) actions.moveInGrid(i, target)
                                        } else {
                                            menuSlot = i
                                        }
                                        dragFrom = null
                                        dragOffset = Offset.Zero
                                    }
                                }
                            }
                        },
                ) {
                    when (item) {
                        is GridItem.App -> {
                            AppIcon(item.entry, iconSize, actions, renderSize = maxIconSize)
                            AppMenu(menuSlot == i, onDismiss = { menuSlot = null }, app = item.entry, inGrid = true, actions = actions)
                        }
                        GridItem.Stapel -> {
                            StapelIcon(iconSize)
                            if (menuSlot == i) {
                                StapelMenuPopup(
                                    opensSettings = stapelOpensSettings,
                                    iconSize = iconSize,
                                    actions = actions,
                                    onOpenSettings = onOpenSettings,
                                    onDismiss = { menuSlot = null },
                                )
                            }
                        }
                        is GridItem.Search -> {
                            SearchBar(searchMic, Modifier.size(cellSize.width * span - (cellSize.width - iconSize), iconSize))
                            if (menuSlot == i) SearchMenuPopup(span, searchMic, actions, onDismiss = { menuSlot = null })
                        }
                        GridItem.Covered -> Unit
                    }
                }
            }
        }
    }
}

/** The home search bar: a long bubble with a magnifier, a hand-written hint and an optional mic. */
@Composable
private fun SearchBar(mic: Boolean, modifier: Modifier) {
    val dark = isSystemInDarkTheme()
    val bubble = if (dark) Color(0xFF1B1B1F) else Color(0xFFF3EFF4)
    val glyph = if (dark) Color(0xFFF3EFF4) else Color(0xFF1B1B1F)
    val description = stringResource(R.string.cd_search)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(bubble, RoundedCornerShape(36))
            .semantics { contentDescription = description },
    ) {
        Spacer(Modifier.width(12.dp))
        SearchGlyph(glyph)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.search_hint), maxLines = 1, style = TextStyle(fontFamily = IndieFlower, fontSize = 19.sp, color = glyph.copy(alpha = 0.6f)), modifier = Modifier.weight(1f))
        if (mic) {
            // Drawn narrower than the bar is tall so the "search" hint still fits on a 2-wide bar;
            // the tap handler gives the mic a full square, which covers this box.
            Box(Modifier.fillMaxHeight().width(38.dp), contentAlignment = Alignment.Center) { MicGlyph(glyph) }
        } else {
            Spacer(Modifier.width(14.dp))
        }
    }
}

/** A small microphone. */
@Composable
fun MicGlyph(color: Color) {
    Canvas(Modifier.size(20.dp)) { drawMicGlyph(color) }
}

/** A bubble like the app icons, with a 3x3 grid of dots. */
@Composable
fun StapelIcon(size: Dp) {
    val dark = isSystemInDarkTheme()
    val bubble = if (dark) Color(0xFF1B1B1F) else Color(0xFFF3EFF4)
    val dot = if (dark) Color(0xFFF3EFF4) else Color(0xFF1B1B1F)
    val description = stringResource(R.string.cd_all_apps)
    Canvas(
        Modifier
            .size(size)
            .background(bubble, RoundedCornerShape(36))
            .semantics { contentDescription = description },
    ) {
        val step = this.size.minDimension * 0.18f
        val radius = this.size.minDimension * 0.045f
        for (row in -1..1) for (col in -1..1) {
            drawCircle(dot, radius, center + Offset(col * step, row * step))
        }
    }
}
