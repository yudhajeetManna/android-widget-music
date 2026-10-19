package studio.neodev.smartisland

import android.app.Activity
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

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(24))
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
        col.addView(label("Music island with synced lyrics  \u00B7  by NEODEV STUDIO", 13f, a = 0.6f))

        s1 = label("", 15f).also { it.setPadding(0, dp(28), 0, dp(4)) }
        col.addView(s1)
        col.addView(btn("Grant notification access") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })

        s2 = label("", 15f).also { it.setPadding(0, dp(20), 0, dp(4)) }
        col.addView(s2)
        col.addView(btn("Grant overlay permission") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        val sw = Switch(this).apply {
            this.text = "Show island"
            setTextColor(Color.WHITE)
            isChecked = Prefs.enabled(this@MainActivity)
            setPadding(0, dp(24), 0, dp(8))
            setOnCheckedChangeListener { _, on ->
                Prefs.setEnabled(this@MainActivity, on)
                MediaListener.instance?.applyPrefs()
            }
        }
        col.addView(sw)

        val themeBtn = btn("") {}
        fun refreshThemeBtn() { themeBtn.text = "Theme: " + Prefs.theme(this).name.lowercase() + " (tap to change)" }
        themeBtn.setOnClickListener {
            Prefs.setTheme(this, Prefs.theme(this).next())
            refreshThemeBtn()
            MediaListener.instance?.applyPrefs()
        }
        refreshThemeBtn()
        col.addView(themeBtn)

        col.addView(btn("Battery settings (let it run in background)") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })

        col.addView(label(
            "Play music in any app. Tap the island to expand it; tap again to collapse.\n\n" +
                "If notification access is greyed out after sideloading: Settings > Apps > Lizmo > " +
                "\u22EE menu > Allow restricted settings, then try again.",
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
