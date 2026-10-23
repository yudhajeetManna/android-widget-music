package app.lizmo.island

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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlin.math.min

/**
 * Optional always-on wake word ("lizmo ..."), built on the phone's speech recognizer.
 * Every state and error is reported to VoiceBus.log so the Lizmo screen shows what it hears.
 */
class VoiceService : Service() {
    companion object {
        const val ACTION_STOP = "app.lizmo.island.STOP_VOICE"
        private const val CHANNEL = "voice"
        private const val NOTIF_ID = 7

        @Volatile
        var running = false

        fun start(ctx: Context) {
            val i = Intent(ctx, VoiceService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, VoiceService::class.java))
        }
    }

    private val h = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var armedUntil = 0L
    private var hardErrors = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Prefs.setVoiceOn(this, false)
            VoiceBus.log("Voice control is off")
            stopSelf()
            return START_NOT_STICKY
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            fail("Microphone permission is off. Allow it in Settings > Apps > Lizmo > Permissions.")
            return START_NOT_STICKY
        }
        try {
            val n = notification("Starting\u2026")
            if (Build.VERSION.SDK_INT >= 30) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (e: Exception) {
            fail("Android blocked the microphone service: " + (e.message ?: e.javaClass.simpleName))
            return START_NOT_STICKY
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            fail("No speech recognizer found. Install/enable the Google app and set it as the voice input.")
            return START_NOT_STICKY
        }
        hardErrors = 0
        h.removeCallbacksAndMessages(null)
        h.post { listen() }
        return START_STICKY
    }

    private fun fail(msg: String) {
        Prefs.setVoiceOn(this, false)
        VoiceBus.log("\u26A0 $msg")
        VoiceBus.say(this, msg)
        stopSelf()
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Voice control", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        return b.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Lizmo \u00B7 say \"" + Prefs.wake(this).substringBefore(",").trim() + "\"")
            .setContentText(text)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop listening", stop)
            .build()
    }

    private fun status(msg: String, notify: Boolean = true) {
        VoiceBus.log(msg)
        if (notify) {
            try { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(msg)) } catch (e: Exception) {}
        }
    }

    // ---------- listening loop ----------
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

    private fun listen() {
        if (!running) return
        try {
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Prefs.voiceLang(this@VoiceService))
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            }
            recognizer().startListening(i)
            status("Listening for \"" + Prefs.wake(this).substringBefore(",").trim() + "\"\u2026", notify = false)
        } catch (e: Exception) {
            hardError("couldn't start (" + (e.message ?: e.javaClass.simpleName) + ")")
        }
    }

    private fun restart(delayMs: Long) {
        if (!running) return
        h.removeCallbacksAndMessages(null)
        h.postDelayed({ listen() }, delayMs)
    }

    private fun hardError(reason: String) {
        hardErrors++
        resetRecognizer()
        if (hardErrors >= 6) {
            fail("Voice stopped: $reason")
            return
        }
        status("Voice problem: $reason \u2014 retrying (" + hardErrors + "/6)")
        restart(min(10_000L, 1000L * hardErrors * hardErrors))
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            if (!p.isNullOrBlank()) status("Hearing: $p", notify = false)
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_NO_MATCH -> {
                    hardErrors = 0
                    restart(150)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    fail("Microphone permission is off. Allow it in Settings > Apps > Lizmo > Permissions.")
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> {
                    resetRecognizer()
                    restart(1200)
                }
                else -> hardError(speechErrorName(error))
            }
        }

        override fun onResults(results: Bundle?) {
            hardErrors = 0
            val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
            handle(list)
            restart(if (SystemClock.elapsedRealtime() < armedUntil) 100 else 200)
        }
    }

    private fun handle(candidates: List<String>) {
        if (candidates.isEmpty()) return
        val now = SystemClock.elapsedRealtime()
        // previous utterance was only the wake word -> this whole utterance is the command
        if (now < armedUntil) {
            armedUntil = 0L
            status("Command: " + candidates[0])
            exec(candidates[0])
            return
        }
        val wake = Prefs.wake(this)
        for (c in candidates) {
            val rest = WakeWord.extract(c, wake) ?: continue
            if (rest.isBlank()) {
                armedUntil = now + 8000
                status("Heard the wake word \u2014 say your command")
                VoiceBus.say(this, "\uD83C\uDFA4 Listening\u2026")
            } else {
                status("Command: $rest")
                exec(rest)
            }
            return
        }
        // wake word not found: show what was heard so a mishearing can be added as an alias
        status("Heard \"" + candidates[0] + "\" (no wake word)", notify = false)
    }

    private fun exec(cmd: String) {
        VoiceBus.say(this, VoiceCommands.run(this, cmd))
    }

    override fun onDestroy() {
        running = false
        h.removeCallbacksAndMessages(null)
        resetRecognizer()
        super.onDestroy()
    }
}
