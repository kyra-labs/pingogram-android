package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.WelcomeDraft
import dev.kyralabs.pingogram.core.domain.model.WelcomeLimits
import dev.kyralabs.pingogram.core.domain.model.WelcomeMessage
import dev.kyralabs.pingogram.core.domain.model.WelcomePhoto
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Welcome messages: loading and following them, the limit, adding, editing and deleting. */
@OptIn(ExperimentalCoroutinesApi::class)
class TdlibWelcomeMessageRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibWelcomeMessageRepository(client)

    private fun text(text: String) = TdApi.MessageText(TdApi.FormattedText(text, emptyArray()), null, null)

    @Test
    fun `loads the welcome messages, then follows their updates`() =
        runTest {
            client.on(TdApi.LoadChatWelcomeMessages::class, TdResult.Success(TdApi.Ok()))
            val seen = mutableListOf<Outcome<List<WelcomeMessage>>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.messages(CHAT).take(2).toList(seen)
            }
            runCurrent()
            assertEquals(listOf(CHAT), client.sentOf<TdApi.LoadChatWelcomeMessages>().map { it.chatId })

            client.emit(TdApi.UpdateChatWelcomeMessages(OTHER_CHAT, arrayOf(TdApi.WelcomeMessage(9, text("No")))))
            client.emit(TdApi.UpdateChatWelcomeMessages(CHAT, arrayOf(TdApi.WelcomeMessage(1, text("Hi")))))
            val dice = TdApi.MessageDice(null, null, "🎲", 3, 6)
            client.emit(
                TdApi.UpdateChatWelcomeMessages(
                    CHAT,
                    arrayOf(TdApi.WelcomeMessage(1, text("Hi")), TdApi.WelcomeMessage(2, dice)),
                ),
            )
            runCurrent()

            val (first, second) = seen.map { (it as Outcome.Success).value }
            assertEquals(listOf("Hi"), first.map { it.text.text })
            assertEquals(listOf(1, 2), second.map { it.id })
            assertFalse(second[1].isSupported)
        }

    @Test
    fun `a photo's caption is its text`() =
        runTest {
            client.on(TdApi.LoadChatWelcomeMessages::class, TdResult.Success(TdApi.Ok()))
            val seen = mutableListOf<Outcome<List<WelcomeMessage>>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                repository.messages(CHAT).take(1).toList(seen)
            }
            val size = TdApi.PhotoSize("x", TdApi.File().apply { id = 5 }, 800, 600, intArrayOf())
            val caption = TdApi.FormattedText("Rain", emptyArray())
            val photo = TdApi.MessagePhoto(TdApi.Photo(false, null, arrayOf(size)), null, caption, false, false, false)
            client.emit(TdApi.UpdateChatWelcomeMessages(CHAT, arrayOf(TdApi.WelcomeMessage(3, photo))))
            runCurrent()

            val message = (seen.single() as Outcome.Success).value.single()
            assertEquals("Rain", message.text.text)
            assertEquals(
                5,
                message.photo
                    ?.sizes
                    ?.single()
                    ?.file
                    ?.id,
            )
        }

    @Test
    fun `a failure to load comes as a failure`() =
        runTest {
            client.on(TdApi.LoadChatWelcomeMessages::class, TdResult.Failure(TdError(400, "CHAT_ADMIN_REQUIRED")))
            assertTrue(repository.messages(CHAT).first() is Outcome.Failure)
        }

    @Test
    fun `the limit is TDLib's option, or Telegram's five`() =
        runTest {
            client.on(TdApi.GetOption::class, TdResult.Success(TdApi.OptionValueInteger(8)))
            assertEquals(8, repository.countMax())
            client.on(TdApi.GetOption::class, TdResult.Success(TdApi.OptionValueEmpty()))
            assertEquals(WelcomeLimits.COUNT_MAX_DEFAULT, repository.countMax())
            assertEquals("welcome_message_count_max", client.sentOf<TdApi.GetOption>().first().name)
        }

    @Test
    fun `adds a text and a picked photo with its caption`() =
        runTest {
            client.on(TdApi.AddChatWelcomeMessage::class, TdResult.Success(TdApi.Ok()))
            repository.add(CHAT, WelcomeDraft(FormattedText("Welcome!", persistentListOf())))
            val caption = FormattedText("Rain", persistentListOf())
            repository.add(CHAT, WelcomeDraft(caption, WelcomePhoto.Picked("/up/p.jpg")))

            val (text, photo) = client.sentOf<TdApi.AddChatWelcomeMessage>().map { it.inputMessageContent }
            assertEquals("Welcome!", (text as TdApi.InputMessageText).text.text)
            photo as TdApi.InputMessagePhoto
            assertEquals("/up/p.jpg", (photo.photo.photo as TdApi.InputFileLocal).path)
            assertEquals("Rain", photo.caption.text)
        }

    @Test
    fun `a blank text without a photo is refused before TDLib`() =
        runTest {
            val result = repository.add(CHAT, WelcomeDraft(FormattedText("  ", persistentListOf())))
            assertEquals(Outcome.Failure(DomainError.InputRejected), result)
            assertTrue(client.sent.isEmpty())
        }

    @Test
    fun `editing keeps the message's photo by its largest size`() =
        runTest {
            client.on(TdApi.EditChatWelcomeMessage::class, TdResult.Success(TdApi.Ok()))
            val photo =
                Photo(
                    persistentListOf(PhotoSize("m", 320, 240, FileRef(4, 0)), PhotoSize("x", 800, 600, FileRef(5, 0))),
                    null,
                )
            repository.edit(CHAT, 3, WelcomeDraft(FormattedText("New", persistentListOf()), WelcomePhoto.Kept(photo)))

            val sent = client.sentOf<TdApi.EditChatWelcomeMessage>().single()
            assertEquals(3, sent.welcomeMessageId)
            val input = (sent.inputMessageContent as TdApi.InputMessagePhoto).photo
            assertEquals(5, (input.photo as TdApi.InputFileId).id)
            assertEquals(800, input.width)
        }

    @Test
    fun `deletes one welcome message, or all of them`() =
        runTest {
            client.on(TdApi.DeleteChatWelcomeMessage::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.DeleteAllChatWelcomeMessages::class, TdResult.Success(TdApi.Ok()))
            assertTrue(repository.delete(CHAT, 2) is Outcome.Success)
            assertTrue(repository.deleteAll(CHAT) is Outcome.Success)
            assertEquals(2, client.sentOf<TdApi.DeleteChatWelcomeMessage>().single().welcomeMessageId)
            assertEquals(CHAT, client.sentOf<TdApi.DeleteAllChatWelcomeMessages>().single().chatId)
        }

    private companion object {
        const val CHAT = -100L
        const val OTHER_CHAT = -200L
    }
}
