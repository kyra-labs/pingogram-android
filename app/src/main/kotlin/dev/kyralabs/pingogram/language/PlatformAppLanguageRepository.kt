package dev.kyralabs.pingogram.language

import android.app.LocaleManager
import android.content.Context
import android.content.res.XmlResourceParser
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.annotation.XmlRes
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.datastore.DataStoreLanguagePreference
import dev.kyralabs.pingogram.core.domain.repository.AppLanguageRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AppLanguageRepository] on Android's per-app language from Android 13 ([LocaleManager], which Android's settings
 * share), and on [DataStoreLanguagePreference] before, where the app applies the language itself ([InAppLanguage],
 * `MainActivity`). The languages it ships are those of `res/xml/locales_config.xml`, which Android 13+ lists in its
 * settings too (`android:localeConfig`), and which the build packages resources for (`localeFilters`), read once on
 * the IO dispatcher.
 */
@Singleton
class PlatformAppLanguageRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val preference: DataStoreLanguagePreference,
        @param:Dispatcher(IO) private val io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ) : AppLanguageRepository {
        private val supported by lazy { readLocaleConfig(context, R.xml.locales_config) }

        /**
         * Before Android 13: the language the app's screens take ([InAppLanguage]), null until it is read (a few
         * milliseconds after the process starts), then every change. Read eagerly, so `MainActivity` knows it as
         * soon as possible; not read at all from Android 13 on, where Android applies the language itself.
         */
        val inAppLanguage: StateFlow<InAppLanguage?> by lazy {
            preference.languageTag
                .map(::InAppLanguage)
                .stateIn(scope, SharingStarted.Eagerly, initialValue = null)
        }

        override suspend fun supportedLanguages(): List<String> = withContext(io) { supported }

        override val language: Flow<String?> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Read at each collection: Android's settings change it without telling the app, other than by
                // creating its activity again, and the screen collects again when it shows.
                flow { emit(localeManager().applicationLocales.firstTagOrNull()) }.flowOn(io)
            } else {
                inAppLanguage.filterNotNull().map { it.languageTag }
            }

        override suspend fun setLanguage(languageTag: String?): Outcome<Unit> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android keeps it and creates the activity again in that language.
                withContext(io) {
                    localeManager().applicationLocales =
                        languageTag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
                }
                Outcome.Success(Unit)
            } else {
                // MainActivity creates itself again once inAppLanguage changes.
                preference.setLanguageTag(languageTag)
            }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun localeManager(): LocaleManager = context.getSystemService(LocaleManager::class.java)
    }

/**
 * The language the app's screens take before Android 13, where the app applies it itself: [languageTag], or the
 * system's with null.
 */
data class InAppLanguage(
    val languageTag: String?,
)

private fun LocaleList.firstTagOrNull(): String? = if (isEmpty) null else get(0).toLanguageTag()

/**
 * The language tags of a locale config ([res]): the `android:name` of each `<locale>`, in their order. A file that
 * cannot be parsed would fail the build's resource processing, so this does not expect one.
 */
internal fun readLocaleConfig(
    context: Context,
    @XmlRes res: Int,
): List<String> =
    context.resources.getXml(res).use { parser ->
        buildList {
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                    parser.localeName()?.let(::add)
                }
            }
        }
    }

private fun XmlResourceParser.localeName(): String? = getAttributeValue(ANDROID_NAMESPACE, "name")

private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
