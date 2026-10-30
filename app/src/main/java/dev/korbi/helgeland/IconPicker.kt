package dev.korbi.helgeland

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Bluish white and near-black, pulled from the wallpaper's sky and mountain.
val PopupColor = Color(0xF5EDF0F5)
val Ink = Color(0xFF1A1F2B)
private val Card = Color(0xFF536A92).copy(alpha = 0.10f)
private val Muted = Ink.copy(alpha = 0.55f)
private val titleStyle = TextStyle(fontFamily = IndieFlower, fontSize = 30.sp, color = Ink)
private val rowStyle = TextStyle(fontFamily = Lato, fontSize = 17.sp, color = Ink)
private val hintStyle = TextStyle(fontFamily = Lato, fontSize = 13.sp, color = Muted)

/** The styles everyone can pick, in display order, with a one-line explanation each. */
private val BUILT_IN_STYLES = listOf(
    Triple(IconStyle.Original, "Original", "das bunte App-Icon mit Rand in der Bubble"),
    Triple(IconStyle.Grayscale, "Original schwarz-weiß", "das App-Icon entsättigt"),
    Triple(IconStyle.Mono, "Einfaches App-Icon", "das schlichte Icon, das viele Apps mitliefern"),
)

/**
 * A rounded card floating over the home screen, from below the clock down to the
 * bottom edge. Tapping outside it dismisses.
 */
@Composable
fun HelgelandPopup(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(if (shown) 1f else 0f, tween(220), label = "popup")
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = appear }
            .background(Color(0x40000000))
            .clickable(noRipple, null, onClick = onDismiss)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 14.dp, end = 14.dp, top = 150.dp, bottom = 12.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = 0.94f + 0.06f * appear
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
                .clip(RoundedCornerShape(34.dp))
                .background(PopupColor)
                // Swallow taps so they don't reach the dismiss layer behind.
                .clickable(noRipple, null) {},
        ) {
            content()
        }
    }
}

/** Launcher settings: the default icon style and the 3x3 button. */
@Composable
fun SettingsPopup(
    sampleApp: AppEntry?,
    globalStyle: IconStyle,
    iconPacks: List<IconSource>,
    stapelShown: Boolean,
    stapelOpensSettings: Boolean,
    actions: LauncherActions,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    HelgelandPopup(onDismiss) {
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("Einstellungen", style = titleStyle) }
            item { Text("Icons für alle Apps · einzelne Apps: gedrückt halten → Icon ändern", style = hintStyle) }
            styleRows(sampleApp, globalStyle, iconPacks, actions, onSelect = actions::setGlobalIconStyle, onOpenPack = null)
            item { SectionTitle("3×3-Knopf") }
            item {
                StyleCard(
                    label = "Auf dem Startbildschirm",
                    hint = when {
                        !stapelShown -> "aus · hochwischen öffnet den Stapel"
                        stapelOpensSettings -> "öffnet die Einstellungen · Knopf gedrückt halten zum Ändern"
                        else -> "öffnet den Stapel · Knopf gedrückt halten zum Ändern"
                    },
                    selected = stapelShown,
                    onClick = { actions.setStapelShown(!stapelShown) },
                )
            }
            item {
                Spacer(Modifier.height(12.dp))
                StyleCard(label = "Als Standard-Launcher festlegen", onClick = actions::openHomeSettings)
            }
        }
    }
}

/** Per-app icon choice, modelled on Niagara's "change icon" page. */
@Composable
fun IconPickerPopup(
    app: AppEntry,
    globalStyle: IconStyle,
    iconPacks: List<IconSource>,
    actions: LauncherActions,
    onClose: () -> Unit,
) {
    var browsing by rememberSaveable { mutableStateOf<String?>(null) }
    val current = actions.iconOverride(app)
    BackHandler { if (browsing != null) browsing = null else onClose() }

    HelgelandPopup(onClose) {
        val pkg = browsing
        if (pkg != null) {
            PackBrowser(
                app = app,
                pack = iconPacks.firstOrNull { it.pkg == pkg } ?: IconSource(pkg, pkg),
                current = current,
                actions = actions,
                onPick = { style ->
                    actions.setAppIconStyle(app, style)
                    onClose()
                },
                onBack = { browsing = null },
            )
            return@HelgelandPopup
        }
        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("Icon von ${app.label}", style = titleStyle) }
            item {
                StyleCard(
                    label = "Standard",
                    hint = "wie in den Einstellungen",
                    selected = current == null,
                    preview = { StylePreview(app, globalStyle, actions) },
                    onClick = { actions.setAppIconStyle(app, null); onClose() },
                )
            }
            styleRows(
                app = app,
                selected = current,
                iconPacks = iconPacks,
                actions = actions,
                onSelect = { actions.setAppIconStyle(app, it); onClose() },
                onOpenPack = { browsing = it },
            )
            item {
                StyleCard(
                    label = "Eigenes Bild",
                    hint = "aus der Galerie wählen",
                    selected = current == IconStyle.Custom,
                    preview = if (current == IconStyle.Custom) ({ StylePreview(app, IconStyle.Custom, actions) }) else null,
                    onClick = { actions.pickCustomIcon(app); onClose() },
                )
            }
        }
    }
}

