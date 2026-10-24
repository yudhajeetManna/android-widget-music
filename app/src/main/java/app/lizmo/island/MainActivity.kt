package app.lizmo.island

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
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
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    // ── field refs needed across methods ──────────────────────────────────────
    private lateinit var s1: TextView          // notification access status
    private lateinit var s1Chip: TextView
    private lateinit var s2: TextView          // overlay permission status
    private lateinit var s2Chip: TextView
    private lateinit var s3: TextView          // mic permission status
    private lateinit var s3Chip: TextView
    private lateinit var voiceSw: Switch
    private lateinit var voiceStatus: TextView
    private lateinit var vuBar: FrameLayout
    private lateinit var vuContainer: FrameLayout
    private lateinit var platformTv: TextView

    private var testRec: SpeechRecognizer? = null
    private var syncing = false
    private val h = Handler(Looper.getMainLooper())
    private val langs = listOf("en-IN", "en-US", "en-GB", "hi-IN")

    // ── colours ───────────────────────────────────────────────────────────────
    private val BG       = Color.parseColor("#07070C")
    private val CARD     = Color.parseColor("#12121A")
    private val CARD2    = Color.parseColor("#1A1A26")
    private val ACCENT   = Color.parseColor("#7C5CFC")   // purple
    private val ACCENT2  = Color.parseColor("#4F8EF7")   // blue
    private val GREEN    = Color.parseColor("#22C55E")
    private val RED      = Color.parseColor("#EF4444")
    private val GOLD     = Color.parseColor("#F59E0B")
    private val WHITE    = Color.WHITE
    private val DIM      = Color.parseColor("#99FFFFFF")
    private val DIMMER   = Color.parseColor("#55FFFFFF")

    // ── helpers ───────────────────────────────────────────────────────────────
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    private fun sp(v: Float): Float = v   // textSize is already sp
    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** A styled dark card with rounded corners and optional accent left-border. */
    private fun card(accentColor: Int = 0, content: LinearLayout.() -> Unit): LinearLayout {
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            content()
        }
        return if (accentColor != 0) {
            // Card with left accent stripe
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = GradientDrawable().apply {
                    setColor(CARD)
                    cornerRadius = dp(14).toFloat()
                }
                elevation = dp(2).toFloat()
                // accent stripe
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

    /** A rounded pill chip for status display. */
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

    /** A styled primary action button. */
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

    /** A secondary ghost button. */
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

    /** Section header with emoji icon. */
    private fun sectionHeader(emoji: String, title: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(4))
            addView(tv(emoji, 20f).apply { setPadding(0, 0, dp(10), 0) })
            addView(tv(title, 17f, WHITE, bold = true))
        }

    /** A "how to use" row with a numbered badge and instruction text. */
    private fun stepRow(num: String, title: String, sub: String, accent: Int = ACCENT): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            setPadding(0, dp(6), 0, dp(6))
            // number badge
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
            // text column
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(tv(title, 14f, WHITE, bold = true))
                addView(tv(sub, 12f, DIMMER).apply { setPadding(0, dp(2), 0, 0) })
            })
        }

    /** A voice command example row. */
    private fun commandRow(say: String, action: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(5), dp(4), dp(5))
            // "say" pill
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
            // arrow
            addView(tv("→", 12f, DIMMER).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(10) }
            })
            // action
            addView(tv(action, 12f, DIM))
        }

    /** Thin divider. */
    private fun divider(): View = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
            setMargins(0, dp(8), 0, dp(8))
        }
        setBackgroundColor(Color.parseColor("#1AFFFFFF"))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // onCreate
    // ═══════════════════════════════════════════════════════════════════════════
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
            // Gradient banner background
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#1A7C5CFC"), Color.parseColor("#0A4F8EF7"), Color.TRANSPARENT)
            ).apply { cornerRadius = 0f }
            addView(tv("🏝️", 40f, gravity = Gravity.CENTER))
            addView(tv("Lizmo", 32f, WHITE, bold = true, gravity = Gravity.CENTER).apply {
                setPadding(0, dp(4), 0, 0)
            })
            addView(tv("Dynamic Island Music Overlay", 13f, DIMMER, gravity = Gravity.CENTER).apply {
                setPadding(0, dp(4), 0, dp(12))
            })
            // version badge
            addView(chip("  v0.7.0  ●  Always-On Voice", Color.parseColor("#22FFFFFF"), DIM).apply {
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

            // Row helper: permission row
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

            addView(permRow("🔔", "Notification Access", { s1Chip = it; s1 = it }) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            })
            addView(divider())
            addView(permRow("🪟", "Overlay (Float over apps)", { s2Chip = it; s2 = it }) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            })
            addView(divider())
            addView(permRow("🎙️", "Microphone (Voice control)", { s3Chip = it; s3 = it }) {
                if (!hasMic()) askMic(7) else Toast.makeText(this@MainActivity, "✅ Microphone already granted", Toast.LENGTH_SHORT).show()
            })

            addView(divider())
            addView(tv("Tap any ⊘ chip to fix it.", 11f, DIMMER).apply { setPadding(0, dp(4), 0, 0) })
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
            addView(stepRow("3", "Say your wake word", "Just say \"lizmo\" (or your custom word). The mic is always listening.", GREEN))
            addView(stepRow("4", "Speak your command", "After the wake word, say what you want — play, pause, search, volume, etc.", GOLD))
            addView(divider())
            addView(tv("💡  Tip: You can also long-press the island pill to speak without the wake word.", 11f, DIMMER).apply {
                setPadding(0, dp(4), 0, 0)
            })
        }
        root.addView(howCard, lp(0, 12))

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION 3 — ISLAND CONTROLS
        // ═══════════════════════════════════════════════════════════════════════
        root.addView(tv("  Island Controls", 13f, DIMMER, bold = true).apply {
            setPadding(dp(20), dp(4), 0, dp(8))
        })
        val islandCard = card {
            addView(sectionHeader("🏝️", "Island Settings"))
            addView(divider())

            // Show/hide island switch
            val islandSw = Switch(this@MainActivity).apply {
                text = "Show floating island"
                setTextColor(WHITE)
                textSize = 14f
                isChecked = Prefs.enabled(this@MainActivity)
                setOnCheckedChangeListener { _, on ->
                    Prefs.setEnabled(this@MainActivity, on)
                    MediaListener.instance?.applyPrefs()
                }
            }
            addView(islandSw)
            addView(tv("The island floats over all apps and shows the track playing.", 11f, DIMMER).apply { setPadding(dp(4), dp(2), 0, dp(10)) })

            addView(divider())

            // Theme picker
            val themeRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val themeLbl = tv("Theme:", 13f, WHITE)
            themeRow.addView(themeLbl)
            themeRow.addView(View(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })
            val themeChip = chip(Prefs.theme(this@MainActivity).name.lowercase(), ACCENT)
            themeChip.setOnClickListener {
                Prefs.setTheme(this@MainActivity, Prefs.theme(this@MainActivity).next())
                themeChip.text = Prefs.theme(this@MainActivity).name.lowercase()
                MediaListener.instance?.applyPrefs()
            }
            themeRow.addView(themeChip)
            addView(themeRow)
            addView(tv("Glass · Neon · Minimal · Sunset · Mono — tap chip to cycle.", 11f, DIMMER).apply { setPadding(0, dp(2), 0, dp(6)) })

            addView(divider())

            // Lyrics mode
            val lyricRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            lyricRow.addView(tv("Lyrics language:", 13f, WHITE))
            lyricRow.addView(View(this@MainActivity).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) })
            val lyricChip = chip(if (Prefs.lyricMode(this@MainActivity) == "en") "English" else "Original", ACCENT2)
            lyricChip.setOnClickListener {
                val next = if (Prefs.lyricMode(this@MainActivity) == "en") "orig" else "en"
                Prefs.setLyricMode(this@MainActivity, next)
                lyricChip.text = if (next == "en") "English" else "Original"
                MediaListener.instance?.applyPrefs()
            }
            lyricRow.addView(lyricChip)
            addView(lyricRow)
            addView(tv("Tap EN/ORIG chip on the island too. Long-press to set the song's language.", 11f, DIMMER).apply { setPadding(0, dp(2), 0, dp(6)) })
        }
        root.addView(islandCard, lp(0, 12))

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION 4 — VOICE COMMANDS CHEAT-SHEET
        // ═══════════════════════════════════════════════════════════════════════
        root.addView(tv("  Voice Commands", 13f, DIMMER, bold = true).apply {
            setPadding(dp(20), dp(4), 0, dp(8))
        })
        val cmdCard = card(GREEN) {
            addView(sectionHeader("🎤", "Say any of these after your wake word"))
            addView(divider())
            // Playback
            addView(tv("⏯  Playback", 12f, DIMMER, bold = true).apply { setPadding(0, dp(4), 0, dp(2)) })
            addView(commandRow("lizmo pause", "Pauses music"))
            addView(commandRow("lizmo play", "Resumes music"))
            addView(commandRow("lizmo next", "Skip to next track"))
            addView(commandRow("lizmo previous", "Go back to last track"))
            addView(commandRow("lizmo restart", "Play from beginning"))
            addView(commandRow("lizmo stop", "Stop playback"))
            addView(divider())
            // Search
            addView(tv("🔍  Search & Play", 12f, DIMMER, bold = true).apply { setPadding(0, dp(4), 0, dp(2)) })
            addView(commandRow("lizmo play Kesariya", "Searches & plays in your active app"))
            addView(commandRow("lizmo play Blinding Lights", "Works with artist name too"))
            addView(divider())
            // Volume
            addView(tv("🔊  Volume", 12f, DIMMER, bold = true).apply { setPadding(0, dp(4), 0, dp(2)) })
            addView(commandRow("lizmo volume up", "Increase volume"))
            addView(commandRow("lizmo volume 60", "Set exact volume (0–100)"))
            addView(commandRow("lizmo mute", "Mute / unmute"))
            addView(divider())
            // Shuffle & Repeat
            addView(tv("🔀  Shuffle & Repeat", 12f, DIMMER, bold = true).apply { setPadding(0, dp(4), 0, dp(2)) })
            addView(commandRow("lizmo shuffle", "Toggle shuffle on/off"))
            addView(commandRow("lizmo repeat one", "Repeat this song"))
            addView(commandRow("lizmo repeat all", "Repeat whole playlist"))
            addView(divider())
            // Seeking
            addView(tv("⏩  Seek", 12f, DIMMER, bold = true).apply { setPadding(0, dp(4), 0, dp(2)) })
            addView(commandRow("lizmo skip forward 30 seconds", "Jump ahead 30 s"))
            addView(commandRow("lizmo rewind 15 seconds", "Jump back 15 s"))
            addView(commandRow("lizmo jump to 2 minutes", "Go to exact timestamp"))
            addView(divider())
            // Info
            addView(tv("ℹ️  Info", 12f, DIMMER, bold = true).apply { setPadding(0, dp(4), 0, dp(2)) })
            addView(commandRow("lizmo what's playing", "Read out current track & artist"))
            addView(commandRow("lizmo like this", "❤ Like / save song"))
            addView(commandRow("lizmo what's the volume", "Tell me current volume %"))
        }
        root.addView(cmdCard, lp(0, 12))

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION 5 — WAKE WORD SETUP
        // ═══════════════════════════════════════════════════════════════════════
        root.addView(tv("  Wake Word", 13f, DIMMER, bold = true).apply {
            setPadding(dp(20), dp(4), 0, dp(8))
        })
        val wakeCard = card(ACCENT) {
            addView(sectionHeader("💬", "Customize Your Trigger Word"))
            addView(divider())
            addView(tv("The microphone is always listening for this word. Say it, then your command.", 13f, DIM).apply {
                setPadding(0, dp(2), 0, dp(10))
            })

            // recommendation box
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A7C5CFC"))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.parseColor("#337C5CFC"))
                }
                addView(tv("✨  Recommended", 11f, Color.parseColor("#A78BFA"), bold = true))
                addView(tv("Keep \"lizmo\" or change to a short, unique word (3+ letters). Add comma-separated alternatives for how your phone mishears it.\n\nExample:  jarvis, jarves, jarvis", 12f, DIMMER).apply { setPadding(0, dp(4), 0, 0) })
            })

            addView(tv("\nYour wake word(s):", 13f, WHITE, bold = true))
            val wakeInput = EditText(this@MainActivity).apply {
                setText(Prefs.wake(this@MainActivity))
                hint = "lizmo, lismo, limo"
                setTextColor(WHITE)
                textSize = 15f
                setHintTextColor(Color.parseColor("#55FFFFFF"))
                inputType = InputType.TYPE_CLASS_TEXT
                setPadding(dp(14), dp(12), dp(14), dp(12))
                setBackgroundColor(Color.TRANSPARENT)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#0DFFFFFF"))
                    cornerRadius = dp(10).toFloat()
                    setStroke(dp(1), Color.parseColor("#33FFFFFF"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(6); bottomMargin = dp(10) }
            }
            addView(wakeInput)

            addView(primaryBtn("💾  Save Wake Word") {
                val list = wakeInput.text.toString().split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                if (list.isEmpty() || list.any { it.length < 3 }) {
                    Toast.makeText(this@MainActivity, "Each name needs at least 3 letters", Toast.LENGTH_SHORT).show()
                } else {
                    Prefs.setWake(this@MainActivity, list.joinToString(", "))
                    Toast.makeText(this@MainActivity, "✅ Saved: " + list.joinToString(", "), Toast.LENGTH_SHORT).show()
                    if (Prefs.voiceOn(this@MainActivity) && hasMic()) {
                        VoiceService.stop(this@MainActivity)
                        h.postDelayed({ VoiceService.start(this@MainActivity) }, 300)
                    }
                }
            })

            addView(divider())

            // Voice language
            val langRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(4))
            }
            langRow.addView(tv("Voice language:", 13f, WHITE).apply {
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
            addView(tv("en-IN = Indian English  •  en-US = American  •  en-GB = British  •  hi-IN = Hindi", 11f, DIMMER).apply {
                setPadding(0, dp(2), 0, 0)
            })
        }
        root.addView(wakeCard, lp(0, 12))

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION 6 — MICROPHONE SENSITIVITY + LIVE TEST
        // ═══════════════════════════════════════════════════════════════════════
        root.addView(tv("  Microphone", 13f, DIMMER, bold = true).apply {
            setPadding(dp(20), dp(4), 0, dp(8))
        })
        val micCard = card(GOLD) {
            addView(sectionHeader("🎙️", "Sensitivity & Live Test"))
            addView(divider())

            // Sensitivity picker
            addView(tv("How loud do you need to speak?", 13f, WHITE, bold = true))
            addView(tv("Pick based on your environment:", 12f, DIMMER).apply { setPadding(0, dp(2), 0, dp(8)) })

            // 3-option visual sensitivity picker
            val sensRow = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }
            fun makeSensChip(sens: MicSensitivity): LinearLayout =
                LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    val isSelected = Prefs.micSensitivity(this@MainActivity) == sens
                    setPadding(dp(10), dp(10), dp(10), dp(10))
                    background = GradientDrawable().apply {
                        setColor(if (isSelected) Color.parseColor("#33F59E0B") else Color.parseColor("#0DFFFFFF"))
                        cornerRadius = dp(12).toFloat()
                        setStroke(dp(if (isSelected) 2 else 1), if (isSelected) GOLD else Color.parseColor("#22FFFFFF"))
                    }
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(dp(3), 0, dp(3), 0)
                    }
                    val icon = when(sens) {
                        MicSensitivity.LOW -> "🤫"; MicSensitivity.MEDIUM -> "🗣️"; MicSensitivity.HIGH -> "📢"
                    }
                    val name = when(sens) {
                        MicSensitivity.LOW -> "Low"; MicSensitivity.MEDIUM -> "Medium"; MicSensitivity.HIGH -> "High"
                    }
                    val desc = when(sens) {
                        MicSensitivity.LOW -> "Quiet\nroom"; MicSensitivity.MEDIUM -> "Normal\nspeech"; MicSensitivity.HIGH -> "Noisy\nroom"
                    }
                    addView(tv(icon, 22f, gravity = Gravity.CENTER))
                    addView(tv(name, 12f, if (isSelected) GOLD else WHITE, bold = true, gravity = Gravity.CENTER))
                    addView(tv(desc, 10f, DIMMER, gravity = Gravity.CENTER))
                    tag = sens
                }

            val sensChips = MicSensitivity.values().map { makeSensChip(it) }

            fun refreshSensChips() {
                val cur = Prefs.micSensitivity(this@MainActivity)
                sensChips.forEachIndexed { _, chip ->
                    val sens = chip.tag as MicSensitivity
                    val sel = sens == cur
                    (chip.background as? GradientDrawable)?.apply {
                        setColor(if (sel) Color.parseColor("#33F59E0B") else Color.parseColor("#0DFFFFFF"))
                        setStroke(dp(if (sel) 2 else 1), if (sel) GOLD else Color.parseColor("#22FFFFFF"))
                    }
                    (chip.getChildAt(1) as? TextView)?.setTextColor(if (sel) GOLD else WHITE)
                }
            }

            sensChips.forEach { c ->
                c.setOnClickListener {
                    val sens = c.tag as MicSensitivity
                    Prefs.setMicSensitivity(this@MainActivity, sens)
                    refreshSensChips()
                    Toast.makeText(this@MainActivity, "Sensitivity: ${sens.label}", Toast.LENGTH_SHORT).show()
                }
                sensRow.addView(c)
            }
            addView(sensRow)

            addView(divider())

            // Always-on voice switch
            voiceSw = Switch(this@MainActivity).apply {
                text = "Always-on wake word (recommended ON)"
                setTextColor(WHITE)
                textSize = 13f
                isChecked = Prefs.voiceOn(this@MainActivity)
                setPadding(0, dp(4), 0, dp(4))
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
            addView(tv("Voice is ON by default. Turn off only if you want to save battery.", 11f, DIMMER).apply { setPadding(dp(4), dp(2), 0, dp(8)) })

            addView(divider())

            // VU meter
            addView(tv("Live mic level:", 12f, DIM, bold = true).apply { setPadding(0, dp(6), 0, dp(4)) })
            vuContainer = FrameLayout(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(22)).apply {
                    setMargins(0, 0, 0, dp(6))
                }
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1AFFFFFF"))
                    cornerRadius = dp(11).toFloat()
                }
            }
            vuBar = FrameLayout(this@MainActivity).apply {
                layoutParams = FrameLayout.LayoutParams(dp(4), FrameLayout.LayoutParams.MATCH_PARENT).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                }
                background = GradientDrawable().apply {
                    setColor(GREEN)
                    cornerRadius = dp(11).toFloat()
                }
            }
            vuContainer.addView(vuBar)
            addView(vuContainer)

            // Status text
            voiceStatus = tv(VoiceBus.lastLog, 12f, DIMMER).apply { setPadding(0, dp(2), 0, dp(10)) }
            addView(voiceStatus)

            addView(primaryBtn("🧪  Test Microphone — say \"lizmo pause\"", GOLD) { testMic() })
            addView(tv("Tests wake word recognition. Shows what your phone heard and whether it matched.", 11f, DIMMER).apply {
                setPadding(0, dp(6), 0, 0)
            })

            addView(divider())
            addView(ghostBtn("🔋  Battery settings (disable battery optimization)") {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            })
        }
        root.addView(micCard, lp(0, 12))

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION 7 — ACTIVE MUSIC PLATFORM
        // ═══════════════════════════════════════════════════════════════════════
        root.addView(tv("  Music Platform", 13f, DIMMER, bold = true).apply {
            setPadding(dp(20), dp(4), 0, dp(8))
        })
        val platformCard = card(ACCENT2) {
            addView(sectionHeader("🎵", "Active Player Detection"))
            addView(divider())
            addView(tv("Lizmo auto-detects which music app you're using. When you say \"lizmo play [song]\", it opens the song directly in that app without showing a chooser dialog.", 13f, DIM).apply {
                setPadding(0, dp(2), 0, dp(10))
            })

            platformTv = tv("Not detected yet — start playing music first.", 13f, DIMMER).apply {
                setPadding(0, 0, 0, dp(10))
            }
            addView(platformTv)

            addView(primaryBtn("🔍  Detect Active Platform", ACCENT2) {
                val pkg = Playback.activeMusicPackage(this@MainActivity)
                platformTv.setTextColor(if (pkg != null) GREEN else RED)
                platformTv.text = if (pkg != null) {
                    val name = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
                               catch (e: Exception) { pkg }
                    "● Active: $name\n\"lizmo play [song]\" will play directly in $name."
                } else {
                    "⚠ No active session — open a music app and play something first."
                }
            })

            addView(divider())
            addView(tv("Supported: Spotify · YouTube Music · Apple Music · JioSaavn · SoundCloud · Gaana · Wynk · Poweramp · VLC · Retro Music", 11f, DIMMER).apply {
                setPadding(0, dp(4), 0, 0)
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
            addView(primaryBtn("🔍  Search Music", ACCENT) {
                startActivity(Intent(this@MainActivity, SearchActivity::class.java))
            }.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) } })
            addView(primaryBtn("🎤  Voice Search", ACCENT2) {
                startActivity(Intent(this@MainActivity, SearchActivity::class.java).putExtra(SearchActivity.EXTRA_VOICE, true))
            }.apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(8) } })
        })

        // ═══════════════════════════════════════════════════════════════════════
        // SECTION 9 — TIPS & TROUBLESHOOTING
        // ═══════════════════════════════════════════════════════════════════════
        root.addView(tv("  Tips & Troubleshooting", 13f, DIMMER, bold = true).apply {
            setPadding(dp(20), dp(16), 0, dp(8))
        })
        val tipCard = card {
            addView(sectionHeader("💡", "Common Issues & Fixes"))
            addView(divider())

            fun tipRow(icon: String, title: String, body: String) {
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(6), 0, dp(6))
                    addView(tv("$icon  $title", 13f, WHITE, bold = true))
                    addView(tv(body, 12f, DIMMER).apply { setPadding(dp(24), dp(2), 0, 0) })
                })
                addView(divider())
            }

            tipRow("🏝️", "Island doesn't appear",
                "Enable Notification Access + Overlay permission. For Xiaomi/Samsung/OnePlus, also disable battery optimization.")
            tipRow("🎙️", "Wake word not working",
                "Say your name clearly. Check the mic test above to see what the phone hears. Add alternate spellings (e.g. \"lizmo, lismo, lismo\").")
            tipRow("🎵", "Song search opens a dialog / wrong app",
                "Tap 'Detect Active Platform' above after starting music. The last active app will be used automatically.")
            tipRow("🔒", "Notification access greyed out (Android 13+)",
                "Settings → Apps → Lizmo → ⋮ menu → Allow restricted settings → re-enable notification access.")
            tipRow("🔋", "Mic stops after a few minutes",
                "Tap 'Battery settings' above and disable battery optimization for Lizmo. Also enable Autostart if your phone has it.")
            addView(tv("Version 0.7.0  •  Open source on GitHub", 11f, DIMMER, gravity = Gravity.CENTER).apply {
                setPadding(0, dp(4), 0, 0)
            })
        }
        root.addView(tipCard, lp(0, 24))

        setContentView(scroll)

        // ── First-launch: auto-request mic ────────────────────────────────────
        if (Prefs.isFirstLaunch(this)) {
            Prefs.setFirstLaunchDone(this)
            if (!hasMic()) askMic(7) else { if (!VoiceService.running) enableVoice() }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Permission helpers
    // ═══════════════════════════════════════════════════════════════════════════
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
            if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                Toast.makeText(this, "Enable Microphone for Lizmo in app Permissions", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
        }
        updatePermChips()
    }

    private fun enableVoice() {
        Prefs.setVoiceOn(this, true)
        VoiceService.start(this)
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Mic test
    // ═══════════════════════════════════════════════════════════════════════════
    private fun testMic() {
        if (!hasMic()) { askMic(8); return }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceStatus.text = "No speech recognizer. Install/enable the Google app."
            return
        }
        VoiceService.stop(this)
        voiceStatus.text = "Test: speak now…"
        updateVu(-10f)
        try {
            testRec?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(this)
            testRec = r
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { voiceStatus.text = "🎙 Listening…" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {
                    updateVu(rmsdB)
                    val sens = Prefs.micSensitivity(this@MainActivity)
                    val gate = if (rmsdB >= sens.rmsThresholdDb) "ACTIVE ✓" else "too quiet"
                    runOnUiThread {
                        voiceStatus.text = "Level: %.1f dB  |  [${sens.label.substringBefore(" (")}]: $gate".format(rmsdB)
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
                    val rest = WakeWord.extract(said, Prefs.wake(this@MainActivity))
                    voiceStatus.text = "Heard: \"$said\"\n" + when {
                        rest == null  -> "❌ Wake word not found. Add how it was heard to your wake word list above."
                        rest.isBlank() -> "✅ Wake word detected! (no command after it)"
                        else          -> "✅ Wake word + command: \"$rest\""
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

    // ═══════════════════════════════════════════════════════════════════════════
    // VU meter
    // ═══════════════════════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════════════════════
    // Permission chip refresh
    // ═══════════════════════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════════════════════
    // Lifecycle
    // ═══════════════════════════════════════════════════════════════════════════
    override fun onResume() {
        super.onResume()
        updatePermChips()
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
