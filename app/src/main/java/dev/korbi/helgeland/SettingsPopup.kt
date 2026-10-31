package dev.korbi.helgeland

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The settings popup's pages: an overview of categories, then one page per category. */
private enum class SettingsPage(val title: String, val hint: String, val glyph: DrawScope.(Color) -> Unit) {
    Home("Startbildschirm", "Hintergrund, 3×3-Knopf, Suchleiste, Media", DrawScope::drawGridGlyph),
    Clock("Uhr & Wetter", "Größe, Wetter, Wecker", DrawScope::drawBellGlyph),
    Stapel("Stapel", "zuletzt, Alphabet, ausgeblendete Apps", DrawScope::drawListGlyph),
    Icons("Icons", "Stil für alle Apps", DrawScope::drawIconGlyph),
    Gestures("Gesten", "Runterwischen, Doppeltippen", DrawScope::drawSwipeGlyph),
    General("Allgemein", "Vibration, Standard-Launcher", DrawScope::drawGearGlyph),
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
) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    BackHandler { if (page != null) page = null else onDismiss() }
    val update = actions::updateSettings

    HelgelandPopup(onDismiss) {
        Column(Modifier.fillMaxSize()) {
            val current = page
            PopupHeader(current?.title ?: "Einstellungen", onBack = if (current != null) ({ page = null }) else null)
            LazyColumn(
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (current) {
                    null -> overview(onOpen = { page = it })
                    SettingsPage.Home -> homePage(settings, stapelShown, searchShown, actions, update)
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
                    Text(page.title, style = rowStyle)
                    Text(page.hint, style = hintStyle)
                }
                Text("›", style = rowStyle.copy(fontSize = 24.sp))
            }
        }
    }
}

private fun LazyListScope.homePage(settings: LauncherSettings, stapelShown: Boolean, searchShown: Boolean, actions: LauncherActions, update: Update) {
    item { HandTitle("Hintergrund", Modifier.padding(horizontal = 22.dp)) }
    item {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            Scene.entries.forEach { scene ->
                BubbleChoice(
                    label = scene.label,
                    selected = scene == settings.scene,
                    onClick = { update { it.copy(scene = scene) } },
                    modifier = Modifier.weight(1f),
                ) {
                    Image(
                        painter = painterResource(scene.res),
                        contentDescription = scene.label,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.BottomCenter,
                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(36)),
                    )
                }
            }
        }
    }
    item { HandTitle("Auf dem Startbildschirm", Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp)) }
    item {
        ToggleRow(
            label = "3×3-Knopf",
            hint = when {
                !stapelShown -> "aus · hochwischen öffnet den Stapel"
                settings.stapelOpensSettings -> "öffnet die Einstellungen · gedrückt halten zum Ändern"
                else -> "öffnet den Stapel · gedrückt halten zum Ändern"
            },
            on = stapelShown,
            onToggle = { actions.setStapelShown(!stapelShown) },
        ) { StapelIcon(44.dp) }
    }
    item {
        ToggleRow(
            label = "Suchleiste",
            hint = if (searchShown) "gedrückt halten für die Breite" else "aus",
            on = searchShown,
            onToggle = { actions.setSearchShown(!searchShown) },
        ) { GlyphBubble { SearchGlyph(Ink) } }
    }
    if (searchShown) {
        item {
            ToggleRow(label = "Mikrofon in der Suchleiste", on = settings.searchMic, onToggle = { update { it.copy(searchMic = !it.searchMic) } }) {
                GlyphBubble { MicGlyph(Ink) }
            }
        }
    }
    item {
        val missingAccess = settings.media && !actions.hasMediaAccess()
        ToggleRow(
            label = "Media",
            hint = when {
                missingAccess -> "Benachrichtigungszugriff fehlt · antippen zum Freigeben"
                settings.media -> "zeigt, was gerade läuft · Titel öffnet die App"
                else -> "aus"
            },
            on = settings.media,
            onToggle = { if (missingAccess) actions.openMediaAccessSettings() else update { it.copy(media = !it.media) } },
        ) { GlyphBubble { Canvas(Modifier.size(20.dp)) { drawNoteGlyph(Ink) } } }
    }
}

