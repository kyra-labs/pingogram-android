package dev.kyralabs.pingogram.core.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Which chats' names on this phone changed between renders ([ChatNameChanges]). */
class ChatNameChangesTest {
    @Test
    fun `nothing changed at first, then each name given, changed or taken off`() {
        val changes = ChatNameChanges()

        assertEquals(emptySet<Long>(), changes.since(mapOf(1L to "Mum", 2L to "Book club")))
        assertEquals(emptySet<Long>(), changes.since(mapOf(1L to "Mum", 2L to "Book club")))
        assertEquals(setOf(2L, 3L), changes.since(mapOf(1L to "Mum", 2L to "Readers", 3L to "Team")))
        assertEquals(setOf(1L, 2L, 3L), changes.since(emptyMap()))
    }
}
