package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.attachment.FakeContentSource
import dev.kyralabs.pingogram.core.data.attachment.FakeMediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.ImageInfo
import dev.kyralabs.pingogram.core.data.attachment.Orientation
import dev.kyralabs.pingogram.core.data.attachment.PhoneContact
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentThumbnail
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messages
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/** What [TdlibAttachmentRepository] sends for the attach menu: the exact contents, albums, replies and options. */
class TdlibAttachmentRepositoryTest {
    @TempDir
    lateinit var cacheDir: File

    private val client = FakeTdlibClient()
    private val content = FakeContentSource()
    private val media = FakeMediaProcessor()
    private val files by lazy { UploadFiles { cacheDir } }

    private fun TestScope.repository() =
        TdlibAttachmentRepository(
            client = client,
            files = files,
            content = content,
            media = media,
            io = StandardTestDispatcher(testScheduler),
            scope = backgroundScope,
        )

    private fun answerSends() {
        client.on(TdApi.SendMessage::class, TdResult.Success(message(id = -1)))
        client.on(TdApi.SendMessageAlbum::class) { album ->
            TdResult.Success(messages(*Array(album.inputMessageContents.size) { message(id = -1L - it) }))
        }
    }

    /** A copy in a directory of its own, as the preparer leaves it. */
    private fun copy(name: String): String = File(files.newUploadDirectory(), name).apply { writeText("data") }.path

    private fun photo(name: String = "photo.jpg") = Attachment.Photo(copy(name), 4, width = 1280, height = 960)

    private fun video() =
        Attachment.Video(
            path = copy("clip.mp4"),
            size = 4,
            width = 1080,
            height = 1920,
            duration = 12,
            thumbnail = AttachmentThumbnail("/cache/uploads/x/thumbnail.jpg", 180, 320),
        )

    private fun document(name: String = "report.pdf") = Attachment.Document(copy(name), 4, name, "application/pdf")

    private fun text(text: String) = FormattedText(text, persistentListOf())

    /** Each sent message as the kinds of its contents, and the index of the content with the caption. */
    private fun sentShape(): List<String> =
        client.sent.mapNotNull { function ->
            val contents =
                when (function) {
                    is TdApi.SendMessage -> listOf(function.inputMessageContent)
                    is TdApi.SendMessageAlbum -> function.inputMessageContents.toList()
                    else -> return@mapNotNull null
                }
            val kinds = contents.joinToString(",") { it.javaClass.simpleName.removePrefix("InputMessage") }
            val captioned = contents.indexOfFirst { it.caption?.text?.isNotEmpty() == true }
            "${function.javaClass.simpleName}($kinds; caption $captioned)"
        }

    private val TdApi.InputMessageContent.caption: TdApi.FormattedText?
        get() =
            when (this) {
                is TdApi.InputMessagePhoto -> caption
                is TdApi.InputMessageVideo -> caption
                is TdApi.InputMessageDocument -> caption
                else -> null
            }

    @Test
    fun `a photo goes as an inputMessagePhoto of its copy, with its size and caption`() =
        runTest {
            answerSends()
            val photo = photo()
            val caption = FormattedText("Look", persistentListOf(TextEntity(0, 4, TextEntityType.Bold)))

            val result = repository().send(CHAT, listOf(photo), caption)

            val request = client.sentOf<TdApi.SendMessage>().single()
            val content = request.inputMessageContent as TdApi.InputMessagePhoto
            val input = content.photo
            assertEquals(Outcome.Success(Unit), result)
            assertEquals(
                listOf<Any?>(CHAT, null, null, null),
                listOf(request.chatId, request.topicId, request.replyTo, request.replyMarkup),
            )
            assertEquals(photo.path, (input.photo as TdApi.InputFileLocal).path)
            assertEquals(
                listOf<Any?>(null, null, 0, 1280, 960),
                listOf(input.thumbnail, input.video, input.addedStickerFileIds.size, input.width, input.height),
            )
            assertEquals(
                listOf<Any?>("Look", 1, false, null, false),
                listOf(
                    content.caption.text,
                    content.caption.entities.size,
                    content.showCaptionAboveMedia,
                    content.selfDestructType,
                    content.hasSpoiler,
                ),
            )
        }

    @Test
    fun `a video goes streamable with its thumbnail, length and size as it plays`() =
        runTest {
            answerSends()
            val video = video()

            repository().send(CHAT, listOf(video), text(""))

            val content = client.sentOf<TdApi.SendMessage>().single().inputMessageContent as TdApi.InputMessageVideo
            val input = content.video
            assertEquals(video.path, (input.video as TdApi.InputFileLocal).path)
            val thumbnail = input.thumbnail
            assertEquals(
                listOf<Any?>("/cache/uploads/x/thumbnail.jpg", 180, 320),
                listOf((thumbnail.thumbnail as TdApi.InputFileLocal).path, thumbnail.width, thumbnail.height),
            )
            assertEquals(
                listOf<Any?>(null, 0, 0, 12, 1080, 1920, true),
                listOf(
                    input.cover,
                    input.startTimestamp,
                    input.addedStickerFileIds.size,
                    input.duration,
                    input.width,
                    input.height,
                    input.supportsStreaming,
                ),
            )
            assertNull(content.caption)
        }

