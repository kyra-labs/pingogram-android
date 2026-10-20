package dev.kyralabs.pingogram

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.kyralabs.pingogram.core.domain.repository.AppLanguageRepository
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale
import javax.inject.Inject
import dev.kyralabs.pingogram.feature.auth.R as AuthR

/**
 * Before Android 13, [MainActivity] applies the app's language itself: a language chosen in Settings creates it again
 * in that language, and the system default in the system's again.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi", sdk = [32])
class MainActivityLanguageTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient =
        FakeTdlibClient(authState = AuthState.WaitPhoneNumber, connectionState = ConnectionState.Ready)

    @Inject
    lateinit var appLanguageRepository: AppLanguageRepository

    @Before
    fun inject() {
        hiltRule.inject()
    }

    @Test
    fun `before Android 13, the activity takes the language chosen in Settings, and the system's again`() {
        val system = activityLocale()
        awaitWelcome()

        runBlocking { appLanguageRepository.setLanguage("fr") }

        composeRule.waitUntil(TIMEOUT_MILLIS) { activityLocale() == Locale.FRENCH }
        awaitWelcome()

        runBlocking { appLanguageRepository.setLanguage(null) }

        composeRule.waitUntil(TIMEOUT_MILLIS) { activityLocale() == system }
        awaitWelcome()
    }

    private fun activityLocale(): Locale {
        lateinit var locale: Locale
        composeRule.activityRule.scenario.onActivity { locale = it.resources.configuration.locales[0] }
        return locale
    }

    /** The welcome screen is composed, in the activity's current language. */
    private fun awaitWelcome() {
        val text = RuntimeEnvironment.getApplication().getString(AuthR.string.auth_start_messaging)
        composeRule.waitUntil(
            TIMEOUT_MILLIS,
        ) { composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
