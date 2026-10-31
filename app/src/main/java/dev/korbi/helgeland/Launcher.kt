package dev.korbi.helgeland

import android.app.Activity
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import java.text.Normalizer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val Lato = FontFamily(
    Font(R.font.lato_thin, FontWeight.Thin),
    Font(R.font.lato_light, FontWeight.Light),
    Font(R.font.lato_regular, FontWeight.Normal),
)

/** Same hand-drawn face as the original KLWP setup. */
val IndieFlower = FontFamily(Font(R.font.indie_flower))

private val softShadow = Shadow(Color(0x40000000), Offset(0f, 2f), blurRadius = 12f)

/** Changes whenever icons need re-rendering (icon source switched, custom picture set, ...). */
private val LocalIconVersion = compositionLocalOf { 0 }

/** Opens the per-app icon page from any app menu. */
private val LocalOpenIconPicker = staticCompositionLocalOf<(AppEntry) -> Unit> { {} }

@Composable
fun Launcher(
    apps: List<AppEntry>,
    gridKeys: List<String?>,
    recentKeys: List<String>,
    launchCounts: Map<String, Int>,
    hiddenKeys: Set<String>,
    temperature: Int?,
    nextAlarm: Long?,
    iconVersion: Int,
    globalIconStyle: IconStyle,
    iconPacks: List<IconSource>,
    settings: LauncherSettings,
    nowPlaying: NowPlaying?,
    homePresses: Int,
    actions: LauncherActions,
) {
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var iconPickerFor by remember { mutableStateOf<AppEntry?>(null) }
    LaunchedEffect(homePresses) {
        drawerOpen = false
        settingsOpen = false
        iconPickerFor = null
    }
    BackHandler(drawerOpen) { drawerOpen = false }

    val view = LocalView.current
    SideEffect {
        // The drawer is always dark; on home it depends on the scene's sky.
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = settings.scene.lightSky && !drawerOpen
    }
    val haptics = if (settings.haptics) LocalHapticFeedback.current else NoHaptics

    val menuColors = lightColorScheme(surface = PopupColor, surfaceContainer = PopupColor, onSurface = Ink)
    MaterialTheme(colorScheme = menuColors) {
        CompositionLocalProvider(
            LocalIconVersion provides iconVersion,
            LocalOpenIconPicker provides { app -> iconPickerFor = app },
            LocalHapticFeedback provides haptics,
        ) {
            Box(Modifier.fillMaxSize()) {
                WallpaperLayer(settings.scene, blurred = drawerOpen)

                val homeAlpha by animateFloatAsState(if (drawerOpen) 0f else 1f, label = "homeAlpha")
                Home(
                    apps = apps,
                    gridKeys = gridKeys,
                    temperature = temperature.takeIf { settings.showWeather },
                    nextAlarm = nextAlarm.takeIf { settings.showAlarm },
                    nowPlaying = nowPlaying.takeIf { settings.media },
                    settings = settings,
                    actions = actions,
                    onOpenDrawer = { drawerOpen = true },
                    onOpenSettings = { settingsOpen = true },
                    modifier = Modifier.alpha(homeAlpha),
                )

                AnimatedVisibility(
                    visible = drawerOpen,
                    enter = slideInVertically { it / 3 } + fadeIn(),
                    exit = slideOutVertically { it / 3 } + fadeOut(),
                ) {
                    Drawer(
                        apps = apps,
                        gridKeys = gridKeys,
                        recentKeys = recentKeys,
                        launchCounts = launchCounts,
                        hiddenKeys = hiddenKeys,
                        settings = settings,
                        actions = actions,
                        onClose = { drawerOpen = false },
                    )
                }
            }
            PopupTransition(settingsOpen) {
                SettingsPopup(
                    sampleApp = gridKeys.firstNotNullOfOrNull { key -> apps.firstOrNull { it.key == key } } ?: apps.firstOrNull(),
                    globalStyle = globalIconStyle,
                    iconPacks = iconPacks,
                    settings = settings,
                    stapelShown = MainActivity.STAPEL_KEY in gridKeys,
                    searchShown = GridSlots.SEARCH_KEY in gridKeys,
                    hiddenApps = apps.filter { it.key in hiddenKeys },
                    actions = actions,
                    onDismiss = { settingsOpen = false },
                )
            }
            // Keep the last app around so the picker can still render while it animates out.
            var pickerApp by remember { mutableStateOf<AppEntry?>(null) }
            iconPickerFor?.let { pickerApp = it }
            PopupTransition(iconPickerFor != null) {
                pickerApp?.let { app ->
                    IconPickerPopup(
                        app = app,
                        globalStyle = globalIconStyle,
                        iconPacks = iconPacks,
                        actions = actions,
                        onClose = { iconPickerFor = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun WallpaperLayer(scene: Scene, blurred: Boolean) {
    val blur by animateDpAsState(if (blurred) 24.dp else 0.dp, tween(350), label = "blur")
    Image(
        painter = painterResource(scene.res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().blur(blur),
    )
}

@Composable
private fun Home(
    apps: List<AppEntry>,
    gridKeys: List<String?>,
    temperature: Int?,
    nextAlarm: Long?,
    nowPlaying: NowPlaying?,
    settings: LauncherSettings,
    actions: LauncherActions,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openDrawer by rememberUpdatedState(onOpenDrawer)
    val openSettings by rememberUpdatedState(onOpenSettings)
    val swipeDown by rememberUpdatedState(settings.swipeDown)
    val doubleTap by rememberUpdatedState(settings.doubleTap)
    fun run(action: DoubleTapAction) = when (action) {
        DoubleTapAction.Nothing -> Unit
        DoubleTapAction.Search -> actions.openWebSearch()
        DoubleTapAction.Stapel -> openDrawer()
        DoubleTapAction.Settings -> openSettings()
    }
    val density = LocalDensity.current
    val threshold = with(density) { 72.dp.toPx() }
    val slots = remember(apps, gridKeys) {
        val byKey = apps.associateBy { it.key }
        val searchSpan = GridSlots.search(gridKeys)?.count() ?: 0
        gridKeys.map { key ->
            when (key) {
                null -> null
                GridSlots.STAPEL_KEY -> GridItem.Stapel
                GridSlots.SEARCH_KEY -> GridItem.Search(searchSpan)
                GridSlots.COVERED -> GridItem.Covered
                else -> byKey[key]?.let(GridItem::App)
            }
        }
    }
    val scene = settings.scene
    val wallpaperSize = painterResource(scene.res).intrinsicSize

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var total = Offset.Zero
                detectDragGestures(
                    onDragStart = { total = Offset.Zero },
                    onDrag = { change, amount -> change.consume(); total += amount },
                    onDragEnd = {
                        val (dx, dy) = total
                        when {
                            abs(dx) > abs(dy) -> Unit
                            dy < -threshold -> openDrawer()
                            dy > threshold -> when (swipeDown) {
                                SwipeDownAction.Notifications -> actions.expandNotifications()
                                SwipeDownAction.Search -> actions.openWebSearch()
                                SwipeDownAction.Nothing -> Unit
                            }
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { openSettings() },
                    onDoubleTap = { run(doubleTap) },
                )
            },
    ) {
        Clock(
            color = scene.ink,
            large = settings.largeClock,
            temperature = temperature,
            nextAlarm = nextAlarm,
            actions = actions,
            modifier = Modifier.statusBarsPadding().padding(start = 30.dp, top = 64.dp),
        )

        // Mirror ContentScale.Crop to find where the scene's bands land on this screen.
        val scale = max(constraints.maxWidth / wallpaperSize.width, constraints.maxHeight / wallpaperSize.height)
        val overflow = (wallpaperSize.height * scale - constraints.maxHeight) / 2
        fun sceneY(fraction: Float) = wallpaperSize.height * scale * fraction - overflow
        val bandTop = sceneY(scene.gridTop)

        if (nowPlaying != null) {
            val top = sceneY(scene.mediaTop)
            MediaLine(
                nowPlaying = nowPlaying,
                color = scene.mediaInk,
                actions = actions,
                modifier = Modifier
                    .offset { IntOffset(0, top.roundToInt()) }
                    .fillMaxWidth()
                    .height(with(density) { (sceneY(scene.mediaBottom) - top).toDp() })
                    .padding(horizontal = 24.dp),
            )
        }
        val navBottom = WindowInsets.navigationBars.getBottom(density)
        val gridTop = bandTop + with(density) { 12.dp.toPx() }
        val gridHeight = constraints.maxHeight - gridTop - navBottom - with(density) { 8.dp.toPx() }

        Box(
            Modifier
                .offset { IntOffset(0, gridTop.roundToInt()) }
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(with(density) { gridHeight.toDp() }),
            contentAlignment = Alignment.Center,
        ) {
            if (slots.all { it == null }) {
                Text(
                    "swipe up · hold an app → add to home screen",
                    style = TextStyle(fontFamily = IndieFlower, fontSize = 18.sp, color = Color.White, shadow = softShadow),
                )
            } else {
                AppGrid(
                    slots = slots,
                    actions = actions,
                    stapelOpensSettings = settings.stapelOpensSettings,
                    searchMic = settings.searchMic,
                    onOpenDrawer = { openDrawer() },
                    onOpenSettings = { openSettings() },
                )
            }
        }
    }
}

private enum class Press { Tap, Cancel, Long }

/** What sits in a home grid slot. */
private sealed interface GridItem {
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
private fun AppGrid(
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

/** Stands in for the real haptics when vibration is switched off. */
private object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/**
 * The "now playing" line in the wallpaper's media band: title and artist in hand
 * lettering plus ⏯ and ⏭. Tapping the title opens the player for everything else.
 */
@Composable
private fun MediaLine(nowPlaying: NowPlaying, color: Color, actions: LauncherActions, modifier: Modifier = Modifier) {
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
            MediaButton(color, actions::mediaPlayPause) { if (playing) drawPauseGlyph(it) else drawPlayGlyph(it) }
        }
        MediaButton(color, actions::mediaNext) { drawNextGlyph(it) }
    }
}

@Composable
private fun MediaButton(color: Color, onClick: () -> Unit, glyph: DrawScope.(Color) -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(36.dp).clickable(noRipple, null, onClick = onClick),
    ) {
        Canvas(Modifier.size(16.dp)) { glyph(color) }
    }
}

private fun DrawScope.drawPlayGlyph(color: Color) {
    val w = size.width
    val h = size.height
    drawPath(Path().apply { moveTo(w * 0.22f, h * 0.1f); lineTo(w * 0.9f, h * 0.5f); lineTo(w * 0.22f, h * 0.9f); close() }, color)
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
    drawPath(Path().apply { moveTo(w * 0.08f, h * 0.12f); lineTo(w * 0.66f, h * 0.5f); lineTo(w * 0.08f, h * 0.88f); close() }, color)
    drawRoundRect(color, Offset(w * 0.72f, h * 0.12f), Size(w * 0.16f, h * 0.76f), CornerRadius(w * 0.05f))
}

/** The home search bar: a long bubble with a magnifier, a hand-written hint and an optional mic. */
@Composable
private fun SearchBar(mic: Boolean, modifier: Modifier) {
    val dark = isSystemInDarkTheme()
    val bubble = if (dark) Color(0xFF1B1B1F) else Color(0xFFF3EFF4)
    val glyph = if (dark) Color(0xFFF3EFF4) else Color(0xFF1B1B1F)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(bubble, RoundedCornerShape(36))
            .semantics { contentDescription = "Search" },
    ) {
        Spacer(Modifier.width(12.dp))
        SearchGlyph(glyph)
        Spacer(Modifier.width(8.dp))
        Text("search", maxLines = 1, style = TextStyle(fontFamily = IndieFlower, fontSize = 19.sp, color = glyph.copy(alpha = 0.6f)), modifier = Modifier.weight(1f))
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
    Canvas(
        Modifier
            .size(size)
            .background(bubble, RoundedCornerShape(36))
            .semantics { contentDescription = "All apps" },
    ) {
        val step = this.size.minDimension * 0.18f
        val radius = this.size.minDimension * 0.045f
        for (row in -1..1) for (col in -1..1) {
            drawCircle(dot, radius, center + Offset(col * step, row * step))
        }
    }
}

@Composable
private fun Clock(color: Color, large: Boolean, temperature: Int?, nextAlarm: Long?, actions: LauncherActions, modifier: Modifier = Modifier) {
    val context = LocalView.current.context
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    // Re-keyed on lifecycle so the time is fresh the moment we come back to the foreground.
    val now by produceState(LocalDateTime.now(), lifecycleState) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val timePattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val time = now.format(DateTimeFormatter.ofPattern(timePattern))
    val date = now.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    val subline = if (temperature != null) "$date | $temperature°C" else date
    // Only alarms within the next day are worth a glance on the home screen.
    val alarm = nextAlarm
        ?.takeIf { it - System.currentTimeMillis() in 0..ALARM_HORIZON_MS }
        ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(timePattern)) }
    val noRipple = remember { MutableInteractionSource() }
    val sublineStyle = TextStyle(fontFamily = IndieFlower, fontSize = 21.sp, color = color, shadow = softShadow)

    Column(modifier) {
        Text(
            text = time,
            style = TextStyle(fontFamily = IndieFlower, fontSize = if (large) 76.sp else 54.sp, color = color, shadow = softShadow),
            modifier = Modifier.clickable(noRipple, null) { actions.openClock() },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 6.dp).offset(y = (-18).dp),
        ) {
            Text(subline, style = sublineStyle, modifier = Modifier.clickable(noRipple, null) { actions.openCalendar() })
            if (alarm != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(noRipple, null) { actions.openClock() },
                ) {
                    Text(" | ", style = sublineStyle)
                    Canvas(Modifier.size(15.dp)) { drawBellGlyph(color) }
                    Text(" $alarm", style = sublineStyle)
                }
            }
        }
    }
}

private const val ALARM_HORIZON_MS = 24 * 60 * 60 * 1000L

/** A little alarm bell, hand-drawn style. */
internal fun DrawScope.drawBellGlyph(color: Color) {
    val stroke = size.minDimension * 0.11f
    val w = size.width
    val h = size.height
    val bell = Path().apply {
        moveTo(w * 0.18f, h * 0.74f)
        cubicTo(w * 0.26f, h * 0.6f, w * 0.2f, h * 0.18f, w * 0.5f, h * 0.16f)
        cubicTo(w * 0.8f, h * 0.18f, w * 0.74f, h * 0.6f, w * 0.82f, h * 0.74f)
        close()
    }
    drawPath(bell, color, style = Stroke(stroke, join = StrokeJoin.Round))
    drawCircle(color, stroke * 0.9f, Offset(w * 0.5f, h * 0.86f))
}

@Composable
private fun Drawer(
    apps: List<AppEntry>,
    gridKeys: List<String?>,
    recentKeys: List<String>,
    launchCounts: Map<String, Int>,
    hiddenKeys: Set<String>,
    settings: LauncherSettings,
    actions: LauncherActions,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val showHeaders = settings.drawerSections
    // The stack holds everything not on the home grid; search covers all apps.
    val filtered = remember(apps, gridKeys, hiddenKeys, query, launchCounts) {
        val q = query.trim()
        if (q.isEmpty()) {
            apps.filter { it.key !in gridKeys && it.key !in hiddenKeys }
        } else {
            // Search hits: most used first, ties alphabetical (stable sort).
            apps.filter { it.label.contains(q, ignoreCase = true) }.sortedByDescending { launchCounts[it.key] ?: 0 }
        }
    }
    val recentApps = remember(apps, gridKeys, hiddenKeys, recentKeys, query, settings.drawerRecent) {
        if (query.isNotBlank() || !settings.drawerRecent) return@remember emptyList()
        val byKey = apps.associateBy { it.key }
        recentKeys.filter { it !in gridKeys && it !in hiddenKeys }.mapNotNull(byKey::get).take(RECENT_SHOWN)
    }
    val focus = LocalFocusManager.current
    val close by rememberUpdatedState {
        focus.clearFocus()
        onClose()
    }
    val pullToClose = with(LocalDensity.current) { 110.dp.toPx() }
    // Pulling down while the list is already at the top closes the drawer.
    val closeOnPull = remember {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    pulled += available.y
                    if (pulled > pullToClose) {
                        pulled = 0f
                        close()
                    }
                } else if (consumed.y != 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                pulled = 0f
                return Velocity.Zero
            }
        }
    }
    val launchAndClose = { app: AppEntry ->
        actions.launch(app)
        close()
    }
    // Letter sections only while browsing; a search result list stays flat.
    // Within a letter, often-used apps come first; the sort is stable, so ties stay alphabetical.
    val sections = remember(filtered, query, launchCounts) {
        if (query.isBlank()) {
            filtered.groupBy { sectionLetter(it.label) }.mapValues { (_, group) -> group.sortedByDescending { launchCounts[it.key] ?: 0 } }
        } else {
            null
        }
    }
    // Where each letter starts in the list, for the alphabet strip. Without visible headers
    // that's simply the letter's first app.
    val headerIndex = remember(sections, recentApps.isEmpty(), showHeaders) {
        // The "recent" header and row sit above the first letter.
        var index = if (recentApps.isEmpty()) 0 else 2
        sections.orEmpty().mapValues { (_, group) -> index.also { index += group.size + if (showHeaders) 1 else 0 } }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val currentLetter by remember(headerIndex) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            headerIndex.entries.lastOrNull { it.value <= first }?.key
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0x99101422))
            .statusBarsPadding()
            .imePadding(),
    ) {
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(fontFamily = Lato, fontSize = 18.sp, color = Color.White),
            cursorBrush = SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = {
                    val first = filtered.firstOrNull()
                    if (first != null) {
                        launchAndClose(first)
                    } else if (query.isNotBlank()) {
                        actions.searchWeb(query.trim())
                        close()
                    }
                },
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(horizontal = 22.dp, vertical = 14.dp),
            decorationBox = { field ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchGlyph(Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.width(14.dp))
                    Box {
                        if (query.isEmpty()) {
                            Text("search", style = TextStyle(fontFamily = Lato, fontSize = 18.sp, color = Color.White.copy(alpha = 0.55f)))
                        }
                        field()
                    }
                }
            },
        )

        val appRow: @Composable (AppEntry) -> Unit = { app ->
            AppWithMenu(
                app = app,
                inGrid = app.key in gridKeys,
                actions = actions,
                onLaunch = { launchAndClose(app) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 28.dp, end = 40.dp, top = 9.dp, bottom = 9.dp),
                ) {
                    AppIcon(app, 42.dp, actions)
                    Spacer(Modifier.width(18.dp))
                    Text(app.label, style = TextStyle(fontFamily = Lato, fontSize = 18.sp, color = Color.White))
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(closeOnPull),
                contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            ) {
                if (sections == null) {
                    items(filtered, key = { it.key }) { appRow(it) }
                    item(key = "web") {
                        WebSearchRow(query.trim()) {
                            actions.searchWeb(query.trim())
                            close()
                        }
                    }
                } else {
                    if (recentApps.isNotEmpty()) {
                        item(key = "recent:header") { SectionHeader("recent") }
                        item(key = "recent:row") { RecentRow(recentApps, actions, onLaunch = launchAndClose) }
                    }
                    sections.forEach { (letter, group) ->
                        if (showHeaders) item(key = "section:$letter") { SectionHeader(letter) }
                        items(group, key = { it.key }) { appRow(it) }
                    }
                }
            }
            if (sections != null && settings.drawerIndex) {
                AlphabetIndex(
                    available = sections.keys,
                    current = currentLetter,
                    onSelect = { letter -> headerIndex[letter]?.let { scope.launch { listState.scrollToItem(it) } } },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .navigationBarsPadding()
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

private const val RECENT_SHOWN = 4

/** The last few apps started from the launcher, as a row of bubbles at the top of the stack. */
@Composable
private fun RecentRow(apps: List<AppEntry>, actions: LauncherActions, onLaunch: (AppEntry) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 40.dp, top = 6.dp, bottom = 4.dp)) {
        apps.forEach { app ->
            AppWithMenu(
                app = app,
                inGrid = false,
                actions = actions,
                onLaunch = { onLaunch(app) },
                modifier = Modifier.weight(1f),
            ) {
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                    AppIcon(app, 50.dp, actions)
                }
            }
        }
        repeat(RECENT_SHOWN - apps.size) { Spacer(Modifier.weight(1f)) }
    }
}

