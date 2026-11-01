package dev.korbi.fjell

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import java.time.Clock
import java.time.LocalDateTime

/** December to February: the time for the winter scene, if one is chosen. */
fun isWinter(time: LocalDateTime) = time.monthValue in WINTER_MONTHS

private val WINTER_MONTHS = setOf(12, 1, 2)

enum class SunEvent { Sunrise, Sunset }

/**
 * From [minute] of the day (0 until 1440) on, [scene] shows, until the next slot takes over.
 * A [sun] slot follows sunrise or sunset instead; [minute] is its stand-in while those are unknown.
 */
data class SceneSlot(val minute: Int, val scene: String, val sun: SunEvent? = null) {
    fun minuteWith(times: SunTimes?): Int = when (sun) {
        null -> minute
        SunEvent.Sunrise -> times?.sunrise ?: minute
        SunEvent.Sunset -> times?.sunset ?: minute
    }
}

/** The user's wallpaper timetable; each slot runs until the next one, the last one past midnight. */
object SceneSchedule {
    const val MAX_SLOTS = 6
    val DEFAULT = listOf(SceneSlot(7 * 60, Scene.DEFAULT_ID), SceneSlot(18 * 60, "dusk"))

    /** The slot in charge at [minute] given today's [sun] times, or null for an empty schedule. */
    fun slotAt(slots: List<SceneSlot>, minute: Int, sun: SunTimes? = null): SceneSlot? {
        val sorted = slots.sortedBy { it.minuteWith(sun) }
        return sorted.lastOrNull { it.minuteWith(sun) <= minute } ?: sorted.lastOrNull()
    }

    /** A free time for a new slot: an hour after the last one, stepping on until nothing is there yet. */
    fun nextFreeMinute(slots: List<SceneSlot>): Int {
        val taken = slots.map { it.minute }.toSet()
        var minute = ((slots.maxOfOrNull { it.minute } ?: (11 * 60)) + 60) % MINUTES_PER_DAY
        while (minute in taken) minute = (minute + 60) % MINUTES_PER_DAY
        return minute
    }

    /** "420:Mountain,1080:Pole:Sunset"; unknown scenes and broken entries are skipped. */
    fun encode(slots: List<SceneSlot>) = slots.joinToString(",") { listOfNotNull(it.minute, it.scene, it.sun?.name).joinToString(":") }

    fun decode(text: String?): List<SceneSlot>? = text?.split(',')?.mapNotNull { part ->
        // Custom scene ids contain a colon themselves ("custom:…"), so split from both ends.
        val sun = part.substringAfterLast(':').let { last -> SunEvent.entries.firstOrNull { it.name == last } }
        val body = if (sun != null) part.substringBeforeLast(':') else part
        val scene = body.substringAfter(':', missingDelimiterValue = "").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        body.substringBefore(':').toIntOrNull()?.takeIf { it in 0 until MINUTES_PER_DAY }?.let { SceneSlot(it, scene, sun) }
    }

    private const val MINUTES_PER_DAY = 24 * 60
}

/** Where the UI reads the time from: the system clock, or a fixed one in screenshot tests. */
val LocalClock = staticCompositionLocalOf<Clock> { Clock.systemDefaultZone() }

/** The active scene, for parts (like popup sheets) that redraw the wallpaper themselves. */
val LocalScene = staticCompositionLocalOf { SceneArt.Fjord.scene }

enum class SwipeDownAction(@StringRes val label: Int) {
    Notifications(R.string.action_notifications),
    Search(R.string.action_search),
    Nothing(R.string.action_nothing),
}

enum class DoubleTapAction(@StringRes val label: Int) {
    Nothing(R.string.action_nothing),
    Search(R.string.action_search),
    Stapel(R.string.action_stack),
    Settings(R.string.settings),
    Lock(R.string.action_lock),
}

