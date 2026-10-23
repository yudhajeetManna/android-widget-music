package studio.neodev.smartisland

import android.app.Activity
import android.content.pm.PackageManager
import android.view.View
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var s1: TextView
    private lateinit var s2: TextView
    private lateinit var notchBtn: Button
    private lateinit var s3: TextView
    private lateinit var micPermBtn: Button
    private lateinit var sensBtn: Button

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(44), dp(24), dp(36))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0B0F"))
            addView(col)
        }

        fun label(s: String, size: Float, bold: Boolean = false, a: Float = 1f): TextView =
            TextView(this).apply {
                this.text = s
                setTextColor(Color.WHITE)
                textSize = size
                alpha = a
                if (bold) setTypeface(typeface, Typeface.BOLD)
            }

        fun btn(s: String, onClick: () -> Unit): Button =
            Button(this).apply {
                this.text = s
                isAllCaps = false
                setOnClickListener { onClick() }
            }

        col.addView(label("Lizmo", 28f, true))
        col.addView(label("Dynamic Music Island  \u00B7  v0.2.0", 13f, a = 0.6f))

        // Permissions Status
        s1 = label("", 15f).also { it.setPadding(0, dp(24), 0, dp(4)) }
        col.addView(s1)
        col.addView(btn("Grant notification access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })

        s2 = label("", 15f).also { it.setPadding(0, dp(16), 0, dp(4)) }
        col.addView(s2)
        col.addView(btn("Grant overlay permission") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        // Microphone Permission Status
        s3 = label("", 15f).also { it.setPadding(0, dp(16), 0, dp(4)) }
        col.addView(s3)
        micPermBtn = btn("Grant microphone permission") {
            requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 201)
        }
        col.addView(micPermBtn)

        // Preview Button
        col.addView(label("Quick Test", 14f, bold = true).also { it.setPadding(0, dp(24), 0, dp(4)) })
        col.addView(btn("\u25B6 Preview Island (8s demo)") {
            if (Settings.canDrawOverlays(this)) {
                MediaListener.instance?.showTestIsland()
            } else {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        })

        // Toggles
        col.addView(label("Customization", 14f, bold = true).also { it.setPadding(0, dp(24), 0, dp(4)) })

        val sw = Switch(this).apply {
            this.text = "Show dynamic island"
            setTextColor(Color.WHITE)
            isChecked = Prefs.enabled(this@MainActivity)
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, on ->
                Prefs.setEnabled(this@MainActivity, on)
                MediaListener.instance?.applyPrefs()
            }
        }
        col.addView(sw)

        val swHaptic = Switch(this).apply {
            this.text = "Haptic feedback on controls"
            setTextColor(Color.WHITE)
            isChecked = Prefs.hapticEnabled(this@MainActivity)
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, on ->
                Prefs.setHapticEnabled(this@MainActivity, on)
                MediaListener.instance?.applyPrefs()
            }
        }
        col.addView(swHaptic)

        val swGestures = Switch(this).apply {
            this.text = "Swipe island to skip songs"
            setTextColor(Color.WHITE)
            isChecked = Prefs.gesturesEnabled(this@MainActivity)
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, on ->
                Prefs.setGesturesEnabled(this@MainActivity, on)
                MediaListener.instance?.applyPrefs()
            }
        }
        col.addView(swGestures)

        val swVoice = Switch(this).apply {
            this.text = "Voice control in island"
            setTextColor(Color.WHITE)
            isChecked = Prefs.voiceEnabled(this@MainActivity)
            setPadding(0, dp(8), 0, dp(8))
            setOnCheckedChangeListener { _, on ->
                Prefs.setVoiceEnabled(this@MainActivity, on)
                MediaListener.instance?.applyPrefs()
            }
        }
        col.addView(swVoice)

        sensBtn = btn("Mic Sensitivity: " + Prefs.voiceSensitivity(this).label + " (" + Prefs.voiceSensitivity(this).desc + ")") {
            val nextSens = Prefs.voiceSensitivity(this).next()
            Prefs.setVoiceSensitivity(this, nextSens)
            sensBtn.text = "Mic Sensitivity: " + nextSens.label + " (" + nextSens.desc + ")"
            MediaListener.instance?.applyPrefs()
        }
        col.addView(sensBtn)

        // Notch position offset selector
        val offsets = listOf(6, 12, 18, 24)
        val offsetLabels = mapOf(
            6 to "Top Margin: 6 dp (Flush / Top)",
            12 to "Top Margin: 12 dp (Punch Hole)",
            18 to "Top Margin: 18 dp (Camera Cutout)",
            24 to "Top Margin: 24 dp (Deep Notch)"
        )
        notchBtn = btn("") {}
        fun refreshNotchBtn() {
            val cur = Prefs.yOffsetDp(this)
            notchBtn.text = offsetLabels[cur] ?: "Top Margin: $cur dp"
        }
        notchBtn.setOnClickListener {
            val cur = Prefs.yOffsetDp(this)
            val idx = offsets.indexOf(cur)
            val next = offsets[(if (idx >= 0) idx + 1 else 0) % offsets.size]
            Prefs.setYOffsetDp(this, next)
            refreshNotchBtn()
            MediaListener.instance?.applyPrefs()
        }
        refreshNotchBtn()
        col.addView(notchBtn)

        // Theme cycle button
        val themeBtn = btn("") {}
        fun refreshThemeBtn() { themeBtn.text = "Theme: " + Prefs.theme(this).name.lowercase() + " (tap to change)" }
        themeBtn.setOnClickListener {
            Prefs.setTheme(this, Prefs.theme(this).next())
            refreshThemeBtn()
            MediaListener.instance?.applyPrefs()
        }
        refreshThemeBtn()
        col.addView(themeBtn)

        col.addView(btn("Battery optimization settings") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })

        col.addView(label(
            "\u2022 Tap the island to expand into mini-player.\n" +
            "\u2022 Scrub the progress bar to seek through songs.\n" +
            "\u2022 Swipe left/right on the island to change tracks.\n" +
            "\u2022 Tap album art to open current music app.\n\n" +
            "If notification access is greyed out after install:\n" +
            "Settings > Apps > Lizmo > \u22EE menu > Allow restricted settings, then enable.",
            13f, a = 0.55f
        ).also { it.setPadding(0, dp(24), 0, 0) })

        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        val n = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
        val o = Settings.canDrawOverlays(this)
        s1.text = "1. Notification access: " + if (n) "ON \u2713" else "OFF"
        s2.text = "2. Overlay permission: " + if (o) "ON \u2713" else "OFF"
        MediaListener.instance?.applyPrefs()
    }
}
