package dev.kyralabs.pingogram.core.data.repository

import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLinks
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.domain.model.TextFindings
import dev.kyralabs.pingogram.core.domain.repository.DeviceTextFinder
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [DeviceTextFinder] on Android's own text classifier (`TextClassifier.generateLinks`, Android 10+), which runs on the
 * device, in Pingogram's process or in the system's text classification service, as for detecting languages
 * ([AndroidDeviceLanguageDetector]): nothing is sent anywhere, and no library or model comes with the app. Only dates,
 * times and addresses are asked for, from at most the classifier's longest text; a call may cross processes, so it runs
 * on the [io] dispatcher. A date's moment is read from the words found ([foundEventOf]). The texts and what was found
 * are never logged.
 */
@Singleton
class AndroidDeviceTextFinder
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:Dispatcher(IO) private val io: CoroutineDispatcher,
    ) : DeviceTextFinder {
        override suspend fun find(
            text: String,
            writtenAtMillis: Long,
        ): TextFindings =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && text.isNotBlank()) {
                withContext(io) { findOnDevice(text, writtenAtMillis) }
            } else {
                TextFindings.None
            }

        @RequiresApi(Build.VERSION_CODES.Q)
        private fun findOnDevice(
            text: String,
            writtenAtMillis: Long,
        ): TextFindings {
            val classifier =
                context
                    .getSystemService(TextClassificationManager::class.java)
                    ?.textClassifier
                    ?.takeIf { it != TextClassifier.NO_OP }
            val shown = classifier?.let { text.take(it.maxGenerateLinksTextLength) }.orEmpty()
            val writtenAt = ZonedDateTime.ofInstant(Instant.ofEpochMilli(writtenAtMillis), ZoneId.systemDefault())
            val links =
                try {
                    classifier?.generateLinks(request(shown, writtenAt))?.links.orEmpty()
                } catch (_: IllegalStateException) {
                    // The classifier was destroyed meanwhile, or refused this thread.
                    emptyList()
                } catch (_: IllegalArgumentException) {
                    // A text longer than the classifier takes after all.
                    emptyList()
                }
            val date = links.firstOrNull { it.isOneOf(TextClassifier.TYPE_DATE, TextClassifier.TYPE_DATE_TIME) }
            val address = links.firstOrNull { it.isOneOf(TextClassifier.TYPE_ADDRESS) }
            return TextFindings(
                event = date?.let { foundEventOf(shown.substring(it.start, it.end), Locale.getDefault(), writtenAt) },
                address = address?.let { shown.substring(it.start, it.end).trim() },
            )
        }

        @RequiresApi(Build.VERSION_CODES.Q)
        private fun request(
            text: String,
            writtenAt: ZonedDateTime,
        ): TextLinks.Request {
            val entities =
                TextClassifier.EntityConfig
                    .Builder()
                    .setIncludedTypes(
                        listOf(TextClassifier.TYPE_DATE, TextClassifier.TYPE_DATE_TIME, TextClassifier.TYPE_ADDRESS),
                    ).includeTypesFromTextClassifier(false)
                    .build()
            val builder = TextLinks.Request.Builder(text).setEntityConfig(entities)
            // "Friday" or "17:30" count from when the message was written, where the classifier takes it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setReferenceTime(writtenAt)
            return builder.build()
        }

        /** Whether the classifier's likeliest kind for this link is one of [types]. */
        @RequiresApi(Build.VERSION_CODES.Q)
        private fun TextLinks.TextLink.isOneOf(vararg types: String): Boolean = entityCount > 0 && getEntity(0) in types
    }