/** Every user-adjustable launcher option in one place. Grid layout and icons live elsewhere. */
data class LauncherSettings(
    /** Id of the chosen scene: a built-in one or the user's own picture. */
    val scene: String = Scene.DEFAULT_ID,
    val largeClock: Boolean = true,
    val showWeather: Boolean = true,
    val showAlarm: Boolean = true,
    val showEvents: Boolean = false,
    val drawerRecent: Boolean = true,
    val drawerIndex: Boolean = true,
    val drawerSections: Boolean = true,
    val swipeDown: SwipeDownAction = SwipeDownAction.Notifications,
    val doubleTap: DoubleTapAction = DoubleTapAction.Nothing,
    val haptics: Boolean = true,
    val stapelOpensSettings: Boolean = false,
    val searchMic: Boolean = true,
    val media: Boolean = false,
    val notificationDots: Boolean = true,
    val searchContacts: Boolean = false,
    /** Follow [schedule] (and the season) instead of [scene]. */
    val autoScene: Boolean = false,
    val schedule: List<SceneSlot> = SceneSchedule.DEFAULT,
    val snowInWinter: Boolean = true,
    /** Shown all day in winter while [snowInWinter] is on. */
    val winterScene: String = Scene.WINTER_ID,
) {
    /** Id of the scene to draw at [time]: the scheduled one if switched on, else the chosen one. */
    fun sceneAt(time: LocalDateTime, sun: SunTimes? = null): String = when {
        !autoScene -> scene
        snowInWinter && isWinter(time) -> winterScene
        else -> SceneSchedule.slotAt(schedule, time.hour * 60 + time.minute, sun)?.scene ?: scene
    }

    fun save(sp: SharedPreferences) {
        sp.edit()
            .putString("scene", scene)
            .putBoolean("largeClock", largeClock)
            .putBoolean("showWeather", showWeather)
            .putBoolean("showAlarm", showAlarm)
            .putBoolean("showEvents", showEvents)
            .putBoolean("drawerRecent", drawerRecent)
            .putBoolean("drawerIndex", drawerIndex)
            .putBoolean("drawerSections", drawerSections)
            .putString("swipeDown", swipeDown.name)
            .putString("doubleTap", doubleTap.name)
            .putBoolean("haptics", haptics)
            .putBoolean("stapelOpensSettings", stapelOpensSettings)
            .putBoolean("searchMic", searchMic)
            .putBoolean("media", media)
            .putBoolean("notificationDots", notificationDots)
            .putBoolean("searchContacts", searchContacts)
            .putBoolean("autoScene", autoScene)
            .putString("schedule", SceneSchedule.encode(schedule))
            .putBoolean("snowInWinter", snowInWinter)
            .putString("winterScene", winterScene)
            .apply()
    }

    companion object {
        fun load(sp: SharedPreferences): LauncherSettings {
            val d = LauncherSettings()
            return LauncherSettings(
                scene = sp.getString("scene", null) ?: d.scene,
                largeClock = sp.getBoolean("largeClock", d.largeClock),
                showWeather = sp.getBoolean("showWeather", d.showWeather),
                showAlarm = sp.getBoolean("showAlarm", d.showAlarm),
                showEvents = sp.getBoolean("showEvents", d.showEvents),
                drawerRecent = sp.getBoolean("drawerRecent", d.drawerRecent),
                drawerIndex = sp.getBoolean("drawerIndex", d.drawerIndex),
                drawerSections = sp.getBoolean("drawerSections", d.drawerSections),
                swipeDown = enumOr(sp.getString("swipeDown", null), d.swipeDown),
                doubleTap = enumOr(sp.getString("doubleTap", null), d.doubleTap),
                haptics = sp.getBoolean("haptics", d.haptics),
                stapelOpensSettings = sp.getBoolean("stapelOpensSettings", d.stapelOpensSettings),
                searchMic = sp.getBoolean("searchMic", d.searchMic),
                media = sp.getBoolean("media", d.media),
                notificationDots = sp.getBoolean("notificationDots", d.notificationDots),
                searchContacts = sp.getBoolean("searchContacts", d.searchContacts),
                autoScene = sp.getBoolean("autoScene", d.autoScene),
                schedule = SceneSchedule.decode(sp.getString("schedule", null)) ?: d.schedule,
                snowInWinter = sp.getBoolean("snowInWinter", d.snowInWinter),
                winterScene = sp.getString("winterScene", null) ?: d.winterScene,
            )
        }

        /** An unknown stored name (renamed or removed option) falls back to the default. */
        private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E = enumValues<E>().firstOrNull { it.name == name } ?: default
    }
}
