package dev.kyralabs.pingogram

import android.content.Intent
import androidx.annotation.StringRes
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import dev.kyralabs.pingogram.core.notifications.NotificationIntents
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
import dev.kyralabs.pingogram.feature.chat.R as ChatR
import dev.kyralabs.pingogram.feature.settings.R as SettingsR

/**
 * What another app shares with Pingogram, end to end in [MainActivity] on a [FakeTdlibClient] (`REQUIREMENTS.md`
 * FR-N2, direct share): the share sheet opens once the account is logged in and the app unlocked, signed out after the
 * login; a share to a Direct Share target opens its chat with the text in the composer; a `t.me/share` link opens the
 * sheet too. Nothing is sent by itself.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class MainActivityShareTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private val tdlib =
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready).apply {
            // TDLib's answer once a chat list is loaded completely: the chat list is empty.
            on(TdApi.LoadChats::class, TdResult.Failure(TdError(404, "Not Found")))
        }

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient = tdlib

    @Inject
    lateinit var passcodes: PasscodeRepository

    /** Created here, so it follows TDLib's updates before the chat is announced. */
    @Inject
    lateinit var chats: ChatRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `a shared text opens the share sheet and sends nothing`() {
        launch(sendText(LINK)).use {
            awaitText(string(ChatR.string.share_title))
            awaitText(LINK)

            assertTrue(tdlib.sentOf<TdApi.SendMessage>().isEmpty())
        }
    }

    @Test
    fun `signed out, a share waits for the login`() {
        tdlib.authState.value = AuthState.WaitPhoneNumber
        launch(sendText(LINK)).use {
            awaitText(string(AuthR.string.auth_start_messaging))
            assertTrue(
                composeRule.onAllNodes(hasText(string(ChatR.string.share_title))).fetchSemanticsNodes().isEmpty(),
            )

            runBlocking { tdlib.emitAuthorizationState(TdApiFixtures.ready()) }

            awaitText(string(ChatR.string.share_title))
        }
    }

    @Test
    fun `locked, a share opens once unlocked`() {
        runBlocking { passcodes.setPasscode("2580", PasscodeType.Pin) }
        launch(sendText(LINK)).use {
            awaitText(string(SettingsR.string.lock_title))
            assertTrue(
                composeRule.onAllNodes(hasText(string(ChatR.string.share_title))).fetchSemanticsNodes().isEmpty(),
            )

            "2580".forEach { composeRule.onNode(hasText(it.toString())).performClick() }
            composeRule.onNodeWithContentDescription(string(SettingsR.string.lock_unlock)).performClick()

            awaitText(string(ChatR.string.share_title))
        }
    }

    @Test
    fun `a share to a Direct Share target opens its chat with the text in the composer, unsent`() {
        runBlocking { tdlib.emit(TdApiFixtures.updateNewChat(TdApiFixtures.chat(id = CHAT, title = "Ada"))) }
        val intent =
            sendText(
                LINK,
            ).putExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID, NotificationIntents.shortcutId(CHAT))
        launch(intent).use {
            composeRule.waitUntil(TIMEOUT_MILLIS) {
                composeRule.onAllNodes(hasSetTextAction() and hasText(LINK)).fetchSemanticsNodes().isNotEmpty()
            }

            assertTrue(tdlib.sentOf<TdApi.SendMessage>().isEmpty())
        }
    }

    @Test
    fun `a t_me share link opens the share sheet with its text`() {
        val text = TdApi.FormattedText("$LINK\nLook", emptyArray())
        tdlib.on(TdApi.GetInternalLinkType::class, TdResult.Success(TdApi.InternalLinkTypeMessageDraft(text, true)))
        val view =
            Intent(
                Intent.ACTION_VIEW,
                android.net.Uri.parse("https://t.me/share?url=$LINK&text=Look"),
                RuntimeEnvironment.getApplication(),
                MainActivity::class.java,
            )
        launch(view).use {
            awaitText(string(ChatR.string.share_title))
            awaitText("$LINK Look")
        }
    }

    private fun launch(intent: Intent) = ActivityScenario.launch<MainActivity>(intent)

    private fun sendText(text: String) =
        Intent(Intent.ACTION_SEND)
            .setClass(RuntimeEnvironment.getApplication(), MainActivity::class.java)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)

    private fun awaitText(text: String) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val LINK = "https://example.com/article"
        const val CHAT = 100L
        const val TIMEOUT_MILLIS = 10_000L
    }
}
