package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.feature.chat.ui.keyword.KeywordShown
import dev.kyralabs.pingogram.feature.chat.ui.keyword.withKeywordMatch
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.SponsoredPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.search.SearchHighlight
import dev.kyralabs.pingogram.feature.chat.ui.search.withSearchMatch
import dev.kyralabs.pingogram.feature.chat.ui.translation.BubbleTranslation
import dev.kyralabs.pingogram.feature.chat.ui.translation.withTranslations
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * The overlay stages of the conversation's state ([OverlayCopies]): run again on every state, a stage whose items and
 * overlay did not change gives the same list and the same copies as before; a new message leaves the copies of the
 * other bubbles as they were; an overlay that changes makes a new copy; and every result equals what the stage makes
 * without its kept copies.
 */
class OverlayStagesTest {
    private val bubbles = (10L downTo 1L).map { ChatPreviewData.bubble(it, "Hola amigo $it", time = "9:41 AM") }
    private val state = stateOf(bubbles)
    private val translations = persistentMapOf(TRANSLATED to translation("Hello friend"))
    private val search = SearchHighlight(SEARCHED, MatchHighlight(persistentListOf("amigo")))
    private val keyword = KeywordShown(SEARCHED, "amigo")
    private val ads = SponsoredState(listOf(PlacedSponsored(SponsoredPreviewData.text, TRANSLATED)), sheet = null)

    private fun stateOf(items: List<ChatItem>) =
        ChatUiState(content = ChatContent.Content(items.toImmutableList(), true, false, null))

    private fun translation(text: String) = BubbleTranslation.Shown(FormattedText(text, persistentListOf()), "es")

    private val ChatUiState.items get() = (content as ChatContent.Content).items

    private fun ChatUiState.entry(key: Any) = items.single { it.key == key }

    /** Each stage, with the copies it keeps. */
    private val stages = OverlayStages()

    private fun ChatUiState.overlaid(
        translations: Map<Long, BubbleTranslation> = this@OverlayStagesTest.translations,
        copies: OverlayStages = stages,
    ): ChatUiState =
        withSponsored(ads, copies.sponsored)
            .withTranslations(translations.toImmutableMap(), copies.translations)
            .withSearchMatch(search, copies.search)
            .withKeywordMatch(keyword, copies.keyword)

    @Test
    fun `the same items and overlays give the same list and the same copies`() {
        val first = state.overlaid()

        val again = state.overlaid()

        assertSame(first.items, again.items)
    }

    @Test
    fun `a new message leaves the other bubbles' copies as they were`() {
        val first = state.overlaid()
        val newer = stateOf(listOf(ChatPreviewData.bubble(11L, "Nuevo", time = "9:42 AM")) + bubbles)

        val next = newer.overlaid()

        assertNotSame(first.items, next.items)
        assertSame(first.entry(TRANSLATED), next.entry(TRANSLATED))
        assertSame(first.entry(SEARCHED), next.entry(SEARCHED))
        assertSame(
            first.entry(sponsoredKey(SponsoredPreviewData.text.id)),
            next.entry(sponsoredKey(SponsoredPreviewData.text.id)),
        )
    }

    @Test
    fun `a changed translation makes a new copy`() {
        val first = state.overlaid()

        val next = state.overlaid(translations = mapOf(TRANSLATED to translation("Hi friend")))

        assertNotSame(first.entry(TRANSLATED), next.entry(TRANSLATED))
        assertEquals("Hi friend", (next.entry(TRANSLATED) as ChatItem.Bubble).text.text)
        assertSame(first.entry(SEARCHED), next.entry(SEARCHED))
    }

    @Test
    fun `kept copies give what the stages make without them`() {
        state.overlaid()
        val newer = stateOf(listOf(ChatPreviewData.bubble(11L, "Nuevo", time = "9:42 AM")) + bubbles)

        val kept = newer.overlaid()

        assertEquals(newer.overlaid(copies = OverlayStages()), kept)
    }

    private companion object {
        const val TRANSLATED = 7L
        const val SEARCHED = 4L
    }
}
