package dev.korbi.fjell

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** The settings popup's pages: an overview of categories, then one page per category. */
private enum class SettingsPage(@StringRes val title: Int, @StringRes val hint: Int, val glyph: DrawScope.(Color) -> Unit) {
    Home(R.string.page_home, R.string.page_home_hint, DrawScope::drawGridGlyph),
    Clock(R.string.page_clock, R.string.page_clock_hint, DrawScope::drawBellGlyph),
    Stapel(R.string.page_stack, R.string.page_stack_hint, DrawScope::drawListGlyph),
    Icons(R.string.page_icons, R.string.page_icons_hint, DrawScope::drawIconGlyph),
    Gestures(R.string.page_gestures, R.string.page_gestures_hint, DrawScope::drawSwipeGlyph),
    General(R.string.page_general, R.string.page_general_hint, DrawScope::drawGearGlyph),
}

@Composable
fun SettingsPopup(
    sampleApp: AppEntry?,
    globalStyle: IconStyle,
    iconPacks: List<IconSource>,
    settings: LauncherSettings,
    stapelShown: Boolean,
    searchShown: Boolean,
    hiddenApps: List<AppEntry>,
    actions: LauncherActions,
    onDismiss: () -> Unit,
    sun: SunTimes? = null,
    scenes: List<Scene> = Scene.BUILT_IN,
) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler { if (page != null) page = null else onDismiss() }
    val update = actions::updateSettings

    FjellPopup(onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            val current = page
            PopupHeader(stringResource(current?.title ?: R.string.settings), onBack = if (current != null) ({ page = null }) else null)
            LazyColumn(
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (current) {
                    null -> overview(onOpen = { page = it })
                    SettingsPage.Home -> homePage(settings, stapelShown, searchShown, sun, scenes, actions, update)
                    SettingsPage.Clock -> clockPage(settings, update)
                    SettingsPage.Stapel -> stapelPage(settings, hiddenApps, actions, update)
                    SettingsPage.Icons -> iconsPage(sampleApp, globalStyle, iconPacks, actions)
                    SettingsPage.Gestures -> gesturesPage(settings, update)
                    SettingsPage.General -> generalPage(settings, actions, update)
                }
            }
        }
    }
}

private typealias Update = ((LauncherSettings) -> LauncherSettings) -> Unit

private fun LazyListScope.overview(onOpen: (SettingsPage) -> Unit) {
    SettingsPage.entries.forEach { page ->
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(page) }
                    .padding(horizontal = 22.dp, vertical = 6.dp),
            ) {
                GlyphBubble { Canvas(Modifier.size(20.dp)) { page.glyph(this, Ink) } }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(page.title), style = rowStyle)
                    Text(stringResource(page.hint), style = hintStyle)
                }
                Text("›", style = rowStyle.copy(fontSize = 24.sp))
            }
        }
    }
}

