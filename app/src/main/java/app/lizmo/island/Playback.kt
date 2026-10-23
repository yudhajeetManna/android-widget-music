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
        "com.spotify.music"            to { q: String -> platformIntent("spotify", "spotify:search:$q") },
        "com.google.android.apps.youtube.music" to { q: String -> ytMusicSearch(q) },
        "com.google.android.youtube"   to { q: String -> ytSearch(q) },
        "com.jiosaavn.jio"            to { q: String -> platformIntent("jiosaavn", "jiosaavn://songs?query=$q") },
        "com.gaana"                    to { q: String -> webSearch("https://gaana.com/search/$q") },
        "com.wynk.music"              to { q: String -> webSearch("https://wynk.in/music/search?q=$q") },
        "com.apple.android.music"      to { q: String -> platformIntent("music", "music://search?q=$q") },
        "soundcloud.android.playback" to { q: String -> webSearch("https://soundcloud.com/search?q=$q") },
        "com.maxmpz.audioplayer"      to null,   // Poweramp — no deep-link, use media session
        "com.sonyericsson.music"      to null,
        "code.name.monkey.retromusic" to null,
        "com.martijncouprie.musicolet" to null,
    )

    // ---------- internal helpers ----------

    private fun platformIntent(scheme: String, uri: String): Intent =
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
