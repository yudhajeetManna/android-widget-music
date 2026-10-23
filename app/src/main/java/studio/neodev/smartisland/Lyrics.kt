package studio.neodev.smartisland

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/** Synced lyrics from LRCLIB (free, no key). Cached on disk per track. */
object Lyrics {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val lrcLine = Regex("""^\[(\d+):(\d+(?:\.\d+)?)](.*)$""")
    private val noise = Regex(
        """[(\[][^)\]]*(official|video|audio|lyric|hd|4k|remaster|deluxe|bonus|explicit)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE
    )
    private val topic = Regex("""\s*-\s*topic$""", RegexOption.IGNORE_CASE)

    fun clean(s: String): String = s.replace(noise, "").replace(topic, "").trim()

    fun load(ctx: Context, t: Track, cb: (List<Line>) -> Unit) {
        val app = ctx.applicationContext
        io.execute {
            val lines = try { fetch(app, t) } catch (e: Exception) { emptyList() }
            main.post { cb(lines) }
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun JSONObject.synced(): String? =
        if (isNull("syncedLyrics")) null else optString("syncedLyrics").takeIf { it.isNotBlank() }

    private fun get(url: String): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Lizmo/0.2 (Android)")
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun fetch(ctx: Context, t: Track): List<Line> {
        val title = clean(t.title)
        val artist = clean(t.artist)
        val id = "$artist|$title".hashCode()
        val cache = File(ctx.cacheDir, "lrc_$id.lrc")
        if (cache.exists()) return parse(cache.readText())

        val q = StringBuilder("track_name=${enc(title)}&artist_name=${enc(artist)}")
        if (t.album.isNotBlank()) q.append("&album_name=${enc(t.album)}")
        if (t.durationMs > 0) q.append("&duration=${t.durationMs / 1000}")

        var synced: String? = get("https://lrclib.net/api/get?$q")?.let { JSONObject(it).synced() }
        if (synced == null) {
            val arr = get("https://lrclib.net/api/search?q=${enc("$artist $title")}")?.let { JSONArray(it) }
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    synced = arr.getJSONObject(i).synced()
                    if (synced != null) break
                }
            }
        }
        if (synced == null) return emptyList()
        cache.writeText(synced)
        return parse(synced)
    }

    fun parse(lrc: String): List<Line> =
        lrc.lineSequence().mapNotNull { row ->
            lrcLine.matchEntire(row.trim())?.let { m ->
                val secs = m.groupValues[1].toLong() * 60 + m.groupValues[2].toDouble()
                Line((secs * 1000).toLong(), m.groupValues[3].trim())
            }
        }.sortedBy { it.t }.toList()
}
