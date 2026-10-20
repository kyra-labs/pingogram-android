package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.ui.text.AnnotatedString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The preview line of rows at large font scales, which starts with the time. */
class PreviewTextTest {
    @Test
    fun `the time comes first, and the description keeps the preview alone`() {
        val preview = PreviewText(AnnotatedString("Alice: Hi"), "Alice: Hi")

        val shown = preview.after("9:41 AM")

        assertEquals("9:41 AM · Alice: Hi", shown.styled.text)
        assertEquals("Alice: Hi", shown.text)
    }
}
