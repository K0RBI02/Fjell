package dev.korbi.helgeland

import android.app.Activity
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import java.time.LocalDateTime
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
    temperature: Int?,
    iconVersion: Int,
    globalIconStyle: IconStyle,
    iconPacks: List<IconSource>,
    stapelOpensSettings: Boolean,
    searchOptions: SearchBarOptions,
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
        // Both the sky and the drawer are dark enough for light status bar icons.
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }

    val menuColors = lightColorScheme(surface = PopupColor, surfaceContainer = PopupColor, onSurface = Ink)
    MaterialTheme(colorScheme = menuColors) {
        CompositionLocalProvider(
            LocalIconVersion provides iconVersion,
            LocalOpenIconPicker provides { app -> iconPickerFor = app },
        ) {
            Box(Modifier.fillMaxSize()) {
                WallpaperLayer(blurred = drawerOpen)

                val homeAlpha by animateFloatAsState(if (drawerOpen) 0f else 1f, label = "homeAlpha")
                Home(
                    apps = apps,
                    gridKeys = gridKeys,
                    temperature = temperature,
                    actions = actions,
                    onOpenDrawer = { drawerOpen = true },
                    onOpenSettings = { settingsOpen = true },
                    stapelOpensSettings = stapelOpensSettings,
                    searchOptions = searchOptions,
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
                        actions = actions,
                        onClose = { drawerOpen = false },
                    )
                }
            }
            if (settingsOpen) {
                SettingsPopup(
                    sampleApp = gridKeys.firstNotNullOfOrNull { key -> apps.firstOrNull { it.key == key } } ?: apps.firstOrNull(),
                    globalStyle = globalIconStyle,
                    iconPacks = iconPacks,
                    stapelShown = MainActivity.STAPEL_KEY in gridKeys,
                    stapelOpensSettings = stapelOpensSettings,
                    searchShown = GridSlots.SEARCH_KEY in gridKeys,
                    searchOptions = searchOptions,
                    actions = actions,
                    onDismiss = { settingsOpen = false },
                )
            }
            iconPickerFor?.let { app ->
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

@Composable
private fun WallpaperLayer(blurred: Boolean) {
    val blur by animateDpAsState(if (blurred) 24.dp else 0.dp, tween(350), label = "blur")
    Image(
        painter = painterResource(R.drawable.wp_mountain),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().blur(blur),
    )
}

/** Where the wallpaper's bottom lilac band starts, as a fraction of the image height. */
private const val BAND_TOP = 0.6998f

@Composable
private fun Home(
    apps: List<AppEntry>,
    gridKeys: List<String?>,
    temperature: Int?,
    actions: LauncherActions,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    stapelOpensSettings: Boolean,
    searchOptions: SearchBarOptions,
    modifier: Modifier = Modifier,
) {
    val openDrawer by rememberUpdatedState(onOpenDrawer)
    val openSettings by rememberUpdatedState(onOpenSettings)
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
    val wallpaperSize = painterResource(R.drawable.wp_mountain).intrinsicSize

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
                            dy > threshold -> actions.expandNotifications()
                        }
                    },
                )
            }
            .pointerInput(Unit) { detectTapGestures(onLongPress = { openSettings() }) },
    ) {
        Clock(
            color = Color.White,
            temperature = temperature,
            actions = actions,
            modifier = Modifier.statusBarsPadding().padding(start = 30.dp, top = 64.dp),
        )

        // Mirror ContentScale.Crop to find where the lilac band lands on this screen.
        val scale = max(constraints.maxWidth / wallpaperSize.width, constraints.maxHeight / wallpaperSize.height)
        val overflow = (wallpaperSize.height * scale - constraints.maxHeight) / 2
        val bandTop = wallpaperSize.height * scale * BAND_TOP - overflow
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
                    "hochwischen · app gedrückt halten → zum startbildschirm",
                    style = TextStyle(fontFamily = IndieFlower, fontSize = 18.sp, color = Color.White, shadow = softShadow),
                )
            } else {
                AppGrid(
                    slots = slots,
                    actions = actions,
                    stapelOpensSettings = stapelOpensSettings,
                    searchOptions = searchOptions,
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
    searchOptions: SearchBarOptions,
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
    val searchButtons by rememberUpdatedState(searchOptions.trailing)
    val openDrawer by rememberUpdatedState(onOpenDrawer)
    val openSettings by rememberUpdatedState(onOpenSettings)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cellW = constraints.maxWidth / columns.toFloat()
        val cellH = constraints.maxHeight / rows.toFloat()
        val cellSize = DpSize(maxWidth / columns, maxHeight / rows)
        val iconSize = minOf(48.dp, cellSize.height * 0.74f, cellSize.width * 0.74f)
        val slotShape = RoundedCornerShape(36)

        fun slotUnder(from: Int, offset: Offset): Int {
            val col = (from % columns + (offset.x / cellW).roundToInt()).coerceIn(0, columns - 1)
            val row = (from / columns + (offset.y / cellH).roundToInt()).coerceIn(0, rows - 1)
            return row * columns + col
        }
        val hover = dragFrom?.let { slotUnder(it, dragOffset) }

        fun Modifier.atSlot(i: Int) = offset { IntOffset(((i % columns) * cellW).roundToInt(), ((i / columns) * cellH).roundToInt()) }

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
                                translationY = dragOffset.y
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
                                        // Mic and Lens each own a square at the bar's right end.
                                        is GridItem.Search -> {
                                            val fromRight = ((size.width - down.position.x) / size.height).toInt()
                                            when (searchButtons.getOrNull(fromRight)) {
                                                SearchButton.Mic -> actions.openVoiceSearch()
                                                SearchButton.Lens -> actions.openLens()
                                                null -> actions.openWebSearch()
                                            }
                                        }
                                        GridItem.Covered -> Unit
                                    }
                                    Press.Cancel -> Unit
                                    Press.Long -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
                            AppIcon(item.entry, iconSize, actions)
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
                            SearchBar(searchOptions, span, Modifier.size(cellSize.width * span - (cellSize.width - iconSize), iconSize))
                            if (menuSlot == i) SearchMenuPopup(span, searchOptions, actions, onDismiss = { menuSlot = null })
                        }
                        GridItem.Covered -> Unit
                    }
                }
            }
        }
    }
}

