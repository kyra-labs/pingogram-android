package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.viewModelScope
import dev.kyralabs.pingogram.core.testing.FakeLocalChatNamesRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The conversation's top bar titled with the chat's name on this phone (`REQUIREMENTS.md` FR-X20). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatLocalNameTest {
    private val names = FakeLocalChatNamesRepository()
    private val users = FakeUserRepository()
    private val created = mutableListOf<ChatLocalNameViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(chatId: Long): ChatLocalNameViewModel =
        ChatLocalNameViewModel(chatId, names, users).also { viewModel ->
            created += viewModel
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.name.collect {} }
            runCurrent()
        }

    @Test
    fun `a group's and someone's name show, a contact's does not, and none once it is cleared`() =
        runTest {
            names.names.value = persistentMapOf(GROUP to "Book club", PERSON to "Dev", CONTACT to "Mum's phone")
            users.setUsers(user(PERSON), user(CONTACT).copy(isContact = true))
            val group = viewModel(GROUP)
            val person = viewModel(PERSON)
            val contact = viewModel(CONTACT)

            assertEquals("Book club", group.name.value)
            assertEquals("Dev", person.name.value)
            assertNull(contact.name.value)

            names.names.value = persistentMapOf()
            runCurrent()
            assertNull(group.name.value)
        }

    @Test
    fun `the top bar takes the name, and stays as it is without one and in a topic`() {
        val state = ChatUiState(header = header(title = "Readers 2026"))

        assertEquals(UiText.Plain("Book club"), state.withLocalName("Book club").header?.title)
        assertSame(state, state.withLocalName(null))
        assertSame(state, state.withLocalName("Book club", inTopic = true))
        val loading = ChatUiState()
        assertSame(loading, loading.withLocalName("Book club"))
    }

    private fun header(title: String) =
        ChatHeader(chatId = GROUP, title = UiText.Plain(title), photo = null, placeholderIcon = null, subtitle = null)

    private companion object {
        const val GROUP = -100L
        const val PERSON = 5L
        const val CONTACT = 6L
    }
}
