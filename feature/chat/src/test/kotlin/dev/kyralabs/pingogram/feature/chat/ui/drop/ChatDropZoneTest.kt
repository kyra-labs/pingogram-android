package dev.kyralabs.pingogram.feature.chat.ui.drop

import android.content.ClipDescription
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Which drags the conversation takes (board 35a): files, never text alone; photos and videos as an album. */
class ChatDropZoneTest {
    @Test
    fun `files are taken, text is left to the composer`() {
        assertTrue(isFileType("image/png"))
        assertTrue(isFileType("application/pdf"))
        assertFalse(isFileType(ClipDescription.MIMETYPE_TEXT_PLAIN))
        assertFalse(isFileType(ClipDescription.MIMETYPE_TEXT_HTML))
    }

    @Test
    fun `photos and videos go as media, everything else as files`() {
        assertTrue(isMediaType("image/jpeg"))
        assertTrue(isMediaType("video/mp4"))
        assertFalse(isMediaType("application/pdf"))
        assertFalse(isMediaType("audio/ogg"))
    }
}
