package dev.kyralabs.pingogram

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.inject.Inject
import dev.kyralabs.pingogram.feature.auth.R as AuthR
import dev.kyralabs.pingogram.feature.settings.R as SettingsR

/**
 * A Telegram link another app opens Pingogram with, end to end in [MainActivity] on a [FakeTdlibClient]
 * (`docs/adr/0013-deep-links.md`): TDLib reads it once the account is logged in and the app unlocked, and its screen
 * opens; signed out, it waits for the login.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class MainActivityLinkTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private val tdlib =
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready).apply {
            // TDLib's answer once a chat list is loaded completely: the chat list is empty.
            on(TdApi.LoadChats::class, TdResult.Failure(TdError(404, "Not Found")))
            on(TdApi.GetInternalLinkType::class, TdResult.Success(TdApi.InternalLinkTypeSettings(null)))
        }

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient = tdlib

    @Inject
    lateinit var passcodes: PasscodeRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `a link opens its screen`() {
        launch(viewIntent(SETTINGS_LINK)).use {
            awaitSettingsTab()

            assertEquals(SETTINGS_LINK, tdlib.sentOf<TdApi.GetInternalLinkType>().single().link)
        }
    }

    @Test
    fun `signed out, a link waits for the login`() {
        tdlib.authState.value = AuthState.WaitPhoneNumber
        launch(viewIntent(SETTINGS_LINK)).use {
            awaitText(AuthR.string.auth_start_messaging)
            assertTrue(tdlib.sentOf<TdApi.GetInternalLinkType>().isEmpty())

            runBlocking { tdlib.emitAuthorizationState(TdApiFixtures.ready()) }

            awaitSettingsTab()
            assertEquals(1, tdlib.sentOf<TdApi.GetInternalLinkType>().size)
        }
    }

    @Test
    fun `locked, a link opens once unlocked`() {
        runBlocking { passcodes.setPasscode("2580", PasscodeType.Pin) }
        launch(viewIntent(SETTINGS_LINK)).use {
            awaitText(SettingsR.string.lock_title)
            assertTrue(tdlib.sentOf<TdApi.GetInternalLinkType>().isEmpty())

            "2580".forEach { composeRule.onNode(hasText(it.toString())).performClick() }
            composeRule.onNodeWithContentDescription(string(SettingsR.string.lock_unlock)).performClick()

            awaitSettingsTab()
            assertEquals(1, tdlib.sentOf<TdApi.GetInternalLinkType>().size)
        }
    }

    @Test
    fun `other links are left alone`() {
        launch(viewIntent("https://example.com/page")).use {
            awaitText(R.string.nav_chats)
            composeRule.waitForIdle()

            assertTrue(tdlib.sentOf<TdApi.GetInternalLinkType>().isEmpty())
        }
    }

    private fun launch(intent: Intent) = ActivityScenario.launch<MainActivity>(intent)

    private fun viewIntent(link: String) =
        Intent(Intent.ACTION_VIEW, Uri.parse(link), RuntimeEnvironment.getApplication(), MainActivity::class.java)

    private fun awaitSettingsTab() {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule
                .onAllNodes(hasText(string(R.string.nav_settings)) and isSelectable())
                .fetchSemanticsNodes()
                .any { node -> node.config.getOrElseNullable(SemanticsProperties.Selected) { null } == true }
        }
        composeRule.onNode(hasText(string(R.string.nav_settings)) and isSelectable()).assertIsSelected()
    }

    private fun awaitText(
        @StringRes id: Int,
    ) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(string(id))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val SETTINGS_LINK = "tg://settings"
        const val TIMEOUT_MILLIS = 10_000L
    }
}
