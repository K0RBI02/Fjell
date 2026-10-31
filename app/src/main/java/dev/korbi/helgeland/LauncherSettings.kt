package dev.korbi.helgeland

import android.content.SharedPreferences
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color

/**
 * The four Sør-Helgeland scenes. Positions are fractions of the image height:
 * [gridTop] is where the calm bottom area for the app grid starts, and the media
 * line sits between [mediaTop] and [mediaBottom] without covering any motif.
 */
enum class Scene(
    @DrawableRes val res: Int,
    val label: String,
    val gridTop: Float,
    val mediaTop: Float,
    val mediaBottom: Float,
    /** Clock and date color, chosen against the sky. */
    val ink: Color,
    /** Media line color, chosen against its band. */
    val mediaInk: Color,
    /** Light sky: status bar icons must be dark. */
    val lightSky: Boolean,
) {
    Mountain(R.drawable.wp_mountain, "Mountain", 0.6998f, 0.6567f, 0.6998f, Color.White, Color.White, lightSky = false),
    Barn(R.drawable.wp_barn, "Barn", 0.817f, 0.655f, 0.705f, Color.White, DarkInk, lightSky = false),
    Pole(R.drawable.wp_pole, "Pole", 0.648f, 0.6049f, 0.648f, DarkInk, Color.White, lightSky = true),
    Snow(R.drawable.wp_snow, "Snow", 0.69f, 0.6439f, 0.69f, DarkInk, DarkInk, lightSky = true),
}

private val DarkInk get() = Color(0xFF2E3550)

enum class SwipeDownAction(val label: String) {
    Notifications("Notifications"),
    Search("Search"),
    Nothing("Nothing"),
}

enum class DoubleTapAction(val label: String) {
    Nothing("Nothing"),
    Search("Search"),
    Stapel("Stack"),
    Settings("Settings"),
}

/** Every user-adjustable launcher option in one place. Grid layout and icons live elsewhere. */
data class LauncherSettings(
    val scene: Scene = Scene.Mountain,
    val largeClock: Boolean = true,
    val showWeather: Boolean = true,
    val showAlarm: Boolean = true,
    val drawerRecent: Boolean = true,
    val drawerIndex: Boolean = true,
    val drawerSections: Boolean = true,
    val swipeDown: SwipeDownAction = SwipeDownAction.Notifications,
    val doubleTap: DoubleTapAction = DoubleTapAction.Nothing,
    val haptics: Boolean = true,
    val stapelOpensSettings: Boolean = false,
    val searchMic: Boolean = true,
    val media: Boolean = false,
) {
    fun save(sp: SharedPreferences) {
        sp.edit()
            .putString("scene", scene.name)
            .putBoolean("largeClock", largeClock)
            .putBoolean("showWeather", showWeather)
            .putBoolean("showAlarm", showAlarm)
            .putBoolean("drawerRecent", drawerRecent)
            .putBoolean("drawerIndex", drawerIndex)
            .putBoolean("drawerSections", drawerSections)
            .putString("swipeDown", swipeDown.name)
            .putString("doubleTap", doubleTap.name)
            .putBoolean("haptics", haptics)
            .putBoolean("stapelOpensSettings", stapelOpensSettings)
            .putBoolean("searchMic", searchMic)
            .putBoolean("media", media)
            .apply()
    }

    companion object {
        fun load(sp: SharedPreferences): LauncherSettings {
            val d = LauncherSettings()
            return LauncherSettings(
                scene = enumOr(sp.getString("scene", null), d.scene),
                largeClock = sp.getBoolean("largeClock", d.largeClock),
                showWeather = sp.getBoolean("showWeather", d.showWeather),
                showAlarm = sp.getBoolean("showAlarm", d.showAlarm),
                drawerRecent = sp.getBoolean("drawerRecent", d.drawerRecent),
                drawerIndex = sp.getBoolean("drawerIndex", d.drawerIndex),
                drawerSections = sp.getBoolean("drawerSections", d.drawerSections),
                swipeDown = enumOr(sp.getString("swipeDown", null), d.swipeDown),
                doubleTap = enumOr(sp.getString("doubleTap", null), d.doubleTap),
                haptics = sp.getBoolean("haptics", d.haptics),
                stapelOpensSettings = sp.getBoolean("stapelOpensSettings", d.stapelOpensSettings),
                searchMic = sp.getBoolean("searchMic", d.searchMic),
                media = sp.getBoolean("media", d.media),
            )
        }

        /** An unknown stored name (renamed or removed option) falls back to the default. */
        private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: default
    }
}
