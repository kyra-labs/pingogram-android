package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.testing.FakeAccountGraphs
import dev.kyralabs.pingogram.core.testing.FakeDemoRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Where each conversation was left, kept apart by account: switching accounts, or to the demo, carries none over. */
class ChatPlacesTest {
    private val graphs = FakeAccountGraphs(activeSlot = 0)
    private val demo = FakeDemoRepository()
    private val places = ChatPlaces(graphs, demo)

    private fun place() = places.place(CHAT, topic = null, savedState = SavedStateHandle())

    @Test
    fun `a switch of accounts finds no place in the same chat, and switching back finds its own`() {
        place().shown(5)

        graphs.active.value = 1
        assertNull(place().left)
        place().shown(9)

        graphs.active.value = 0
        assertEquals(5L, place().left)
        graphs.active.value = 1
        assertEquals(9L, place().left)
    }

    @Test
    fun `the demo keeps its own place, apart from the account it was entered from`() {
        place().shown(5)

        demo.isDemo.value = true
        assertNull(place().left)
        place().shown(2)

        demo.isDemo.value = false
        assertEquals(5L, place().left)
    }

    private companion object {
        const val CHAT = 42L
    }
}
