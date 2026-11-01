package dev.korbi.fjell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun Drawer(
    apps: List<AppEntry>,
    gridKeys: List<String?>,
    recentKeys: List<String>,
    launchCounts: Map<String, Int>,
    hiddenKeys: Set<String>,
    settings: LauncherSettings,
    actions: LauncherActions,
    /** Opened from a search entry point: focus the field and bring up the keyboard. */
    focusSearch: Boolean,
    onClose: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }
    val searchLabel = stringResource(R.string.cd_search)
    LaunchedEffect(focusSearch) { if (focusSearch) searchFocus.requestFocus() }
    val calculation = remember(query) { Calculator.evaluate(query)?.let(Calculator::format) }
    // Contacts arrive a moment later (and only with the option and permission on).
    val contacts by produceState(emptyList<ContactHit>(), query, settings.searchContacts) {
        value = if (settings.searchContacts && query.isNotBlank()) {
            delay(150)
            actions.searchContacts(query)
        } else {
            emptyList()
        }
    }
    val showHeaders = settings.drawerSections
    // The stack holds everything not on the home grid; search covers all apps.
    val filtered = remember(apps, gridKeys, hiddenKeys, query, launchCounts) {
        val q = query.trim()
        if (q.isEmpty()) {
            apps.filter { it.key !in gridKeys && it.key !in hiddenKeys }
        } else {
            // Best matching names first ("ma" → Maps before Amazon), then most used, ties alphabetical (stable sort).
            apps.mapNotNull { app -> SearchRank.tier(app.label, q)?.let { app to it } }
                .sortedWith(compareBy<Pair<AppEntry, Int>> { it.second }.thenByDescending { launchCounts[it.first.key] ?: 0 })
                .map { it.first }
        }
    }
    // The "relevant" row: new apps first, then the most used (see Relevance).
    val clock = LocalClock.current
    val recentApps = remember(apps, gridKeys, hiddenKeys, recentKeys, launchCounts, query, settings.drawerRecent) {
        if (query.isNotBlank() || !settings.drawerRecent) return@remember emptyList()
        val byKey = apps.associateBy { it.key }
        val candidates = apps.filter { it.key !in gridKeys && it.key !in hiddenKeys }.map { it.key }
        Relevance.pick(
            candidates = candidates,
            installedAt = { byKey[it]?.installedAt ?: 0L },
            launches = { launchCounts[it] ?: 0 },
            recent = recentKeys,
            now = clock.millis(),
            limit = RECENT_SHOWN,
        ).mapNotNull(byKey::get)
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
                .focusRequester(searchFocus)
                .semantics { contentDescription = searchLabel }
                .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(horizontal = 22.dp, vertical = 14.dp),
            decorationBox = { field ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SearchGlyph(Color.White.copy(alpha = 0.7f))
                    Spacer(Modifier.width(14.dp))
                    Box {
                        if (query.isEmpty()) {
                            Text(stringResource(R.string.search_hint), style = TextStyle(fontFamily = Lato, fontSize = 18.sp, color = Color.White.copy(alpha = 0.55f)))
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
                    // Apps first, then a calculation, then contacts; the web is the last resort.
                    items(filtered, key = { it.key }) { appRow(it) }
                    if (calculation != null) {
                        item(key = "calc") { CalculationRow(calculation) { actions.copyToClipboard(calculation) } }
                    }
                    items(contacts, key = { "contact:" + it.uri }) { contact ->
                        ContactRow(
                            contact,
                            onCall = {
                                actions.callContact(contact)
                                close()
                            },
                        ) {
                            actions.openContact(contact)
                            close()
                        }
                    }
                    item(key = "web") {
                        WebSearchRow(query.trim()) {
                            actions.searchWeb(query.trim())
                            close()
                        }
                    }
                } else {
                    if (recentApps.isNotEmpty()) {
                        item(key = "recent:header") { SectionHeader(stringResource(R.string.recent_header)) }
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

/** "= 48" for a calculation typed into the search; tap to copy the result. */
@Composable
private fun CalculationRow(result: String, onClick: () -> Unit) {
    SearchExtraRow(onClick = onClick, leading = { Text("=", style = TextStyle(fontFamily = IndieFlower, fontSize = 24.sp, color = Color.White)) }) {
        Text(result, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Lato, fontSize = 18.sp, color = Color.White))
    }
}

/** A contact hit: tap the row to open the contact, the handset on the right to call. */
@Composable
private fun ContactRow(contact: ContactHit, onCall: () -> Unit, onClick: () -> Unit) {
    SearchExtraRow(onClick = onClick, leading = {
        Text(contact.name.take(1).uppercase(), style = TextStyle(fontFamily = IndieFlower, fontSize = 22.sp, color = Color.White))
    }) {
        Text(contact.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontFamily = Lato, fontSize = 18.sp, color = Color.White), modifier = Modifier.weight(1f))
        if (contact.number != null) {
            val callLabel = stringResource(R.string.cd_call, contact.name)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(36))
                    .clickable(role = Role.Button, onClickLabel = callLabel, onClick = onCall)
                    .semantics { contentDescription = callLabel },
            ) {
                Canvas(Modifier.size(20.dp)) { drawHandsetGlyph(Color.White) }
            }
        }
    }
}

/** A classic telephone handset, in the launcher's line style. */
private fun DrawScope.drawHandsetGlyph(color: Color) {
    val w = size.width
    val h = size.height
    val handset = Path().apply {
        moveTo(w * 0.22f, h * 0.1f)
        lineTo(w * 0.36f, h * 0.1f)
        lineTo(w * 0.44f, h * 0.3f)
        lineTo(w * 0.34f, h * 0.4f)
        cubicTo(w * 0.42f, h * 0.56f, w * 0.48f, h * 0.62f, w * 0.62f, h * 0.68f)
        lineTo(w * 0.72f, h * 0.58f)
        lineTo(w * 0.9f, h * 0.66f)
        lineTo(w * 0.9f, h * 0.8f)
        cubicTo(w * 0.9f, h * 0.88f, w * 0.84f, h * 0.92f, w * 0.76f, h * 0.9f)
        cubicTo(w * 0.42f, h * 0.84f, w * 0.14f, h * 0.56f, w * 0.1f, h * 0.24f)
        cubicTo(w * 0.08f, h * 0.16f, w * 0.14f, h * 0.1f, w * 0.22f, h * 0.1f)
        close()
    }
    drawPath(handset, color, style = Stroke(size.minDimension * 0.09f, join = StrokeJoin.Round))
}

/** A search result that isn't an app: a faint bubble with a glyph or letter, then the text. */
@Composable
private fun SearchExtraRow(onClick: () -> Unit, leading: @Composable () -> Unit, text: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 28.dp, end = 40.dp, top = 9.dp, bottom = 9.dp),
    ) {
        Box(Modifier.size(42.dp).background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(36)), contentAlignment = Alignment.Center) { leading() }
        Spacer(Modifier.width(18.dp))
        text()
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
            stringResource(R.string.search_web, query),
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
            // A touch strip for sighted users; TalkBack users scroll the list instead.
            .clearAndSetSemantics {}
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
