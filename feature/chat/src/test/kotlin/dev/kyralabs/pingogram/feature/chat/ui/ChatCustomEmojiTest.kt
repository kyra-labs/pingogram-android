package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.snapshots.Snapshot
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatDraft
import dev.kyralabs.pingogram.core.domain.model.CustomEmoji
import dev.kyralabs.pingogram.core.domain.model.DraftContent
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.InteractionInfo
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Reaction
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The custom emoji of the conversation ([ChatCustomEmoji]): those of the messages on screen and of the composer go to
 * the repository, at most every 100 ms, never the whole history's; what it loaded is the view model's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatCustomEmojiTest : ChatViewModelHarness() {
    /** "Hi 🎉" with the party popper as custom emoji [id]. */
    private fun withEmoji(id: Long) =
        FormattedText("Hi 🎉", persistentListOf(TextEntity(3, 2, TextEntityType.CustomEmoji(id))))

    private fun emojiMessage(
        id: Long,
        customEmojiId: Long,
    ) = message(id, content = MessageContent.Text(withEmoji(customEmojiId), linkPreview = null))

    private fun TestScope.visible(
        viewModel: ChatViewModel,
        vararg ids: Long,
    ) {
        send(viewModel, ChatEvent.MessagesVisible(ids.toSet()))
        advanceTimeBy(BATCH_WINDOW_MILLIS)
        runCurrent()
    }

    @Test
    fun `loads the custom emoji of the messages on screen, not of the rest of the history`() =
        runViewModelTest {
            val viewModel =
                showing(chat(), history(emojiMessage(1, customEmojiId = 10), emojiMessage(2, customEmojiId = 20)))

            visible(viewModel, 2)

            assertEquals(listOf(setOf(20L)), customEmoji.loads)
        }

    @Test
    fun `takes those of captions and reactions too`() =
        runViewModelTest {
            val photo = ContentPreviewData.photo(minithumbnail = null).copy(caption = withEmoji(30))
            val reactions =
                persistentListOf(Reaction(ReactionType.CustomEmoji(40), totalCount = 1, isChosen = false))
            val info = InteractionInfo(viewCount = 0, forwardCount = 0, replyCount = null, reactions = reactions)
            val viewModel = showing(chat(), history(message(1, content = photo, interactionInfo = info)))

            visible(viewModel, 1)

            assertEquals(listOf(setOf(30L, 40L)), customEmoji.loads)
        }

    @Test
    fun `asks at most every 100 ms while the list scrolls, with what is on screen then`() =
        runViewModelTest {
            val messages = (1L..5L).map { emojiMessage(it, customEmojiId = it * 10) }
            val viewModel = showing(chat(), history(*messages.toTypedArray()))

            send(viewModel, ChatEvent.MessagesVisible(setOf(1L)))
            send(viewModel, ChatEvent.MessagesVisible(setOf(1L, 2L)))
            send(viewModel, ChatEvent.MessagesVisible(setOf(2L, 3L)))
            advanceTimeBy(BATCH_WINDOW_MILLIS)
            runCurrent()

            assertEquals(listOf(setOf(10L), setOf(20L, 30L)), customEmoji.loads)
        }

    @Test
    fun `loads the custom emoji of the composer's draft`() =
        runViewModelTest {
            val draft = ChatDraft(DraftContent.Text(withEmoji(50)), date = 0)
            val viewModel = showing(chat(draft = draft), history(message(1)))
            Snapshot.sendApplyNotifications()
            runCurrent()
            advanceTimeBy(BATCH_WINDOW_MILLIS)
            runCurrent()

            assertEquals("Hi 🎉", viewModel.composerText.content.text)
            assertTrue(customEmoji.loads.any { 50L in it }, "${customEmoji.loads}")
        }

    @Test
    fun `what the repository loaded is the view model's`() =
        runViewModelTest {
            val party = CustomEmoji(10, ContentPreviewData.sticker(StickerFormat.Tgs).sticker, needsRepainting = false)
            customEmoji.known = mapOf(10L to party)
            val viewModel = showing(chat(), history(emojiMessage(1, customEmojiId = 10)))

            visible(viewModel, 1)

            assertEquals(mapOf(10L to party), viewModel.customEmoji.value)
        }

    @Test
    fun `a failure leaves the fallback emoji, and the ids are asked for again when they show again`() =
        runViewModelTest {
            customEmoji.failure = DomainError.Network
            val viewModel = showing(chat(), history(emojiMessage(1, customEmojiId = 10), message(2)))

            visible(viewModel, 1)
            visible(viewModel, 2)
            visible(viewModel, 1)

            assertTrue(viewModel.customEmoji.value.isEmpty())
            assertEquals(listOf(setOf(10L), setOf(10L)), customEmoji.loads.filter { it.isNotEmpty() })
        }

    @Test
    fun `messages without custom emoji ask for nothing`() =
        runViewModelTest {
            val viewModel = showing(chat(), history(message(1), message(2)))

            visible(viewModel, 1, 2)

            assertEquals(emptyList<Set<Long>>(), customEmoji.loads)
            assertEquals(
                emptyList<CustomEmoji>(),
                viewModel.customEmoji.value.values
                    .toImmutableList(),
            )
        }

    private companion object {
        const val BATCH_WINDOW_MILLIS = 100L
    }
}
