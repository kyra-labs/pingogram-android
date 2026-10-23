package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.UsernameOwner
import dev.kyralabs.pingogram.core.ui.text.MentionTarget
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GRACE
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Profiles from the conversation (`REQUIREMENTS.md` FR-P2): which title opens one, which senders' avatars and names
 * do, and what a tapped mention opens.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatProfilesTest : ChatViewModelHarness() {
    private fun TestScope.effectsOf(viewModel: ChatViewModel): List<ChatEffect> {
        val effects = mutableListOf<ChatEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    @Test
    fun `the title of a private chat opens the other user's profile`() =
        runViewModelTest {
            users.setUsers(user(ADA))
            val viewModel = showing(chat(), history(message(1)))

            assertEquals(ADA, viewModel.state.header?.profileUserId)
        }

    @Test
    fun `the title of Saved Messages opens the user's own profile, that of a group no profile`() =
        runViewModelTest {
            users.setUsers(user(ME))
            assertEquals(ME, showing(chat(id = ME), history(message(1, chatId = ME))).state.header?.profileUserId)
            assertNull(showing(groupChat, history(message(1, chatId = groupChat.id))).state.header?.profileUserId)
        }

    @Test
    fun `incoming messages of users in a group name their sender for the profile, others do not`() =
        runViewModelTest {
            users.setUsers(user(GRACE, firstName = "Grace"))
            val viewModel =
                showing(
                    groupChat,
                    history(
                        message(1, chatId = groupChat.id, sender = MessageSender.User(GRACE)),
                        message(2, chatId = groupChat.id, sender = MessageSender.User(ME), isOutgoing = true),
                        message(3, chatId = groupChat.id, sender = MessageSender.Chat(groupChat.id)),
                    ),
                )

            assertEquals(GRACE, viewModel.bubble(1).senderUserId)
            assertNull(viewModel.bubble(2).senderUserId)
            assertNull(viewModel.bubble(3).senderUserId)
        }

    @Test
    fun `a private chat's bubbles name no sender for the profile`() =
        runViewModelTest {
            val viewModel = showing(chat(), history(message(1)))

            assertNull(viewModel.bubble(1).senderUserId)
        }

    @Test
    fun `a sender's avatar or name opens their profile`() =
        runViewModelTest {
            val viewModel = showing(chat(), history(message(1)))
            val effects = effectsOf(viewModel)

            send(viewModel, ChatEvent.OpenProfile(GRACE))

            assertEquals(listOf(ChatEffect.OpenProfile(GRACE)), effects)
        }

    @Test
    fun `a mention name opens the profile of the user it names, without asking TDLib`() =
        runViewModelTest {
            val viewModel = showing(chat(), history(message(1)))
            val effects = effectsOf(viewModel)

            send(viewModel, ChatEvent.OpenMention(MentionTarget.User(GRACE)))

            assertEquals(listOf(ChatEffect.OpenProfile(GRACE)), effects)
            assertEquals(emptyList<String>(), search.usernames)
        }

    @Test
    fun `a username opens the profile of the user who has it, or the chat of a group or channel`() =
        runViewModelTest {
            search.onPublicChat = { name ->
                if (name ==
                    "grace"
                ) {
                    Outcome.Success(UsernameOwner.User(GRACE, GRACE))
                } else {
                    Outcome.Success(UsernameOwner.Chat(-7))
                }
            }
            val viewModel = showing(chat(), history(message(1)))
            val effects = effectsOf(viewModel)

            send(
                viewModel,
                ChatEvent.OpenMention(MentionTarget.Username("grace")),
                ChatEvent.OpenMention(MentionTarget.Username("news")),
            )

            assertEquals(listOf(ChatEffect.OpenProfile(GRACE), ChatEffect.OpenChat(-7)), effects)
            assertEquals(listOf("grace", "news"), search.usernames)
        }

    @Test
    fun `a username nobody has is a snackbar`() =
        runViewModelTest {
            val viewModel = showing(chat(), history(message(1)))
            val effects = effectsOf(viewModel)

            send(viewModel, ChatEvent.OpenMention(MentionTarget.Username("nobody")))

            assertEquals(listOf(ChatEffect.ShowError(DomainError.NotFound)), effects)
            assertEquals(PRIVATE_CHAT, viewModel.state.header?.chatId)
        }
}
