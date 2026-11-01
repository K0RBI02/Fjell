package dev.korbi.fjell

import android.content.ComponentName
import android.os.Process
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Fixed inputs for the screenshot tests, so every run renders exactly the same pixels. */
object Fixtures {
    /** Sunday, 1 Nov 2026, 10:09. */
    val clock: Clock = Clock.fixed(Instant.parse("2026-11-01T10:09:00Z"), ZoneOffset.UTC)

    private val names = listOf(
        "Camera", "Discord", "Firefox", "Google", "Home Assistant", "Instagram", "Maps", "Messages",
        "Outlook", "Phone", "Schulmanager", "Settings", "Snapchat", "Spotify", "WhatsApp", "YouTube",
    )

    val apps: List<AppEntry> = names.map { name ->
        val pkg = "app." + name.lowercase().replace(" ", "")
        AppEntry(name, ComponentName(pkg, "$pkg.Main"), Process.myUserHandle(), installedAt = 0L)
    }

    fun key(name: String) = apps.first { it.label == name }.key

    /** Search bar over two slots, five apps, the 3x3 button in the last slot of row two. */
    val grid: List<String?> = listOf(
        GridSlots.SEARCH_KEY, GridSlots.COVERED, key("WhatsApp"), key("Outlook"),
        key("Spotify"), key("Home Assistant"), key("Settings"), GridSlots.STAPEL_KEY,
        null, null, null, null,
    )
}

/**
 * Stands in for MainActivity: does nothing and draws every app icon as a plain bubble
 * with a dot in the app's own colour, so icon packs and installed apps don't matter.
 */
class FakeActions : LauncherActions {
    override fun launch(app: AppEntry) = Unit
    override suspend fun shortcuts(app: AppEntry, iconPx: Int) = emptyList<AppShortcut>()
    override fun launchShortcut(id: String) = Unit
    override fun appInfo(app: AppEntry) = Unit
    override fun uninstall(app: AppEntry) = Unit
    override fun toggleGrid(app: AppEntry) = Unit
    override fun moveInGrid(from: Int, to: Int) = Unit
    override fun setStapelShown(shown: Boolean) = Unit
    override fun setSearchShown(shown: Boolean) = Unit
    override fun setSearchWidth(span: Int) = Unit
    override fun openVoiceSearch() = Unit
    override fun openGoogleSearch() = Unit
    override fun searchWeb(query: String) = Unit
    override fun updateSettings(change: (LauncherSettings) -> LauncherSettings) = Unit
    override suspend fun searchContacts(query: String) = emptyList<ContactHit>()
    override fun openContact(contact: ContactHit) = Unit
    override fun callContact(contact: ContactHit) = Unit
    override fun copyToClipboard(text: String) = Unit
    override fun lockScreen() = Unit
    override fun hasMediaAccess() = true
    override fun openMediaAccessSettings() = Unit
    override fun mediaPlayPause() = Unit
    override fun mediaNext() = Unit
    override fun mediaOpen() = Unit
    override fun openClock() = Unit
    override fun openCalendar() = Unit
    override fun openNextAlarm() = Unit
    override fun openEvent(event: CalendarEvent) = Unit
    override fun expandNotifications() = Unit
    override suspend fun icon(app: AppEntry, sizePx: Int) = bubble(app, sizePx)
    override suspend fun previewIcon(app: AppEntry, style: IconStyle, sizePx: Int) = bubble(app, sizePx)
    override suspend fun packIconNames(pkg: String) = emptyList<String>()
    override suspend fun packIcon(pkg: String, name: String, sizePx: Int): ImageBitmap? = null
    override fun setGlobalIconStyle(style: IconStyle) = Unit
    override fun iconOverride(app: AppEntry): IconStyle? = null
    override fun setAppIconStyle(app: AppEntry, style: IconStyle?) = Unit
    override fun pickCustomIcon(app: AppEntry) = Unit
    override fun setDotHidden(app: AppEntry, hide: Boolean) = Unit
    override fun isDotHidden(app: AppEntry) = false
    override fun setHidden(app: AppEntry, hide: Boolean) = Unit
    override fun isHidden(app: AppEntry) = false
    override fun rename(app: AppEntry, label: String) = Unit
    override fun openHomeSettings() = Unit

    private fun bubble(app: AppEntry, size: Int): ImageBitmap {
        val bitmap = ImageBitmap(size, size)
        val canvas = Canvas(bitmap)
        val side = size.toFloat()
        canvas.drawRoundRect(0f, 0f, side, side, side * 0.36f, side * 0.36f, Paint().apply { color = Color(0xFFF3EFF4) })
        val hue = (app.label.hashCode() and 0xFFFFFF) or 0xFF000000.toInt()
        canvas.drawCircle(Offset(side / 2, side / 2), side * 0.2f, Paint().apply { color = Color(hue) })
        return bitmap
    }
}
