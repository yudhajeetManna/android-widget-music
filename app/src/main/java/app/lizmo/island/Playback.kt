package app.lizmo.island

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.KeyEvent

/**
 * Controls whatever music app is active (Spotify, YT Music, JioSaavn, etc.).
 *
 * Song search flow (most-specific → most-generic):
 *  1. playFromSearch() on the active MediaSession (Spotify, YT Music support this).
 *  2. Deep-link intent into the detected active music platform (no fallback dialog).
 *  3. Standard MEDIA_PLAY_FROM_SEARCH broadcast (any installed music app picks it up).
 */
object Playback {

    /** Known music platform package names and their search intent schemes. */
    private val PLATFORM_SEARCH = mapOf(
        "com.spotify.music"            to { q: String -> platformIntent("spotify:search:$q") },
        "com.google.android.apps.youtube.music" to { q: String -> ytMusicSearch(q) },
        "com.google.android.youtube"   to { q: String -> ytSearch(q) },
        "com.jiosaavn.jio"            to { q: String -> platformIntent("jiosaavn://songs?query=$q") },
        "com.gaana"                    to { q: String -> webSearch("https://gaana.com/search/$q") },
        "com.wynk.music"              to { q: String -> webSearch("https://wynk.in/music/search?q=$q") },
        "com.apple.android.music"      to { q: String -> platformIntent("music://search?q=$q") },
        "soundcloud.android.playback" to { q: String -> webSearch("https://soundcloud.com/search?q=$q") },
        "com.maxmpz.audioplayer"      to null,   // Poweramp — no deep-link, use media session
        "com.sonyericsson.music"      to null,
        "code.name.monkey.retromusic" to null,
        "com.martijncouprie.musicolet" to null,
    )

    // ---------- internal helpers ----------

