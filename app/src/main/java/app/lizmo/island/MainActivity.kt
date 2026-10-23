package app.lizmo.island

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.EditText
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
    private var testRec: SpeechRecognizer? = null
    private var syncing = false

    private val langs = listOf("en-IN", "en-US", "en-GB")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

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

        // ---------------- wake word ----------------
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

        voiceStatus = label(VoiceBus.lastLog, 13f).also { it.setPadding(0, dp(12), 0, dp(4)) }
        col.addView(voiceStatus)
        col.addView(btn("Test microphone (say \"lizmo pause\")") { testMic() })

        voiceSw = Switch(this).apply {
            this.text = "Always listen for wake word (experimental)"
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
            "Always-listening uses the phone's speech recognizer, so it can beep, lower your music and use " +
                "battery. If you prefer, leave it off and long-press the island instead.",
            12f, a = 0.5f
        ))

        col.addView(btn("Battery settings (let it run in background)") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
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
    }

    // ---------------- permissions ----------------
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
                // denied "don't ask again": the only way is the app settings page
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

    // ---------------- microphone test ----------------
    private fun testMic() {
        if (!hasMic()) { askMic(8); return }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceStatus.text = "No speech recognizer on this phone. Install/enable the Google app and set it as voice input."
            return
        }
        VoiceService.stop(this)   // only one recognizer can listen at a time
        voiceStatus.text = "Test: speak now\u2026"
        try {
            testRec?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            testRec = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { voiceStatus.text = "Test: listening\u2026" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onPartialResults(partialResults: Bundle?) {
                    val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!p.isNullOrBlank()) voiceStatus.text = "Test hearing: $p"
                }
                override fun onError(error: Int) {
                    voiceStatus.text = "Test failed: " + speechErrorName(error)
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

    // ---------------- lifecycle ----------------
    override fun onResume() {
        super.onResume()
        val n = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
        val o = Settings.canDrawOverlays(this)
        s1.text = "1. Notification access: " + if (n) "ON \u2713" else "OFF"
        s2.text = "2. Overlay permission: " + if (o) "ON \u2713" else "OFF"
        MediaListener.instance?.applyPrefs()
        syncing = true
        voiceSw.isChecked = Prefs.voiceOn(this)
        syncing = false
        voiceStatus.text = VoiceBus.lastLog
        VoiceBus.logListener = { msg -> runOnUiThread { voiceStatus.text = msg } }
        // restart the wake-word service if it was on but got killed (allowed: we're in the foreground)
        if (Prefs.voiceOn(this) && !VoiceService.running && hasMic()) VoiceService.start(this)
    }

    override fun onPause() {
        VoiceBus.logListener = null
        super.onPause()
    }

    override fun onDestroy() {
        try { testRec?.destroy() } catch (e: Exception) {}
        super.onDestroy()
    }
}
