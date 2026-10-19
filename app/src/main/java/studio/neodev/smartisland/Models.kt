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
    MINIMAL(Color.BLACK, Color.BLACK, Color.WHITE, 0),
    SUNSET(Color.parseColor("#FFFF5F6D"), Color.parseColor("#FF6A1B9A"), Color.WHITE, 0),
    MONO(Color.parseColor("#FFF4F4F5"), Color.parseColor("#FFE4E4E7"), Color.parseColor("#FF111111"), Color.parseColor("#22000000"), light = true);

    fun next(): IslandTheme = values()[(ordinal + 1) % values().size]
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("island", Context.MODE_PRIVATE)

    fun enabled(c: Context): Boolean = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, v: Boolean) = sp(c).edit().putBoolean("enabled", v).apply()

    fun theme(c: Context): IslandTheme =
        try { IslandTheme.valueOf(sp(c).getString("theme", "GLASS") ?: "GLASS") }
        catch (e: Exception) { IslandTheme.GLASS }

    fun setTheme(c: Context, t: IslandTheme) = sp(c).edit().putString("theme", t.name).apply()
}
