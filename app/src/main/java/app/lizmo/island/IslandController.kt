package app.lizmo.island

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class IslandController(private val ctx: Context, private val tracker: MediaTracker) {

    var enabled = true

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val h = Handler(Looper.getMainLooper())
    private val density = ctx.resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * density).toInt()
    private fun screenW(): Int = ctx.resources.displayMetrics.widthPixels
    private fun lerp(a: Int, b: Int, p: Float): Int = (a + (b - a) * p).toInt()

    private var theme = Prefs.theme(ctx)
    private var accent = Color.parseColor("#7C5CFF")
    private var expanded = false
    private var attached = false
    private var attachedAt = 0L
    private var lines: List<Line> = emptyList()
    private var lyricsLoaded = false
    private var lyricsSource = ""
    private var lyricKey = ""
    private var lyricMode = Prefs.lyricMode(ctx)   // "orig" or "en"
    private var trState = 0                         // 0 none, 1 loading, 2 ready, 3 failed
    private var trSrc = ""
    private var lastCur = "\u0000"
    private var lastArt: Bitmap? = null
    private var lastPlaying: Boolean? = null
    private var anim: ValueAnimator? = null

    // 0 = pill, 1 = fully expanded; drives width, height, padding, art size and content fade
    private var progress = 0f
    private var heightC = 0
    private var heightE = 0

    private var flashMsg = ""
    private var flashUntil = 0L

    // ---- views ----
    private val root = LinearLayout(ctx)
    private val art = ImageView(ctx)
    private val mainTv = TextView(ctx)
    private val subTv = TextView(ctx)
    private val eq = EqView(ctx)
    private val lyricBox = LinearLayout(ctx)
    private val curTv = TextView(ctx)
    private val nextTv = TextView(ctx)
    private val bar = BarView(ctx)
    private val controls = LinearLayout(ctx)
    private val prevBtn = ImageView(ctx)
    private val playBtn = ImageView(ctx)
    private val nextBtn = ImageView(ctx)
    private val searchBtn = ImageView(ctx)
    private val themeTv = TextView(ctx)
    private val modeTv = TextView(ctx)
    private val extras: List<View> = listOf(lyricBox, bar, controls)

    /** Command HUD banner — shown briefly when a voice command fires. */
    private val hudTv = TextView(ctx)

    private val expandEase = PathInterpolator(0.34f, 1.3f, 0.64f, 1f)   // gentle spring overshoot
    private val collapseEase = PathInterpolator(0.4f, 0f, 0.2f, 1f)

    private val lp = WindowManager.LayoutParams(
        0,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = dp(6)
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!attached) return
            render()
            h.postDelayed(this, if (progress > 0f) 50L else 250L)
        }
    }
    private val collapse = Runnable { setExpanded(false) }
    private val idleHide = Runnable { if (!tracker.playing) detach() }

    init {
        buildViews()
        applyTheme()
        applyState(0f)
        VoiceBus.island = { msg -> flash(msg) }
        VoiceBus.commandHud = { label -> showCommandHud(label) }
    }

    private fun collapsedW() = min(dp(240), screenW() - dp(48))
    private fun expandedW() = min(dp(420), screenW() - dp(24))

    private fun buildViews() {
        root.orientation = LinearLayout.VERTICAL
        root.setOnClickListener { setExpanded(!expanded) }
        root.setOnLongClickListener { openSearch(true); true }   // long-press pill = speak

        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        art.setBackgroundColor(Color.parseColor("#33FFFFFF"))
        art.clipToOutline = true
        art.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) {
                o.setRoundRect(0, 0, v.width, v.height, dp(8).toFloat())
            }
        }
        header.addView(art, LinearLayout.LayoutParams(dp(28), dp(28)))

        val titleBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        mainTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        mainTv.maxLines = 1
        mainTv.ellipsize = android.text.TextUtils.TruncateAt.END
        mainTv.setTypeface(mainTv.typeface, Typeface.BOLD)
        mainTv.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        subTv.maxLines = 1
        subTv.ellipsize = android.text.TextUtils.TruncateAt.END
        titleBox.addView(mainTv)
        titleBox.addView(subTv)
        header.addView(titleBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(10)
            marginEnd = dp(10)
        })
        header.addView(eq, LinearLayout.LayoutParams(dp(18), dp(14)))
        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // lyrics: fixed height so the island never jumps when a line wraps
        lyricBox.orientation = LinearLayout.VERTICAL
        lyricBox.gravity = Gravity.CENTER
        lyricBox.setOnClickListener {
            val t = tracker.track
            if (t != null && lines.isEmpty() && lyricsLoaded) { loadLyrics(t, true); touch() }
            else setExpanded(false)
        }
        curTv.setTypeface(curTv.typeface, Typeface.BOLD)
        curTv.gravity = Gravity.CENTER
        curTv.maxLines = 2
        curTv.ellipsize = android.text.TextUtils.TruncateAt.END
        curTv.setAutoSizeTextTypeUniformWithConfiguration(13, 20, 1, TypedValue.COMPLEX_UNIT_SP)
        curTv.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        nextTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        nextTv.gravity = Gravity.CENTER
        nextTv.maxLines = 1
        nextTv.ellipsize = android.text.TextUtils.TruncateAt.END
        nextTv.alpha = 0.5f
        nextTv.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        lyricBox.addView(curTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(56)))
        lyricBox.addView(nextTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(22)).apply { topMargin = dp(4) })
        root.addView(lyricBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(86)).apply { topMargin = dp(8) })

        root.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3)).apply { topMargin = dp(6) })

        // controls: [EN/ORIG chip][prev][play][next][search][theme chip]
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER_VERTICAL
        modeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        modeTv.setTypeface(modeTv.typeface, Typeface.BOLD)
        modeTv.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        modeTv.setPadding(dp(4), dp(12), dp(8), dp(12))
        modeTv.setOnClickListener { toggleMode(); touch() }
        modeTv.setOnLongClickListener { cycleSongLang(); touch(); true }
        controls.addView(modeTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        prevBtn.setImageResource(android.R.drawable.ic_media_previous)
        playBtn.setImageResource(android.R.drawable.ic_media_pause)
        nextBtn.setImageResource(android.R.drawable.ic_media_next)
        searchBtn.setImageResource(android.R.drawable.ic_menu_search)
        for (b in listOf(prevBtn, playBtn, nextBtn, searchBtn)) {
            b.setPadding(dp(10), dp(10), dp(10), dp(10))
            controls.addView(b, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        prevBtn.setOnClickListener { tracker.prev(); touch() }
        playBtn.setOnClickListener { tracker.toggle(); touch() }
        nextBtn.setOnClickListener { tracker.next(); touch() }
        searchBtn.setOnClickListener { openSearch(false) }
        searchBtn.setOnLongClickListener { openSearch(true); true }
        themeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        themeTv.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        themeTv.alpha = 0.7f
        themeTv.setPadding(dp(8), dp(12), dp(4), dp(12))
        themeTv.setOnClickListener {
            theme = theme.next()
            Prefs.setTheme(ctx, theme)
            applyTheme()
            touch()
        }
        controls.addView(themeTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(controls, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // Command HUD — overlaid on top of the root, centred; hidden until a command fires
        hudTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        hudTv.setTypeface(hudTv.typeface, Typeface.BOLD)
        hudTv.setTextColor(Color.WHITE)
        hudTv.gravity = Gravity.CENTER
        hudTv.setPadding(dp(14), dp(6), dp(14), dp(6))
        hudTv.background = GradientDrawable().apply {
            setColor(Color.parseColor("#CC1A1A2E"))
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), Color.parseColor("#557C5CFF"))
        }
        hudTv.visibility = View.GONE
        hudTv.elevation = dp(8).toFloat()
        root.addView(hudTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(4) })
    }

    /** Tap = type a search; long-press = voice (say a song, or "pause", "volume 50"...). */
    private fun openSearch(voice: Boolean) {
        val i = Intent(ctx, SearchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(SearchActivity.EXTRA_VOICE, voice)
        try { ctx.startActivity(i) } catch (e: Exception) {}
        setExpanded(false)
    }

    fun reloadTheme() {
        theme = Prefs.theme(ctx)
        applyTheme()
        val m = Prefs.lyricMode(ctx)
        if (m != lyricMode) {
            lyricMode = m
            lastCur = "\u0000"
            ensureTranslation()
        }
    }

    private fun applyTheme() {
        val th = theme
        val acc = if (th.light) th.fg else accent
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(th.bg1, th.bg2)).apply {
            cornerRadius = dp(28).toFloat()
            val sc = if (th.accentStroke) acc else th.stroke
            if (sc != 0) setStroke(dp(1), sc)
        }
        root.background = bg
        for (t in listOf(mainTv, subTv, nextTv, themeTv)) t.setTextColor(th.fg)
        modeTv.setTextColor(acc)
        curTv.setTextColor(acc)
        for (b in listOf(prevBtn, playBtn, nextBtn, searchBtn)) b.setColorFilter(th.fg)
        eq.color = acc
        eq.invalidate()
        bar.setColors(acc, if (th.light) 0x22000000 else 0x33FFFFFF)
        themeTv.text = "\u25D0 " + th.name.lowercase()
    }

    // ---- smooth expand / collapse ----
    private fun setExpanded(v: Boolean) {
        if (v == expanded) return
        expanded = v
        animateProgress(if (v) 1f else 0f)
        touch()
        render()
    }

    private fun touch() {
        h.removeCallbacks(collapse)
        if (expanded) h.postDelayed(collapse, 7000)
    }

    /** Applies everything except the window size for a given progress. */
    private fun applyState(p: Float) {
        progress = p
        val ap = ((p - 0.3f) / 0.7f).coerceIn(0f, 1f)
        val vis = if (p > 0.01f) View.VISIBLE else View.GONE
        subTv.visibility = vis
        subTv.alpha = 0.6f * ap
        for (v in extras) {
            v.visibility = vis
            v.alpha = ap
            v.translationY = (1f - ap) * dp(10)
        }
        val size = lerp(dp(28), dp(52), p)
        val la = art.layoutParams as LinearLayout.LayoutParams
        if (la.width != size) {
            la.width = size
            la.height = size
            art.layoutParams = la
        }
        root.setPadding(
            lerp(dp(8), dp(16), p), lerp(dp(6), dp(14), p),
            lerp(dp(14), dp(16), p), lerp(dp(6), dp(8), p)
        )
    }

    private fun applyWindow(p: Float) {
        lp.width = min(lerp(collapsedW(), expandedW(), p), screenW())
        lp.height = max(dp(30), lerp(heightC, heightE, p))
        if (attached) {
            try { wm.updateViewLayout(root, lp) } catch (e: Exception) {}
        }
    }

    private fun measureHeight(widthPx: Int, p: Float): Int {
        applyState(p)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return root.measuredHeight
    }

    private fun animateProgress(to: Float) {
        anim?.cancel()
        val from = progress
        if (!attached) {
            applyState(to)
            lp.width = if (to > 0.5f) expandedW() else collapsedW()
            lp.height = WindowManager.LayoutParams.WRAP_CONTENT
            return
        }
        heightC = measureHeight(collapsedW(), 0f)
        heightE = measureHeight(expandedW(), 1f)
        applyState(from)

        var cancelled = false
        val a = ValueAnimator.ofFloat(from, to)
        a.duration = if (to > from) 440L else 320L
        a.interpolator = if (to > from) expandEase else collapseEase
        a.addUpdateListener {
            val p = it.animatedValue as Float
            applyState(p)
            applyWindow(p)
        }
        a.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationCancel(animation: Animator) { cancelled = true }
            override fun onAnimationEnd(animation: Animator) {
                if (cancelled) return
                applyState(to)
                lp.width = if (to > 0.5f) expandedW() else collapsedW()
                lp.height = WindowManager.LayoutParams.WRAP_CONTENT
                if (attached) {
                    try { wm.updateViewLayout(root, lp) } catch (e: Exception) {}
                }
            }
        })
        anim = a
        a.start()
    }

    // ---- attach / detach with entrance + exit animation ----
    private fun attach() {
        if (attached || !Settings.canDrawOverlays(ctx)) return
        if (root.parent != null) {
            // still fading out from a previous detach: bring it back
            attached = true
            root.animate().cancel()
            root.alpha = 1f
            root.scaleX = 1f
            root.scaleY = 1f
            h.removeCallbacks(tick)
            h.post(tick)
            return
        }
        expanded = false
        anim?.cancel()
        applyState(0f)
        lp.width = collapsedW()
        lp.height = WindowManager.LayoutParams.WRAP_CONTENT
        try {
            wm.addView(root, lp)
            attached = true
            attachedAt = SystemClock.uptimeMillis()
            root.pivotX = collapsedW() / 2f
            root.pivotY = 0f
            root.alpha = 0f
            root.scaleX = 0.7f
            root.scaleY = 0.7f
            root.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(380).setInterpolator(OvershootInterpolator(1.1f)).start()
            h.removeCallbacks(tick)
            h.post(tick)
        } catch (e: Exception) {
            attached = false
        }
    }

    private fun detach() {
        h.removeCallbacks(tick)
        h.removeCallbacks(collapse)
        if (!attached) return
        attached = false
        anim?.cancel()
        root.pivotX = root.width / 2f
        root.pivotY = 0f
        root.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f)
            .setDuration(200).setInterpolator(AccelerateInterpolator())
            .withEndAction {
                if (!attached) {
                    try { wm.removeView(root) } catch (e: Exception) {}
                    root.alpha = 1f
                    root.scaleX = 1f
                    root.scaleY = 1f
                }
            }.start()
    }

    fun destroy() {
        VoiceBus.island = null
        VoiceBus.commandHud = null
        h.removeCallbacksAndMessages(null)
        attached = false
        anim?.cancel()
        try { wm.removeView(root) } catch (e: Exception) {}
    }

    /**
     * Flash an emoji+label command banner (e.g. "⏭ Next") on the island for 1.8 s.
     * Shown only when the island is attached; otherwise VoiceBus.showCommand() sends a Toast.
     */
    private fun showCommandHud(label: String) {
        if (!attached) return
        h.post {
            hudTv.text = label
            hudTv.visibility = View.VISIBLE
            hudTv.alpha = 0f
            hudTv.translationY = dp(-6).toFloat()
            hudTv.animate().cancel()
            hudTv.animate()
                .alpha(1f).translationY(0f)
                .setDuration(180)
                .setInterpolator(OvershootInterpolator(1.5f))
                .withEndAction {
                    h.postDelayed({
                        hudTv.animate()
                            .alpha(0f).translationY(dp(-4).toFloat())
                            .setDuration(250)
                            .withEndAction { hudTv.visibility = View.GONE }
                            .start()
                    }, 1600)
                }.start()
        }
    }

    private fun flash(msg: String): Boolean {
        if (!attached) return false
        flashMsg = msg
        flashUntil = SystemClock.uptimeMillis() + 2600
        render()
        return true
    }

    private fun bounce() {
        root.animate().scaleX(1.05f).scaleY(1.05f).setDuration(140).withEndAction {
            if (attached) {
                root.animate().scaleX(1f).scaleY(1f).setDuration(280)
                    .setInterpolator(OvershootInterpolator(2f)).start()
            }
        }.start()
    }

    /** Called whenever the media session changes. */
    fun update() {
        val t = tracker.track
        if (!enabled || t == null) {
            detach()
            return
        }
        h.removeCallbacks(idleHide)
        if (!tracker.playing) h.postDelayed(idleHide, 60_000)
        attach()

        val key = t.title + "|" + t.artist
        if (key != lyricKey) loadLyrics(t, false)
        render()
    }

    private fun loadLyrics(t: Track, force: Boolean) {
        val key = t.title + "|" + t.artist
        lyricKey = key
        lines = emptyList()
        lyricsLoaded = false
        lyricsSource = ""
        lastCur = "\u0000"
        trState = 0
        trSrc = ""
        Lyrics.load(ctx, t, force) { res ->
            if (key == lyricKey) {
                lines = res.lines
                lyricsSource = res.source
                lyricsLoaded = true
                ensureTranslation()
                render()
            }
        }
        render()
    }

    // ---- English / original lyric modes ----
    private fun songLang(): String = Prefs.songLang(ctx, lyricKey)

    private fun ensureTranslation() {
        if (lyricMode != "en" || lines.isEmpty() || trState != 0) return
        val key = lyricKey
        val snapshot = lines
        trState = 1
        Translator.translate(ctx, key, snapshot.map { it.text }, songLang()) { out ->
            if (key != lyricKey) return@translate
            if (out == null || out.en.size != snapshot.size) {
                trState = 3
            } else {
                lines = snapshot.mapIndexed { i, l -> l.copy(en = out.en[i]) }
                trSrc = out.src
                trState = 2
            }
            lastCur = "\u0000"
            render()
        }
    }

    private fun toggleMode() {
        lyricMode = if (lyricMode == "en") "orig" else "en"
        Prefs.setLyricMode(ctx, lyricMode)
        lastCur = "\u0000"
        ensureTranslation()
        render()
    }

    /** Long-press the chip: tell Lizmo which language this song is in (for romanized or mis-detected songs). */
    private fun cycleSongLang() {
        val choices = Translator.sourceChoices
        val next = choices[(choices.indexOf(songLang()) + 1) % choices.size]
        Prefs.setSongLang(ctx, lyricKey, next)
        VoiceBus.island?.invoke("Song language: " + Translator.langName(next))
        if (lyricMode != "en") { lyricMode = "en"; Prefs.setLyricMode(ctx, "en") }
        trState = 0
        lines = lines.map { it.copy(en = "") }
        lastCur = "\u0000"
        ensureTranslation()
        render()
    }

    private fun lineText(l: Line?): String =
        if (l == null) "" else if (lyricMode == "en" && trState == 2 && l.en.isNotBlank()) l.en else l.text

    // ---- rendering ----
    private fun setText(tv: TextView, s: String) {
        if (tv.text.toString() != s) tv.text = s
    }

    private fun render() {
        val t = tracker.track ?: return
        if (t.art !== lastArt) {
            lastArt = t.art
            art.setImageBitmap(t.art)
            accent = accentFrom(t.art)
            applyTheme()
            art.alpha = 0f
            art.animate().alpha(1f).setDuration(350).start()
            if (attached && progress < 0.5f && SystemClock.uptimeMillis() - attachedAt > 800) bounce()
        }
        val pos = tracker.positionMs()
        val idx = lines.indexOfLast { it.t <= pos + 150 }
        val cur = if (idx >= 0) lineText(lines[idx]) else ""
        val next = lineText(lines.getOrNull(idx + 1))

        val flashing = SystemClock.uptimeMillis() < flashUntil
        val pillText = if (cur.isNotBlank()) cur else t.title + " \u00B7 " + t.artist
        setText(mainTv, if (flashing) flashMsg else if (progress > 0.5f) t.title else pillText)

        val status = when {
            !lyricsLoaded -> "searching lyrics\u2026"
            lines.isEmpty() -> "no lyrics found"
            else -> lyricsSource
        }
        val tr = when {
            lyricMode != "en" || lines.isEmpty() -> ""
            trState == 1 -> " \u00B7 translating\u2026"
            trState == 3 -> " \u00B7 translation failed"
            trState == 2 && trSrc.isNotBlank() && trSrc != "en" -> " \u00B7 English from " + Translator.langName(trSrc)
            else -> ""
        }
        setText(subTv, t.artist + " \u00B7 " + status + tr)
        setText(modeTv, if (lyricMode == "en") "EN \u21C4 original" else "ORIGINAL \u21C4 en")
        setText(nextTv, next)

        val shown = when {
            cur.isNotBlank() -> cur
            lines.isEmpty() && lyricsLoaded -> "No lyrics found \u00B7 tap to retry"
            lines.isEmpty() -> "Searching lyrics\u2026"
            else -> "\u266A"
        }
        if (shown != lastCur) {
            lastCur = shown
            curTv.text = shown
            if (progress > 0.5f) {
                curTv.alpha = 0f
                curTv.translationY = dp(12).toFloat()
                curTv.animate().alpha(1f).translationY(0f).setDuration(260)
                    .setInterpolator(PathInterpolator(0.2f, 0.9f, 0.3f, 1f)).start()
            }
        }

        bar.fraction = if (t.durationMs > 0) (pos.toFloat() / t.durationMs).coerceIn(0f, 1f) else 0f

        if (lastPlaying != tracker.playing) {
            lastPlaying = tracker.playing
            playBtn.setImageResource(
                if (tracker.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            eq.active = tracker.playing
            eq.invalidate()
        }
    }

    private fun accentFrom(b: Bitmap?): Int {
        val fallback = Color.parseColor("#7C5CFF")
        if (b == null) return fallback
        return try {
            val s = Bitmap.createScaledBitmap(b.copy(Bitmap.Config.ARGB_8888, false), 8, 8, true)
            var r = 0
            var g = 0
            var bl = 0
            for (x in 0 until 8) for (y in 0 until 8) {
                val p = s.getPixel(x, y)
                r += Color.red(p); g += Color.green(p); bl += Color.blue(p)
            }
            Color.rgb(min(255, r / 64 + 60), min(255, g / 64 + 60), min(255, bl / 64 + 60))
        } catch (e: Exception) {
            fallback
        }
    }
}

/** Four animated equalizer bars. */
class EqView(ctx: Context) : View(ctx) {
    var color = Color.WHITE
    var active = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        p.color = color
        val n = 4
        val bw = width / (n * 2f - 1f)
        val t = SystemClock.uptimeMillis() / 1000.0
        for (i in 0 until n) {
            val f = if (active) 0.25f + 0.75f * ((sin(t * 5 + i * 1.7) + 1) / 2).toFloat() else 0.2f
            val hh = height * f
            val x = i * 2 * bw
            c.drawRoundRect(x, height - hh, x + bw, height.toFloat(), bw / 2, bw / 2, p)
        }
        if (active) postInvalidateOnAnimation()
    }
}

/** Thin rounded progress bar. */
class BarView(ctx: Context) : View(ctx) {
    var fraction = 0f
        set(v) {
            field = v
            invalidate()
        }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var fg = Color.WHITE
    private var bgc = 0x33FFFFFF

    fun setColors(f: Int, b: Int) {
        fg = f
        bgc = b
        invalidate()
    }

    override fun onDraw(c: Canvas) {
        val r = height / 2f
        p.color = bgc
        c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, p)
        p.color = fg
        c.drawRoundRect(0f, 0f, width * fraction, height.toFloat(), r, r, p)
    }
}