enum class SearchButton { Mic, Lens }

data class SearchBarOptions(val mic: Boolean, val lens: Boolean) {
    /** Buttons at the bar's right end, listed from the right edge inwards. */
    val trailing: List<SearchButton> get() = listOfNotNull(SearchButton.Mic.takeIf { mic }, SearchButton.Lens.takeIf { lens })
}

/** The home search bar: a long bubble with a magnifier, a hand-written hint and optional mic and Lens. */
@Composable
private fun SearchBar(options: SearchBarOptions, span: Int, modifier: Modifier) {
    val dark = isSystemInDarkTheme()
    val bubble = if (dark) Color(0xFF1B1B1F) else Color(0xFFF3EFF4)
    val glyph = if (dark) Color(0xFFF3EFF4) else Color(0xFF1B1B1F)
    val buttons = options.trailing
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(bubble, RoundedCornerShape(36))
            .semantics { contentDescription = "Suche" },
    ) {
        Spacer(Modifier.width(14.dp))
        SearchGlyph(glyph)
        Spacer(Modifier.width(10.dp))
        // On a 2-wide bar with both buttons there's no room left for the hint.
        Box(Modifier.weight(1f)) {
            if (span > 2 || buttons.size < 2) {
                Text("suchen", maxLines = 1, style = TextStyle(fontFamily = IndieFlower, fontSize = 19.sp, color = glyph.copy(alpha = 0.6f)))
            }
        }
        // Each button sits in a square as tall as the bar; the tap handler relies on that.
        buttons.asReversed().forEach { button ->
            Box(Modifier.fillMaxHeight().aspectRatio(1f), contentAlignment = Alignment.Center) {
                when (button) {
                    SearchButton.Mic -> MicGlyph(glyph)
                    SearchButton.Lens -> LensGlyph(glyph)
                }
            }
        }
        if (buttons.isEmpty()) Spacer(Modifier.width(14.dp))
    }
}

/** Google Lens: four corner brackets around a dot. */
@Composable
fun LensGlyph(color: Color) {
    Canvas(Modifier.size(20.dp)) { drawLensGlyph(color) }
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
            .semantics { contentDescription = "Alle Apps" },
    ) {
        val step = this.size.minDimension * 0.18f
        val radius = this.size.minDimension * 0.045f
        for (row in -1..1) for (col in -1..1) {
            drawCircle(dot, radius, center + Offset(col * step, row * step))
        }
    }
}

@Composable
private fun Clock(color: Color, temperature: Int?, actions: LauncherActions, modifier: Modifier = Modifier) {
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
    val date = now.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    val subline = if (temperature != null) "$date | $temperature°C" else date
    val noRipple = remember { MutableInteractionSource() }

    Column(modifier) {
        Text(
            text = time,
            style = TextStyle(fontFamily = IndieFlower, fontSize = 76.sp, color = color, shadow = softShadow),
            modifier = Modifier.clickable(noRipple, null) { actions.openClock() },
        )
        Text(
            text = subline,
            style = TextStyle(fontFamily = IndieFlower, fontSize = 21.sp, color = color, shadow = softShadow),
            modifier = Modifier.padding(start = 6.dp).offset(y = (-18).dp).clickable(noRipple, null) { actions.openCalendar() },
        )
    }
}

@Composable
private fun Drawer(
    apps: List<AppEntry>,
    gridKeys: List<String?>,
    actions: LauncherActions,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // The stack holds everything not on the home grid; search covers all apps.
    val filtered = remember(apps, gridKeys, query) {
        val q = query.trim()
        if (q.isEmpty()) apps.filter { it.key !in gridKeys } else apps.filter { it.label.contains(q, ignoreCase = true) }
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
    val sections = remember(filtered, query) {
        if (query.isBlank()) filtered.groupBy { sectionLetter(it.label) } else null
    }
    val headerIndex = remember(sections) {
        var index = 0
        sections.orEmpty().mapValues { (_, group) -> index.also { index += group.size + 1 } }
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
            keyboardActions = KeyboardActions(onGo = { filtered.firstOrNull()?.let(launchAndClose) }),
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
                            Text("suchen", style = TextStyle(fontFamily = Lato, fontSize = 18.sp, color = Color.White.copy(alpha = 0.55f)))
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
                } else {
                    sections.forEach { (letter, group) ->
                        item(key = "section:$letter") { SectionHeader(letter) }
                        items(group, key = { it.key }) { appRow(it) }
                    }
                }
            }
            if (sections != null) {
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
private fun sectionLetter(label: String): String {
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

@Composable
fun AppIcon(app: AppEntry, size: Dp, actions: LauncherActions) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val version = LocalIconVersion.current
    val bitmap by produceState<ImageBitmap?>(null, app.key, px, version) { value = actions.icon(app, px) }
    Box(Modifier.size(size)) {
        bitmap?.let { Image(it, contentDescription = app.label, modifier = Modifier.fillMaxSize()) }
    }
}