/** Last row of a stack search: hand the query to the web. */
@Composable
private fun WebSearchRow(query: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 28.dp, end = 40.dp, top = 9.dp, bottom = 9.dp),
    ) {
        Box(Modifier.size(42.dp).background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(36)), contentAlignment = Alignment.Center) {
            SearchGlyph(Color.White)
        }
        Spacer(Modifier.width(18.dp))
        Text(
            "Search the web: “$query”",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = Lato, fontSize = 18.sp, color = Color.White.copy(alpha = 0.85f)),
        )
    }
}

/** A small magnifying glass. */
@Composable
fun SearchGlyph(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val stroke = size.minDimension * 0.11f
        val radius = size.minDimension * 0.32f
        val lens = Offset(size.width * 0.42f, size.height * 0.42f)
        drawCircle(color, radius, lens, style = Stroke(stroke))
        val handleStart = lens + Offset(radius * 0.72f, radius * 0.72f)
        drawLine(color, handleStart, Offset(size.width * 0.9f, size.height * 0.9f), stroke, StrokeCap.Round)
    }
}

private val INDEX_LETTERS = listOf("#") + ('A'..'Z').map(Char::toString)

/** "Ärzte" -> "A", "2 Player Games" -> "#". */
internal fun sectionLetter(label: String): String {
    val first = Normalizer.normalize(label.take(1), Normalizer.Form.NFD).firstOrNull()?.uppercaseChar()
    return if (first != null && first in 'A'..'Z') first.toString() else "#"
}

