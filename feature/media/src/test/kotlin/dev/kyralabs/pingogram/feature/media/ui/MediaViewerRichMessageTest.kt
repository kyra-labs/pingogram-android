package dev.kyralabs.pingogram.feature.media.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.RichBlock
import dev.kyralabs.pingogram.core.domain.model.RichCaption
import dev.kyralabs.pingogram.core.domain.model.RichMessage
import dev.kyralabs.pingogram.core.domain.model.richMediaId
import dev.kyralabs.pingogram.feature.media.ui.FakeMessageActionRepository.Companion.properties
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.CHAT
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.message
import dev.kyralabs.pingogram.feature.media.ui.MediaFixtures.photoContent
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The media viewer opened on a rich message's photo: it pages through that message's media only, in their order, the
 * actions that need a message (Forward, "Show in chat", saving) acting on the rich message, without Delete, which would
 * delete the whole message; deleting that message elsewhere takes its media with it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MediaViewerRichMessageTest : MediaViewerViewModelHarness() {
    private val first = richMediaId(POST, 0)
    private val second = richMediaId(POST, 1)

    @BeforeEach
    fun serveRichMessage() {
        fun photo(index: Int) =
            RichBlock.Photo(
                index,
                photoContent(index + 1L).photo,
                RichCaption(FormattedText("Photo $index", persistentListOf()), FormattedText.Empty),
                "",
                hasSpoiler = false,
            )
        val rich =
            RichMessage(
                persistentListOf(RichBlock.Title(FormattedText("News", persistentListOf())), photo(0), photo(1)),
                isRtl = false,
                isFull = true,
            )
        media.richMessages[POST] = message(POST, MessageContent.RichMessage(rich))
    }

    @Test
    fun `opens on the tapped media among the message's media alone, with nothing more to load`() =
        runViewModelTest {
            val viewModel = viewModel(messageId = second)

            assertEquals(listOf(first, second), viewModel.ids)
            assertEquals(second, viewModel.current)
            assertEquals("Photo 1", viewModel.content.current.caption.text)
            assertEquals(listOf("rich:$POST"), media.calls)
        }

    @Test
    fun `forward and show in chat act on the rich message, and delete never shows`() =
        runViewModelTest {
            messageActions.onProperties = { Outcome.Success(properties(forward = true)) }
            val viewModel = viewModel(messageId = first)

            viewModel.effects.test {
                send(viewModel, MediaViewerEvent.Forward, MediaViewerEvent.ShowInChat)

                assertEquals(MediaViewerEffect.Forward(CHAT, POST), awaitItem())
                assertEquals(MediaViewerEffect.ShowInChat(CHAT, POST), awaitItem())
            }
            assertTrue(viewModel.content.actions.canForward)
            assertFalse(viewModel.content.actions.canDelete)
            assertEquals(listOf(POST), messageActions.propertyChecks.distinct())
            assertEquals(listOf(POST), media.saveChecks.distinct())
        }

    @Test
    fun `the rich message deleted elsewhere takes its media with it`() =
        runViewModelTest {
            val viewModel = viewModel(messageId = first)

            media.deletions.emit(setOf(POST))
            runCurrent()

            assertEquals(MediaViewerUiState.Unavailable, viewModel.state)
        }

    private companion object {
        const val POST = 9L shl 20
    }
}
