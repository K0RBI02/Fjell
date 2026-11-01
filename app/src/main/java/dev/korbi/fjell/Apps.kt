package dev.korbi.fjell

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Drawable
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import java.text.Collator

data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    /** When the app was first installed, for spotting new ones. */
    val installedAt: Long = 0L,
) {
    /** Stable id used for the home grid; includes the user so work-profile twins stay distinct. */
    val key: String = "${component.flattenToString()}#${user.hashCode()}"

    companion object {
        /** Whether [key] belongs to an app of [packageName] for the user with [userHash] (see [key]). */
        fun isOf(key: String, packageName: String, userHash: Int): Boolean = key.startsWith("$packageName/") && key.endsWith("#$userHash")
    }
}

/** One of an app's quick actions, ready to show in its menu. */
data class AppShortcut(val id: String, val label: String, val icon: ImageBitmap?)

class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    /** All launchable apps, sorted by label. [renamed] supplies user-chosen names by [AppEntry.key]. */
    fun load(renamed: (String) -> String? = { null }): List<AppEntry> {
        val self = context.packageName
        return launcherApps.profiles
            .flatMap { user -> launcherApps.getActivityList(null, user) }
            .filter { it.componentName.packageName != self }
            .map { it.toEntry() }
            .map { entry -> renamed(entry.key)?.let { entry.copy(label = it) } ?: entry }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun activityInfo(app: AppEntry): LauncherActivityInfo? = launcherApps.getActivityList(app.component.packageName, app.user)
        .firstOrNull { it.componentName == app.component }

    fun launch(app: AppEntry) {
        launcherApps.startMainActivity(app.component, app.user, null, null)
    }

    fun openAppInfo(app: AppEntry) {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }

    /**
     * The app's quick actions (its manifest shortcuts first, then dynamic ones by rank),
     * as the system launcher would show them. Only the default launcher may ask.
     */
    fun shortcuts(app: AppEntry, limit: Int = 4): List<ShortcutInfo> {
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(app.component.packageName)
            .setActivity(app.component)
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC)
        return runCatching { launcherApps.getShortcuts(query, app.user) }.getOrNull().orEmpty()
            .filter { it.isEnabled }
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            .take(limit)
    }

    fun shortcutIcon(shortcut: ShortcutInfo, density: Int): Drawable? = runCatching { launcherApps.getShortcutIconDrawable(shortcut, density) }.getOrNull()

    fun startShortcut(shortcut: ShortcutInfo) {
        launcherApps.startShortcut(shortcut, null, null)
    }

    /**
     * Calls [onChange] whenever an app is installed, removed or updated, and
     * [onRemoved] first when it's really uninstalled (not just updated).
     */
    fun watch(onRemoved: (String, UserHandle) -> Unit, onChange: () -> Unit): () -> Unit {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                onRemoved(packageName, user)
                onChange()
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) = onChange()
            override fun onPackageChanged(packageName: String, user: UserHandle) = onChange()
            override fun onPackagesAvailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = onChange()
            override fun onPackagesUnavailable(names: Array<out String>, user: UserHandle, replacing: Boolean) = onChange()
        }
        launcherApps.registerCallback(callback)
        return { launcherApps.unregisterCallback(callback) }
    }

    private fun LauncherActivityInfo.toEntry() = AppEntry(
        label = label.toString(),
        component = componentName,
        user = user,
        installedAt = firstInstallTime,
    )
}
