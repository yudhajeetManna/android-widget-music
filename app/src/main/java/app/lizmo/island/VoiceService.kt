package app.lizmo.island

import ai.picovoice.android.voiceprocessor.VoiceProcessor
import ai.picovoice.android.voiceprocessor.VoiceProcessorFrameListener
import ai.picovoice.eagle.Eagle
import ai.picovoice.eagle.EagleProfile
import ai.picovoice.porcupine.Porcupine
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import ai.picovoice.porcupine.PorcupineManagerErrorCallback
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.File
import kotlin.math.min

/**
 * Foreground Voice Service:
 *  - Porcupine on-device wake-word engine (16 kHz raw PCM processed locally, no audio sent anywhere).
 *  - Eagle speaker verification: checks the wake-word audio against the enrolled voiceprint.
 *  - One-shot SpeechRecognizer for the subsequent command, avoiding mic contention.
 *  - "Mic only while music is playing" mode to remove the status-bar green dot when idle.
 *  - Fallback to basic SpeechRecognizer transcription loop when no Picovoice key is provided.
 */
class VoiceService : Service() {

    enum class State {
        IDLE,
        WAITING_FOR_WAKE,
        WAKE_WORD_DETECTED,
        VERIFYING_SPEAKER,
        LISTENING_FOR_COMMAND,
        EXECUTING_COMMAND,
        RESUMING_WAKE
    }

    companion object {
        const val ACTION_STOP = "app.lizmo.island.STOP_VOICE"
        private const val CHANNEL = "voice"
        private const val NOTIF_ID = 7

        @Volatile var running = false
        @Volatile var currentState = State.IDLE

        fun start(ctx: Context) {
            val i = Intent(ctx, VoiceService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, VoiceService::class.java))
        }

        fun customKeywordFile(ctx: Context): File = File(ctx.filesDir, "wake.ppn")

        fun voiceProfileFile(ctx: Context): File = File(ctx.filesDir, "voice.eagle")

