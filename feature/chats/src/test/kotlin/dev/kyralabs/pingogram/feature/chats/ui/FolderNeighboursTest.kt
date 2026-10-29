package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The folder tabs a swipe of the list shows: those beside the selected one, none past the ends or without tabs. */
class FolderNeighboursTest {
    private val tabs = ChatListPreviewData.folders

    @Test
    fun `a folder in the middle has a tab on each side`() =
        assertEquals(FolderNeighbours(FolderTarget(null), FolderTarget(2)), FolderNeighbours.of(tabs, 1))

    @Test
    fun `All chats has nothing before it`() =
        assertEquals(FolderNeighbours(next = FolderTarget(1)), FolderNeighbours.of(tabs, null))

    @Test
    fun `the last folder has nothing after it`() =
        assertEquals(FolderNeighbours(previous = FolderTarget(2)), FolderNeighbours.of(tabs, 3))

    @Test
    fun `without tabs or with a folder no longer there, nothing`() {
        assertEquals(FolderNeighbours(), FolderNeighbours.of(emptyList(), null))
        assertEquals(FolderNeighbours(), FolderNeighbours.of(tabs, 42))
    }
}
