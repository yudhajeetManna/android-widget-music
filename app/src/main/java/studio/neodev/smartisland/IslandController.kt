package studio.neodev.smartisland

import android.animation.ValueAnimator
import android.annotation.SuppressLint
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
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
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
    private var isPreview = false
    private var lines: List<Line> = emptyList()
    private var lyricsLoaded = false
    private var lyricKey = ""
    private var lastCur = "\u0000"
    private var lastArt: Bitmap? = null
    private var lastPlaying: Boolean? = null
    private var anim: ValueAnimator? = null

    // Voice recognition & sensitivity state
    private var voiceListening = false
    private var voiceMsg = ""

    private val voiceManager by lazy {
        VoiceManager(
            ctx = ctx,
            onResult = { command, peakRms ->
                h.post { handleVoiceCommand(command, peakRms) }
            },
            onRms = { rmsdB ->
                h.post {
                    val level = ((rmsdB + 2f) / 14f).coerceIn(0f, 1f)
                    voiceMeter.fraction = level
                }
            },
            onStateChange = { listening, message ->
                h.post {
                    voiceListening = listening
                    voiceMsg = message
                    voiceMeter.visibility = if (listening) View.VISIBLE else View.GONE
                    micBtn.setColorFilter(if (listening) Color.parseColor("#FF0055") else theme.fg)
                    if (listening) {
                        mainTv.text = "🎙️ $message"
                    } else {
                        render()
                    }
                }
            }
        )
    }

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
    private val voiceMeter = BarView(ctx)
    private val timeBox = LinearLayout(ctx)
    private val timeCurTv = TextView(ctx)
    private val timeTotalTv = TextView(ctx)
    private val controls = LinearLayout(ctx)
    private val prevBtn = ImageView(ctx)
    private val playBtn = ImageView(ctx)
    private val nextBtn = ImageView(ctx)
    private val micBtn = ImageView(ctx)
    private val sensTv = TextView(ctx)
    private val themeTv = TextView(ctx)

    private val lp = WindowManager.LayoutParams(
        0,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = dp(Prefs.yOffsetDp(ctx))
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!attached) return
            render()
            h.postDelayed(this, 250)
        }
    }
    private val collapse = Runnable { setExpanded(false) }
    private val idleHide = Runnable { if (!tracker.playing && !isPreview) detach() }

    private val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            vibrate()
            setExpanded(!expanded)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            vibrate()
            tracker.toggle()
            touch()
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (!Prefs.gesturesEnabled(ctx) || e1 == null) return false
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (abs(dx) > abs(dy) && abs(dx) > dp(36)) {
                vibrate()
                if (dx > 0) tracker.next() else tracker.prev()
                touch()
                return true
            }
            return false
        }

        override fun onLongPress(e: MotionEvent) {
            vibrate()
            tracker.openApp(ctx)
            touch()
        }
    })

    init {
        buildViews()
        applyTheme()
        applyExpanded(false)
    }

    private fun collapsedW() = min(dp(240), screenW() - dp(48))
    private fun expandedW() = min(dp(420), screenW() - dp(24))

    @SuppressLint("ClickableViewAccessibility")
    private fun buildViews() {
        root.orientation = LinearLayout.VERTICAL
        root.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }

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
        art.setOnClickListener {
            vibrate()
            tracker.openApp(ctx)
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

        // scrubbable progress bar
        bar.onScrub = { frac ->
            val d = tracker.track?.durationMs ?: 0L
            if (d > 0) tracker.seekTo((frac * d).toLong())
            touch()
        }
        root.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(14)).apply { topMargin = dp(4) })

        // voice VU meter
        voiceMeter.visibility = View.GONE
        root.addView(voiceMeter, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(4)).apply { topMargin = dp(2) })

        // time indicator (elapsed / total)
        timeBox.orientation = LinearLayout.HORIZONTAL
        timeBox.gravity = Gravity.CENTER_VERTICAL
        timeCurTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        timeCurTv.alpha = 0.6f
        timeTotalTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        timeTotalTv.alpha = 0.6f
        timeTotalTv.gravity = Gravity.END
        timeBox.addView(timeCurTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        timeBox.addView(timeTotalTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        timeBox.setPadding(dp(2), 0, dp(2), 0)
        root.addView(timeBox, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        // controls: [spacer][prev][play][next][theme chip]
        controls.orientation = LinearLayout.HORIZONTAL
        controls.gravity = Gravity.CENTER_VERTICAL
        controls.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        prevBtn.setImageResource(android.R.drawable.ic_media_previous)
        playBtn.setImageResource(android.R.drawable.ic_media_pause)
        nextBtn.setImageResource(android.R.drawable.ic_media_next)
        micBtn.setImageResource(android.R.drawable.ic_btn_speak_now)
        for (b in listOf(prevBtn, playBtn, nextBtn, micBtn)) {
            b.setPadding(dp(10), dp(10), dp(10), dp(10))
            controls.addView(b, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        prevBtn.setOnClickListener { vibrate(); tracker.prev(); touch() }
        playBtn.setOnClickListener { vibrate(); tracker.toggle(); touch() }
        nextBtn.setOnClickListener { vibrate(); tracker.next(); touch() }
        micBtn.setOnClickListener { toggleVoice() }

        sensTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        sensTv.gravity = Gravity.CENTER_VERTICAL
        sensTv.alpha = 0.8f
        sensTv.setPadding(dp(6), dp(10), dp(6), dp(10))
        sensTv.setOnClickListener {
            vibrate()
            val nextSens = Prefs.voiceSensitivity(ctx).next()
            Prefs.setVoiceSensitivity(ctx, nextSens)
            updateSensitivityLabel()
            touch()
        }
        updateSensitivityLabel()
        controls.addView(sensTv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))

        themeTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        themeTv.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        themeTv.alpha = 0.7f
        themeTv.setPadding(dp(8), dp(12), dp(4), dp(12))
        themeTv.setOnClickListener {
            vibrate()
            theme = theme.next()
            Prefs.setTheme(ctx, theme)
            applyTheme()
            touch()
        }
        controls.addView(themeTv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(controls, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun updateSensitivityLabel() {
        val s = Prefs.voiceSensitivity(ctx)
        sensTv.text = "🎙️ " + s.label.uppercase()
    }

    private fun toggleVoice() {
        vibrate()
        if (voiceListening) {
            voiceManager.stop()
        } else {
            touch()
            voiceManager.start()
        }
    }

    private fun handleVoiceCommand(raw: String, peakRms: Float) {
        val sensitivity = Prefs.voiceSensitivity(ctx)
        if (peakRms < sensitivity.minRmsDb) {
            voiceMsg = "Too quiet for ${sensitivity.label} sensitivity"
            mainTv.text = "🎙️ $voiceMsg"
            h.postDelayed({ render() }, 3000)
            return
        }

        val text = raw.trim().lowercase()
        vibrate()

        when {
            text.contains("next") || text.contains("skip") || text.contains("forward") -> {
                voiceMsg = "⏭ Next track"
                tracker.next()
            }
            text.contains("prev") || text.contains("back") || text.contains("last") -> {
                voiceMsg = "⏮ Previous track"
                tracker.prev()
            }
            text.contains("pause") || text.contains("stop") -> {
                voiceMsg = "⏸ Paused"
                if (tracker.playing) tracker.toggle()
            }
            text.contains("play") || text.contains("resume") || text.contains("start") -> {
                voiceMsg = "▶ Resumed"
                if (!tracker.playing) tracker.toggle()
            }
            text.contains("toggle") -> {
                voiceMsg = "⏯ Toggled"
                tracker.toggle()
            }
            text.contains("theme") -> {
                theme = theme.next()
                Prefs.setTheme(ctx, theme)
                applyTheme()
                voiceMsg = "🎨 Theme: ${theme.name.lowercase()}"
            }
            text.contains("close") || text.contains("collapse") || text.contains("hide") -> {
                voiceMsg = "Collapsed"
                setExpanded(false)
            }
            text.contains("open") || text.contains("expand") || text.contains("show") -> {
                voiceMsg = "Expanded"
                setExpanded(true)
            }
            else -> {
                voiceMsg = "Heard: \"$raw\""
            }
        }
        mainTv.text = "🎙️ $voiceMsg"
        touch()
        h.postDelayed({ render() }, 3000)
    }

    private fun vibrate() {
        if (Prefs.hapticEnabled(ctx)) {
            try { root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } catch (e: Exception) {}
        }
    }

    fun applyYOffset() {
        lp.y = dp(Prefs.yOffsetDp(ctx))
        if (attached) {
            try { wm.updateViewLayout(root, lp) } catch (e: Exception) {}
        }
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
        for (t in listOf(mainTv, subTv, nextTv, themeTv, timeCurTv, timeTotalTv, sensTv)) t.setTextColor(th.fg)
        curTv.setTextColor(acc)
        for (b in listOf(prevBtn, playBtn, nextBtn, micBtn)) b.setColorFilter(th.fg)
        eq.color = acc
        eq.invalidate()
        bar.setColors(acc, if (th.light) 0x22000000 else 0x33FFFFFF)
        voiceMeter.setColors(Color.parseColor("#FF0055"), 0x22FFFFFF)
        themeTv.text = "\u25D0 " + th.name.lowercase()
        applyYOffset()
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
        timeBox.visibility = vis
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
        isPreview = false
    }

    fun destroy() {
        voiceManager.destroy()
        detach()
        h.removeCallbacksAndMessages(null)
    }

    fun showPreview() {
        isPreview = true
        val demoArt = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.parseColor("#7C5CFF"))
        }
        val demo = Track("Blinding Lights (Preview)", "The Weeknd", "After Hours", 200_000, demoArt)
        lines = listOf(
            Line(0, "I've been on my own for long enough"),
            Line(4000, "Maybe you can show me how to love, maybe"),
            Line(8000, "I'm going through withdrawals"),
            Line(12000, "You don't even have to do too much")
        )
        lyricsLoaded = true
        attach()
        renderTrack(demo, 4500, true)
        setExpanded(true)
        vibrate()
        h.removeCallbacks(idleHide)
        h.postDelayed({ if (!tracker.playing) detach() }, 8000)
    }

    /** Called whenever the media session changes. */
    fun update() {
        if (isPreview) return
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

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun render() {
        if (voiceListening) return
        val t = tracker.track ?: return
        renderTrack(t, tracker.positionMs(), tracker.playing)
    }

    private fun renderTrack(t: Track, pos: Long, isPlaying: Boolean) {
        if (t.art !== lastArt) {
            lastArt = t.art
            art.setImageBitmap(t.art)
            accent = accentFrom(t.art)
            applyTheme()
        }
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
        timeCurTv.text = formatTime(pos)
        timeTotalTv.text = if (t.durationMs > 0) formatTime(t.durationMs) else "--:--"

        if (lastPlaying != isPlaying) {
            lastPlaying = isPlaying
            playBtn.setImageResource(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            )
            eq.active = isPlaying
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

/** Interactive scrubbable progress bar. */
class BarView(ctx: Context) : View(ctx) {
    var fraction = 0f
        set(v) {
            if (!isDragging) {
                field = v.coerceIn(0f, 1f)
                invalidate()
            }
        }
    var onScrub: ((Float) -> Unit)? = null
    private var isDragging = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var fg = Color.WHITE
    private var bgc = 0x33FFFFFF
    private val barHeight = 4f * resources.displayMetrics.density

    fun setColors(f: Int, b: Int) {
        fg = f
        bgc = b
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (onScrub == null) return super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                fraction = (event.x / width.toFloat()).coerceIn(0f, 1f)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    fraction = (event.x / width.toFloat()).coerceIn(0f, 1f)
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    fraction = (event.x / width.toFloat()).coerceIn(0f, 1f)
                    onScrub?.invoke(fraction)
                    invalidate()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(c: Canvas) {
        val top = (height - barHeight) / 2f
        val bottom = top + barHeight
        val r = barHeight / 2f
        p.color = bgc
        c.drawRoundRect(0f, top, width.toFloat(), bottom, r, r, p)
        p.color = fg
        c.drawRoundRect(0f, top, width * fraction, bottom, r, r, p)
        if (isDragging) {
            p.color = fg
            c.drawCircle(width * fraction, height / 2f, r * 2.2f, p)
        }
    }
}
