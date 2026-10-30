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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Bluish white and near-black, pulled from the wallpaper's sky and mountain.
val PopupColor = Color(0xF5EDF0F5)
val Ink = Color(0xFF1A1F2B)
private val Card = Color.White.copy(alpha = 0.55f)
private val Night = Color(0xFF1C2233)
private val Mountain = Color(0xFF536A92)
private val Cream = Color(0xFFE8E2D0)
private val Muted = Ink.copy(alpha = 0.55f)
private val rowStyle = TextStyle(fontFamily = Lato, fontSize = 17.sp, color = Ink)
private val hintStyle = TextStyle(fontFamily = Lato, fontSize = 13.sp, color = Muted)

/** Captions for the bubble row in the settings. */
private val SHORT_LABELS = mapOf(
    IconStyle.Original to "Original",
    IconStyle.Grayscale to "Grau",
    IconStyle.Mono to "Einfach",
)

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
                // Swallow taps so they don't reach the dismiss layer behind.
                .clickable(noRipple, null) {},
        ) {
            // The barn scene from the same series, washed out so it stays a backdrop.
            Image(
                painter = painterResource(R.drawable.wp_barn),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.BottomCenter,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.62f)))
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
    searchShown: Boolean,
    searchOptions: SearchBarOptions,
    actions: LauncherActions,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    HelgelandPopup(onDismiss) {
        Column(Modifier.fillMaxSize()) {
            PopupHeader("Einstellungen")
            LazyColumn(
                contentPadding = PaddingValues(vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { HandTitle("Icons", Modifier.padding(horizontal = 22.dp)) }
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
                item { HandTitle("3×3-Knopf", Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp)) }
                item {
                    ToggleRow(
                        label = "auf dem Startbildschirm",
                        hint = when {
                            !stapelShown -> "aus"
                            stapelOpensSettings -> "öffnet die Einstellungen"
                            else -> "öffnet den Stapel"
                        },
                        on = stapelShown,
                        onToggle = { actions.setStapelShown(!stapelShown) },
                    ) { StapelIcon(44.dp) }
                }
                item { HandTitle("Suchleiste", Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp)) }
                item {
                    ToggleRow(
                        label = "auf dem Startbildschirm",
                        hint = if (searchShown) "gedrückt halten für die Breite" else "aus",
                        on = searchShown,
                        onToggle = { actions.setSearchShown(!searchShown) },
                    ) { GlyphBubble { SearchGlyph(Ink) } }
                }
                item {
                    ToggleRow(label = "Mikrofon", on = searchOptions.mic, onToggle = { actions.setSearchMic(!searchOptions.mic) }) {
                        GlyphBubble { MicGlyph(Ink) }
                    }
                }
                item {
                    Text(
                        "Als Standard-Launcher festlegen",
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        style = TextStyle(fontFamily = IndieFlower, fontSize = 19.sp, color = Cream),
                        modifier = Modifier
                            .padding(start = 22.dp, end = 22.dp, top = 18.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .background(Night)
                            .clickable(onClick = actions::openHomeSettings)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

/** Hand-written popup title, with an optional ‹ to go back. */
@Composable
fun PopupHeader(title: String, onBack: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) 8.dp else 24.dp, end = 24.dp, top = 22.dp, bottom = 4.dp),
    ) {
        if (onBack != null) {
            Text(
                "‹",
                style = TextStyle(fontFamily = Lato, fontSize = 34.sp, color = Ink),
                modifier = Modifier.clickable(onClick = onBack).padding(horizontal = 12.dp),
            )
        }
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = IndieFlower, fontSize = 32.sp, color = Ink),
        )
    }
}

@Composable
private fun HandTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = TextStyle(fontFamily = IndieFlower, fontSize = 22.sp, color = Ink), modifier = modifier)
}

/** A preview bubble with a hand-written caption; the chosen one gets a ring. */
@Composable
private fun BubbleChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    preview: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(64.dp)
                .border(2.dp, if (selected) Night else Color.Transparent, RoundedCornerShape(36)),
        ) { preview() }
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = IndieFlower, fontSize = 15.sp, color = if (selected) Ink else Muted),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A settings row: leading bubble, label with optional hint, switch at the end. */
@Composable
private fun ToggleRow(label: String, on: Boolean, onToggle: () -> Unit, hint: String? = null, leading: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 22.dp, vertical = 6.dp),
    ) {
        leading()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = rowStyle)
            if (hint != null) Text(hint, style = hintStyle)
        }
        BubbleSwitch(on)
    }
}

@Composable
private fun GlyphBubble(glyph: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).background(Color(0xFFF3EFF4), RoundedCornerShape(36)), contentAlignment = Alignment.Center) { glyph() }
}

/** On/off switch: mountain-blue track when on, a white bubble knob that glides across. */
@Composable
private fun BubbleSwitch(on: Boolean) {
    val progress by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "switch")
    val track = lerp(Ink.copy(alpha = 0.14f), Mountain, progress)
    Box(
        Modifier
            .size(width = 46.dp, height = 26.dp)
            .background(track, RoundedCornerShape(50))
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = 20.dp * progress)
                .size(20.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
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
        Column(Modifier.fillMaxSize()) {
            PopupHeader("Icon von ${app.label}")
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
        PopupHeader(pack.label, onBack = onBack)
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
