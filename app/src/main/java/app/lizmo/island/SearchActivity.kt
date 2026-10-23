package app.lizmo.island

import android.app.Activity
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/** Search any song (typed or spoken), scroll the results, tap to play in your music app. */
class SearchActivity : Activity() {
    companion object {
        const val EXTRA_VOICE = "voice"
        private const val REQ_VOICE = 42
    }

    private val h = Handler(Looper.getMainLooper())
    private val songs = ArrayList<Song>()
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var adapter: ResultAdapter
    private var searchId = 0
    private var silent = false
    private var preview: MediaPlayer? = null
    private val debounce = Runnable { runSearch(input.text.toString(), false) }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0B0B0F"))
            setPadding(dp(16), dp(40), dp(16), dp(8))
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        input = EditText(this).apply {
            hint = "Search any song or artist"
            setHintTextColor(Color.parseColor("#88FFFFFF"))
            setTextColor(Color.WHITE)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, id, _ ->
                if (id == EditorInfo.IME_ACTION_SEARCH) {
                    h.removeCallbacks(debounce)
                    runSearch(text.toString(), false)
                    true
                } else false
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (silent) return
                    h.removeCallbacks(debounce)
                    h.postDelayed(debounce, 450)
                }
            })
        }
        bar.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(Button(this).apply {
            text = "\uD83C\uDFA4"
            setOnClickListener { startVoice() }
        })
        root.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        status = TextView(this).apply {
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 12f
            text = "Type a song, or tap the mic and say it"
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(status)

        adapter = ResultAdapter(this, songs)
        val list = ListView(this).apply {
            divider = null
            dividerHeight = 0
            this.adapter = this@SearchActivity.adapter
            setOnItemClickListener { _, _, p, _ -> play(songs[p]) }
            setOnItemLongClickListener { _, _, p, _ -> preview(songs[p]); true }
        }
        root.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        if (intent.getBooleanExtra(EXTRA_VOICE, false)) h.postDelayed({ startVoice() }, 250)
    }

    // ---------- search ----------
    private fun runSearch(q: String, auto: Boolean) {
        val query = q.trim()
        if (query.length < 2) return
        val id = ++searchId
        status.text = "Searching\u2026"
        Catalog.search(query) { res ->
            if (id != searchId) return@search
            if (res == null) {
                status.text = "Couldn't reach the music catalog. Check your internet."
                return@search
            }
            songs.clear()
            songs.addAll(res)
            adapter.notifyDataSetChanged()
            status.text = if (res.isEmpty()) "No results" else "${res.size} results \u00B7 tap to play \u00B7 hold for a 30s preview"
            if (auto && res.isNotEmpty()) play(res[0])
        }
    }

    // ---------- voice ----------
    private fun startVoice() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Prefs.voiceLang(this@SearchActivity))
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a song or a command")
        }
        try {
            startActivityForResult(i, REQ_VOICE)
        } catch (e: Exception) {
            status.text = "Voice search isn't available on this phone."
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_VOICE || resultCode != RESULT_OK) return
        val said = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: return
        if (VoiceCommands.isControl(said)) {
            Toast.makeText(this, VoiceCommands.run(this, said), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val q = Catalog.cleanVoice(said)
        h.removeCallbacks(debounce)
        silent = true
        input.setText(q)
        silent = false
        status.text = "Heard: \"$said\""
        runSearch(q, true) // voice = find it and play the best match
    }

    // ---------- play ----------
    private fun play(s: Song) {
        stopPreview()
        val q = (s.title + " " + s.artist).trim()
        if (Playback.play(this, s)) {
            Toast.makeText(this, "Playing: $q", Toast.LENGTH_SHORT).show()
            finish()
        } else {
            status.text = "No music app can play from search. Hold a song for a 30s preview."
        }
    }

    // ---------- 30s preview (no music app needed) ----------
    private fun preview(s: Song) {
        if (s.preview.isBlank()) return
        stopPreview()
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            mp.setDataSource(s.preview)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { stopPreview() }
            mp.prepareAsync()
            preview = mp
            status.text = "Preview: " + s.title
        } catch (e: Exception) {
            preview = null
        }
    }

    private fun stopPreview() {
        try { preview?.release() } catch (e: Exception) {}
        preview = null
    }

    override fun onDestroy() {
        stopPreview()
        h.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}

class ResultAdapter(private val ctx: Context, private val items: List<Song>) : BaseAdapter() {
    private class Holder(val art: ImageView, val title: TextView, val sub: TextView)

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val holder: Holder
        val v: View
        if (convertView == null) {
            val art = ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#22FFFFFF"))
            }
            val title = TextView(ctx).apply {
                setTextColor(Color.WHITE)
                textSize = 15f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setTypeface(typeface, Typeface.BOLD)
            }
            val sub = TextView(ctx).apply {
                setTextColor(Color.parseColor("#99FFFFFF"))
                textSize = 12f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            val col = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(title)
                addView(sub)
            }
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
                addView(art, LinearLayout.LayoutParams(dp(52), dp(52)))
                addView(col, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                })
            }
            holder = Holder(art, title, sub)
            row.tag = holder
            v = row
        } else {
            v = convertView
            holder = convertView.tag as Holder
        }
        val s = items[position]
        holder.title.text = s.title
        holder.sub.text = if (s.album.isNotBlank()) s.artist + " \u00B7 " + s.album else s.artist
        ImageLoader.load(s.art, holder.art)
        return v
    }
}
