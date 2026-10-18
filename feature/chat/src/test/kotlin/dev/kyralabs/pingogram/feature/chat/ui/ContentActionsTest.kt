package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.content.ContentPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What screen readers can do with a bubble's content, which they hear as one with the bubble. */
class ContentActionsTest {
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

    private val document = ContentPreviewData.document("report.pdf", "application/pdf")
    private val documentFile = document.document.file.id

    @Test
    fun `media opens in the viewer`() {
        val photo = ContentPreviewData.photo(minithumbnail = null)

        assertEquals(
            listOf(ContentAction(R.string.content_open, ChatEvent.OpenMedia(MESSAGE))),
            contentActions(bubble(photo), null),
        )
    }

    @Test
    fun `an album opens at its first item`() {
        val items = ContentPreviewData.album(count = 3, minithumbnail = null)
        val album = MessageAlbum(id = 5, messageIds = listOf(10L, 11L, 12L).toImmutableList(), items = items)

        assertEquals(
            listOf(ContentAction(R.string.content_open, ChatEvent.OpenMedia(10))),
            contentActions(bubble(items.first(), album), null),
        )
    }

    @Test
    fun `a voice message plays`() {
        val voice = ContentPreviewData.voiceNote(isListened = true)

        assertEquals(
            listOf(ContentAction(R.string.content_play, ChatEvent.PlayPause(MESSAGE, voice.voiceNote.file.id))),
            contentActions(bubble(voice), null),
        )
    }

    @Test
    fun `a document downloads, cancels or opens as far as it is downloaded`() {
        val download = ContentAction(R.string.content_download, ChatEvent.DownloadFile(documentFile))

        assertEquals(listOf(download), contentActions(bubble(document), null))
        assertEquals(
            listOf(download),
            contentActions(bubble(document), FileState.NotDownloaded(size = 1, canBeDownloaded = true)),
        )
        assertEquals(
            listOf(ContentAction(R.string.content_cancel_download, ChatEvent.CancelDownload(documentFile))),
            contentActions(bubble(document), FileState.Downloading(downloadedSize = 1, size = 2, isActive = true)),
        )
        assertEquals(
            listOf(ContentAction(R.string.content_open, ChatEvent.OpenFile(MESSAGE, documentFile))),
            contentActions(bubble(document), FileState.Downloaded(path = "/report.pdf", size = 2)),
        )
    }

    @Test
    fun `a document TDLib cannot download offers nothing`() {
        val state = FileState.NotDownloaded(size = 1, canBeDownloaded = false)

        assertTrue(contentActions(bubble(document), state).isEmpty())
    }

    @Test
    fun `a location opens in a maps app`() {
        assertEquals(
            listOf(ContentAction(R.string.content_open, ChatEvent.OpenLocation(MESSAGE))),
            contentActions(bubble(ContentPreviewData.venue), null),
        )
    }

    @Test
    fun `text and polls offer nothing`() {
        val text = MessageContent.Text(FormattedText.Empty, linkPreview = null)

        assertTrue(contentActions(bubble(text), null).isEmpty())
        assertTrue(contentActions(bubble(ContentPreviewData.poll(voted = true)), null).isEmpty())
    }

    private companion object {
        const val MESSAGE = 42L
    }
}
