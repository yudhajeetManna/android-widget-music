package studio.neodev.smartisland

import android.animation.ValueAnimator
import android.content.Context
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min
import kotlin.math.sin

class IslandController(private val ctx: Context, private val tracker: MediaTracker) {

    var enabled = true

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val h = Handler(Looper.getMainLooper())
    private val density = ctx.resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * density).toInt()
    private fun screenW(): Int = ctx.resources.displayMetrics.widthPixels

    private var theme = Prefs.theme(ctx)
    private var accent = Color.parseColor("#7C5CFF")
    private var expanded = false
    private var attached = false
    private var lines: List<Line> = emptyList()
    private var lyricsLoaded = false
    private var lyricKey = ""
    private var lastCur = "\u0000"
    private var lastArt: Bitmap? = null
    private var lastPlaying: Boolean? = null
    private var anim: ValueAnimator? = null

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
    private val themeTv = TextView(ctx)

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
            h.postDelayed(this, 250)
        }
    }
    private val collapse = Runnable { setExpanded(false) }
    private val idleHide = Runnable { if (!tracker.playing) detach() }

    init {
        buildViews()
        applyTheme()
        applyExpanded(false)
    }

    private fun collapsedW() = min(dp(240), screenW() - dp(48))
    private fun expandedW() = min(dp(420), screenW() - dp(24))

    private fun buildViews() {
        root.orientation = LinearLayout.VERTICAL
        root.setOnClickListener { setExpanded(!expanded) }

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
        subTv.alpha = 0.6f
        subTv.ellipsize = android.text.TextUtils.TruncateAt.END
        titleBox.addView(mainTv)
        titleBox.addView(subTv)
        header.addView(titleBox, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(10)
            marginEnd = dp(10)
        })
        header.addView(eq, LinearLayout.LayoutParams(dp(18), dp(14)))
        root.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // lyrics
        lyricBox.orientation = LinearLayout.VERTICAL
        lyricBox.gravity = Gravity.CENTER
        lyricBox.minimumHeight = dp(76)
        curTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        curTv.setTypeface(curTv.typeface, Typeface.BOLD)
        curTv.gravity = Gravity.CENTER_HORIZONTAL
        curTv.maxLines = 3
        curTv.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        nextTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        nextTv.gravity = Gravity.CENTER_HORIZONTAL
        nextTv.maxLines = 2
        nextTv.alpha = 0.5f
        nextTv.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        lyricBox.addView(curTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        lyricBox.addView(nextTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        root.addView(lyricBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        root.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(3)).apply { topMargin = dp(6) })

        // controls: [spacer][prev][play][next][theme chip]
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER_VERTICAL
        controls.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        prevBtn.setImageResource(android.R.drawable.ic_media_previous)
        playBtn.setImageResource(android.R.drawable.ic_media_pause)
        nextBtn.setImageResource(android.R.drawable.ic_media_next)
        for (b in listOf(prevBtn, playBtn, nextBtn)) {
            b.setPadding(dp(10), dp(10), dp(10), dp(10))
            controls.addView(b, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        prevBtn.setOnClickListener { tracker.prev(); touch() }
        playBtn.setOnClickListener { tracker.toggle(); touch() }
        nextBtn.setOnClickListener { tracker.next(); touch() }
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
    }

    fun reloadTheme() {
        theme = Prefs.theme(ctx)
        applyTheme()
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
        curTv.setTextColor(acc)
        for (b in listOf(prevBtn, playBtn, nextBtn)) b.setColorFilter(th.fg)
        eq.color = acc
        eq.invalidate()
        bar.setColors(acc, if (th.light) 0x22000000 else 0x33FFFFFF)
        themeTv.text = "\u25D0 " + th.name.lowercase()
    }

    // ---- expand / collapse ----
    private fun setExpanded(v: Boolean) {
        if (v == expanded) return
        expanded = v
        applyExpanded(true)
        touch()
        render()
    }

    private fun touch() {
        h.removeCallbacks(collapse)
        if (expanded) h.postDelayed(collapse, 7000)
    }

    private fun applyExpanded(animate: Boolean) {
        val vis = if (expanded) View.VISIBLE else View.GONE
        subTv.visibility = vis
        lyricBox.visibility = vis
        bar.visibility = vis
        controls.visibility = vis
        val size = dp(if (expanded) 52 else 28)
        (art.layoutParams as LinearLayout.LayoutParams).apply { width = size; height = size }
        art.requestLayout()
        if (expanded) root.setPadding(dp(16), dp(14), dp(16), dp(8))
        else root.setPadding(dp(8), dp(6), dp(14), dp(6))

        val target = if (expanded) expandedW() else collapsedW()
        anim?.cancel()
        if (animate && attached) {
            val a = ValueAnimator.ofInt(lp.width, target).setDuration(260)
            a.addUpdateListener {
                lp.width = it.animatedValue as Int
                if (attached) wm.updateViewLayout(root, lp)
            }
            anim = a
            a.start()
        } else {
            lp.width = target
            if (attached) wm.updateViewLayout(root, lp)
        }
    }

    // ---- attach / detach ----
    private fun attach() {
        if (attached || !Settings.canDrawOverlays(ctx)) return
        expanded = false
        applyExpanded(false)
        try {
            wm.addView(root, lp)
            attached = true
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
        try { wm.removeView(root) } catch (e: Exception) {}
        attached = false
    }

    fun destroy() {
        detach()
        h.removeCallbacksAndMessages(null)
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
        if (key != lyricKey) {
            lyricKey = key
            lines = emptyList()
            lyricsLoaded = false
            lastCur = "\u0000"
            Lyrics.load(ctx, t) { ls ->
                if (key == lyricKey) {
                    lines = ls
                    lyricsLoaded = true
                    render()
                }
            }
        }
        render()
    }

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
        }
        val pos = tracker.positionMs()
        val idx = lines.indexOfLast { it.t <= pos + 150 }
        val cur = if (idx >= 0) lines[idx].text else ""
        val next = lines.getOrNull(idx + 1)?.text ?: ""

        setText(mainTv, if (expanded) t.title else if (cur.isNotBlank()) cur else t.title + " \u00B7 " + t.artist)
        setText(subTv, t.artist)
        setText(nextTv, next)

        val shown = when {
            cur.isNotBlank() -> cur
            lines.isEmpty() && lyricsLoaded -> "No synced lyrics"
            else -> "\u266A"
        }
        if (shown != lastCur) {
            lastCur = shown
            curTv.text = shown
            if (expanded) {
                curTv.alpha = 0f
                curTv.translationY = dp(10).toFloat()
                curTv.animate().alpha(1f).translationY(0f).setDuration(220).start()
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
