package dev.korbi.helgeland

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.os.UserHandle
import java.text.Collator

data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
) {
    /** Stable id used for the home grid; includes the user so work-profile twins stay distinct. */
    val key: String = "${component.flattenToString()}#${user.hashCode()}"
}

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

    fun activityInfo(app: AppEntry): LauncherActivityInfo? =
        launcherApps.getActivityList(app.component.packageName, app.user)
            .firstOrNull { it.componentName == app.component }

    fun launch(app: AppEntry) {
        launcherApps.startMainActivity(app.component, app.user, null, null)
    }

    fun openAppInfo(app: AppEntry) {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }

    /** Calls [onChange] whenever an app is installed, removed or updated. */
    fun watch(onChange: () -> Unit): () -> Unit {
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) = onChange()
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
    )
}
