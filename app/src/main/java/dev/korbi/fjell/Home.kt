package dev.korbi.fjell

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
internal fun Home(
    apps: List<AppEntry>,
    gridKeys: List<String?>,
    weather: WeatherNow?,
    nextAlarm: Long?,
    nowPlaying: NowPlaying?,
    dotPackages: Set<String>,
    noDots: Set<String>,
    settings: LauncherSettings,
    actions: LauncherActions,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openDrawer by rememberUpdatedState(onOpenDrawer)
    val openSearch by rememberUpdatedState(onOpenSearch)
    val openSettings by rememberUpdatedState(onOpenSettings)
    val swipeDown by rememberUpdatedState(settings.swipeDown)
    val doubleTap by rememberUpdatedState(settings.doubleTap)
    fun run(action: DoubleTapAction) = when (action) {
        DoubleTapAction.Nothing -> Unit
        DoubleTapAction.Search -> openSearch()
        DoubleTapAction.Stapel -> openDrawer()
        DoubleTapAction.Settings -> openSettings()
        DoubleTapAction.Lock -> actions.lockScreen()
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
                    onDrag = { change, amount ->
                        change.consume()
                        total += amount
                    },
                    onDragEnd = {
                        val (dx, dy) = total
                        when {
                            abs(dx) > abs(dy) -> Unit
                            dy < -threshold -> openDrawer()
                            dy > threshold -> when (swipeDown) {
                                SwipeDownAction.Notifications -> actions.expandNotifications()
                                SwipeDownAction.Search -> openSearch()
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
            weather = weather,
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
                    stringResource(R.string.home_empty_hint),
                    style = TextStyle(fontFamily = IndieFlower, fontSize = 18.sp, color = Color.White, shadow = softShadow),
                )
            } else {
                AppGrid(
                    slots = slots,
                    actions = actions,
                    stapelOpensSettings = settings.stapelOpensSettings,
                    searchMic = settings.searchMic,
                    dotPackages = dotPackages,
                    noDots = noDots,
                    onOpenDrawer = { openDrawer() },
                    onOpenSettings = { openSettings() },
                )
            }
        }
    }
}

@Composable
private fun Clock(color: Color, large: Boolean, weather: WeatherNow?, nextAlarm: Long?, actions: LauncherActions, modifier: Modifier = Modifier) {
    val context = LocalView.current.context
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    // Re-keyed on lifecycle so the time is fresh the moment we come back to the foreground.
    val clock = LocalClock.current
    val now by produceState(LocalDateTime.now(clock), lifecycleState) {
        while (true) {
            value = LocalDateTime.now(clock)
            delay(60_000L - clock.millis() % 60_000L)
        }
    }
    val timePattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
    val time = now.format(DateTimeFormatter.ofPattern(timePattern))
    val date = now.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
    val subline = if (weather != null) "$date | ${weather.temperature}°C" else date
    // Only alarms within the next day are worth a glance on the home screen.
    val alarm = nextAlarm
        ?.takeIf { it - clock.millis() in 0..ALARM_HORIZON_MS }
        ?.let { Instant.ofEpochMilli(it).atZone(clock.zone).format(DateTimeFormatter.ofPattern(timePattern)) }
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
            if (weather != null) {
                Spacer(Modifier.width(5.dp))
                val skyLabel = stringResource(skyLabel(weather.sky))
                Canvas(Modifier.size(17.dp).semantics { contentDescription = skyLabel }) { drawSkyGlyph(weather.sky, color) }
            }
            if (alarm != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(noRipple, null) { actions.openClock() },
                ) {
                    Text(" | ", style = sublineStyle)
                    val alarmLabel = stringResource(R.string.cd_alarm)
                    Canvas(Modifier.size(15.dp).semantics { contentDescription = alarmLabel }) { drawBellGlyph(color) }
                    Text(" $alarm", style = sublineStyle)
                }
            }
        }
    }
}

