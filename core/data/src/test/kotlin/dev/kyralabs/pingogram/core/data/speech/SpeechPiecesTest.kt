package dev.kyralabs.pingogram.core.data.speech

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [speechPieces]: texts cut into pieces the speech engine takes, where a listener would pause anyway. */
class SpeechPiecesTest {
    @Test
    fun `a text that fits is one piece`() = assertEquals(listOf("Hello there."), speechPieces("  Hello there.  ", 20))

    @Test
    fun `a blank text gives none`() = assertEquals(emptyList<String>(), speechPieces(" \n ", 20))

    @Test
    fun `a long text is cut after the last sentence that fits`() =
        assertEquals(listOf("One two. Three!", "Four five six"), speechPieces("One two. Three! Four five six", 20))

    @Test
    fun `without a sentence end it is cut at the last space`() =
        assertEquals(listOf("one two three", "four five"), speechPieces("one two three four five", 15))

    @Test
    fun `a word longer than a piece is cut, never inside an emoji`() {
        val pieces = speechPieces("abcd😀efgh", 5)

        assertEquals(listOf("abcd", "😀efg", "h"), pieces)
    }
}
