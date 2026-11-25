package dev.kyralabs.pingogram.feature.chats.ui

import dev.kyralabs.pingogram.core.domain.model.ChatListType
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.user
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The user's own names for chats in the list (`REQUIREMENTS.md` FR-X20): in place of a group's or someone's title,
 * never of a contact's, and gone again when the name is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatListLocalNamesTest : ChatListViewModelHarness() {
    @Test
    fun `a group and someone who is not a contact show the name given on this phone`() =
        runTest {
            localNames.names.value = persistentMapOf(GROUP to "Book club", STRANGER to "Dev from the meetup")
            users.setUsers(user(STRANGER, firstName = "Dev"))
            chats.setChats(
                ChatListType.Main,
                chat(id = GROUP, type = ChatType.BasicGroup(1), title = "Readers 2026"),
                chat(id = STRANGER, title = "Dev"),
            )

            val viewModel = viewModel()

            assertEquals(UiText.Plain("Book club"), viewModel.item(GROUP).title)
            assertEquals(UiText.Plain("Dev from the meetup"), viewModel.item(STRANGER).title)
        }

    @Test
    fun `a contact shows the contact name, and the chat's title comes back when the name goes`() =
        runTest {
            localNames.names.value = persistentMapOf(CONTACT to "Mum's phone", GROUP to "Book club")
            users.setUsers(user(CONTACT, firstName = "Ada").copy(isContact = true))
            chats.setChats(
                ChatListType.Main,
                chat(id = CONTACT, title = "Ada"),
                chat(id = GROUP, type = ChatType.BasicGroup(1), title = "Readers 2026"),
            )
            val viewModel = viewModel()
            assertEquals(UiText.Plain("Ada"), viewModel.item(CONTACT).title)

            localNames.names.value = persistentMapOf()
            runCurrent()

            assertEquals(UiText.Plain("Readers 2026"), viewModel.item(GROUP).title)
        }

    private companion object {
        const val GROUP = -100L
        const val STRANGER = 5L
        const val CONTACT = 6L
    }
}
