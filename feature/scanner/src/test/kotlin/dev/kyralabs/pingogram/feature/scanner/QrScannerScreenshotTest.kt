package dev.kyralabs.pingogram.feature.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the QR code scanner (`docs/TESTING.md`): the rationale before Android asks, after a refusal for good, a
 * device without a camera, and with the camera (a flat placeholder for its picture) while it reads, for a device's
 * login, with a code that is no Telegram link, and after a failure; light and dark where the scanner draws its own
 * surfaces, and the ones with the most text at 200% font scale. Brand colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class QrScannerScreenshotTest {
    @Test
    fun rationaleLight() = capture(ThemeMode.Light, QrScannerUiState(ScanPurpose.Any, CameraAccess.Rationale()))

    @Test
    fun rationaleDark() = capture(ThemeMode.Dark, QrScannerUiState(ScanPurpose.Any, CameraAccess.Rationale()))

    @Test
    fun deniedLight() = capture(ThemeMode.Light, QrScannerUiState(ScanPurpose.Any, CameraAccess.Rationale(true)))

    @Test
    fun noCameraDark() = capture(ThemeMode.Dark, QrScannerUiState(ScanPurpose.Any, CameraAccess.NoCamera))

    @Test
    fun scanningLight() = capture(ThemeMode.Light, QrScannerUiState(ScanPurpose.Any, CameraAccess.Granted))

    @Test
    fun deviceLoginDark() = capture(ThemeMode.Dark, QrScannerUiState(ScanPurpose.DeviceLogin, CameraAccess.Granted))

    @Test
    fun notTelegramLight() =
        capture(
            ThemeMode.Light,
            QrScannerUiState(
                ScanPurpose.Any,
                CameraAccess.Granted,
                ScanResult.NotTelegramLink("https://example.com/menu"),
            ),
        )

    @Test
    fun notLoginDark() =
        capture(
            ThemeMode.Dark,
            QrScannerUiState(ScanPurpose.DeviceLogin, CameraAccess.Granted, ScanResult.NotLoginCode),
        )

    @Test
    fun failedDark() =
        capture(
            ThemeMode.Dark,
            QrScannerUiState(ScanPurpose.Any, CameraAccess.Granted, ScanResult.Failed(DomainError.Network)),
        )

    /** At 200% font scale (`REQUIREMENTS.md` §6.3), the states with the most text: they grow and never clip. */
    @Test
    @Config(fontScale = 2f)
    fun rationaleFontScale200() = capture(ThemeMode.Light, QrScannerUiState(ScanPurpose.Any, CameraAccess.Rationale()))

    @Test
    @Config(fontScale = 2f)
    fun notTelegramFontScale200() =
        capture(
            ThemeMode.Light,
            QrScannerUiState(
                ScanPurpose.Any,
                CameraAccess.Granted,
                ScanResult.NotTelegramLink("https://example.com/menu"),
            ),
        )

    @Test
    @Config(fontScale = 2f)
    fun deviceLoginFontScale200() =
        capture(ThemeMode.Dark, QrScannerUiState(ScanPurpose.DeviceLogin, CameraAccess.Granted))

    private fun capture(
        themeMode: ThemeMode,
        state: QrScannerUiState,
    ) = captureRoboImage {
        PingogramTheme(themeMode = themeMode, dynamicColor = false) {
            QrScannerScreen(state, {}, {}, viewfinder = { CameraPlaceholder() })
        }
    }
}

/** What stands in for the camera's picture: a flat mid tone, so the window and the dim around it show. */
@Composable
private fun CameraPlaceholder() {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.outline),
    )
}

/** A compact phone in portrait (`docs/TESTING.md`). */
const val COMPACT = "w411dp-h891dp-xxhdpi"
