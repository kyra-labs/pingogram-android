package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.AttachmentThumbnail
import dev.kyralabs.pingogram.core.domain.model.ChatDraft
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.core.domain.model.DraftContent
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FileUpload
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Location
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.CHANNEL_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.basicGroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.supergroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.FakeAttachmentRepository.Companion.photo
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import dev.kyralabs.pingogram.feature.chat.ui.attach.MessageUpload
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollSheet
import dev.kyralabs.pingogram.feature.chat.ui.attach.PreviewItem
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The attach menu's part of [ChatViewModel] (`REQUIREMENTS.md` FR-C5): what the attach button offers, the preview of
 * picked items, sending them with the composer's reply, the camera, contacts, polls, uploads and their cancelling,
 * failures, and process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatAttachmentsTest : ChatViewModelHarness() {
    /** A view model of the private chat with Ada, replying to message 1 if [replyTo] is set (through the draft). */
    private fun TestScope.privateChat(
        replyTo: Long? = null,
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        val draft = replyTo?.let { ChatDraft(DraftContent.Text(FormattedText.Empty), 0, it) }
        chats.setChats(chat(draft = draft))
        messages.setHistory(ADA, history(message(1)))
        return viewModel(handle = handle)
    }

    private val ChatViewModel.preview get() = state.attachPreview

    private val ChatViewModel.options get() = (state.composer as ComposerUiState.Available).attachOptions

    private val ChatViewModel.itemKinds: List<String>
        get() =
            preview?.items.orEmpty().map { item ->
                when (item) {
                    is PreviewItem.Preparing -> "preparing"
                    is PreviewItem.Ready -> item.attachment.path.substringAfterLast('/')
                }
            }

    private val ChatViewModel.replyTo get() = (state.composer as ComposerUiState.Available).bar?.messageId

    private fun TestScope.setCaption(
        viewModel: ChatViewModel,
        caption: String,
    ) {
        viewModel.attachCaption.state.setTextAndPlaceCursorAtEnd(caption)
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    @Nested
    inner class Options {
        @Test
        fun `a private chat offers everything but polls`() =
            runViewModelTest {
                assertEquals(
                    listOf(
                        AttachOption.Gallery,
                        AttachOption.Camera,
                        AttachOption.File,
                        AttachOption.Location,
                        AttachOption.Contact,
                    ),
                    privateChat().options,
                )
            }

        @Test
        fun `groups, bots and Saved Messages take polls too`() =
            runViewModelTest {
                groups.basicGroupMap.value = mapOf(ChatFixtures.BASIC_GROUP_ID to basicGroup(3))
                users.setUsers(user(ChatFixtures.GRACE, type = UserType.Bot))

                // Each chat is checked while it is the only one the fake knows.
                assertEquals(AttachOption.entries, showing(groupChat, history()).options)
                assertEquals(AttachOption.entries, showing(chat(id = ChatFixtures.GRACE), history()).options)
                assertEquals(AttachOption.entries, showing(chat(id = ME), history()).options)
            }

        @Test
        fun `secret chats take no polls`() =
            runViewModelTest {
                secretChats.setSecretChats(ChatFixtures.secretChat())
                val secret = showing(chat(type = ChatType.Secret(secretChatId = 7, userId = ADA)), history())

                assertTrue(AttachOption.Poll !in secret.options)
            }

        @Test
        fun `the attach button goes while a message is edited`() =
            runViewModelTest {
                messages.setHistory(ADA, history(ChatFixtures.outgoing(5)))
                chats.setChats(chat())
                val viewModel = viewModel()

                send(viewModel, ChatEvent.Edit(5))

                assertEquals(emptyList<AttachOption>(), viewModel.options)
            }
    }

    @Nested
    inner class Preview {
        @Test
        fun `picked photos show at once, each ready once it is prepared, in their order`() =
            runViewModelTest {
                val answers = mutableMapOf<String, CompletableDeferred<Outcome<Attachment>>>()
                attachments.onPrepare = { uri, _ -> answers.getOrPut(uri) { CompletableDeferred() }.await() }
                val viewModel = privateChat()

                send(viewModel, ChatEvent.AttachMedia(listOf("content://a", "content://b")))
                assertEquals(listOf("preparing", "preparing"), viewModel.itemKinds)
                answers.getValue("content://a").complete(Outcome.Success(photo("a")))
                runCurrent()

                assertEquals(listOf("a.jpg", "preparing"), viewModel.itemKinds)
                assertEquals(
                    listOf("content://a" to AttachmentKind.Media, "content://b" to AttachmentKind.Media),
                    attachments.prepared,
                )
                assertEquals(AttachmentKind.Media, viewModel.preview?.kind)
            }

        @Test
        fun `picked files are prepared as documents`() =
            runViewModelTest {
                val viewModel = privateChat()

                send(viewModel, ChatEvent.AttachFiles(listOf("content://report")))

                assertEquals(listOf("content://report" to AttachmentKind.Document), attachments.prepared)
                assertEquals(AttachmentKind.Document, viewModel.preview?.kind)
                assertTrue(viewModel.preview!!.isReady)
            }

        @Test
        fun `an item that cannot be prepared is dropped with a snackbar, and the last one closes the preview`() =
            runViewModelTest {
                attachments.onPrepare = { uri, _ ->
                    if (uri.endsWith("bad")) {
                        Outcome.Failure(DomainError.FileUnavailable)
                    } else {
                        Outcome.Success(photo(uri))
                    }
                }
                val viewModel = privateChat()

                viewModel.effects.test {
                    send(viewModel, ChatEvent.AttachMedia(listOf("content://good", "content://bad")))
                    assertEquals(ChatEffect.ShowError(DomainError.FileUnavailable), awaitItem())
                    assertEquals(listOf("good.jpg"), viewModel.itemKinds)

                    send(viewModel, ChatEvent.AttachMedia(listOf("content://bad")))
                    assertEquals(ChatEffect.ShowError(DomainError.FileUnavailable), awaitItem())
                }
                assertEquals(listOf("good.jpg"), viewModel.itemKinds)
            }

        @Test
        fun `a picked file too large for Telegram says so`() =
            runViewModelTest {
                attachments.onPrepare = { _, _ -> Outcome.Failure(DomainError.FileTooLarge) }
                val viewModel = privateChat()

                viewModel.effects.test {
                    send(viewModel, ChatEvent.AttachFiles(listOf("content://huge")))

                    assertEquals(ChatEffect.ShowError(DomainError.FileTooLarge), awaitItem())
                }
                assertNull(viewModel.preview)
            }

        @Test
        fun `removing an item deletes its copy, and removing the last closes the preview`() =
            runViewModelTest {
                val viewModel = privateChat()
                send(viewModel, ChatEvent.AttachMedia(listOf("content://a", "content://b")))
                val (first, second) = viewModel.preview!!.items

                send(viewModel, ChatEvent.RemoveAttachment(first.key))
                assertEquals(listOf("b.jpg"), viewModel.itemKinds)
                send(viewModel, ChatEvent.RemoveAttachment(second.key))

                assertNull(viewModel.preview)
                assertEquals(listOf(photo("a"), photo("b")), attachments.discarded)
            }

        @Test
        fun `dismissing the preview deletes the copies and stops what is being prepared`() =
            runViewModelTest {
                val never = CompletableDeferred<Outcome<Attachment>>()
                attachments.onPrepare = { uri, _ ->
                    if (uri.endsWith("slow")) never.await() else Outcome.Success(photo(uri))
                }
                val viewModel = privateChat()
                send(viewModel, ChatEvent.AttachMedia(listOf("content://a", "content://slow", "content://c")))
                setCaption(viewModel, "Never sent")

                send(viewModel, ChatEvent.DismissAttachments)

                assertNull(viewModel.preview)
                assertEquals(listOf(photo("a")), attachments.discarded)
                // The third one is never prepared.
                assertEquals(listOf("content://a", "content://slow"), attachments.prepared.map { it.first })
                assertEquals("", viewModel.attachCaption.content.text)
            }

        @Test
        fun `more photos join the preview, files from the file picker replace it`() =
            runViewModelTest {
                val viewModel = privateChat()

                send(viewModel, ChatEvent.AttachMedia(listOf("content://a")))
                send(viewModel, ChatEvent.AttachMedia(listOf("content://b")))
                assertEquals(listOf("a.jpg", "b.jpg"), viewModel.itemKinds)
                send(viewModel, ChatEvent.AttachFiles(listOf("content://report")))

                assertEquals(listOf("report.jpg"), viewModel.itemKinds)
                assertEquals(listOf(photo("a"), photo("b")), attachments.discarded)
            }

        @Test
        fun `the preview schedules where the composer does`() =
            runViewModelTest {
                secretChats.setSecretChats(ChatFixtures.secretChat())
                val secret = chat(type = ChatType.Secret(secretChatId = 7, userId = ADA))
                val viewModel = showing(secret, history())

                send(viewModel, ChatEvent.AttachMedia(listOf("content://a")))

                assertEquals(false, viewModel.preview?.canSchedule)
            }
    }

    @Nested
    inner class Sending {
        @Test
        fun `sending sends every item with the caption, the options and the composer's reply, then closes`() =
            runViewModelTest {
                val viewModel = privateChat(replyTo = 1)
                assertEquals(1L, viewModel.replyTo)
                send(viewModel, ChatEvent.AttachMedia(listOf("content://a", "content://b")))
                val caption = FormattedText("Trip", persistentListOf(TextEntity(0, 4, TextEntityType.Bold)))
                viewModel.attachCaption.setContent(caption)

                send(viewModel, ChatEvent.SendAttachments(SendOptions(silent = true)))

                val sent = attachments.sent.single()
                assertEquals(
                    FakeAttachmentRepository.SentAttachments(
                        ADA,
                        listOf(photo("a"), photo("b")),
                        caption,
                        SendOptions(silent = true),
                        replyToMessageId = 1,
                    ),
                    sent,
                )
                assertNull(viewModel.preview)
                assertNull(viewModel.replyTo)
                assertEquals("", viewModel.attachCaption.content.text)
                assertEquals(emptyList<Attachment>(), attachments.discarded)
            }

        @Test
        fun `nothing is sent while an item is being prepared`() =
            runViewModelTest {
                attachments.onPrepare = { _, _ -> CompletableDeferred<Outcome<Attachment>>().await() }
                val viewModel = privateChat()
                send(viewModel, ChatEvent.AttachMedia(listOf("content://a")))

                send(viewModel, ChatEvent.SendAttachments())

                assertEquals(emptyList<FakeAttachmentRepository.SentAttachments>(), attachments.sent)
                assertEquals(listOf("preparing"), viewModel.itemKinds)
            }

        @Test
        fun `a scheduled send is confirmed with its time`() =
            runViewModelTest {
                val viewModel = privateChat()
                send(viewModel, ChatEvent.AttachFiles(listOf("content://report")))

                viewModel.effects.test {
                    send(viewModel, ChatEvent.SendAttachments(SendOptions(scheduledAt = SCHEDULED_AT)))

                    assertEquals(ChatEffect.MessageScheduled(SCHEDULED_AT), awaitItem())
                }
            }

        @Test
        fun `a refused send says why and gives the reply back`() =
            runViewModelTest {
                attachments.onSend = { Outcome.Failure(DomainError.FloodWait(30)) }
                val viewModel = privateChat(replyTo = 1)
                send(viewModel, ChatEvent.AttachMedia(listOf("content://a")))

                viewModel.effects.test {
                    send(viewModel, ChatEvent.SendAttachments())

                    assertEquals(ChatEffect.ShowError(DomainError.FloodWait(30)), awaitItem())
                }
                assertEquals(1L, viewModel.replyTo)
            }

        @Test
        fun `no connection says so`() =
            runViewModelTest {
                attachments.onSend = { Outcome.Failure(DomainError.Network) }
                val viewModel = privateChat()
                send(viewModel, ChatEvent.AttachMedia(listOf("content://a")))

                viewModel.effects.test {
                    send(viewModel, ChatEvent.SendAttachments())

                    assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem())
                }
            }
    }

    @Nested
    inner class Camera {
        @Test
        fun `the camera opens on a new file, whose photo joins the preview`() =
            runViewModelTest {
                val viewModel = privateChat()

                viewModel.effects.test {
                    send(viewModel, ChatEvent.TakePhoto)

                    assertEquals(ChatEffect.LaunchCamera(FakeAttachmentRepository.CAMERA_FILE), awaitItem())
                }
                send(viewModel, ChatEvent.PhotoTaken(taken = true))

                assertEquals(listOf(FakeAttachmentRepository.CAMERA_FILE), attachments.preparedCameraPhotos)
                assertEquals(listOf("camera.jpg"), viewModel.itemKinds)
                assertEquals(AttachmentKind.Media, viewModel.preview?.kind)
            }

        @Test
        fun `no photo taken deletes the camera's file`() =
            runViewModelTest {
                val viewModel = privateChat()
                send(viewModel, ChatEvent.TakePhoto, ChatEvent.PhotoTaken(taken = false))

                assertEquals(listOf(FakeAttachmentRepository.CAMERA_FILE), attachments.discardedCameraFiles)
                assertNull(viewModel.preview)
            }

        @Test
        fun `the camera's file survives process death while the camera app is open`() =
            runViewModelTest {
                val handle = SavedStateHandle()
                send(privateChat(handle = handle), ChatEvent.TakePhoto)

                val restored = privateChat(handle = handle)
                send(restored, ChatEvent.PhotoTaken(taken = true))

                assertEquals(listOf(FakeAttachmentRepository.CAMERA_FILE), attachments.preparedCameraPhotos)
                assertEquals(listOf("camera.jpg"), restored.itemKinds)
            }
    }

    @Nested
    inner class Contacts {
        private val ada = Contact("+15551234567", "Ada Lovelace", "", 0)

        @Test
        fun `a picked contact is read and sent with the composer's reply`() =
            runViewModelTest {
                attachments.onReadContact = { Outcome.Success(ada) }
                val viewModel = privateChat(replyTo = 1)

                send(viewModel, ChatEvent.AttachContact("content://contacts/1"))

                assertEquals(listOf(ada to 1L), attachments.sentContacts)
                assertNull(viewModel.replyTo)
            }

        @Test
        fun `a contact that cannot be read is not sent, and the reply stays`() =
            runViewModelTest {
                val viewModel = privateChat(replyTo = 1)

                viewModel.effects.test {
                    send(viewModel, ChatEvent.AttachContact("content://contacts/1"))

                    assertEquals(ChatEffect.ShowError(DomainError.FileUnavailable), awaitItem())
                }
                assertTrue(attachments.sentContacts.isEmpty())
                assertEquals(1L, viewModel.replyTo)
            }
    }

    @Nested
    inner class Locations {
        private val here = DeviceLocation(Location(51.4978, -0.1745), accuracyMeters = 12f)

        @Test
        fun `the location is sent as asked, with the composer's reply`() =
            runViewModelTest {
                val viewModel = privateChat(replyTo = 1)
                val silent = SendOptions(silent = true)

                send(viewModel, ChatEvent.SendLocation(here, silent))

                assertEquals(listOf(Triple(here, silent, 1L)), attachments.sentLocations)
                assertNull(viewModel.replyTo)
            }

        @Test
        fun `a refused location gives the reply back, with the error`() =
            runViewModelTest {
                attachments.onSendLocation = { Outcome.Failure(DomainError.SendingNotAllowed) }
                val viewModel = privateChat(replyTo = 1)

                viewModel.effects.test {
                    send(viewModel, ChatEvent.SendLocation(here))

                    assertEquals(ChatEffect.ShowError(DomainError.SendingNotAllowed), awaitItem())
                }
                assertEquals(1L, viewModel.replyTo)
            }

        @Test
        fun `a scheduled location is confirmed with its time`() =
            runViewModelTest {
                val viewModel = privateChat()

                viewModel.effects.test {
                    send(viewModel, ChatEvent.SendLocation(here, SendOptions(scheduledAt = 1_800_000_000)))

                    assertEquals(ChatEffect.MessageScheduled(1_800_000_000), awaitItem())
                }
            }
    }

    @Nested
    inner class Stickers {
        private val duck =
            Sticker(
                setId = 7,
                width = 512,
                height = 512,
                emoji = "🦆",
                format = StickerFormat.Tgs,
                thumbnail = null,
                file = FileRef(70, 30_000),
            )

        @Test
        fun `a sticker from the panel is sent at once, with the composer's reply`() =
            runViewModelTest {
                val viewModel = privateChat(replyTo = 1)

                send(viewModel, ChatEvent.SendSticker(duck))

                assertEquals(listOf(Triple(duck, SendOptions(), 1L)), attachments.sentStickers)
                assertNull(viewModel.replyTo)
            }

        @Test
        fun `a refused sticker gives the reply back, with the error`() =
            runViewModelTest {
                attachments.onSendSticker = { Outcome.Failure(DomainError.FloodWait(30)) }
                val viewModel = privateChat(replyTo = 1)

                viewModel.effects.test {
                    send(viewModel, ChatEvent.SendSticker(duck))

                    assertEquals(ChatEffect.ShowError(DomainError.FloodWait(30)), awaitItem())
                }
                assertEquals(1L, viewModel.replyTo)
            }
    }

    @Nested
    inner class Polls {
        private fun TestScope.group(): ChatViewModel {
            groups.basicGroupMap.value = mapOf(ChatFixtures.BASIC_GROUP_ID to basicGroup(3))
            return showing(groupChat, history())
        }

        private fun TestScope.fill(
            viewModel: ChatViewModel,
            question: String,
            vararg options: String,
        ) {
            val editor = viewModel.pollEditor
            editor.question.setTextAndPlaceCursorAtEnd(question)
            options.forEachIndexed { index, option ->
                if (index >= editor.options.size) editor.addOption()
                editor.options[index].text.setTextAndPlaceCursorAtEnd(option)
            }
            Snapshot.sendApplyNotifications()
            runCurrent()
        }

        @Test
        fun `the poll form opens where polls can be sent`() =
            runViewModelTest {
                val viewModel = group()

                send(viewModel, ChatEvent.OpenPoll)

                assertEquals(PollSheet(isChannel = false, canSchedule = true), viewModel.state.poll)
            }

        @Test
        fun `a complete poll is sent, the form closing and emptying`() =
            runViewModelTest {
                val viewModel = group()
                send(viewModel, ChatEvent.OpenPoll)
                fill(viewModel, "Lunch?", "Pizza", "Sushi", "Salad")
                viewModel.pollEditor.allowsMultipleAnswers = true
                viewModel.pollEditor.isAnonymous = false

                send(viewModel, ChatEvent.SendPoll(SendOptions(silent = true)))

                val sent = attachments.sentPolls.single()
                assertEquals(
                    NewPoll(
                        "Lunch?",
                        persistentListOf("Pizza", "Sushi", "Salad"),
                        isAnonymous = false,
                        allowsMultipleAnswers = true,
                        correctOptionIndex = null,
                    ),
                    sent.poll,
                )
                assertEquals(SendOptions(silent = true), sent.options)
                assertNull(viewModel.state.poll)
                assertEquals(
                    "",
                    viewModel.pollEditor.question.text
                        .toString(),
                )
            }

        @Test
        fun `a quiz goes with its correct option`() =
            runViewModelTest {
                val viewModel = group()
                send(viewModel, ChatEvent.OpenPoll)
                fill(viewModel, "2 + 2?", "3", "4")
                val editor = viewModel.pollEditor
                editor.isQuiz = true
                send(viewModel, ChatEvent.SendPoll())
                assertTrue(attachments.sentPolls.isEmpty())

                editor.correctOption = editor.options[1].key
                send(viewModel, ChatEvent.SendPoll())

                assertEquals(
                    1,
                    attachments.sentPolls
                        .single()
                        .poll.correctOptionIndex,
                )
            }

        @Test
        fun `polls in a channel are anonymous`() =
            runViewModelTest {
                val admin = ChatMemberStatus.Administrator(canPostMessages = true)
                groups.supergroupMap.value = mapOf(ChatFixtures.SUPERGROUP_ID to supergroup(9, status = admin))
                val viewModel = showing(channelChat, history(message(1, CHANNEL_CHAT)))
                send(viewModel, ChatEvent.OpenPoll)
                fill(viewModel, "Next topic?", "A", "B")
                viewModel.pollEditor.isAnonymous = false

                assertEquals(true, viewModel.state.poll?.isChannel)
                send(viewModel, ChatEvent.SendPoll())

                assertTrue(
                    attachments.sentPolls
                        .single()
                        .poll.isAnonymous,
                )
            }

        @Test
        fun `a refused poll opens again as it was`() =
            runViewModelTest {
                attachments.onSendPoll = { Outcome.Failure(DomainError.SendingNotAllowed) }
                val viewModel = group()
                send(viewModel, ChatEvent.OpenPoll)
                fill(viewModel, "Lunch?", "Pizza", "Sushi")

                viewModel.effects.test {
                    send(viewModel, ChatEvent.SendPoll())

                    assertEquals(ChatEffect.ShowError(DomainError.SendingNotAllowed), awaitItem())
                }
                assertEquals(PollSheet(), viewModel.state.poll)
                assertEquals(
                    "Lunch?",
                    viewModel.pollEditor.question.text
                        .toString(),
                )
            }

        @Test
        fun `dismissing the form empties it`() =
            runViewModelTest {
                val viewModel = group()
                send(viewModel, ChatEvent.OpenPoll)
                fill(viewModel, "Lunch?", "Pizza")

                send(viewModel, ChatEvent.DismissPoll)

                assertNull(viewModel.state.poll)
                assertEquals(
                    "",
                    viewModel.pollEditor.question.text
                        .toString(),
                )
                assertEquals(listOf("", ""), viewModel.pollEditor.options.map { it.text.text.toString() })
            }

        @Test
        fun `the poll form never opens where polls cannot be sent`() =
            runViewModelTest {
                val viewModel = privateChat()

                send(viewModel, ChatEvent.OpenPoll)

                assertNull(viewModel.state.poll)
            }
    }

    @Nested
    inner class Uploads {
        private val photoContent =
            MessageContent.Photo(
                photo =
                    Photo(
                        sizes = persistentListOf(PhotoSize("i", 800, 600, FileRef(id = UPLOAD_FILE, size = 2_000))),
                        minithumbnail = null,
                    ),
                caption = FormattedText.Empty,
                hasSpoiler = false,
                isSecret = false,
                showCaptionAboveMedia = false,
            )

        private fun pending(id: Long) =
            message(id, isOutgoing = true, content = photoContent, sendingState = SendingState.Pending)

        @Test
        fun `a photo being sent shows its upload, unknown until TDLib reports it`() =
            runViewModelTest {
                val viewModel = showing(chat(), history(pending(-1)))
                assertEquals(mapOf(-1L to MessageUpload(progress = null)), viewModel.state.uploads)

                files.uploads.value = mapOf(UPLOAD_FILE to FileUpload(500, 2_000, isActive = true, isCompleted = false))
                runCurrent()

                assertEquals(mapOf(-1L to MessageUpload(progress = 0.25f)), viewModel.state.uploads)
                assertEquals(listOf(UPLOAD_FILE), files.observedUploads)
            }

        @Test
        fun `messages that are sent or received show no upload`() =
            runViewModelTest {
                val sent = message(2, isOutgoing = true, content = photoContent)
                val received = message(3, content = photoContent)

                val viewModel = showing(chat(), history(sent, received))

                assertEquals(emptyMap<Long, MessageUpload>(), viewModel.state.uploads)
            }

        @Test
        fun `cancelling an upload deletes its messages`() =
            runViewModelTest {
                val viewModel = showing(chat(), history(pending(-1)))

                send(viewModel, ChatEvent.CancelUpload(listOf(-1L)))

                assertEquals(listOf(listOf(-1L)), attachments.cancelled)
            }

        @Test
        fun `a cancel that fails says so`() =
            runViewModelTest {
                attachments.onCancelUpload = { Outcome.Failure(DomainError.Network) }
                val viewModel = showing(chat(), history(pending(-1)))

                viewModel.effects.test {
                    send(viewModel, ChatEvent.CancelUpload(listOf(-1L)))

                    assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem())
                }
            }
    }

    @Nested
    inner class ProcessDeath {
        @Test
        fun `the ready items and the caption come back, items being prepared do not`() =
            runViewModelTest {
                val still = AttachmentThumbnail("/cache/uploads/v/thumbnail.jpg", 180, 320)
                val video = Attachment.Video("/cache/uploads/v/clip.mp4", 9, 1080, 1920, 12, still)
                attachments.onPrepare = { uri, _ ->
                    when {
                        uri.endsWith("video") -> Outcome.Success(video)
                        uri.endsWith("slow") -> CompletableDeferred<Outcome<Attachment>>().await()
                        else -> Outcome.Success(photo(uri))
                    }
                }
                val handle = SavedStateHandle()
                val viewModel = privateChat(handle = handle)
                send(viewModel, ChatEvent.AttachMedia(listOf("content://a", "content://video", "content://slow")))
                setCaption(viewModel, "Trip")

                val restored = privateChat(handle = handle)

                assertEquals(listOf(photo("a"), video), restored.preview?.attachments)
                assertEquals(text("Trip"), restored.attachCaption.content)
            }

        @Test
        fun `the poll form comes back open with what it held`() =
            runViewModelTest {
                groups.basicGroupMap.value = mapOf(ChatFixtures.BASIC_GROUP_ID to basicGroup(3))
                val handle = SavedStateHandle()
                chats.setChats(groupChat)
                val viewModel = viewModel(chatId = groupChat.id, handle = handle)
                send(viewModel, ChatEvent.OpenPoll)
                viewModel.pollEditor.question.setTextAndPlaceCursorAtEnd("Lunch?")
                viewModel.pollEditor.isQuiz = true
                Snapshot.sendApplyNotifications()
                runCurrent()

                val restored = viewModel(chatId = groupChat.id, handle = handle)

                assertEquals(PollSheet(), restored.state.poll)
                assertEquals(
                    "Lunch?",
                    restored.pollEditor.question.text
                        .toString(),
                )
                assertTrue(restored.pollEditor.isQuiz)
            }

        @Test
        fun `leaving the conversation deletes the copies of a preview never sent`() =
            runViewModelTest {
                val viewModel = privateChat()
                send(viewModel, ChatEvent.AttachMedia(listOf("content://a")))

                clear(viewModel)

                assertEquals(listOf(photo("a")), attachments.discarded)
            }
    }

    private companion object {
        const val SCHEDULED_AT = 1_800_000_000L
        const val UPLOAD_FILE = 77
    }
}
