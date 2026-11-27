package dev.kyralabs.pingogram.core.data.speech

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/** [offlineVoice]: only installed voices that need no network, of the device's language, the closest first. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OfflineVoiceTest {
    private fun voice(
        name: String,
        locale: Locale = Locale.US,
        quality: Int = Voice.QUALITY_NORMAL,
        network: Boolean = false,
        features: Set<String> = emptySet(),
    ) = Voice(name, locale, quality, Voice.LATENCY_NORMAL, network, features)

    @Test
    fun `a voice that needs the network is never chosen, however good`() {
        val local = voice("local")
        val online = voice("online", quality = Voice.QUALITY_VERY_HIGH, network = true)

        assertEquals(local, offlineVoice(listOf(online, local), Locale.US))
    }

    @Test
    fun `a voice still to download is never chosen`() {
        val missing = voice("missing", features = setOf(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED))

        assertNull(offlineVoice(listOf(missing), Locale.US))
    }

    @Test
    fun `without a voice of the device's language there is none`() =
        assertNull(offlineVoice(listOf(voice("french", Locale.FRANCE)), Locale.US))

    @Test
    fun `the device's country comes first, then quality`() {
        val british = voice("british", Locale.UK, quality = Voice.QUALITY_VERY_HIGH)
        val american = voice("american", Locale.US)
        val betterAmerican = voice("american-high", Locale.US, quality = Voice.QUALITY_HIGH)

        assertEquals(betterAmerican, offlineVoice(listOf(british, american, betterAmerican), Locale.US))
        assertEquals(british, offlineVoice(listOf(british, american), Locale.forLanguageTag("en-IE")))
    }

    @Test
    fun `a three-letter language code is the same language`() {
        val english =
            voice(
                "eng",
                Locale
                    .Builder()
                    .setLanguage("eng")
                    .setRegion("US")
                    .build(),
            )

        assertEquals(english, offlineVoice(listOf(english), Locale.US))
    }
}
