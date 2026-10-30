package dev.korbi.helgeland

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("helgeland", Context.MODE_PRIVATE)

    /** One entry per home grid slot: an [AppEntry.key], or null for an empty slot. */
    var grid: List<String?>
        get() {
            val stored = sp.getString("grid", null)?.split('\n')?.map { it.trim().ifEmpty { null } } ?: emptyList()
            return List(MainActivity.GRID_SIZE) { stored.getOrNull(it) }
        }
        set(value) = sp.edit().putString("grid", value.joinToString("\n") { it ?: "" }).apply()

    val hasChosenGrid: Boolean get() = sp.contains("grid")

    /** What the 3x3 button does: open the stack (default) or the settings. */
    var stapelOpensSettings: Boolean
        get() = sp.getBoolean("stapelOpensSettings", false)
        set(value) = sp.edit().putBoolean("stapelOpensSettings", value).apply()

    var searchMic: Boolean
        get() = sp.getBoolean("searchMic", true)
        set(value) = sp.edit().putBoolean("searchMic", value).apply()

    var searchLens: Boolean
        get() = sp.getBoolean("searchLens", false)
        set(value) = sp.edit().putBoolean("searchLens", value).apply()

    var stapelButtonPlaced: Boolean
        get() = sp.getBoolean("stapelButtonPlaced", false)
        set(value) = sp.edit().putBoolean("stapelButtonPlaced", value).apply()

    /** Default [IconStyle.id] for all apps; null means the Nothing pack. */
    var iconStyle: String?
        get() = sp.getString("iconSource", null)
        set(value) = sp.edit().putString("iconSource", value).apply()

    /** A name the user gave the app, replacing its own label. */
    fun label(appKey: String): String? = sp.getString("label:$appKey", null)

    fun setLabel(appKey: String, value: String?) {
        sp.edit().apply { if (value == null) remove("label:$appKey") else putString("label:$appKey", value) }.apply()
    }

    /** Per-app [IconStyle.id] that beats [iconStyle], or null to follow it. */
    fun iconOverride(appKey: String): String? = sp.getString("icon:$appKey", null)

    fun setIconOverride(appKey: String, value: String?) {
        sp.edit().apply { if (value == null) remove("icon:$appKey") else putString("icon:$appKey", value) }.apply()
    }

    var lastTemperature: Int?
        get() = if (sp.contains("temperature")) sp.getInt("temperature", 0) else null
        set(value) = sp.edit().apply { if (value == null) remove("temperature") else putInt("temperature", value) }.apply()

    var askedForLocation: Boolean
        get() = sp.getBoolean("askedForLocation", false)
        set(value) = sp.edit().putBoolean("askedForLocation", value).apply()
}