private fun LazyListScope.clockPage(settings: LauncherSettings, update: Update) {
    item {
        ToggleRow(label = "Große Uhr", hint = if (settings.largeClock) "groß" else "kleiner", on = settings.largeClock, onToggle = { update { it.copy(largeClock = !it.largeClock) } }) {
            GlyphBubble { Text("12", style = TextStyle(fontFamily = IndieFlower, fontSize = 20.sp, color = Ink)) }
        }
    }
    item {
        ToggleRow(label = "Wetter", hint = "Temperatur hinter dem Datum", on = settings.showWeather, onToggle = { update { it.copy(showWeather = !it.showWeather) } }) {
            GlyphBubble { Text("°C", style = TextStyle(fontFamily = IndieFlower, fontSize = 18.sp, color = Ink)) }
        }
    }
    item {
        ToggleRow(label = "Nächster Wecker", hint = "wenn einer in den nächsten 24 Stunden klingelt", on = settings.showAlarm, onToggle = { update { it.copy(showAlarm = !it.showAlarm) } }) {
            GlyphBubble { Canvas(Modifier.size(20.dp)) { drawBellGlyph(Ink) } }
        }
    }
}

private fun LazyListScope.stapelPage(settings: LauncherSettings, hiddenApps: List<AppEntry>, actions: LauncherActions, update: Update) {
    item {
        ToggleRow(label = "zuletzt", hint = "die letzten 4 Apps ganz oben", on = settings.drawerRecent, onToggle = { update { it.copy(drawerRecent = !it.drawerRecent) } }) {
            GlyphBubble { Canvas(Modifier.size(20.dp)) { drawGridGlyph(Ink) } }
        }
    }
    item {
        ToggleRow(label = "Alphabet rechts", hint = "zum Springen", on = settings.drawerIndex, onToggle = { update { it.copy(drawerIndex = !it.drawerIndex) } }) {
            GlyphBubble { Text("A–Z", style = TextStyle(fontFamily = IndieFlower, fontSize = 16.sp, color = Ink)) }
        }
    }
    item {
        ToggleRow(label = "Buchstaben-Trenner", hint = "kleiner Buchstabe mit Linie", on = settings.drawerSections, onToggle = { update { it.copy(drawerSections = !it.drawerSections) } }) {
            GlyphBubble { Text("A", style = TextStyle(fontFamily = IndieFlower, fontSize = 20.sp, color = Ink)) }
        }
    }
    item { HandTitle("Ausgeblendet", Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp)) }
    if (hiddenApps.isEmpty()) {
        item { Text("keine · im Stapel eine App gedrückt halten → ausblenden", style = hintStyle, modifier = Modifier.padding(horizontal = 22.dp)) }
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
                Text("einblenden", style = hintStyle)
            }
        }
    }
}

private fun LazyListScope.iconsPage(sampleApp: AppEntry?, globalStyle: IconStyle, iconPacks: List<IconSource>, actions: LauncherActions) {
    item { Text("für alle Apps · einzelne Apps: gedrückt halten → Icon ändern", style = hintStyle, modifier = Modifier.padding(horizontal = 22.dp)) }
    item {
        val choices = BUILT_IN_STYLES.map { (style) -> style to SHORT_LABELS.getValue(style) } +
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
    item { HandTitle("Runterwischen", Modifier.padding(horizontal = 22.dp)) }
    SwipeDownAction.entries.forEach { action ->
        item { ChoiceRow(action.label, selected = settings.swipeDown == action) { update { it.copy(swipeDown = action) } } }
    }
    item { HandTitle("Doppeltippen", Modifier.padding(start = 22.dp, end = 22.dp, top = 8.dp)) }
    DoubleTapAction.entries.forEach { action ->
        item { ChoiceRow(action.label, selected = settings.doubleTap == action) { update { it.copy(doubleTap = action) } } }
    }
}

private fun LazyListScope.generalPage(settings: LauncherSettings, actions: LauncherActions, update: Update) {
    item {
        ToggleRow(label = "Vibration", hint = "beim Gedrückthalten und Ziehen", on = settings.haptics, onToggle = { update { it.copy(haptics = !it.haptics) } }) {
            GlyphBubble { Canvas(Modifier.size(20.dp)) { drawWaveGlyph(Ink) } }
        }
    }
    item {
        Text(
            "Als Standard-Launcher festlegen",
            maxLines = 1,
            textAlign = TextAlign.Center,
            style = TextStyle(fontFamily = IndieFlower, fontSize = 19.sp, color = Cream),
            modifier = Modifier
                .padding(start = 22.dp, end = 22.dp, top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(Night)
                .clickable(onClick = actions::openHomeSettings)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
    item {
        val context = LocalContext.current
        val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
        Text(
            "Helgeland $version",
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
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp),
    ) {
        Text(label, style = rowStyle.copy(color = if (selected) Ink else Muted), modifier = Modifier.weight(1f))
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
