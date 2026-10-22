package dev.kyralabs.pingogram.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
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
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.drinkless.tdlib.TdApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.feature.chats.R as ChatsR
import dev.kyralabs.pingogram.feature.settings.R as SettingsR

/**
 * Key screens in Android's pseudo-locales (`docs/TRANSLATING.md`), which debug builds generate: `en-XA` sets every
 * string accented and about a third longer, as German or Finnish would, and `ar-XB` mirrors the layout and the text
 * right to left, as Arabic or Hebrew would. The chat list (empty, with its navigation) and Settings, on a compact
 * phone; texts must wrap or end in an ellipsis, never be cut or overlap, and right-to-left layouts must mirror.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class PseudoLocaleScreenshotTest {
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
    @Config(qualifiers = "en-rXA-$COMPACT")
    fun chatsLongText() = captureChats()

    @Test
    @Config(qualifiers = "ar-rXB-$COMPACT")
    fun chatsRightToLeft() = captureChats()

    @Test
    @Config(qualifiers = "en-rXA-$COMPACT")
    fun settingsLongText() = captureSettings()

    @Test
    @Config(qualifiers = "ar-rXB-$COMPACT")
    fun settingsRightToLeft() = captureSettings()

    private fun showApp() {
        composeRule.setContent {
            PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                PingogramApp(uiState = MainActivityUiState.SignedIn)
            }
        }
        // The chat list gets its state from a background dispatcher.
        waitForText(ChatsR.string.chat_list_empty_title)
    }

    private fun captureChats() {
        showApp()
        composeRule.onRoot().captureRoboImage()
    }

    private fun captureSettings() {
        showApp()
        // The navigation's last tab, whatever its label reads in the pseudo-locale.
        composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).let { tabs ->
            tabs[tabs.fetchSemanticsNodes().lastIndex].performClick()
        }
        waitForText(SettingsR.string.settings_appearance)
        composeRule.onRoot().captureRoboImage()
    }

    private fun waitForText(id: Int) {
        val text = RuntimeEnvironment.getApplication().getString(id)
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}

private const val COMPACT = "w411dp-h891dp-xxhdpi"
