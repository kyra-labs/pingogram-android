package dev.kyralabs.pingogram.core.data.transcription

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The audio handed the recognizer: one channel, 16 kHz, a continuous stream across chunks. */
class SpeechPcmTest {
    private fun ByteArray.samples(): List<Int> =
        (indices step 2).map { (this[it].toInt() and 0xFF) or (this[it + 1].toInt() shl 8) }

    @Test
    fun `16 kHz mono passes through unchanged`() {
        val samples = shortArrayOf(0, 1000, -1000, 32767, -32768)
        assertEquals(samples.map(Short::toInt), SpeechPcm(16_000, 1).convert(samples).samples())
    }

    @Test
    fun `channels are averaged into one`() {
        val stereo = shortArrayOf(100, 300, -200, 200)
        assertEquals(listOf(200, 0), SpeechPcm(16_000, 2).convert(stereo).samples())
    }

    @Test
    fun `48 kHz keeps one sample in three, across chunks`() {
        val pcm = SpeechPcm(48_000, 1)
        val first = pcm.convert(ShortArray(6) { (it * 10).toShort() }).samples()
        val second = pcm.convert(ShortArray(6) { ((it + 6) * 10).toShort() }).samples()
        assertEquals(listOf(0, 30, 60, 90), first + second)
    }

    @Test
    fun `8 kHz is interpolated between samples`() {
        val pcm = SpeechPcm(8_000, 1)
        val out = pcm.convert(shortArrayOf(0, 100)).samples() + pcm.convert(shortArrayOf(200)).samples()
        assertEquals(listOf(0, 50, 100, 150, 200), out)
    }

    @Test
    fun `the speech language follows the user's languages, then any that is ready`() {
        val ready = listOf("en-US", "hi-IN")
        assertEquals("hi-IN", languageFor(ready, listOf("hi-IN", "en-GB")))
        assertEquals("en-US", languageFor(ready, listOf("en-IN")))
        assertEquals("en-US", languageFor(ready, listOf("ta-IN")))
        assertNull(languageFor(emptyList(), listOf("en-US")))
    }
}
