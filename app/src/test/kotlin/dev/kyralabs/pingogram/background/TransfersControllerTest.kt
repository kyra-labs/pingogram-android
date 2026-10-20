package dev.kyralabs.pingogram.background

import dev.kyralabs.pingogram.core.domain.model.Transfers
import dev.kyralabs.pingogram.core.testing.FakeTransferRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** When the transfers' worker starts: as the user's transfers begin in the foreground, or when the user returns. */
@OptIn(ExperimentalCoroutinesApi::class)
class TransfersControllerTest {
    private val transfers = FakeTransferRepository()
    private val foreground = MutableStateFlow(true)
    private var starts = 0

    private fun TestScope.started() {
        TransfersController(transfers, foreground, { starts++ }, backgroundScope).start()
        runCurrent()
    }

    private fun TestScope.set(
        transfers: Transfers? = null,
        inForeground: Boolean? = null,
    ) {
        transfers?.let { this@TransfersControllerTest.transfers.transfers.value = it }
        inForeground?.let { foreground.value = it }
        runCurrent()
    }

    @Test
    fun `nothing on its way starts nothing`() =
        runTest {
            started()

            assertEquals(0, starts)
        }

    @Test
    fun `transfers that begin start the worker once, however they progress`() =
        runTest {
            started()

            set(transfers = Transfers(uploads = 1, totalSize = 100))
            set(transfers = Transfers(uploads = 1, transferredSize = 50, totalSize = 100))
            set(transfers = Transfers(uploads = 2, totalSize = 200))

            assertEquals(1, starts)
        }

    @Test
    fun `transfers that begin again start it again`() =
        runTest {
            started()

            set(transfers = Transfers(downloads = 1))
            set(transfers = Transfers.None)
            set(transfers = Transfers(downloads = 1))

            assertEquals(2, starts)
        }

    @Test
    fun `in the background it waits for the user to come back while transfers run`() =
        runTest {
            foreground.value = false
            started()

            set(transfers = Transfers(uploads = 1))
            assertEquals(0, starts)

            set(inForeground = true)
            assertEquals(1, starts)

            set(inForeground = false)
            set(inForeground = true)
            assertEquals(2, starts)
        }
}
