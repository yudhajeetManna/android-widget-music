package app.lizmo.island

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// VoiceBus — the bridge between VoiceService and every UI element
// ─────────────────────────────────────────────────────────────────────────────

/** Where voice feedback goes: flashes on the island when it's showing, otherwise a toast. */
object VoiceBus {
    @Volatile var island: ((String) -> Boolean)? = null

    /** Live mic/debug status shown on the Settings screen. */
    @Volatile var lastLog: String = "Voice control is off"
    @Volatile var logListener: ((String) -> Unit)? = null

    /** Live RMS dB stream from the microphone (for VU meter display). */
    @Volatile var rmsListener: ((Float) -> Unit)? = null

    /**
     * Command HUD: called with the action emoji + label whenever a voice command executes.
     * e.g. "⏭ Next", "🔀 Shuffle on", "🔉 Volume 60%"
     * The island overlay registers here to show an animated banner; falls back to a toast.
     */
    @Volatile var commandHud: ((String) -> Unit)? = null

    fun log(msg: String) {
        lastLog = msg
        logListener?.invoke(msg)
    }

    fun say(ctx: Context, msg: String) {
        val shown = island?.invoke(msg) ?: false
        if (!shown) Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    /** Show the command HUD and, if the island is not visible, also a toast. */
    fun showCommand(ctx: Context, label: String) {
        commandHud?.invoke(label)
        if (commandHud == null) {
            Toast.makeText(ctx.applicationContext, label, Toast.LENGTH_SHORT).show()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VoiceCommands — command parser and executor
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Full voice media-control command set.
 *
 * Wake word: "lizmo <command>"
 *
 * ── Playback ──
 *  "pause" / "stop" / "hold on"
 *  "play" / "resume" / "continue"
 *  "next" / "skip"
 *  "previous" / "back" / "go back"
 *  "restart" / "play from the beginning" / "replay"
 *  "stop music" / "stop playing"
 *
 * ── Seeking ──
 *  "skip forward 30 seconds"
 *  "skip forward 2 minutes"
 *  "rewind 10 seconds"
 *  "go back 30 seconds"
 *  "fast forward 1 minute"
 *  "jump to 2 minutes 30 seconds"
 *
 * ── Volume ──
 *  "volume up" / "louder" / "turn it up"
 *  "volume down" / "quieter" / "softer"
 *  "max volume"
 *  "set volume to 60"  /  "volume 70 percent"
 *  "mute" / "unmute"
 *  "what's the volume" / "volume level"
 *
 * ── Shuffle & Repeat ──
 *  "shuffle" / "shuffle on" / "turn on shuffle"
 *  "shuffle off" / "turn off shuffle"
 *  "repeat" / "repeat this song" / "repeat one" / "loop this"
 *  "repeat all" / "loop all"
 *  "repeat off" / "no repeat"
 *
 * ── Like / Dislike ──
 *  "like this" / "like this song" / "thumbs up"
 *  "dislike" / "thumbs down"
 *
 * ── Info ──
 *  "what's playing" / "what song is this" / "what is playing"
 *
 * ── Anything else → music search & play ──
 *  "play Kesariya"  /  "play Blinding Lights by The Weeknd"
 */
object VoiceCommands {

    // ── Playback ──────────────────────────────────────────────────────────────
    private val pauseRe = Regex(
        """(pause|pause (the )?(music|song|track|playback)|stop (the )?(music|song|track|playback)|hold on)""")
    private val playRe = Regex(
        """(play|resume|continue|(play|resume) (the )?(music|song|track))""")
    private val nextRe = Regex(
        """(next|skip|(next|skip) (this )?(song|track)|(play )?next (song|track))""")
    private val prevRe = Regex(
        """(previous|back|go back|(previous|last) (song|track)|(play )?previous (song|track))""")
    private val restartRe = Regex(
        """(restart( (the )?(song|track))?|play from (the )?(beginning|start)|replay( (this )?(song|track))?)""")
    private val stopRe = Regex(
        """(stop (music|playing|playback)|stop it|quit playing)""")

    // ── Seeking ───────────────────────────────────────────────────────────────
    // "skip forward 30 seconds", "fast forward 2 minutes", "rewind 10 seconds",
    // "go back 1 minute 30 seconds", "jump to 2 minutes"
    private val fwdRe = Regex(
        """(skip|fast)? ?forward(?: by)?(?: (\d+) minutes?)?(?: and)?(?: (\d+) seconds?)?""")
    private val rwdRe = Regex(
        """(rewind|go back|skip back)(?: by)?(?: (\d+) minutes?)?(?: and)?(?: (\d+) seconds?)?""")
    private val jumpRe = Regex(
        """jump to(?: (\d+) minutes?)?(?: and)?(?: (\d+) seconds?)?""")

    // ── Volume ────────────────────────────────────────────────────────────────
    private val upRe = Regex(
        """(volume up|louder|turn (it )?up|(increase|raise|turn up) (the )?volume|more volume)""")
    private val downRe = Regex(
        """(volume down|quieter|softer|lower|turn (it )?down|(decrease|lower|turn down|reduce) (the )?volume|less volume)""")
    private val maxRe = Regex(
        """((max|full|maximum) volume|volume (max|full|maximum)|turn (it )?all the way up)""")
    private val muteRe = Regex(
        """(mute|mute (the )?(music|sound|volume)|silence)""")
    private val unmuteRe = Regex(
        """(unmute|unmute (the )?(music|sound|volume)|un mute)""")
    private val setRe = Regex(
        """(set )?(the )?volume( to| at)? (\d{1,3})( ?%| percent)?""")
    private val volQueryRe = Regex(
        """(what('?s| is) the volume|volume level|how loud(( is)? (it|the music))?)""")

    // ── Shuffle ───────────────────────────────────────────────────────────────
    private val shuffleOnRe = Regex(
        """(shuffle( on)?|turn on shuffle|(enable|start) shuffle|play (on )?shuffle|mix it up)""")
    private val shuffleOffRe = Regex(
        """(shuffle off|turn off shuffle|(disable|stop) shuffle|no shuffle)""")

    // ── Repeat ────────────────────────────────────────────────────────────────
    private val repeatOneRe = Regex(
        """(repeat( this)? (song|track|one)?|loop( this)?( song| track)?|repeat one|play (on )?repeat)""")
    private val repeatAllRe = Regex(
        """(repeat all|loop all|repeat playlist|loop playlist)""")
    private val repeatOffRe = Regex(
        """(repeat off|no repeat|(stop|disable) repeat|(stop|disable) loop(ing)?)""")

    // ── Like / Dislike ────────────────────────────────────────────────────────
    private val likeRe = Regex(
        """(like( this( song| track)?)?|thumbs up|(add to )?favourites?|save (this )?song|love (this )?song)""")
    private val dislikeRe = Regex(
        """(dislike( this( song| track)?)?|thumbs down|don'?t like (this )?song)""")

    // ── Info ──────────────────────────────────────────────────────────────────
    private val nowPlayingRe = Regex(
        """(what'?s? (is )?playing|what (song|track) is (this|playing)|what am i listening to|name (this|the) song)""")

    // ─────────────────────────────────────────────────────────────────────────

    private fun norm(s: String): String =
        s.lowercase().replace(Regex("""[^\p{L}\p{N}%'\s]"""), " ").replace(Regex("""\s+"""), " ").trim()

    /** Returns true if [raw] is a known control command (not a search query). */
    fun isControl(raw: String): Boolean {
        val t = norm(raw)
        return listOf(
            pauseRe, playRe, nextRe, prevRe, restartRe, stopRe,
            upRe, downRe, maxRe, muteRe, unmuteRe, setRe, volQueryRe,
            shuffleOnRe, shuffleOffRe,
            repeatOneRe, repeatAllRe, repeatOffRe,
            likeRe, dislikeRe,
            nowPlayingRe
        ).any { it.matches(t) }
            || fwdRe.containsMatchIn(t) || rwdRe.containsMatchIn(t) || jumpRe.containsMatchIn(t)
    }

    /** Runs the command, returns short feedback text for VoiceBus.say(). */
    fun run(ctx: Context, raw: String): String {
        val t = norm(raw)
        return when {
            // ── Playback ──────────────────────────────────────────────────────
            pauseRe.matches(t)   -> { Playback.pause(ctx);  hud(ctx, "⏸ Paused");  "Paused" }
            playRe.matches(t)    -> { Playback.resume(ctx); hud(ctx, "▶ Playing"); "Playing" }
            nextRe.matches(t)    -> { Playback.next(ctx);   hud(ctx, "⏭ Next");   "Next" }
            prevRe.matches(t)    -> { Playback.prev(ctx);   hud(ctx, "⏮ Previous"); "Previous" }
            restartRe.matches(t) -> { Playback.restart(ctx); hud(ctx, "↩ Restart"); "Restarting" }
            stopRe.matches(t)    -> { Playback.stop(ctx);   hud(ctx, "⏹ Stopped"); "Stopped" }

            // ── Seeking ───────────────────────────────────────────────────────
            fwdRe.containsMatchIn(t) -> {
                val ms = parseTimeMs(t, fwdRe) ?: 30_000L
                Playback.seek(ctx, ms)
                val label = "⏩ +${formatTime(ms)}"
                hud(ctx, label); label
            }
            rwdRe.containsMatchIn(t) -> {
                val ms = parseTimeMs(t, rwdRe) ?: 10_000L
                Playback.seek(ctx, -ms)
                val label = "⏪ −${formatTime(ms)}"
                hud(ctx, label); label
            }
            jumpRe.containsMatchIn(t) -> {
                val ms = parseTimeMs(t, jumpRe) ?: 0L
                // Jump to absolute position
                val c = Playback.controller(ctx)
                c?.transportControls?.seekTo(ms)
                val label = "⏩ → ${formatTime(ms)}"
                hud(ctx, label); label
            }

            // ── Volume ────────────────────────────────────────────────────────
            upRe.matches(t)    -> { step(ctx, true);    hud(ctx, "🔊 Louder");   "Volume up" }
            downRe.matches(t)  -> { step(ctx, false);   hud(ctx, "🔉 Quieter"); "Volume down" }
            maxRe.matches(t)   -> { setPct(ctx, 100);   hud(ctx, "🔊 Max volume"); "Volume max" }
            muteRe.matches(t)  -> { mute(ctx, true);    hud(ctx, "🔇 Muted");   "Muted" }
            unmuteRe.matches(t)-> { mute(ctx, false);   hud(ctx, "🔊 Unmuted"); "Unmuted" }
            setRe.matches(t)   -> {
                val n = setRe.matchEntire(t)!!.groupValues[4].toInt().coerceIn(0, 100)
                setPct(ctx, n)
                hud(ctx, "🔊 Volume $n%")
                "Volume $n%"
            }
            volQueryRe.matches(t) -> {
                val pct = Playback.volumePct(ctx)
                val msg = if (pct != null) "Volume is $pct%" else "Can't read volume right now"
                hud(ctx, "🔊 $msg"); msg
            }

            // ── Shuffle ───────────────────────────────────────────────────────
            shuffleOffRe.matches(t) -> {
                // setShuffleMode NONE explicitly
                try {
                    Playback.controller(ctx)?.transportControls?.setShuffleMode(
                        android.media.session.PlaybackState.SHUFFLE_MODE_NONE)
                } catch (e: Exception) {}
                hud(ctx, "🔀 Shuffle off"); "Shuffle off"
            }
            shuffleOnRe.matches(t)  -> {
                Playback.shuffle(ctx)
                hud(ctx, "🔀 Shuffle on"); "Shuffle on"
            }

            // ── Repeat ────────────────────────────────────────────────────────
            repeatAllRe.matches(t) -> {
                try { Playback.controller(ctx)?.transportControls?.setRepeatMode(
                    android.media.session.PlaybackState.REPEAT_MODE_ALL) } catch (e: Exception) {}
                hud(ctx, "🔁 Repeat all"); "Repeat all"
            }
            repeatOffRe.matches(t) -> {
                try { Playback.controller(ctx)?.transportControls?.setRepeatMode(
                    android.media.session.PlaybackState.REPEAT_MODE_NONE) } catch (e: Exception) {}
                hud(ctx, "➡ Repeat off"); "Repeat off"
            }
            repeatOneRe.matches(t) -> {
                try { Playback.controller(ctx)?.transportControls?.setRepeatMode(
                    android.media.session.PlaybackState.REPEAT_MODE_ONE) } catch (e: Exception) {}
                hud(ctx, "🔂 Repeat one"); "Repeat this song"
            }

            // ── Like / Dislike ────────────────────────────────────────────────
            likeRe.matches(t) -> {
                val ok = Playback.like(ctx)
                val msg = if (ok) "❤ Liked!" else "❤ Liked (app may not support ratings)"
                hud(ctx, msg); msg
            }
            dislikeRe.matches(t) -> {
                val ok = Playback.dislike(ctx)
                val msg = if (ok) "👎 Disliked" else "👎 Disliked (app may not support ratings)"
                hud(ctx, msg); msg
            }

            // ── Info ──────────────────────────────────────────────────────────
            nowPlayingRe.matches(t) -> {
                val info = Playback.nowPlaying(ctx) ?: "Nothing playing"
                hud(ctx, "🎵 $info"); info
            }

            // ── Search & play ─────────────────────────────────────────────────
            else -> search(ctx, raw)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Flash the command label on the island HUD. */
    private fun hud(ctx: Context, label: String) = VoiceBus.showCommand(ctx, label)

    private fun am(ctx: Context) = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun step(ctx: Context, up: Boolean) {
        try {
            val d = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            am(ctx).adjustStreamVolume(AudioManager.STREAM_MUSIC, d, 0)
            am(ctx).adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {}
    }

    private fun mute(ctx: Context, on: Boolean) {
        try {
            val d = if (on) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            am(ctx).adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {}
    }

    private fun setPct(ctx: Context, pct: Int) {
        try {
            val a = am(ctx)
            val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            a.setStreamVolume(AudioManager.STREAM_MUSIC, (pct * max / 100.0).roundToInt(), AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {}
    }

    /**
     * Parse time from a regex match on [text].
     * Groups tried: the regex may contain optional minute/second groups by position.
     * Falls back to looking for bare number patterns.
     */
    private fun parseTimeMs(text: String, re: Regex): Long? {
        val m = re.find(text) ?: return null
        // Try to extract minutes and seconds from the remaining text after the keyword
        val numRe = Regex("""(\d+)\s*(minute|min|m\b)|(\d+)\s*(second|sec|s\b)""")
        var minutes = 0L
        var seconds = 0L
        for (n in numRe.findAll(text)) {
            val v = (n.groupValues[1].toLongOrNull() ?: n.groupValues[3].toLongOrNull()) ?: continue
            val unit = n.groupValues[2].ifEmpty { n.groupValues[4] }
            if (unit.startsWith("m")) minutes = v else seconds = v
        }
        if (minutes == 0L && seconds == 0L) {
            // Plain number with no unit = seconds
            val plain = Regex("""(?<!\d)(\d+)(?!\d)""").find(text)?.groupValues?.get(1)?.toLongOrNull()
            if (plain != null) seconds = plain
        }
        val total = minutes * 60_000L + seconds * 1000L
        return if (total > 0) total else null
    }

    /** Format ms → "1m 30s" or "30s" */
    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        return if (m > 0) "${m}m ${s % 60}s" else "${s}s"
    }

    /** Anything else = a song request: find it, play in the active music platform. */
    private fun search(ctx: Context, raw: String): String {
        val q = Catalog.cleanVoice(raw)
        if (q.length < 2) return "Didn't catch that"
        hud(ctx, "🔍 \"$q\"…")
        Catalog.search(q) { res ->
            val s = res?.firstOrNull()
            val msg = when {
                res == null -> "Couldn't search — check internet"
                s == null   -> "No match for \"$q\""
                Playback.play(ctx, s) -> "▶ " + s.title
                else -> "No music app could play " + s.title
            }
            VoiceBus.say(ctx, msg)
            if (s != null && Playback.play(ctx, s)) hud(ctx, "🎵 Playing: ${s.title}")
        }
        return "Searching \"$q\"…"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WakeWord — fuzzy wake-word matcher
// ─────────────────────────────────────────────────────────────────────────────

/** Fuzzy wake-word matching on speech-to-text output (STT often mishears made-up names). */
object WakeWord {
    private fun norm(s: String): String =
        s.lowercase().replace(Regex("""[^\p{L}\p{N}\s]"""), " ").replace(Regex("""\s+"""), " ").trim()

    private fun lev(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    private fun close(a: String, b: String): Boolean = a == b || (b.length >= 5 && lev(a, b) <= 1)

    /** Text after the wake word; "" if only the wake word was heard; null if it wasn't said.
     *  [wakeCsv] can hold several names/spellings separated by commas. */
    fun extract(said: String, wakeCsv: String): String? {
        for (alias in wakeCsv.split(",")) {
            val r = extractOne(said, alias)
            if (r != null) return r
        }
        return null
    }

    private fun extractOne(said: String, wake: String): String? {
        val t = norm(said).split(" ").filter { it.isNotEmpty() }
        val w = norm(wake).split(" ").filter { it.isNotEmpty() }
        if (w.isEmpty() || t.size < w.size) return null
        for (i in 0..minOf(2, t.size - w.size)) {
            var ok = true
            for (k in w.indices) if (!close(t[i + k], w[k])) { ok = false; break }
            if (ok) return t.drop(i + w.size).joinToString(" ")
            // "lis mo" → "lismo"
            if (w.size == 1 && i + 1 < t.size && close(t[i] + t[i + 1], w[0])) {
                return t.drop(i + 2).joinToString(" ")
            }
        }
        return null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Error name helper
// ─────────────────────────────────────────────────────────────────────────────

fun speechErrorName(code: Int): String = when (code) {
    1  -> "network timeout"
    2  -> "no internet (speech needs a connection)"
    3  -> "audio/microphone problem"
    4  -> "speech server error"
    5  -> "recognizer client error"
    6  -> "no speech heard"
    7  -> "didn't understand"
    8  -> "recognizer busy"
    9  -> "microphone permission missing"
    10 -> "too many requests"
    11 -> "speech service disconnected"
    12 -> "language not supported"
    13 -> "language pack unavailable"
    else -> "error $code"
}
