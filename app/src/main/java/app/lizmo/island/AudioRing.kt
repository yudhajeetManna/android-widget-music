package app.lizmo.island

import kotlin.math.min

/**
 * Thread-safe ring buffer of 16 kHz 16-bit PCM audio samples.
 * Keeps the most recent [size] samples (e.g. 16000 * 3 = 3 seconds)
 * so that when Porcupine triggers a wake-word event, the wake-word audio
 * can be extracted immediately for Picovoice Eagle speaker verification.
 */
class AudioRing(private val size: Int) {
    private val buf = ShortArray(size)
    private var pos = 0
    private var filled = 0

    @Synchronized
    fun push(frame: ShortArray) {
        for (s in frame) {
            buf[pos] = s
            pos = (pos + 1) % size
        }
        filled = min(size, filled + frame.size)
    }

    @Synchronized
    fun snapshot(): ShortArray {
        val out = ShortArray(filled)
        val start = (pos - filled + size) % size
        for (i in 0 until filled) {
            out[i] = buf[(start + i) % size]
        }
        return out
    }

    @Synchronized
    fun clear() {
        pos = 0
        filled = 0
    }
}
