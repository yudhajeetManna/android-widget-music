package app.lizmo.island

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
import kotlin.math.abs

/**
 * Lyrics chain: LRCLIB (synced) -> NetEase (synced, unofficial) -> LRCLIB plain text with
 * estimated timing. Title/artist are normalised first (feat., "- Topic", "(Official Video)", etc).
 */
object Lyrics {
    data class Result(val lines: List<Line>, val source: String)
    private data class Found(val source: String, val text: String, val synced: Boolean)

    private val io = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    private val lrcLine = Regex("""^\[(\d+):(\d+(?:\.\d+)?)](.*)$""")
    private val noise = Regex(
        """[(\[][^)\]]*(official|video|audio|lyric|lyrics|hd|4k|remaster|remastered|live|from|feat|ft\.|with)[^)\]]*[)\]]""",
        RegexOption.IGNORE_CASE
    )
    private val dashTail = Regex(
        """\s+-\s+(remaster(ed)?|\d{4}|from\s|live|official|lyric).*$""",
        RegexOption.IGNORE_CASE
    )
    private val topic = Regex("""\s*-\s*topic$""", RegexOption.IGNORE_CASE)
    private val artistSplit = Regex("""\s*(,|&|;|\sfeat\.?\s|\sft\.?\s|\sx\s)\s*""", RegexOption.IGNORE_CASE)

    fun cleanTitle(s: String): String =
        s.replace(noise, "").replace(dashTail, "").replace(topic, "").trim()

    fun firstArtist(s: String): String =
        s.replace(topic, "").split(artistSplit).first().trim()

    fun load(ctx: Context, t: Track, force: Boolean = false, cb: (Result) -> Unit) {
        val app = ctx.applicationContext
        io.execute {
            val res = try { fetch(app, t, force) } catch (e: Exception) { Result(emptyList(), "none") }
            main.post { cb(res) }
        }
    }

    // ---------- pipeline ----------
    private fun fetch(ctx: Context, t: Track, force: Boolean): Result {
        var title = cleanTitle(t.title)
        var artist = firstArtist(t.artist)
        if (artist.isBlank() && title.contains(" - ")) {
            artist = title.substringBefore(" - ").trim()
            title = title.substringAfter(" - ").trim()
        }
        val id = "$artist|$title".hashCode()
        val cache = File(ctx.cacheDir, "lrc2_$id.txt")
        if (force) cache.delete()
        if (cache.exists()) readCache(cache, t.durationMs)?.let { return it }

        val lrc = safe { lrclib(title, artist, t.artist, t.album, t.durationMs) }
        val found = if (lrc != null && lrc.synced) lrc
        else (safe { netease(title, artist, t.durationMs) } ?: lrc)
        if (found == null) return Result(emptyList(), "none")

        cache.writeText("#" + found.source + "|" + (if (found.synced) "synced" else "plain") + "\n" + found.text)
        return toResult(found, t.durationMs)
    }

    private fun toResult(f: Found, durMs: Long): Result =
        if (f.synced) Result(parse(f.text), f.source)
        else Result(estimate(f.text, durMs), f.source + " est.")

    private fun readCache(f: File, durMs: Long): Result? {
        val txt = f.readText()
        val head = txt.lineSequence().firstOrNull() ?: return null
        if (!head.startsWith("#")) return null
        val parts = head.removePrefix("#").split("|")
        if (parts.size < 2) return null
        val body = txt.substringAfter("\n", "")
        return toResult(Found(parts[0], body, parts[1] == "synced"), durMs)
    }

    // ---------- LRCLIB ----------
    private fun lrclib(title: String, artist: String, rawArtist: String, album: String, durMs: Long): Found? {
        val durSec = durMs / 1000
        val base = "track_name=${enc(title)}&artist_name=${enc(artist)}"
        val exact = StringBuilder(base)
        if (album.isNotBlank()) exact.append("&album_name=${enc(album)}")
        if (durSec > 0) exact.append("&duration=$durSec")

        val rawBase = "track_name=${enc(title)}&artist_name=${enc(rawArtist)}"
        val attempts: List<() -> Found?> = listOf(
            { objAt("https://lrclib.net/api/get?$exact") },
            { if (rawArtist.isNotBlank() && rawArtist != artist) objAt("https://lrclib.net/api/get?$rawBase") else null },
            { objAt("https://lrclib.net/api/get?$base") },
            { listAt("https://lrclib.net/api/search?$base", durSec) },
            { listAt("https://lrclib.net/api/search?q=${enc(title)}", durSec) },
            { listAt("https://lrclib.net/api/search?q=${enc("$artist $title")}", durSec) }
        )
        var plain: Found? = null
        for (a in attempts) {
            val f = safe(a) ?: continue
            if (f.synced) return f
            if (plain == null) plain = f
        }
        return plain
    }

