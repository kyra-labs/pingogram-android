package dev.kyralabs.pingogram.ui

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
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
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.time.AndroidTimePatterns
import dev.kyralabs.pingogram.core.ui.time.TimePatterns
import dev.kyralabs.pingogram.di.TimeModule
import org.drinkless.tdlib.TdApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The Chats tab as a phone shows it, with the floating navigation bar, the "New chat" button beside it and the list
 * fading out above them (`docs/DESIGN_SYSTEM.md` §6.1), the pinned chats' group at the top: light, dark and AMOLED.
 * The screen-level goldens of `feature:chats` have no bar, so only these show the fade over the rows. The chat list
 * runs its real view model and repositories on a [FakeTdlibClient] bound with Hilt, with enough chats to reach the
 * bar.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class, TimeModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = COMPACT)
class ChatListWithBarScreenshotTest {
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
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready).also(::installChats)

    @BindValue
    @JvmField
    val wallClock: WallClock = StoppedWallClock()

    @BindValue
    @JvmField
    val timePatterns: TimePatterns = AndroidTimePatterns(RuntimeEnvironment.getApplication())

    @Test
    fun light() = capture(ThemeMode.Light)

    @Test
    fun dark() = capture(ThemeMode.Dark)

    @Test
    fun amoled() = capture(ThemeMode.Dark, amoled = true)

    private fun capture(
        themeMode: ThemeMode,
        amoled: Boolean = false,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false, amoled = amoled) {
                PingogramApp(uiState = MainActivityUiState.SignedIn)
            }
        }
        // The chat list gets its state from a background dispatcher.
        val row = hasContentDescription(NAMES.first(), substring = true) and hasClickAction()
        composeRule.waitUntil(TIMEOUT_MILLIS) { composeRule.onAllNodes(row).fetchSemanticsNodes().isNotEmpty() }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage()
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
        const val FIRST_ID = 200L
        const val PINNED = 2
        const val MESSAGE_IDS = 1_000L
        const val MINUTE_SECONDS = 60

        val NAMES =
            listOf(
                "Ada Lovelace",
                "Grace Hopper",
                "Alan Turing",
                "Katherine Johnson",
                "Hedy Lamarr",
                "Margaret Hamilton",
                "Charles Babbage",
                "Dorothy Vaughan",
                "John von Neumann",
                "Mary Jackson",
                "Claude Shannon",
                "Annie Easley",
            )

        /**
         * The main list's chats as TDLib announces them while it loads, a minute apart, newest first, the first
         * [PINNED] pinned; every later page and the archive are empty.
         */
        fun installChats(tdlib: FakeTdlibClient) {
            val loaded = AtomicBoolean(false)
            val newest = ChatsScript.now.epochSecond.toInt() - MINUTE_SECONDS
            val chats =
                NAMES.mapIndexed { index, name ->
                    val id = FIRST_ID + index
                    val (first, last) = name.split(" ", limit = 2)
                    val user = TdApiFixtures.user(id = id, firstName = first, lastName = last)
                    val message =
                        TdApiFixtures.message(
                            id = id * MESSAGE_IDS,
                            chatId = id,
                            sender = TdApi.MessageSenderUser(id),
                            date = newest - index * MINUTE_SECONDS,
                            content = TdApiFixtures.messageText("A note from $first"),
                        )
                    user to
                        TdApiFixtures.chat(
                            id = id,
                            type = TdApi.ChatTypePrivate(id),
                            title = name,
                            lastMessage = message,
                            positions =
                                arrayOf(
                                    TdApiFixtures.chatPosition(
                                        order = (NAMES.size - index).toLong(),
                                        isPinned = index < PINNED,
                                    ),
                                ),
                            unreadCount = if (index % 3 == 1) index else 0,
                            lastReadInboxMessageId = if (index % 3 == 1) 0 else message.id,
                        )
                }
            tdlib.on(TdApi.LoadChats::class) { request ->
                if (request.chatList is TdApi.ChatListMain && loaded.compareAndSet(false, true)) {
                    chats.forEach { (user, chat) ->
                        tdlib.emit(TdApiFixtures.updateUser(user))
                        tdlib.emit(TdApiFixtures.updateNewChat(chat))
                    }
                    TdResult.Success(TdApi.Ok())
                } else {
                    // TDLib's answer once a chat list is loaded completely.
                    TdResult.Failure(TdError(404, "Not Found"))
                }
            }
        }
    }
}

private const val COMPACT = "w411dp-h891dp-xxhdpi"
