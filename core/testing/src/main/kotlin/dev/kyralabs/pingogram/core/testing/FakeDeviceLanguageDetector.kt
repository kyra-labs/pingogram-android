package dev.kyralabs.pingogram.core.testing

import dev.kyralabs.pingogram.core.domain.model.LanguageDetection
import dev.kyralabs.pingogram.core.domain.repository.DeviceLanguageDetector
import kotlinx.coroutines.CompletableDeferred

/**
 * [DeviceLanguageDetector] without Android: texts are in the language [languages] gives them (by the whole text, or
 * else [default]), the system's languages are [preferred]; [canDetect] false detects nothing, as before Android 10.
 * [detected] records every text asked about; while [held] is set, each answer waits for it, as a slow device would.
 */
class FakeDeviceLanguageDetector(
    var preferred: List<String> = listOf("en-US"),
    override var canDetect: Boolean = true,
    var default: LanguageDetection = LanguageDetection.Undetermined,
) : DeviceLanguageDetector {
    /** The language of each text, by the whole text. */
    val languages = mutableMapOf<String, LanguageDetection>()

    /** Every text [detect] was asked about, in order. */
    val detected = mutableListOf<String>()

    /** What each answer waits for, while set. */
    var held: CompletableDeferred<Unit>? = null

    /** Makes [texts] come out as [languageTag]. */
    fun inLanguage(
        languageTag: String,
        vararg texts: String,
    ) {
        texts.forEach { languages[it] = LanguageDetection.Detected(languageTag) }
    }

    override suspend fun detect(text: String): LanguageDetection {
        detected += text
        held?.await()
        return if (canDetect) languages[text] ?: default else LanguageDetection.Unavailable
    }

    override fun preferredLanguages(): List<String> = preferred
}
