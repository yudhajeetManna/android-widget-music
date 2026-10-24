package app.lizmo.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandsTest {

    data class PhraseCase(
        val raw: String,
        val expectControl: Boolean,
        val expectedCategory: String
    )

    private val testCases = listOf(
        // ── 1. Playback: Pause / Stop ──
        PhraseCase("pause", true, "pause"),
        PhraseCase("pause the music", true, "pause"),
        PhraseCase("pause song", true, "pause"),
        PhraseCase("can you please pause the music", true, "pause"),
        PhraseCase("stop", true, "pause/stop"),
        PhraseCase("stop the music", true, "stop"),
        PhraseCase("stop playing", true, "stop"),
        PhraseCase("hold on", true, "pause"),

        // ── 2. Playback: Play / Resume ──
        PhraseCase("play", true, "play"),
        PhraseCase("resume", true, "play"),
        PhraseCase("continue", true, "play"),
        PhraseCase("resume the music", true, "play"),
        PhraseCase("play the song please", true, "play"),
        PhraseCase("keep playing", true, "play"),

        // ── 3. Playback: Next / Skip ──
        PhraseCase("next", true, "next"),
        PhraseCase("skip", true, "next"),
        PhraseCase("skip this song", true, "next"),
        PhraseCase("skip this track", true, "next"),
        PhraseCase("next song please", true, "next"),
        PhraseCase("can you skip this one", true, "next"),

        // ── 4. Playback: Previous / Back ──
        PhraseCase("previous", true, "previous"),
        PhraseCase("go back", true, "previous"),
        PhraseCase("play previous song", true, "previous"),
        PhraseCase("back", true, "previous"),
        PhraseCase("previous track please", true, "previous"),
        PhraseCase("go back to previous track", true, "previous"),

        // ── 5. Playback: Restart ──
        PhraseCase("restart", true, "restart"),
        PhraseCase("restart this song", true, "restart"),
        PhraseCase("play from the beginning", true, "restart"),
        PhraseCase("start over", true, "restart"),
        PhraseCase("replay this track", true, "restart"),

        // ── 6. Volume: Up / Louder ──
        PhraseCase("volume up", true, "volume up"),
        PhraseCase("make it louder", true, "volume up"),
        PhraseCase("turn it up", true, "volume up"),
        PhraseCase("turn up the volume please", true, "volume up"),
        PhraseCase("a little louder", true, "volume up"),
        PhraseCase("increase volume", true, "volume up"),

        // ── 7. Volume: Down / Quieter ──
        PhraseCase("volume down", true, "volume down"),
        PhraseCase("turn the volume down a bit", true, "volume down"),
        PhraseCase("make it quieter", true, "volume down"),
        PhraseCase("lower the volume", true, "volume down"),
        PhraseCase("turn it down please", true, "volume down"),
        PhraseCase("softer", true, "volume down"),

        // ── 8. Volume: Max / Mute / Unmute ──
        PhraseCase("max volume", true, "volume max"),
        PhraseCase("turn it all the way up", true, "volume max"),
        PhraseCase("mute", true, "mute"),
        PhraseCase("mute the music", true, "mute"),
        PhraseCase("silence", true, "mute"),
        PhraseCase("unmute", true, "unmute"),
        PhraseCase("unmute the sound", true, "unmute"),

        // ── 9. Volume: Exact Percentage (Digits & Number Words) ──
        PhraseCase("volume 60", true, "volume exact"),
        PhraseCase("set volume to 80%", true, "volume exact"),
        PhraseCase("volume fifty percent", true, "volume exact"),
        PhraseCase("set volume to twenty percent", true, "volume exact"),
        PhraseCase("turn volume to zero", true, "volume exact"),
        PhraseCase("volume hundred percent", true, "volume exact"),
        PhraseCase("what's the volume", true, "volume query"),
        PhraseCase("volume level", true, "volume query"),

        // ── 10. Seeking ──
        PhraseCase("skip forward 30 seconds", true, "seek forward"),
        PhraseCase("forward 1 minute", true, "seek forward"),
        PhraseCase("rewind 15 seconds", true, "seek backward"),
        PhraseCase("go back 30 seconds", true, "seek backward"),
        PhraseCase("jump to 2 minutes", true, "seek absolute"),

        // ── 11. Shuffle & Repeat ──
        PhraseCase("shuffle", true, "shuffle on"),
        PhraseCase("turn on shuffle", true, "shuffle on"),
        PhraseCase("mix it up", true, "shuffle on"),
        PhraseCase("shuffle off", true, "shuffle off"),
        PhraseCase("repeat this song", true, "repeat one"),
        PhraseCase("loop this", true, "repeat one"),
        PhraseCase("repeat all", true, "repeat all"),
        PhraseCase("repeat off", true, "repeat off"),

        // ── 12. Rating & Info ──
        PhraseCase("like this song", true, "like"),
        PhraseCase("thumbs up", true, "like"),
        PhraseCase("dislike", true, "dislike"),
        PhraseCase("what's playing", true, "now playing"),
        PhraseCase("what song is this", true, "now playing"),
        PhraseCase("which song is this", true, "now playing"),

        // ── 13. Song Searches (MUST NOT BE CONTROLS!) ──
        PhraseCase("play Kesariya", false, "search"),
        PhraseCase("play Blinding Lights by The Weeknd", false, "search"),
        PhraseCase("search Bohemian Rhapsody", false, "search"),
        PhraseCase("find Starboy", false, "search"),
        PhraseCase("play some Taylor Swift", false, "search"),
        PhraseCase("listen to Hotel California", false, "search"),
        PhraseCase("put on Drake", false, "search")
    )

    @Test
    fun testAllPhrases() {
        var passed = 0
        val failed = ArrayList<String>()

        for (tc in testCases) {
            val isCtrl = VoiceCommands.isControl(tc.raw)
            if (isCtrl == tc.expectControl) {
                passed++
            } else {
                failed.add("Phrase: \"${tc.raw}\" expected isControl=${tc.expectControl} ($tc.expectedCategory) but got $isCtrl")
            }
        }

        assertTrue(
            "Failures:\n" + failed.joinToString("\n"),
            failed.isEmpty()
        )
        assertEquals(testCases.size, passed)
        println("Passed all ${testCases.size} voice command test cases!")
    }

    @Test
    fun testNormalizationNumberWords() {
        val normalized = VoiceCommands.normalize("set volume to fifty percent please")
        assertEquals("set volume to 50 percent", normalized)

        val normalized2 = VoiceCommands.normalize("hey lizmo turn the volume down a bit")
        assertEquals("turn the volume down", normalized2)
    }
}