private fun LazyListScope.homePage(
    settings: LauncherSettings,
    stapelShown: Boolean,
    searchShown: Boolean,
    sun: SunTimes?,
    scenes: List<Scene>,
    actions: LauncherActions,
    update: Update,
) {
    item { HandTitle(stringResource(R.string.wallpaper), Modifier.padding(horizontal = 22.dp)) }
    item {
        // Built-in scenes, then the user's own pictures (hold one to adjust it), then "add".
        LazyRow(contentPadding = PaddingValues(horizontal = 14.dp)) {
            items(scenes, key = { it.id }) { scene ->
                BubbleChoice(
                    label = scene.label(),
                    selected = !settings.autoScene && scene.id == scenes.resolve(settings.scene).id,
                    onClick = { update { it.copy(scene = scene.id, autoScene = false) } },
                    onLongClick = if (scene.isCustom) ({ actions.editWallpaper(scene) }) else null,
                    modifier = Modifier.width(84.dp),
                ) { SceneThumb(scene, 54.dp) }
            }
            item(key = "add") {
                BubbleChoice(
                    label = stringResource(R.string.add_wallpaper),
                    selected = false,
                    onClick = actions::addWallpaper,
                    modifier = Modifier.width(84.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(36)).background(Card)) {
                        Text("+", style = rowStyle.copy(fontSize = 28.sp))
                    }
                }
            }
        }
    }
    if (scenes.any { it.isCustom }) {
        item { Text(stringResource(R.string.wallpaper_hold_hint), style = hintStyle, modifier = Modifier.padding(horizontal = 22.dp)) }
    }
    item {
        ToggleRow(
            label = stringResource(R.string.auto_scene),
            hint = stringResource(R.string.auto_scene_hint),
            on = settings.autoScene,
            onToggle = { update { it.copy(autoScene = !it.autoScene) } },
        ) { GlyphBubble { Canvas(Modifier.size(20.dp)) { drawSkyGlyph(Sky.PartlyCloudy, Ink) } } }
    }
    if (settings.autoScene) {
        item { ScheduleEditor(settings.schedule, update, sun = sun, scenes = scenes) }
        item {
            ToggleRow(
                label = stringResource(R.string.snow_in_winter),
                hint = stringResource(R.string.snow_in_winter_hint),
                on = settings.snowInWinter,
                onToggle = { update { it.copy(snowInWinter = !it.snowInWinter) } },
            ) { GlyphBubble { Canvas(Modifier.size(20.dp)) { drawSkyGlyph(Sky.Snow, Ink) } } }
        }
        if (settings.snowInWinter) {
            item {
                val winter = scenes.resolve(settings.winterScene)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClickLabel = stringResource(R.string.cd_change_scene, winter.label())) {
                            update { it.copy(winterScene = scenes.next(winter).id) }
                        }
                        .padding(horizontal = 22.dp, vertical = 6.dp),
                ) {
                    SceneThumb(winter, 44.dp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.winter_scene), style = rowStyle)
                        Text(winter.label() + " · " + stringResource(R.string.tap_to_change), style = hintStyle)
                    }
                }
            }
        }
    }
    item { HandTitle(stringResource(R.string.on_home_screen), Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp)) }
    item {
        ToggleRow(
            label = stringResource(R.string.stapel_button),
            hint = when {
                !stapelShown -> stringResource(R.string.stapel_off_hint)
                settings.stapelOpensSettings -> stringResource(R.string.stapel_settings_hint)
                else -> stringResource(R.string.stapel_stack_hint)
            },
            on = stapelShown,
            onToggle = { actions.setStapelShown(!stapelShown) },
        ) { StapelIcon(44.dp) }
    }
    item {
        ToggleRow(
            label = stringResource(R.string.search_bar),
            hint = if (searchShown) stringResource(R.string.search_bar_hint) else stringResource(R.string.off),
            on = searchShown,
            onToggle = { actions.setSearchShown(!searchShown) },
        ) { GlyphBubble { SearchGlyph(Ink) } }
    }
    if (searchShown) {
        item {
            ToggleRow(label = stringResource(R.string.mic_in_search_bar), on = settings.searchMic, onToggle = { update { it.copy(searchMic = !it.searchMic) } }) {
                GlyphBubble { MicGlyph(Ink) }
            }
        }
    }
    item {
        val missingAccess = settings.notificationDots && !actions.hasMediaAccess()
        ToggleRow(
            label = stringResource(R.string.notification_dots),
            hint = when {
                missingAccess -> stringResource(R.string.media_needs_access)
                settings.notificationDots -> stringResource(R.string.notification_dots_hint)
                else -> stringResource(R.string.off)
            },
            on = settings.notificationDots,
            onToggle = { if (missingAccess) actions.openMediaAccessSettings() else update { it.copy(notificationDots = !it.notificationDots) } },
        ) { GlyphBubble { Canvas(Modifier.size(20.dp)) { drawCircle(Ink, size.minDimension * 0.22f) } } }
    }
    item {
        val missingAccess = settings.media && !actions.hasMediaAccess()
        ToggleRow(
            label = stringResource(R.string.media),
            hint = when {
                missingAccess -> stringResource(R.string.media_needs_access)
                settings.media -> stringResource(R.string.media_on_hint)
                else -> stringResource(R.string.off)
            },
            on = settings.media,
            onToggle = { if (missingAccess) actions.openMediaAccessSettings() else update { it.copy(media = !it.media) } },
        ) { GlyphBubble { Canvas(Modifier.size(20.dp)) { drawNoteGlyph(Ink) } } }
    }
}

