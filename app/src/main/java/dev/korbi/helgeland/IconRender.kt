package dev.korbi.helgeland

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.abs

/** Draws icons in the launcher's bubble shape, in each of the available styles. */
object IconRender {
    /** Corner radius as a fraction of the icon size: just a touch squarer than a circle. */
    private const val CORNER = 0.36f

    /** How much of the bubble an original icon covers in framed mode. */
    private const val FRAMED_SCALE = 0.7f

    /** Above this share of glyph pixels a silhouette is just a blob. */
    private const val MAX_GLYPH_COVERAGE = 0.4f

    private fun squircle(size: Int) = Path().apply {
        val radius = size * CORNER
        addRoundRect(0f, 0f, size.toFloat(), size.toFloat(), radius, radius, Path.Direction.CW)
    }

    /** Glyph color that reads well on [bubble]. */
    fun glyphFor(bubble: Int): Int = if (Color.luminance(bubble) > 0.5f) 0xFF1B1B1F.toInt() else 0xFFF3EFF4.toInt()

    /** Plain drawable to bitmap; adaptive icons are cut to the squircle. */
    fun render(drawable: Drawable?, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable ?: return bitmap
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            canvas.clipPath(squircle(size))
            // Adaptive layers are 108dp with the visible 72dp in the middle, i.e. 1.5x the icon.
            val inset = size / 4
            for (layer in listOfNotNull(drawable.background, drawable.foreground)) {
                layer.setBounds(-inset, -inset, size + inset, size + inset)
                layer.draw(canvas)
            }
        } else {
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
        }
        return bitmap
    }

    /** The app's own icon, a bit smaller, on a plain bubble so a rim shows around it. */
    fun framed(original: Drawable?, size: Int, bubble: Int, grayscale: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.clipPath(squircle(size))
        canvas.drawColor(bubble)
        val inner = (size * FRAMED_SCALE).toInt()
        val offset = (size - inner) / 2f
        val paint = if (grayscale) Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) } else null
        canvas.drawBitmap(render(original, inner), offset, offset, paint)
        return bitmap
    }

    /**
     * The app's own simple icon: its Android 13 monochrome layer on the bubble. Apps
     * without one get a silhouette made from whatever stands out from their background.
     */
    fun monochrome(original: Drawable?, size: Int, bubble: Int): Bitmap {
        val glyph = glyphFor(bubble)
        // Monochrome layers arrived with Android 13; older phones always get the silhouette.
        val mono = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) (original as? AdaptiveIconDrawable)?.monochrome else null
        if (mono != null) {
            mono.mutate().setTint(glyph)
            return render(AdaptiveIconDrawable(ColorDrawable(bubble), mono), size)
        }
        return silhouette(original, size, bubble, glyph)
    }

    private fun silhouette(original: Drawable?, size: Int, bubble: Int, glyph: Int): Bitmap {
        val bitmap = render(original, size)
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        val bg = pixels[(size * 0.06f).toInt() * size + size / 2]
        if (Color.alpha(bg) < 255) return framed(original, size, bubble, grayscale = true) // legacy icon with its own shape
        var opaque = 0
        var glyphPixels = 0
        for (i in pixels.indices) {
            val p = pixels[i]
            val alpha = Color.alpha(p)
            if (alpha == 0) continue
            val diff = (abs(Color.red(p) - Color.red(bg)) + abs(Color.green(p) - Color.green(bg)) + abs(Color.blue(p) - Color.blue(bg))) / 765f
            val t = ((diff - 0.1f) * 5f).coerceIn(0f, 1f)
            opaque++
            if (t > 0.5f) glyphPixels++
            pixels[i] = Color.argb(
                alpha,
                lerp(Color.red(bubble), Color.red(glyph), t),
                lerp(Color.green(bubble), Color.green(glyph), t),
                lerp(Color.blue(bubble), Color.blue(glyph), t),
            )
        }
        // Picture-like icons (games, photos) have no clear foreground and turn into a blob;
        // a desaturated original reads far better there.
        if (glyphPixels > opaque * MAX_GLYPH_COVERAGE) return framed(original, size, bubble, grayscale = true)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /** A user-picked picture, center-cropped into the bubble shape. */
    fun custom(picture: Bitmap, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.clipPath(squircle(size))
        val side = minOf(picture.width, picture.height)
        val src = Rect((picture.width - side) / 2, (picture.height - side) / 2, (picture.width + side) / 2, (picture.height + side) / 2)
        canvas.drawBitmap(picture, src, Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
        return bitmap
    }

    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()
}
