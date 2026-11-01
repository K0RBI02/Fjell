package dev.korbi.fjell

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** Sunrise and sunset as minutes of the local day; null where the sun doesn't rise or set (polar days and nights). */
data class SunTimes(val sunrise: Int?, val sunset: Int?)

/** A coarse position, good enough for sun times (a few km off shifts them by seconds). */
data class Place(val latitude: Double, val longitude: Double)

/** Sun times from the standard sunrise equation; accurate to a minute or two, no network needed. */
object Sun {
    fun times(date: LocalDate, place: Place, zone: ZoneId): SunTimes {
        val days = ceil(date.toEpochDay() + UNIX_EPOCH_JD - J2000 + 0.0008) // whole days since J2000
        val meanSolarTime = days - place.longitude / 360
        val anomaly = rad((357.5291 + 0.98560028 * meanSolarTime) % 360)
        val center = 1.9148 * sin(anomaly) + 0.02 * sin(2 * anomaly) + 0.0003 * sin(3 * anomaly)
        val longitude = rad((deg(anomaly) + center + 180 + 102.9372) % 360)
        val transit = J2000 + meanSolarTime + 0.0053 * sin(anomaly) - 0.0069 * sin(2 * longitude)
        val declination = asin(sin(longitude) * sin(rad(23.4397)))
        val lat = rad(place.latitude)
        // -0.833°: the sun's radius plus refraction, so "sunrise" is the first glint, as everyone uses it.
        val cosHourAngle = (sin(rad(-0.833)) - sin(lat) * sin(declination)) / (cos(lat) * cos(declination))
        if (cosHourAngle !in -1.0..1.0) return SunTimes(null, null)
        val halfDay = deg(acos(cosHourAngle)) / 360
        return SunTimes(minuteOfDay(transit - halfDay, zone), minuteOfDay(transit + halfDay, zone))
    }

    private fun minuteOfDay(julianDay: Double, zone: ZoneId): Int {
        val millis = ((julianDay - UNIX_EPOCH_JD) * 86_400_000).toLong()
        val time = Instant.ofEpochMilli(millis).atZone(zone)
        return time.hour * 60 + time.minute
    }

    private fun rad(degrees: Double) = degrees * PI / 180
    private fun deg(radians: Double) = radians * 180 / PI

    private const val J2000 = 2451545.0
    private const val UNIX_EPOCH_JD = 2440587.5
}
