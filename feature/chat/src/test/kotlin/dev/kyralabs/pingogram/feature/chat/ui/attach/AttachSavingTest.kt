package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.AttachmentThumbnail
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** The attach preview and the poll form in a `SavedStateHandle`, read back as they were. */
class AttachSavingTest {
    private val attachments =
        listOf(
            Attachment.Photo("/cache/uploads/a/photo.jpg", 812_000, 2560, 1920),
            Attachment.Video(
                "/cache/uploads/b/clip.mp4",
                12_400_000,
                1080,
                1920,
                42,
                AttachmentThumbnail("/cache/uploads/b/thumbnail.jpg", 180, 320),
            ),
            Attachment.Video("/cache/uploads/c/still-less.mp4", 3_000, 640, 480, 3, thumbnail = null),
            Attachment.Document("/cache/uploads/d/Q3 | final.pdf", 2_450, "Q3 | final.pdf", ""),
        )

    @Test
    fun `every kind of attachment reads back as it was saved, names with separators too`() {
        assertEquals(attachments, attachments.map { restoredAttachment(it.saved()) })
    }

    @Test
    fun `strings that are no saved attachment read as nothing`() {
        assertNull(restoredAttachment(""))
        assertNull(restoredAttachment("photo\u0000/path\u0000not-a-size\u00001\u00001"))
        assertNull(restoredAttachment("sticker\u0000/path\u00001"))
    }

    @Test
    fun `the preview reads back with its kind, and an empty one as none`() {
        val handle = SavedStateHandle()

        handle.savePreview(AttachmentKind.Media, attachments)
        assertEquals(AttachmentKind.Media to attachments, handle.restoredPreview())
        handle.savePreview(null, emptyList())

        assertNull(handle.restoredPreview())
    }

    @Test
    fun `the poll form reads back open, with its options and correct answer`() {
        val handle = SavedStateHandle()
        val draft =
            PollDraft("Largest?", listOf("Mars", "Jupiter"), isAnonymous = false, isQuiz = true, correctOptionIndex = 1)

        handle.savePoll(open = true, draft = draft)

        assertEquals(true, handle.restoredPollOpen())
        assertEquals(draft, handle.restoredPoll())
        assertEquals(PollDraft(), SavedStateHandle().restoredPoll())
    }
}
