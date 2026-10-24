package app.lizmo.island

import ai.picovoice.eagle.EagleProfiler
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.max

/**
 * Teaches Lizmo the owner's voice via Picovoice Eagle:
 *  - 3 different spoken sentences recorded on-device
 *  - Progress feedback (percentage towards complete voiceprint)
 *  - Save, Retry, Strictness selection, and Delete voice profile
 */
class EnrollActivity : Activity() {

    private val h = Handler(Looper.getMainLooper())
    private var profiler: EagleProfiler? = null
    private var recording = false
    private var takes = 0
    private var pct = 0f
    private var saved = false

    private lateinit var stepTv: TextView
    private lateinit var promptTv: TextView
    private lateinit var progressTv: TextView
    private lateinit var progressBar: FrameLayout
    private lateinit var progressFill: FrameLayout
    private lateinit var statusTv: TextView
    private lateinit var recordBtn: Button
    private lateinit var retryBtn: Button
    private lateinit var deleteBtn: Button
    private lateinit var strictnessContainer: LinearLayout

    private val prompts = listOf(
        "\u201CLizmo, play some music\u201D",
        "\u201CHey Lizmo, turn the volume down\u201D",
        "\u201CLizmo, search for my favourite song\u201D",
        "\u201CLizmo, pause the music\u201D",
        "\u201CLizmo, play the next song\u201D",
        "\u201CLizmo, start it again from the beginning\u201D",
        "\u201CLizmo, make it louder\u201D",
        "\u201CLizmo, skip this one\u201D"
    )

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.parseColor("#0B0B0F"))
            setPadding(dp(20), dp(40), dp(20), dp(32))
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0B0B0F"))
            addView(root)
        }

        fun tv(size: Float, bold: Boolean = false, a: Float = 1f, color: Int = Color.WHITE) = TextView(this).apply {
            setTextColor(color)
            textSize = size
            alpha = a
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

        root.addView(tv(24f, bold = true).also {
            it.text = "\uD83C\uDFA4  Teach Lizmo Your Voice"
            it.setPadding(0, 0, 0, dp(8))
        })

        root.addView(tv(13f, a = 0.7f).also {
            it.text = "Record 3 different sentences in a quiet room, speaking in your normal tone. Once enrolled, Lizmo only responds to you."
            it.setPadding(0, 0, 0, dp(24))
        })

        stepTv = tv(13f, bold = true, color = Color.parseColor("#C4B5FD")).also {
            it.setPadding(0, 0, 0, dp(8))
        }
        root.addView(stepTv)

        promptTv = tv(20f, bold = true).also {
            it.setBackgroundColor(Color.parseColor("#151522"))
            it.setPadding(dp(16), dp(16), dp(16), dp(16))
            it.background = GradientDrawable().apply {
                setColor(Color.parseColor("#151522"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#33FFFFFF"))
            }
        }
        root.addView(promptTv, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(20) })

        // Progress bar
        progressBar = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1AFFFFFF"))
                cornerRadius = dp(8).toFloat()
            }
        }
        progressFill = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#7C5CFC"))
                cornerRadius = dp(8).toFloat()
            }
        }
        progressBar.addView(progressFill, FrameLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT))
        root.addView(progressBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(12)
        ).apply { bottomMargin = dp(8) })

        progressTv = tv(14f, bold = true, a = 0.9f)
        root.addView(progressTv)

        statusTv = tv(13f, a = 0.8f, color = Color.parseColor("#E0E0E0")).also {
            it.setPadding(0, dp(12), 0, dp(20))
        }
        root.addView(statusTv)

        // Record Button
        recordBtn = Button(this).apply {
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#7C5CFC"))
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(20), dp(14), dp(20), dp(14))
            setOnClickListener {
                if (saved) finish() else record()
            }
        }
        root.addView(recordBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // Retry Button
        retryBtn = Button(this).apply {
            text = "\u21BA  Retry Current Sample"
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.parseColor("#C4B5FD"))
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), Color.parseColor("#337C5CFC"))
                cornerRadius = dp(10).toFloat()
            }
            visibility = View.GONE
            setOnClickListener {
                resetCurrentProfiler()
                takes = 0
                pct = 0f
                saved = false
                refresh()
                record()
            }
        }
        root.addView(retryBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // Strictness Selector
        strictnessContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(12))
        }
        val strictTitle = tv(13f, bold = true).apply {
            text = "Voice Strictness Setting:"
            gravity = Gravity.START
            setPadding(0, 0, 0, dp(6))
        }
        strictnessContainer.addView(strictTitle)

        val strictRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val levels = listOf("Relaxed", "Balanced", "Strict")
        levels.forEachIndexed { idx, name ->
            val cur = Prefs.lockLevel(this)
            val btn = Button(this).apply {
                text = name
                isAllCaps = false
                textSize = 12f
                setTextColor(if (cur == idx) Color.WHITE else Color.parseColor("#99FFFFFF"))
                background = GradientDrawable().apply {
                    setColor(if (cur == idx) Color.parseColor("#337C5CFC") else Color.parseColor("#0DFFFFFF"))
                    cornerRadius = dp(8).toFloat()
                    setStroke(dp(1), if (cur == idx) Color.parseColor("#7C5CFC") else Color.parseColor("#22FFFFFF"))
                }
                setOnClickListener {
                    Prefs.setLockLevel(this@EnrollActivity, idx)
                    refreshStrictness(strictRow)
                }
            }
            strictRow.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(3), 0, dp(3), 0)
            })
        }
        strictnessContainer.addView(strictRow)
        root.addView(strictnessContainer)

        // Delete profile button
        val profileExists = VoiceService.voiceProfileFile(this).exists()
        deleteBtn = Button(this).apply {
            text = "\uD83D\uDDD1\uFE0F  Delete Enrolled Voice"
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.parseColor("#EF4444"))
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                setStroke(dp(1), Color.parseColor("#44EF4444"))
                cornerRadius = dp(10).toFloat()
            }
            visibility = if (profileExists) View.VISIBLE else View.GONE
            setOnClickListener {
                deleteVoiceProfile()
            }
        }
        root.addView(deleteBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // Cancel / Back button
        val closeBtn = Button(this).apply {
            text = "Close"
            isAllCaps = false
            textSize = 13f
            setTextColor(Color.parseColor("#99FFFFFF"))
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(10).toFloat()
            }
            setOnClickListener { finish() }
        }
        root.addView(closeBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        setContentView(scroll)
        refresh()
    }

    private fun refreshStrictness(row: LinearLayout) {
        val cur = Prefs.lockLevel(this)
        for (i in 0 until row.childCount) {
            val b = row.getChildAt(i) as? Button ?: continue
            val sel = (i == cur)
            b.setTextColor(if (sel) Color.WHITE else Color.parseColor("#99FFFFFF"))
            (b.background as? GradientDrawable)?.apply {
                setColor(if (sel) Color.parseColor("#337C5CFC") else Color.parseColor("#0DFFFFFF"))
                setStroke(dp(1), if (sel) Color.parseColor("#7C5CFC") else Color.parseColor("#22FFFFFF"))
            }
        }
    }

    private fun updateProgress(percent: Float) {
        pct = percent
        val clamped = percent.coerceIn(0f, 100f)
        progressTv.text = "Voice Profile: " + clamped.toInt() + "%"
        progressBar.post {
            val totalW = progressBar.width
            val fillW = (totalW * (clamped / 100f)).toInt()
            progressFill.layoutParams = FrameLayout.LayoutParams(fillW, FrameLayout.LayoutParams.MATCH_PARENT)
        }
    }

    private fun refresh() {
        val n = takes + 1
        stepTv.text = if (n <= 3) "Sample $n of 3" else "Extra Sample $n (a little more voice audio needed)"
        promptTv.text = "Say clearly:\n" + prompts[takes % prompts.size]
        updateProgress(pct)
        recordBtn.text = "\uD83C\uDFA4  Record (Speak for ~6 seconds)"
    }

    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 21 && hasMic()) {
            record()
        } else if (requestCode == 21) {
            statusTv.text = "Microphone permission is required to learn your voice."
        }
    }

    private fun resetCurrentProfiler() {
        try { profiler?.delete() } catch (e: Exception) {}
        profiler = null
    }

    private fun record() {
        if (recording) return
        if (!hasMic()) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 21)
            return
        }
        val key = Prefs.picoKey(this).trim()
        if (key.isEmpty()) {
            statusTv.text = "\u26A0 Add your Picovoice AccessKey in Settings first."
            return
        }

        // Release the microphone if VoiceService is currently active
        VoiceService.stop(this)

        try {
            if (profiler == null) {
                profiler = EagleProfiler.Builder().setAccessKey(key).build(applicationContext)
            }
        } catch (e: Exception) {
            statusTv.text = "Voice engine error: " + (e.message ?: e.javaClass.simpleName)
            return
        }

        val prof = profiler ?: return
        recording = true
        recordBtn.isEnabled = false
        retryBtn.visibility = View.VISIBLE
        statusTv.text = "\uD83C\uDF99\uFE0F Recording\u2026 speak the sentence above clearly"

        Thread {
            var result = pct
            var err: String? = null
            var rec: AudioRecord? = null
            try {
                val sr = prof.sampleRate
                val fl = prof.frameLength
                val minBuf = AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                rec = AudioRecord(
                    MediaRecorder.AudioSource.MIC, sr, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, max(minBuf, fl * 8)
                )
                if (rec.state != AudioRecord.STATE_INITIALIZED) {
                    throw IllegalStateException("Microphone is busy. Please wait a second and retry.")
                }
                rec.startRecording()
                val frame = ShortArray(fl)
                val end = SystemClock.elapsedRealtime() + 6500L
                while (SystemClock.elapsedRealtime() < end) {
                    var got = 0
                    while (got < fl) {
                        val n = rec.read(frame, got, fl - got)
                        if (n <= 0) break
                        got += n
                    }
                    if (got < fl) break
                    result = prof.enroll(frame)
                    val currentPct = result
                    h.post { updateProgress(currentPct) }
                }
            } catch (e: Exception) {
                err = e.message ?: e.javaClass.simpleName
            } finally {
                try { rec?.stop() } catch (e: Exception) {}
                try { rec?.release() } catch (e: Exception) {}
            }

            h.post {
                recording = false
                recordBtn.isEnabled = true
                if (err != null) {
                    statusTv.text = "Recording failed: $err"
                } else {
                    onTake(result)
                }
            }
        }.start()
    }

    private fun onTake(p: Float) {
        takes++
        pct = p
        updateProgress(p)
        when {
            takes >= 3 && p >= 100f -> save()
            takes >= 8 -> {
                statusTv.text = "Couldn't complete voice profile. Try again in a quieter room."
                refresh()
            }
            takes < 3 -> {
                statusTv.text = "\u2713 Sample $takes captured! Now record the next sentence."
                refresh()
            }
            else -> {
                statusTv.text = "Almost done! One more sample needed, speak clearly and slightly louder."
                refresh()
            }
        }
    }

    private fun save() {
        try {
            val p = profiler?.export() ?: throw IllegalStateException("No voice profile generated")
            VoiceService.voiceProfileFile(this).writeBytes(p.bytes)
            Prefs.setLock(this, true)
            saved = true
            stepTv.text = "\u2705 Enrollment Complete"
            promptTv.text = "Lizmo now only responds to your voice."
            updateProgress(100f)
            statusTv.text = "Voice profile saved! You can adjust strictness or delete your voice anytime."
            recordBtn.text = "\u2713 Finish"
            retryBtn.visibility = View.GONE
            deleteBtn.visibility = View.VISIBLE
        } catch (e: Exception) {
            statusTv.text = "Couldn't save voice profile: " + (e.message ?: e.javaClass.simpleName)
        }
    }

    private fun deleteVoiceProfile() {
        try {
            val file = VoiceService.voiceProfileFile(this)
            if (file.exists()) file.delete()
            Prefs.setLock(this, false)
            resetCurrentProfiler()
            takes = 0
            pct = 0f
            saved = false
            deleteBtn.visibility = View.GONE
            statusTv.text = "Enrolled voice profile deleted. Lizmo will respond to anyone's voice until you enroll again."
            refresh()
        } catch (e: Exception) {
            statusTv.text = "Failed to delete voice profile: " + e.message
        }
    }

    override fun onPause() {
        if (isFinishing && Prefs.voiceOn(this) && hasMic() && !VoiceService.running) {
            try { VoiceService.start(this) } catch (e: Exception) {}
        }
        super.onPause()
    }

    override fun onDestroy() {
        resetCurrentProfiler()
        super.onDestroy()
    }
}