@Composable
private fun SectionHeader(letter: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 28.dp, end = 40.dp, top = 14.dp, bottom = 4.dp),
    ) {
        Text(letter, style = TextStyle(fontFamily = IndieFlower, fontSize = 22.sp, color = Color.White.copy(alpha = 0.8f)))
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f).height(1.dp).background(Color.White.copy(alpha = 0.18f)))
    }
}

/**
 * Letter strip along the right edge. Tap or slide over it to jump; letters with
 * no apps jump to the next one that has some.
 */
@Composable
private fun AlphabetIndex(
    available: Set<String>,
    current: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val select by rememberUpdatedState(onSelect)
    val present by rememberUpdatedState(available)
    Column(
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(30.dp)
            .fillMaxHeight()
            .pointerInput(Unit) {
                var last: String? = null
                fun pick(y: Float) {
                    val i = (y / size.height * INDEX_LETTERS.size).toInt().coerceIn(INDEX_LETTERS.indices)
                    val target = INDEX_LETTERS.drop(i).firstOrNull { it in present }
                        ?: INDEX_LETTERS.take(i).lastOrNull { it in present }
                        ?: return
                    if (target == last) return
                    last = target
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    select(target)
                }
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    pick(down.position.y)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        pick(change.position.y)
                    }
                    last = null
                }
            },
    ) {
        INDEX_LETTERS.forEach { letter ->
            val alpha = when {
                letter == current -> 1f
                letter in available -> 0.7f
                else -> 0.25f
            }
            Text(
                letter,
                style = TextStyle(
                    fontFamily = Lato,
                    fontSize = 12.sp,
                    fontWeight = if (letter == current) FontWeight.Bold else FontWeight.Normal,
                    color = Color.White.copy(alpha = alpha),
                ),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppWithMenu(
    app: AppEntry,
    inGrid: Boolean,
    actions: LauncherActions,
    onLaunch: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box(
        modifier.combinedClickable(
            onClick = onLaunch,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                menuOpen = true
            },
        ),
    ) {
        content()
        AppMenu(menuOpen, onDismiss = { menuOpen = false }, app = app, inGrid = inGrid, actions = actions)
    }
}

@Composable
private fun AppMenu(expanded: Boolean, onDismiss: () -> Unit, app: AppEntry, inGrid: Boolean, actions: LauncherActions) {
    if (!expanded) return
    val openIconPicker = LocalOpenIconPicker.current
    AppMenuPopup(app, inGrid, actions, onOpenIconPicker = { openIconPicker(app) }, onDismiss = onDismiss)
}

/** [renderSize] is the resolution the bitmap is drawn at; pass a fixed one when [size] animates. */
@Composable
fun AppIcon(app: AppEntry, size: Dp, actions: LauncherActions, renderSize: Dp = size) {
    val px = with(LocalDensity.current) { renderSize.roundToPx() }
    val version = LocalIconVersion.current
    val bitmap by produceState<ImageBitmap?>(null, app.key, px, version) { value = actions.icon(app, px) }
    Box(Modifier.size(size)) {
        bitmap?.let { Image(it, contentDescription = app.label, modifier = Modifier.fillMaxSize()) }
    }
}
