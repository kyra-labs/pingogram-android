package dev.kyralabs.pingogram

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** The application's own lifetime in tests: what it starts on its scope ends with it, before its context is gone. */
@RunWith(RobolectricTestRunner::class)
class PingogramApplicationTest {
    @Test
    fun `terminating the application cancels the work it started on its scope`() {
        val application = ApplicationProvider.getApplicationContext<PingogramApplication>()
        assertTrue(application.applicationScope.isActive)

        application.onTerminate()

        assertFalse(application.applicationScope.isActive)
    }

    @Test
    fun `terminating the application waits for a call already under way on its scope`() {
        val application = ApplicationProvider.getApplicationContext<PingogramApplication>()
        val started = CountDownLatch(1)
        val finished = AtomicBoolean(false)
        application.applicationScope.launch {
            started.countDown()
            // A call that does not suspend, which cancelling cannot stop: a registration with Android, say.
            Thread.sleep(BLOCKING_CALL_MILLIS)
            finished.set(true)
        }
        assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))

        application.onTerminate()

        assertTrue(finished.get())
    }

    private companion object {
        const val BLOCKING_CALL_MILLIS = 300L
        const val TIMEOUT_SECONDS = 5L
    }
}
