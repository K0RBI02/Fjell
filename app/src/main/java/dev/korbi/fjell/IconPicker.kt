package dev.korbi.fjell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

// Bluish white and near-black, pulled from the wallpaper's sky and mountain.
val PopupColor = Color(0xF5EDF0F5)
val Ink = Color(0xFF1A1F2B)

/** Text on the dark, blurred popups (same look as the stack). */
internal val PopupText = Color.White
internal val Card = Color.White.copy(alpha = 0.1f)
internal val Night = Color(0xFF1C2233)
internal val Mountain = Color(0xFF536A92)
internal val Cream = Color(0xFFE8E2D0)
internal val Muted = PopupText.copy(alpha = 0.6f)
internal val rowStyle = TextStyle(fontFamily = Lato, fontSize = 17.sp, color = PopupText)
internal val hintStyle = TextStyle(fontFamily = Lato, fontSize = 13.sp, color = Muted)

/** Captions for the bubble row in the settings. */
internal val SHORT_LABELS = mapOf(
    IconStyle.Original to R.string.style_original,
    IconStyle.Grayscale to R.string.style_gray,
    IconStyle.Mono to R.string.style_simple,
)

/** The styles everyone can pick, in display order, with a one-line explanation each. */
internal val BUILT_IN_STYLES = listOf(
    Triple(IconStyle.Original, R.string.style_original_long, R.string.style_original_hint),
    Triple(IconStyle.Grayscale, R.string.style_gray_long, R.string.style_gray_hint),
    Triple(IconStyle.Mono, R.string.style_simple_long, R.string.style_simple_hint),
)

/**
 * A sheet docked to the bottom edge, dark and see-through like the stack (the wallpaper
 * behind it is blurred by the launcher). Drag the handle down, or keep pulling once
 * the list is at its top, to close it; let go early and it springs back. Tapping
 * above it closes it too. Callers animate it in and out with [PopupTransition].
 */
@Composable
fun FjellPopup(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val noRipple = remember { MutableInteractionSource() }
    val density = LocalDensity.current
    val closeDistance = with(density) { 120.dp.toPx() }
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val dismiss by rememberUpdatedState(onDismiss)
    val closeLabel = stringResource(R.string.cd_close)

    fun dragBy(delta: Float) {
        scope.launch { offset.snapTo((offset.value + delta).coerceAtLeast(0f)) }
    }
    fun settle(velocity: Float) {
        if (offset.value > closeDistance || velocity > FLING_TO_CLOSE) {
            dismiss()
        } else {
            scope.launch { offset.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
        }
    }
    // Lets the list hand over: pulling down at its top moves the sheet, pushing up first
    // brings the sheet back before the list scrolls again.
    val pullToClose = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && offset.value > 0f) {
                    val used = maxOf(available.y, -offset.value)
                    dragBy(used)
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    dragBy(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offset.value > 0f) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .clickable(noRipple, null, onClick = onDismiss)
            .statusBarsPadding()
            .padding(top = 24.dp),
    ) {
        // As tall as the page needs (up to the top margin); the height glides between pages.
        var sheetTop by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier
                .fillMaxWidth()
                .animateContentSize(tween(220))
                .graphicsLayer { translationY = offset.value }
                .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                .onGloballyPositioned { sheetTop = it.positionInRoot().y },
        ) {
            FrostedBackdrop(sheetTop)
            Column(
                Modifier
                    // Swallow taps so they don't reach the dismiss layer behind.
                    .clickable(noRipple, null) {}
                    .nestedScroll(pullToClose)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .draggable(
                            state = rememberDraggableState { dragBy(it) },
                            orientation = Orientation.Vertical,
                            onDragStopped = { settle(it) },
                        )
                        // Tapping closes too, so the handle works without dragging (and for TalkBack).
                        .clickable(noRipple, null, role = Role.Button, onClickLabel = closeLabel, onClick = onDismiss)
                        .semantics { contentDescription = closeLabel },
                ) {
                    Box(Modifier.size(width = 40.dp, height = 4.dp).background(PopupText.copy(alpha = 0.45f), RoundedCornerShape(50)))
                }
                Box(Modifier.weight(1f, fill = false)) { content() }
            }
        }
    }
}

/**
 * Frosted glass for a sheet whose top edge sits [sheetTop] px below the window top: the
 * scene drawn full-window, shifted so it lines up with the real wallpaper, then blurred
 * and tinted like the stack. Only the part under the sheet shows.
 */
@Composable
private fun BoxScope.FrostedBackdrop(sheetTop: Float) {
    val window = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    Image(
        painter = painterResource(LocalScene.current.res),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .matchParentSize()
            .wrapContentHeight(Alignment.Top, unbounded = true)
            .requiredSize(with(density) { window.width.toDp() }, with(density) { window.height.toDp() })
            .offset { IntOffset(0, -sheetTop.roundToInt()) }
            .blur(24.dp),
    )
    Box(Modifier.matchParentSize().background(SheetColor))
}

/** Same tint the stack lays over the blurred wallpaper. */
private val SheetColor = Color(0x99101422)

/** Downward fling speed (px/s) that closes the sheet even if it hasn't been dragged far. */
private const val FLING_TO_CLOSE = 1800f

/** Shows [content] (a [FjellPopup]) while [visible], sliding up from the bottom edge and back down. */
@Composable
fun PopupTransition(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(tween(300)) { it },
        exit = slideOutVertically(tween(220)) { it },
    ) { content() }
}

