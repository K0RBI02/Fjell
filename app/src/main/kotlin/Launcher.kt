package dev.korbi.fjell

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import java.time.LocalDateTime
import kotlinx.coroutines.delay

val Lato = FontFamily(
    Font(R.font.lato_thin, FontWeight.Thin),
    Font(R.font.lato_light, FontWeight.Light),
    Font(R.font.lato_regular, FontWeight.Normal),
)

/** Same hand-drawn face as the original KLWP setup. */
val IndieFlower = FontFamily(Font(R.font.indie_flower))

internal val softShadow = Shadow(Color(0x40000000), Offset(0f, 2f), blurRadius = 12f)

/** Changes whenever icons need re-rendering (icon source switched, custom picture set, ...). */
internal val LocalIconVersion = compositionLocalOf { 0 }

/** Opens the per-app icon page from any app menu. */
internal val LocalOpenIconPicker = staticCompositionLocalOf<(AppEntry) -> Unit> { {} }

@Composable
fun Launcher(
    apps: List<AppEntry>,
    gridKeys: List<String?>,
    recentKeys: List<String>,
    launchCounts: Map<String, Int>,
    hiddenKeys: Set<String>,
    weather: WeatherNow?,
    nextAlarm: Long?,
    iconVersion: Int,
    globalIconStyle: IconStyle,
    iconPacks: List<IconSource>,
    settings: LauncherSettings,
    nowPlaying: NowPlaying?,
    dotPackages: Set<String>,
    noDots: Set<String>,
    homePresses: Int,
    actions: LauncherActions,
    events: List<CalendarEvent> = emptyList(),
    place: Place? = null,
) {
    // With the automatic scene on, the wallpaper follows the clock; checked once a minute.
    val clock = LocalClock.current
    val now by produceState(LocalDateTime.now(clock), settings.autoScene) {
        while (settings.autoScene) {
            value = LocalDateTime.now(clock)
            delay(60_000L - clock.millis() % 60_000L)
        }
    }
    val chosenSettings = settings

    @Suppress("NAME_SHADOWING")
    val sun = remember(now.toLocalDate(), place) { place?.let { Sun.times(now.toLocalDate(), it, clock.zone) } }
    val settings = settings.copy(scene = settings.sceneAt(now, sun))
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var drawerSearch by remember { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var iconPickerFor by remember { mutableStateOf<AppEntry?>(null) }
    LaunchedEffect(homePresses) {
        drawerOpen = false
        drawerSearch = false
        settingsOpen = false
        iconPickerFor = null
    }
    BackHandler(drawerOpen) {
        drawerOpen = false
        drawerSearch = false
    }
    // Only the stack blurs the whole wallpaper; popups blur just what's behind their sheet.
    val overlayOpen = drawerOpen

    val view = LocalView.current
    SideEffect {
        // The drawer is always dark; on home it depends on the scene's sky.
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = settings.scene.lightSky && !overlayOpen
    }
    val haptics = if (settings.haptics) LocalHapticFeedback.current else NoHaptics

    val menuColors = lightColorScheme(surface = PopupColor, surfaceContainer = PopupColor, onSurface = Ink)
    MaterialTheme(colorScheme = menuColors) {
        CompositionLocalProvider(
            LocalIconVersion provides iconVersion,
            LocalOpenIconPicker provides { app -> iconPickerFor = app },
            LocalHapticFeedback provides haptics,
            LocalScene provides settings.scene,
        ) {
            Box(Modifier.fillMaxSize()) {
                WallpaperLayer(settings.scene, blurred = overlayOpen)

                val homeAlpha by animateFloatAsState(if (overlayOpen) 0f else 1f, label = "homeAlpha")
                Home(
                    apps = apps,
                    gridKeys = gridKeys,
                    weather = weather.takeIf { settings.showWeather },
                    nextAlarm = nextAlarm.takeIf { settings.showAlarm },
                    events = if (settings.showEvents) events else emptyList(),
                    nowPlaying = nowPlaying.takeIf { settings.media },
                    dotPackages = dotPackages,
                    noDots = noDots,
                    settings = settings,
                    actions = actions,
                    onOpenDrawer = { drawerOpen = true },
                    onOpenSearch = {
                        drawerSearch = true
                        drawerOpen = true
                    },
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
                        focusSearch = drawerSearch,
                        onClose = {
                            drawerOpen = false
                            drawerSearch = false
                        },
                    )
                }
            }
            PopupTransition(settingsOpen) {
                SettingsPopup(
                    sampleApp = gridKeys.firstNotNullOfOrNull { key -> apps.firstOrNull { it.key == key } } ?: apps.firstOrNull(),
                    globalStyle = globalIconStyle,
                    iconPacks = iconPacks,
                    settings = chosenSettings,
                    stapelShown = MainActivity.STAPEL_KEY in gridKeys,
                    searchShown = GridSlots.SEARCH_KEY in gridKeys,
                    hiddenApps = apps.filter { it.key in hiddenKeys },
                    actions = actions,
                    onDismiss = { settingsOpen = false },
                    sun = sun,
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
internal fun WallpaperLayer(scene: Scene, blurred: Boolean) {
    val blur by animateDpAsState(if (blurred) 24.dp else 0.dp, tween(350), label = "blur")
    Image(
        painter = painterResource(scene.res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().blur(blur),
    )
}

/** Stands in for the real haptics when vibration is switched off. */
internal object NoHaptics : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AppWithMenu(
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
internal fun AppMenu(expanded: Boolean, onDismiss: () -> Unit, app: AppEntry, inGrid: Boolean, actions: LauncherActions) {
    if (!expanded) return
    val openIconPicker = LocalOpenIconPicker.current
    AppMenuPopup(app, inGrid, actions, onOpenIconPicker = { openIconPicker(app) }, onDismiss = onDismiss)
}

/** [renderSize] is the resolution the bitmap is drawn at; pass a fixed one when [size] animates. */
@Composable
fun AppIcon(app: AppEntry, size: Dp, actions: LauncherActions, renderSize: Dp = size, ring: Boolean = false) {
    val px = with(LocalDensity.current) { renderSize.roundToPx() }
    val version = LocalIconVersion.current
    val bitmap by produceState<ImageBitmap?>(null, app.key, px, version) { value = actions.icon(app, px) }
    // New notifications: a hairline arc in the icon's own ink, hugging the top-right corner
    // just outside the bubble, from about the middle of the top edge to the middle of the right one.
    val ink = remember(bitmap) { bitmap?.let { Color(IconRender.inkOf(it.asAndroidBitmap())) } }
    val ringAlpha by animateFloatAsState(if (ring && ink != null) 0.6f else 0f, tween(300), label = "ring")
    Box(
        Modifier.size(size).drawWithContent {
            drawContent()
            if (ringAlpha > 0f && ink != null) {
                val width = 1.5.dp.toPx()
                val gap = 2.5.dp.toPx() + width / 2
                val side = this.size.width + 2 * gap
                val left = -gap
                val top = -gap
                val right = left + side
                val radius = side * 0.36f
                val arc = Path().apply {
                    moveTo(left + side * 0.56f, top)
                    lineTo(right - radius, top)
                    arcTo(Rect(right - 2 * radius, top, right, top + 2 * radius), -90f, 90f, forceMoveTo = false)
                    lineTo(right, top + side * 0.44f)
                }
                drawPath(arc, ink.copy(alpha = ringAlpha), style = Stroke(width, cap = StrokeCap.Round))
            }
        },
    ) {
        bitmap?.let { Image(it, contentDescription = app.label, modifier = Modifier.fillMaxSize()) }
    }
}
