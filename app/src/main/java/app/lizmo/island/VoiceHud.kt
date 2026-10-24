package app.lizmo.island

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.TextView

/**
 * Floating pill HUD for voice feedback when no music overlay is showing
 * (e.g. "🎤 Listening...", "⏸ Paused", "🔊 Volume 60%").
 */
object VoiceHud {
    private val h = Handler(Looper.getMainLooper())
    private var view: TextView? = null
    private var wm: WindowManager? = null
    private val hideRun = Runnable { dismiss() }

    fun show(ctx: Context, msg: String, ms: Long = 2500): Boolean {
        val app = ctx.applicationContext
        if (!Settings.canDrawOverlays(app)) return false
        val d = app.resources.displayMetrics.density
        var v = view
        if (v == null) {
            val w = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val tv = TextView(app).apply {
                setTextColor(Color.WHITE)
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding((18 * d).toInt(), (10 * d).toInt(), (18 * d).toInt(), (10 * d).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#F2101014"))
                    cornerRadius = 26 * d
                    setStroke((1 * d).toInt(), Color.parseColor("#33FFFFFF"))
                }
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (16 * d).toInt()
            }
            try {
                w.addView(tv, lp)
            } catch (e: Exception) {
                return false
            }
            wm = w
            view = tv
            v = tv
            tv.alpha = 0f
            tv.scaleX = 0.8f
            tv.scaleY = 0.8f
            tv.post {
                tv.pivotX = tv.width / 2f
                tv.pivotY = 0f
            }
            tv.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280)
                .setInterpolator(OvershootInterpolator(1.2f)).start()
        }
        if (v.text.toString() != msg) v.text = msg
        h.removeCallbacks(hideRun)
        h.postDelayed(hideRun, ms)
        return true
    }

    fun dismiss() {
        h.removeCallbacks(hideRun)
        val v = view ?: return
        view = null
        v.animate().alpha(0f).scaleX(0.85f).scaleY(0.85f).setDuration(200).withEndAction {
            try { wm?.removeView(v) } catch (e: Exception) {}
        }.start()
    }
}
