package dev.kyralabs.pingogram.feature.chat.ui.bot

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** What counts as a command being typed, and which commands a query finds. */
class CommandMatchingTest {
    private val commands =
        persistentListOf(
            BotCommandItem("start", "/start@helper_bot", "Start the bot"),
            BotCommandItem("stickers", "/stickers@helper_bot", "Find stickers"),
            BotCommandItem("roll", "/roll@dice_bot", "Roll a die"),
        )

    @Test
    fun `a slash and a command's characters are a command being typed`() {
        assertEquals("", commandQuery("/"))
        assertEquals("st", commandQuery("/st"))
        assertEquals("start@hel", commandQuery("/start@hel"))
        assertEquals("Start_2", commandQuery("/Start_2"))
    }

    @Test
    fun `text around it, arguments, another line or other characters are not`() {
        assertNull(commandQuery(""))
        assertNull(commandQuery("st"))
        assertNull(commandQuery("hi /st"))
        assertNull(commandQuery("/start now"))
        assertNull(commandQuery("/start\n"))
        assertNull(commandQuery("/stärt"))
        assertNull(commandQuery("/" + "a".repeat(33)))
    }

    @Test
    fun `a query finds the commands it starts, in order, ignoring case`() {
        assertEquals(listOf("start", "stickers"), commands.startingWith("ST").map { it.command })
        assertEquals(listOf("start"), commands.startingWith("start@h").map { it.command })
        assertEquals(emptyList<String>(), commands.startingWith("start@dice").map { it.command })
        assertEquals(3, commands.startingWith("").size)
    }

    @Test
    fun `the sheet's search finds names and descriptions`() {
        assertEquals(listOf("roll"), commands.searching("die").map { it.command })
        assertEquals(listOf("stickers"), commands.searching("/STICK").map { it.command })
        assertEquals(3, commands.searching("  ").size)
    }
}
