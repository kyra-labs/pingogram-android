package dev.kyralabs.pingogram.feature.scanner

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews

@PingogramPreviews
@Composable
private fun QrScannerRationalePreview() {
    PingogramPreview { QrScannerScreen(QrScannerUiState(ScanPurpose.Any, CameraAccess.Rationale()), {}, {}) }
}

@PingogramPreviews
@Composable
private fun QrScannerDeniedPreview() {
    PingogramPreview { QrScannerScreen(QrScannerUiState(ScanPurpose.Any, CameraAccess.Rationale(true)), {}, {}) }
}

@PingogramPreviews
@Composable
private fun QrScannerNoCameraPreview() {
    PingogramPreview { QrScannerScreen(QrScannerUiState(ScanPurpose.Any, CameraAccess.NoCamera), {}, {}) }
}

@PingogramPreviews
@Composable
private fun QrScannerScanningPreview() {
    PingogramPreview {
        QrScannerScreen(QrScannerUiState(ScanPurpose.DeviceLogin, CameraAccess.Granted), {}, {}, viewfinder = {})
    }
}

@PingogramPreviews
@Composable
private fun QrScannerNotTelegramPreview() {
    PingogramPreview {
        QrScannerScreen(
            QrScannerUiState(
                ScanPurpose.Any,
                CameraAccess.Granted,
                result = ScanResult.NotTelegramLink("https://example.com/menu?table=12"),
            ),
            {},
            {},
            viewfinder = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun QrScannerFailedPreview() {
    PingogramPreview {
        QrScannerScreen(
            QrScannerUiState(ScanPurpose.Any, CameraAccess.Granted, result = ScanResult.Failed(DomainError.Network)),
            {},
            {},
            viewfinder = {},
        )
    }
}