/**
 * Built-in styles followed by one row per installed pack. With [onOpenPack] set, a
 * pack row opens that pack to pick a specific icon instead of selecting it outright.
 */
private fun LazyListScope.styleRows(
    app: AppEntry?,
    selected: IconStyle?,
    iconPacks: List<IconSource>,
    actions: LauncherActions,
    onSelect: (IconStyle) -> Unit,
    onOpenPack: ((String) -> Unit)?,
) {
    item { SectionTitle("Stile") }
    BUILT_IN_STYLES.forEach { (style, label, hint) ->
        item {
            StyleCard(
                label = label,
                hint = hint,
                selected = selected == style,
                preview = app?.let { { StylePreview(it, style, actions) } },
                onClick = { onSelect(style) },
            )
        }
    }
    if (iconPacks.isNotEmpty()) {
        item { SectionTitle("Installierte Icon Packs") }
        iconPacks.forEach { pack ->
            val style = IconStyle.Pack(pack.pkg)
            item {
                StyleCard(
                    label = pack.label,
                    hint = if (onOpenPack != null) "Icon aus dem Pack aussuchen" else "passendes Icon automatisch",
                    selected = selected == style || (selected as? IconStyle.PackIcon)?.pkg == pack.pkg,
                    chevron = onOpenPack != null,
                    preview = app?.let { { StylePreview(it, style, actions) } },
                    onClick = { if (onOpenPack != null) onOpenPack(pack.pkg) else onSelect(style) },
                )
            }
        }
    }
}

/** Every icon in one pack, searchable; "Automatisch" keeps the pack's own match. */
@Composable
private fun PackBrowser(
    app: AppEntry,
    pack: IconSource,
    current: IconStyle?,
    actions: LauncherActions,
    onPick: (IconStyle) -> Unit,
    onBack: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val names by produceState<List<String>?>(null, pack.pkg) { value = actions.packIconNames(pack.pkg) }
    val filtered = remember(names, query) {
        val q = query.trim().replace(' ', '_')
        names.orEmpty().filter { q.isEmpty() || it.contains(q, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, end = 20.dp, top = 14.dp)) {
            Text(
                "‹",
                style = TextStyle(fontFamily = Lato, fontSize = 34.sp, color = Ink),
                modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 12.dp),
            )
            Text(pack.label, style = titleStyle)
        }
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = rowStyle,
            cursorBrush = SolidColor(Ink),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .background(Card, RoundedCornerShape(50))
                .padding(horizontal = 22.dp, vertical = 14.dp),
            decorationBox = { field ->
                if (query.isEmpty()) Text("Icon suchen", style = rowStyle.copy(color = Muted))
                field()
            },
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                StyleCard(
                    label = "Automatisch",
                    hint = "das Icon, das ${pack.label} für ${app.label} vorsieht",
                    selected = current == IconStyle.Pack(pack.pkg),
                    preview = { StylePreview(app, IconStyle.Pack(pack.pkg), actions) },
                    onClick = { onPick(IconStyle.Pack(pack.pkg)) },
                )
            }
            if (names == null) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text("lädt …", style = hintStyle, modifier = Modifier.padding(8.dp)) }
            }
            items(filtered, key = { it }) { name ->
                val style = IconStyle.PackIcon(pack.pkg, name)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .then(if (current == style) Modifier.border(2.dp, Ink, RoundedCornerShape(30)) else Modifier)
                        .clickable { onPick(style) }
                        .padding(6.dp),
                ) {
                    PackIconImage(pack.pkg, name, 48.dp, actions)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = TextStyle(fontFamily = Lato, fontSize = 15.sp, color = Muted), modifier = Modifier.padding(top = 14.dp, start = 4.dp))
}

@Composable
private fun StyleCard(
    label: String,
    onClick: () -> Unit,
    hint: String? = null,
    selected: Boolean = false,
    chevron: Boolean = false,
    preview: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(20.dp))
            .then(if (selected) Modifier.border(1.5.dp, Ink.copy(alpha = 0.8f), RoundedCornerShape(20.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        if (preview != null) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { preview() }
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = rowStyle)
            if (hint != null) Text(hint, style = hintStyle)
        }
        if (selected) Text("✓", style = rowStyle)
        if (chevron) Text("›", style = rowStyle.copy(fontSize = 24.sp), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun StylePreview(app: AppEntry, style: IconStyle, actions: LauncherActions, size: Dp = 44.dp) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, app.key, style, px) { value = actions.previewIcon(app, style, px) }
    bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
}

@Composable
private fun PackIconImage(pkg: String, name: String, size: Dp, actions: LauncherActions) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, pkg, name, px) { value = actions.packIcon(pkg, name, px) }
    bitmap?.let { Image(it, contentDescription = name, modifier = Modifier.size(size)) }
}
