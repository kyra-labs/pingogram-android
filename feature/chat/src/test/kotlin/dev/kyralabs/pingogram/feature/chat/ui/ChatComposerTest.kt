package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatDraft
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.domain.model.ChatPermissions
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.DraftContent
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.CHANNEL_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.basicGroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.supergroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatComposer
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The composer's part of [ChatViewModel] (`REQUIREMENTS.md` FR-C5): where the user can write, the chat's draft in and
 * out, sending now, silently or at a time, failures, and process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatComposerTest : ChatViewModelHarness() {
    private val formatted = FormattedText("Hello there", persistentListOf(TextEntity(0, 5, TextEntityType.Bold)))

    private fun draft(text: String) = ChatDraft(DraftContent.Text(text(text)), date = 0)

    /** A view model of a private chat with [draft], and nothing to load. */
    private fun TestScope.privateChat(draft: ChatDraft? = null) = showing(chat(draft = draft), history(message(1)))

    /** Replaces the composer's text, as typing does, and lets the view model see it. */
    private fun TestScope.type(
        viewModel: ChatViewModel,
        text: String,
    ) {
        viewModel.composerText.state.edit { replace(0, length, text) }
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    /** The chat's draft changes, as on another device. */
    private fun TestScope.changeDraft(draft: ChatDraft?) {
        chats.setChats(chat(draft = draft))
        runCurrent()
    }

    private fun TestScope.waitForDraftSave() {
        advanceTimeBy(ChatComposer.DRAFT_SAVE_DELAY_MILLIS)
        runCurrent()
    }

    /** The composer without its attach options, which [ChatAttachmentsTest] covers. */
    private val ChatViewModel.composer
        get() =
            state.composer.let { (it as? ComposerUiState.Available)?.copy(attachOptions = persistentListOf()) ?: it }

    private val ChatViewModel.composed get() = composerText.content

    @Nested
    inner class WhoCanWrite {
        @Test
        fun `a private chat has the composer, with scheduling`() =
            runViewModelTest {
                assertEquals(ComposerUiState.Available(canSchedule = true), privateChat().composer)
            }

        @Test
        fun `a secret chat has the composer without scheduling`() =
            runViewModelTest {
                secretChats.setSecretChats(ChatFixtures.secretChat())
                val secret = chat(type = ChatType.Secret(secretChatId = 7, userId = ADA))

                assertEquals(ComposerUiState.Available(canSchedule = false), showing(secret, history()).composer)
            }

        @Test
        fun `a channel the user only reads shows nothing`() =
            runViewModelTest {
                groups.supergroupMap.value = mapOf(ChatFixtures.SUPERGROUP_ID to supergroup(memberCount = 9))

                assertEquals(ComposerUiState.Hidden, showing(channelChat, history(message(1, CHANNEL_CHAT))).composer)
            }

        @Test
        fun `a channel administrator who may post has the composer`() =
            runViewModelTest {
                val admin = ChatMemberStatus.Administrator(canPostMessages = true)
                groups.supergroupMap.value = mapOf(ChatFixtures.SUPERGROUP_ID to supergroup(9, status = admin))

                assertEquals(
                    ComposerUiState.Available(canSchedule = true),
                    showing(channelChat, history(message(1, CHANNEL_CHAT))).composer,
                )
            }

        @Test
        fun `a group where members may not write says so`() =
            runViewModelTest {
                groups.basicGroupMap.value = mapOf(ChatFixtures.BASIC_GROUP_ID to basicGroup(3))
                val closed = groupChat.copy(permissions = ChatPermissions.None)

                assertEquals(
                    ComposerUiState.Unavailable(res(R.string.composer_restricted)),
                    showing(closed, history(message(1, GROUP_CHAT))).composer,
                )
            }

        @Test
        fun `a group the user left says they are not a member`() =
            runViewModelTest {
                val left = basicGroup(3, status = ChatMemberStatus.Left)
                groups.basicGroupMap.value = mapOf(ChatFixtures.BASIC_GROUP_ID to left)

                assertEquals(
                    ComposerUiState.Unavailable(res(R.string.composer_not_member)),
                    showing(groupChat, history(message(1, GROUP_CHAT))).composer,
                )
            }

        @Test
        fun `a deleted account cannot be written to`() =
            runViewModelTest {
                users.setUsers(user(ADA, type = UserType.Deleted))

                assertEquals(ComposerUiState.Unavailable(res(R.string.composer_restricted)), privateChat().composer)
            }

        @Test
        fun `nothing shows until the group is known`() =
            runViewModelTest {
                assertEquals(ComposerUiState.Hidden, showing(groupChat, history(message(1, GROUP_CHAT))).composer)
            }

        @Test
        fun `a chat without messages still has the composer`() =
            runViewModelTest {
                val viewModel = showing(chat(), history())

                assertEquals(ChatContent.Empty, viewModel.state.content)
                assertEquals(ComposerUiState.Available(canSchedule = true), viewModel.composer)
            }
    }

    @Nested
    inner class Drafts {
        @Test
        fun `the chat's draft fills the composer`() =
            runViewModelTest {
                assertEquals(text("See you later"), privateChat(draft("See you later")).composed)
            }

        @Test
        fun `typing saves the draft a second after the last edit`() =
            runViewModelTest {
                val viewModel = privateChat()

                type(viewModel, "H")
                advanceTimeBy(ChatComposer.DRAFT_SAVE_DELAY_MILLIS / 2)
                type(viewModel, "Hi")
                advanceTimeBy(ChatComposer.DRAFT_SAVE_DELAY_MILLIS - 1)
                runCurrent()
                val beforeDelay = messages.drafts.toList()
                waitForDraftSave()

                assertEquals(emptyList<Pair<Long, FormattedText>>(), beforeDelay)
                assertEquals(listOf(PRIVATE_CHAT to text("Hi")), messages.drafts)
            }

        @Test
        fun `the draft keeps the formatting`() =
            runViewModelTest {
                val viewModel = privateChat()

                viewModel.composerText.setContent(formatted)
                Snapshot.sendApplyNotifications()
                waitForDraftSave()

                assertEquals(listOf(PRIVATE_CHAT to formatted), messages.drafts)
            }

        @Test
        fun `a text equal to the chat's draft saves nothing`() =
            runViewModelTest {
                val viewModel = privateChat(draft("Later"))

                type(viewModel, "Later!")
                type(viewModel, "Later")
                waitForDraftSave()

                assertEquals(emptyList<Pair<Long, FormattedText>>(), messages.drafts)
            }

        @Test
        fun `stopping the screen saves the draft at once`() =
            runViewModelTest {
                val viewModel = privateChat()
                send(viewModel, ChatEvent.Started)
                type(viewModel, "Bye")

                viewModel.onEvent(ChatEvent.Stopped)

                assertEquals(listOf(PRIVATE_CHAT to text("Bye")), messages.drafts)
            }

        @Test
        fun `emptying the composer removes the draft`() =
            runViewModelTest {
                val viewModel = privateChat(draft("Later"))

                type(viewModel, "")
                waitForDraftSave()

                assertEquals(listOf(PRIVATE_CHAT to FormattedText.Empty), messages.drafts)
            }

        @Test
        fun `a voice note draft stays until the user types`() =
            runViewModelTest {
                val viewModel = privateChat(ChatDraft(DraftContent.VoiceNote, date = 0))

                type(viewModel, "a")
                type(viewModel, "")
                waitForDraftSave()

                assertEquals(emptyList<Pair<Long, FormattedText>>(), messages.drafts)
            }

        @Test
        fun `a draft changed on another device fills an empty composer without the focus`() =
            runViewModelTest {
                val viewModel = privateChat()

                changeDraft(draft("From my laptop"))

                assertEquals(text("From my laptop"), viewModel.composed)
            }

        @Test
        fun `a draft changed on another device never replaces what the user typed`() =
            runViewModelTest {
                val viewModel = privateChat()
                type(viewModel, "Mine")

                changeDraft(draft("Theirs"))

                assertEquals(text("Mine"), viewModel.composed)
            }

        @Test
        fun `a draft changed on another device waits while the composer has the focus`() =
            runViewModelTest {
                val viewModel = privateChat()
                send(viewModel, ChatEvent.ComposerFocusChanged(true))

                changeDraft(draft("Theirs"))

                assertEquals(FormattedText.Empty, viewModel.composed)
            }

        @Test
        fun `the echo of a saved draft does not refill the composer after sending`() =
            runViewModelTest {
                val viewModel = privateChat()
                type(viewModel, "Hi")
                waitForDraftSave()
                send(viewModel, ChatEvent.Send())

                // TDLib reports the saved draft, then that sending cleared it.
                changeDraft(draft("Hi"))
                changeDraft(null)

                assertEquals(FormattedText.Empty, viewModel.composed)
            }
    }

    @Nested
    inner class Sending {
        @Test
        fun `the text goes with its formatting, and the composer empties`() =
            runViewModelTest {
                val viewModel = privateChat()
                viewModel.composerText.setContent(formatted)

                send(viewModel, ChatEvent.Send())

                assertEquals(listOf(Triple(PRIVATE_CHAT, formatted, SendOptions())), messages.sent)
                assertEquals(FormattedText.Empty, viewModel.composed)
            }

        @Test
        fun `a silent message goes without a notification`() =
            runViewModelTest {
                val viewModel = privateChat()
                type(viewModel, "Shh")

                send(viewModel, ChatEvent.Send(SendOptions(silent = true)))

                assertEquals(SendOptions(silent = true), messages.sent.single().third)
            }

        @Test
        fun `a scheduled message goes with its time, and a snackbar confirms it`() =
            runViewModelTest {
                val viewModel = privateChat()
                type(viewModel, "Happy birthday!")

                viewModel.effects.test {
                    send(viewModel, ChatEvent.Send(SendOptions(scheduledAt = SCHEDULED_AT)))

                    assertEquals(ChatEffect.MessageScheduled(SCHEDULED_AT), awaitItem())
                }
                assertEquals(SendOptions(scheduledAt = SCHEDULED_AT), messages.sent.single().third)
            }

        @Test
        fun `white space alone is not sent`() =
            runViewModelTest {
                val viewModel = privateChat()
                type(viewModel, "  \n ")

                send(viewModel, ChatEvent.Send())

                assertEquals(emptyList<Any>(), messages.sent)
                assertEquals(text("  \n "), viewModel.composed)
            }

        @Test
        fun `a message sent before the draft was saved leaves no draft behind`() =
            runViewModelTest {
                val viewModel = privateChat()
                send(viewModel, ChatEvent.Started)
                type(viewModel, "Quick")

                send(viewModel, ChatEvent.Send())
                waitForDraftSave()
                viewModel.onEvent(ChatEvent.Stopped)

                assertEquals(emptyList<Pair<Long, FormattedText>>(), messages.drafts)
            }

        @Test
        fun `a flood wait brings the text back with a snackbar`() =
            runViewModelTest {
                messages.onSendText = { Outcome.Failure(DomainError.FloodWait(30)) }
                val viewModel = privateChat()
                viewModel.composerText.setContent(formatted)

                viewModel.effects.test {
                    send(viewModel, ChatEvent.Send())

                    assertEquals(ChatEffect.ShowError(DomainError.FloodWait(30)), awaitItem())
                }
                assertEquals(formatted, viewModel.composed)
            }

        @Test
        fun `no connection brings the text back with a snackbar`() =
            runViewModelTest {
                messages.onSendText = { Outcome.Failure(DomainError.Network) }
                val viewModel = privateChat()
                type(viewModel, "Offline")

                viewModel.effects.test {
                    send(viewModel, ChatEvent.Send())

                    assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem())
                }
                assertEquals(text("Offline"), viewModel.composed)
            }

        @Test
        fun `a failure keeps what was typed meanwhile`() =
            runViewModelTest {
                messages.onSendText = {
                    Outcome.Failure(DomainError.Unauthorized)
                }
                val viewModel = privateChat()
                type(viewModel, "First")
                viewModel.onEvent(ChatEvent.Send())
                // Typed before the answer.
                viewModel.composerText.state.edit { replace(0, length, "Second") }

                runCurrent()

                assertEquals(text("Second"), viewModel.composed)
            }
    }

    @Nested
    inner class ProcessDeath {
        @Test
        fun `the text and its formatting survive`() =
            runViewModelTest {
                val before = privateChat()
                before.composerText.setContent(formatted)
                Snapshot.sendApplyNotifications()
                runCurrent()
                clear(before)

                val after =
                    viewModel(handle = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

                assertEquals(formatted, after.composed)
            }

        @Test
        fun `a restored text wins over the chat's draft`() =
            runViewModelTest {
                chats.setChats(chat(draft = draft("Theirs")))
                messages.setHistory(PRIVATE_CHAT, history(message(1)))
                savedState[ChatComposer.KEY_TEXT] = "Mine"

                assertEquals(text("Mine"), viewModel().composed)
            }
    }

    private companion object {
        const val SCHEDULED_AT = 1_800_000_000L
    }
}
