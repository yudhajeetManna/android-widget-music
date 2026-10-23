package studio.neodev.smartisland

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

data class Line(val t: Long, val text: String)

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
    CYBERPUNK(Color.parseColor("#FF080318"), Color.parseColor("#FF150529"), Color.parseColor("#FF00F0FF"), Color.parseColor("#44FF0055"), accentStroke = true),
    EMERALD(Color.parseColor("#FF03140E"), Color.parseColor("#FF07251B"), Color.parseColor("#FF38EF7D"), Color.parseColor("#4411998E")),
    AMBER(Color.parseColor("#FF170D03"), Color.parseColor("#FF291605"), Color.parseColor("#FFFFB300"), Color.parseColor("#44FF8F00")),
    MINIMAL(Color.BLACK, Color.BLACK, Color.WHITE, 0),
    SUNSET(Color.parseColor("#FFFF5F6D"), Color.parseColor("#FF6A1B9A"), Color.WHITE, 0),
    MONO(Color.parseColor("#FFF4F4F5"), Color.parseColor("#FFE4E4E7"), Color.parseColor("#FF111111"), Color.parseColor("#22000000"), light = true);

    fun next(): IslandTheme = values()[(ordinal + 1) % values().size]
}

enum class AudioSensitivity(val label: String, val minRmsDb: Float, val desc: String) {
    LOW("Low", 4.0f, "Noise filter (Speak loud)"),
    MED("Med", 1.5f, "Standard conversational"),
    HIGH("High", -1.0f, "Sensitive (Quiet / whispers)");

    fun next(): AudioSensitivity = values()[(ordinal + 1) % values().size]
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("island", Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun theme(c: Context): IslandTheme =
        try { IslandTheme.valueOf(sp(c).getString("theme", "GLASS") ?: "GLASS") }
        catch (e: Exception) { IslandTheme.GLASS }

    fun setTheme(c: Context, t: IslandTheme) = sp(c).edit().putString("theme", t.name).apply()

    fun yOffsetDp(c: Context): Int = sp(c).getInt("y_offset_dp", 6)
    fun setYOffsetDp(c: Context, v: Int) = sp(c).edit().putInt("y_offset_dp", v).apply()

    fun hapticEnabled(c: Context): Boolean = sp(c).getBoolean("haptic_enabled", true)
    fun setHapticEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("haptic_enabled", v).apply()

    fun gesturesEnabled(c: Context): Boolean = sp(c).getBoolean("gestures_enabled", true)
    fun setGesturesEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("gestures_enabled", v).apply()

    fun voiceEnabled(c: Context): Boolean = sp(c).getBoolean("voice_enabled", true)
    fun setVoiceEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("voice_enabled", v).apply()

    fun voiceSensitivity(c: Context): AudioSensitivity =
        try { AudioSensitivity.valueOf(sp(c).getString("voice_sensitivity", "MED") ?: "MED") }
        catch (e: Exception) { AudioSensitivity.MED }

    fun setVoiceSensitivity(c: Context, s: AudioSensitivity) =
        sp(c).edit().putString("voice_sensitivity", s.name).apply()
}
