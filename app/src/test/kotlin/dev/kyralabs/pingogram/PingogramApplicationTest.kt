package dev.kyralabs.pingogram

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.isActive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The application's own lifetime in tests: what it starts on its scope ends with it. */
@RunWith(RobolectricTestRunner::class)
class PingogramApplicationTest {
    @Test
    fun `terminating the application cancels the work it started on its scope`() {
        val application = ApplicationProvider.getApplicationContext<PingogramApplication>()
        assertTrue(application.applicationScope.isActive)

        application.onTerminate()

        assertFalse(application.applicationScope.isActive)
    }
}
