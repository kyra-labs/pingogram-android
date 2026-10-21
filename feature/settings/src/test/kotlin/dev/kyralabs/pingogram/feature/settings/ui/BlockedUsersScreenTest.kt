package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibBlockListRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.drinkless.tdlib.TdApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Unblocking on the Blocked users page: the row's button, and "Undo" in the snackbar that follows, which blocks again.
 * The screen alone is stateless; the undo test runs the route with its real view model and repositories on a
 * [FakeTdlibClient].
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BlockedUsersScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<BlockedUsersEvent>()

    /** The repositories' scope; nothing is emitted, so their update collectors only wait. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() {
        scope.cancel()
    }

    private fun setScreen(state: BlockedUsersUiState = SettingsPreviewData.blockedUsers) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                BlockedUsersScreen(state = state, onEvent = { events += it }, onBack = {})
            }
        }
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ) = RuntimeEnvironment.getApplication().getString(id, *args)

    @Test
    fun `the row's button unblocks`() {
        setScreen()

        composeRule.onNodeWithContentDescription(string(R.string.settings_unblock, "Grace Hopper")).performClick()

        assertEquals(listOf(BlockedUsersEvent.Unblock(MessageSender.User(7))), events)
    }

    @Test
    fun `a row being unblocked shows progress instead of its button`() {
        setScreen()

        composeRule.onNode(hasContentDescription(string(R.string.settings_unblocking, "Alan Turing"))).assertExists()
        composeRule.onNodeWithContentDescription(string(R.string.settings_unblock, "Alan Turing")).assertDoesNotExist()
    }

    @Test
    fun `the row's button is an Unblock text button named after the row`() {
        setScreen()

        composeRule
            .onNodeWithContentDescription(string(R.string.settings_unblock, "Grace Hopper"))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(hasText(string(R.string.settings_unblock_button)))
    }

    @Test
    fun `rows show usernames and phone numbers formatted for their country`() {
        setScreen()

        composeRule.onNodeWithText("@grace").assertExists()
        composeRule.onNodeWithText("+44 7700 900123").assertExists()
        composeRule.onNodeWithText("4 blocked users and chats").assertExists()
    }

    @Test
    fun `nobody blocked shows the empty state`() {
        setScreen(BlockedUsersUiState.Empty)

        composeRule.onNodeWithText(string(R.string.settings_blocked_users_empty_title)).assertExists()
    }

    @Test
    fun `a failed next page offers to try again`() {
        setScreen(SettingsPreviewData.blockedUsers.copy(more = MoreBlockedSenders.Failed(DomainError.Network)))

        composeRule.onNodeWithText(string(R.string.settings_retry)).performClick()

        assertEquals(listOf(BlockedUsersEvent.LoadMore), events)
    }

    @Test
    fun `unblocking can be undone from the snackbar`() {
        val client =
            FakeTdlibClient().apply {
                on(
                    TdApi.GetBlockedMessageSenders::class,
                    TdResult.Success(TdApi.MessageSenders(1, arrayOf(TdApi.MessageSenderUser(7)))),
                )
                on(
                    TdApi.GetUser::class,
                    TdResult.Success(TdApiFixtures.user(id = 7, firstName = "Grace", lastName = "Hopper")),
                )
                on(TdApi.SetMessageSenderBlockList::class, TdResult.Success(TdApi.Ok()))
            }
        val viewModel =
            BlockedUsersViewModel(
                TdlibBlockListRepository(client),
                TdlibUserRepository(client, scope),
                TdlibChatRepository(client, scope, Dispatchers.Unconfined, OpenChats()),
                TdlibGroupRepository(client, scope),
            )
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { BlockedUsersRoute(onBack = {}, viewModel = viewModel) }
        }

        composeRule.onNodeWithContentDescription(string(R.string.settings_unblock, "Grace Hopper")).performClick()
        composeRule.onNodeWithText(string(R.string.settings_unblocked, "Grace Hopper")).assertExists()
        composeRule.onNodeWithText(string(R.string.settings_unblock_undo)).performClick()
        composeRule.waitForIdle()

        val requests = client.sentOf<TdApi.SetMessageSenderBlockList>()
        assertEquals(2, requests.size)
        assertNull(requests[0].blockList)
        assertTrue(requests[1].blockList is TdApi.BlockListMain)
        assertEquals(7L, (requests[1].senderId as TdApi.MessageSenderUser).userId)
        composeRule.onNodeWithText("Grace Hopper").assertExists()
    }
}
