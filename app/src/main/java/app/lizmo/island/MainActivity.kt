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
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    // ── Field references across methods ───────────────────────────────────────
    private lateinit var s1Chip: TextView          // notification access status
    private lateinit var s2Chip: TextView          // overlay permission status
    private lateinit var s3Chip: TextView          // mic permission status
    private lateinit var voiceSw: Switch
    private lateinit var voiceStatus: TextView
    private lateinit var vuBar: FrameLayout
    private lateinit var vuContainer: FrameLayout
    private lateinit var platformTv: TextView
    private lateinit var voiceLockSw: Switch
    private lateinit var enrollStatusTv: TextView
    private lateinit var deleteVoiceBtn: TextView

    private var testRec: SpeechRecognizer? = null
    private var syncing = false
    private val h = Handler(Looper.getMainLooper())
    private val langs = listOf("en-IN", "en-US", "en-GB", "hi-IN")
    private val builtins = listOf("JARVIS", "PICOVOICE", "COMPUTER", "BUMBLEBEE")

    // ── Colours ───────────────────────────────────────────────────────────────
    private val BG       = Color.parseColor("#07070C")
    private val CARD     = Color.parseColor("#12121A")
    private val ACCENT   = Color.parseColor("#7C5CFC")   // purple
    private val ACCENT2  = Color.parseColor("#4F8EF7")   // blue
    private val GREEN    = Color.parseColor("#22C55E")
    private val RED      = Color.parseColor("#EF4444")
    private val GOLD     = Color.parseColor("#F59E0B")
    private val WHITE    = Color.WHITE
    private val DIM      = Color.parseColor("#99FFFFFF")
    private val DIMMER   = Color.parseColor("#55FFFFFF")

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** A styled dark card with rounded corners and optional accent left-border. */
    private fun card(accentColor: Int = 0, content: LinearLayout.() -> Unit): LinearLayout {
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            content()
        }
        return if (accentColor != 0) {
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = GradientDrawable().apply {
                    setColor(CARD)
                    cornerRadius = dp(14).toFloat()
                }
                elevation = dp(2).toFloat()
                addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(4), LinearLayout.LayoutParams.MATCH_PARENT)
                    background = GradientDrawable().apply {
                        setColor(accentColor)
                        cornerRadius = dp(2).toFloat()
                    }
                })
                addView(inner, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        } else {
            inner.apply {
                background = GradientDrawable().apply {
                    setColor(CARD)
                    cornerRadius = dp(14).toFloat()
                }
                elevation = dp(2).toFloat()
            }
            inner
        }
    }

    private fun lp(topDp: Int = 0, bottomDp: Int = 0) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(dp(16), dp(topDp), dp(16), dp(bottomDp))
        }

    private fun tv(text: String, size: Float = 14f, color: Int = WHITE, bold: Boolean = false, gravity: Int = Gravity.START): TextView =
        TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            this.gravity = gravity
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun chip(text: String, bg: Int, textColor: Int = WHITE): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 11f
            setTextColor(textColor)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = dp(20).toFloat()
            }
        }

    private fun primaryBtn(text: String, bg: Int = ACCENT, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { onClick() }
        }

    private fun ghostBtn(text: String, onClick: () -> Unit): TextView =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(DIM)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { onClick() }
        }

    private fun sectionHeader(emoji: String, title: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
            addView(tv(emoji, 20f).apply { setPadding(0, 0, dp(10), 0) })
            addView(tv(title, 17f, WHITE, bold = true))
        }

    private fun stepRow(num: String, title: String, sub: String, accent: Int = ACCENT): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(6), 0, dp(6))
            addView(TextView(context).apply {
                text = num
                textSize = 12f
                setTextColor(WHITE)
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(7), dp(4), dp(7), dp(4))
                background = GradientDrawable().apply {
                    setColor(accent)
                    cornerRadius = dp(20).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(12); topMargin = dp(2) }
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(tv(title, 14f, WHITE, bold = true))
                addView(tv(sub, 12f, DIMMER).apply { setPadding(0, dp(2), 0, 0) })
            })
        }

    private fun commandRow(say: String, action: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(5), dp(4), dp(5))
            addView(TextView(context).apply {
                text = "\"$say\""
                textSize = 12f
                setTextColor(Color.parseColor("#C4B5FD"))
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A7C5CFC"))
                    cornerRadius = dp(8).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10) }
            })
            addView(tv("→", 12f, DIMMER).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10) }
            })
            addView(tv(action, 12f, DIM))
        }

    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            setMargins(0, dp(8), 0, dp(8))
        }
        setBackgroundColor(Color.parseColor("#1AFFFFFF"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(24), 0, dp(48))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            addView(root)
        }

        // ── HERO HEADER ───────────────────────────────────────────────────────
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(16), dp(24), dp(20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#1A7C5CFC"), Color.parseColor("#0A4F8EF7"), Color.TRANSPARENT)
            )
            addView(tv("🏝️", 40f, gravity = Gravity.CENTER))
            addView(tv("Lizmo", 32f, WHITE, bold = true, gravity = Gravity.CENTER).apply {
                setPadding(0, dp(4), 0, 0)
            })
            addView(tv("Dynamic Island Music & Voice Assistant", 13f, DIMMER, gravity = Gravity.CENTER).apply {
                setPadding(0, dp(4), 0, dp(12))
            })
            val keyPresent = Prefs.picoKey(this@MainActivity).isNotBlank()
            val engineBadge = if (keyPresent) "  Porcupine On-Device Wake Word  ●  Active" else "  SpeechRecognizer Fallback Mode"
            addView(chip(engineBadge, Color.parseColor("#22FFFFFF"), if (keyPresent) GREEN else GOLD).apply {
                gravity = Gravity.CENTER
            })
        })

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION 1 — SETUP CHECKLIST
        // ═══════════════════════════════════════════════════════════════════════
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            addView(tv("  Setup Checklist", 13f, DIMMER, bold = true).apply {
                setPadding(dp(4), 0, 0, dp(8))
            })
        })

        val setupCard = card(ACCENT) {
            addView(sectionHeader("🔧", "3 Permissions Required"))
            addView(divider())

            fun permRow(icon: String, label: String, chipRef: (TextView) -> Unit, onClick: () -> Unit): LinearLayout =
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(10), 0, dp(10))
                    addView(tv(icon, 18f).apply { layoutParams = LinearLayout.LayoutParams(dp(32), LinearLayout.LayoutParams.WRAP_CONTENT) })
                    addView(tv(label, 13f, WHITE).apply {
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    val c = chip("Checking…", Color.parseColor("#33FFFFFF"), DIM)
                    chipRef(c)
                    c.setOnClickListener { onClick() }
                    addView(c)
                }

            addView(permRow("🔔", "Notification Access", { s1Chip = it }) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            })
            addView(divider())
            addView(permRow("🪟", "Overlay (Float over apps)", { s2Chip = it }) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            })
            addView(divider())
            addView(permRow("🎙️", "Microphone (Voice control)", { s3Chip = it }) {
                if (!hasMic()) askMic(7) else Toast.makeText(this@MainActivity, "✅ Microphone already granted", Toast.LENGTH_SHORT).show()
            })

            addView(divider())
            addView(tv("Tap any ⊘ chip to grant permission.", 11f, DIMMER).apply { setPadding(0, dp(4), 0, 0) })
        }
        root.addView(setupCard, lp(0, 12))

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION 2 — HOW TO USE
        // ═══════════════════════════════════════════════════════════════════════
        root.addView(tv("  How to Use", 13f, DIMMER, bold = true).apply {
            setPadding(dp(20), dp(4), 0, dp(8))
        })
        val howCard = card(ACCENT2) {
            addView(sectionHeader("🚀", "Quick Start Guide"))
            addView(divider())
            addView(stepRow("1", "Play any music", "Open Spotify, YouTube Music, JioSaavn, or any player and start a song.", ACCENT))
            addView(stepRow("2", "Island appears automatically", "A floating pill appears at the top of your screen. Tap it to expand.", ACCENT2))
            addView(stepRow("3", "Say your wake word", "Say \"lizmo\" (or your chosen name). Porcupine wakes up on-device instantly.", GREEN))
            addView(stepRow("4", "Speak your command", "Pause, skip, play song, volume up/down, repeat, or ask what's playing.", GOLD))
            addView(divider())
            addView(tv("💡  Tip: You can also tap the mic in the 'Quick Actions' below or use the Quick Settings tile.", 11f, DIMMER).apply {
                setPadding(0, dp(4), 0, 0)
            })
        }
        root.addView(howCard, lp(0, 12))

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION 3 — WAKE WORD & PICOVOICE ENGINE
        // ═══════════════════════════════════════════════════════════════════════
        root.addView(tv("  Wake Word Engine", 13f, DIMMER, bold = true).apply {
            setPadding(dp(20), dp(4), 0, dp(8))
        })
        val wakeCard = card(ACCENT) {
            addView(sectionHeader("⚡", "Picovoice Porcupine (On-Device)"))
            addView(divider())
            addView(tv("Porcupine runs 100% locally on raw PCM frames without audio transcription, beeps, or cloud network latency.", 13f, DIM).apply {
                setPadding(0, dp(2), 0, dp(8))
            })

            // Picovoice AccessKey input
            addView(tv("Picovoice AccessKey (Free):", 13f, WHITE, bold = true))
            val keyInput = EditText(this@MainActivity).apply {
                setText(Prefs.picoKey(this@MainActivity))
                hint = "Paste your free key from console.picovoice.ai"
                setTextColor(WHITE)
                textSize = 14f
                setHintTextColor(Color.parseColor("#55FFFFFF"))
                inputType = InputType.TYPE_CLASS_TEXT
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0DFFFFFF"))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.parseColor("#33FFFFFF"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6); bottomMargin = dp(8) }
            }
            addView(keyInput)

            addView(primaryBtn("💾  Save AccessKey") {
                val key = keyInput.text.toString().trim()
                Prefs.setPicoKey(this@MainActivity, key)
                Toast.makeText(this@MainActivity, if (key.isNotEmpty()) "✅ Key saved" else "Key cleared (fallback active)", Toast.LENGTH_SHORT).show()
                if (Prefs.voiceOn(this@MainActivity) && hasMic()) {
                    VoiceService.stop(this@MainActivity)
                    h.postDelayed({ VoiceService.start(this@MainActivity) }, 400)
                }
            })

            addView(divider())

            // Custom keyword status / built-in selector
            val ppn = VoiceService.customKeywordFile(this@MainActivity)
            val hasCustomPpn = ppn.exists()
            val ppnStatus = if (hasCustomPpn) "Using custom 'wake.ppn' (${Prefs.wake(this@MainActivity).substringBefore(",").trim()})"
                           else "Using built-in word: ${Prefs.builtin(this@MainActivity)}"
            addView(tv(ppnStatus, 12f, if (hasCustomPpn) GREEN else Color.parseColor("#C4B5FD"), bold = true).apply {
                setPadding(0, dp(4), 0, dp(4))
            })

            if (!hasCustomPpn) {
                val builtinRow = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(6))
                }
                builtinRow.addView(tv("Built-in trigger word:", 13f, WHITE).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                val builtinChip = chip(Prefs.builtin(this@MainActivity), ACCENT2)
                builtinChip.setOnClickListener {
                    val next = builtins[(builtins.indexOf(Prefs.builtin(this@MainActivity)) + 1) % builtins.size]
                    Prefs.setBuiltin(this@MainActivity, next)
                    builtinChip.text = next
                    if (Prefs.voiceOn(this@MainActivity) && hasMic()) {
                        VoiceService.stop(this@MainActivity)
                        h.postDelayed({ VoiceService.start(this@MainActivity) }, 400)
                    }
                }
                builtinRow.addView(builtinChip)
                addView(builtinRow)
            }

            addView(divider())

            // Fallback wake word aliases
            addView(tv("Fallback wake word & mishearings (for STT fallback):", 12f, DIMMER))
            val wakeInput = EditText(this@MainActivity).apply {
                setText(Prefs.wake(this@MainActivity))
                hint = "lizmo, lismo, lizma, limo"
                setTextColor(WHITE)
                textSize = 14f
                setHintTextColor(Color.parseColor("#55FFFFFF"))
                inputType = InputType.TYPE_CLASS_TEXT
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0DFFFFFF"))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.parseColor("#33FFFFFF"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4); bottomMargin = dp(8) }
            }
            addView(wakeInput)
            addView(ghostBtn("Save Fallback Aliases") {
                val list = wakeInput.text.toString().split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                if (list.isNotEmpty()) {
                    Prefs.setWake(this@MainActivity, list.joinToString(", "))
                    Toast.makeText(this@MainActivity, "✅ Aliases saved", Toast.LENGTH_SHORT).show()
                }
            })

            addView(divider())

            // Voice language
            val langRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(4))
            }
            langRow.addView(tv("Voice recognition language:", 13f, WHITE).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            val langChip = chip(Prefs.voiceLang(this@MainActivity), ACCENT2)
            langChip.setOnClickListener {
                val next = langs[(langs.indexOf(Prefs.voiceLang(this@MainActivity)) + 1) % langs.size]
                Prefs.setVoiceLang(this@MainActivity, next)
                langChip.text = next
            }
            langRow.addView(langChip)
            addView(langRow)
            addView(tv("en-IN = Indian English  •  en-US = American  •  en-GB = British  •  hi-IN = Hindi", 11f, DIMMER))
        }
        root.addView(wakeCard, lp(0, 12))

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION 4 — SPEAKER VERIFICATION & ENROLLMENT (EAGLE)
        // ═══════════════════════════════════════════════════════════════════════
        root.addView(tv("  Speaker Recognition", 13f, DIMMER, bold = true).apply {
            setPadding(dp(20), dp(4), 0, dp(8))
        })
        val enrollCard = card(Color.parseColor("#EC4899")) {
            addView(sectionHeader("👤", "Voice Lock (Only Your Voice)"))
            addView(divider())
            addView(tv("Train Lizmo on your voice with 3 different sentences. Once enrolled, Lizmo verifies your voiceprint before executing commands.", 13f, DIM).apply {
                setPadding(0, dp(2), 0, dp(10))
            })

            enrollStatusTv = tv("Checking voice profile…", 13f, WHITE, bold = true).apply {
                setPadding(0, 0, 0, dp(8))
            }
            addView(enrollStatusTv)

            // Switch to enable/disable lock
            voiceLockSw = Switch(this@MainActivity).apply {
                text = "Only respond to enrolled voice"
                setTextColor(WHITE)
                textSize = 13f
                isChecked = Prefs.lock(this@MainActivity)
                setOnCheckedChangeListener { _, on ->
                    val profileExists = VoiceService.voiceProfileFile(this@MainActivity).exists()
                    if (on && !profileExists) {
                        Toast.makeText(this@MainActivity, "Enroll your voice first!", Toast.LENGTH_SHORT).show()
                        isChecked = false
                    } else {
                        Prefs.setLock(this@MainActivity, on)
                        if (VoiceService.running) {
                            VoiceService.stop(this@MainActivity)
                            h.postDelayed({ VoiceService.start(this@MainActivity) }, 300)
                        }
                    }
                }
            }
            addView(voiceLockSw)

            addView(divider())

            // Strictness selector
            addView(tv("Verification Strictness:", 13f, WHITE, bold = true))
            val strictRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(10))
            }
            val strictLevels = listOf("Relaxed (0.30)", "Balanced (0.45)", "Strict (0.60)")
            val strictButtons = ArrayList<TextView>()
            strictLevels.forEachIndexed { idx, name ->
                val cur = Prefs.lockLevel(this@MainActivity)
                val sel = (cur == idx)
                val b = TextView(this@MainActivity).apply {
                    text = name
                    textSize = 11f
                    gravity = Gravity.CENTER
                    setTextColor(if (sel) WHITE else DIM)
                    setTypeface(typeface, if (sel) Typeface.BOLD else Typeface.NORMAL)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    background = GradientDrawable().apply {
                        setColor(if (sel) Color.parseColor("#33EC4899") else Color.parseColor("#0DFFFFFF"))
                        cornerRadius = dp(8).toFloat()
                        setStroke(dp(1), if (sel) Color.parseColor("#EC4899") else Color.parseColor("#22FFFFFF"))
                    }
                    setOnClickListener {
                        Prefs.setLockLevel(this@MainActivity, idx)
                        strictButtons.forEachIndexed { i, btn ->
                            val isChosen = (i == idx)
                            btn.setTextColor(if (isChosen) WHITE else DIM)
                            (btn.background as? GradientDrawable)?.apply {
                                setColor(if (isChosen) Color.parseColor("#33EC4899") else Color.parseColor("#0DFFFFFF"))
                                setStroke(dp(1), if (isChosen) Color.parseColor("#EC4899") else Color.parseColor("#22FFFFFF"))
                            }
                        }
                    }
                }
                strictButtons.add(b)
                strictRow.addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                })
            }
            addView(strictRow)

            addView(primaryBtn("🎙️  Teach Lizmo Your Voice", Color.parseColor("#EC4899")) {
                startActivity(Intent(this@MainActivity, EnrollActivity::class.java))
            })

            deleteVoiceBtn = ghostBtn("🗑️  Delete Enrolled Voice") {
                val f = VoiceService.voiceProfileFile(this@MainActivity)
                if (f.exists()) f.delete()
                Prefs.setLock(this@MainActivity, false)
                voiceLockSw.isChecked = false
                refreshEnrollStatus()
                Toast.makeText(this@MainActivity, "Voice profile deleted", Toast.LENGTH_SHORT).show()
            }
            addView(deleteVoiceBtn.apply {
                (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(8)
            })
        }
        root.addView(enrollCard, lp(0, 12))

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION 5 — MICROPHONE PRIVACY & LIVE DIAGNOSTICS
        // ═══════════════════════════════════════════════════════════════════════
        root.addView(tv("  Microphone & Privacy", 13f, DIMMER, bold = true).apply {
            setPadding(dp(20), dp(4), 0, dp(8))
        })
        val micCard = card(GOLD) {
            addView(sectionHeader("🎙️", "Privacy Mode & Diagnostics"))
            addView(divider())

            // Mic Mode Selector: Always vs Music Only
            addView(tv("Microphone Active Window:", 13f, WHITE, bold = true))
            val modeRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, dp(8))
            }
            val isMusicOnly = Prefs.micMode(this@MainActivity) == "music"

            val btnAlways = TextView(this@MainActivity).apply {
                text = "Always Listening"
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }
            val btnMusic = TextView(this@MainActivity).apply {
                text = "Only While Music Plays"
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(10), dp(12), dp(10))
            }

            fun updateModeButtons(musicOnly: Boolean) {
                btnAlways.apply {
                    setTextColor(if (!musicOnly) GOLD else DIM)
                    background = GradientDrawable().apply {
                        setColor(if (!musicOnly) Color.parseColor("#33F59E0B") else Color.parseColor("#0DFFFFFF"))
                        cornerRadius = dp(10).toFloat()
                        setStroke(dp(1), if (!musicOnly) GOLD else Color.parseColor("#22FFFFFF"))
                    }
                }
                btnMusic.apply {
                    setTextColor(if (musicOnly) GOLD else DIM)
                    background = GradientDrawable().apply {
                        setColor(if (musicOnly) Color.parseColor("#33F59E0B") else Color.parseColor("#0DFFFFFF"))
                        cornerRadius = dp(10).toFloat()
                        setStroke(dp(1), if (musicOnly) GOLD else Color.parseColor("#22FFFFFF"))
                    }
                }
            }
            updateModeButtons(isMusicOnly)

            btnAlways.setOnClickListener {
                Prefs.setMicMode(this@MainActivity, "always")
                updateModeButtons(false)
                Toast.makeText(this@MainActivity, "Mic is always active for wake word", Toast.LENGTH_SHORT).show()
            }
            btnMusic.setOnClickListener {
                Prefs.setMicMode(this@MainActivity, "music")
                updateModeButtons(true)
                Toast.makeText(this@MainActivity, "Mic pauses when music stops (hides green dot)", Toast.LENGTH_SHORT).show()
            }

            modeRow.addView(btnAlways, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(4) })
            modeRow.addView(btnMusic, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(4) })
            addView(modeRow)
            addView(tv("Tip: 'Only While Music Plays' releases the microphone as soon as music stops, removing Android's green privacy indicator.", 11f, DIMMER).apply {
                setPadding(0, dp(2), 0, dp(10))
            })

            addView(divider())

            // Always-on voice switch
            voiceSw = Switch(this@MainActivity).apply {
                text = "Voice Assistant Master Switch"
                setTextColor(WHITE)
                textSize = 13f
                isChecked = Prefs.voiceOn(this@MainActivity)
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
            addView(voiceSw)

            addView(divider())

            // Live VU meter
            addView(tv("Live Microphone Audio Level:", 12f, DIM, bold = true).apply { setPadding(0, dp(6), 0, dp(4)) })
            vuContainer = FrameLayout(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(20)).apply {
                    setMargins(0, 0, 0, dp(6))
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1AFFFFFF"))
                    cornerRadius = dp(10).toFloat()
                }
            }
            vuBar = FrameLayout(this@MainActivity).apply {
                layoutParams = FrameLayout.LayoutParams(dp(4), FrameLayout.LayoutParams.MATCH_PARENT).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
                background = GradientDrawable().apply {
                    setColor(GREEN)
                    cornerRadius = dp(10).toFloat()
                }
            }
            vuContainer.addView(vuBar)
            addView(vuContainer)

            // Diagnostic status line
            voiceStatus = tv(VoiceBus.lastLog, 12f, DIMMER).apply { setPadding(0, dp(2), 0, dp(10)) }
            addView(voiceStatus)

            addView(primaryBtn("🧪  Test Microphone Recognition", GOLD) { testMic() })
            addView(tv("Shows exactly what the recognizer hears and validates wake word / control match.", 11f, DIMMER).apply {
                setPadding(0, dp(6), 0, 0)
            })

            addView(divider())
            addView(ghostBtn("🔋  Disable Battery Optimization (Keeps Voice Running)") {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            })
        }
        root.addView(micCard, lp(0, 12))

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION 6 — VOICE COMMANDS CHEAT-SHEET
        // ═══════════════════════════════════════════════════════════════════════
        root.addView(tv("  Voice Commands Reference", 13f, DIMMER, bold = true).apply {
            setPadding(dp(20), dp(4), 0, dp(8))
        })
        val cmdCard = card(GREEN) {
            addView(sectionHeader("🎤", "Natural Commands Recognized"))
            addView(divider())
            addView(tv("⏯  Playback", 12f, DIMMER, bold = true).apply { setPadding(0, dp(4), 0, dp(2)) })
            addView(commandRow("pause", "Pauses active player"))
            addView(commandRow("resume / play", "Resumes music"))
            addView(commandRow("next / skip this song", "Next track"))
            addView(commandRow("go back / previous", "Previous track"))
            addView(commandRow("restart / play from start", "Replays from beginning"))
            addView(divider())
            addView(tv("🔊  Volume", 12f, DIMMER, bold = true).apply { setPadding(0, dp(4), 0, dp(2)) })
            addView(commandRow("turn the volume down a bit", "Decreases volume"))
            addView(commandRow("make it louder", "Increases volume"))
            addView(commandRow("volume to sixty percent", "Sets exact volume (digits/words)"))
            addView(commandRow("mute / unmute", "Mutes or restores volume"))
            addView(divider())
            addView(tv("🔍  Search & Play", 12f, DIMMER, bold = true).apply { setPadding(0, dp(4), 0, dp(2)) })
            addView(commandRow("play Kesariya", "Plays in Spotify/YT Music automatically"))
            addView(commandRow("play Blinding Lights by The Weeknd", "Searches song + artist"))
            addView(divider())
            addView(tv("🔀  Shuffle & Repeat", 12f, DIMMER, bold = true).apply { setPadding(0, dp(4), 0, dp(2)) })
            addView(commandRow("shuffle / mix it up", "Enables shuffle"))
            addView(commandRow("repeat this song / loop one", "Repeats current song"))
            addView(divider())
            addView(tv("ℹ️  Info", 12f, DIMMER, bold = true).apply { setPadding(0, dp(4), 0, dp(2)) })
            addView(commandRow("what's playing", "Reads title and artist"))
            addView(commandRow("what's the volume", "Reports current volume %"))
        }
        root.addView(cmdCard, lp(0, 12))

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION 7 — ACTIVE MUSIC PLATFORM
        // ═══════════════════════════════════════════════════════════════════════
        root.addView(tv("  Music Platform", 13f, DIMMER, bold = true).apply {
            setPadding(dp(20), dp(4), 0, dp(8))
        })
        val platformCard = card(ACCENT2) {
            addView(sectionHeader("🎵", "Active Player Detection"))
            addView(divider())
            addView(tv("Lizmo detects which music app is active. When you say \"play [song]\", it commands that app directly without chooser prompts.", 13f, DIM).apply {
                setPadding(0, dp(2), 0, dp(10))
            })

            platformTv = tv("Not detected yet — start music playback first.", 13f, DIMMER).apply {
                setPadding(0, 0, 0, dp(10))
            }
            addView(platformTv)

            addView(primaryBtn("🔍  Detect Active Platform", ACCENT2) {
                val pkg = Playback.activeMusicPackage(this@MainActivity)
                platformTv.setTextColor(if (pkg != null) GREEN else RED)
                platformTv.text = if (pkg != null) {
                    val name = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
                               catch (e: Exception) { pkg }
                    "● Active: $name\nVoice commands will control $name seamlessly."
                } else {
                    "⚠ No active media session — open your music app and start playing first."
                }
            })
        }
        root.addView(platformCard, lp(0, 12))

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION 8 — QUICK ACTIONS
        // ═══════════════════════════════════════════════════════════════════════
        root.addView(tv("  Quick Actions", 13f, DIMMER, bold = true).apply {
            setPadding(dp(20), dp(4), 0, dp(8))
        })
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), 0, dp(16), 0)
            addView(primaryBtn("🔍  Search Song", ACCENT) {
                startActivity(Intent(this@MainActivity, SearchActivity::class.java))
            }.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) } })
            addView(primaryBtn("🎤  Voice Search", ACCENT2) {
                startActivity(Intent(this@MainActivity, SearchActivity::class.java).putExtra(SearchActivity.EXTRA_VOICE, true))
            }.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) } })
        })

        setContentView(scroll)

        // First launch auto-request
        if (Prefs.isFirstLaunch(this)) {
            Prefs.setFirstLaunchDone(this)
            if (!hasMic()) askMic(7) else { if (!VoiceService.running) enableVoice() }
        }
    }

    private fun refreshEnrollStatus() {
        val enrolled = VoiceService.voiceProfileFile(this).exists()
        val locked = Prefs.lock(this)
        enrollStatusTv.text = when {
            enrolled && locked -> "✅ Enrolled & Active (Lizmo only responds to you)"
            enrolled           -> "✓ Enrolled (Lock toggle is currently OFF)"
            else               -> "○ Not enrolled yet (responds to any voice)"
        }
        enrollStatusTv.setTextColor(if (enrolled) GREEN else Color.parseColor("#E0E0E0"))
        deleteVoiceBtn.visibility = if (enrolled) View.VISIBLE else View.GONE
        voiceLockSw.isChecked = locked
    }

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
            if (requestCode == 7) { syncing = true; voiceSw.isChecked = false; syncing = false }
            voiceStatus.text = "Microphone permission denied."
        }
        updatePermChips()
    }

    private fun enableVoice() {
        Prefs.setVoiceOn(this, true)
        VoiceService.start(this)
    }

    private fun testMic() {
        if (!hasMic()) { askMic(8); return }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceStatus.text = "No speech recognizer found on device."
            return
        }

        VoiceService.stop(this)
        voiceStatus.text = "🧪 Speak a wake word or command now (e.g. \"lizmo next\" or \"turn it down\")…"
        updateVu(-10f)

        try {
            testRec?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            testRec = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { voiceStatus.text = "🎙 Microphone open — speak now…" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    updateVu(rmsdB)
                    runOnUiThread {
                        voiceStatus.text = "Mic Level: %.1f dB".format(rmsdB)
                    }
                }
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(t: Int, p: Bundle?) {}
                override fun onPartialResults(pr: Bundle?) {
                    val p = pr?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!p.isNullOrBlank()) voiceStatus.text = "Hearing: \"$p\""
                }
                override fun onError(error: Int) {
                    voiceStatus.text = "Test error: " + speechErrorName(error)
                    updateVu(-10f)
                    resumeVoiceIfOn()
                }
                override fun onResults(results: Bundle?) {
                    val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                    val isCtrl = VoiceCommands.isControl(said)
                    val rest = WakeWord.extract(said, Prefs.wake(this@MainActivity))
                    val keyActive = Prefs.picoKey(this@MainActivity).isNotBlank()

                    val report = StringBuilder()
                    report.append("Heard: \"$said\"\n")
                    if (isCtrl) {
                        report.append("✅ Recognized as Media Control: \"${VoiceCommands.normalize(said)}\"\n")
                    } else if (rest != null) {
                        report.append("✅ Wake Word detected in transcript! (Command: \"$rest\")\n")
                    } else {
                        report.append("ℹ No control or wake word matched in transcript.\n")
                    }
                    report.append(if (keyActive) "⚡ Porcupine key configured (uses acoustic wake word)"
                                  else "ℹ No Picovoice key (uses STT wake word)")

                    voiceStatus.text = report.toString()
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

    private fun updateVu(rmsdB: Float) {
        runOnUiThread {
            val pct = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            val color = when {
                pct < 0.4f  -> GREEN
                pct < 0.75f -> GOLD
                else        -> RED
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

    private fun updatePermChips() {
        val n = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?.contains(packageName) == true
        val o = Settings.canDrawOverlays(this)
        val m = hasMic()

        fun setChip(chip: TextView, ok: Boolean, label: String) {
            chip.text = if (ok) "✓ $label" else "⊘ Tap to fix"
            chip.background = GradientDrawable().apply {
                setColor(if (ok) Color.parseColor("#1A22C55E") else Color.parseColor("#1AEF4444"))
                cornerRadius = dp(20).toFloat()
            }
            chip.setTextColor(if (ok) GREEN else RED)
        }

        setChip(s1Chip, n, "Granted")
        setChip(s2Chip, o, "Granted")
        setChip(s3Chip, m, "Granted")
    }

    override fun onResume() {
        super.onResume()
        updatePermChips()
        refreshEnrollStatus()
        MediaListener.instance?.applyPrefs()
        syncing = true
        voiceSw.isChecked = Prefs.voiceOn(this)
        syncing = false
        voiceStatus.text = VoiceBus.lastLog
        VoiceBus.logListener = { msg -> runOnUiThread { voiceStatus.text = msg } }
        VoiceBus.rmsListener = { db -> updateVu(db) }
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
