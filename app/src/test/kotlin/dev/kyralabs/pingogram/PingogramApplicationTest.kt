package dev.kyralabs.pingogram

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

/**
 * The application's own lifetime in tests: what it starts on its scope ends with it, before its context is gone.
 * These are the only tests that run on it (every other test runs on a plain `Application`), so they wait themselves
 * for its work to end ([waitForApplicationWork]): cancelling cannot stop a call already under way that does not
 * suspend, such as a repository registering with Android as it is made, which could otherwise reach the next test's
 * torn-down context.
 */
@RunWith(RobolectricTestRunner::class)
class PingogramApplicationTest {
    private val application by lazy { ApplicationProvider.getApplicationContext<PingogramApplication>() }

    /** Cancels the application's work and waits for it, at most [TERMINATE_TIMEOUT], before Robolectric drops it. */
    @After
    fun waitForApplicationWork() {
        runBlocking {
            withTimeoutOrNull(TERMINATE_TIMEOUT) {
                application.applicationScope.coroutineContext.job
                    .cancelAndJoin()
            }
        }
    }

    @Test
    fun `terminating the application cancels the work it started on its scope`() {
        assertTrue(application.applicationScope.isActive)

        application.onTerminate()

        assertFalse(application.applicationScope.isActive)
    }

    @Test
    fun `a call already under way on its scope ends before the test does`() {
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
        waitForApplicationWork()

        assertTrue(finished.get())
    }

    private companion object {
        const val BLOCKING_CALL_MILLIS = 300L
        const val TIMEOUT_SECONDS = 5L
        val TERMINATE_TIMEOUT = 5.seconds
    }
}
