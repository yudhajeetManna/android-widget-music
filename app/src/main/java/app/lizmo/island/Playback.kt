package app.lizmo.island

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.provider.MediaStore
import android.view.KeyEvent

/** Controls whatever music app is active (Spotify, YT Music, ...). */
object Playback {
    fun controller(ctx: Context): MediaController? = try {
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val l = msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        l.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: l.firstOrNull()
    } catch (e: Exception) {
        null
    }

    /** Media-key fallback works even when no session is exposed. */
    private fun key(ctx: Context, code: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun pause(ctx: Context) {
        val c = controller(ctx)
        if (c != null) c.transportControls.pause() else key(ctx, KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    fun resume(ctx: Context) {
        val c = controller(ctx)
        if (c != null) c.transportControls.play() else key(ctx, KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    fun next(ctx: Context) {
        val c = controller(ctx)
        if (c != null) c.transportControls.skipToNext() else key(ctx, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun prev(ctx: Context) {
        val c = controller(ctx)
        if (c != null) c.transportControls.skipToPrevious() else key(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    /** Find-and-play: ask the running music app first (no UI), else the standard play-from-search intent. */
    fun play(ctx: Context, s: Song): Boolean {
        val q = (s.title + " " + s.artist).trim()
        return viaSession(ctx, q, s) || viaIntent(ctx, q, s)
    }

    private fun viaSession(ctx: Context, q: String, s: Song): Boolean {
        return try {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val list = msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
            val c = list.firstOrNull {
                ((it.playbackState?.actions ?: 0L) and PlaybackState.ACTION_PLAY_FROM_SEARCH) != 0L
            } ?: return false
            val extras = Bundle().apply {
                putString(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio")
                putString(MediaStore.EXTRA_MEDIA_TITLE, s.title)
                putString(MediaStore.EXTRA_MEDIA_ARTIST, s.artist)
            }
            c.transportControls.playFromSearch(q, extras)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun viaIntent(ctx: Context, q: String, s: Song): Boolean {
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(SearchManager.QUERY, q)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio")
            putExtra(MediaStore.EXTRA_MEDIA_TITLE, s.title)
            putExtra(MediaStore.EXTRA_MEDIA_ARTIST, s.artist)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }
}
