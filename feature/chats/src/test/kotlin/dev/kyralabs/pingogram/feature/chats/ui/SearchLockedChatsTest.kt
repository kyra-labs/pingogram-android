package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Search's results without the locked chats not opened with the passcode (`docs/adr/0029-locked-chats.md`). */
class SearchLockedChatsTest {
    private val results =
        SearchState.Results(
            query = "a",
            chats = SearchSection.Items(persistentListOf(chat(1), chat(2))),
            contacts = SearchSection.Items(persistentListOf(chat(2), chat(3))),
            global = SearchSection.Loading,
            messages =
                MessagePages(
                    found = persistentListOf(FoundMessage(message(1), chat(1)), FoundMessage(message(2), chat(2))),
                    hasFirstPage = true,
                    isLoading = false,
                ),
        )

    @Test
    fun `a locked chat leaves every section and the messages found`() {
        val shown = results.withoutChats(setOf(2L)) as SearchState.Results

        assertEquals(listOf(1L), (shown.chats as SearchSection.Items).items.map { it.id })
        assertEquals(listOf(3L), (shown.contacts as SearchSection.Items).items.map { it.id })
        assertEquals(SearchSection.Loading, shown.global)
        assertEquals(listOf(1L), shown.messages.found.map { it.chat.id })
    }

    @Test
    fun `without locked chats the results stay as they are`() {
        assertSame(results, results.withoutChats(emptySet()))
    }
}
