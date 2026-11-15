package dev.kyralabs.pingogram.feature.chat.ui.drop

import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.GroupPosition
import dev.kyralabs.pingogram.feature.chat.ui.MessageAlbum
import dev.kyralabs.pingogram.feature.chat.ui.MessageShortcuts
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MessageDragOutTest {
    private val photo = ContentPreviewData.photo(minithumbnail = null, firstFileId = 1)
    private val photoFile = 3 // The largest size.
    private val video = ContentPreviewData.video(minithumbnail = null)
    private val document = ContentPreviewData.document("report.pdf", "application/pdf")

    private fun bubble(
        content: MessageContent,
        album: MessageAlbum? = null,
    ) = ChatItem.Bubble(
        id = MESSAGE,
        isOutgoing = false,
        position = GroupPosition.Single,
        sender = UiText.Plain("Ada"),
        senderId = 2,
        showsSenderName = false,
        avatar = null,
        hasAvatarColumn = false,
        time = "9:41 AM",
        isEdited = false,
        status = null,
        forwardedFrom = null,
        reply = null,
        content = content,
        contentLabel = null,
        text = FormattedText.Empty,
        spoilersRevealed = false,
        linkPreview = null,
        reactions = persistentListOf(),
        date = UiText.Plain("Today"),
        album = album,
    )

    private fun state(
        bubble: ChatItem.Bubble,
        downloaded: Map<Int, String>,
        canForward: Boolean = true,
    ) = ChatUiState(
        content =
            ChatContent.Content(
                persistentListOf(bubble),
                reachedStart = true,
                isLoadingOlder = false,
                olderError = null,
            ),
        fileStates =
            downloaded
                .mapValues { (_, path) ->
                    FileState.Downloaded(path, size = 1) as FileState
                }.toImmutableMap(),
        shortcuts = MessageShortcuts(canForward = canForward),
    )

    @Test
    fun `a downloaded photo drags out as its largest size`() {
        val files = state(bubble(photo), mapOf(photoFile to "/files/photos/1.jpg")).dragOutFiles(MESSAGE)

        assertEquals(listOf(DragOutFile("/files/photos/1.jpg", "image/jpeg", DragOutKind.Photo)), files)
    }

    @Test
    fun `a document keeps its type and name`() {
        val file = document.document.file.id
        val files = state(bubble(document), mapOf(file to "/files/documents/report.pdf")).dragOutFiles(MESSAGE)

        assertEquals(
            listOf(DragOutFile("/files/documents/report.pdf", "application/pdf", DragOutKind.Document, "report.pdf")),
            files,
        )
    }

    @Test
    fun `a file not on the device does not drag`() {
        assertTrue(state(bubble(video), emptyMap()).dragOutFiles(MESSAGE).isEmpty())
    }

    @Test
    fun `nothing drags out of a chat that keeps its content in`() {
        val files =
            state(
                bubble(photo),
                mapOf(photoFile to "/files/photos/1.jpg"),
                canForward = false,
            ).dragOutFiles(MESSAGE)

        assertTrue(files.isEmpty())
    }

    @Test
    fun `self-destructing media does not drag`() {
        val secret = photo.copy(isSecret = true)

        assertTrue(state(bubble(secret), mapOf(photoFile to "/files/photos/1.jpg")).dragOutFiles(MESSAGE).isEmpty())
    }

    @Test
    fun `an album drags its downloaded items`() {
        val videoFile = video.video.file.id
        val album = MessageAlbum(1, persistentListOf(MESSAGE, MESSAGE + 1), persistentListOf(photo, video))
        val files =
            state(bubble(photo, album), mapOf(photoFile to "/p.jpg", videoFile to "/v.mp4")).dragOutFiles(MESSAGE)

        assertEquals(listOf(DragOutKind.Photo, DragOutKind.Video), files.map { it.kind })
    }

    @Test
    fun `a drag that starts closes the actions, one that cannot keeps them`() {
        var closed = 0
        val file = DragOutFile("/p.jpg", "image/jpeg", DragOutKind.Photo)
        val started = MessageDragOut(files = { listOf(file) }, drag = { true }, onStarted = { closed++ })
        val refused = MessageDragOut(files = { listOf(file) }, drag = { false }, onStarted = { closed++ })
        val empty = MessageDragOut(files = { emptyList() }, drag = { true }, onStarted = { closed++ })

        assertTrue(started.start(MESSAGE))
        assertFalse(refused.start(MESSAGE))
        assertFalse(empty.start(MESSAGE))
        assertEquals(1, closed)
    }

    private companion object {
        const val MESSAGE = 10L
    }
}
