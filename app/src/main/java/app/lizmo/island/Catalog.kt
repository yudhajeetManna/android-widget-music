package app.lizmo.island

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

data class Song(
    val title: String,
    val artist: String,
    val album: String,
    val art: String,
    val preview: String
)

/** Key-free song catalog (iTunes Search). Used only to find/identify songs. */
object Catalog {
    private val io = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    private val voicePrefix = Regex(
        """^(hey\s+)?(lizmo\s+)?(please\s+)?(play|search( for)?|find|put on|listen to)\s+(the\s+)?(song\s+|music\s+|track\s+)?""",
        RegexOption.IGNORE_CASE
    )
    private val voiceSuffix = Regex(
        """\s+(on|in|using)\s+(spotify|youtube( music)?|music|jiosaavn|gaana|apple music)\s*$""",
        RegexOption.IGNORE_CASE
    )

    /** "play kesariya on spotify" -> "kesariya" */
    fun cleanVoice(s: String): String = s.trim().replace(voicePrefix, "").replace(voiceSuffix, "").trim()

    /** cb gets null when the network/catalog failed, empty list when nothing matched. */
    fun search(query: String, cb: (List<Song>?) -> Unit) {
        io.execute {
            val res = try { fetch(query) } catch (e: Exception) { null }
            main.post { cb(res) }
        }
    }

    private fun fetch(q: String): List<Song> {
        val country = Locale.getDefault().country.ifBlank { "US" }
        val url = "https://itunes.apple.com/search?media=music&entity=song&limit=30" +
            "&country=$country&term=" + URLEncoder.encode(q, "UTF-8")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            if (c.responseCode != 200) throw java.io.IOException("http " + c.responseCode)
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(body).getJSONArray("results")
            val out = ArrayList<Song>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val title = o.optString("trackName")
                if (title.isBlank()) continue
                out.add(
                    Song(
                        title,
                        o.optString("artistName"),
                        o.optString("collectionName"),
                        o.optString("artworkUrl100").replace("100x100bb", "200x200bb"),
                        o.optString("previewUrl")
                    )
                )
            }
            return out
        } finally {
            c.disconnect()
        }
    }
}

/** Tiny async image loader with an in-memory cache. */
object ImageLoader {
    private val io = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(8 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun load(url: String, view: ImageView) {
        view.tag = url
        val hit = cache.get(url)
        if (hit != null) {
            view.setImageBitmap(hit)
            return
        }
        view.setImageDrawable(null)
        if (url.isBlank()) return
        io.execute {
            val bmp = try {
                URL(url).openStream().use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) {
                null
            }
            if (bmp != null) {
                cache.put(url, bmp)
                main.post { if (view.tag == url) view.setImageBitmap(bmp) }
            }
        }
    }
}