    @Test
    fun `a file goes as a document with content type detection`() =
        runTest {
            answerSends()
            val picked = document()

            repository().send(CHAT, listOf(picked), text("Q3"))

            val content = client.sentOf<TdApi.SendMessage>().single().inputMessageContent as TdApi.InputMessageDocument
            assertEquals(picked.path, (content.document.document as TdApi.InputFileLocal).path)
            val document = content.document
            assertEquals(
                listOf<Any?>(null, false, "Q3"),
                listOf(document.thumbnail, document.disableContentTypeDetection, content.caption.text),
            )
        }

    @Test
    fun `photos and videos go as one album, the caption with the first one`() =
        runTest {
            answerSends()

            repository().send(CHAT, listOf(photo(), video(), photo()), text("Trip"))

            assertEquals(listOf("SendMessageAlbum(Photo,Video,Photo; caption 0)"), sentShape())
        }

    @Test
    fun `more than ten go as albums of ten`() =
        runTest {
            answerSends()

            repository().send(CHAT, List(12) { photo() }, text(""))

            val albums = client.sentOf<TdApi.SendMessageAlbum>().map { it.inputMessageContents.size }
            assertEquals(listOf(10, 2), albums)
        }

    @Test
    fun `one left over after an album of ten goes as a message of its own`() =
        runTest {
            answerSends()

            repository().send(CHAT, List(11) { photo() }, text("Caption"))

            assertEquals(
                listOf(
                    "SendMessageAlbum(Photo,Photo,Photo,Photo,Photo,Photo,Photo,Photo,Photo,Photo; caption 0)",
                    "SendMessage(Photo; caption -1)",
                ),
                sentShape(),
            )
        }

    @Test
    fun `documents go as an album of documents, the caption with the last one`() =
        runTest {
            answerSends()

            val documents = listOf(document("a.pdf"), document("b.pdf"), document("c.pdf"))

            repository().send(CHAT, documents, text("All three"))

            assertEquals(listOf("SendMessageAlbum(Document,Document,Document; caption 2)"), sentShape())
        }

    @Test
    fun `documents never share an album with photos`() =
        runTest {
            answerSends()

            repository().send(CHAT, listOf(photo(), photo(), document()), text("Mixed"))

            assertEquals(
                listOf("SendMessageAlbum(Photo,Photo; caption 0)", "SendMessage(Document; caption -1)"),
                sentShape(),
            )
        }

    @Test
    fun `the reply goes with the first message only, and the options with every one`() =
        runTest {
            answerSends()
            val options = SendOptions(silent = true, scheduledAt = 1_800_000_000)

            repository().send(CHAT, List(11) { photo() }, text(""), options, replyToMessageId = 42)

            val album = client.sentOf<TdApi.SendMessageAlbum>().single()
            val single = client.sentOf<TdApi.SendMessage>().single()
            assertEquals(42L, (album.replyTo as TdApi.InputMessageReplyToMessage).messageId)
            assertNull(single.replyTo)
            for (sent in listOf(album.options, single.options)) {
                assertTrue(sent.disableNotification)
                assertEquals(1_800_000_000, (sent.schedulingState as TdApi.MessageSchedulingStateSendAtDate).sendDate)
            }
        }

    @Test
    fun `a refused send fails and deletes the copies of what was not sent`() =
        runTest {
            val refusal = TdError(400, "Not enough rights to send photos to the chat")
            client.on(TdApi.SendMessageAlbum::class, TdResult.Failure(refusal))
            val photos = listOf(photo(), photo())

            val result = repository().send(CHAT, photos, text(""))
            runCurrent()

            assertEquals(Outcome.Failure(DomainError.SendingNotAllowed), result)
            assertTrue(photos.none { File(it.path).exists() })
        }

    @Test
    fun `a contact goes as an inputMessageContact without vCard`() =
        runTest {
            answerSends()

            repository().sendContact(CHAT, Contact("+15551234567", "Ada Lovelace", "", 0), replyToMessageId = 7)

            val request = client.sentOf<TdApi.SendMessage>().single()
            val contact = (request.inputMessageContent as TdApi.InputMessageContact).contact
            assertEquals(
                listOf<Any?>("+15551234567", "Ada Lovelace", "", "", 0L),
                listOf(contact.phoneNumber, contact.firstName, contact.lastName, contact.vcard, contact.userId),
            )
            assertEquals(7L, (request.replyTo as TdApi.InputMessageReplyToMessage).messageId)
        }

