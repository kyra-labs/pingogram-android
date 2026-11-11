package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.data.repository.TdlibWelcomeMessageRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakePickedPhotoRepository
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.chatinfo.welcome.EditorPhoto
import dev.kyralabs.pingogram.feature.profile.chatinfo.welcome.WelcomeDialog
import dev.kyralabs.pingogram.feature.profile.chatinfo.welcome.WelcomeMessagesEffect
import dev.kyralabs.pingogram.feature.profile.chatinfo.welcome.WelcomeMessagesEvent
import dev.kyralabs.pingogram.feature.profile.chatinfo.welcome.WelcomeMessagesUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.welcome.WelcomeMessagesViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [WelcomeMessagesViewModel] on the real TDLib repository: the messages and the switch, adding a text and a photo,
 * editing (formatting kept while the text is), deleting, turning off, errors and process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WelcomeMessagesViewModelTest : GroupViewModelTest() {
    private val photos = FakePickedPhotoRepository()

    private fun text(
        text: String,
        bold: Boolean = false,
    ): TdApi.MessageContent {
        val entities = if (bold) arrayOf(TdApi.TextEntity(0, text.length, TdApi.TextEntityTypeBold())) else emptyArray()
        return TdApi.MessageText(TdApi.FormattedText(text, entities), null, null)
    }

    private suspend fun TestScope.viewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        messages: Array<TdApi.WelcomeMessage> = emptyArray(),
    ): WelcomeMessagesViewModel {
        client.on(TdApi.LoadChatWelcomeMessages::class) {
            backgroundScope.launch { client.emit(TdApi.UpdateChatWelcomeMessages(CHAT_ID, messages)) }
            TdResult.Success(TdApi.Ok())
        }
        client.on(TdApi.GetOption::class, TdResult.Success(TdApi.OptionValueInteger(3)))
        val viewModel = WelcomeMessagesViewModel(CHAT_ID, handle, TdlibWelcomeMessageRepository(client), photos)
        created += viewModel
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    /** The state shown, once the main dispatcher ran what the last event changed. */
    private fun WelcomeMessagesViewModel.content() = uiState.value as WelcomeMessagesUiState.Content

    @Test
    fun `shows the messages, the limit, and the switch on while there are some`() =
        runTest {
            val viewModel =
                viewModel(
                    messages = arrayOf(TdApi.WelcomeMessage(1, text("Hi")), TdApi.WelcomeMessage(2, text("Rules"))),
                )
            with(viewModel.content()) {
                assertEquals(listOf("Hi", "Rules"), messages.map { it.text.text })
                assertEquals(3, countMax)
                assertTrue(isOn)
                assertTrue(canAdd)
            }
        }

    @Test
    fun `turning it on opens the editor, and adding a text sends it`() =
        runTest {
            client.on(TdApi.AddChatWelcomeMessage::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            assertFalse(viewModel.content().isOn)
            val effects = mutableListOf<WelcomeMessagesEffect>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }

            viewModel.onEvent(WelcomeMessagesEvent.Toggle(on = true))
            runCurrent()
            assertNull(viewModel.content().editor?.messageId)
            viewModel.onEvent(WelcomeMessagesEvent.TextChanged("Welcome!"))
            runCurrent()
            viewModel.onEvent(WelcomeMessagesEvent.Save)
            runCurrent()

            val sent = client.sentOf<TdApi.AddChatWelcomeMessage>().single()
            assertEquals("Welcome!", (sent.inputMessageContent as TdApi.InputMessageText).text.text)
            assertNull(viewModel.content().editor)
            assertEquals(listOf(WelcomeMessagesEffect.Notice(R.string.welcome_added)), effects)
        }

    @Test
    fun `a picked photo goes with its caption, and its copy is discarded once Telegram answered`() =
        runTest {
            client.on(TdApi.AddChatWelcomeMessage::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            viewModel.onEvent(WelcomeMessagesEvent.Add)
            runCurrent()
            viewModel.onEvent(WelcomeMessagesEvent.PhotoPicked("content://rain"))
            runCurrent()
            assertEquals(EditorPhoto.Picked("/cache/uploads/content://rain.jpg"), viewModel.content().editor?.photo)
            viewModel.onEvent(WelcomeMessagesEvent.TextChanged("Rain"))
            runCurrent()
            viewModel.onEvent(WelcomeMessagesEvent.Save)
            runCurrent()

            val sent =
                client.sentOf<TdApi.AddChatWelcomeMessage>().single().inputMessageContent as TdApi.InputMessagePhoto
            assertEquals("Rain", sent.caption.text)
            assertEquals(listOf("/cache/uploads/content://rain.jpg"), photos.discarded)
        }

    @Test
    fun `editing keeps the formatting while the text is unchanged, and drops it when it changes`() =
        runTest {
            client.on(TdApi.EditChatWelcomeMessage::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel(messages = arrayOf(TdApi.WelcomeMessage(4, text("Hi", bold = true))))
            viewModel.onEvent(WelcomeMessagesEvent.Edit(4))
            runCurrent()
            assertTrue(viewModel.content().editor!!.isFormatted)
            viewModel.onEvent(WelcomeMessagesEvent.Save)
            runCurrent()
            viewModel.onEvent(WelcomeMessagesEvent.Edit(4))
            runCurrent()
            viewModel.onEvent(WelcomeMessagesEvent.TextChanged("Hello"))
            runCurrent()
            viewModel.onEvent(WelcomeMessagesEvent.Save)
            runCurrent()

            val (kept, changed) =
                client.sentOf<TdApi.EditChatWelcomeMessage>().map {
                    it.inputMessageContent as TdApi.InputMessageText
                }
            assertEquals(1, kept.text.entities.size)
            assertEquals("Hello", changed.text.text)
            assertEquals(0, changed.text.entities.size)
        }

    @Test
    fun `a refusal stays in the editor with what was typed`() =
        runTest {
            client.on(TdApi.AddChatWelcomeMessage::class, TdResult.Failure(TdError(400, "MESSAGE_TOO_LONG")))
            val viewModel = viewModel()
            viewModel.onEvent(WelcomeMessagesEvent.Add)
            runCurrent()
            viewModel.onEvent(WelcomeMessagesEvent.TextChanged("Too long"))
            runCurrent()
            viewModel.onEvent(WelcomeMessagesEvent.Save)
            runCurrent()

            with(viewModel.content().editor!!) {
                assertEquals("Too long", text)
                assertFalse(isSaving)
                assertTrue(error != null)
            }
        }

    @Test
    fun `deleting one asks first, and turning off deletes them all`() =
        runTest {
            client.on(TdApi.DeleteChatWelcomeMessage::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.DeleteAllChatWelcomeMessages::class, TdResult.Success(TdApi.Ok()))
            val viewModel =
                viewModel(
                    messages = arrayOf(TdApi.WelcomeMessage(1, text("Hi")), TdApi.WelcomeMessage(2, text("Rules"))),
                )

            viewModel.onEvent(WelcomeMessagesEvent.Delete(2))
            runCurrent()
            assertEquals(WelcomeDialog.Delete(2, position = 2), viewModel.content().dialog)
            assertTrue(client.sentOf<TdApi.DeleteChatWelcomeMessage>().isEmpty())
            viewModel.onEvent(WelcomeMessagesEvent.ConfirmDialog)
            runCurrent()
            assertEquals(2, client.sentOf<TdApi.DeleteChatWelcomeMessage>().single().welcomeMessageId)

            viewModel.onEvent(WelcomeMessagesEvent.Toggle(on = false))
            runCurrent()
            assertEquals(WelcomeDialog.TurnOff, viewModel.content().dialog)
            viewModel.onEvent(WelcomeMessagesEvent.ConfirmDialog)
            runCurrent()
            assertEquals(CHAT_ID, client.sentOf<TdApi.DeleteAllChatWelcomeMessages>().single().chatId)
        }

    @Test
    fun `the limit stops adding`() =
        runTest {
            val full = Array(3) { TdApi.WelcomeMessage(it + 1, text("Message $it")) }
            val viewModel = viewModel(messages = full)
            assertFalse(viewModel.content().canAdd)
            viewModel.onEvent(WelcomeMessagesEvent.Add)
            runCurrent()
            assertNull(viewModel.content().editor)
        }

    @Test
    fun `the editor and what was typed survive process death`() =
        runTest {
            val handle = SavedStateHandle()
            val first = viewModel(handle, arrayOf(TdApi.WelcomeMessage(4, text("Hi"))))
            first.onEvent(WelcomeMessagesEvent.Edit(4))
            runCurrent()
            first.onEvent(WelcomeMessagesEvent.TextChanged("Hi there"))
            runCurrent()

            val restored =
                viewModel(
                    SavedStateHandle(
                        handle.keys().associateWith {
                            handle.get<Any>(it)
                        },
                    ),
                    arrayOf(TdApi.WelcomeMessage(4, text("Hi"))),
                )
            with(restored.content().editor!!) {
                assertEquals(4, messageId)
                assertEquals("Hi there", text)
            }
        }

    @Test
    fun `a failure to load shows the error, and trying again loads once more`() =
        runTest {
            client.on(TdApi.GetOption::class, TdResult.Success(TdApi.OptionValueEmpty()))
            client.on(TdApi.LoadChatWelcomeMessages::class, TdResult.Failure(TdError(400, "CHAT_ADMIN_REQUIRED")))
            val viewModel =
                WelcomeMessagesViewModel(CHAT_ID, SavedStateHandle(), TdlibWelcomeMessageRepository(client), photos)
            created += viewModel
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
            runCurrent()
            assertInstanceOf(WelcomeMessagesUiState.Error::class.java, viewModel.uiState.value)

            viewModel.onEvent(WelcomeMessagesEvent.Retry)
            runCurrent()
            assertEquals(2, client.sentOf<TdApi.LoadChatWelcomeMessages>().size)
        }

    private companion object {
        const val CHAT_ID = -100_777L
    }
}
