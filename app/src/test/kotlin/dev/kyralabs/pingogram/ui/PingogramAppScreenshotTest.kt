package dev.kyralabs.pingogram.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
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

/**
 * The signed-in shell on the Chats tab: the floating navigation bar on a compact phone, also narrow and at 200% font
 * scale, and a collapsed wide navigation rail on an expanded window, beside the tab's list and empty detail panes
 * (`docs/TESTING.md`; [ChatsListDetailScreenshotTest] has them with chats). The chat list runs its real view model and
 * repositories on a [FakeTdlibClient] bound with Hilt, with no chats.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = COMPACT)
class PingogramAppScreenshotTest {
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
    fun compactLight() = capture(ThemeMode.Light)

    @Test
    fun compactDark() = capture(ThemeMode.Dark)

    @Test
    @Config(qualifiers = NARROW, fontScale = LARGE_FONT_SCALE)
    fun narrowLargeFontLight() = capture(ThemeMode.Light)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedLight() = capture(ThemeMode.Light)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedDark() = capture(ThemeMode.Dark)

    /** A phone in landscape: the collapsed rail instead of the floating bar, which would take scarce height. */
    @Test
    @Config(qualifiers = LANDSCAPE)
    fun landscapeLight() = capture(ThemeMode.Light)

    /** A large window: the rail expanded, search as an extended FAB in its header. */
    @Test
    @Config(qualifiers = LARGE)
    fun largeLight() = capture(ThemeMode.Light)

    private fun capture(themeMode: ThemeMode) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                PingogramApp(uiState = MainActivityUiState.SignedIn)
            }
        }
        // The chat list gets its state from a background dispatcher.
        val emptyTitle = RuntimeEnvironment.getApplication().getString(ChatsR.string.chat_list_empty_title)
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(emptyTitle)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onRoot().captureRoboImage()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}

private const val COMPACT = "w411dp-h891dp-xxhdpi"
private const val NARROW = "w360dp-h780dp-xxhdpi"
private const val LARGE_FONT_SCALE = 2f
private const val EXPANDED = "w840dp-h891dp-xxhdpi"
private const val LANDSCAPE = "w891dp-h411dp-land-xxhdpi"
private const val LARGE = "w1280dp-h800dp-xhdpi"