private fun LazyListScope.clockPage(settings: LauncherSettings, update: Update) {
    item {
        ToggleRow(label = stringResource(R.string.large_clock), hint = if (settings.largeClock) stringResource(R.string.large) else stringResource(R.string.smaller), on = settings.largeClock, onToggle = { update { it.copy(largeClock = !it.largeClock) } }) {
            GlyphBubble { Text("12", style = TextStyle(fontFamily = IndieFlower, fontSize = 20.sp, color = Ink)) }
        }
    }
    item {
        ToggleRow(label = stringResource(R.string.weather), hint = stringResource(R.string.weather_hint), on = settings.showWeather, onToggle = { update { it.copy(showWeather = !it.showWeather) } }) {
            GlyphBubble { Text("°C", style = TextStyle(fontFamily = IndieFlower, fontSize = 18.sp, color = Ink)) }
        }
    }
    item {
        ToggleRow(label = stringResource(R.string.next_alarm), hint = stringResource(R.string.next_alarm_hint), on = settings.showAlarm, onToggle = { update { it.copy(showAlarm = !it.showAlarm) } }) {
            GlyphBubble { Canvas(Modifier.size(20.dp)) { drawBellGlyph(Ink) } }
        }
    }
    item {
        ToggleRow(label = stringResource(R.string.next_event), hint = stringResource(R.string.next_event_hint), on = settings.showEvents, onToggle = { update { it.copy(showEvents = !it.showEvents) } }) {
            GlyphBubble { Canvas(Modifier.size(20.dp)) { drawCalendarGlyph(Ink) } }
        }
    }
}

private fun LazyListScope.stapelPage(settings: LauncherSettings, hiddenApps: List<AppEntry>, actions: LauncherActions, update: Update) {
    item {
        ToggleRow(label = stringResource(R.string.recent), hint = stringResource(R.string.recent_hint), on = settings.drawerRecent, onToggle = { update { it.copy(drawerRecent = !it.drawerRecent) } }) {
            GlyphBubble { Canvas(Modifier.size(20.dp)) { drawGridGlyph(Ink) } }
        }
    }
    item {
        ToggleRow(label = stringResource(R.string.alphabet_strip), hint = stringResource(R.string.alphabet_strip_hint), on = settings.drawerIndex, onToggle = { update { it.copy(drawerIndex = !it.drawerIndex) } }) {
            GlyphBubble { Text("A–Z", style = TextStyle(fontFamily = IndieFlower, fontSize = 16.sp, color = Ink)) }
        }
    }
    item {
        ToggleRow(label = stringResource(R.string.letter_headers), hint = stringResource(R.string.letter_headers_hint), on = settings.drawerSections, onToggle = { update { it.copy(drawerSections = !it.drawerSections) } }) {
            GlyphBubble { Text("A", style = TextStyle(fontFamily = IndieFlower, fontSize = 20.sp, color = Ink)) }
        }
    }
    item {
        ToggleRow(
            label = stringResource(R.string.contacts_in_search),
            hint = stringResource(R.string.contacts_in_search_hint),
            on = settings.searchContacts,
            onToggle = { update { it.copy(searchContacts = !it.searchContacts) } },
        ) { GlyphBubble { Text("@", style = TextStyle(fontFamily = IndieFlower, fontSize = 20.sp, color = Ink)) } }
    }
    item { HandTitle(stringResource(R.string.hidden), Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp)) }
    if (hiddenApps.isEmpty()) {
        item { Text(stringResource(R.string.hidden_none), style = hintStyle, modifier = Modifier.padding(horizontal = 22.dp)) }
    }
    hiddenApps.forEach { app ->
        item(key = app.key) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { actions.setHidden(app, false) }
                    .padding(horizontal = 22.dp, vertical = 6.dp),
            ) {
                AppIcon(app, 44.dp, actions)
                Spacer(Modifier.width(16.dp))
                Text(app.label, style = rowStyle, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.show), style = hintStyle)
            }
        }
    }
}

