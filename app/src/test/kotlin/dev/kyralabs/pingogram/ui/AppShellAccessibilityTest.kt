package dev.kyralabs.pingogram.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.drinkless.tdlib.TdApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.feature.chats.R as ChatsR

/**
 * The app shell's controls (the floating navigation bar's tabs and search, the chat list's chrome around an empty
 * list) have names and 48 dp touch targets ([assertAccessible], `docs/audit/accessibility.md`), on a compact phone
 * and at 200% font scale.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AppShellAccessibilityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val activityRegistration = ActivityRegistration(HiltTestActivity::class.java)

    @get:Rule(order = 1)
    val notificationsAllowed = NotificationsAllowed()

    @get:Rule(order = 2)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient =
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready).apply {
            // TDLib's answer once a chat list is loaded completely: the list is empty.
            on(TdApi.LoadChats::class, TdResult.Failure(TdError(404, "Not Found")))
        }

    @Test
    fun `the navigation and the chat list's chrome are accessible`() {
        showApp()
        composeRule.assertAccessible()
    }

    @Test
    @Config(fontScale = 2f)
    fun `they are at 200 percent font scale too`() {
        showApp()
        composeRule.assertAccessible()
    }

    private fun showApp() {
        composeRule.setContent {
            PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                PingogramApp(uiState = MainActivityUiState.SignedIn)
            }
        }
        // The chat list gets its state from a background dispatcher.
        val empty = RuntimeEnvironment.getApplication().getString(ChatsR.string.chat_list_empty_title)
        composeRule.waitUntil(
            TIMEOUT_MILLIS,
        ) { composeRule.onAllNodes(hasText(empty)).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
