package dev.korbi.helgeland

import android.Manifest
import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var repo: AppRepository
    private lateinit var prefs: Prefs
    private lateinit var icons: IconProvider
    private var unwatch: (() -> Unit)? = null

    private val apps = mutableStateOf<List<AppEntry>>(emptyList())
    private val grid = mutableStateOf<List<String?>>(List(GRID_SIZE) { null })
    private val temperature = mutableStateOf<Int?>(null)
    private var weatherFetchedAt = 0L
    private val globalIconStyle = mutableStateOf<IconStyle>(IconStyle.Pack(IconPack.NOTHING))
    private val iconPacks = mutableStateOf<List<IconSource>>(emptyList())
    private val stapelOpensSettings = mutableStateOf(false)
    private val searchOptions = mutableStateOf(SearchBarOptions(mic = true, lens = false))

    /** Bumped whenever icons may look different, so the UI re-renders them. */
    private val iconVersion = mutableIntStateOf(0)
    private var pendingIconApp: AppEntry? = null

    /** Lens refuses to open unless started for a result, which is how it learns who called it. */
    private val lens = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    private val pickPicture = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val app = pendingIconApp ?: return@registerForActivityResult
        pendingIconApp = null
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { icons.saveCustom(app, uri) }
            if (saved) setIconOverride(app, IconStyle.Custom)
            else Toast.makeText(this@MainActivity, "Bild konnte nicht geladen werden", Toast.LENGTH_SHORT).show()
        }
    }

    private val locationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) refreshWeather(force = true)
    }

    /** Bumped when Home is pressed while we're already in front, so the UI can reset. */
    private val homePresses = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repo = AppRepository(this)
        prefs = Prefs(this)
        icons = IconProvider(this, prefs)
        globalIconStyle.value = icons.globalStyle
        stapelOpensSettings.value = prefs.stapelOpensSettings
        searchOptions.value = SearchBarOptions(prefs.searchMic, prefs.searchLens)
        grid.value = prefs.grid
        // Grids from before the stack button existed get it once; fresh installs get it from seedGrid.
        if (prefs.hasChosenGrid && !prefs.stapelButtonPlaced) addStapelButton()
        temperature.value = prefs.lastTemperature

        if (!prefs.askedForLocation) {
            prefs.askedForLocation = true
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        reload()
        unwatch = repo.watch { reload() }

        setContent {
            Launcher(
                apps = apps.value,
                gridKeys = grid.value,
                temperature = temperature.value,
                iconVersion = iconVersion.intValue,
                globalIconStyle = globalIconStyle.value,
                iconPacks = iconPacks.value,
                stapelOpensSettings = stapelOpensSettings.value,
                searchOptions = searchOptions.value,
                homePresses = homePresses.intValue,
                actions = actions,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses.intValue++
    }

    override fun onResume() {
        super.onResume()
        refreshWeather()
    }

    private fun refreshWeather(force: Boolean = false) {
        val granted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val stale = System.currentTimeMillis() - weatherFetchedAt > WEATHER_MAX_AGE_MS
        if (!granted || !(force || stale)) return
        weatherFetchedAt = System.currentTimeMillis()
        lifecycleScope.launch {
            val temp = Weather.currentTemperature(this@MainActivity) ?: return@launch
            temperature.value = temp
            prefs.lastTemperature = temp
        }
    }

    override fun onDestroy() {
        unwatch?.invoke()
        super.onDestroy()
    }

    private fun reload() {
        lifecycleScope.launch {
            // The selected pack may itself have been updated, so it's re-read too.
            val (loaded, sources) = withContext(Dispatchers.IO) {
                icons.reload()
                repo.load(prefs::label) to icons.installedPacks()
            }
            iconPacks.value = sources
            iconVersion.intValue++
            apps.value = loaded
            if (!prefs.hasChosenGrid) seedGrid(loaded)
        }
    }

    /** First run: fill the grid with everyday apps so home isn't empty. */
    private fun seedGrid(loaded: List<AppEntry>) {
        val wanted = listOf(
            "com.whatsapp", "com.snapchat.android", "com.instagram.android", "com.discord",
            "com.spotify.music", "com.microsoft.office.outlook", "online.schulmanager.app", "io.homeassistant.companion.android",
            "com.google.android.googlequicksearchbox", "org.mozilla.firefox", "com.fps.camera",
        )
        val seeded = wanted.mapNotNull { pkg -> loaded.firstOrNull { it.component.packageName == pkg }?.key }
        setGrid(List(GRID_SIZE) { if (it == GRID_SIZE - 1) STAPEL_KEY else seeded.getOrNull(it) })
        prefs.stapelButtonPlaced = true
    }

    /** Puts the stack button in the last slot, or the first free one if that's taken. False if the grid is full. */
    private fun addStapelButton(): Boolean {
        prefs.stapelButtonPlaced = true
        val slots = grid.value.toMutableList()
        if (STAPEL_KEY in slots) return true
        val target = if (slots.last() == null) slots.lastIndex else slots.indexOf(null)
        if (target < 0) return false
        slots[target] = STAPEL_KEY
        setGrid(slots)
        return true
    }

    private fun setIconOverride(app: AppEntry, style: IconStyle?) {
        if (style != IconStyle.Custom) icons.deleteCustom(app)
        prefs.setIconOverride(app.key, style?.id)
        icons.invalidate()
        iconVersion.intValue++
    }

    private fun setGrid(keys: List<String?>) {
        grid.value = keys
        prefs.grid = keys
    }

    private val actions = object : LauncherActions {
        override fun launch(app: AppEntry) {
            runCatching { repo.launch(app) }
        }

        override fun appInfo(app: AppEntry) {
            runCatching { repo.openAppInfo(app) }
        }

        override fun uninstall(app: AppEntry) {
            startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.component.packageName}")))
        }

        override fun toggleGrid(app: AppEntry) {
            val slots = grid.value.toMutableList()
            val current = slots.indexOf(app.key)
            val free = slots.indexOf(null)
            when {
                current >= 0 -> slots[current] = null
                free >= 0 -> slots[free] = app.key
                else -> {
                    Toast.makeText(this@MainActivity, "Gitter ist voll – erst eine App entfernen", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            setGrid(slots)
        }

        override fun setStapelShown(shown: Boolean) {
            if (!shown) {
                setGrid(grid.value.map { if (it == STAPEL_KEY) null else it })
            } else if (!addStapelButton()) {
                Toast.makeText(this@MainActivity, "Gitter ist voll – erst eine App entfernen", Toast.LENGTH_SHORT).show()
            }
        }

        override fun setStapelOpensSettings(opensSettings: Boolean) {
            prefs.stapelOpensSettings = opensSettings
            stapelOpensSettings.value = opensSettings
        }

        override fun moveInGrid(from: Int, to: Int) {
            setGrid(GridSlots.move(grid.value, from, to, GRID_COLUMNS))
        }

        override fun setSearchShown(shown: Boolean) {
            val placed = if (shown) GridSlots.placeSearch(grid.value, 2, GRID_COLUMNS) else GridSlots.withoutSearch(grid.value)
            if (placed != null) setGrid(placed)
            else Toast.makeText(this@MainActivity, "Die Suchleiste braucht 2 freie Plätze nebeneinander", Toast.LENGTH_SHORT).show()
        }

        override fun setSearchWidth(span: Int) {
            val resized = GridSlots.resizeSearch(grid.value, span, GRID_COLUMNS)
            if (resized != null) setGrid(resized)
            else Toast.makeText(this@MainActivity, "Dafür ist in keiner Reihe genug Platz frei", Toast.LENGTH_SHORT).show()
        }

        override fun openWebSearch() {
            val google = Intent(Intent.ACTION_MAIN).setClassName(GOOGLE_APP, "$GOOGLE_APP.SearchActivity")
            if (!startSafely(google)) startSafely(Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH))
        }

        override fun setSearchMic(shown: Boolean) {
            prefs.searchMic = shown
            searchOptions.value = searchOptions.value.copy(mic = shown)
        }

        override fun setSearchLens(shown: Boolean) {
            prefs.searchLens = shown
            searchOptions.value = searchOptions.value.copy(lens = shown)
        }

        override fun openLens() {
            val lensScreen = Intent(Intent.ACTION_VIEW, Uri.parse("google://lens")).setPackage(GOOGLE_APP)
            val standalone = packageManager.getLaunchIntentForPackage("com.google.ar.lens")
            val opened = runCatching { lens.launch(lensScreen) }.isSuccess || (standalone != null && startSafely(standalone))
            if (!opened) {
                Toast.makeText(this@MainActivity, "Google Lens ist nicht installiert", Toast.LENGTH_SHORT).show()
            }
        }

        override fun openVoiceSearch() {
            if (!startSafely(Intent(RecognizerIntent.ACTION_WEB_SEARCH))) startSafely(Intent(Intent.ACTION_VOICE_COMMAND))
        }

        override fun openClock() {
            startSafely(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        }

        override fun openCalendar() {
            startSafely(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR))
        }

        override fun expandNotifications() = this@MainActivity.expandNotifications()

        override suspend fun icon(app: AppEntry, sizePx: Int): ImageBitmap = withContext(Dispatchers.Default) {
            val original = repo.activityInfo(app)?.getIcon(0)
            icons.icon(app, original, sizePx).asImageBitmap()
        }

        override suspend fun previewIcon(app: AppEntry, style: IconStyle, sizePx: Int): ImageBitmap = withContext(Dispatchers.Default) {
            val original = repo.activityInfo(app)?.getIcon(0)
            icons.render(app, original, sizePx, style).asImageBitmap()
        }

        override suspend fun packIconNames(pkg: String): List<String> = withContext(Dispatchers.IO) { icons.packIconNames(pkg) }

        override suspend fun packIcon(pkg: String, name: String, sizePx: Int): ImageBitmap? = withContext(Dispatchers.Default) {
            icons.packIcon(pkg, name, sizePx)?.asImageBitmap()
        }

        override fun setGlobalIconStyle(style: IconStyle) {
            prefs.iconStyle = style.id
            globalIconStyle.value = style
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { icons.reload() }
                iconVersion.intValue++
            }
        }

        override fun iconOverride(app: AppEntry) = icons.overrideFor(app)

        override fun setAppIconStyle(app: AppEntry, style: IconStyle?) = setIconOverride(app, style)

        override fun rename(app: AppEntry, label: String) {
            prefs.setLabel(app.key, label.trim().ifEmpty { null })
            reload()
        }

        override fun pickCustomIcon(app: AppEntry) {
            pendingIconApp = app
            pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        override fun openHomeSettings() {
            startSafely(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    /** False if nothing on the phone handles [intent]. */
    private fun startSafely(intent: Intent): Boolean =
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    companion object {
        const val GRID_COLUMNS = 4
        const val GRID_SIZE = GRID_COLUMNS * 3

        const val STAPEL_KEY = GridSlots.STAPEL_KEY
        private const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
        const val WEATHER_MAX_AGE_MS = 30 * 60 * 1000L
    }
}

/** StatusBarManager.expandNotificationsPanel() is hidden API, but launchers have relied on it forever. */
@SuppressLint("WrongConstant")
private fun Context.expandNotifications() {
    runCatching {
        val service = getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
    }
}

interface LauncherActions {
    fun launch(app: AppEntry)
    fun appInfo(app: AppEntry)
    fun uninstall(app: AppEntry)
    fun toggleGrid(app: AppEntry)
    fun moveInGrid(from: Int, to: Int)
    fun setStapelShown(shown: Boolean)
    fun setSearchShown(shown: Boolean)

    /** [span] is 2 or 4 slots. */
    fun setSearchWidth(span: Int)
    fun openWebSearch()
    fun openVoiceSearch()
    fun openLens()
    fun setSearchMic(shown: Boolean)
    fun setSearchLens(shown: Boolean)
    fun setStapelOpensSettings(opensSettings: Boolean)
    fun openClock()
    fun openCalendar()
    fun expandNotifications()
    suspend fun icon(app: AppEntry, sizePx: Int): ImageBitmap
    suspend fun previewIcon(app: AppEntry, style: IconStyle, sizePx: Int): ImageBitmap
    suspend fun packIconNames(pkg: String): List<String>
    suspend fun packIcon(pkg: String, name: String, sizePx: Int): ImageBitmap?
    fun setGlobalIconStyle(style: IconStyle)
    fun iconOverride(app: AppEntry): IconStyle?

    /** null resets the app to the global style. */
    fun setAppIconStyle(app: AppEntry, style: IconStyle?)
    fun pickCustomIcon(app: AppEntry)

    /** A blank [label] restores the app's own name. */
    fun rename(app: AppEntry, label: String)
    fun openHomeSettings()
}
