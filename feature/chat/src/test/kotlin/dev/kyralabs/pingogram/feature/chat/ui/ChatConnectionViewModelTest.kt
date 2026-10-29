package dev.kyralabs.pingogram.feature.chat.ui

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.repository.ConnectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** [ChatConnectionViewModel]: ready until TDLib says otherwise, then every change of the connection, once each. */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatConnectionViewModelTest {
    private val connection = MutableStateFlow(ConnectionStatus.Ready)
    private val repository =
        object : ConnectionRepository {
            override val connectionStatus = connection
        }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `it is ready before the repository answers`() {
        assertEquals(ConnectionStatus.Ready, ChatConnectionViewModel(repository).status.value)
    }

    @Test
    fun `it follows the connection while collected`() =
        runTest {
            connection.value = ConnectionStatus.Connecting
            val viewModel = ChatConnectionViewModel(repository)

            viewModel.status.test {
                assertEquals(ConnectionStatus.Ready, awaitItem())
                assertEquals(ConnectionStatus.Connecting, awaitItem())

                connection.value = ConnectionStatus.Updating
                assertEquals(ConnectionStatus.Updating, awaitItem())

                connection.value = ConnectionStatus.Ready
                assertEquals(ConnectionStatus.Ready, awaitItem())
            }
        }
}
