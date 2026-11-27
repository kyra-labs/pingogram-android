package dev.kyralabs.pingogram.core.data.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.domain.model.SpeechStart
import dev.kyralabs.pingogram.core.domain.repository.TextSpeaker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.MissingResourceException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * [TextSpeaker] on Android's text-to-speech (`REQUIREMENTS.md` FR-X26), with the engine the user chose in Android's
 * settings. Only an installed voice that needs no network connection is used ([offlineVoice]), never the engine's
 * default, so what is read stays on the phone; without one, [SpeechStart.NoOfflineVoice]. The engine is started, used
 * and released on the main thread, as it asks, and waited for at most [START_TIMEOUT_MILLIS]. Texts are never logged.
 */
@Singleton
class AndroidTextSpeaker
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : TextSpeaker {
        override suspend fun open(): SpeechStart =
            withContext(Dispatchers.Main) {
                val engine = withTimeoutOrNull(START_TIMEOUT_MILLIS) { start() }
                val voice = engine?.let { offlineVoice(it.voices.orEmpty(), Locale.getDefault()) }
                when {
                    engine == null -> {
                        SpeechStart.Unavailable
                    }

                    voice != null && engine.setVoice(voice) == TextToSpeech.SUCCESS -> {
                        SpeechStart.Ready(TextToSpeechSession(engine))
                    }

                    else -> {
                        engine.shutdown()
                        SpeechStart.NoOfflineVoice
                    }
                }
            }

        /** The engine once it has started; null if there is none or it failed. */
        private suspend fun start(): TextToSpeech? {
            var engine: TextToSpeech? = null
            val started =
                try {
                    suspendCancellableCoroutine<Boolean> { continuation ->
                        engine =
                            TextToSpeech(context) { status ->
                                if (continuation.isActive) continuation.resume(status == TextToSpeech.SUCCESS)
                            }
                    }
                } catch (cancelled: CancellationException) {
                    engine?.shutdown()
                    throw cancelled
                }
            if (!started) engine?.shutdown()
            return engine.takeIf { started }
        }

        private companion object {
            const val START_TIMEOUT_MILLIS = 5_000L
        }
    }

/**
 * The voice to read in [locale]'s language: installed, needing no network connection, of the same language; one of
 * the same country first, then the best quality, then the quickest, then by name so the choice does not change between
 * runs. Null when there is none.
 */
internal fun offlineVoice(
    voices: Collection<Voice>,
    locale: Locale,
): Voice? =
    voices
        .filter { voice ->
            !voice.isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features.orEmpty() &&
                voice.locale.speaks(locale)
        }.maxWithOrNull(
            compareBy<Voice> { it.locale.country.equals(locale.country, ignoreCase = true) }
                .thenBy { it.quality }
                .thenByDescending { it.latency }
                .thenByDescending { it.name },
        )

/** Whether this is [other]'s language, whichever code (two letters or three) either uses. */
private fun Locale.speaks(other: Locale): Boolean =
    language.equals(other.language, ignoreCase = true) ||
        threeLetterLanguage()?.equals(other.threeLetterLanguage(), ignoreCase = true) == true

private fun Locale.threeLetterLanguage(): String? =
    try {
        isO3Language.ifEmpty { null }
    } catch (_: MissingResourceException) {
        null
    }
