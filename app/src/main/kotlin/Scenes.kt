package dev.korbi.fjell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.LruCache
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * A wallpaper and where its calm areas are. Positions are fractions of the image height:
 * [gridTop] is where the calm bottom area for the app grid starts, and the media line sits
 * between [mediaTop] and [mediaBottom]. Built-in scenes are drawn in code ([art]); the
 * user's own come from a picture in app storage ([file], [width] × [height] pixels).
 */
@Immutable
data class Scene(
    val id: String,
    val gridTop: Float,
    val mediaTop: Float,
    val mediaBottom: Float,
    /** Clock and date color, chosen against the sky. */
    val ink: Color,
    /** Media line color, chosen against its band. */
    val mediaInk: Color,
    /** Light sky: status bar icons must be dark. */
    val lightSky: Boolean,
    val art: SceneArt? = null,
    val name: String = "",
    val file: String? = null,
    val width: Int = 0,
    val height: Int = 0,
) {
    val isCustom get() = art == null

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("file", file)
        .put("width", width)
        .put("height", height)
        .put("gridTop", gridTop.toDouble())
        .put("mediaTop", mediaTop.toDouble())
        .put("mediaBottom", mediaBottom.toDouble())
        .put("ink", ink.value.toLong())
        .put("mediaInk", mediaInk.value.toLong())
        .put("lightSky", lightSky)

    companion object {
        const val DEFAULT_ID = "fjord"
        const val WINTER_ID = "winter"
        val BUILT_IN: List<Scene> get() = SceneArt.entries.map { it.scene }

        /** The media band sits this far (in image height) above the grid. */
        const val MEDIA_BAND = 0.043f

        fun fromJson(json: JSONObject): Scene? = runCatching {
            Scene(
                id = json.getString("id"),
                name = json.optString("name"),
                file = json.getString("file"),
                width = json.getInt("width"),
                height = json.getInt("height"),
                gridTop = json.getDouble("gridTop").toFloat(),
                mediaTop = json.getDouble("mediaTop").toFloat(),
                mediaBottom = json.getDouble("mediaBottom").toFloat(),
                ink = Color(json.getLong("ink").toULong()),
                mediaInk = Color(json.getLong("mediaInk").toULong()),
                lightSky = json.getBoolean("lightSky"),
            )
        }.getOrNull()

        fun listToJson(scenes: List<Scene>): String = JSONArray(scenes.map(Scene::toJson)).toString()

        fun listFromJson(text: String?): List<Scene> = runCatching {
            val array = JSONArray(text ?: return emptyList())
            (0 until array.length()).mapNotNull { fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }
}

/** The scene with [id], or the first one if it's gone (a deleted picture, an old name). */
fun List<Scene>.resolve(id: String): Scene = firstOrNull { it.id == id } ?: first()

@Composable
fun Scene.label(): String = art?.let { stringResource(it.label) } ?: name

private val DarkInk = Color(0xFF2E3550)

/** Flat, layered landscapes drawn in code, so the app ships with no pictures of its own. */
enum class SceneArt(@StringRes val label: Int, private val id: String) {
    Fjord(R.string.scene_fjord, Scene.DEFAULT_ID),
    Dusk(R.string.scene_dusk, "dusk"),
    Winter(R.string.scene_winter, Scene.WINTER_ID),
    ;

    val scene: Scene
        get() = when (this) {
            Fjord -> Scene(id, gridTop = 0.70f, mediaTop = 0.657f, mediaBottom = 0.70f, ink = Color.White, mediaInk = Color.White, lightSky = false, art = this)
            Dusk -> Scene(id, gridTop = 0.69f, mediaTop = 0.647f, mediaBottom = 0.69f, ink = Color.White, mediaInk = Color.White, lightSky = false, art = this)
            Winter -> Scene(id, gridTop = 0.692f, mediaTop = 0.649f, mediaBottom = 0.692f, ink = DarkInk, mediaInk = DarkInk, lightSky = true, art = this)
        }
}

/** Draws a [SceneArt] at any size; shapes are given in fractions of a 9:20 phone screen. */
class ScenePainter(private val art: SceneArt) : Painter() {
    override val intrinsicSize = Size(1116f, 2484f)

    override fun DrawScope.onDraw() {
        val w = size.width
        val h = size.height
        fun band(color: Long, top: Float, bottom: Float) = drawRect(Color(color), Offset(0f, top * h), Size(w, (bottom - top) * h))
        fun ridge(color: Long, vararg points: Pair<Float, Float>, bottom: Float) {
            val path = Path().apply {
                moveTo(0f, bottom * h)
                points.forEach { (x, y) -> lineTo(x * w, y * h) }
                lineTo(w, bottom * h)
                close()
            }
            drawPath(path, Color(color))
        }
        fun pine(color: Long, x: Float, base: Float, height: Float) {
            val half = height * 0.32f
            for (tier in 0 until 3) {
                val top = base - height * (0.42f + 0.29f * tier)
                val bottom = base - height * (0.29f * tier)
                val spread = half * (1f - 0.22f * tier)
                drawPath(
                    Path().apply {
                        moveTo(x * w, top * h)
                        lineTo((x - spread) * w, bottom * h)
                        lineTo((x + spread) * w, bottom * h)
                        close()
                    },
                    Color(color),
                )
            }
        }
        when (art) {
            SceneArt.Fjord -> {
                band(0xFFA7B6C8, 0f, 0.62f)
                drawCircle(Color(0xFFF1E4CC), 0.075f * w, Offset(0.72f * w, 0.30f * h))
                ridge(0xFF8496B0, 0.18f to 0.41f, 0.34f to 0.47f, 0.55f to 0.335f, 0.78f to 0.45f, 1f to 0.385f, bottom = 0.62f)
                ridge(0xFF2A3347, 0.22f to 0.475f, 0.40f to 0.55f, 0.63f to 0.495f, 0.82f to 0.545f, 1f to 0.52f, bottom = 0.62f)
                band(0xFF5A7092, 0.60f, 0.652f)
                band(0xFFDCD4C2, 0.652f, 0.657f)
                band(0xFF6F6987, 0.657f, 0.70f)
                band(0xFF8E89A2, 0.70f, 1f)
            }
            SceneArt.Dusk -> {
                band(0xFF2E3550, 0f, 0.22f)
                band(0xFF5E5274, 0.22f, 0.33f)
                band(0xFFB9847F, 0.33f, 0.41f)
                band(0xFFE5BE9D, 0.41f, 0.66f)
                drawCircle(Color(0xFFF3E6D0), 0.035f * w, Offset(0.26f * w, 0.17f * h))
                ridge(0xFF1C2233, 0.15f to 0.445f, 0.30f to 0.50f, 0.50f to 0.385f, 0.70f to 0.49f, 0.86f to 0.43f, 1f to 0.48f, bottom = 0.66f)
                listOf(0.08f to 0.040f, 0.16f to 0.055f, 0.83f to 0.05f, 0.92f to 0.065f).forEach { (x, size) -> pine(0xFF121722, x, 0.648f, size) }
                band(0xFF3A3450, 0.647f, 0.69f)
                band(0xFF514866, 0.69f, 1f)
            }
            SceneArt.Winter -> {
                band(0xFFD7E0E9, 0f, 0.66f)
                drawCircle(Color(0xFFF5F2EA), 0.06f * w, Offset(0.30f * w, 0.26f * h))
                ridge(0xFFB9C7D5, 0.25f to 0.47f, 0.48f to 0.40f, 0.72f to 0.46f, 1f to 0.43f, bottom = 0.66f)
                ridge(0xFFEDF1F5, 0.30f to 0.56f, 0.62f to 0.52f, 1f to 0.575f, bottom = 0.66f)
                listOf(0.14f to 0.07f, 0.22f to 0.05f, 0.70f to 0.06f, 0.79f to 0.085f, 0.88f to 0.055f).forEach { (x, size) -> pine(0xFF2E3A4A, x, 0.645f, size) }
                band(0xFFC2CDD9, 0.649f, 0.692f)
                band(0xFFDCE3EA, 0.692f, 1f)
            }
        }
    }
}

/** A plain color in the picture's shape, while the real one is still loading. */
private class PlaceholderPainter(override val intrinsicSize: Size) : Painter() {
    override fun DrawScope.onDraw() = drawRect(Color(0xFF1C2233))
}

@Composable
fun rememberScenePainter(scene: Scene): Painter {
    scene.art?.let { art -> return remember(art) { ScenePainter(art) } }
    val context = LocalContext.current
    val file = scene.file
    val bitmap by produceState(file?.let(ScenePictures::cached), file) {
        if (file != null) value = ScenePictures.load(context, file)
    }
    return remember(bitmap, scene.width, scene.height) {
        bitmap?.let { BitmapPainter(it.asImageBitmap()) } ?: PlaceholderPainter(Size(max(scene.width, 1).toFloat(), max(scene.height, 1).toFloat()))
    }
}

/** The user's own wallpapers: stored downscaled in app storage, decoded once and kept in memory. */
object ScenePictures {
    /** Longest side kept; plenty for a phone screen and its blur. */
    private const val MAX_PX = 2800
    private val memory = object : LruCache<String, Bitmap>(64 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    private fun dir(context: Context) = File(context.filesDir, "wallpapers")

    fun cached(file: String): Bitmap? = memory.get(file)

    suspend fun load(context: Context, file: String): Bitmap? = memory.get(file) ?: withContext(Dispatchers.IO) {
        // Software, not hardware: the colors are read back from it when the grid line moves.
        runCatching { ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(dir(context), file))) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE } }
            .getOrNull()
            ?.also { memory.put(file, it) }
    }

    /** Copies the picture at [uri] into app storage and makes a scene of it with guessed positions. */
    suspend fun import(context: Context, uri: Uri, name: String): Scene? = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_PX) {
                    val scale = MAX_PX.toFloat() / longest
                    decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            save(context, bitmap, name, gridTop = 0.70f)
        }.getOrNull()
    }

    /** Stores [bitmap] and returns its scene; the colors are read from the picture itself. */
    fun save(context: Context, bitmap: Bitmap, name: String, gridTop: Float): Scene {
        val id = "custom:" + UUID.randomUUID()
        val file = id.removePrefix("custom:") + ".jpg"
        dir(context).mkdirs()
        File(dir(context), file).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        memory.put(file, bitmap)
        val base = Scene(id, gridTop, gridTop - Scene.MEDIA_BAND, gridTop, Color.White, Color.White, false, name = name, file = file, width = bitmap.width, height = bitmap.height)
        return withColors(base, bitmap)
    }

    /** Re-reads ink colors after the grid line moved, since the media band moved with it. */
    fun withColors(scene: Scene, bitmap: Bitmap): Scene {
        val sky = luminance(bitmap, 0.08f, 0.40f)
        val band = luminance(bitmap, scene.mediaTop, scene.mediaBottom)
        return scene.copy(
            ink = if (sky > 0.62f) DarkInk else Color.White,
            mediaInk = if (band > 0.62f) DarkInk else Color.White,
            lightSky = sky > 0.62f,
        )
    }

    fun delete(context: Context, scene: Scene) {
        val file = scene.file ?: return
        memory.remove(file)
        File(dir(context), file).delete()
    }

    /** Average brightness (0–1) of a horizontal strip of [bitmap], from [top] to [bottom] (fractions of its height). */
    private fun luminance(bitmap: Bitmap, top: Float, bottom: Float): Float {
        val y0 = (top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val y1 = (bottom * bitmap.height).toInt().coerceIn(y0 + 1, bitmap.height)
        val stepY = max(1, (y1 - y0) / 24)
        val stepX = max(1, bitmap.width / 24)
        var sum = 0f
        var count = 0
        for (y in y0 until y1 step stepY) {
            for (x in 0 until bitmap.width step stepX) {
                sum += android.graphics.Color.luminance(bitmap.getPixel(x, y))
                count++
            }
        }
        return if (count == 0) 0f else sum / count
    }
}
