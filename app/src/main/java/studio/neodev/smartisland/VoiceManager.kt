package studio.neodev.smartisland

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class VoiceManager(
    private val ctx: Context,
    private val onResult: (command: String, peakRmsDb: Float) -> Unit,
    private val onRms: (rmsdB: Float) -> Unit,
    private val onStateChange: (listening: Boolean, message: String) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var peakRms = -100f

    val listening: Boolean
        get() = isListening

    fun start() {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            onStateChange(false, "Speech recognition not available")
            return
        }

        stop()

        try {
            peakRms = -100f
            isListening = true
            onStateChange(true, "Listening...")

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }

            recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        onStateChange(true, "Speak a command...")
                    }

                    override fun onBeginningOfSpeech() {
                        onStateChange(true, "Listening...")
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        if (rmsdB > peakRms) peakRms = rmsdB
                        onRms(rmsdB)
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        onStateChange(false, "Processing...")
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        val msg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "No command detected"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Timeout - no speech"
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Mic permission needed"
                            SpeechRecognizer.ERROR_NETWORK -> "Network required"
                            else -> "Voice recognition cancelled"
                        }
                        onStateChange(false, msg)
                        cleanup()
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val top = matches?.firstOrNull() ?: ""
                        cleanup()
                        if (top.isNotBlank()) {
                            onResult(top, peakRms)
                        } else {
                            onStateChange(false, "No speech recognized")
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val partial = matches?.firstOrNull()
                        if (!partial.isNullOrBlank()) {
                            onStateChange(true, partial)
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
                startListening(intent)
            }
        } catch (e: Exception) {
            isListening = false
            onStateChange(false, "Failed to start microphone: ${e.message}")
            cleanup()
        }
    }

    fun stop() {
        if (!isListening) return
        isListening = false
        try {
            recognizer?.stopListening()
            recognizer?.cancel()
        } catch (e: Exception) {}
        cleanup()
        onStateChange(false, "Voice cancelled")
    }

    private fun cleanup() {
        try {
            recognizer?.destroy()
        } catch (e: Exception) {}
        recognizer = null
    }

    fun destroy() {
        stop()
    }
}
