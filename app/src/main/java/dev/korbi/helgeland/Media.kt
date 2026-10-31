package dev.korbi.helgeland

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat

/**
 * Exists only so Android lets us read other apps' media sessions; it never looks
 * at notifications themselves.
 */
class MediaListener : NotificationListenerService()

/** What's playing right now, as far as the home screen cares. */
data class NowPlaying(
    val title: String,
    val artist: String?,
    val playing: Boolean,
    val packageName: String,
)

/**
 * Follows the most relevant media session (one that's playing, else the newest)
 * and reports changes through [onChange]. Needs notification access.
 */
class MediaWatcher(private val context: Context, private val onChange: (NowPlaying?) -> Unit) {
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListener::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = pick()
        override fun onMetadataChanged(metadata: MediaMetadata?) = report()
        override fun onSessionDestroyed() = pick()
    }

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { pick() }

    fun start() {
        if (!hasAccess(context)) return onChange(null)
        runCatching { sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, main) }
        pick()
    }

    fun stop() {
        runCatching { sessions.removeOnActiveSessionsChangedListener(sessionsChanged) }
        controller?.unregisterCallback(callback)
        controller = null
        onChange(null)
    }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() {
        controller?.transportControls?.skipToNext()
    }

    fun previous() {
        controller?.transportControls?.skipToPrevious()
    }

    /** Opens the player: its own "now playing" screen if it offers one, else the app. */
    fun open() {
        val c = controller ?: return
        val shown = runCatching { c.sessionActivity?.send() }.isSuccess && c.sessionActivity != null
        if (!shown) {
            context.packageManager.getLaunchIntentForPackage(c.packageName)?.let {
                context.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    private fun pick() {
        val active = runCatching { sessions.getActiveSessions(listener) }.getOrDefault(emptyList())
        val best = active.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: active.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, main)
        }
        report()
    }

    private fun report() {
        val c = controller
        val title = c?.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (c == null || title.isNullOrBlank()) return onChange(null)
        onChange(
            NowPlaying(
                title = title,
                artist = c.metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() },
                playing = c.playbackState?.state == PlaybackState.STATE_PLAYING,
                packageName = c.packageName,
            ),
        )
    }

    companion object {
        fun hasAccess(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }
}