private const val ALARM_HORIZON_MS = 24 * 60 * 60 * 1000L

@StringRes
private fun skyLabel(sky: Sky): Int = when (sky) {
    Sky.Clear -> R.string.sky_clear
    Sky.ClearNight -> R.string.sky_clear_night
    Sky.PartlyCloudy -> R.string.sky_partly_cloudy
    Sky.Cloudy -> R.string.sky_cloudy
    Sky.Fog -> R.string.sky_fog
    Sky.Rain -> R.string.sky_rain
    Sky.Snow -> R.string.sky_snow
    Sky.Thunder -> R.string.sky_thunder
}

/** The current sky as a small line glyph, in the same hand as the bell. */
internal fun DrawScope.drawSkyGlyph(sky: Sky, color: Color) {
    val stroke = size.minDimension * 0.1f
    val w = size.width
    val h = size.height
    fun sun(center: Offset, radius: Float) {
        drawCircle(color, radius, center, style = Stroke(stroke))
        for (i in 0 until 8) {
            val angle = Math.PI / 4 * i
            val dir = Offset(cos(angle).toFloat(), sin(angle).toFloat())
            drawLine(color, center + dir * (radius * 1.45f), center + dir * (radius * 1.95f), stroke, StrokeCap.Round)
        }
    }
    fun cloud(top: Float) {
        val path = Path().apply {
            moveTo(w * 0.2f, top + h * 0.34f)
            cubicTo(w * 0.02f, top + h * 0.34f, w * 0.04f, top + h * 0.08f, w * 0.24f, top + h * 0.1f)
            cubicTo(w * 0.3f, top - h * 0.1f, w * 0.62f, top - h * 0.12f, w * 0.68f, top + h * 0.06f)
            cubicTo(w * 0.94f, top + h * 0.02f, w * 1f, top + h * 0.34f, w * 0.8f, top + h * 0.34f)
            close()
        }
        drawPath(path, color, style = Stroke(stroke, join = StrokeJoin.Round))
    }
    when (sky) {
        Sky.Clear -> sun(center, w * 0.2f)
        Sky.ClearNight -> {
            // A crescent: the full disc minus a shifted one.
            val disc = Path().apply { addOval(Rect(center, w * 0.36f)) }
            val bite = Path().apply { addOval(Rect(center + Offset(w * 0.2f, -h * 0.16f), w * 0.3f)) }
            drawPath(Path.combine(PathOperation.Difference, disc, bite), color, style = Stroke(stroke, join = StrokeJoin.Round))
        }
        Sky.PartlyCloudy -> {
            sun(Offset(w * 0.34f, h * 0.32f), w * 0.13f)
            cloud(h * 0.5f)
        }
        Sky.Cloudy -> cloud(h * 0.36f)
        Sky.Fog -> listOf(0.3f, 0.5f, 0.7f).forEachIndexed { i, y ->
            drawLine(color, Offset(w * (0.1f + 0.08f * i), h * y), Offset(w * (0.9f - 0.08f * (2 - i)), h * y), stroke, StrokeCap.Round)
        }
        Sky.Rain -> {
            cloud(h * 0.22f)
            listOf(0.3f, 0.52f, 0.74f).forEach { x -> drawLine(color, Offset(w * x, h * 0.68f), Offset(w * (x - 0.06f), h * 0.88f), stroke, StrokeCap.Round) }
        }
        Sky.Snow -> {
            cloud(h * 0.22f)
            listOf(0.3f, 0.52f, 0.74f).forEach { x -> drawCircle(color, stroke * 0.9f, Offset(w * x, h * 0.8f)) }
        }
        Sky.Thunder -> {
            cloud(h * 0.22f)
            val bolt = Path().apply {
                moveTo(w * 0.56f, h * 0.6f)
                lineTo(w * 0.42f, h * 0.8f)
                lineTo(w * 0.56f, h * 0.8f)
                lineTo(w * 0.46f, h * 0.98f)
            }
            drawPath(bolt, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

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
