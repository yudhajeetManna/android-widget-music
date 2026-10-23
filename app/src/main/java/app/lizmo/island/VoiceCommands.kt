package app.lizmo.island

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import kotlin.math.roundToInt

/** Where voice feedback goes: flashes on the island when it's showing, otherwise a toast. */
object VoiceBus {
    @Volatile
    var island: ((String) -> Boolean)? = null

    /** Live mic/debug status shown on the Lizmo screen. */
    @Volatile
    var lastLog: String = "Voice control is off"

    @Volatile
    var logListener: ((String) -> Unit)? = null

    fun log(msg: String) {
        lastLog = msg
        logListener?.invoke(msg)
    }

    fun say(ctx: Context, msg: String) {
        val shown = island?.invoke(msg) ?: false
        if (!shown) Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show()
    }
}

/** pause / play / next / previous / volume / mute / "play <song>" */
object VoiceCommands {
    private val pauseRe = Regex("""(pause|stop|hold on|(pause|stop) (the )?(music|song|track|playback))""")
    private val playRe = Regex("""(play|resume|continue|(play|resume) (the )?(music|song|track))""")
    private val nextRe = Regex("""(next|skip|(next|skip) (this )?(song|track))""")
    private val prevRe = Regex("""(previous|back|go back|(previous|last) (song|track))""")
    private val upRe = Regex("""(volume up|louder|turn (it )?up|(increase|raise|turn up) (the )?volume|more volume)""")
    private val downRe = Regex("""(volume down|quieter|softer|lower|turn (it )?down|(decrease|lower|turn down|reduce) (the )?volume|less volume)""")
    private val maxRe = Regex("""((max|full|maximum) volume|volume (max|full|maximum))""")
    private val muteRe = Regex("""(mute|mute (the )?(music|sound|volume))""")
    private val unmuteRe = Regex("""(unmute|unmute (the )?(music|sound|volume))""")
    private val setRe = Regex("""(set )?(the )?volume( to| at)? (\d{1,3})( ?%| percent)?""")

    private fun norm(s: String): String =
        s.lowercase().replace(Regex("""[^\p{L}\p{N}%\s]"""), " ").replace(Regex("""\s+"""), " ").trim()

    fun isControl(raw: String): Boolean {
        val t = norm(raw)
        return listOf(pauseRe, playRe, nextRe, prevRe, upRe, downRe, maxRe, muteRe, unmuteRe, setRe).any { it.matches(t) }
    }

    /** Runs the command, returns short feedback text. */
    fun run(ctx: Context, raw: String): String {
        val t = norm(raw)
        return when {
            pauseRe.matches(t) -> { Playback.pause(ctx); "Paused" }
            playRe.matches(t) -> { Playback.resume(ctx); "Playing" }
            nextRe.matches(t) -> { Playback.next(ctx); "Next" }
            prevRe.matches(t) -> { Playback.prev(ctx); "Previous" }
            upRe.matches(t) -> { step(ctx, true); "Volume up" }
            downRe.matches(t) -> { step(ctx, false); "Volume down" }
            maxRe.matches(t) -> { setPct(ctx, 100); "Volume max" }
            muteRe.matches(t) -> { mute(ctx, true); "Muted" }
            unmuteRe.matches(t) -> { mute(ctx, false); "Unmuted" }
            setRe.matches(t) -> {
                val n = setRe.matchEntire(t)!!.groupValues[4].toInt().coerceIn(0, 100)
                setPct(ctx, n)
                "Volume $n%"
            }
            else -> search(ctx, raw)
        }
    }

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

    /** Anything else = a song request: find it, play the best match. */
    private fun search(ctx: Context, raw: String): String {
        val q = Catalog.cleanVoice(raw)
        if (q.length < 2) return "Didn't catch that"
        Catalog.search(q) { res ->
            val s = res?.firstOrNull()
            val msg = when {
                res == null -> "Couldn't search \u2014 check internet"
                s == null -> "No match for \"$q\""
                Playback.play(ctx, s) -> "Playing " + s.title
                else -> "No music app could play " + s.title
            }
            VoiceBus.say(ctx, msg)
        }
        return "Searching \"$q\"\u2026"
    }
}

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
            // "lis mo" -> "lismo"
            if (w.size == 1 && i + 1 < t.size && close(t[i] + t[i + 1], w[0])) {
                return t.drop(i + 2).joinToString(" ")
            }
        }
        return null
    }
}

fun speechErrorName(code: Int): String = when (code) {
    1 -> "network timeout"
    2 -> "no internet (speech needs a connection)"
    3 -> "audio/microphone problem"
    4 -> "speech server error"
    5 -> "recognizer client error"
    6 -> "no speech heard"
    7 -> "didn't understand"
    8 -> "recognizer busy"
    9 -> "microphone permission missing"
    10 -> "too many requests"
    11 -> "speech service disconnected"
    12 -> "language not supported"
    13 -> "language pack unavailable"
    else -> "error $code"
}