    @Test
    fun `a regular poll lets voters change their vote`() =
        runTest {
            answerSends()
            val poll =
                NewPoll(
                    "Lunch?",
                    persistentListOf("Pizza", "Sushi"),
                    isAnonymous = false,
                    allowsMultipleAnswers = true,
                    correctOptionIndex = null,
                )

            repository().sendPoll(CHAT, poll)

            val sent = client.sentOf<TdApi.SendMessage>().single().inputMessageContent as TdApi.InputMessagePoll
            assertEquals("Lunch?", sent.question.text)
            assertEquals(listOf("Pizza", "Sushi"), sent.options.map { it.text.text })
            assertTrue(sent.options.all { it.media == null })
            assertEquals(
                listOf<Any?>(null, null, false, true, true, false, 0, false, false, 0, 0, false),
                listOf(
                    sent.description,
                    sent.media,
                    sent.isAnonymous,
                    sent.allowsMultipleAnswers,
                    sent.allowsRevoting,
                    sent.membersOnly,
                    sent.countryCodes.size,
                    sent.shuffleOptions,
                    sent.hideResultsUntilCloses,
                    sent.openPeriod,
                    sent.closeDate,
                    sent.isClosed,
                ),
            )
            assertFalse((sent.type as TdApi.InputPollTypeRegular).allowAddingOptions)
        }

    @Test
    fun `a quiz has one correct option, one answer and final votes`() =
        runTest {
            answerSends()
            val options = persistentListOf("3", "4", "5")
            val quiz =
                NewPoll("2 + 2?", options, isAnonymous = true, allowsMultipleAnswers = true, correctOptionIndex = 1)

            repository().sendPoll(CHAT, quiz)

            val sent = client.sentOf<TdApi.SendMessage>().single().inputMessageContent as TdApi.InputMessagePoll
            val type = sent.type as TdApi.InputPollTypeQuiz
            assertArrayEquals(intArrayOf(1), type.correctOptionIds)
            assertEquals(listOf<Any?>("", null), listOf(type.explanation.text, type.explanationMedia))
            assertEquals(
                listOf(true, false, false),
                listOf(sent.isAnonymous, sent.allowsMultipleAnswers, sent.allowsRevoting),
            )
        }

    @Test
    fun `cancelling an upload deletes its messages for everyone`() =
        runTest {
            client.on(TdApi.DeleteMessages::class, TdResult.Success(TdApi.Ok()))

            val result = repository().cancelUpload(CHAT, listOf(-1L, -2L))

            val request = client.sentOf<TdApi.DeleteMessages>().single()
            assertEquals(Outcome.Success(Unit), result)
            assertEquals(listOf(-1L, -2L), request.messageIds.toList())
            assertTrue(request.revoke)
        }

    @Test
    fun `a picked contact is read with its name and number`() =
        runTest {
            content.contacts["content://phone/1"] = PhoneContact(" Ada Lovelace ", " +1 555 123 ")
            content.contacts["content://phone/2"] = PhoneContact("", "+44 20 7946")
            content.contacts["content://phone/3"] = PhoneContact("N".repeat(80), "+1")

            val repository = repository()

            assertEquals(
                Outcome.Success(Contact("+1 555 123", "Ada Lovelace", "", 0)),
                repository.readContact("content://phone/1"),
            )
            assertEquals(
                Outcome.Success(Contact("+44 20 7946", "+44 20 7946", "", 0)),
                repository.readContact("content://phone/2"),
            )
            assertEquals(64, ((repository.readContact("content://phone/3") as Outcome.Success).value.firstName.length))
            assertEquals(Outcome.Failure(DomainError.FileUnavailable), repository.readContact("content://phone/4"))
        }

    @Test
    fun `a camera photo is prepared and the camera's file deleted`() =
        runTest {
            val repository = repository()
            val shot = File((repository.newCameraFile() as Outcome.Success).value)
            shot.writeBytes(byteArrayOf(1))
            media.images[1] = ImageInfo(800, 600, Orientation.Normal)

            val photo = (repository.prepareCameraPhoto(shot.path) as Outcome.Success).value

            assertTrue(photo is Attachment.Photo)
            assertFalse(shot.exists())
        }

    @Test
    fun `only the camera's own files are prepared or deleted as camera photos`() =
        runTest {
            val elsewhere = File(cacheDir, "elsewhere.jpg").apply { writeBytes(byteArrayOf(1)) }
            val repository = repository()

            assertEquals(Outcome.Failure(DomainError.FileUnavailable), repository.prepareCameraPhoto(elsewhere.path))
            repository.discardCameraFile(elsewhere.path)
            runCurrent()

            assertTrue(elsewhere.exists())
        }

    @Test
    fun `discarding deletes the copies`() =
        runTest {
            val attachments = listOf(photo(), document())

            repository().discard(attachments)
            runCurrent()

            assertTrue(attachments.none { File(it.path).exists() })
        }

    private companion object {
        const val CHAT = 100L
    }
}
