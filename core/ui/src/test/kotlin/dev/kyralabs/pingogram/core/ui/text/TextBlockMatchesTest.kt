package dev.kyralabs.pingogram.core.ui.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** A search's matches marked in a text as blocks for [textBlocks], their text in the block's content color. */
class TextBlockMatchesTest {
    @Test
    fun `marks every match as a match block in the content color`() {
        val text = AnnotatedString("Ada met ada").withMatches(MatchHighlight.of("ada"), Color.White)

        val blocks = text.getStringAnnotations(TEXT_BLOCK_TAG, 0, text.length)
        assertEquals(listOf(0 to 3, 8 to 11), blocks.map { it.start to it.end })
        assertTrue(blocks.all { it.item == TextBlock.MATCH })
        assertEquals(2, text.spanStyles.count { it.item == SpanStyle(color = Color.White) })
        assertTrue(text.hasTextBlocks())
    }

    @Test
    fun `a match within a hidden spoiler stays hidden`() {
        val text =
            buildAnnotatedString {
                append("Ada ")
                val start = length
                append("secret ada")
                addStringAnnotation(TEXT_BLOCK_TAG, TextBlock.SPOILER, start, length)
            }.withMatches(MatchHighlight.of("ada"), Color.White)

        val matches = text.getStringAnnotations(TEXT_BLOCK_TAG, 0, text.length).filter { it.item == TextBlock.MATCH }
        assertEquals(listOf(0 to 3), matches.map { it.start to it.end })
    }

    @Test
    fun `a text without matches is left as it is`() {
        val text = AnnotatedString("Grace")

        assertSame(text, text.withMatches(MatchHighlight.of("ada"), Color.White))
    }
}
