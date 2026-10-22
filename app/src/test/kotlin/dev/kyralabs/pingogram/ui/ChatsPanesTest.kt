package dev.kyralabs.pingogram.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** How many panes the Chats tab has per window ([chatsPaneScaffoldDirective]), and its panes' content keys. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class ChatsPanesTest {
    @Test
    fun `compact and medium windows get one pane`() {
        assertEquals(1, partitions(widthDp = 411, heightDp = 891))
        assertEquals(1, partitions(widthDp = 700, heightDp = 891))
    }

    @Test
    fun `expanded and large windows get two panes`() {
        assertEquals(2, partitions(widthDp = 840, heightDp = 891))
        assertEquals(2, partitions(widthDp = 1280, heightDp = 800))
        assertEquals(2, partitions(widthDp = 1599, heightDp = 1000))
    }

    @Test
    fun `extra-large windows get three panes`() {
        assertEquals(3, partitions(widthDp = 1600, heightDp = 1000))
        assertEquals(3, partitions(widthDp = 2560, heightDp = 1440))
    }

    @Test
    fun `every pane content survives as its key`() {
        val contents =
            listOf(
                ChatsPaneContent.Chat(101),
                ChatsPaneContent.Profile(chatId = 101, userId = 101),
                ChatsPaneContent.ChatInfo(chatId = -1_001_234_567_890, infoChatId = -1_001_234_567_890),
                ChatsPaneContent.Profile(chatId = -42, userId = 7),
            )

        contents.forEach { assertEquals(it, ChatsPaneContent.of(it.key)) }
    }

    @Test
    fun `keys this layout did not make read as nothing`() {
        listOf(null, "", "chat", "chat:x", "chat:1:2", "profile:1", "info:1:2:3", "page:1").forEach {
            assertNull(ChatsPaneContent.of(it), "for $it")
        }
    }

    private fun partitions(
        widthDp: Int,
        heightDp: Int,
    ): Int {
        val sizeClass = WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(widthDp, heightDp)
        return chatsPaneScaffoldDirective(WindowAdaptiveInfo(sizeClass, Posture())).maxHorizontalPartitions
    }
}
