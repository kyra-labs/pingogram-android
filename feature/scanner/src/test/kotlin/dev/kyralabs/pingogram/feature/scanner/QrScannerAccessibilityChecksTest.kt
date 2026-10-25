package dev.kyralabs.pingogram.feature.scanner

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The QR code scanner's states have named controls with 48 dp touch targets ([assertAccessible],
 * `docs/audit/accessibility.md`), on a compact phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class QrScannerAccessibilityChecksTest {
    @get:Rule(order = 0)
    val activityRegistration =
        object : ExternalResource() {
            override fun before() {
                val application = RuntimeEnvironment.getApplication()
                shadowOf(application.packageManager)
                    .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
            }
        }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun rationale() = check(QrScannerUiState(ScanPurpose.Any, CameraAccess.Rationale()))

    @Test
    fun denied() = check(QrScannerUiState(ScanPurpose.Any, CameraAccess.Rationale(true)))

    @Test
    fun noCamera() = check(QrScannerUiState(ScanPurpose.Any, CameraAccess.NoCamera))

    @Test
    fun scanning() = check(QrScannerUiState(ScanPurpose.DeviceLogin, CameraAccess.Granted))

    @Test
    fun notTelegram() =
        check(
            QrScannerUiState(
                ScanPurpose.Any,
                CameraAccess.Granted,
                ScanResult.NotTelegramLink("https://example.com/menu"),
            ),
        )

    @Test
    fun failed() =
        check(QrScannerUiState(ScanPurpose.Any, CameraAccess.Granted, ScanResult.Failed(DomainError.Network)))

    private fun check(state: QrScannerUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                QrScannerScreen(state, {}, {}, viewfinder = { Box(Modifier.fillMaxSize()) })
            }
        }
        composeRule.assertAccessible()
    }
}
