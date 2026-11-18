package dev.kyralabs.pingogram.core.data.transcription

import android.Manifest
import android.os.Bundle
import android.os.Looper
import android.speech.RecognitionSupport
import android.speech.SpeechRecognizer
import dev.kyralabs.pingogram.core.domain.model.TranscriptionResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSpeechRecognizer

/**
 * [AndroidVoiceTranscriber] on Robolectric's speech recognizer: the language check and the transcript share one
 * recognizer, kept until the transcript comes, since destroying a recognizer cancels another one's new session.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AndroidVoiceTranscriberTest {
    private val context = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        ShadowSpeechRecognizer.setIsOnDeviceRecognitionAvailable(true)
        shadowOf(context).grantPermissions(Manifest.permission.RECORD_AUDIO)
    }

    @Test
    fun `checks the languages and transcribes with one recognizer`() =
        runTest {
            val transcriber =
                AndroidVoiceTranscriber(context, StandardTestDispatcher(testScheduler)) { _, output ->
                    output.close()
                    true
                }
            // In the background scope, so a failed assertion ends the test instead of waiting for the main thread.
            val result = backgroundScope.async { transcriber.transcribe(path = "voice.ogg", durationSeconds = 3) }
            settle()
            val recognizer = ShadowSpeechRecognizer.getLatestSpeechRecognizer()
            recognizer.shadow.triggerSupportResult(
                RecognitionSupport.Builder().setInstalledOnDeviceLanguages(listOf("en-US")).build(),
            )
            settle()

            assertSame(recognizer, ShadowSpeechRecognizer.getLatestSpeechRecognizer())
            assertFalse(recognizer.shadow.isDestroyed)

            recognizer.shadow.triggerOnResults(
                Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("hello")) },
            )
            settle()

            assertEquals(TranscriptionResult.Recognized("hello"), result.await())
            assertTrue(recognizer.shadow.isDestroyed)
        }

    /** Runs the work waiting on the test dispatcher and on the main thread, where the recognizer is used. */
    private fun TestScope.settle() {
        repeat(SETTLE_ROUNDS) {
            runCurrent()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private val SpeechRecognizer.shadow: ShadowSpeechRecognizer get() = Shadow.extract(this)

    private companion object {
        const val SETTLE_ROUNDS = 3
    }
}
