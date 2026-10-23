package app.lizmo.island

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var s1: TextView
    private lateinit var s2: TextView
    private lateinit var voiceSw: Switch
    private lateinit var voiceStatus: TextView
    private lateinit var vuBar: FrameLayout         // live VU meter fill
    private lateinit var vuContainer: FrameLayout   // VU meter outer shell
    private var testRec: SpeechRecognizer? = null
    private var syncing = false
    private val h = Handler(Looper.getMainLooper())

    private val langs = listOf("en-IN", "en-US", "en-GB")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    // ----------- VU meter -----------
    /** Update the live RMS bar. rmsdB is typically in the range –10 … +10 from Android. */
    private fun updateVu(rmsdB: Float) {
        runOnUiThread {
            // Normalise to 0–1: Android reports roughly –2 .. 10 dB for normal speech
            val pct = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            val color = when {
                pct < 0.4f -> Color.parseColor("#44FF44")   // green — quiet
                pct < 0.75f -> Color.parseColor("#FFD700")  // yellow — medium
                else -> Color.parseColor("#FF4444")         // red — loud / peak
            }
            val lp = FrameLayout.LayoutParams(
                (vuContainer.width * pct).toInt().coerceAtLeast(dp(2)),
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            lp.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            (vuBar.background as? GradientDrawable)?.setColor(color)
            vuBar.layoutParams = lp
        }
    }

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
        col.addView(label("Music island with synced lyrics", 13f, a = 0.6f))

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

        val modeBtn = btn("") {}
        fun refreshModeBtn() {
            modeBtn.text = "Lyrics: " + (if (Prefs.lyricMode(this) == "en") "English translation" else "Original language") + " (tap to switch)"
        }
        modeBtn.setOnClickListener {
            Prefs.setLyricMode(this, if (Prefs.lyricMode(this) == "en") "orig" else "en")
            refreshModeBtn()
            MediaListener.instance?.applyPrefs()
        }
        refreshModeBtn()
        col.addView(modeBtn)

        col.addView(btn("Search & play music") {
            startActivity(Intent(this, SearchActivity::class.java))
        })
        col.addView(btn("Voice: say a song or a command") {
            startActivity(Intent(this, SearchActivity::class.java).putExtra(SearchActivity.EXTRA_VOICE, true))
        })

        // ============================================================
        // SECTION: Wake word
        // ============================================================
        col.addView(label("Wake word", 18f, true).also { it.setPadding(0, dp(28), 0, dp(4)) })
        col.addView(label(
            "Say the wake word, then a command: \"lizmo pause\", \"lizmo volume 40\", \"lizmo play kesariya\". " +
                "Or say just the wake word, then the command.\n" +
                "Phones often mishear made-up names, so list every spelling separated by commas " +
                "(the test below shows what your phone hears).",
            12f, a = 0.6f
        ))
        val wakeInput = EditText(this).apply {
            setText(Prefs.wake(this@MainActivity))
            hint = "lizmo, lismo, limo"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.parseColor("#88FFFFFF"))
        }
        col.addView(wakeInput)
        col.addView(btn("Save wake word(s)") {
            val list = wakeInput.text.toString().split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (list.isEmpty() || list.any { it.length < 3 }) {
                Toast.makeText(this, "Each name needs at least 3 letters", Toast.LENGTH_SHORT).show()
            } else {
                Prefs.setWake(this, list.joinToString(", "))
                Toast.makeText(this, "Saved: " + list.joinToString(", "), Toast.LENGTH_SHORT).show()
                // Restart the service so it picks up the new wake word immediately
                if (Prefs.voiceOn(this) && hasMic()) {
                    VoiceService.stop(this)
                    h.postDelayed({ VoiceService.start(this) }, 300)
                }
            }
        })

        val langBtn = btn("") {}
        fun refreshLangBtn() { langBtn.text = "Voice language: " + Prefs.voiceLang(this) + " (tap to change)" }
        langBtn.setOnClickListener {
            val next = langs[(langs.indexOf(Prefs.voiceLang(this)) + 1) % langs.size]
            Prefs.setVoiceLang(this, next)
            refreshLangBtn()
        }
        refreshLangBtn()
        col.addView(langBtn)

        // ============================================================
        // SECTION: Microphone Sensitivity picker
        // ============================================================
        col.addView(label("Microphone Sensitivity", 18f, true).also { it.setPadding(0, dp(28), 0, dp(4)) })
        col.addView(label(
            "Controls how loud you need to speak before Lizmo reacts.\n" +
                "• Low — whisper-friendly; may pick up background noise.\n" +
                "• Medium — recommended; works at normal conversation distance.\n" +
                "• High — only reacts to clear, loud speech; ideal in noisy rooms.",
            12f, a = 0.6f
        ))

        val sensBtn = btn("") {}
        fun refreshSensBtn() {
            val s = Prefs.micSensitivity(this)
            sensBtn.text = "Sensitivity: ${s.label}  ← tap to change"
        }
        sensBtn.setOnClickListener {
            val next = Prefs.micSensitivity(this).next()
            Prefs.setMicSensitivity(this, next)
            refreshSensBtn()
            Toast.makeText(this, "Sensitivity: ${next.label}", Toast.LENGTH_SHORT).show()
        }
        refreshSensBtn()
        col.addView(sensBtn)

        // ============================================================
        // SECTION: Live VU meter + microphone test
        // ============================================================
        col.addView(label("Microphone level", 14f, bold = true).also { it.setPadding(0, dp(20), 0, dp(4)) })
        col.addView(label("Shows the live audio level while the microphone is active.", 12f, a = 0.6f))

        // Build VU meter bar
        vuContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(20)).apply {
                setMargins(0, dp(8), 0, dp(4))
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1AFFFFFF"))
                cornerRadius = dp(10).toFloat()
            }
        }
        vuBar = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(4), FrameLayout.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#44FF44"))
                cornerRadius = dp(10).toFloat()
            }
        }
        vuContainer.addView(vuBar)
        col.addView(vuContainer)

        voiceStatus = label(VoiceBus.lastLog, 13f).also { it.setPadding(0, dp(12), 0, dp(4)) }
        col.addView(voiceStatus)
        col.addView(btn("Test microphone (say \"lizmo pause\")") { testMic() })

        // ============================================================
        // SECTION: Always-on wake word switch
        // ============================================================
        voiceSw = Switch(this).apply {
            this.text = "Always listen for wake word  (ON by default)"
            setTextColor(Color.WHITE)
            isChecked = Prefs.voiceOn(this@MainActivity)
            setPadding(0, dp(12), 0, dp(8))
            setOnCheckedChangeListener { _, on ->
                if (syncing) return@setOnCheckedChangeListener
                if (on) {
                    if (hasMic()) enableVoice() else askMic(7)
                } else {
                    Prefs.setVoiceOn(this@MainActivity, false)
                    VoiceService.stop(this@MainActivity)
                    VoiceBus.log("Voice control is off")
                }
            }
        }
        col.addView(voiceSw)
        col.addView(label(
            "Say your wake word (\"lizmo\") at any time — the microphone is always ready.\n" +
                "Change the wake word above to any name you prefer. The island pops up within ~1 second.",
            12f, a = 0.5f
        ))

        col.addView(btn("Battery settings (let it run in background)") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })

        // ============================================================
        // SECTION: Active music platform info
        // ============================================================
        col.addView(label("Active music platform", 18f, true).also { it.setPadding(0, dp(28), 0, dp(4)) })
        val platformTv = label("Not detected yet — start playing music first.", 13f, a = 0.7f)
        col.addView(platformTv)
        col.addView(btn("Detect active platform") {
            val pkg = Playback.activeMusicPackage(this)
            platformTv.text = if (pkg != null) {
                val name = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
                           catch (e: Exception) { pkg }
                "\u25CF Active: $name ($pkg)\nVoice song search will play directly in $name."
            } else {
                "No active media session — open a music app and start playing first."
            }
        })

        col.addView(label(
            "Tap the island to expand it. Tap the EN/ORIG chip to switch lyric language (hold it to set the " +
                "song's language if the translation looks wrong). Tap the search icon to search, or long-press " +
                "it (or the pill) to speak.\n\n" +
                "If notification access is greyed out after sideloading: Settings > Apps > Lizmo > " +
                "\u22EE menu > Allow restricted settings, then try again.",
            13f, a = 0.55f
        ).also { it.setPadding(0, dp(24), 0, 0) })

        setContentView(scroll)

        // ── First-launch auto-setup ───────────────────────────────────────────
        // Voice listening defaults to ON. On the very first run, prompt for mic
        // permission immediately so the wake word works right away.
        if (Prefs.isFirstLaunch(this)) {
            Prefs.setFirstLaunchDone(this)
            if (!hasMic()) {
                askMic(7)   // permission result → enableVoice() if granted
            } else {
                // Mic already available (re-install / emulator) — start right now
                if (!VoiceService.running) enableVoice()
            }
        }
    }

    // ---------- permissions ----------
    private fun askMic(code: Int) {
        val perms = if (Build.VERSION.SDK_INT >= 33)
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
        else arrayOf(Manifest.permission.RECORD_AUDIO)
        requestPermissions(perms, code)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 7 && requestCode != 8) return
        if (hasMic()) {
            if (requestCode == 7) enableVoice() else testMic()
        } else {
            if (requestCode == 7) voiceSw.isChecked = false
            voiceStatus.text = "Microphone permission is off."
            if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "Turn on Microphone for Lizmo in Permissions", Toast.LENGTH_LONG).show()
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                )
            }
        }
    }

    private fun enableVoice() {
        Prefs.setVoiceOn(this, true)
        VoiceService.start(this)
    }

    // ---------- microphone test ----------
    private fun testMic() {
        if (!hasMic()) { askMic(8); return }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceStatus.text = "No speech recognizer on this phone. Install/enable the Google app and set it as voice input."
            return
        }
        VoiceService.stop(this)   // only one recognizer can listen at a time
        voiceStatus.text = "Test: speak now\u2026"
        // Reset VU bar
        updateVu(-10f)
        try {
            testRec?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            testRec = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { voiceStatus.text = "Test: listening\u2026" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    updateVu(rmsdB)
                    val sens = Prefs.micSensitivity(this@MainActivity)
                    val gate = if (rmsdB >= sens.rmsThresholdDb) "ACTIVE \u2713" else "too quiet"
                    runOnUiThread {
                        voiceStatus.text = "Level: %.1f dB  |  Gate [${sens.label.substringBefore(" (")}]: $gate".format(rmsdB)
                    }
                }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {
                    val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!p.isNullOrBlank()) voiceStatus.text = "Test hearing: $p"
                }
                override fun onError(error: Int) {
                    voiceStatus.text = "Test failed: " + speechErrorName(error)
                    updateVu(-10f)
                    resumeVoiceIfOn()
                }
                override fun onResults(results: Bundle?) {
                    val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    val rest = WakeWord.extract(said, Prefs.wake(this@MainActivity))
                    voiceStatus.text = "Heard: \"$said\"\n" + when {
                        rest == null -> "\u274C wake word not found. Add how it was written above to the wake word list."
                        rest.isBlank() -> "\u2705 wake word heard (no command)"
                        else -> "\u2705 wake word + command: \"$rest\""
                    }
                    updateVu(-10f)
                    resumeVoiceIfOn()
                }
            })
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Prefs.voiceLang(this@MainActivity))
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            })
        } catch (e: Exception) {
            voiceStatus.text = "Test failed: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    private fun resumeVoiceIfOn() {
        try { testRec?.destroy() } catch (e: Exception) {}
        testRec = null
        if (Prefs.voiceOn(this) && hasMic()) VoiceService.start(this)
    }

    // ---------- lifecycle ----------
    override fun onResume() {
        super.onResume()
        val n = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
        val o = Settings.canDrawOverlays(this)
        s1.text = "1. Notification access: " + if (n) "ON ✓" else "OFF"
        s2.text = "2. Overlay permission: " + if (o) "ON ✓" else "OFF"
        MediaListener.instance?.applyPrefs()
        syncing = true
        voiceSw.isChecked = Prefs.voiceOn(this)
        syncing = false
        voiceStatus.text = VoiceBus.lastLog
        VoiceBus.logListener = { msg -> runOnUiThread { voiceStatus.text = msg } }
        // Hook live RMS into our VU meter
        VoiceBus.rmsListener = { db -> updateVu(db) }
        // Restart the wake-word service if it was on but got killed, or just needs starting
        if (Prefs.voiceOn(this) && !VoiceService.running && hasMic()) VoiceService.start(this)
    }

    override fun onPause() {
        VoiceBus.logListener = null
        VoiceBus.rmsListener = null
        super.onPause()
    }

    override fun onDestroy() {
        try { testRec?.destroy() } catch (e: Exception) {}
        super.onDestroy()
    }
}