        fun wakeLabel(ctx: Context): String =
            if (customKeywordFile(ctx).exists()) Prefs.wake(ctx).substringBefore(",").trim()
            else Prefs.builtin(ctx).lowercase().replace('_', ' ')
    }

    private val h = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var pico: PorcupineManager? = null
    private var armedUntil = 0L
    private var hardErrors = 0
    private val commandTimeout = Runnable {
        if (currentState == State.LISTENING_FOR_COMMAND) {
            finishCommand("Didn't catch that")
        }
    }

    // ── Speaker Verification (Eagle) ──
    private var eagle: Eagle? = null
    private var profile: EagleProfile? = null
    private val ring = AudioRing(16000 * 3) // 3 seconds buffer
    private val frameListener = VoiceProcessorFrameListener { f -> ring.push(f) }
    private var framesAttached = false
    private var consecutiveFailures = 0

    // ── "Mic only while music is playing" ──
    private var micPaused = false
    private val musicWatch = object : Runnable {
        override fun run() {
            if (!running) return
            if (pico != null && (currentState == State.WAITING_FOR_WAKE || currentState == State.IDLE)) {
                val onlyMusic = Prefs.micMode(this@VoiceService) == "music"
                val isPlaying = Playback.controller(this@VoiceService)?.playbackState?.state == PlaybackState.STATE_PLAYING
                val wantMic = !onlyMusic || isPlaying

                if (wantMic && micPaused) {
                    micPaused = false
                    try {
                        pico?.start()
                        attachFrames()
                        currentState = State.WAITING_FOR_WAKE
                        status("Listening for \"${wakeLabel(this@VoiceService)}\"")
                    } catch (e: Exception) {
                        begin()
                    }
                } else if (!wantMic && !micPaused) {
                    micPaused = true
                    detachFrames()
                    try { pico?.stop() } catch (e: Exception) {}
                    currentState = State.IDLE
                    status("Microphone off — music is paused")
                }
            }
            h.postDelayed(this, 2500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        currentState = State.IDLE
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.setVoiceOn(this, false)
            VoiceBus.log("Voice control stopped")
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("Microphone permission missing")
            return START_NOT_STICKY
        }

        try {
            val n = notification("Starting voice service…")
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            fail("Android blocked microphone foreground service: " + (e.message ?: e.javaClass.simpleName))
            return START_NOT_STICKY
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            fail("No speech recognizer installed on device")
            return START_NOT_STICKY
        }

        hardErrors = 0
        begin()
        return START_STICKY
    }

    private fun fail(msg: String) {
        Prefs.setVoiceOn(this, false)
        VoiceBus.log("⚠ $msg")
        VoiceBus.say(this, msg, 4000)
        stopSelf()
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            val chan = NotificationChannel(CHANNEL, "Lizmo Voice Control", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows status of hands-free wake word listening"
                setShowBadge(false)
            }
            nm.createNotificationChannel(chan)
        }

        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return b.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Lizmo · Say \"${wakeLabel(this)}\"")
            .setContentText(text)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop listening", stopIntent)
            .build()
    }

    private fun status(msg: String, notify: Boolean = true) {
        VoiceBus.log(msg)
        if (notify) {
            try {
                getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(msg))
            } catch (e: Exception) {}
        }
    }

    // ── Engine Setup ──

    private fun begin() {
        h.removeCallbacksAndMessages(null)
        stopPico()
        currentState = State.IDLE

        val key = Prefs.picoKey(this).trim()
        if (key.isNotBlank()) {
            if (startPico()) return
            VoiceBus.say(this, "Porcupine key invalid — using basic voice recognizer", 3500)
        }
        // Fallback: continuous transcription
        h.post { listenBasic() }
    }

    private fun startPico(): Boolean {
        return try {
            val key = Prefs.picoKey(this).trim()
            val ppn = customKeywordFile(this)
            val builder = PorcupineManager.Builder()
                .setAccessKey(key)
                .setSensitivity(0.7f)
                .setErrorCallback(PorcupineManagerErrorCallback { e ->
                    h.post { status("Wake engine error: " + (e.message ?: "Unknown")) }
                })

            if (ppn.exists()) {
                builder.setKeywordPath(ppn.absolutePath)
            } else {
                builder.setKeyword(Porcupine.BuiltInKeyword.valueOf(Prefs.builtin(this)))
            }

            val mgr = builder.build(applicationContext, PorcupineManagerCallback { _ ->
                h.post { onWakeWord() }
            })

            mgr.start()
            pico = mgr
            micPaused = false
            setupLock()
            attachFrames()
            currentState = State.WAITING_FOR_WAKE
            status("Say \"${wakeLabel(this)}\" anytime")

            h.removeCallbacks(musicWatch)
            h.postDelayed(musicWatch, 2500)
            true
        } catch (e: Exception) {
            VoiceBus.log("Porcupine setup failed: " + (e.message ?: e.javaClass.simpleName))
            false
        }
    }

    private fun stopPico() {
        h.removeCallbacks(musicWatch)
        detachFrames()
        closeLock()
        try { pico?.stop() } catch (e: Exception) {}
        try { pico?.delete() } catch (e: Exception) {}
        pico = null
    }

    // ── Wake Word & Verification State Transitions ──

    private fun onWakeWord() {
        if (!running || micPaused) return
        if (currentState != State.WAITING_FOR_WAKE) return

        currentState = State.WAKE_WORD_DETECTED
        VoiceBus.log("Wake word detected! Checking speaker…")

        // 1. Grab snapshot of the spoken wake word for Eagle verification
        val audio = if (eagle != null) ring.snapshot() else null

        // 2. Stop Porcupine & VoiceProcessor to IMMEDIATELY free the microphone
        detachFrames()
        try { pico?.stop() } catch (e: Exception) {}

        if (audio == null || !Prefs.lock(this)) {
            proceedToCommandListening()
        } else {
            currentState = State.VERIFYING_SPEAKER
            verifySpeakerThen(audio) { verified ->
                if (verified) {
                    proceedToCommandListening()
                } else {
                    currentState = State.RESUMING_WAKE
                    resumeWakeEngine()
                }
            }
        }
    }

    private fun proceedToCommandListening() {
        currentState = State.LISTENING_FOR_COMMAND
        playChime()
        VoiceBus.say(this, "🎤 Listening…", 8000)
        VoiceBus.log("Listening for command…")

        // Inter-engine stabilization delay (250ms) to ensure mic hardware is released by Porcupine
        h.postDelayed({ listenForOneCommand() }, 250)
        h.removeCallbacks(commandTimeout)
        h.postDelayed(commandTimeout, 8500)
    }

    private fun resumeWakeEngine() {
        if (pico == null) return
        h.postDelayed({
            if (!running || micPaused) return@postDelayed
            try {
                pico?.start()
                attachFrames()
                ring.clear()
                currentState = State.WAITING_FOR_WAKE
                status("Say \"${wakeLabel(this)}\" anytime")
            } catch (e: Exception) {
                begin()
            }
        }, 350)
    }

    // ── Speaker Lock (Eagle) ──

    private fun setupLock() {
        closeLock()
        if (!Prefs.lock(this) || !voiceProfileFile(this).exists()) return
        try {
            val bytes = voiceProfileFile(this).readBytes()
            profile = EagleProfile(bytes)
            eagle = Eagle.Builder()
                .setAccessKey(Prefs.picoKey(this).trim())
                .setVoiceThreshold(0.2f)
                .build(applicationContext)
        } catch (e: Exception) {
            closeLock()
            VoiceBus.say(this, "Voice lock unavailable: " + (e.message ?: e.javaClass.simpleName), 3500)
        }
    }

    private fun closeLock() {
        try { eagle?.delete() } catch (e: Exception) {}
        try { profile?.delete() } catch (e: Exception) {}
        eagle = null
        profile = null
    }

    private fun attachFrames() {
        if (eagle != null && !framesAttached) {
            try {
                VoiceProcessor.getInstance().addFrameListener(frameListener)
                framesAttached = true
            } catch (e: Exception) {}
        }
    }

    private fun detachFrames() {
        if (framesAttached) {
            try {
                VoiceProcessor.getInstance().removeFrameListener(frameListener)
            } catch (e: Exception) {}
            framesAttached = false
        }
    }

    private fun verifySpeakerThen(audio: ShortArray, cb: (Boolean) -> Unit) {
        val eng = eagle
        val prof = profile
        if (eng == null || prof == null) {
            cb(true)
            return
        }

        Thread {
            var score: Float? = null
            try {
                if (audio.size >= eng.minProcessSamples) {
                    val scores = eng.process(audio, arrayOf(prof))
                    score = scores?.firstOrNull()
                }
            } catch (e: Exception) {}

            h.post {
                val threshold = when (Prefs.lockLevel(this)) {
                    0 -> 0.30f // Relaxed
                    2 -> 0.60f // Strict
                    else -> 0.45f // Balanced
                }

                val ok: Boolean
                if (score == null) {
                    consecutiveFailures++
                    // Owner lockout protection: second inconclusive attempt is let through
                    ok = consecutiveFailures >= 2
                    VoiceBus.log("Voice check: insufficient voice samples" + if (ok) " (fallback allow)" else "")
                    if (!ok) VoiceBus.say(this, "Say it again a bit louder", 2000)
                } else {
                    ok = score >= threshold
                    if (ok) {
                        consecutiveFailures = 0
                        VoiceBus.log("Voice verified: %.2f ✓ (owner)".format(score))
                    } else {
                        consecutiveFailures++
                        VoiceBus.log("Voice rejected: %.2f ✗ (threshold %.2f)".format(score, threshold))
                        VoiceBus.say(this, "🔒 Voice not recognised", 2000)
                    }
                }
                cb(ok)
            }
        }.start()
    }

    private fun playChime() {
        try {
            val t = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 75)
            t.startTone(ToneGenerator.TONE_PROP_ACK, 160)
            h.postDelayed({ t.release() }, 400)
        } catch (e: Exception) {}
    }

    // ── Command Listening (One-Shot) ──

    private fun listenForOneCommand() {
        if (!running || currentState != State.LISTENING_FOR_COMMAND) return
        try {
            resetRecognizer()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Prefs.voiceLang(this@VoiceService))
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            recognizer().startListening(intent)
        } catch (e: Exception) {
            finishCommand("Couldn't start voice recognition")
        }
    }

    private fun finishCommand(feedback: String?) {
        h.removeCallbacks(commandTimeout)
        currentState = State.RESUMING_WAKE
        resetRecognizer()
        if (feedback != null) {
            VoiceBus.say(this, feedback)
            VoiceBus.log(feedback)
        }
        resumeWakeEngine()
    }

    private fun recognizer(): SpeechRecognizer {
        val r = rec
        if (r != null) return r
        val n = SpeechRecognizer.createSpeechRecognizer(this)
        n.setRecognitionListener(listener)
        rec = n
        return n
    }

    private fun resetRecognizer() {
        try { rec?.destroy() } catch (e: Exception) {}
        rec = null
    }

    // ── Basic Mode (Fallback when no key is set) ──

    private fun listenBasic() {
        if (!running) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Prefs.voiceLang(this@VoiceService))
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            recognizer().startListening(intent)
            status("Listening for \"${wakeLabel(this)}\"…", notify = false)
        } catch (e: Exception) {
            hardError("Couldn't start: " + (e.message ?: e.javaClass.simpleName))
        }
    }

    private fun restartBasic(delayMs: Long) {
        if (!running) return
        h.removeCallbacksAndMessages(null)
        h.postDelayed({ listenBasic() }, delayMs)
    }

    private fun hardError(reason: String) {
        hardErrors++
        resetRecognizer()
        if (hardErrors >= 6) {
            fail("Voice stopped: $reason")
            return
        }
        status("Voice issue: $reason — retrying ($hardErrors/6)")
        restartBasic(min(10_000L, 1000L * hardErrors * hardErrors))
    }

    // ── Speech Recognizer Listener ──

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            VoiceBus.rmsListener?.invoke(rmsdB)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (currentState == State.LISTENING_FOR_COMMAND) return
            val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!p.isNullOrBlank()) {
                status("Hearing: \"$p\"", notify = false)
            }
        }

        override fun onError(error: Int) {
            if (currentState == State.LISTENING_FOR_COMMAND) {
                val msg = if (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH)
                    "Didn't catch that" else speechErrorName(error)
                finishCommand(msg)
                return
            }

            when (error) {
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> {
                    hardErrors = 0
                    restartBasic(100)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    fail("Microphone permission missing")
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    resetRecognizer()
                    restartBasic(1000)
                }
                else -> hardError(speechErrorName(error))
            }
        }

        override fun onResults(results: Bundle?) {
            hardErrors = 0
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()

            if (currentState == State.LISTENING_FOR_COMMAND) {
                val said = list.firstOrNull()
                if (said.isNullOrBlank()) {
                    finishCommand("Didn't catch that")
                } else {
                    currentState = State.EXECUTING_COMMAND
                    status("Command: \"$said\"")
                    val feedback = VoiceCommands.run(this@VoiceService, said)
                    finishCommand(feedback)
                }
                return
            }

            // Fallback mode result handling
            handleBasicResult(list)
            restartBasic(if (SystemClock.elapsedRealtime() < armedUntil) 100 else 200)
        }
    }

    private fun handleBasicResult(candidates: List<String>) {
        if (candidates.isEmpty()) return
        val now = SystemClock.elapsedRealtime()

        if (now < armedUntil) {
            armedUntil = 0L
            status("Command: " + candidates[0])
            val feedback = VoiceCommands.run(this, candidates[0])
            VoiceBus.say(this, feedback)
            return
        }

        val wake = Prefs.wake(this)
        for (c in candidates) {
            val rest = WakeWord.extract(c, wake) ?: continue
            if (rest.isBlank()) {
                armedUntil = now + 8000
                status("Heard wake word — say your command")
                playChime()
                VoiceBus.say(this, "🎤 Listening…", 8000)
            } else {
                status("Command: $rest")
                val feedback = VoiceCommands.run(this, rest)
                VoiceBus.say(this, feedback)
            }
            return
        }
        status("Heard: \"${candidates[0]}\" (no wake word)", notify = false)
    }

    override fun onDestroy() {
        running = false
        currentState = State.IDLE
        h.removeCallbacksAndMessages(null)
        stopPico()
        resetRecognizer()
        VoiceHud.dismiss()
        super.onDestroy()
    }
}
