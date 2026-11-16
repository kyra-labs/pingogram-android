package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** How [FolderSwitch] places the list shown and the one it slides against as gestures and selections change. */
class FolderSwitchTest {
    private val outgoing = ChatListPreviewData.contentWithFolders

    @Test
    fun `a drag that reveals the side of the list a swipe left keeps it`() {
        val folderSwitch = FolderSwitch().apply { width = WIDTH }
        folderSwitch.fixed = outgoing
        folderSwitch.side = -1

        // Toward the end: the list before the one shown, on the start side, where the left list is.
        folderSwitch.drag = WIDTH * PART
        folderSwitch.followDrag()

        assertSame(outgoing, folderSwitch.fixed)
        assertEquals(-1, folderSwitch.side)
    }

    @Test
    fun `a drag toward the other side gives way to the live neighbour there`() {
        val folderSwitch = FolderSwitch().apply { width = WIDTH }
        folderSwitch.fixed = outgoing
        folderSwitch.side = -1

        folderSwitch.drag = -WIDTH * PART
        folderSwitch.followDrag()

        assertNull(folderSwitch.fixed)
        assertEquals(1, folderSwitch.side)
    }

    @Test
    fun `a tab tapped while a slide runs leaves the list on its way where it is`() =
        runTest {
            val folderSwitch = FolderSwitch().apply { width = WIDTH }
            // The list shown is still a third of the width from its place.
            folderSwitch.offset.snapTo(WIDTH * PART)
            val before = folderSwitch.position

            folderSwitch.bringIn(outgoing, toward = 1, swiped = false)

            // It is the companion now, a width toward the start of the list coming in.
            assertEquals(-1, folderSwitch.side)
            assertEquals(before, folderSwitch.position + folderSwitch.side * WIDTH, 0f)
        }

    @Test
    fun `a landing swipe leaves the list it slid against where it is`() =
        runTest {
            val folderSwitch = FolderSwitch().apply { width = WIDTH }
            folderSwitch.drag = -WIDTH * PART
            folderSwitch.followDrag()
            val companionBefore = folderSwitch.position + folderSwitch.side * WIDTH

            folderSwitch.bringIn(outgoing, toward = 0, swiped = true)

            // The list it slid against is shown now, where it was; the one it left is beside it.
            assertEquals(companionBefore, folderSwitch.position, 0f)
            assertEquals(-1, folderSwitch.side)
            assertSame(outgoing, folderSwitch.fixed)
        }

    private companion object {
        const val WIDTH = 1000
        const val PART = 0.3f
    }
}
