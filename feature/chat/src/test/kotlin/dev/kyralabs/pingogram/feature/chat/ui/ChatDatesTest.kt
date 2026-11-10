package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.DateTimeFormat
import dev.kyralabs.pingogram.core.domain.model.DateTimePrecision
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Dates in messages in [ChatViewModel] (td_api.tl `textEntityTypeDateTime`): the bubble shows them in the reader's
 * zone and format, the entities after them following; one without a format keeps its words; a tap opens the date's
 * sheet, kept across process death; "Add to calendar" and "Copy" hand the moment on and close the sheet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatDatesTest : ChatViewModelHarness() {
    private val effects = mutableListOf<ChatEffect>()

    private fun TestScope.conversation(
        text: FormattedText,
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        chats.setChats(chat())
        messages.setHistory(
            PRIVATE_CHAT,
            history(message(MESSAGE, content = MessageContent.Text(text, linkPreview = null))),
        )
        val viewModel = viewModel(PRIVATE_CHAT, handle = handle)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.collect { effects += it } }
        runCurrent()
        return viewModel
    }

    private fun dated(
        format: DateTimeFormat?,
        written: String = "6 pm",
    ): FormattedText {
        val text = "Meet at $written with @ada"
        return FormattedText(
            text,
            persistentListOf(
                TextEntity(text.indexOf(written), written.length, TextEntityType.DateTime(MOMENT, format)),
                TextEntity(text.indexOf("@ada"), 4, TextEntityType.Mention),
            ),
        )
    }

    @Test
    fun `the bubble shows the date in the reader's zone and format, and the mention after it follows`() =
        runViewModelTest {
            val viewModel =
                conversation(
                    dated(
                        DateTimeFormat.Absolute(
                            DateTimePrecision.Short,
                            DateTimePrecision.Short,
                            showDayOfWeek = false,
                        ),
                    ),
                )

            val text = viewModel.bubble(MESSAGE).text

            assertEquals("Meet at 12/6/2026, 6:00 PM with @ada", text.text)
            val mention = text.entities.single { it.type == TextEntityType.Mention }
            assertEquals("@ada", text.text.substring(mention.offset, mention.offset + mention.length))
        }

    @Test
    fun `a date without a format keeps its words`() =
        runViewModelTest {
            val viewModel = conversation(dated(format = null))

            assertEquals("Meet at 6 pm with @ada", viewModel.bubble(MESSAGE).text.text)
        }

    @Test
    fun `a relative date the platform cannot word shows in full`() =
        runViewModelTest {
            val viewModel = conversation(dated(DateTimeFormat.Relative))

            assertEquals("Meet at Sunday, December 6, 2026, 6:00 PM with @ada", viewModel.bubble(MESSAGE).text.text)
        }

    @Test
    fun `a tap opens the date's sheet, which comes back after process death`() =
        runViewModelTest {
            val viewModel = conversation(dated(format = null))

            send(viewModel, DateTimeEvent.Open(MOMENT))
            val restored =
                conversation(
                    dated(format = null),
                    handle = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }),
                )

            assertEquals("Sunday, December 6, 2026, 6:00 PM", viewModel.dateTimeSheet.value?.text)
            assertEquals(MOMENT, restored.dateTimeSheet.value?.unixTime)
        }

    @Test
    fun `Add to calendar hands the moment on, Copy its words, and both close the sheet`() =
        runViewModelTest {
            val viewModel = conversation(dated(format = null))

            send(viewModel, DateTimeEvent.Open(MOMENT), DateTimeEvent.AddToCalendar)
            val afterCalendar = viewModel.dateTimeSheet.value
            send(viewModel, DateTimeEvent.Open(MOMENT), DateTimeEvent.Copy)

            assertNull(afterCalendar)
            assertNull(viewModel.dateTimeSheet.value)
            assertEquals(
                listOf(ChatEffect.AddToCalendar(MOMENT), ChatEffect.CopyText("Sunday, December 6, 2026, 6:00 PM")),
                effects,
            )
        }

    @Test
    fun `dismissing closes the sheet`() =
        runViewModelTest {
            val viewModel = conversation(dated(format = null))

            send(viewModel, DateTimeEvent.Open(MOMENT), DateTimeEvent.Dismiss)

            assertNull(viewModel.dateTimeSheet.value)
            assertNull(savedState.get<Long>(dev.kyralabs.pingogram.feature.chat.ui.dates.ChatDateTimes.KEY_OPEN))
        }

    private companion object {
        const val MESSAGE = 10L

        /** 2026-12-06 18:00 UTC: Sunday. */
        const val MOMENT = 1_796_580_000L
    }
}