    private fun platformIntent(uri: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun ytMusicSearch(q: String): Intent =
        Intent(Intent.ACTION_SEARCH).apply {
            setPackage("com.google.android.apps.youtube.music")
            putExtra(SearchManager.QUERY, q)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun ytSearch(q: String): Intent =
        Intent(Intent.ACTION_SEARCH).apply {
            setPackage("com.google.android.youtube")
            putExtra(SearchManager.QUERY, q)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun webSearch(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    // ---------- active session helpers ----------

    fun controller(ctx: Context): MediaController? = try {
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val l = msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        l.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: l.firstOrNull()
    } catch (e: Exception) {
        null
    }

    /** Returns the package name of the currently active music player, or null. */
    fun activeMusicPackage(ctx: Context): String? = try {
        val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val l = msm.getActiveSessions(ComponentName(ctx, MediaListener::class.java))
        (l.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: l.firstOrNull())
            ?.packageName
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

    /** Seek to start (position 0 ms). */
    fun restart(ctx: Context) {
        controller(ctx)?.transportControls?.seekTo(0L)
    }

    /** Seek forward/backward by [deltaMs] milliseconds. Positive = forward, negative = rewind. */
    fun seek(ctx: Context, deltaMs: Long) {
        val c = controller(ctx) ?: return
        val current = c.playbackState?.position ?: 0L
        c.transportControls.seekTo((current + deltaMs).coerceAtLeast(0L))
    }

    const val SHUFFLE_MODE_NONE = 0
    const val SHUFFLE_MODE_ALL = 1

    const val REPEAT_MODE_NONE = 0
    const val REPEAT_MODE_ONE = 1
    const val REPEAT_MODE_ALL = 2

    private const val ACTION_SET_SHUFFLE_MODE = "android.support.v4.media.session.action.SET_SHUFFLE_MODE"
    private const val ARG_SHUFFLE_MODE = "android.support.v4.media.session.ARGUMENT_SHUFFLE_MODE"
    private const val ACTION_SET_REPEAT_MODE = "android.support.v4.media.session.action.SET_REPEAT_MODE"
    private const val ARG_REPEAT_MODE = "android.support.v4.media.session.ARGUMENT_REPEAT_MODE"

    @Volatile private var isShuffled = false
    @Volatile private var currentRepeatMode = REPEAT_MODE_NONE

    fun setShuffle(ctx: Context, enable: Boolean) {
        val c = controller(ctx) ?: return
        isShuffled = enable
        val mode = if (enable) SHUFFLE_MODE_ALL else SHUFFLE_MODE_NONE
        try {
            val bundle = Bundle().apply { putInt(ARG_SHUFFLE_MODE, mode) }
            c.transportControls.sendCustomAction(ACTION_SET_SHUFFLE_MODE, bundle)
        } catch (e: Exception) {}

        try {
            c.playbackState?.customActions?.find {
                it.action.contains("shuffle", ignoreCase = true)
            }?.let { act ->
                c.transportControls.sendCustomAction(act.action, act.extras ?: Bundle())
            }
        } catch (e: Exception) {}
    }

    /** Toggle shuffle. */
    fun shuffle(ctx: Context) {
        setShuffle(ctx, !isShuffled)
    }

    fun setRepeat(ctx: Context, mode: Int) {
        val c = controller(ctx) ?: return
        currentRepeatMode = mode
        try {
            val bundle = Bundle().apply { putInt(ARG_REPEAT_MODE, mode) }
            c.transportControls.sendCustomAction(ACTION_SET_REPEAT_MODE, bundle)
        } catch (e: Exception) {}

        try {
            c.playbackState?.customActions?.find {
                it.action.contains("repeat", ignoreCase = true)
            }?.let { act ->
                c.transportControls.sendCustomAction(act.action, act.extras ?: Bundle())
            }
        } catch (e: Exception) {}
    }

    /** Cycle repeat mode: NONE → ONE → ALL → NONE. */
    fun repeat(ctx: Context) {
        val next = when (currentRepeatMode) {
            REPEAT_MODE_NONE -> REPEAT_MODE_ONE
            REPEAT_MODE_ONE  -> REPEAT_MODE_ALL
            else             -> REPEAT_MODE_NONE
        }
        setRepeat(ctx, next)
    }

    /** Send a "like" (thumbs up) rating to the active session. Returns true if supported. */
    fun like(ctx: Context): Boolean {
        return try {
            val c = controller(ctx) ?: return false
            c.transportControls.setRating(android.media.Rating.newThumbRating(true))
            true
        } catch (e: Exception) { false }
    }

    /** Send a "dislike" (thumbs down) rating to the active session. Returns true if supported. */
    fun dislike(ctx: Context): Boolean {
        return try {
            val c = controller(ctx) ?: return false
            c.transportControls.setRating(android.media.Rating.newThumbRating(false))
            true
        } catch (e: Exception) { false }
    }

    /** Stop playback completely (not just pause). */
    fun stop(ctx: Context) {
        val c = controller(ctx)
        if (c != null) c.transportControls.stop() else key(ctx, KeyEvent.KEYCODE_MEDIA_STOP)
    }

    /** Returns "Title – Artist" of the currently playing track, or null. */
    fun nowPlaying(ctx: Context): String? {
        val c = controller(ctx) ?: return null
        val m = c.metadata ?: return null
        val title = m.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: return null
        val artist = m.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
        return if (artist.isNullOrBlank()) title else "$title – $artist"
    }

    /** Current volume as 0–100%, or null if unavailable. */
    fun volumePct(ctx: Context): Int? {
        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max == 0) null else (cur * 100 / max)
        } catch (e: Exception) { null }
    }

    // ---------- song search & play ----------

    /**
     * Find and play [s] in the currently active music platform.
     * Priority:
     *  1. MediaSession playFromSearch (works silently in-app — Spotify, YT Music, etc.)
     *  2. Platform deep-link intent for the detected active app
     *  3. Generic MEDIA_PLAY_FROM_SEARCH broadcast (all music apps)
     */
    fun play(ctx: Context, s: Song): Boolean {
        val q = (s.title + " " + s.artist).trim()
        return viaSession(ctx, q, s) || viaPlatformDeepLink(ctx, q) || viaIntent(ctx, q, s)
    }

    /** 1. Ask the running music app's session to play from search (seamless, no UI). */
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

    /**
     * 2. Detect which music platform is active and launch a platform-specific deep-link
     *    or search intent directly — no system picker dialog.
     */
    private fun viaPlatformDeepLink(ctx: Context, q: String): Boolean {
        val pkg = activeMusicPackage(ctx) ?: return false
        // Check if platform is installed
        val pm = ctx.packageManager
        val isInstalled = try { pm.getPackageInfo(pkg, 0); true } catch (e: PackageManager.NameNotFoundException) { false }
        if (!isInstalled) return false

        val intentFactory = PLATFORM_SEARCH[pkg] ?: return false   // null = media session only
        return try {
            val intent = intentFactory(q)
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 3. Generic broadcast: any installed music app that supports MEDIA_PLAY_FROM_SEARCH will handle it.
     *    If more than one app is installed Android shows a picker dialog.
     */
    private fun viaIntent(ctx: Context, q: String, s: Song): Boolean {
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).apply {
            putExtra(SearchManager.QUERY, q)
            putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio")
            putExtra(MediaStore.EXTRA_MEDIA_TITLE, s.title)
            putExtra(MediaStore.EXTRA_MEDIA_ARTIST, s.artist)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // If we know the active platform, constrain to it to avoid a picker
        val pkg = activeMusicPackage(ctx)
        if (pkg != null) i.setPackage(pkg)
        return try {
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            // Package constrained intent failed, try without package constraint
            try {
                i.setPackage(null)
                ctx.startActivity(i)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }
}
