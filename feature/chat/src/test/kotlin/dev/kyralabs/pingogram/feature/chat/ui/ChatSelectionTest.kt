package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatReportResult
import dev.kyralabs.pingogram.core.domain.model.Document
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.ReportOption
import dev.kyralabs.pingogram.core.domain.model.ReportOptionId
import dev.kyralabs.pingogram.core.domain.model.SharedFile
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.outgoing
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Selecting, reporting and saving messages in [ChatViewModel] (`REQUIREMENTS.md` FR-C6 "select, report, save to
 * downloads"): entering and leaving selection mode, toggling, albums, the toolbar's actions as TDLib allows them,
 * deleted messages dropping out, process death; TDLib's report flow; saving files to Downloads, with downloads and the
 * storage permission of Android 8–9.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSelectionTest : ChatViewModelHarness() {
    private val photo = ContentPreviewData.photo(minithumbnail = null, firstFileId = PHOTO_FILE)

    /** Everything, reporting too. */
    private val allowed = FakeMessageActionRepository.ALL_ALLOWED.copy(canBeReported = true)

    init {
        messageActions.onProperties = { Outcome.Success(allowed) }
    }

    /**
     * The chat with Ada: her texts 1 and 2, the user's text 3, and Ada's album of photos 4 and 5 (file ids from
     * [PHOTO_FILE] and [PHOTO_FILE] + 10), each a second apart.
     */
    private fun TestScope.conversation(
        handle: SavedStateHandle = savedState,
        vararg extra: Message,
    ): ChatViewModel {
        users.setUsers(user(ADA, firstName = "Ada", lastName = "Lovelace"), user(ME, firstName = "Me"))
        chats.setChats(chat(title = "Ada Lovelace"))
        messages.setHistory(PRIVATE_CHAT, history(*(messages() + extra).toTypedArray()))
        return viewModel(handle = handle)
    }

    private fun messages(): List<Message> =
        listOf(
            message(1, date = NOW.minusSeconds(4), content = MessageContent.Text(text("Hello"), null)),
            message(2, date = NOW.minusSeconds(3), content = MessageContent.Text(text("How are you?"), null)),
            outgoing(3, date = NOW.minusSeconds(2)).copy(content = MessageContent.Text(text("Fine"), null)),
            message(4, date = NOW.minusSeconds(1), content = photo, mediaAlbumId = ALBUM),
            message(5, date = NOW, content = photo.copy(photo = photo10()), mediaAlbumId = ALBUM),
        )

    /** The conversation without messages [deleted], as TDLib's updates leave it. */
    private fun TestScope.delete(vararg deleted: Long) {
        messages.setHistory(PRIVATE_CHAT, history(*messages().filter { it.id !in deleted }.toTypedArray()))
        runCurrent()
    }

    /** The photo of [photo] with its file ids 10 higher. */
    private fun photo10() = ContentPreviewData.photo(minithumbnail = null, firstFileId = PHOTO_FILE + 10).photo

    private val ChatViewModel.selection get() = state.selection

    private val ChatViewModel.selectedIds get() = selection?.messageIds

    private fun TestScope.selecting(
        vararg ids: Long,
        handle: SavedStateHandle = savedState,
    ): ChatViewModel {
        val viewModel = conversation(handle)
        send(viewModel, SelectionEvent.Select(ids.first()))
        ids.drop(1).forEach { send(viewModel, SelectionEvent.Toggle(it)) }
        return viewModel
    }

    @Nested
    inner class Selecting {
        @Test
        fun `Select in a message's actions starts selection mode with it, with what TDLib allows for it`() =
            runViewModelTest {
                val viewModel = conversation()

                send(viewModel, ChatEvent.ShowMessageActions(2))
                assertTrue(MessageAction.Select in viewModel.state.messageActions!!.actions)
                send(viewModel, SelectionEvent.Select(2))

                assertEquals(
                    MessageSelection(
                        persistentSetOf(2L),
                        persistentListOf(
                            MessageAction.Copy,
                            MessageAction.Forward,
                            MessageAction.Report,
                            MessageAction.Delete,
                        ),
                    ),
                    viewModel.selection,
                )
                assertNull(viewModel.state.messageActions)
            }

        @Test
        fun `a tap selects another message, a second tap deselects it`() =
            runViewModelTest {
                val viewModel = selecting(2)

                send(viewModel, SelectionEvent.Toggle(1))
                assertEquals(setOf(1L, 2L), viewModel.selectedIds)

                send(viewModel, SelectionEvent.Toggle(1))
                assertEquals(setOf(2L), viewModel.selectedIds)
            }

        @Test
        fun `an album is selected and deselected as a whole`() =
            runViewModelTest {
                val viewModel = selecting(2)

                send(viewModel, SelectionEvent.Toggle(5))
                assertEquals(setOf(2L, 4L, 5L), viewModel.selectedIds)

                send(viewModel, SelectionEvent.Toggle(4))
                assertEquals(setOf(2L), viewModel.selectedIds)
            }

        @Test
        fun `deselecting the last message ends selection mode`() =
            runViewModelTest {
                val viewModel = selecting(2)

                send(viewModel, SelectionEvent.Toggle(2))

                assertNull(viewModel.selection)
            }

        @Test
        fun `the close button and back end it`() =
            runViewModelTest {
                val viewModel = selecting(1, 2)

                send(viewModel, SelectionEvent.Clear)

                assertNull(viewModel.selection)
            }

        @Test
        fun `a deleted message drops out, and deleting every selected one ends selection mode`() =
            runViewModelTest {
                val viewModel = selecting(1, 2)

                delete(1)
                assertEquals(setOf(2L), viewModel.selectedIds)

                delete(1, 2)
                assertNull(viewModel.selection)
            }

        @Test
        fun `selection mode survives process death`() =
            runViewModelTest {
                val handle = SavedStateHandle()
                val first = selecting(1, 3, handle = handle)
                clear(first)

                val restored = conversation(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))

                assertEquals(setOf(1L, 3L), restored.selectedIds)
            }

        @Test
        fun `the toolbar offers only what TDLib allows for every selected message`() =
            runViewModelTest {
                messageActions.onProperties = { id ->
                    val all = allowed
                    Outcome.Success(
                        if (id == 3L) all.copy(canBeReported = false, canBeForwarded = false) else all,
                    )
                }

                val viewModel = selecting(2, 3)

                assertEquals(listOf(MessageAction.Copy, MessageAction.Delete), viewModel.selection?.actions)
            }

        @Test
        fun `photos without captions have no text to copy`() =
            runViewModelTest {
                val viewModel = selecting(4)

                assertEquals(false, MessageAction.Copy in viewModel.selection!!.actions)
            }
    }

    @Nested
    inner class Toolbar {
        @Test
        fun `copying one text copies it as it is, and ends selection mode`() =
            runViewModelTest {
                val viewModel = selecting(2, 4)

                viewModel.effects.test {
                    send(viewModel, SelectionEvent.Act(MessageAction.Copy))

                    assertEquals(ChatEffect.CopyText("How are you?"), awaitItem())
                }
                assertNull(viewModel.selection)
            }

        @Test
        fun `copying several texts copies them oldest first, under their senders and times`() =
            runViewModelTest {
                val viewModel = selecting(3, 1)

                viewModel.effects.test {
                    send(viewModel, SelectionEvent.Act(MessageAction.Copy))

                    assertEquals(
                        SelectionEffect.CopyMessages(
                            persistentListOf(
                                CopiedMessage(UiText.Plain("Ada Lovelace"), NOW.epochSecond - 4, "Hello"),
                                CopiedMessage(UiText.Plain("Me"), NOW.epochSecond - 2, "Fine"),
                            ),
                        ),
                        awaitItem(),
                    )
                }
            }

        @Test
        fun `forwarding opens the forward sheet with every selected message, and sending ends selection mode`() =
            runViewModelTest {
                val viewModel = selecting(1, 5)
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.forwardSheet.collect {} }

                send(viewModel, SelectionEvent.Act(MessageAction.Forward))
                assertEquals(3, viewModel.forwardSheet.value?.messageCount)
                assertEquals(setOf(1L, 4L, 5L), viewModel.selectedIds)

                send(viewModel, ForwardEvent.ToggleTarget(PRIVATE_CHAT), ForwardEvent.Send)

                assertNull(viewModel.selection)
                assertTrue(messageActions.calls.any { it.startsWith("forward:$PRIVATE_CHAT:[1, 4, 5]") })
            }

        @Test
        fun `deleting asks once for all of them, for whom every one allows, and ends selection mode`() =
            runViewModelTest {
                messageActions.onProperties = { id ->
                    val all = allowed
                    Outcome.Success(if (id == 1L) all.copy(canBeDeletedForAllUsers = false) else all)
                }
                val viewModel = selecting(1, 3)

                send(viewModel, SelectionEvent.Act(MessageAction.Delete))
                assertEquals(
                    DeleteMessagesDialog(persistentListOf(1L, 3L), DeleteScope.ForMe, fromSelection = true),
                    viewModel.state.deleteDialog,
                )

                send(viewModel, ChatEvent.ConfirmDelete(forEveryone = false))

                assertEquals(listOf("delete:$PRIVATE_CHAT:[1, 3]:false"), messageActions.calls)
                assertNull(viewModel.selection)
            }

        @Test
        fun `an action TDLib no longer allows for one of them says so`() =
            runViewModelTest {
                val viewModel = selecting(1, 2)
                messageActions.onProperties = { id ->
                    val all = allowed
                    Outcome.Success(if (id == 2L) all.copy(canBeForwarded = false) else all)
                }

                viewModel.effects.test {
                    send(viewModel, SelectionEvent.Act(MessageAction.Forward))

                    assertEquals(ChatEffect.ActionUnavailable, awaitItem())
                }
                assertNull(viewModel.forwardSheet.value)
            }
    }

    @Nested
    inner class Reporting {
        private val options =
            persistentListOf(
                ReportOption(ReportOptionId(byteArrayOf(1)), "Spam"),
                ReportOption(ReportOptionId(byteArrayOf(2)), "Other"),
            )

        @Test
        fun `Report opens the sheet at once and asks TDLib for the reasons, for an album's every item`() =
            runViewModelTest {
                val answer = CompletableDeferred<Outcome<ChatReportResult>>()
                messageActions.onReport = { _, _ -> answer.await() }
                val viewModel = conversation()

                send(viewModel, SelectionEvent.Report(5))
                assertEquals(ReportSheet(step = null, isReporting = true), viewModel.state.report)
                assertEquals(listOf("report:$PRIVATE_CHAT:[4, 5]:[]:"), messageActions.reports)

                answer.complete(Outcome.Success(ChatReportResult.OptionRequired("Why?", options)))
                runCurrent()

                assertEquals(
                    ReportSheet(ReportStep.Options("Why?", persistentListOf("Spam", "Other"))),
                    viewModel.state.report,
                )
            }

        @Test
        fun `a reason, then a comment, are each one request, and a done report thanks the user`() =
            runViewModelTest {
                messageActions.onReport = { option, text ->
                    Outcome.Success(
                        when {
                            option == ReportOptionId.Initial -> {
                                ChatReportResult.OptionRequired("Why?", options)
                            }

                            text.isEmpty() -> {
                                ChatReportResult.TextRequired(
                                    ReportOptionId(byteArrayOf(9)),
                                    isOptional = false,
                                )
                            }

                            else -> {
                                ChatReportResult.Reported
                            }
                        },
                    )
                }
                val viewModel = selecting(1, 2)

                send(viewModel, SelectionEvent.Act(MessageAction.Report), SelectionEvent.ChooseReportOption(1))
                assertEquals(ReportSheet(ReportStep.Comment(isOptional = false)), viewModel.state.report)

                // A required comment cannot be empty.
                send(viewModel, SelectionEvent.SendReportComment)
                assertEquals(2, messageActions.reports.size)

                viewModel.reportComment.setTextAndPlaceCursorAtEnd("  Sends scam links ")
                viewModel.effects.test {
                    send(viewModel, SelectionEvent.SendReportComment)

                    assertEquals(SelectionEffect.Reported, awaitItem())
                }
                assertEquals(
                    listOf(
                        "report:$PRIVATE_CHAT:[1, 2]:[]:",
                        "report:$PRIVATE_CHAT:[1, 2]:[2]:",
                        "report:$PRIVATE_CHAT:[1, 2]:[9]:Sends scam links",
                    ),
                    messageActions.reports,
                )
                assertNull(viewModel.state.report)
                assertNull(viewModel.selection)
            }

        @Test
        fun `a first request that fails closes the sheet, a later one keeps the step`() =
            runViewModelTest {
                messageActions.onReport = { _, _ -> Outcome.Failure(DomainError.Network) }
                val viewModel = conversation()

                viewModel.effects.test {
                    send(viewModel, SelectionEvent.Report(1))
                    assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem())
                }
                assertNull(viewModel.state.report)

                messageActions.onReport = { option, _ ->
                    if (option == ReportOptionId.Initial) {
                        Outcome.Success(ChatReportResult.OptionRequired("Why?", options))
                    } else {
                        Outcome.Failure(DomainError.FloodWait(30))
                    }
                }
                send(viewModel, SelectionEvent.Report(1), SelectionEvent.ChooseReportOption(0))

                assertEquals(
                    ReportSheet(ReportStep.Options("Why?", persistentListOf("Spam", "Other"))),
                    viewModel.state.report,
                )
            }

        @Test
        fun `closing the sheet reports nothing more`() =
            runViewModelTest {
                messageActions.onReport = { _, _ -> Outcome.Success(ChatReportResult.OptionRequired("Why?", options)) }
                val viewModel = conversation()

                send(
                    viewModel,
                    SelectionEvent.Report(1),
                    SelectionEvent.DismissReport,
                    SelectionEvent.ChooseReportOption(0),
                )

                assertNull(viewModel.state.report)
                assertEquals(1, messageActions.reports.size)
            }

        @Test
        fun `a message TDLib does not let be reported offers no report`() =
            runViewModelTest {
                messageActions.onProperties = {
                    Outcome.Success(allowed.copy(canBeReported = false))
                }
                val viewModel = conversation()

                send(viewModel, ChatEvent.ShowMessageActions(1))
                assertEquals(false, MessageAction.Report in viewModel.state.messageActions!!.actions)

                viewModel.effects.test {
                    send(viewModel, SelectionEvent.Report(1))
                    assertEquals(ChatEffect.ActionUnavailable, awaitItem())
                }
                assertTrue(messageActions.reports.isEmpty())
            }
    }

    @Nested
    inner class Saving {
        private val document =
            MessageContent.Document(
                Document(
                    fileName = "Report 2026.pdf",
                    mimeType = "application/pdf",
                    minithumbnail = null,
                    thumbnail = null,
                    file = FileRef(DOCUMENT_FILE, 1_000),
                ),
                caption = FormattedText.Empty,
            )

        private fun TestScope.withDocument(handle: SavedStateHandle = savedState) =
            conversation(handle, message(6, content = document))

        @Test
        fun `a downloaded document goes to Downloads under its own name, with an action that opens it`() =
            runViewModelTest {
                files.setFile(DOCUMENT_FILE, FileState.Downloaded(DOCUMENT_PATH, 1_000))
                val viewModel = withDocument()

                send(viewModel, ChatEvent.ShowMessageActions(6))
                assertTrue(MessageAction.SaveToDownloads in viewModel.state.messageActions!!.actions)
                viewModel.effects.test {
                    send(viewModel, SelectionEvent.SaveToDownloads(6))

                    assertEquals(
                        SelectionEffect.Saved(1, ChatEffect.OpenFile(DOCUMENT_PATH, "application/pdf")),
                        awaitItem(),
                    )
                }
                assertEquals(
                    listOf(SharedFile(DOCUMENT_PATH, "application/pdf", "Report 2026")),
                    sharedStorage.savedToDownloads,
                )
            }

        @Test
        fun `an album saves each photo's largest size, downloading it first`() =
            runViewModelTest {
                files.setFile(PHOTO_FILE + 2, FileState.Downloaded("/files/photos/4.jpg", 1_000))
                files.setFile(PHOTO_FILE + 12, FileState.NotDownloaded(1_000, canBeDownloaded = true))
                val viewModel = conversation()

                viewModel.effects.test {
                    send(viewModel, SelectionEvent.SaveToDownloads(4))
                    assertEquals(SelectionEffect.Saving, awaitItem())
                    assertEquals(listOf("download:${PHOTO_FILE + 12}:Visible"), files.calls)

                    files.setFile(PHOTO_FILE + 12, FileState.Downloading(500, 1_000, isActive = true))
                    files.setFile(PHOTO_FILE + 12, FileState.Downloaded("/files/photos/5.jpg", 1_000))
                    runCurrent()

                    assertEquals(SelectionEffect.Saved(2), awaitItem())
                }
                assertEquals(
                    listOf(
                        SharedFile("/files/photos/5.jpg", "image/jpeg", "Pingogram_5"),
                        SharedFile("/files/photos/4.jpg", "image/jpeg", "Pingogram_4"),
                    ),
                    sharedStorage.savedToDownloads,
                )
            }

        @Test
        fun `a download that stops saves nothing`() =
            runViewModelTest {
                files.setFile(DOCUMENT_FILE, FileState.NotDownloaded(1_000, canBeDownloaded = true))
                val viewModel = withDocument()

                viewModel.effects.test {
                    send(viewModel, SelectionEvent.SaveToDownloads(6))
                    assertEquals(SelectionEffect.Saving, awaitItem())

                    files.setFile(DOCUMENT_FILE, FileState.Downloading(100, 1_000, isActive = true))
                    runCurrent()
                    files.setFile(DOCUMENT_FILE, FileState.Downloading(100, 1_000, isActive = false))
                    runCurrent()

                    assertEquals(SelectionEffect.SaveFailed, awaitItem())
                }
                assertTrue(sharedStorage.savedToDownloads.isEmpty())
            }

        @Test
        fun `a chat that protects its content offers no saving, and a text has nothing to save`() =
            runViewModelTest {
                messageActions.onProperties = {
                    Outcome.Success(allowed.copy(canBeSaved = false))
                }
                val viewModel = withDocument()

                send(viewModel, ChatEvent.ShowMessageActions(6))
                assertEquals(false, MessageAction.SaveToDownloads in viewModel.state.messageActions!!.actions)
                messageActions.onProperties = { Outcome.Success(allowed) }
                send(viewModel, ChatEvent.ShowMessageActions(1))
                assertEquals(false, MessageAction.SaveToDownloads in viewModel.state.messageActions!!.actions)
            }

        @Test
        fun `Android 8-9 ask for the storage permission first, and a grant saves after process death`() =
            runViewModelTest {
                sharedStorage.needsPermission = true
                files.setFile(DOCUMENT_FILE, FileState.Downloaded(DOCUMENT_PATH, 1_000))
                val handle = SavedStateHandle()
                val first = withDocument(handle)

                first.effects.test {
                    send(first, SelectionEvent.SaveToDownloads(6))
                    assertEquals(SelectionEffect.RequestStoragePermission, awaitItem())
                }
                assertTrue(sharedStorage.savedToDownloads.isEmpty())
                clear(first)

                sharedStorage.needsPermission = false
                val restored = withDocument(SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }))
                restored.effects.test {
                    send(restored, SelectionEvent.StoragePermissionResult(granted = true))

                    assertEquals(
                        SelectionEffect.Saved(1, ChatEffect.OpenFile(DOCUMENT_PATH, "application/pdf")),
                        awaitItem(),
                    )
                }
            }

        @Test
        fun `a refused storage permission says that saving needs it`() =
            runViewModelTest {
                sharedStorage.needsPermission = true
                files.setFile(DOCUMENT_FILE, FileState.Downloaded(DOCUMENT_PATH, 1_000))
                val viewModel = withDocument()

                viewModel.effects.test {
                    send(viewModel, SelectionEvent.SaveToDownloads(6))
                    assertEquals(SelectionEffect.RequestStoragePermission, awaitItem())

                    send(viewModel, SelectionEvent.StoragePermissionResult(granted = false))
                    assertEquals(SelectionEffect.StoragePermissionDenied, awaitItem())
                }
                assertTrue(sharedStorage.savedToDownloads.isEmpty())
            }

        @Test
        fun `a copy that fails says so`() =
            runViewModelTest {
                sharedStorage.onSave = { false }
                files.setFile(DOCUMENT_FILE, FileState.Downloaded(DOCUMENT_PATH, 1_000))
                val viewModel = withDocument()

                viewModel.effects.test {
                    send(viewModel, SelectionEvent.SaveToDownloads(6))

                    assertEquals(SelectionEffect.SaveFailed, awaitItem())
                }
            }
    }

    private companion object {
        const val ALBUM = 77L
        const val PHOTO_FILE = 600
        const val DOCUMENT_FILE = 700
        const val DOCUMENT_PATH = "/data/files/tdlib/files/documents/Report 2026.pdf"
    }
}
