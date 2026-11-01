package dev.korbi.fjell

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color

/**
 * One-off move of the three pictures this app used to ship with into the user's own
 * wallpapers, with their hand-tuned positions, so they survive once they leave the app.
 * Delete this file (and the drawables) in the release after.
 */
object LegacyScenes {
    private class Legacy(val name: String, val res: Int, val gridTop: Float, val mediaTop: Float, val ink: Color, val mediaInk: Color, val lightSky: Boolean)

    private val DarkInk = Color(0xFF2E3550)
    private val legacy = listOf(
        Legacy("Mountain", R.drawable.wp_mountain, 0.6998f, 0.6567f, Color.White, Color.White, lightSky = false),
        Legacy("Pole", R.drawable.wp_pole, 0.648f, 0.6049f, DarkInk, Color.White, lightSky = true),
        Legacy("Snow", R.drawable.wp_snow, 0.69f, 0.6439f, DarkInk, DarkInk, lightSky = true),
    )

    /** Saves the pictures as custom scenes; returns old name → new scene id. Blocking. */
    fun migrate(context: Context): Map<String, Scene> = legacy.associate { old ->
        val bitmap = BitmapFactory.decodeResource(context.resources, old.res, BitmapFactory.Options().apply { inScaled = false })
        val saved = ScenePictures.save(context, bitmap, old.name, old.gridTop)
        old.name to saved.copy(mediaTop = old.mediaTop, mediaBottom = old.gridTop, ink = old.ink, mediaInk = old.mediaInk, lightSky = old.lightSky)
    }
}
