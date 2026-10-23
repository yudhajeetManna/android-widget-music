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
import java.util.Locale
import java.util.concurrent.Executors

/**
 * English lyrics for any language: line-by-line machine translation (Google Translate web endpoint,
 * no key). Sends the lyric text to Google. Cached on the phone per song.
 */
object Translator {
    /** en: same size as the input lines; src: detected language code ("hi", "ko", "en"...). */
    data class Out(val en: List<String>, val src: String)

    val sourceChoices = listOf(
        "auto", "hi", "pa", "bn", "ur", "ta", "te", "mr", "gu", "kn", "ml", "ne",
        "ko", "ja", "zh-CN", "es", "fr", "ar", "ru", "tr"
    )

    private val io = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    private val englishWords = setOf(
        "the", "and", "you", "i", "me", "my", "to", "a", "of", "in", "it", "is", "that", "for", "on", "with",
        "this", "we", "be", "are", "was", "but", "not", "your", "all", "so", "just", "like", "oh", "baby",
        "know", "love", "be", "can", "when", "what", "if", "no", "up", "get", "got", "now", "yeah", "im", "dont"
    )

    fun langName(code: String): String {
        if (code.isBlank() || code == "auto") return "Auto"
        val n = Locale(code.substringBefore("-")).getDisplayLanguage(Locale.ENGLISH)
        return if (n.isBlank()) code else n
    }

    /** Cheap check so English songs don't need a network call. */
    fun looksEnglish(texts: List<String>): Boolean {
        val tokens = texts.joinToString(" ").lowercase().split(Regex("""[^\p{L}']+""")).filter { it.isNotEmpty() }
        if (tokens.size < 12) return false
        if (tokens.any { t -> t.any { it.code > 0x24F } }) return false // non-Latin script
        val hits = tokens.count { it in englishWords || it.replace("'", "") in englishWords }
        return hits.toDouble() / tokens.size >= 0.22
    }

    fun translate(ctx: Context, key: String, texts: List<String>, srcHint: String, cb: (Out?) -> Unit) {
        val app = ctx.applicationContext
        io.execute {
            val out = try { run(app, key, texts, srcHint) } catch (e: Exception) { null }
            main.post { cb(out) }
        }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun singable(s: String): Boolean = s.isNotBlank() && s.any { it.isLetter() }

    private fun run(ctx: Context, key: String, texts: List<String>, srcHint: String): Out? {
        val src = srcHint.ifBlank { "auto" }
        val cache = File(ctx.cacheDir, "tr_" + (key + "|" + src).hashCode() + ".json")
        if (cache.exists()) {
            try {
                val j = JSONObject(cache.readText())
                val arr = j.getJSONArray("en")
                if (arr.length() == texts.size) {
                    return Out(List(arr.length()) { arr.getString(it) }, j.optString("src"))
                }
            } catch (e: Exception) { /* refetch */ }
        }
        if (src == "auto" && looksEnglish(texts)) return Out(texts, "en")

        val idx = texts.indices.filter { singable(texts[it]) }
        if (idx.isEmpty()) return Out(texts, "en")

        val result = MutableList(texts.size) { texts[it] }
        var detected = ""
        var i = 0
        while (i < idx.size) {
            val chunk = ArrayList<Int>()
            var size = 0
            while (i < idx.size && chunk.size < 40) {
                val len = enc(texts[idx[i]]).length + 3
                if (chunk.isNotEmpty() && size + len > 1600) break
                chunk.add(idx[i])
                size += len
                i++
            }
            val joined = chunk.joinToString("\n") { texts[it] }
            val r = gt(joined, src) ?: return null
            if (detected.isEmpty()) detected = r.second
            val parts = r.first.trimEnd('\n').split("\n")
            if (parts.size == chunk.size) {
                for (k in chunk.indices) result[chunk[k]] = parts[k].trim()
            } else {
                // lines got merged/split by the translator: do this chunk one line at a time
                for (n in chunk) {
                    val one = gt(texts[n], src) ?: return null
                    result[n] = one.first.trim()
                }
            }
        }
        try {
            val j = JSONObject()
            j.put("src", detected)
            j.put("en", JSONArray(result))
            cache.writeText(j.toString())
        } catch (e: Exception) {}
        return Out(result, detected)
    }

    /** Returns (English text, detected source language) or null on failure. */
    private fun gt(text: String, src: String): Pair<String, String>? {
        val url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=$src&tl=en&dt=t&dj=1&q=" + enc(text)
        val c = URL(url).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Lizmo/0.2.0")
            if (c.responseCode != 200) return null
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val arr = j.getJSONArray("sentences")
            val sb = StringBuilder()
            for (k in 0 until arr.length()) sb.append(arr.getJSONObject(k).optString("trans"))
            Pair(sb.toString(), j.optString("src"))
        } finally {
            c.disconnect()
        }
    }
}
