package dev.korbi.helgeland

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.SearchManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.core.content.ContextCompat
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
    private val recent = mutableStateOf<List<String>>(emptyList())
    private val launchCounts = mutableStateOf<Map<String, Int>>(emptyMap())
    private val hidden = mutableStateOf<Set<String>>(emptySet())
    private val temperature = mutableStateOf<Int?>(null)

    /** Trigger time of the next alarm, if one is set; the UI decides whether it's close enough to show. */
    private val nextAlarm = mutableStateOf<Long?>(null)
    private val alarmChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshNextAlarm()
    }
    private var weatherFetchedAt = 0L
    private val globalIconStyle = mutableStateOf<IconStyle>(IconStyle.Pack(IconPack.NOTHING))
    private val iconPacks = mutableStateOf<List<IconSource>>(emptyList())
    private val settings = mutableStateOf(LauncherSettings())
    private val nowPlaying = mutableStateOf<NowPlaying?>(null)
    private val media by lazy { MediaWatcher(this) { nowPlaying.value = it } }

    /** Bumped whenever icons may look different, so the UI re-renders them. */
    private val iconVersion = mutableIntStateOf(0)
    private var pendingIconApp: AppEntry? = null

    private val pickPicture = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val app = pendingIconApp ?: return@registerForActivityResult
        pendingIconApp = null
        uri ?: return@registerForActivityResult
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) { icons.saveCustom(app, uri) }
            if (saved) setIconOverride(app, IconStyle.Custom)
            else Toast.makeText(this@MainActivity, getString(R.string.toast_picture_failed), Toast.LENGTH_SHORT).show()
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
        settings.value = prefs.settings
        grid.value = prefs.grid
        recent.value = prefs.recent
        launchCounts.value = prefs.launchCounts
        hidden.value = prefs.hidden
        // Grids from before the stack button existed get it once; fresh installs get it from seedGrid.
        if (prefs.hasChosenGrid && !prefs.stapelButtonPlaced) addStapelButton()
        temperature.value = prefs.lastTemperature

        if (!prefs.askedForLocation) {
            prefs.askedForLocation = true
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        reload()
        // System broadcast, so it still arrives with NOT_EXPORTED; recent Android insists on a flag.
        ContextCompat.registerReceiver(this, alarmChanged, IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        unwatch = repo.watch(
            onRemoved = { pkg, user -> setGrid(grid.value.map { key -> if (key != null && AppEntry.isOf(key, pkg, user.hashCode())) null else key }) },
            onChange = { reload() },
        )

        setContent {
            Launcher(
                apps = apps.value,
                gridKeys = grid.value,
                recentKeys = recent.value,
                launchCounts = launchCounts.value,
                hiddenKeys = hidden.value,
                temperature = temperature.value,
                nextAlarm = nextAlarm.value,
                iconVersion = iconVersion.intValue,
                globalIconStyle = globalIconStyle.value,
                iconPacks = iconPacks.value,
                settings = settings.value,
                nowPlaying = nowPlaying.value,
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
        refreshNextAlarm()
        // Restarted on every return, so a freshly granted notification access takes effect right away.
        if (settings.value.media) media.start()
    }

    override fun onPause() {
        media.stop()
        super.onPause()
    }

    private fun refreshNextAlarm() {
        nextAlarm.value = getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime
    }

    private fun refreshWeather(force: Boolean = false) {
        val granted = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val stale = System.currentTimeMillis() - weatherFetchedAt > WEATHER_MAX_AGE_MS
        if (!settings.value.showWeather || !granted || !(force || stale)) return
        weatherFetchedAt = System.currentTimeMillis()
        lifecycleScope.launch {
            val temp = Weather.currentTemperature(this@MainActivity) ?: return@launch
            temperature.value = temp
            prefs.lastTemperature = temp
        }
    }

    override fun onDestroy() {
        unregisterReceiver(alarmChanged)
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
            if (runCatching { repo.launch(app) }.isFailure) return
            recent.value = (listOf(app.key) + recent.value.filter { it != app.key }).take(RECENT_KEPT)
            prefs.recent = recent.value
            launchCounts.value = launchCounts.value + (app.key to (launchCounts.value[app.key] ?: 0) + 1)
            prefs.launchCounts = launchCounts.value
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
                    Toast.makeText(this@MainActivity, getString(R.string.toast_grid_full), Toast.LENGTH_SHORT).show()
                    return
                }
            }
            setGrid(slots)
        }

        override fun setStapelShown(shown: Boolean) {
            if (!shown) {
                setGrid(grid.value.map { if (it == STAPEL_KEY) null else it })
            } else if (!addStapelButton()) {
                Toast.makeText(this@MainActivity, getString(R.string.toast_grid_full), Toast.LENGTH_SHORT).show()
            }
        }

        override fun updateSettings(change: (LauncherSettings) -> LauncherSettings) {
            val before = settings.value
            val after = change(before)
            settings.value = after
            prefs.settings = after
            if (after.media != before.media) {
                if (after.media) {
                    if (!MediaWatcher.hasAccess(this@MainActivity)) openMediaAccessSettings()
                    media.start()
                } else {
                    media.stop()
                }
            }
            if (after.showWeather && !before.showWeather) refreshWeather(force = true)
        }

        override fun hasMediaAccess() = MediaWatcher.hasAccess(this@MainActivity)

        override fun openMediaAccessSettings() {
            // Android 11+ can jump straight to our switch; older versions show the full list.
            val detail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(this@MainActivity, MediaListener::class.java).flattenToString())
            } else {
                null
            }
            if (detail == null || !startSafely(detail)) startSafely(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        override fun mediaPlayPause() = media.playPause()

        override fun mediaNext() = media.next()

        override fun mediaOpen() = media.open()

        override fun moveInGrid(from: Int, to: Int) {
            setGrid(GridSlots.move(grid.value, from, to, GRID_COLUMNS))
        }

        override fun setSearchShown(shown: Boolean) {
            val placed = if (shown) GridSlots.placeSearch(grid.value, 2, GRID_COLUMNS) else GridSlots.withoutSearch(grid.value)
            if (placed != null) setGrid(placed)
            else Toast.makeText(this@MainActivity, getString(R.string.toast_search_needs_space), Toast.LENGTH_SHORT).show()
        }

        override fun setSearchWidth(span: Int) {
            val resized = GridSlots.resizeSearch(grid.value, span, GRID_COLUMNS)
            if (resized != null) setGrid(resized)
            else Toast.makeText(this@MainActivity, getString(R.string.toast_no_row_space), Toast.LENGTH_SHORT).show()
        }

        override fun openWebSearch() {
            val google = Intent(Intent.ACTION_MAIN).setClassName(GOOGLE_APP, "$GOOGLE_APP.SearchActivity")
            if (!startSafely(google)) startSafely(Intent(SearchManager.INTENT_ACTION_GLOBAL_SEARCH))
        }

        override fun searchWeb(query: String) {
            val search = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
            if (!startSafely(search)) {
                startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
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

        override fun setHidden(app: AppEntry, hide: Boolean) {
            hidden.value = if (hide) hidden.value + app.key else hidden.value - app.key
            prefs.hidden = hidden.value
        }

        override fun isHidden(app: AppEntry) = app.key in hidden.value

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
        private const val RECENT_KEPT = 12
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
    fun searchWeb(query: String)
    fun updateSettings(change: (LauncherSettings) -> LauncherSettings)
    fun hasMediaAccess(): Boolean
    fun openMediaAccessSettings()
    fun mediaPlayPause()
    fun mediaNext()
    fun mediaOpen()
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

    fun setHidden(app: AppEntry, hide: Boolean)
    fun isHidden(app: AppEntry): Boolean

    /** A blank [label] restores the app's own name. */
    fun rename(app: AppEntry, label: String)
    fun openHomeSettings()
}
