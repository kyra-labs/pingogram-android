package dev.kyralabs.pingogram.feature.chat.ui.keyword

import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** The word of the keyword alert match the match bar shows, marked in its bubble only (board 30). */
class KeywordShownTest {
    private val state = ChatPreviewData.groupChat

    @Test
    fun `marks the word wherever the bubble's text has it, in any case, and copies that bubble only`() {
        val marked = state.withKeywordMatch(KeywordShown(messageId = 24, word = "MOTION"))

        val before = (state.content as ChatContent.Content).items
        val after = (marked.content as ChatContent.Content).items
        val bubble = after.filterIsInstance<ChatItem.Bubble>().single { it.id == 24L }
        val start = bubble.text.text.indexOf("motion")
        assertEquals(listOf(start until start + "motion".length), bubble.keywordMatches)
        before.zip(after).forEach { (old, new) -> if (new !== bubble) assertSame(old, new) }
    }

    @Test
    fun `no match, a word the text does not have as a whole word, and no bar leave the state as it is`() {
        assertSame(state, state.withKeywordMatch(null))
        assertSame(state, state.withKeywordMatch(KeywordShown(messageId = 24, word = "mot")))
        assertSame(state, state.withKeywordMatch(KeywordShown(messageId = -1, word = "motion")))
    }
}
