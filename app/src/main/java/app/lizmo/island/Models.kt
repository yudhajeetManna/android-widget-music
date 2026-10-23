package app.lizmo.island

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color

data class Track(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val art: Bitmap?
)

data class Line(val t: Long, val text: String, val en: String = "")

enum class IslandTheme(
    val bg1: Int,
    val bg2: Int,
    val fg: Int,
    val stroke: Int,
    val accentStroke: Boolean = false,
    val light: Boolean = false
) {
    GLASS(Color.parseColor("#E6101014"), Color.parseColor("#E61A1A24"), Color.WHITE, Color.parseColor("#22FFFFFF")),
    NEON(Color.parseColor("#FF05010F"), Color.parseColor("#FF05010F"), Color.parseColor("#FFE8E0FF"), 0, accentStroke = true),
    MINIMAL(Color.BLACK, Color.BLACK, Color.WHITE, 0),
    SUNSET(Color.parseColor("#FFFF5F6D"), Color.parseColor("#FF6A1B9A"), Color.WHITE, 0),
    MONO(Color.parseColor("#FFF4F4F5"), Color.parseColor("#FFE4E4E7"), Color.parseColor("#FF111111"), Color.parseColor("#22000000"), light = true);

    fun next(): IslandTheme = values()[(ordinal + 1) % values().size]
}

/**
 * Voice sensitivity: controls the minimum RMS dB level the microphone must register
 * before a partial result triggers the wake-word scan. Low = whisper-sensitive,
 * High = only loud/clear speech triggers (useful in noisy environments).
 */
enum class MicSensitivity(val label: String, val rmsThresholdDb: Float) {
    LOW("Low (whisper friendly)", -15f),      // fires on quiet speech; may pick up background noise
    MEDIUM("Medium (recommended)", -8f),       // balanced: normal speaking distance
    HIGH("High (loud/noisy room)", 0f);        // only fires on clear, loud speech

    fun next(): MicSensitivity = values()[(ordinal + 1) % values().size]
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("island", Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun theme(c: Context): IslandTheme =
        try { IslandTheme.valueOf(sp(c).getString("theme", "GLASS") ?: "GLASS") }
        catch (e: Exception) { IslandTheme.GLASS }

    fun setTheme(c: Context, t: IslandTheme) = sp(c).edit().putString("theme", t.name).apply()

    /** Comma-separated names; add the spellings your phone mishears (e.g. "lizmo, lismo, limo"). */
    const val DEFAULT_WAKE = "lizmo, lismo, lizma, limo"
    fun wake(c: Context): String = sp(c).getString("wake", DEFAULT_WAKE) ?: DEFAULT_WAKE
    fun setWake(c: Context, v: String) = sp(c).edit().putString("wake", v).apply()

    fun voiceLang(c: Context): String = sp(c).getString("vlang", "en-IN") ?: "en-IN"
    fun setVoiceLang(c: Context, v: String) = sp(c).edit().putString("vlang", v).apply()

    /** "orig" or "en" */
    fun lyricMode(c: Context): String = sp(c).getString("lmode", "orig") ?: "orig"
    fun setLyricMode(c: Context, v: String) = sp(c).edit().putString("lmode", v).apply()

    /** Per-song override of the lyric language ("auto" = detect). */
    fun songLang(c: Context, key: String): String = sp(c).getString("sl:" + key.hashCode(), "auto") ?: "auto"
    fun setSongLang(c: Context, key: String, v: String) = sp(c).edit().putString("sl:" + key.hashCode(), v).apply()

    /** Defaults to true — always-on listening is enabled out of the box. */
    fun voiceOn(c: Context): Boolean = sp(c).getBoolean("voice", true)
    fun setVoiceOn(c: Context, v: Boolean) = sp(c).edit().putBoolean("voice", v).apply()

    /** True the very first time Lizmo runs (used to auto-request mic permission). */
    fun isFirstLaunch(c: Context): Boolean = sp(c).getBoolean("first_launch", true)
    fun setFirstLaunchDone(c: Context) = sp(c).edit().putBoolean("first_launch", false).apply()

    /** Microphone sensitivity: LOW / MEDIUM / HIGH */
    fun micSensitivity(c: Context): MicSensitivity =
        try { MicSensitivity.valueOf(sp(c).getString("mic_sens", "MEDIUM") ?: "MEDIUM") }
        catch (e: Exception) { MicSensitivity.MEDIUM }

    fun setMicSensitivity(c: Context, v: MicSensitivity) = sp(c).edit().putString("mic_sens", v.name).apply()
}
