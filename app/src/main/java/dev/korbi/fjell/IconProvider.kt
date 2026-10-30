package dev.korbi.fjell

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import java.io.File
import java.security.MessageDigest

/** How an icon is drawn. [id] is what gets stored in prefs. */
sealed interface IconStyle {
    val id: String

    /** The app's own icon, framed in a bubble. */
    data object Original : IconStyle { override val id = "original" }

    /** Like [Original], but desaturated. */
    data object Grayscale : IconStyle { override val id = "grayscale" }

    /** The app's own simple (monochrome) icon. */
    data object Mono : IconStyle { override val id = "mono" }

    /** A picture the user uploaded (per app only). */
    data object Custom : IconStyle { override val id = "custom" }

    /** Whatever the pack maps to the app. */
    data class Pack(val pkg: String) : IconStyle { override val id = "pack:$pkg" }

    /** One specific icon from a pack, picked by hand (per app only). */
    data class PackIcon(val pkg: String, val name: String) : IconStyle { override val id = "packicon:$pkg/$name" }

    companion object {
        fun parse(id: String?): IconStyle? = when {
            id == null -> null
            id == Original.id -> Original
            id == Grayscale.id -> Grayscale
            id == Mono.id -> Mono
            id == Custom.id -> Custom
            id.startsWith("pack:") -> Pack(id.removePrefix("pack:"))
            id.startsWith("packicon:") -> id.removePrefix("packicon:").split('/', limit = 2)
                .takeIf { it.size == 2 }?.let { PackIcon(it[0], it[1]) }
            else -> Pack(id) // early builds stored the bare pack name
        }
    }
}

data class IconSource(val pkg: String, val label: String)

/** Renders each app's icon in its chosen [IconStyle]: its own override, else the global default. */
class IconProvider(private val context: Context, private val prefs: Prefs) {
    private val packs = HashMap<String, IconPack?>()
    private val dir = File(context.filesDir, "icons")

    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    val globalStyle: IconStyle get() = IconStyle.parse(prefs.iconStyle) ?: IconStyle.Pack(IconPack.NOTHING)

    fun overrideFor(app: AppEntry): IconStyle? = IconStyle.parse(prefs.iconOverride(app.key))

    /** Drops loaded packs and rendered icons. Re-reads the default pack; call off the main thread. */
    fun reload() {
        synchronized(packs) { packs.clear() }
        cache.evictAll()
        (globalStyle as? IconStyle.Pack)?.let { pack(it.pkg) }
    }

    fun invalidate() = cache.evictAll()

    fun installedPacks(): List<IconSource> = IconPack.installedPacks(context).mapNotNull { pkg ->
        runCatching {
            val info = context.packageManager.getApplicationInfo(pkg, 0)
            IconSource(pkg, context.packageManager.getApplicationLabel(info).toString())
        }.getOrNull()
    }

    fun icon(app: AppEntry, original: Drawable?, size: Int): Bitmap =
        render(app, original, size, overrideFor(app) ?: globalStyle)

    fun render(app: AppEntry, original: Drawable?, size: Int, style: IconStyle): Bitmap {
        val cacheKey = "${app.key}@$size#${style.id}"
        cache.get(cacheKey)?.let { return it }
        val bubble = bubble()
        val bitmap = when (style) {
            IconStyle.Original -> null
            IconStyle.Grayscale -> IconRender.framed(original, size, bubble, grayscale = true)
            IconStyle.Mono -> IconRender.monochrome(original, size, bubble)
            IconStyle.Custom -> customPicture(app)?.let { IconRender.custom(it, size) }
            is IconStyle.Pack -> pack(style.pkg)?.let { pack ->
                // Apps the pack doesn't cover get their simple icon in the pack's colors.
                pack.matched(app)?.let { pack.icon(it, size) } ?: IconRender.monochrome(original, size, pack.backColor)
            }
            is IconStyle.PackIcon -> pack(style.pkg)?.icon(style.name, size)
        } ?: IconRender.framed(original, size, bubble)
        return bitmap.also { cache.put(cacheKey, it) }
    }

    fun packIconNames(pkg: String): List<String> = pack(pkg)?.iconNames ?: emptyList()

    fun packIcon(pkg: String, name: String, size: Int): Bitmap? {
        val cacheKey = "pack:$pkg/$name@$size"
        cache.get(cacheKey)?.let { return it }
        return pack(pkg)?.icon(name, size)?.also { cache.put(cacheKey, it) }
    }

    /** Copies the picked image into app storage, downscaled. Blocking; call off the main thread. */
    fun saveCustom(app: AppEntry, uri: Uri): Boolean = runCatching {
        val picture = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > CUSTOM_MAX_PX) {
                val scale = CUSTOM_MAX_PX.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        dir.mkdirs()
        customFile(app).outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }.isSuccess

    fun deleteCustom(app: AppEntry) {
        customFile(app).delete()
    }

    private fun pack(pkg: String): IconPack? = synchronized(packs) {
        packs.getOrPut(pkg) { IconPack.loadOrNull(context, pkg) }
    }

    private fun customPicture(app: AppEntry): Bitmap? = BitmapFactory.decodeFile(customFile(app).path)

    private fun customFile(app: AppEntry): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(app.key.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) } + ".png")
    }

    /** Same light/dark bubble the Nothing pack uses, so styles mix well. */
    private fun bubble(): Int {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return if (night) 0xFF1B1B1F.toInt() else 0xFFF3EFF4.toInt()
    }

    companion object {
        private const val CUSTOM_MAX_PX = 512
    }
}
