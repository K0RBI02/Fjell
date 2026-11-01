package dev.korbi.fjell

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** What the clock line shows of the weather. */
enum class Sky { Clear, ClearNight, PartlyCloudy, Cloudy, Fog, Rain, Snow, Thunder }

data class WeatherNow(val temperature: Int, val sky: Sky)

/** Current weather from Open-Meteo (free, no API key) at the phone's coarse location. */
object Weather {
    /** Callers must hold ACCESS_COARSE_LOCATION. Returns null on any failure. */
    @SuppressLint("MissingPermission")
    suspend fun current(context: Context): WeatherNow? = runCatching {
        val location = lastLocation(context) ?: return null
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m,weather_code,is_day",
                location.latitude,
                location.longitude,
            ),
        )
        val body = withContext(Dispatchers.IO) {
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
        val current = JSONObject(body).getJSONObject("current")
        WeatherNow(
            temperature = current.getDouble("temperature_2m").roundToInt(),
            sky = skyFor(current.optInt("weather_code", 0), isDay = current.optInt("is_day", 1) == 1),
        )
    }.getOrNull()

    /** Maps a WMO weather code (as Open-Meteo reports it) to one of our glyphs. */
    fun skyFor(code: Int, isDay: Boolean): Sky = when (code) {
        0 -> if (isDay) Sky.Clear else Sky.ClearNight
        1, 2 -> Sky.PartlyCloudy
        3 -> Sky.Cloudy
        45, 48 -> Sky.Fog
        in 51..67, in 80..82 -> Sky.Rain
        in 71..77, 85, 86 -> Sky.Snow
        in 95..99 -> Sky.Thunder
        else -> Sky.Cloudy
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastLocation(context: Context): Location? {
        val lm = context.getSystemService(LocationManager::class.java)
        val known = lm.getProviders(true)
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (known != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return known

        // Nothing cached yet (fresh boot): ask the network provider for a one-off fix.
        if (!lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return null
        return withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, context.mainExecutor) { cont.resume(it) }
            }
        }
    }
}
