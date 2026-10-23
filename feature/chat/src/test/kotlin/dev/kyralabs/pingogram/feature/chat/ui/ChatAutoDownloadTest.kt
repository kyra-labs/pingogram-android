package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.AutoDownloadRules
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Automatic download in [ChatViewModel] ([ChatAutoDownloads]): what downloads by itself, and photos' downloads. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatAutoDownloadTest : ChatViewModelHarness() {
    private val voice = ContentPreviewData.voiceNote(isListened = false)
    private val voiceFile = voice.voiceNote.file.id
    private val document = ContentPreviewData.document("report.pdf", "application/pdf")
    private val documentFile = document.document.file.id
    private val video = ContentPreviewData.video(minithumbnail = null)
    private val videoFile = video.video.file.id

    private val everything =
        AutoDownloadRules(
            photos = true,
            videos = true,
            videoSizeLimit = 15_000_000,
            files = true,
            fileSizeLimit = 5_000_000,
            voiceAndVideoMessages = true,
        )

    /** The downloads asked for, in no particular order: messages come newest first. */
    private fun downloads() = files.calls.filter { it.startsWith("download:") }.sorted()

    @Test
    fun `the files of messages on screen download by themselves at prefetch priority, once`() =
        runViewModelTest {
            autoDownloads.rules.value = everything
            val viewModel =
                showing(
                    chat(),
                    history(message(1, content = voice), message(2, content = document), message(3, content = video)),
                )

            send(viewModel, ChatEvent.MessagesVisible(setOf(1L, 3L)))
            send(viewModel, ChatEvent.MessagesVisible(setOf(1L, 2L, 3L)))

            assertEquals(
                listOf(
                    "download:$voiceFile:Prefetch",
                    "download:$videoFile:Prefetch",
                    "download:$documentFile:Prefetch",
                ).sorted(),
                downloads(),
            )
        }

    @Test
    fun `only what the rules allow downloads, within their size limits`() =
        runViewModelTest {
            // The video (12.4 MB) is over its limit; the document (4.5 MB) within.
            autoDownloads.rules.value = everything.copy(videoSizeLimit = 10_000_000, voiceAndVideoMessages = false)
            val viewModel =
                showing(
                    chat(),
                    history(message(1, content = voice), message(2, content = document), message(3, content = video)),
                )

            send(viewModel, ChatEvent.MessagesVisible(setOf(1L, 2L, 3L)))

            assertEquals(listOf("download:$documentFile:Prefetch"), downloads())
        }

    @Test
    fun `nothing downloads by itself under the mobile defaults, and what the rules allow later does`() =
        runViewModelTest {
            val viewModel = showing(chat(), history(message(1, content = voice), message(2, content = document)))

            send(viewModel, ChatEvent.MessagesVisible(setOf(1L, 2L)))
            assertEquals(emptyList<String>(), downloads())

            autoDownloads.rules.value = everything
            runCurrent()

            assertEquals(
                listOf("download:$voiceFile:Prefetch", "download:$documentFile:Prefetch").sorted(),
                downloads(),
            )
        }

    @Test
    fun `a download the user cancelled does not start again by itself`() =
        runViewModelTest {
            autoDownloads.rules.value = everything
            val viewModel = showing(chat(), history(message(1, content = document)))
            send(viewModel, ChatEvent.MessagesVisible(setOf(1L)))

            send(viewModel, ChatEvent.CancelDownload(documentFile), ChatEvent.MessagesVisible(emptySet()))
            send(viewModel, ChatEvent.MessagesVisible(setOf(1L)))

            assertEquals(listOf("download:$documentFile:Prefetch"), downloads())
        }

    @Test
    fun `photos download by themselves as the rules say`() =
        runViewModelTest {
            val viewModel = showing(chat(), history(message(1)))
            assertTrue(viewModel.state.mediaDownloads.photos)

            autoDownloads.rules.value = AutoDownloadRules.Nothing
            runCurrent()

            assertFalse(viewModel.state.mediaDownloads.photos)
        }

    @Test
    fun `a download the user asks for shows as requested until cancelled`() =
        runViewModelTest {
            autoDownloads.rules.value = AutoDownloadRules.Nothing
            val viewModel = showing(chat(), history(message(1)))

            send(viewModel, ChatEvent.DownloadFile(PHOTO_FILE))
            assertEquals(persistentSetOf(PHOTO_FILE), viewModel.state.mediaDownloads.requested)
            assertEquals(listOf("download:$PHOTO_FILE:Requested"), files.calls)

            send(viewModel, ChatEvent.CancelDownload(PHOTO_FILE))
            assertEquals(persistentSetOf<Int>(), viewModel.state.mediaDownloads.requested)
            assertEquals("cancel:$PHOTO_FILE", files.calls.last())
        }

    private companion object {
        const val PHOTO_FILE = 7_001
    }
}