private fun LazyListScope.iconsPage(sampleApp: AppEntry?, globalStyle: IconStyle, iconPacks: List<IconSource>, actions: LauncherActions) {
    item { Text(stringResource(R.string.icons_page_hint), style = hintStyle, modifier = Modifier.padding(horizontal = 22.dp)) }
    item {
        val choices = BUILT_IN_STYLES.map { (style) -> style to stringResource(SHORT_LABELS.getValue(style)) } +
            iconPacks.map { IconStyle.Pack(it.pkg) to it.label.removeSuffix(" Icons").removeSuffix(" Icon Pack") }
        // Equal columns, four to a row, so every choice is fully visible.
        Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            choices.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (style, label) ->
                        BubbleChoice(
                            label = label,
                            selected = style == globalStyle,
                            onClick = { actions.setGlobalIconStyle(style) },
                            modifier = Modifier.weight(1f),
                        ) {
                            if (sampleApp != null) StylePreview(sampleApp, style, actions, size = 54.dp)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun LazyListScope.gesturesPage(settings: LauncherSettings, update: Update) {
    item { HandTitle(stringResource(R.string.swipe_down), Modifier.padding(horizontal = 22.dp)) }
    SwipeDownAction.entries.forEach { action ->
        item { ChoiceRow(stringResource(action.label), selected = settings.swipeDown == action) { update { it.copy(swipeDown = action) } } }
    }
    item { HandTitle(stringResource(R.string.double_tap), Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp)) }
    DoubleTapAction.entries.forEach { action ->
        item { ChoiceRow(stringResource(action.label), selected = settings.doubleTap == action) { update { it.copy(doubleTap = action) } } }
    }
}

private fun LazyListScope.generalPage(settings: LauncherSettings, actions: LauncherActions, update: Update) {
    item {
        ToggleRow(label = stringResource(R.string.vibration), hint = stringResource(R.string.vibration_hint), on = settings.haptics, onToggle = { update { it.copy(haptics = !it.haptics) } }) {
            GlyphBubble { Canvas(Modifier.size(20.dp)) { drawWaveGlyph(Ink) } }
        }
    }
    item {
        Text(
            stringResource(R.string.set_default_launcher),
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = IndieFlower, fontSize = 19.sp, color = PopupText),
            modifier = Modifier
                .padding(start = 22.dp, end = 22.dp, top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(PopupText.copy(alpha = 0.14f))
                .clickable(onClick = actions::openHomeSettings)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
    item {
        val context = LocalContext.current
        val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        Text(
            stringResource(R.string.version_line, version.orEmpty()),
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = IndieFlower, fontSize = 15.sp, color = Muted),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )
    }
}

/** One option of a pick-one group; the chosen one carries a check. */
@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(label, style = rowStyle.copy(color = if (selected) PopupText else Muted), modifier = Modifier.weight(1f))
        if (selected) Text("✓", style = rowStyle)
    }
}

private fun DrawScope.drawSwipeGlyph(color: Color) {
    val stroke = size.minDimension * 0.1f
    val w = size.width
    val h = size.height
    drawLine(color, Offset(w * 0.5f, h * 0.1f), Offset(w * 0.5f, h * 0.88f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.24f, h * 0.62f), Offset(w * 0.5f, h * 0.88f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.76f, h * 0.62f), Offset(w * 0.5f, h * 0.88f), stroke, StrokeCap.Round)
}

private fun DrawScope.drawNoteGlyph(color: Color) {
    val stroke = size.minDimension * 0.1f
    val w = size.width
    val h = size.height
    drawCircle(color, w * 0.16f, Offset(w * 0.28f, h * 0.76f))
    drawLine(color, Offset(w * 0.42f, h * 0.76f), Offset(w * 0.42f, h * 0.12f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.42f, h * 0.12f), Offset(w * 0.82f, h * 0.24f), stroke, StrokeCap.Round)
}

private fun DrawScope.drawWaveGlyph(color: Color) {
    val stroke = size.minDimension * 0.1f
    val w = size.width
    val h = size.height
    listOf(0.2f, 0.4f, 0.6f, 0.8f).forEachIndexed { i, x ->
        val half = if (i == 1 || i == 2) 0.36f else 0.2f
        drawLine(color, Offset(w * x, h * (0.5f - half)), Offset(w * x, h * (0.5f + half)), stroke, StrokeCap.Round)
    }
}

/**
 * The wallpaper timetable: one row per "from this time on" slot. Tapping the picture
 * cycles the scene, tapping the time opens a dial, × removes the slot.
 */
@Composable
internal fun ScheduleEditor(
    schedule: List<SceneSlot>,
    update: ((LauncherSettings) -> LauncherSettings) -> Unit,
    initiallyEditing: SceneSlot? = null,
    sun: SunTimes? = null,
    scenes: List<Scene> = Scene.BUILT_IN,
) {
    val is24 = DateFormat.is24HourFormat(LocalContext.current)
    val slots = schedule.sortedBy { it.minuteWith(sun) }
    var editing by remember { mutableStateOf(initiallyEditing) }
    fun replace(old: SceneSlot, new: SceneSlot?) = update { s -> s.copy(schedule = s.schedule.mapNotNull { if (it == old) new else it }) }

    Column(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 4.dp)) {
        slots.forEach { slot ->
            val slotScene = scenes.resolve(slot.scene)
            val sceneName = slotScene.label()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                SceneThumb(
                    slotScene,
                    44.dp,
                    contentDescription = stringResource(R.string.cd_change_scene, sceneName),
                    modifier = Modifier.clickable(role = Role.Button) {
                        val next = scenes.next(slotScene).id
                        replace(slot, slot.copy(scene = next))
                        if (editing == slot) editing = slot.copy(scene = next)
                    },
                )
                Spacer(Modifier.width(16.dp))
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { editing = if (editing == slot) null else slot },
                ) {
                    // Sun slots show today's time once the location is known.
                    val today = when (slot.sun) {
                        null -> slot.minute
                        SunEvent.Sunrise -> sun?.sunrise
                        SunEvent.Sunset -> sun?.sunset
                    }?.let { formatMinute(it, is24) }
                    Text(
                        when (slot.sun) {
                            null -> stringResource(R.string.from_time, today.orEmpty())
                            SunEvent.Sunrise -> today?.let { stringResource(R.string.at_sunrise_time, it) } ?: stringResource(R.string.at_sunrise)
                            SunEvent.Sunset -> today?.let { stringResource(R.string.at_sunset_time, it) } ?: stringResource(R.string.at_sunset)
                        },
                        style = rowStyle,
                    )
                    Text(sceneName, style = hintStyle)
                }
                if (slots.size > 1) {
                    Text(
                        "×",
                        style = rowStyle.copy(fontSize = 26.sp, color = Muted),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.remove)) {
                                replace(slot, null)
                                if (editing == slot) editing = null
                            }
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
            }
            if (editing == slot) {
                TimeEditor(
                    minute = slot.minute,
                    sun = slot.sun,
                    is24 = is24,
                    onDone = { minute, event ->
                        // Two slots at the same time would fight; the editor just stays open on a clash.
                        val others = slots - slot
                        val clash = if (event == null) others.any { it.sun == null && it.minute == minute } else others.any { it.sun == event }
                        if (!clash) {
                            val changed = slot.copy(minute = if (event == null) minute else slot.minute, sun = event)
                            if (changed != slot) replace(slot, changed)
                            editing = null
                        }
                    },
                    onCancel = { editing = null },
                )
            }
        }
        if (slots.size < SceneSchedule.MAX_SLOTS) {
            Text(
                stringResource(R.string.add_time),
                style = rowStyle.copy(color = Muted),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        val slot = SceneSlot(SceneSchedule.nextFreeMinute(slots), Scene.DEFAULT_ID)
                        update { s -> s.copy(schedule = s.schedule + slot) }
                        editing = slot
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeEditor(minute: Int, sun: SunEvent?, is24: Boolean, onDone: (Int, SunEvent?) -> Unit, onCancel: () -> Unit) {
    val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = is24)
    var event by remember { mutableStateOf(sun) }
    // The dial is tall; scroll the sheet so it and its buttons are fully in view when it opens.
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        delay(50) // let it be measured first
        bringIntoView.bringIntoView()
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView).padding(vertical = 8.dp),
    ) {
        // A fixed time on the dial, or whenever the sun rises or sets that day.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            listOf(null to R.string.time_fixed, SunEvent.Sunrise to R.string.sunrise, SunEvent.Sunset to R.string.sunset).forEach { (choice, label) ->
                val selected = event == choice
                Text(
                    stringResource(label),
                    style = rowStyle.copy(fontSize = 15.sp, color = if (selected) Color.White else PopupText),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Mountain else Card)
                        .selectable(selected = selected, role = Role.RadioButton) { event = choice }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        if (event == null) {
            // The dial draws its colon in onSurface, which is dark ink in our light menu scheme.
            MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(onSurface = PopupText)) {
                TimePicker(
                    state = state,
                    colors = TimePickerDefaults.colors(
                        clockDialColor = Card,
                        clockDialSelectedContentColor = Color.White,
                        clockDialUnselectedContentColor = PopupText,
                        selectorColor = Mountain,
                        periodSelectorBorderColor = Muted,
                        periodSelectorSelectedContainerColor = Mountain,
                        periodSelectorUnselectedContainerColor = Color.Transparent,
                        periodSelectorSelectedContentColor = Color.White,
                        periodSelectorUnselectedContentColor = Muted,
                        timeSelectorSelectedContainerColor = Mountain,
                        timeSelectorUnselectedContainerColor = Card,
                        timeSelectorSelectedContentColor = Color.White,
                        timeSelectorUnselectedContentColor = PopupText,
                    ),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel), style = rowStyle.copy(color = Muted), modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onCancel).padding(12.dp))
            Text(stringResource(R.string.set_time), style = rowStyle, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onDone(state.hour * 60 + state.minute, event) }.padding(12.dp))
        }
    }
}

private fun formatMinute(minute: Int, is24: Boolean): String = LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm a", Locale.ENGLISH))

/** A small round-cornered preview of a scene's motif. */
@Composable
private fun SceneThumb(scene: Scene, size: Dp, modifier: Modifier = Modifier, contentDescription: String? = scene.label()) {
    Image(
        painter = rememberScenePainter(scene),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        alignment = Alignment.Center,
        modifier = Modifier.size(size).clip(RoundedCornerShape(36)).then(modifier),
    )
}

/** The scene after [current] in this list, wrapping around. */
private fun List<Scene>.next(current: Scene): Scene = this[(indexOfFirst { it.id == current.id } + 1).mod(size)]
