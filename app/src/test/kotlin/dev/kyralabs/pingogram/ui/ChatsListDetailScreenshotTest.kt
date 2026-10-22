package dev.kyralabs.pingogram.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
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
import dev.kyralabs.pingogram.core.common.time.WallClock
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.ProfileFixtures
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.time.AndroidTimePatterns
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.di.TimeModule
import dev.kyralabs.pingogram.ui.ChatsScript.ADA
import dev.kyralabs.pingogram.ui.ChatsScript.ADA_TITLE
import org.drinkless.tdlib.TdApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.feature.chat.R as ChatR

/**
 * The Chats tab's list-detail layout ([ChatsListDetail]) on an expanded window, light and dark, with no chat open,
 * with a conversation beside the list, whose row looks selected, and with the profile its title pushed above it; on a
 * large window too, whose list pane is wider; and on an extra-large one, with the conversation alone beside the list
 * and with the profile in the third pane (`docs/TESTING.md`). The screens run their real view models and repositories on a [FakeTdlibClient] bound
 * with Hilt, with the chats of [ChatsScript] and a [StoppedWallClock] in UTC, so times never change.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class, TimeModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = EXPANDED)
class ChatsListDetailScreenshotTest {
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
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready)
            .also(ChatsScript::install)
            .also {
                it.on(TdApi.GetUserFullInfo::class, TdResult.Success(ProfileFixtures.userFullInfo(bio = ADA_BIO)))
                it.on(TdApi.SearchChatMessages::class, TdResult.Success(TdApi.FoundChatMessages(0, emptyArray(), 0)))
                it.on(TdApi.GetUserProfilePhotos::class, TdResult.Success(TdApi.ChatPhotos(0, emptyArray())))
            }

    @BindValue
    @JvmField
    val wallClock: WallClock = StoppedWallClock()

    @BindValue
    @JvmField
    val timePatterns: TimePatterns = AndroidTimePatterns(RuntimeEnvironment.getApplication())

    @Test
    fun noChatOpenLight() = capture(ThemeMode.Light, openChat = false)

    @Test
    fun noChatOpenDark() = capture(ThemeMode.Dark, openChat = false)

    @Test
    fun chatOpenLight() = capture(ThemeMode.Light, openChat = true)

    @Test
    fun chatOpenDark() = capture(ThemeMode.Dark, openChat = true)

    @Test
    @Config(qualifiers = LARGE)
    fun extraLargeChatOpenLight() = capture(ThemeMode.Light, openChat = true)

    /** Below extra-large, the conversation's title still pushes the profile above the layout. */
    @Test
    fun profileFromChatLight() = capture(ThemeMode.Light, openChat = true, openProfile = true)

    @Test
    fun profileFromChatDark() = capture(ThemeMode.Dark, openChat = true, openProfile = true)

    @Test
    @Config(qualifiers = EXTRA_LARGE)
    fun threePanesChatOpenLight() = capture(ThemeMode.Light, openChat = true)

    /** Extra-large: the profile beside the conversation, in the third pane. */
    @Test
    @Config(qualifiers = EXTRA_LARGE)
    fun threePanesProfileLight() = capture(ThemeMode.Light, openChat = true, openProfile = true)

    @Test
    @Config(qualifiers = EXTRA_LARGE)
    fun threePanesProfileDark() = capture(ThemeMode.Dark, openChat = true, openProfile = true)

    private fun capture(
        themeMode: ThemeMode,
        openChat: Boolean,
        openProfile: Boolean = false,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                PingogramApp(uiState = MainActivityUiState.SignedIn)
            }
        }
        val row = hasContentDescription(ADA_TITLE, substring = true) and hasClickAction()
        await(row)
        if (openChat) {
            composeRule.onNode(row).performClick()
            await(hasContentDescription(ChatsScript.olderMessage.getValue(ADA), substring = true))
        }
        if (openProfile) {
            val openProfileLabel = RuntimeEnvironment.getApplication().getString(ChatR.string.chat_open_profile)
            composeRule
                .onNode(
                    SemanticsMatcher("opens the profile") {
                        it.config.getOrNull(SemanticsActions.OnClick)?.label == openProfileLabel
                    },
                ).performClick()
            await(hasText(ADA_BIO, substring = true))
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    /** The screens get their state from a background dispatcher. */
    private fun await(matcher: SemanticsMatcher) {
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val ADA_BIO = "Poet of the Analytical Engine"
    }
}

private const val EXPANDED = "w840dp-h891dp-xxhdpi"

/** A landscape tablet: a large window, whose list pane is wider. */
private const val LARGE = "w1280dp-h800dp-xhdpi"

/** A desktop window: extra-large, with room for a third pane. */
private const val EXTRA_LARGE = "w1600dp-h1000dp-mdpi"
