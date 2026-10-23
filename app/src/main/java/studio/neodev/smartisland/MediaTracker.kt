package studio.neodev.smartisland

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock

/** Follows the active media session of whatever app is playing (Spotify, YT Music, etc). */
class MediaTracker(
    ctx: Context,
    private val listener: ComponentName,
    private val onUpdate: () -> Unit
) {
    private val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private var controller: MediaController? = null

    var track: Track? = null
        private set
    var playing: Boolean = false
        private set

    private var baseMs = 0L
    private var baseAt = 0L
    private var speed = 1f

    private val sessionsChanged =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            readMeta(metadata)
            onUpdate()
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            readState(state)
            if (!playing) pick(sessions())
            onUpdate()
        }

        override fun onSessionDestroyed() {
            pick(sessions())
        }
    }

    fun start() {
        try {
            msm.addOnActiveSessionsChangedListener(sessionsChanged, listener)
            pick(msm.getActiveSessions(listener))
        } catch (e: SecurityException) {
            // notification access not granted yet
        }
    }

    fun stop() {
        try { msm.removeOnActiveSessionsChangedListener(sessionsChanged) } catch (e: Exception) {}
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun sessions(): List<MediaController>? =
        try { msm.getActiveSessions(listener) } catch (e: SecurityException) { null }

    private fun pick(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        if (best?.sessionToken == controller?.sessionToken) {
            onUpdate()
            return
        }
        controller?.unregisterCallback(callback)
        controller = best
        best?.registerCallback(callback)
        readMeta(best?.metadata)
        readState(best?.playbackState)
        onUpdate()
    }

    private fun readMeta(m: MediaMetadata?) {
        if (m == null) { track = null; return }
        val title = m.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        val artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: m.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val album = m.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val art = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        track = if (title.isBlank()) null
        else Track(title, artist, album, m.getLong(MediaMetadata.METADATA_KEY_DURATION), art)
    }

    private fun readState(s: PlaybackState?) {
        playing = s?.state == PlaybackState.STATE_PLAYING
        baseMs = (s?.position ?: 0L).coerceAtLeast(0L)
        val stamp = s?.lastPositionUpdateTime ?: 0L
        baseAt = if (stamp > 0) stamp else SystemClock.elapsedRealtime()
        speed = s?.playbackSpeed ?: 1f
    }

    fun positionMs(): Long {
        val p = if (playing) baseMs + ((SystemClock.elapsedRealtime() - baseAt) * speed).toLong() else baseMs
        val d = track?.durationMs ?: 0L
        return if (d > 0) p.coerceIn(0L, d) else p.coerceAtLeast(0L)
    }

    fun toggle() {
        controller?.transportControls?.let { if (playing) it.pause() else it.play() }
    }

    fun next() { controller?.transportControls?.skipToNext() }
    fun prev() { controller?.transportControls?.skipToPrevious() }

    fun seekTo(ms: Long) {
        try { controller?.transportControls?.seekTo(ms) } catch (e: Exception) {}
    }

    fun openApp(ctx: Context): Boolean {
        return try {
            controller?.sessionActivity?.let {
                it.send()
                true
            } ?: run {
                val pkg = controller?.packageName ?: return false
                val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(intent)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
