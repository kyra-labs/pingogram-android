package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.outgoing
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.service
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Where sponsored messages go among the items ([withSponsored]) and when the next one is placed ([placingNext]): pure
 * functions, without a view model.
 */
class SponsoredItemsTest {
    private val today = UiText.Plain("Today")
    private val yesterday = UiText.Plain("Yesterday")

    private fun card(id: Long) =
        SponsoredCard(id, false, "Sponsor", FormattedText.Empty, media = null, sponsorPhoto = null, buttonText = "Open")

    private fun bubble(
        id: Long,
        date: UiText = today,
    ) = ChatPreviewData.bubble(id, "Message $id", time = "9:41 AM", date = date)

    /** Newest first: today's 3 and 2, then yesterday's 1 below its date separator. */
    private val items: ImmutableList<ChatItem> =
        listOf(
            bubble(3),
            bubble(2),
            ChatItem.DateSeparator(epochDay = 2, date = today),
            bubble(1, yesterday),
            ChatItem.DateSeparator(epochDay = 1, date = yesterday),
        ).toImmutableList()

    private fun ImmutableList<ChatItem>.keys() = map { it.key }

    @Test
    fun `one at the end goes below every message, with the newest one's date`() {
        val result = items.withSponsored(listOf(PlacedSponsored(card(9), ChatSponsoredMessages.AT_END)))

        assertEquals(listOf(sponsoredKey(9), 3L, 2L, "date:2", 1L, "date:1"), result.keys())
        assertEquals(today, result.first().date)
    }

    @Test
    fun `one after a message goes directly below it, above the next day's date separator, with its date`() {
        val result = items.withSponsored(listOf(PlacedSponsored(card(9), afterMessageId = 1)))

        assertEquals(listOf(3L, 2L, "date:2", sponsoredKey(9), 1L, "date:1"), result.keys())
        assertEquals(yesterday, result[3].date)
    }

    @Test
    fun `several go each below their message`() {
        val placed = listOf(PlacedSponsored(card(9), afterMessageId = 1), PlacedSponsored(card(8), afterMessageId = 3))

        assertEquals(
            listOf(sponsoredKey(8), 3L, 2L, "date:2", sponsoredKey(9), 1L, "date:1"),
            items.withSponsored(placed).keys(),
        )
    }

    @Test
    fun `one whose message is not among the items does not show, and nothing changes`() {
        assertSame(items, items.withSponsored(listOf(PlacedSponsored(card(9), afterMessageId = 42))))
        assertSame(items, items.withSponsored(emptyList()))
    }

    @Test
    fun `the state shows them only where the items reach the newest message`() {
        val ads = SponsoredState(listOf(PlacedSponsored(card(9), ChatSponsoredMessages.AT_END)), sheet = null)
        val content = ChatContent.Content(items, reachedStart = true, isLoadingOlder = false, olderError = null)
        val atEnd = ChatUiState(content = content)
        val older = ChatUiState(content = content.copy(reachedEnd = false))

        assertEquals(sponsoredKey(9), (atEnd.withSponsored(ads).content as ChatContent.Content).items.first().key)
        assertSame(older, older.withSponsored(ads))
        assertSame(atEnd, atEnd.withSponsored(SponsoredState.None))
    }

    @Test
    fun `the first goes below the newest sent message`() {
        val history = history(message(1), message(2), outgoing(3, sendingState = SendingState.Pending))

        val placed =
            persistentMapOf<Long, Long>().placingNext(
                history,
                listOf(card(9)),
                messagesBetween = 2,
                emptySet(),
            )

        assertEquals(mapOf(9L to 2L), placed)
    }

    @Test
    fun `the next waits for that many messages after the last one placed, service messages not counted`() {
        val placed = persistentMapOf(9L to 1L)
        val cards = listOf(card(9), card(8))
        val tooFew = history(message(1), message(2), service(3, ServiceAction.MemberJoined))
        val enough = history(message(1), message(2), service(3, ServiceAction.MemberJoined), message(4))

        assertSame(placed, placed.placingNext(tooFew, cards, messagesBetween = 2, emptySet()))
        assertEquals(mapOf(9L to 1L, 8L to 4L), placed.placingNext(enough, cards, messagesBetween = 2, emptySet()))
    }

    @Test
    fun `none is placed when all are placed or hidden`() {
        val placed = persistentMapOf(9L to 1L)
        val history = history(message(1), message(2), message(3))

        assertSame(placed, placed.placingNext(history, listOf(card(9), card(8)), messagesBetween = 1, setOf(8L)))
    }
}
