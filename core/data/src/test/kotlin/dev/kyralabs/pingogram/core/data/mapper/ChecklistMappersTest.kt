package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.Checklist
import dev.kyralabs.pingogram.core.domain.model.ChecklistTask
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.NewChecklist
import kotlinx.collections.immutable.persistentListOf
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A checklist in a message, and the checklist the user sends. */
class ChecklistMappersTest {
    @Test
    fun `a checklist carries its title, tasks with their completers, and what the user may do`() {
        val list =
            TdApi.Checklist(
                TdApi.FormattedText("Release", emptyArray()),
                arrayOf(
                    TdApi.ChecklistTask(
                        1,
                        TdApi.FormattedText("Tag", emptyArray()),
                        TdApi.MessageSenderUser(42),
                        1_700_000_000,
                    ),
                    TdApi.ChecklistTask(2, TdApi.FormattedText("Post", emptyArray()), null, 0),
                ),
                true,
                false,
                true,
                true,
            )

        val content = TdApi.MessageChecklist(list).toMessageContent(sender = null, isChannelPost = false)

        assertEquals(
            MessageContent.Checklist(
                Checklist(
                    title = FormattedText("Release", persistentListOf()),
                    tasks =
                        persistentListOf(
                            ChecklistTask(
                                1,
                                FormattedText("Tag", persistentListOf()),
                                MessageSender.User(42),
                                1_700_000_000,
                            ),
                            ChecklistTask(2, FormattedText("Post", persistentListOf()), null, 0),
                        ),
                    othersCanAddTasks = true,
                    canAddTasks = false,
                    othersCanMarkTasksAsDone = true,
                    canMarkTasksAsDone = true,
                ),
            ),
            content,
        )
        val checklist = (content as MessageContent.Checklist).list
        assertEquals(1, checklist.doneCount)
        assertTrue(checklist.tasks[0].isDone)
        assertFalse(checklist.tasks[1].isDone)
    }

    @Test
    fun `a new checklist numbers its tasks from one, as plain text`() {
        val input =
            NewChecklist(
                "Release",
                persistentListOf("Tag", "Post"),
                othersCanAddTasks = false,
                othersCanMarkTasksAsDone = true,
            ).toInputMessageChecklist()

        val list = input.checklist
        assertEquals("Release", list.title.text)
        assertEquals(listOf(1 to "Tag", 2 to "Post"), list.tasks.map { it.id to it.text.text })
        assertEquals(listOf(false, true), listOf(list.othersCanAddTasks, list.othersCanMarkTasksAsDone))
    }
}
