package dev.korbi.helgeland

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/** Current temperature from Open-Meteo (free, no API key) at the phone's coarse location. */
object Weather {
    /** Callers must hold ACCESS_COARSE_LOCATION. Returns null on any failure. */
    @SuppressLint("MissingPermission")
    suspend fun currentTemperature(context: Context): Int? = runCatching {
        val location = lastLocation(context) ?: return null
        val url = URL(
            String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&current=temperature_2m",
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
        JSONObject(body).getJSONObject("current").getDouble("temperature_2m").roundToInt()
    }.getOrNull()

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
