package dev.kyralabs.pingogram.language

import android.app.Application
import android.app.LocaleManager
import android.os.LocaleList
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.datastore.DataStoreLanguagePreference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.Locale

/**
 * [PlatformAppLanguageRepository] on Robolectric's Android: the languages of `res/xml/locales_config.xml`, Android's
 * per-app language from Android 13 on, the app's settings before, and the context `MainActivity` takes before Android
 * 13 ([contextInLanguage]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlatformAppLanguageRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val context = RuntimeEnvironment.getApplication()
    private val defaultLocales = LocaleList.getDefault()

    @After
    fun restoreDefaultLocale() {
        LocaleList.setDefault(defaultLocales)
    }

    private fun TestScope.preference() =
        DataStoreLanguagePreference(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(folder.root, "settings.preferences_pb") },
        )

    private fun TestScope.repository(preference: DataStoreLanguagePreference = preference()) =
        PlatformAppLanguageRepository(context, preference, StandardTestDispatcher(testScheduler), backgroundScope)

    private fun localeManager() = context.getSystemService(LocaleManager::class.java)

    @Test
    fun `ships the languages of the locale config`() =
        runTest {
            assertEquals(listOf("en"), repository().supportedLanguages())
        }

    @Test
    fun `from Android 13, the language is Android's per-app language`() =
        runTest {
            val repository = repository()
            assertNull(repository.language.first())

            assertEquals(Outcome.Success(Unit), repository.setLanguage("de"))

            assertEquals(LocaleList.forLanguageTags("de"), localeManager().applicationLocales)
            assertEquals("de", repository.language.first())

            repository.setLanguage(null)

            assertEquals(LocaleList.getEmptyLocaleList(), localeManager().applicationLocales)
            assertNull(repository.language.first())
        }

    @Test
    fun `from Android 13, a language chosen in Android's settings shows at the next collection`() =
        runTest {
            val repository = repository()

            localeManager().applicationLocales = LocaleList.forLanguageTags("en-GB")

            assertEquals("en-GB", repository.language.first())
        }

    @Test
    @Config(sdk = [32])
    fun `before Android 13, the language is kept in the app's settings`() =
        runTest {
            val preference = preference()
            val repository = repository(preference)
            assertNull(repository.inAppLanguage.value)
            runCurrent()
            assertEquals(InAppLanguage(null), repository.inAppLanguage.value)

            assertEquals(Outcome.Success(Unit), repository.setLanguage("de"))
            runCurrent()

            assertEquals(InAppLanguage("de"), repository.inAppLanguage.value)
            assertEquals("de", repository.language.first())
            assertEquals("de", preference.languageTag.first())
        }

    @Test
    @Config(sdk = [32])
    fun `before Android 13, the activity's context takes the chosen language, and the process's default too`() {
        val french = contextInLanguage(context, InAppLanguage("fr"))

        assertEquals(Locale.FRENCH, french.resources.configuration.locales[0])
        assertEquals(Locale.FRENCH, Locale.getDefault())
    }

    @Test
    @Config(sdk = [32], qualifiers = "es-rES")
    fun `before Android 13, the system default takes the system's languages again`() {
        contextInLanguage(context, InAppLanguage("fr"))

        val system = contextInLanguage(context, InAppLanguage(null))

        assertEquals(Locale.forLanguageTag("es-ES"), system.resources.configuration.locales[0])
        assertEquals(Locale.forLanguageTag("es-ES"), Locale.getDefault())
    }

    @Test
    @Config(sdk = [32])
    fun `before Android 13, a language not read yet leaves the context as it is`() {
        assertSame(context, contextInLanguage(context, null))
    }
}