/** Hand-written popup title, with an optional ‹ to go back. */
@Composable
fun PopupHeader(title: String, onBack: (() -> Unit)? = null) {
    val backLabel = stringResource(R.string.cd_back)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) 8.dp else 24.dp, end = 24.dp, top = 2.dp, bottom = 4.dp),
    ) {
        if (onBack != null) {
            Text(
                "‹",
                style = TextStyle(fontFamily = Lato, fontSize = 34.sp, color = PopupText),
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = backLabel }
                    .padding(horizontal = 12.dp),
            )
        }
        Text(
            title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = IndieFlower, fontSize = 32.sp, color = PopupText),
        )
    }
}

@Composable
internal fun HandTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = TextStyle(fontFamily = IndieFlower, fontSize = 22.sp, color = PopupText), modifier = modifier)
}

/** A preview bubble with a hand-written caption; the chosen one gets a ring. */
@Composable
internal fun BubbleChoice(
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
                .border(2.dp, if (selected) PopupText else Color.Transparent, RoundedCornerShape(36)),
        ) { preview() }
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(fontFamily = IndieFlower, fontSize = 15.sp, color = if (selected) PopupText else Muted),
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** A settings row: leading bubble, label with optional hint, switch at the end. */
@Composable
internal fun ToggleRow(label: String, on: Boolean, onToggle: () -> Unit, hint: String? = null, leading: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = on, role = Role.Switch, onValueChange = { onToggle() })
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
internal fun GlyphBubble(glyph: @Composable () -> Unit) {
    Box(Modifier.size(44.dp).background(Color(0xFFF3EFF4), RoundedCornerShape(36)), contentAlignment = Alignment.Center) { glyph() }
}

/** On/off switch: mountain-blue track when on, a white bubble knob that glides across. */
@Composable
internal fun BubbleSwitch(on: Boolean) {
    val progress by animateFloatAsState(if (on) 1f else 0f, tween(200), label = "switch")
    val track = lerp(PopupText.copy(alpha = 0.2f), Mountain, progress)
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

    FjellPopup(onClose) {
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
            return@FjellPopup
        }
        Column(Modifier.fillMaxWidth()) {
            PopupHeader(stringResource(R.string.icon_for, app.label))
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    StyleCard(
                        label = stringResource(R.string.icon_default),
                        hint = stringResource(R.string.icon_default_hint),
                        selected = current == null,
                        preview = { StylePreview(app, globalStyle, actions) },
                        onClick = {
                            actions.setAppIconStyle(app, null)
                            onClose()
                        },
                    )
                }
                styleRows(
                    app = app,
                    selected = current,
                    iconPacks = iconPacks,
                    actions = actions,
                    onSelect = {
                        actions.setAppIconStyle(app, it)
                        onClose()
                    },
                    onOpenPack = { browsing = it },
                )
                item {
                    StyleCard(
                        label = stringResource(R.string.icon_own_picture),
                        hint = stringResource(R.string.icon_own_picture_hint),
                        selected = current == IconStyle.Custom,
                        preview = if (current == IconStyle.Custom) ({ StylePreview(app, IconStyle.Custom, actions) }) else null,
                        onClick = {
                            actions.pickCustomIcon(app)
                            onClose()
                        },
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
    item { SectionTitle(stringResource(R.string.styles)) }
    BUILT_IN_STYLES.forEach { (style, label, hint) ->
        item {
            StyleCard(
                label = stringResource(label),
                hint = stringResource(hint),
                selected = selected == style,
                preview = app?.let { { StylePreview(it, style, actions) } },
                onClick = { onSelect(style) },
            )
        }
    }
    if (iconPacks.isNotEmpty()) {
        item { SectionTitle(stringResource(R.string.installed_packs)) }
        iconPacks.forEach { pack ->
            val style = IconStyle.Pack(pack.pkg)
            item {
                StyleCard(
                    label = pack.label,
                    hint = if (onOpenPack != null) stringResource(R.string.pack_pick_hint) else stringResource(R.string.pack_auto_hint),
                    selected = selected == style || (selected as? IconStyle.PackIcon)?.pkg == pack.pkg,
                    chevron = onOpenPack != null,
                    preview = app?.let { { StylePreview(it, style, actions) } },
                    onClick = { if (onOpenPack != null) onOpenPack(pack.pkg) else onSelect(style) },
                )
            }
        }
    }
}

/** Every icon in one pack, searchable; stringResource(R.string.automatic) keeps the pack's own match. */
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
            cursorBrush = SolidColor(PopupText),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .background(Card, RoundedCornerShape(50))
                .padding(horizontal = 22.dp, vertical = 14.dp),
            decorationBox = { field ->
                if (query.isEmpty()) Text(stringResource(R.string.search_icons), style = rowStyle.copy(color = Muted))
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
                    label = stringResource(R.string.automatic),
                    hint = stringResource(R.string.pack_auto_long_hint, pack.label, app.label),
                    selected = current == IconStyle.Pack(pack.pkg),
                    preview = { StylePreview(app, IconStyle.Pack(pack.pkg), actions) },
                    onClick = { onPick(IconStyle.Pack(pack.pkg)) },
                )
            }
            if (names == null) {
                item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.loading), style = hintStyle, modifier = Modifier.padding(8.dp)) }
            }
            items(filtered, key = { it }) { name ->
                val style = IconStyle.PackIcon(pack.pkg, name)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .then(if (current == style) Modifier.border(2.dp, PopupText, RoundedCornerShape(30)) else Modifier)
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
internal fun SectionTitle(text: String) {
    Text(text, style = TextStyle(fontFamily = Lato, fontSize = 15.sp, color = Muted), modifier = Modifier.padding(top = 14.dp, start = 4.dp))
}

@Composable
internal fun StyleCard(
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
            .then(if (selected) Modifier.border(1.5.dp, PopupText.copy(alpha = 0.8f), RoundedCornerShape(20.dp)) else Modifier)
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
internal fun StylePreview(app: AppEntry, style: IconStyle, actions: LauncherActions, size: Dp = 44.dp) {
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
