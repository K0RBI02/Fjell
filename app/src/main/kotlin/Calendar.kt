package dev.korbi.fjell

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [day] is set for all-day events (birthdays, holidays), which have no meaningful [begin]. */
data class CalendarEvent(val id: Long, val title: String, val begin: Long, val day: LocalDate? = null)

object Calendar {
    /** How far ahead an event may be to replace the alarm on the home screen. */
    const val HORIZON_MS = 4 * 60 * 60 * 1000L

    fun hasAccess(context: Context): Boolean = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /**
     * Timed events from visible calendars starting between [now] and a bit past the
     * horizon, soonest first. Fetching beyond the horizon lets the UI roll over to
     * the next one without asking again. Empty without permission.
     */
    suspend fun upcoming(context: Context, now: Long): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasAccess(context)) return@withContext emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now)
            ContentUris.appendId(it, now + 2 * HORIZON_MS)
        }.build()
        val columns = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
        )
        val selection = "${CalendarContract.Instances.ALL_DAY} = 0 AND ${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "${CalendarContract.Instances.BEGIN} >= ? AND ${CalendarContract.Instances.STATUS} != ${CalendarContract.Instances.STATUS_CANCELED}"
        runCatching {
            context.contentResolver.query(uri, columns, selection, arrayOf(now.toString()), "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < 5) {
                        val title = cursor.getString(1)?.trim().takeUnless { it.isNullOrEmpty() } ?: continue
                        add(CalendarEvent(cursor.getLong(0), title, cursor.getLong(2)))
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    /** All-day events show until this hour; in the evening tomorrow's alarm matters more. */
    const val ALL_DAY_UNTIL_HOUR = 18

    /** Today's all-day events from visible calendars. Empty without permission. */
    suspend fun allDay(context: Context, today: LocalDate): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (!hasAccess(context)) return@withContext emptyList()
        // All-day events are stored in UTC; the by-day table already maps them to local days.
        val julianDay = today.toEpochDay() + JULIAN_DAY_OF_EPOCH
        val uri = CalendarContract.Instances.CONTENT_BY_DAY_URI.buildUpon().also {
            ContentUris.appendId(it, julianDay)
            ContentUris.appendId(it, julianDay)
        }.build()
        val columns = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE)
        val selection = "${CalendarContract.Instances.ALL_DAY} = 1 AND ${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "${CalendarContract.Instances.STATUS} != ${CalendarContract.Instances.STATUS_CANCELED}"
        runCatching {
            context.contentResolver.query(uri, columns, selection, null, "${CalendarContract.Instances.TITLE} ASC")?.use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < 3) {
                        val title = cursor.getString(1)?.trim().takeUnless { it.isNullOrEmpty() } ?: continue
                        add(CalendarEvent(cursor.getLong(0), title, begin = 0L, day = today))
                    }
                }
            }
        }.getOrNull().orEmpty()
    }

    /** The timed event to show at [now]: the first one still ahead and within the horizon. */
    fun next(events: List<CalendarEvent>, now: Long): CalendarEvent? = events.firstOrNull { it.day == null && it.begin - now in 0..HORIZON_MS }

    /** Today's all-day event to show at [now], if it's not evening yet. */
    fun allDayNow(events: List<CalendarEvent>, now: LocalDateTime): CalendarEvent? = events.firstOrNull { it.day == now.toLocalDate() }?.takeIf { now.hour < ALL_DAY_UNTIL_HOUR }

    private const val JULIAN_DAY_OF_EPOCH = 2440588L
}
