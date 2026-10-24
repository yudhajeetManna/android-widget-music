package app.lizmo.island

import android.content.Context
import android.media.AudioManager
import android.widget.Toast
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// VoiceBus — the bridge between VoiceService, VoiceHud, and the Island
// ─────────────────────────────────────────────────────────────────────────────

object VoiceBus {
    @Volatile var island: ((String) -> Boolean)? = null

    /** Live mic/debug status shown on Settings screen. */
    @Volatile var lastLog: String = "Voice control is off"
    @Volatile var logListener: ((String) -> Unit)? = null

    /** Live RMS dB stream from the microphone (for VU meter display). */
    @Volatile var rmsListener: ((Float) -> Unit)? = null

    /** Command HUD callback registered by IslandController. */
    @Volatile var commandHud: ((String) -> Unit)? = null

    fun log(msg: String) {
        lastLog = msg
        logListener?.invoke(msg)
    }

    fun say(ctx: Context, msg: String, ms: Long = 2500) {
        // First try the standalone VoiceHud overlay
        if (VoiceHud.show(ctx, msg, ms)) return
        // Otherwise flash on island or show toast
        val shown = island?.invoke(msg) ?: false
        if (!shown) Toast.makeText(ctx.applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    /** Show the command HUD on both the Island and VoiceHud pill. */
    fun showCommand(ctx: Context, label: String) {
        commandHud?.invoke(label)
        VoiceHud.show(ctx, label, 2200)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// VoiceCommands — natural language command parser and executor
// ─────────────────────────────────────────────────────────────────────────────

object VoiceCommands {

    // ── Pre-processing & Normalization ────────────────────────────────────────

    private val LEADING_FLUFF = Regex(
        """^(hey\s+lizmo|ok\s+lizmo|lizmo|can\s+you\s+please|could\s+you\s+please|can\s+you|could\s+you|would\s+you|please|kindly|just|a\s+little\s+bit|a\s+bit|a\s+little)\s+""",
        RegexOption.IGNORE_CASE
    )

    private val TRAILING_FLUFF = Regex(
        """\s+(a\s+little\s+bit|a\s+bit|a\s+little|please|now|thanks|thank\s+you|for\s+me)\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val WORD_TO_NUM = listOf(
        Regex("""\bzero\b""", RegexOption.IGNORE_CASE) to "0",
        Regex("""\bten\b""", RegexOption.IGNORE_CASE) to "10",
        Regex("""\btwenty\b""", RegexOption.IGNORE_CASE) to "20",
        Regex("""\bthirty\b""", RegexOption.IGNORE_CASE) to "30",
        Regex("""\bforty\b""", RegexOption.IGNORE_CASE) to "40",
        Regex("""\bfifty\b""", RegexOption.IGNORE_CASE) to "50",
        Regex("""\bsixty\b""", RegexOption.IGNORE_CASE) to "60",
        Regex("""\bseventy\b""", RegexOption.IGNORE_CASE) to "70",
        Regex("""\beighty\b""", RegexOption.IGNORE_CASE) to "80",
        Regex("""\bninety\b""", RegexOption.IGNORE_CASE) to "90",
        Regex("""\bhundred\b""", RegexOption.IGNORE_CASE) to "100",
        Regex("""\bone\s+hundred\b""", RegexOption.IGNORE_CASE) to "100",
        Regex("""\bhalf\b""", RegexOption.IGNORE_CASE) to "50"
    )

    /** Cleans punctuation, removes leading/trailing conversational padding, and converts number words. */
    fun normalize(raw: String): String {
        var s = raw.lowercase()
            .replace("'", "") // e.g. "what's" -> "whats", "don't" -> "dont"
            .replace(Regex("""[^\p{L}\p{N}%\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        // Strip conversational prefixes and suffixes
        s = s.replace(LEADING_FLUFF, "").replace(TRAILING_FLUFF, "").trim()

        // Convert number words to digits for volume parsing
        for ((re, replacement) in WORD_TO_NUM) {
            s = s.replace(re, replacement)
        }
        return s.replace(Regex("""\s+"""), " ").trim()
    }

    // ── Regex Patterns ────────────────────────────────────────────────────────

    // Playback
    private val pauseRe = Regex(
        """^(pause|pause\s+(the\s+)?(music|song|track|playback)|stop|stop\s+(the\s+)?(music|song|track|playback)|hold\s+on|hold\s+playback|freeze)$"""
    )
    private val playRe = Regex(
        """^(play|resume|continue|unpause|(play|resume)\s+(the\s+)?(music|song|track|playback)|keep\s+playing|start\s+playing)$"""
    )
    private val nextRe = Regex(
        """^(next|skip|next\s+(song|track)|skip\s+(this\s+)?(song|track|one)|play\s+next(\s+(song|track))?|skip\s+ahead|skip\s+to\s+next)$"""
    )
    private val prevRe = Regex(
        """^(previous|prev|back|go\s+back|last\s+(song|track|one)|previous\s+(song|track)|play\s+previous(\s+(song|track))?|go\s+back\s+to\s+previous(\s+(song|track))?)$"""
    )
    private val restartRe = Regex(
        """^(restart(\s+(the|this)\s+(song|track))?|start\s+over|play\s+from\s+(the\s+)?(beginning|start)|replay(\s+(this|the)\s+(song|track))?)$"""
    )
    private val stopRe = Regex(
        """^(stop\s+music|stop\s+playing|stop\s+playback|stop\s+it|quit\s+playing|shut\s+up)$"""
    )

    // Seeking
    private val fwdRe = Regex(
        """^((skip|fast)\s+)?forward(\s+by)?(\s+(\d+)\s+minutes?)?(\s+and)?(\s+(\d+)\s+seconds?)?$"""
    )
    private val rwdRe = Regex(
        """^(rewind|go\s+back|skip\s+back)(\s+by)?(\s+(\d+)\s+minutes?)?(\s+and)?(\s+(\d+)\s+seconds?)?$"""
    )
    private val jumpRe = Regex(
        """^(jump\s+to|seek\s+to)(\s+(\d+)\s+minutes?)?(\s+and)?(\s+(\d+)\s+seconds?)?$"""
    )

    // Volume
    private val upRe = Regex(
        """^(volume\s+up|louder|make\s+it\s+louder|turn\s+(it\s+)?up|turn\s+(the\s+)?volume\s+up|(increase|raise|boost|turn\s+up)\s+(the\s+)?volume|more\s+volume|volume\s+higher|higher\s+volume)$"""
    )
    private val downRe = Regex(
        """^(volume\s+down|quieter|make\s+it\s+quieter|softer|lower|turn\s+(it\s+)?down|turn\s+(the\s+)?volume\s+down|(decrease|lower|reduce|turn\s+down)\s+(the\s+)?volume|less\s+volume|volume\s+lower|lower\s+the\s+volume)$"""
    )
    private val maxRe = Regex(
        """^((max|full|maximum)\s+volume|volume\s+(max|full|maximum|100)|turn\s+(it\s+)?all\s+the\s+way\s+up)$"""
    )
    private val muteRe = Regex(
        """^(mute|mute\s+(the\s+)?(music|sound|volume|audio)|silence)$"""
    )
    private val unmuteRe = Regex(
        """^(unmute|unmute\s+(the\s+)?(music|sound|volume|audio)|un\s+mute|turn\s+(the\s+)?sound\s+back\s+on)$"""
    )
    private val setVolRe = Regex(
        """^(set|turn)?\s*(the\s+)?volume(\s+(to|at))?\s+(\d{1,3})(\s*(%|percent))?$"""
    )
    private val volQueryRe = Regex(
        """^(what(s|\s+is)\s+the\s+volume|volume\s+level|how\s+loud(\s+(is\s+)?(it|the\s+music))?)$"""
    )

    // Shuffle
    private val shuffleOnRe = Regex(
        """^(shuffle(\s+on)?|turn\s+on\s+shuffle|(enable|start|activate)\s+shuffle|play\s+(on\s+)?shuffle|mix\s+it\s+up)$"""
    )
    private val shuffleOffRe = Regex(
        """^(shuffle\s+off|turn\s+off\s+shuffle|(disable|stop)\s+shuffle|no\s+shuffle)$"""
    )

    // Repeat
    private val repeatOneRe = Regex(
        """^(repeat(\s+this)?(\s+(song|track|one))?|loop(\s+this)?(\s+(song|track))?|repeat\s+one|play\s+(on\s+)?repeat)$"""
    )
    private val repeatAllRe = Regex(
        """^(repeat\s+all|loop\s+all|repeat\s+playlist|loop\s+playlist)$"""
    )
    private val repeatOffRe = Regex(
        """^(repeat\s+off|no\s+repeat|(stop|disable)\s+repeat|(stop|disable)\s+loop(ing)?)$"""
    )

    // Like / Dislike
    private val likeRe = Regex(
        """^(like(\s+this(\s+(song|track))?)?|thumbs\s+up|(add\s+to\s+)?favourites?|save\s+(this\s+)?song|love\s+(this\s+)?song)$"""
    )
    private val dislikeRe = Regex(
        """^(dislike(\s+this(\s+(song|track))?)?|thumbs\s+down|dont\s+like\s+(this\s+)?song)$"""
    )

    // Info
    private val nowPlayingRe = Regex(
        """^(what(s|\s+is)\s+playing|what\s+(song|track)\s+is\s+(this|playing)|what\s+am\s+i\s+listening\s+to|name\s+(this|the)\s+song|which\s+song\s+is\s+this)$"""
    )

    // ─────────────────────────────────────────────────────────────────────────

    /** Returns true if [raw] is definitely a media control command and not a song query. */
    fun isControl(raw: String): Boolean {
        val t = normalize(raw)
        if (t.isBlank()) return false
        return pauseRe.matches(t) || playRe.matches(t) || nextRe.matches(t) || prevRe.matches(t) ||
               restartRe.matches(t) || stopRe.matches(t) ||
               upRe.matches(t) || downRe.matches(t) || maxRe.matches(t) ||
               muteRe.matches(t) || unmuteRe.matches(t) || setVolRe.matches(t) || volQueryRe.matches(t) ||
               shuffleOnRe.matches(t) || shuffleOffRe.matches(t) ||
               repeatOneRe.matches(t) || repeatAllRe.matches(t) || repeatOffRe.matches(t) ||
               likeRe.matches(t) || dislikeRe.matches(t) || nowPlayingRe.matches(t) ||
               fwdRe.matches(t) || rwdRe.matches(t) || jumpRe.matches(t)
    }

    /** Runs the command and returns a short user-facing feedback string. */
    fun run(ctx: Context, raw: String): String {
        val t = normalize(raw)
        return when {
            // ── Playback ──────────────────────────────────────────────────────
            pauseRe.matches(t)   -> { Playback.pause(ctx);   hud(ctx, "⏸ Paused");    "Paused" }
            playRe.matches(t)    -> { Playback.resume(ctx);  hud(ctx, "▶ Playing");   "Playing" }
            nextRe.matches(t)    -> { Playback.next(ctx);    hud(ctx, "⏭ Next");      "Next" }
            prevRe.matches(t)    -> { Playback.prev(ctx);    hud(ctx, "⏮ Previous");  "Previous" }
            restartRe.matches(t) -> { Playback.restart(ctx); hud(ctx, "↩ Restart");   "Restarting" }
            stopRe.matches(t)    -> { Playback.stop(ctx);    hud(ctx, "⏹ Stopped");   "Stopped" }

            // ── Seeking ───────────────────────────────────────────────────────
            fwdRe.matches(t) -> {
                val ms = parseTimeMs(t) ?: 30_000L
                Playback.seek(ctx, ms)
                val label = "⏩ +${formatTime(ms)}"
                hud(ctx, label); label
            }
            rwdRe.matches(t) -> {
                val ms = parseTimeMs(t) ?: 15_000L
                Playback.seek(ctx, -ms)
                val label = "⏪ −${formatTime(ms)}"
                hud(ctx, label); label
            }
            jumpRe.matches(t) -> {
                val ms = parseTimeMs(t) ?: 0L
                Playback.controller(ctx)?.transportControls?.seekTo(ms)
                val label = "⏩ → ${formatTime(ms)}"
                hud(ctx, label); label
            }

            // ── Volume ────────────────────────────────────────────────────────
            upRe.matches(t)    -> { stepVolume(ctx, true);  hud(ctx, "🔊 Louder");     "Volume up" }
            downRe.matches(t)  -> { stepVolume(ctx, false); hud(ctx, "🔉 Quieter");    "Volume down" }
            maxRe.matches(t)   -> { setVolumePct(ctx, 100); hud(ctx, "🔊 Max Volume"); "Volume max" }
            muteRe.matches(t)  -> { mute(ctx, true);        hud(ctx, "🔇 Muted");      "Muted" }
            unmuteRe.matches(t)-> { mute(ctx, false);       hud(ctx, "🔊 Unmuted");    "Unmuted" }
            setVolRe.matches(t)-> {
                val match = setVolRe.matchEntire(t)!!
                val n = match.groupValues[5].toInt().coerceIn(0, 100)
                setVolumePct(ctx, n)
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
                Playback.setShuffle(ctx, false)
                hud(ctx, "🔀 Shuffle off"); "Shuffle off"
            }
            shuffleOnRe.matches(t)  -> {
                Playback.setShuffle(ctx, true)
                hud(ctx, "🔀 Shuffle on"); "Shuffle on"
            }

            // ── Repeat ────────────────────────────────────────────────────────
            repeatAllRe.matches(t) -> {
                Playback.setRepeat(ctx, Playback.REPEAT_MODE_ALL)
                hud(ctx, "🔁 Repeat all"); "Repeat all"
            }
            repeatOffRe.matches(t) -> {
                Playback.setRepeat(ctx, Playback.REPEAT_MODE_NONE)
                hud(ctx, "➡ Repeat off"); "Repeat off"
            }
            repeatOneRe.matches(t) -> {
                Playback.setRepeat(ctx, Playback.REPEAT_MODE_ONE)
                hud(ctx, "🔂 Repeat one"); "Repeat this song"
            }

            // ── Like / Dislike ────────────────────────────────────────────────
            likeRe.matches(t) -> {
                val ok = Playback.like(ctx)
                val msg = if (ok) "❤ Liked!" else "❤ Liked"
                hud(ctx, msg); msg
            }
            dislikeRe.matches(t) -> {
                val ok = Playback.dislike(ctx)
                val msg = if (ok) "👎 Disliked" else "👎 Disliked"
                hud(ctx, msg); msg
            }

            // ── Info ──────────────────────────────────────────────────────────
            nowPlayingRe.matches(t) -> {
                val info = Playback.nowPlaying(ctx) ?: "Nothing playing"
                hud(ctx, "🎵 $info"); info
            }

            // ── Song Search & Play ────────────────────────────────────────────
            else -> search(ctx, raw)
        }
    }

    // ── Helper Functions ──────────────────────────────────────────────────────

    private fun hud(ctx: Context, label: String) = VoiceBus.showCommand(ctx, label)

    private fun am(ctx: Context) = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun stepVolume(ctx: Context, up: Boolean) {
        try {
            val d = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            am(ctx).adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {}
    }

    private fun mute(ctx: Context, on: Boolean) {
        try {
            val d = if (on) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            am(ctx).adjustStreamVolume(AudioManager.STREAM_MUSIC, d, AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {}
    }

    private fun setVolumePct(ctx: Context, pct: Int) {
        try {
            val a = am(ctx)
            val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            a.setStreamVolume(AudioManager.STREAM_MUSIC, (pct * max / 100.0).roundToInt(), AudioManager.FLAG_SHOW_UI)
        } catch (e: Exception) {}
    }

    private fun parseTimeMs(text: String): Long? {
        val numRe = Regex("""(\d+)\s*(minute|min|m\b)|(\d+)\s*(second|sec|s\b)""")
        var minutes = 0L
        var seconds = 0L
        for (n in numRe.findAll(text)) {
            val v = (n.groupValues[1].toLongOrNull() ?: n.groupValues[3].toLongOrNull()) ?: continue
            val unit = n.groupValues[2].ifEmpty { n.groupValues[4] }
            if (unit.startsWith("m")) minutes = v else seconds = v
        }
        if (minutes == 0L && seconds == 0L) {
            val plain = Regex("""(?<!\d)(\d+)(?!\d)""").find(text)?.groupValues?.get(1)?.toLongOrNull()
            if (plain != null) seconds = plain
        }
        val total = minutes * 60_000L + seconds * 1000L
        return if (total > 0) total else null
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        return if (m > 0) "${m}m ${s % 60}s" else "${s}s"
    }

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
            if (s != null && Playback.play(ctx, s)) {
                hud(ctx, "🎵 Playing: ${s.title}")
            }
        }
        return "Searching \"$q\"…"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// WakeWord — fuzzy matcher for fallback speech recognizer
// ─────────────────────────────────────────────────────────────────────────────

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

    fun extract(said: String, wakeCsv: String): String? {
        for (alias in wakeCsv.split(",")) {
            val r = extractOne(said, alias.trim())
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
            for (k in w.indices) {
                if (!close(t[i + k], w[k])) { ok = false; break }
            }
            if (ok) return t.drop(i + w.size).joinToString(" ")
            if (w.size == 1 && i + 1 < t.size && close(t[i] + t[i + 1], w[0])) {
                return t.drop(i + 2).joinToString(" ")
            }
        }
        return null
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Speech error descriptor
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
