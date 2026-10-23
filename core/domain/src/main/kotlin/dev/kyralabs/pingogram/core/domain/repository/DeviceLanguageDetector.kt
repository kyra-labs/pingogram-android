package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.LanguageDetection

/**
 * The languages of the device (message translation, `REQUIREMENTS.md` FR-C3): those the user reads in the system's
 * settings, and the language of a text, found on the device alone. Nothing of the text is sent anywhere to find it
 * (`REQUIREMENTS.md` §6.2): message translation offers Telegram's translation only for texts the user may not read.
 */
interface DeviceLanguageDetector {
    /** Whether [detect] can tell languages on this device: Android 10 and later. */
    val canDetect: Boolean

    /**
     * The language of [text], found on the device; [LanguageDetection.Unavailable] where [canDetect] is false. Runs
     * off the main thread; never fails: what cannot be told is [LanguageDetection.Undetermined].
     */
    suspend fun detect(text: String): LanguageDetection

    /**
     * The languages of the system's settings, preferred first (IETF BCP 47), whatever language Pingogram itself shows;
     * never empty.
     */
    fun preferredLanguages(): List<String>
}
