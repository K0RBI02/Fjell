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

    /** Keys of apps left out of the stack listing (search still finds them). */
    var hidden: Set<String>
        get() = sp.getStringSet("hidden", null)?.toSet() ?: emptySet()
        set(value) = sp.edit().putStringSet("hidden", value).apply()

    /** How often each app was started from the launcher, by [AppEntry.key]. */
    var launchCounts: Map<String, Int>
        get() = sp.getString("launchCounts", null)
            ?.lineSequence()
            ?.mapNotNull { line -> line.split('\t').takeIf { it.size == 2 }?.let { (key, n) -> n.toIntOrNull()?.let { key to it } } }
            ?.toMap()
            ?: emptyMap()
        set(value) = sp.edit().putString("launchCounts", value.entries.joinToString("\n") { "${it.key}\t${it.value}" }).apply()

    /** Keys of apps whose notification dot is switched off. */
    var noDots: Set<String>
        get() = sp.getStringSet("noDots", null)?.toSet() ?: emptySet()
        set(value) = sp.edit().putStringSet("noDots", value).apply()

    /** App keys, most recently launched first. */
    var recent: List<String>
        get() = sp.getString("recent", null)?.split('\n')?.filter { it.isNotBlank() } ?: emptyList()
        set(value) = sp.edit().putString("recent", value.joinToString("\n")).apply()

    var settings: LauncherSettings
        get() = LauncherSettings.load(sp)
        set(value) = value.save(sp)

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