    private fun objAt(url: String): Found? = get(url)?.let { toFound(JSONObject(it)) }
    private fun listAt(url: String, durSec: Long): Found? = get(url)?.let { pickBest(JSONArray(it), durSec) }

    private fun JSONObject.synced(): String? =
        if (isNull("syncedLyrics")) null else optString("syncedLyrics").takeIf { it.isNotBlank() }

    private fun JSONObject.plain(): String? =
        if (isNull("plainLyrics")) null else optString("plainLyrics").takeIf { it.isNotBlank() }

    private fun toFound(o: JSONObject): Found? =
        o.synced()?.let { Found("LRCLIB", it, true) }
            ?: o.plain()?.let { Found("LRCLIB", it, false) }
            ?: if (o.optBoolean("instrumental", false)) Found("LRCLIB", "[00:00.00]\u266A Instrumental", true) else null

    private fun dist(o: JSONObject, durSec: Long): Double {
        val d = o.optDouble("duration", 0.0)
        return if (durSec > 0 && d > 0) abs(d - durSec) else 5.0
    }

    private fun pickBest(arr: JSONArray, durSec: Long): Found? {
        var best: JSONObject? = null
        var bestScore = Double.MAX_VALUE
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.synced() == null) continue
            val s = dist(o, durSec)
            if (s <= 12.0 && s < bestScore) { best = o; bestScore = s }
        }
        best?.let { b -> b.synced()?.let { return Found("LRCLIB", it, true) } }
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val p = o.plain() ?: continue
            if (dist(o, durSec) <= 15.0) return Found("LRCLIB", p, false)
        }
        return null
    }

    // ---------- NetEase (unofficial endpoints, may change) ----------
    private fun netease(title: String, artist: String, durMs: Long): Found? {
        val ref = "https://music.163.com"
        val s = get("$ref/api/search/get/web?csrf_token=&type=1&offset=0&limit=6&s=${enc("$artist $title")}", ref)
            ?: return null
        val songs = JSONObject(s).optJSONObject("result")?.optJSONArray("songs") ?: return null
        var id = -1L
        var bestScore = Long.MAX_VALUE
        for (i in 0 until songs.length()) {
            val o = songs.getJSONObject(i)
            val dur = o.optLong("duration", o.optLong("dt", 0L))
            val score = if (durMs > 0 && dur > 0) abs(dur - durMs) else 5000L + i
            if (score < bestScore) { bestScore = score; id = o.optLong("id", -1L) }
        }
        if (id < 0 || bestScore > 12_000L) return null
        val l = get("$ref/api/song/lyric?id=$id&lv=1&kv=1&tv=-1", ref) ?: return null
        val txt = JSONObject(l).optJSONObject("lrc")?.optString("lyric") ?: return null
        return if (txt.isBlank()) null else Found("NetEase", txt, true)
    }

    // ---------- helpers ----------
    /** Plain lyrics have no timestamps: spread the lines evenly across the song. */
    private fun estimate(plain: String, durMs: Long): List<Line> {
        val rows = plain.lines().map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("[") }
        if (rows.isEmpty()) return emptyList()
        val total = if (durMs > 0) durMs else 200_000L
        val start = (total * 0.06).toLong()
        val span = (total * 0.88).toLong()
        return rows.mapIndexed { i, r -> Line(start + span * i / rows.size, r) }
    }

    fun parse(lrc: String): List<Line> = LrcParser.parse(lrc)

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun <T> safe(f: () -> T?): T? = try { f() } catch (e: Exception) { null }

    private fun get(url: String, referer: String? = null): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Lizmo/0.2.0")
            if (referer != null) c.setRequestProperty("Referer", referer)
            if (c.responseCode != 200) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
