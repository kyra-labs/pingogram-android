package dev.kyralabs.pingogram.core.data.repository

import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLanguage
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.domain.model.LanguageDetection
import dev.kyralabs.pingogram.core.domain.repository.DeviceLanguageDetector
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DeviceLanguageDetector] on the platform (message translation, `REQUIREMENTS.md` FR-C3, §6.2).
 *
 * - **Detecting** (Android 10+): Android's own text classifier ([TextClassificationManager], `detectLanguage`), which
 *   runs on the device: in Pingogram's process, or in the system's text classification service (Android System
 *   Intelligence on Pixels), which keeps what it is given on the device too. Nothing is sent to Telegram or anyone else
 *   to find a language, and no library or model comes with the app. A call may take a few milliseconds and cross
 *   processes, so it runs on the [io] dispatcher; at most the first [MAX_LENGTH] characters are looked at. The most
 *   likely language counts once its confidence reaches [MIN_CONFIDENCE]; below it, or with the classifier turned off,
 *   the language is undetermined. Before Android 10 there is no such API: [canDetect] is false.
 * - **The user's languages**: the system's language list ([Resources.getSystem]), which Pingogram's own language
 *   (Android 13's per-app language, or the in-app choice before) leaves as it is.
 *
 * The texts and what was detected are never logged.
 */
@Singleton
class AndroidDeviceLanguageDetector
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:Dispatcher(IO) private val io: CoroutineDispatcher,
    ) : DeviceLanguageDetector {
        @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
        override val canDetect: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        override suspend fun detect(text: String): LanguageDetection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                withContext(io) { detectOnDevice(text) }
            } else {
                LanguageDetection.Unavailable
            }

        override fun preferredLanguages(): List<String> {
            val locales = Resources.getSystem().configuration.locales
            val tags = (0 until locales.size()).map { locales[it].toLanguageTag() }
            return tags.ifEmpty { listOf(Locale.getDefault().toLanguageTag()) }
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        private fun detectOnDevice(text: String): LanguageDetection {
            val classifier = context.getSystemService(TextClassificationManager::class.java)?.textClassifier
            val result =
                try {
                    classifier
                        ?.takeIf { it != TextClassifier.NO_OP && text.isNotBlank() }
                        ?.detectLanguage(TextLanguage.Request.Builder(text.take(MAX_LENGTH)).build())
                } catch (_: IllegalStateException) {
                    // The classifier was destroyed meanwhile (a session that ended), or refused this thread.
                    null
                }
            val likeliest = result?.takeIf { it.localeHypothesisCount > 0 }?.getLocale(0)
            val confidence = likeliest?.let { result?.getConfidenceScore(it) } ?: 0f
            return if (likeliest != null && confidence >= MIN_CONFIDENCE) {
                LanguageDetection.Detected(likeliest.toLanguageTag())
            } else {
                LanguageDetection.Undetermined
            }
        }

        private companion object {
            /** How sure the classifier must be of the likeliest language. */
            const val MIN_CONFIDENCE = 0.5f

            /** The characters looked at: enough to tell a language, little enough to stay fast. */
            const val MAX_LENGTH = 500
        }
    }
